cmake_minimum_required(VERSION 3.22.1)

project("ondevice_llama" LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# llama.cpp is a git submodule pinned to a release tag (see .gitmodules).
# Mirrors upstream's examples/llama.android setup: one CPU backend library per Arm feature level,
# picked at runtime by llama_bridge.cpp, plus KleidiAI's runtime-dispatched Arm kernels.
set(GGML_SYSTEM_ARCH "ARM")
set(GGML_CPU_KLEIDIAI ON)
set(GGML_OPENMP ON)

# Photos for GGUF models: the mtmd (multimodal) library, images only (video needs an ffmpeg binary).
set(LLAMA_BUILD_MTMD ON CACHE BOOL "" FORCE)
set(MTMD_VIDEO OFF CACHE BOOL "" FORCE)

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/llama.cpp)
add_subdirectory(${LLAMA_SRC} build-llama)

# Combine devices: llama.cpp's RPC client aborts the whole app when a device drops out. Build it
# from a copy where a lost connection is only flagged (ggml_backend_rpc_lost), so Mochu can end the
# reply and reload the model instead. The submodule itself stays untouched.
if (TARGET ggml-rpc)
    set(RPC_DIR ${LLAMA_SRC}/ggml/src/ggml-rpc)
    file(READ ${RPC_DIR}/ggml-rpc.cpp RPC_SRC)
    set(RPC_PATCHES 0)
    # A function, not a macro: macro arguments get their escapes re-read, which breaks the quotes.
    function(rpc_patch FROM TO)
        string(FIND "${RPC_SRC}" "${FROM}" RPC_AT)
        if (RPC_AT EQUAL -1)
            message(FATAL_ERROR "ggml-rpc.cpp changed upstream; update the Mochu patch for: ${FROM}")
        endif()
        string(REPLACE "${FROM}" "${TO}" RPC_OUT "${RPC_SRC}")
        set(RPC_SRC "${RPC_OUT}" PARENT_SCOPE)
    endfunction()
    rpc_patch("#define RPC_STATUS_ASSERT(x) if (!(x)) GGML_ABORT(\"Remote RPC server crashed or returned malformed response\")"
              "static std::atomic<bool> g_rpc_lost{false};\nstatic void rpc_connection_lost() { if (!g_rpc_lost.exchange(true)) GGML_LOG_ERROR(\"RPC connection lost\\n\"); }\n#define RPC_STATUS_ASSERT(x) if (!(x)) rpc_connection_lost()")
    rpc_patch("GGML_ABORT(\"Failed to connect to %s\\n\", endpoint.c_str());"
              "rpc_connection_lost(); running = true; thread = std::thread(rpc_dispatcher_trampoline, this); return;")
    rpc_patch("GGML_ABORT(\"RPC handshake failed for %s\\n\", endpoint.c_str());"
              "rpc_connection_lost(); sock = nullptr; running = true; thread = std::thread(rpc_dispatcher_trampoline, this); return;")
    # After a loss, requests finish at once (outputs left zeroed) instead of touching a dead socket.
    rpc_patch("if (msg_ptr->cmd != RPC_CMD_NONE) {"
              "if (msg_ptr->cmd != RPC_CMD_NONE && sock && !g_rpc_lost) {")
    string(APPEND RPC_SRC "\nextern \"C\" GGML_BACKEND_API bool ggml_backend_rpc_lost(void) { return g_rpc_lost.load(); }\nextern \"C\" GGML_BACKEND_API void ggml_backend_rpc_clear_lost(void) { g_rpc_lost = false; }\n")
    set(RPC_PATCHED ${CMAKE_CURRENT_BINARY_DIR}/ggml-rpc-mochu.cpp)
    file(WRITE ${RPC_PATCHED}.tmp "${RPC_SRC}")
    configure_file(${RPC_PATCHED}.tmp ${RPC_PATCHED} COPYONLY)
    set_property(TARGET ggml-rpc PROPERTY SOURCES ${RPC_PATCHED} ${RPC_DIR}/transport.cpp)
    target_include_directories(ggml-rpc PRIVATE ${RPC_DIR})
endif()

add_library(${CMAKE_PROJECT_NAME} SHARED llama_bridge.cpp)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${LLAMA_SRC}/common
        ${LLAMA_SRC}/tools/mtmd
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include)

target_link_libraries(${CMAKE_PROJECT_NAME}
        llama
        llama-common
        mtmd
        android
        log
        dl)
