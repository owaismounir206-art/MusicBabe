cmake_minimum_required(VERSION 3.22)
project(musicbabe_whisper C CXX)

set(CMAKE_CXX_STANDARD 17)

# whisper.cpp (vendored under third_party, CPU backend only).
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
add_subdirectory(third_party/whisper.cpp)

add_library(musicbabe_whisper SHARED musicbabe_whisper_jni.c)
target_link_libraries(musicbabe_whisper whisper log)
if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(musicbabe_whisper PRIVATE -O3)
endif ()
target_link_options(musicbabe_whisper PRIVATE -Wl,-z,max-page-size=16384)
