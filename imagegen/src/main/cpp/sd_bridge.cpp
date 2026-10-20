// JNI bridge to stable-diffusion.cpp for on-device image generation (libmochu_sd.so). It carries its
// own ggml, kept private from the copy llama.cpp uses.

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <string>

#include "ggml.h"
#include "stable-diffusion.h"

#define LOG_TAG "ondevice-sd"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::atomic<int> g_step{0};
std::atomic<int> g_steps{0};

void sd_log(enum sd_log_level_t level, const char *text, void * /*data*/) {
    __android_log_write(level == SD_LOG_ERROR ? ANDROID_LOG_ERROR : level == SD_LOG_WARN ? ANDROID_LOG_WARN : ANDROID_LOG_INFO, LOG_TAG, text);
}

void sd_progress(int step, int steps, float /*time*/, void * /*data*/) {
    g_step = step;
    g_steps = steps;
}

std::string from_java(JNIEnv *env, jstring s) {
    const char *chars = env->GetStringUTFChars(s, nullptr);
    std::string out = chars;
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_nikunj_gemmabuilder_StableDiffusionNative_nativeLoad(JNIEnv *env, jobject, jstring jmodel_path, jint n_threads) {
    // ggml asserts print to stderr, which Android drops; send them to logcat before aborting.
    ggml_set_abort_callback([](const char *message) { __android_log_write(ANDROID_LOG_FATAL, LOG_TAG, message); });
    sd_set_log_callback(sd_log, nullptr);
    sd_set_progress_callback(sd_progress, nullptr);
    const std::string path = from_java(env, jmodel_path);
    sd_ctx_params_t params;
    sd_ctx_params_init(&params);
    params.model_path = path.c_str();
    params.n_threads = n_threads;
    params.enable_mmap = true;
    sd_ctx_t *ctx = new_sd_ctx(&params);
    if (!ctx) LOGe("Could not load image model %s", path.c_str());
    return reinterpret_cast<jlong>(ctx);
}

// Returns RGB bytes (width * height * 3), or null if generation failed or was cancelled.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_nikunj_gemmabuilder_StableDiffusionNative_nativeGenerate(JNIEnv *env, jobject, jlong handle, jstring jprompt,
                                                                  jstring jnegative, jint width, jint height, jint steps, jlong seed,
                                                                  jstring jsampler) {
    auto *ctx = reinterpret_cast<sd_ctx_t *>(handle);
    const std::string prompt = from_java(env, jprompt);
    const std::string negative = from_java(env, jnegative);
    const std::string sampler = from_java(env, jsampler);
    sd_img_gen_params_t params;
    sd_img_gen_params_init(&params);
    params.prompt = prompt.c_str();
    params.negative_prompt = negative.c_str();
    params.width = width;
    params.height = height;
    params.seed = seed;
    params.batch_count = 1;
    params.sample_params.sample_steps = steps;
    // Turbo and LCM models are distilled for no classifier-free guidance.
    params.sample_params.guidance.txt_cfg = 1.0f;
    // A model can ask for its own sampler (LCM models need "lcm"); otherwise the model's default.
    params.sample_params.sample_method = sampler.empty() ? sd_get_default_sample_method(ctx) : str_to_sample_method(sampler.c_str());
    params.sample_params.scheduler = sd_get_default_scheduler(ctx, params.sample_params.sample_method);
    g_step = 0;
    g_steps = steps;
    sd_cancel_generation(ctx, SD_CANCEL_RESET);

    sd_image_t *images = nullptr;
    int count = 0;
    if (!generate_image(ctx, &params, &images, &count) || count < 1 || !images || !images[0].data) {
        LOGe("Image generation failed.");
        return nullptr;
    }
    const sd_image_t &img = images[0];
    const jsize size = static_cast<jsize>(img.width * img.height * 3);
    jbyteArray out = env->NewByteArray(size);
    if (img.channel == 3) {
        env->SetByteArrayRegion(out, 0, size, reinterpret_cast<const jbyte *>(img.data));
    } else {
        std::string rgb(static_cast<size_t>(size), '\0');
        for (uint32_t i = 0; i < img.width * img.height; i++) {
            for (int c = 0; c < 3; c++) rgb[i * 3 + c] = static_cast<char>(img.data[i * img.channel + c]);
        }
        env->SetByteArrayRegion(out, 0, size, reinterpret_cast<const jbyte *>(rgb.data()));
    }
    for (int i = 0; i < count; i++) free(images[i].data);
    free(images);
    return out;
}

// {step, steps} of the generation in progress.
extern "C" JNIEXPORT jintArray JNICALL
Java_com_nikunj_gemmabuilder_StableDiffusionNative_nativeProgress(JNIEnv *env, jobject) {
    const jint values[2] = {g_step.load(), g_steps.load()};
    jintArray out = env->NewIntArray(2);
    env->SetIntArrayRegion(out, 0, 2, values);
    return out;
}

extern "C" JNIEXPORT void JNICALL
Java_com_nikunj_gemmabuilder_StableDiffusionNative_nativeCancel(JNIEnv *, jobject, jlong handle) {
    if (handle) sd_cancel_generation(reinterpret_cast<sd_ctx_t *>(handle), SD_CANCEL_ALL);
}

extern "C" JNIEXPORT void JNICALL
Java_com_nikunj_gemmabuilder_StableDiffusionNative_nativeFree(JNIEnv *, jobject, jlong handle) {
    if (handle) free_sd_ctx(reinterpret_cast<sd_ctx_t *>(handle));
}
