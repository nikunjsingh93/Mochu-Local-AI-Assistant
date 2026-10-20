// JNI bridge between LlamaCppEngine (Kotlin) and llama.cpp for running .gguf models.
//
// Each loaded model gets one Session. A session keeps the chat messages and the tokens that are
// currently in the KV cache. Every user turn re-renders the whole chat with the model's own chat
// template and only decodes the part that differs from the cache, so follow-up turns (and fresh
// chats that start with the same system prompt) skip re-processing the shared prefix.

#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>

#include <algorithm>
#include <string>
#include <vector>

#include "chat.h"
#include "common.h"
#include "ggml-backend.h"
#include "llama.h"
#include "sampling.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define LOG_TAG "ondevice-llama"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGw(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int BATCH_SIZE = 512;

// Return codes shared with LlamaCppNative.kt.
constexpr int SEND_OK = 0;
constexpr int SEND_TEMPLATE_FAILED = 1;
constexpr int SEND_DECODE_FAILED = 2;
constexpr int SEND_CONTEXT_FULL = 3;

// CPU backend builds produced by GGML_CPU_ALL_VARIANTS, best first.
const char *const CPU_VARIANTS[] = {
        "libggml-cpu-android_armv9.2_2.so",
        "libggml-cpu-android_armv9.2_1.so",
        "libggml-cpu-android_armv9.0_1.so",
        "libggml-cpu-android_armv8.6_1.so",
        "libggml-cpu-android_armv8.2_2.so",
        "libggml-cpu-android_armv8.2_1.so",
        "libggml-cpu-android_armv8.0_1.so",
};

struct Session {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    common_batch batch;
    common_chat_templates_ptr templates;
    common_sampler *sampler = nullptr;
    mtmd_context *mtmd = nullptr;     // vision encoder (mmproj), when the model has one

    std::vector<common_chat_msg> messages;
    std::vector<llama_token> cached;  // tokens currently in the KV cache (sequence 0)
    std::string reply;                // assistant reply being generated
    std::string pending;              // trailing bytes of an incomplete UTF-8 character
    int max_output = 0;
    bool thinking = false;            // let reasoning models think first (their template decides how)
    std::string lead;                 // text to emit before the first token (a <think> the prompt opened)
    int generated = 0;
    bool generating = false;
    long long kv_per_token = 0;       // KV bytes that grow with each token of context
    long long kv_fixed = 0;           // KV bytes that don't (sliding-window and recurrent state)
};

// KV cache sizes llama.cpp logs while creating a context ("size = X MiB (N cells, ..."), read back
// after the load so the app knows how much memory the context takes.
std::vector<std::pair<double, unsigned>> g_kv_lines;

void android_log_callback(ggml_log_level level, const char *text, void * /*user*/) {
    if (const char *at = strstr(text, "size = ")) {
        double mib = 0;
        unsigned cells = 0;
        if (sscanf(at, "size = %lf MiB (%u cells", &mib, &cells) == 2) g_kv_lines.emplace_back(mib, cells);
    }
    const int prio = level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR
                   : level == GGML_LOG_LEVEL_WARN  ? ANDROID_LOG_WARN
                   : level == GGML_LOG_LEVEL_INFO  ? ANDROID_LOG_INFO
                                                   : ANDROID_LOG_DEBUG;
    __android_log_write(prio, LOG_TAG, text);
}

// The variant libraries live inside the APK, which has no directory ggml could scan, so score
// each one by name (the linker finds them in the APK) and register the best one this CPU supports.
std::string load_best_cpu_backend(const char *native_lib_dir) {
    const char *best = nullptr;
    int best_score = 0;
    for (const char *name : CPU_VARIANTS) {
        void *handle = dlopen(name, RTLD_NOW | RTLD_LOCAL);
        if (!handle) continue;
        auto score_fn = reinterpret_cast<int (*)()>(dlsym(handle, "ggml_backend_score"));
        const int score = score_fn ? score_fn() : 0;
        dlclose(handle);
        if (score > best_score) {
            best_score = score;
            best = name;
        }
    }
    if (best && ggml_backend_load(best)) return best;

    // Fallback for installs where native libraries are extracted to disk.
    ggml_backend_load_all_from_path(native_lib_dir);
    return ggml_backend_reg_count() > 0 ? "default" : "";
}

// Length of the longest prefix of [s] that does not end in the middle of a UTF-8 character.
size_t complete_utf8_prefix(const std::string &s) {
    const size_t n = s.size();
    for (size_t back = 1; back <= 4 && back <= n; back++) {
        const auto c = static_cast<unsigned char>(s[n - back]);
        if ((c & 0xC0) == 0x80) continue;  // continuation byte, keep looking for the lead byte
        const size_t expected = (c & 0x80) == 0x00 ? 1
                              : (c & 0xE0) == 0xC0 ? 2
                              : (c & 0xF0) == 0xE0 ? 3
                              : (c & 0xF8) == 0xF0 ? 4
                                                   : 1;  // invalid lead byte: pass it through
        return back >= expected ? n : n - back;
    }
    return n;
}

std::string render_chat(Session *s) {
    common_chat_templates_inputs inputs;
    inputs.messages = s->messages;
    inputs.add_generation_prompt = true;
    inputs.enable_thinking = s->thinking;
    try {
        inputs.use_jinja = true;
        return common_chat_templates_apply(s->templates.get(), inputs).prompt;
    } catch (const std::exception &e) {
        LOGw("Jinja chat template failed (%s); using the built-in template.", e.what());
        inputs.use_jinja = false;
        return common_chat_templates_apply(s->templates.get(), inputs).prompt;
    }
}

void finish_reply(Session *s) {
    s->reply += s->pending;
    s->pending.clear();
    common_chat_msg msg;
    msg.role = "assistant";
    msg.content = s->reply;
    s->messages.push_back(msg);
    s->generating = false;
}

jbyteArray to_bytes(JNIEnv *env, const std::string &text) {
    jbyteArray out = env->NewByteArray(static_cast<jsize>(text.size()));
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    return out;
}

Session *session(jlong handle) { return reinterpret_cast<Session *>(handle); }

// Some templates (Qwen3.5) end the prompt with "<think>", so the model's output starts mid-thought and
// only the closing tag shows up. Emitting the opening tag first lets the app show thinking as it streams.
bool prompt_opens_thinking(const std::string &prompt) {
    const size_t end = prompt.find_last_not_of(" \t\r\n");
    const std::string tag = "<think>";
    return end != std::string::npos && end + 1 >= tag.size() && prompt.compare(end + 1 - tag.size(), tag.size(), tag) == 0;
}

void start_reply(Session *s, int max_output, const std::string &prompt) {
    s->lead = prompt_opens_thinking(prompt) ? "<think>\n" : "";
    common_sampler_reset(s->sampler);
    s->reply.clear();
    s->pending.clear();
    s->generated = 0;
    s->max_output = max_output > 0 ? max_output : static_cast<int>(llama_n_ctx(s->ctx));
    s->generating = true;
}

// A turn with photos: the chat is rendered with a media marker per photo, the photos are encoded
// by the vision model, and everything is evaluated from an empty cache. Afterwards the message keeps
// a "[photo]" note instead of the markers, so later turns re-render as plain text.
int send_with_images(Session *s, const std::string &text, const std::vector<std::string> &images, int max_output) {
    std::string markers;
    for (size_t i = 0; i < images.size(); i++) markers += mtmd_default_marker();
    common_chat_msg user;
    user.role = "user";
    user.content = markers + "\n" + text;
    s->messages.push_back(user);

    std::string prompt;
    try {
        prompt = render_chat(s);
    } catch (const std::exception &e) {
        LOGe("Chat template failed: %s", e.what());
        s->messages.pop_back();
        return SEND_TEMPLATE_FAILED;
    }

    std::vector<mtmd_bitmap *> bitmaps;
    for (const auto &path : images) {
        mtmd_bitmap *bmp = mtmd_helper_bitmap_init_from_file(s->mtmd, path.c_str(), false, mtmd_helper_init_opt_default()).bitmap;
        if (bmp) bitmaps.push_back(bmp);
        else LOGw("Could not read image %s", path.c_str());
    }
    auto free_bitmaps = [&]() { for (auto *b : bitmaps) mtmd_bitmap_free(b); };
    if (bitmaps.size() != images.size()) {
        free_bitmaps();
        s->messages.pop_back();
        return SEND_DECODE_FAILED;
    }

    mtmd_input_chunks *chunks = mtmd_input_chunks_init();
    mtmd_input_text input{prompt.c_str(), prompt.size(), true, true};
    const int32_t tok = mtmd_tokenize(s->mtmd, chunks, &input, const_cast<const mtmd_bitmap **>(bitmaps.data()), bitmaps.size());
    free_bitmaps();
    const int n_ctx = static_cast<int>(llama_n_ctx(s->ctx));
    if (tok != 0 || static_cast<int>(mtmd_helper_get_n_tokens(chunks)) >= n_ctx - 8) {
        mtmd_input_chunks_free(chunks);
        s->messages.pop_back();
        return tok != 0 ? SEND_DECODE_FAILED : SEND_CONTEXT_FULL;
    }

    llama_memory_t mem = llama_get_memory(s->ctx);
    llama_memory_clear(mem, false);
    s->cached.clear();
    llama_pos new_n_past = 0;
    const int32_t res = mtmd_helper_eval_chunks(s->mtmd, s->ctx, chunks, 0, 0, BATCH_SIZE, true, &new_n_past);
    mtmd_input_chunks_free(chunks);
    if (res != 0) {
        LOGe("Image prompt eval failed (%d).", res);
        llama_memory_clear(mem, false);
        s->messages.pop_back();
        return SEND_DECODE_FAILED;
    }
    // Positions continue from new_n_past; -1 never matches a real token, so the next text turn
    // re-processes the whole chat instead of reusing a prefix that contained image embeddings.
    s->cached.assign(static_cast<size_t>(new_n_past), -1);
    s->messages.back().content = std::string("[photo] ") + text;
    start_reply(s, max_output, prompt);
    return SEND_OK;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_nikunj_gemmabuilder_LlamaCppNative_nativeInitBackends(JNIEnv *env, jobject, jstring jnative_lib_dir) {
    llama_log_set(android_log_callback, nullptr);
    const char *dir = env->GetStringUTFChars(jnative_lib_dir, nullptr);
    const std::string variant = load_best_cpu_backend(dir);
    env->ReleaseStringUTFChars(jnative_lib_dir, dir);
    llama_backend_init();
    LOGi("llama.cpp CPU backend: %s", variant.c_str());
    return env->NewStringUTF(variant.c_str());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_nikunj_gemmabuilder_LlamaCppNative_nativeLoad(JNIEnv *env, jobject, jstring jmodel_path, jint n_ctx, jint n_threads, jint kv_type) {
    const char *path = env->GetStringUTFChars(jmodel_path, nullptr);
    llama_model *model = llama_model_load_from_file(path, llama_model_default_params());
    env->ReleaseStringUTFChars(jmodel_path, path);
    if (!model) {
        LOGe("Could not load model.");
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    const int trained = llama_model_n_ctx_train(model);
    ctx_params.n_ctx = trained > 0 ? std::min<int>(n_ctx, trained) : n_ctx;
    ctx_params.n_batch = BATCH_SIZE;
    ctx_params.n_ubatch = BATCH_SIZE;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    if (kv_type == 1 || kv_type == 2) {
        // A quantized V cache needs flash attention.
        const ggml_type type = kv_type == 1 ? GGML_TYPE_Q8_0 : GGML_TYPE_Q4_0;
        ctx_params.type_k = type;
        ctx_params.type_v = type;
        ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
    }
    g_kv_lines.clear();
    llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGe("Could not create context (n_ctx=%d).", ctx_params.n_ctx);
        llama_model_free(model);
        return 0;
    }

    auto *s = new Session();
    s->model = model;
    s->ctx = ctx;
    const unsigned ctx_cells = llama_n_ctx(ctx);
    for (const auto &[mib, cells] : g_kv_lines) {
        const auto bytes = static_cast<long long>(mib * 1024.0 * 1024.0);
        if (cells >= ctx_cells && cells > 0) s->kv_per_token += bytes / cells;
        else s->kv_fixed += bytes;
    }
    g_kv_lines.clear();
    s->batch = common_batch(ctx);
    try {
        s->templates = common_chat_templates_init(model, "");
    } catch (const std::exception &e) {
        LOGw("Model chat template unusable (%s); falling back to chatml.", e.what());
        try {
            s->templates = common_chat_templates_init(model, "chatml");
        } catch (const std::exception &e2) {
            LOGe("chatml template failed too: %s", e2.what());
            llama_free(ctx);
            llama_model_free(model);
            delete s;
            return 0;
        }
    }

    common_params_sampling sparams;
    sparams.temp = 0.35f;
    sparams.top_k = 20;
    sparams.top_p = 0.9f;
    s->sampler = common_sampler_init(model, sparams);
    if (!s->sampler) {
        LOGe("Could not create sampler.");
        llama_free(ctx);
        llama_model_free(model);
        delete s;
        return 0;
    }
    return reinterpret_cast<jlong>(s);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nikunj_gemmabuilder_LlamaCppNative_nativeLoadVision(JNIEnv *env, jobject, jlong handle, jstring jmmproj_path, jint n_threads) {
    Session *s = session(handle);
    if (s->mtmd) return JNI_TRUE;
    const char *path = env->GetStringUTFChars(jmmproj_path, nullptr);
    mtmd_context_params params = mtmd_context_params_default();
    params.use_gpu = false;
    params.n_threads = n_threads;
    params.print_timings = false;
    params.warmup = false;
    params.media_marker = mtmd_default_marker();
    s->mtmd = mtmd_init_from_file(path, s->model, params);
    env->ReleaseStringUTFChars(jmmproj_path, path);
    if (s->mtmd && !mtmd_support_vision(s->mtmd)) {
        mtmd_free(s->mtmd);
        s->mtmd = nullptr;
    }
    if (!s->mtmd) LOGw("Vision projector could not be loaded.");
    return s->mtmd ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_nikunj_gemmabuilder_LlamaCppNative_nativeContextSize(JNIEnv *, jobject, jlong handle) {
    return static_cast<jint>(llama_n_ctx(session(handle)->ctx));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nikunj_gemmabuilder_LlamaCppNative_nativeDescribe(JNIEnv *env, jobject, jlong handle) {
    char desc[128];
    llama_model_desc(session(handle)->model, desc, sizeof(desc));
    return env->NewStringUTF(desc);
}

extern "C" JNIEXPORT void JNICALL
Java_com_nikunj_gemmabuilder_LlamaCppNative_nativeStartChat(JNIEnv *env, jobject, jlong handle, jstring jsystem_prompt) {
    Session *s = session(handle);
    const char *text = env->GetStringUTFChars(jsystem_prompt, nullptr);
    s->messages.clear();
    common_chat_msg system;
    system.role = "system";
    system.content = text;
    s->messages.push_back(system);
    env->ReleaseStringUTFChars(jsystem_prompt, text);
    s->reply.clear();
    s->pending.clear();
    s->generating = false;
    // The KV cache is kept on purpose: the next turn reuses whatever prefix still matches.
}

extern "C" JNIEXPORT jint JNICALL
Java_com_nikunj_gemmabuilder_LlamaCppNative_nativeSendUser(JNIEnv *env, jobject, jlong handle, jstring juser_text, jint max_output, jboolean thinking, jobjectArray jimages) {
    Session *s = session(handle);
    s->thinking = thinking;
    const char *text = env->GetStringUTFChars(juser_text, nullptr);
    const std::string user_text = text;
    env->ReleaseStringUTFChars(juser_text, text);

    std::vector<std::string> images;
    const jsize n_images = jimages ? env->GetArrayLength(jimages) : 0;
    for (jsize i = 0; i < n_images; i++) {
        auto jpath = static_cast<jstring>(env->GetObjectArrayElement(jimages, i));
        const char *p = env->GetStringUTFChars(jpath, nullptr);
        images.emplace_back(p);
        env->ReleaseStringUTFChars(jpath, p);
        env->DeleteLocalRef(jpath);
    }
    if (!images.empty() && s->mtmd) return send_with_images(s, user_text, images, max_output);

    common_chat_msg user;
    user.role = "user";
    user.content = user_text;
    s->messages.push_back(user);

    std::string prompt;
    try {
        prompt = render_chat(s);
    } catch (const std::exception &e) {
        LOGe("Chat template failed: %s", e.what());
        s->messages.pop_back();
        return SEND_TEMPLATE_FAILED;
    }

    const std::vector<llama_token> tokens = common_tokenize(s->ctx, prompt, true, true);
    const int n_ctx = static_cast<int>(llama_n_ctx(s->ctx));
    if (tokens.empty() || static_cast<int>(tokens.size()) >= n_ctx - 8) {
        s->messages.pop_back();
        return SEND_CONTEXT_FULL;
    }

    size_t n_keep = 0;
    while (n_keep < s->cached.size() && n_keep < tokens.size() && s->cached[n_keep] == tokens[n_keep]) n_keep++;
    if (n_keep == tokens.size()) n_keep--;  // decode at least one token so there are logits to sample from

    llama_memory_t mem = llama_get_memory(s->ctx);
    if (!llama_memory_seq_rm(mem, 0, static_cast<llama_pos>(n_keep), -1)) {
        // Some caches (recurrent / sliding-window) can't drop a partial range; start clean.
        llama_memory_clear(mem, false);
        n_keep = 0;
    }
    s->cached.resize(n_keep);

    for (size_t i = n_keep; i < tokens.size(); i += BATCH_SIZE) {
        const size_t end = std::min(tokens.size(), i + BATCH_SIZE);
        s->batch.clear();
        for (size_t j = i; j < end; j++) {
            s->batch.add(tokens[j], static_cast<llama_pos>(j), 0, j == tokens.size() - 1);
        }
        if (llama_process(s->ctx, LLAMA_PROCESS_TYPE_DECODE, s->batch.get()) != 0) {
            LOGe("Prompt decode failed at token %zu.", i);
            llama_memory_clear(mem, false);
            s->cached.clear();
            s->messages.pop_back();
            return SEND_DECODE_FAILED;
        }
        s->cached.insert(s->cached.end(), tokens.begin() + static_cast<long>(i), tokens.begin() + static_cast<long>(end));
    }

    start_reply(s, max_output, prompt);
    return SEND_OK;
}

// Returns the next chunk of UTF-8 text (possibly empty), or null when the reply is finished.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_nikunj_gemmabuilder_LlamaCppNative_nativeNextToken(JNIEnv *env, jobject, jlong handle) {
    Session *s = session(handle);
    if (!s->generating) return nullptr;
    if (!s->lead.empty()) {
        const std::string lead = s->lead;
        s->lead.clear();
        return to_bytes(env, lead);
    }

    const int n_ctx = static_cast<int>(llama_n_ctx(s->ctx));
    if (s->generated >= s->max_output || static_cast<int>(s->cached.size()) >= n_ctx - 1) {
        finish_reply(s);
        return nullptr;
    }

    const llama_token id = common_sampler_sample(s->sampler, s->ctx, -1);
    common_sampler_accept(s->sampler, id, true);
    if (llama_vocab_is_eog(llama_model_get_vocab(s->model), id)) {
        finish_reply(s);
        return nullptr;
    }

    s->batch.clear();
    s->batch.add(id, static_cast<llama_pos>(s->cached.size()), 0, true);
    if (llama_process(s->ctx, LLAMA_PROCESS_TYPE_DECODE, s->batch.get()) != 0) {
        LOGe("Token decode failed.");
        finish_reply(s);
        return nullptr;
    }
    s->cached.push_back(id);
    s->generated++;

    s->pending += common_token_to_piece(s->ctx, id);
    const size_t ready = complete_utf8_prefix(s->pending);
    const std::string out = s->pending.substr(0, ready);
    s->pending.erase(0, ready);
    s->reply += out;
    return to_bytes(env, out);
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_nikunj_gemmabuilder_LlamaCppNative_nativeKvBytes(JNIEnv *env, jobject, jlong handle) {
    Session *s = session(handle);
    const jlong values[2] = {s->kv_per_token, s->kv_fixed};
    jlongArray out = env->NewLongArray(2);
    env->SetLongArrayRegion(out, 0, 2, values);
    return out;
}

// Feeds text into the reply as though the model had written it, e.g. "</think>" when thinking runs
// past its budget, so the next sampled token continues from there.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_nikunj_gemmabuilder_LlamaCppNative_nativeInject(JNIEnv *env, jobject, jlong handle, jstring jtext) {
    Session *s = session(handle);
    if (!s->generating) return JNI_FALSE;
    const char *chars = env->GetStringUTFChars(jtext, nullptr);
    const std::string text = chars;
    env->ReleaseStringUTFChars(jtext, chars);
    const std::vector<llama_token> tokens = common_tokenize(s->ctx, text, false, true);
    const int n_ctx = static_cast<int>(llama_n_ctx(s->ctx));
    if (tokens.empty() || static_cast<int>(s->cached.size() + tokens.size()) >= n_ctx - 1) return JNI_FALSE;
    s->batch.clear();
    for (size_t i = 0; i < tokens.size(); i++) {
        s->batch.add(tokens[i], static_cast<llama_pos>(s->cached.size() + i), 0, i == tokens.size() - 1);
    }
    if (llama_process(s->ctx, LLAMA_PROCESS_TYPE_DECODE, s->batch.get()) != 0) return JNI_FALSE;
    for (const auto t : tokens) {
        common_sampler_accept(s->sampler, t, true);
        s->cached.push_back(t);
    }
    s->reply += s->pending + text;
    s->pending.clear();
    s->generated += static_cast<int>(tokens.size());
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_nikunj_gemmabuilder_LlamaCppNative_nativeTokenCount(JNIEnv *, jobject, jlong handle) {
    return static_cast<jint>(session(handle)->cached.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_nikunj_gemmabuilder_LlamaCppNative_nativeFree(JNIEnv *, jobject, jlong handle) {
    Session *s = session(handle);
    if (!s) return;
    common_sampler_free(s->sampler);
    if (s->mtmd) mtmd_free(s->mtmd);
    s->templates.reset();
    s->batch = common_batch();
    llama_free(s->ctx);
    llama_model_free(s->model);
    delete s;
}
