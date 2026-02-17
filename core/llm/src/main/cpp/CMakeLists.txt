cmake_minimum_required(VERSION 3.22.1)

project(llm_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/llama.cpp)

if(NOT EXISTS "${LLAMA_DIR}/include/llama.h")
    message(
        FATAL_ERROR
        "llama.cpp sources are missing. Initialize the git submodule at core/llm/src/main/cpp/third_party/llama.cpp (git submodule update --init --recursive)"
    )
endif()

# Debug builds default to `-O0`, which makes on-device inference painfully slow.
# Prefer runtime performance over native debugging convenience.
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    message(STATUS "Optimizing native Debug build (-O3) for faster LLM inference")
    add_compile_options(-O3)
endif()

# Keep llama.cpp minimal for mobile builds.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_TOOLS_INSTALL OFF CACHE BOOL "" FORCE)
set(LLAMA_TESTS_INSTALL OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(LLAMA_ALL_WARNINGS_3RD_PARTY OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_DIR} llama_build)

find_library(LOG_LIB log)

add_library(
    llm
    SHARED
    llama_jni.cpp
)

target_link_libraries(
    llm
    ${LOG_LIB}
    android
    llama
)

target_include_directories(
    llm
    PRIVATE
    ${LLAMA_DIR}/include
)
