cmake_minimum_required(VERSION 3.22.1)

project("mochu_sd" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# stable-diffusion.cpp is a git submodule pinned to a release tag; it builds its own ggml statically.
# Arm v8.2 with dot product and fp16 covers phones from about 2019 on. OpenMP stays off: :app already
# loads its own OpenMP runtime for llama.cpp, and two in one process abort.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(SD_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(SD_WEBP OFF CACHE BOOL "" FORCE)
set(SD_WEBM OFF CACHE BOOL "" FORCE)
add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/stable-diffusion.cpp build-sd)

add_library(mochu_sd SHARED sd_bridge.cpp)
target_link_libraries(mochu_sd stable-diffusion android log)
# Keep this ggml's symbols private, so they never bind to llama.cpp's ggml in the same process.
target_link_options(mochu_sd PRIVATE -Wl,--exclude-libs,ALL -Wl,-Bsymbolic)
