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
