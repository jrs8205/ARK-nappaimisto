# Whisper-sanelun natiiviosa: whisper.cpp käännetään mukaan vain
# CPU-taustalla (ei GPU-backendejä) ja kääritään ohueen JNI-siltaan.
cmake_minimum_required(VERSION 3.22)
project(ark_whisper C CXX)

set(BUILD_SHARED_LIBS OFF)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

add_subdirectory(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp
    whisper_build
)

add_library(ark_whisper SHARED whisper_jni.cpp)
target_link_libraries(ark_whisper whisper log)
