cmake_minimum_required(VERSION 3.22.1)

project(whisper)

set(CMAKE_CXX_STANDARD 17)

set(WHISPER_LIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/whisper.cpp)

if(NOT EXISTS "${WHISPER_LIB_DIR}/src/whisper.cpp")
    message(
        FATAL_ERROR
        "whisper.cpp sources are missing. Initialize the git submodule at core/whisper/src/main/cpp/third_party/whisper.cpp (git submodule update --init --recursive)"
    )
endif()

# Debug builds default to `-O0`, which makes on-device inference painfully slow.
# Prefer runtime performance over native debugging convenience.
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    message(STATUS "Optimizing native Debug build (-O3) for faster Whisper inference")
    add_compile_options(-O3)
endif()

# Get whisper.cpp version
file(READ "${WHISPER_LIB_DIR}/CMakeLists.txt" MAIN_CMAKE_CONTENT)
string(REGEX MATCH "project\\(\"whisper\\.cpp\" VERSION ([0-9]+\\.[0-9]+\\.[0-9]+)\\)" VERSION_MATCH "${MAIN_CMAKE_CONTENT}")
if(CMAKE_MATCH_1)
    set(WHISPER_VERSION ${CMAKE_MATCH_1})
else()
    set(WHISPER_VERSION "unknown")
endif()

message(STATUS "Whisper version: ${WHISPER_VERSION}")

set(
    SOURCE_FILES
    ${WHISPER_LIB_DIR}/src/whisper.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper_jni.cpp
)

find_library(LOG_LIB log)

include(FetchContent)

function(build_whisper_library target_name)
    add_library(
        ${target_name}
        SHARED
        ${SOURCE_FILES}
    )

    target_compile_definitions(${target_name} PUBLIC GGML_USE_CPU)
    target_compile_definitions(${target_name} PRIVATE WHISPER_VERSION="${WHISPER_VERSION}")

    if (${target_name} STREQUAL "whisper_v8fp16_va")
        target_compile_options(${target_name} PRIVATE -march=armv8.2-a+fp16)
        set(GGML_COMPILE_OPTIONS                      -march=armv8.2-a+fp16)
    elseif (${target_name} STREQUAL "whisper_vfpv4")
        target_compile_options(${target_name} PRIVATE -mfpu=neon-vfpv4)
        set(GGML_COMPILE_OPTIONS                      -mfpu=neon-vfpv4)
    endif ()

    if (NOT ${CMAKE_BUILD_TYPE} STREQUAL "Debug")
        target_compile_options(${target_name} PRIVATE -O3)
        target_compile_options(${target_name} PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
        target_compile_options(${target_name} PRIVATE -ffunction-sections -fdata-sections)

        target_link_options(${target_name} PRIVATE -Wl,--gc-sections)
        target_link_options(${target_name} PRIVATE -Wl,--exclude-libs,ALL)
        target_link_options(${target_name} PRIVATE -flto)
    endif ()

    FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_LIB_DIR}/ggml)
    FetchContent_MakeAvailable(ggml)
    target_compile_options(ggml PRIVATE ${GGML_COMPILE_OPTIONS})

    target_link_libraries(${target_name} ${LOG_LIB} android ggml)

    target_include_directories(
        ${target_name}
        PRIVATE
        ${WHISPER_LIB_DIR}
        ${WHISPER_LIB_DIR}/src
        ${WHISPER_LIB_DIR}/include
        ${WHISPER_LIB_DIR}/ggml/include
        ${WHISPER_LIB_DIR}/ggml/src
        ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu
    )
endfunction()

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    build_whisper_library("whisper_v8fp16_va")
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    build_whisper_library("whisper_vfpv4")
endif ()

build_whisper_library("whisper")
