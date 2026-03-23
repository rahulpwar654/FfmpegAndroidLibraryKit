#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>

#include <atomic>
#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <limits>
#include <mutex>
#include <string>
#include <vector>

extern "C" {
typedef void (*av_log_callback_fn)(void*, int, const char*, va_list);
void av_log_set_callback(av_log_callback_fn callback);
void av_log_set_level(int level);
int av_log_get_level(void);
}

static JavaVM* javaVM = nullptr;
static jobject logCallbackObj = nullptr;
static jmethodID onLogMethod = nullptr;
static std::mutex executeMutex;
static std::atomic<bool> cancelRequested{false};

// Exposed for builds where ffmpeg_main checks this flag from native code.
extern "C" volatile int cancel_flag = 0;

using ffmpeg_entry_fn = int (*)(int, char**);
using cstr_noarg_fn = const char* (*)();

static constexpr jint kKeepLogLevel = std::numeric_limits<jint>::min();
static const char* kKnownSymbols[] = {
        "ffmpeg_main",
        "ffmpeg_execute",
        "av_version_info",
        "avcodec_configuration",
        "avcodec_license"
};

static void clear_log_callback(JNIEnv* env) {
    if (env != nullptr && logCallbackObj != nullptr) {
        env->DeleteGlobalRef(logCallbackObj);
    }
    logCallbackObj = nullptr;
    onLogMethod = nullptr;
}

static ffmpeg_entry_fn resolve_ffmpeg_entrypoint() {
    static const char* kEntrypointSymbols[] = {
            "ffmpeg_main",
            "ffmpeg_execute"
    };
    for (const char* symbolName : kEntrypointSymbols) {
        void* symbol = dlsym(RTLD_DEFAULT, symbolName);
        if (symbol != nullptr) {
            return reinterpret_cast<ffmpeg_entry_fn>(symbol);
        }
    }
    return nullptr;
}

static cstr_noarg_fn resolve_text_symbol(const char* symbolName) {
    return reinterpret_cast<cstr_noarg_fn>(dlsym(RTLD_DEFAULT, symbolName));
}

static const char* call_text_symbol(const char* symbolName) {
    const cstr_noarg_fn fn = resolve_text_symbol(symbolName);
    return fn != nullptr ? fn() : nullptr;
}

static void log_callback(void*, int, const char* fmt, va_list vl) {
    if (javaVM == nullptr || logCallbackObj == nullptr || onLogMethod == nullptr) {
        return;
    }

    char buffer[1024];
    vsnprintf(buffer, sizeof(buffer), fmt, vl);

    JNIEnv* env = nullptr;
    bool detachAfter = false;
    const jint getEnvResult = javaVM->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (getEnvResult == JNI_EDETACHED) {
        if (javaVM->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            return;
        }
        detachAfter = true;
    } else if (getEnvResult != JNI_OK) {
        return;
    }

    jstring msg = env->NewStringUTF(buffer);
    if (msg != nullptr) {
        env->CallVoidMethod(logCallbackObj, onLogMethod, msg);
        env->DeleteLocalRef(msg);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    }

    if (detachAfter) {
        javaVM->DetachCurrentThread();
    }
}

static int execute_internal(
        JNIEnv* env,
        jobjectArray commands,
        jobject callback,
        jint logLevel,
        bool resetCancelFlag) {
    std::lock_guard<std::mutex> lock(executeMutex);

    if (commands == nullptr) {
        return -1;
    }

    env->GetJavaVM(&javaVM);

    clear_log_callback(env);
    if (callback != nullptr) {
        logCallbackObj = env->NewGlobalRef(callback);
        jclass callbackClass = env->GetObjectClass(callback);
        onLogMethod = callbackClass != nullptr
                ? env->GetMethodID(callbackClass, "onLog", "(Ljava/lang/String;)V")
                : nullptr;
        if (callbackClass != nullptr) {
            env->DeleteLocalRef(callbackClass);
        }

        // Disable callbacks if callback object doesn't expose onLog.
        if (onLogMethod == nullptr) {
            clear_log_callback(env);
        }
    }

    av_log_set_callback(log_callback);
    if (logLevel != kKeepLogLevel) {
        av_log_set_level(static_cast<int>(logLevel));
    }
    if (resetCancelFlag) {
        cancelRequested.store(false);
        cancel_flag = 0;
    }

    const jint argc = env->GetArrayLength(commands);
    std::vector<char*> argv(static_cast<size_t>(argc) + 1U, nullptr);
    for (jint i = 0; i < argc; ++i) {
        auto str = static_cast<jstring>(env->GetObjectArrayElement(commands, i));
        if (str == nullptr) {
            continue;
        }
        const char* raw = env->GetStringUTFChars(str, nullptr);
        if (raw != nullptr) {
            argv[static_cast<size_t>(i)] = strdup(raw);
            env->ReleaseStringUTFChars(str, raw);
        }
        env->DeleteLocalRef(str);
    }

    int result = -2;
    const ffmpeg_entry_fn ffmpegEntrypoint = resolve_ffmpeg_entrypoint();
    if (ffmpegEntrypoint != nullptr) {
        result = ffmpegEntrypoint(argc, argv.data());
    } else {
        result = -200;
        __android_log_print(
                ANDROID_LOG_ERROR,
                "FFmpeg",
                "No FFmpeg command entrypoint found (expected ffmpeg_main or ffmpeg_execute)");
    }

    for (char* arg : argv) {
        free(arg);
    }

    clear_log_callback(env);
    return result;
}

extern "C" jint JNI_OnLoad(JavaVM* vm, void*) {
    javaVM = vm;
    return JNI_VERSION_1_6;
}

extern "C" jint
Java_com_rahulp_ffmpeg_1core_FFmpegNative_execute(
        JNIEnv* env,
        jobject,
        jobjectArray commands,
        jobject callback) {
    return execute_internal(env, commands, callback, kKeepLogLevel, true);
}

extern "C" jint
Java_com_rahulp_ffmpeg_1core_FFmpegNative_nativeExecute(
        JNIEnv* env,
        jobject,
        jobjectArray commands,
        jobject callback) {
    return execute_internal(env, commands, callback, kKeepLogLevel, true);
}

extern "C" jint
Java_com_rahulp_ffmpeg_1core_FFmpegNative_nativeExecuteWithConfig(
        JNIEnv* env,
        jobject,
        jobjectArray commands,
        jobject callback,
        jint logLevel,
        jboolean resetCancelFlag) {
    return execute_internal(env, commands, callback, logLevel, resetCancelFlag == JNI_TRUE);
}

extern "C" void
Java_com_rahulp_ffmpeg_1core_FFmpegNative_nativeCancel(JNIEnv*, jobject) {
    cancelRequested.store(true);
    cancel_flag = 1;
}

extern "C" void
Java_com_rahulp_ffmpeg_1core_FFmpegNative_nativeSetLogLevel(JNIEnv*, jobject, jint level) {
    av_log_set_level(static_cast<int>(level));
}

extern "C" jint
Java_com_rahulp_ffmpeg_1core_FFmpegNative_nativeGetLogLevel(JNIEnv*, jobject) {
    return static_cast<jint>(av_log_get_level());
}

extern "C" jstring
Java_com_rahulp_ffmpeg_1core_FFmpegNative_nativeGetVersionInfo(JNIEnv* env, jobject) {
    const char* version = call_text_symbol("av_version_info");
    return env->NewStringUTF(version != nullptr ? version : "unavailable");
}

extern "C" jstring
Java_com_rahulp_ffmpeg_1core_FFmpegNative_nativeGetBuildConfiguration(JNIEnv* env, jobject) {
    const char* config = call_text_symbol("avcodec_configuration");
    return env->NewStringUTF(config != nullptr ? config : "unavailable");
}

extern "C" jstring
Java_com_rahulp_ffmpeg_1core_FFmpegNative_nativeGetCodecLicense(JNIEnv* env, jobject) {
    const char* license = call_text_symbol("avcodec_license");
    return env->NewStringUTF(license != nullptr ? license : "unavailable");
}

extern "C" jboolean
Java_com_rahulp_ffmpeg_1core_FFmpegNative_nativeHasFfmpegMain(JNIEnv*, jobject) {
    return resolve_ffmpeg_entrypoint() != nullptr ? JNI_TRUE : JNI_FALSE;
}

extern "C" jboolean
Java_com_rahulp_ffmpeg_1core_FFmpegNative_nativeIsSymbolAvailable(
        JNIEnv* env,
        jobject,
        jstring symbolName) {
    if (symbolName == nullptr) {
        return JNI_FALSE;
    }

    const char* raw = env->GetStringUTFChars(symbolName, nullptr);
    if (raw == nullptr) {
        return JNI_FALSE;
    }

    const bool present = dlsym(RTLD_DEFAULT, raw) != nullptr;
    env->ReleaseStringUTFChars(symbolName, raw);
    return present ? JNI_TRUE : JNI_FALSE;
}

extern "C" jobjectArray
Java_com_rahulp_ffmpeg_1core_FFmpegNative_nativeGetAvailableNativeSymbols(JNIEnv* env, jobject) {
    std::vector<const char*> available;
    available.reserve(sizeof(kKnownSymbols) / sizeof(kKnownSymbols[0]));

    for (const char* symbolName : kKnownSymbols) {
        if (dlsym(RTLD_DEFAULT, symbolName) != nullptr) {
            available.push_back(symbolName);
        }
    }

    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) {
        return nullptr;
    }

    jobjectArray result = env->NewObjectArray(
            static_cast<jsize>(available.size()),
            stringClass,
            nullptr);

    for (jsize i = 0; i < static_cast<jsize>(available.size()); ++i) {
        jstring symbol = env->NewStringUTF(available[static_cast<size_t>(i)]);
        if (symbol != nullptr) {
            env->SetObjectArrayElement(result, i, symbol);
            env->DeleteLocalRef(symbol);
        }
    }

    env->DeleteLocalRef(stringClass);
    return result;
}

