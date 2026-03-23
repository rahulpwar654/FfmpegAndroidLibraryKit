cmake_minimum_required(VERSION 3.22.1)

project(ffmpeg_jni)

# Prebuilt FFmpeg shared libs are packaged in src/main/jniLibs/<abi>/
set(FFMPEG_ROOT ${CMAKE_SOURCE_DIR}/../jniLibs)

add_library(avcodec SHARED IMPORTED)
set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION
        ${FFMPEG_ROOT}/${ANDROID_ABI}/libavcodec.so)

add_library(avformat SHARED IMPORTED)
set_target_properties(avformat PROPERTIES IMPORTED_LOCATION
        ${FFMPEG_ROOT}/${ANDROID_ABI}/libavformat.so)

add_library(avutil SHARED IMPORTED)
set_target_properties(avutil PROPERTIES IMPORTED_LOCATION
        ${FFMPEG_ROOT}/${ANDROID_ABI}/libavutil.so)

add_library(swscale SHARED IMPORTED)
set_target_properties(swscale PROPERTIES IMPORTED_LOCATION
        ${FFMPEG_ROOT}/${ANDROID_ABI}/libswscale.so)

add_library(swresample SHARED IMPORTED)
set_target_properties(swresample PROPERTIES IMPORTED_LOCATION
        ${FFMPEG_ROOT}/${ANDROID_ABI}/libswresample.so)

add_library(avfilter SHARED IMPORTED)
set_target_properties(avfilter PROPERTIES IMPORTED_LOCATION
        ${FFMPEG_ROOT}/${ANDROID_ABI}/libavfilter.so)

add_library(avdevice SHARED IMPORTED)
set_target_properties(avdevice PROPERTIES IMPORTED_LOCATION
        ${FFMPEG_ROOT}/${ANDROID_ABI}/libavdevice.so)

# Build only the JNI bridge; ffmpeg entrypoint is resolved at runtime from prebuilt libs.
add_library(ffmpeg_jni SHARED ffmpeg_jni.cpp)

target_link_libraries(ffmpeg_jni
        avcodec
        avformat
        avutil
        swscale
        swresample
        avfilter
        avdevice
        log
        android
        m
)