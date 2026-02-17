#include <jni.h>

#include <android/log.h>

#include <atomic>
#include <algorithm>
#include <string>

#include "whisper.h"

namespace {

#define LOG_TAG "WhisperJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

std::string JStringToString(JNIEnv * env, jstring value) {
    if (value == nullptr) {
        return "";
    }

    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return "";
    }

    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void ThrowJavaException(JNIEnv * env, const char * class_name, const std::string & message) {
    jclass exception_class = env->FindClass(class_name);
    if (exception_class == nullptr) {
        env->ExceptionClear();
        exception_class = env->FindClass("java/lang/RuntimeException");
        if (exception_class == nullptr) {
            return;
        }
    }
    env->ThrowNew(exception_class, message.c_str());
    env->DeleteLocalRef(exception_class);
}

struct ProgressCallbackState {
    JNIEnv * env = nullptr;
    jobject callback = nullptr;
    jmethodID method_id = nullptr;
    int last_logged_bucket = -1;
};

void WhisperProgressCallback(
        struct whisper_context * /* ctx */,
        struct whisper_state * /* state */,
        int progress,
        void * user_data) {
    auto * state = static_cast<ProgressCallbackState *>(user_data);
    if (state == nullptr || state->env == nullptr || state->callback == nullptr || state->method_id == nullptr) {
        return;
    }

    const int clamped = std::clamp(progress, 0, 100);
    const int bucket = (clamped / 10) * 10;
    if (bucket == state->last_logged_bucket) {
        return;
    }

    state->last_logged_bucket = bucket;
    state->env->CallVoidMethod(state->callback, state->method_id, bucket);
    if (state->env->ExceptionCheck()) {
        LOGW("Progress callback threw; clearing exception");
        state->env->ExceptionClear();
    }
}

std::atomic_bool g_abort_requested{false};

bool WhisperAbortCallback(void * /* user_data */) {
    // whisper.cpp expects the abort callback to return true when the current operation should abort.
    return g_abort_requested.load(std::memory_order_relaxed);
}

}  // namespace

extern "C"
JNIEXPORT jlong JNICALL
Java_io_morgan_idonthaveyourtime_core_whisper_WhisperJni_initContext(
        JNIEnv * env,
        jobject /* thiz */,
        jstring model_path) {
    const std::string model_path_string = JStringToString(env, model_path);
    if (model_path_string.empty()) {
        ThrowJavaException(env, "java/lang/IllegalArgumentException", "Model path is required");
        return 0;
    }

    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    params.flash_attn = false;

    whisper_context * context = whisper_init_from_file_with_params(model_path_string.c_str(), params);
    if (context == nullptr) {
        ThrowJavaException(env, "java/lang/IllegalStateException", "Couldn't create whisper context");
        return 0;
    }

    LOGI("Whisper context created modelPath=%s", model_path_string.c_str());
    return reinterpret_cast<jlong>(context);
}

extern "C"
JNIEXPORT void JNICALL
Java_io_morgan_idonthaveyourtime_core_whisper_WhisperJni_freeContext(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong context_ptr) {
    auto * context = reinterpret_cast<whisper_context *>(context_ptr);
    if (context != nullptr) {
        whisper_free(context);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_io_morgan_idonthaveyourtime_core_whisper_WhisperJni_fullTranscribe(
        JNIEnv * env,
        jobject /* thiz */,
        jlong context_ptr,
        jint num_threads,
        jfloatArray audio_data,
        jstring language,
        jobject progress_cb) {
    auto * context = reinterpret_cast<whisper_context *>(context_ptr);
    if (context == nullptr) {
        ThrowJavaException(env, "java/lang/IllegalStateException", "Whisper context is null");
        return;
    }

    if (audio_data == nullptr) {
        ThrowJavaException(env, "java/lang/IllegalArgumentException", "Audio data is required");
        return;
    }

    const int n_threads = std::max(1, static_cast<int>(num_threads));

    const std::string language_string = JStringToString(env, language);
    const bool auto_language = language_string.empty() || language_string == "auto";

    g_abort_requested.store(false, std::memory_order_relaxed);

    jfloat * audio_data_arr = env->GetFloatArrayElements(audio_data, nullptr);
    if (audio_data_arr == nullptr) {
        ThrowJavaException(env, "java/lang/IllegalStateException", "Unable to read audio data array");
        return;
    }
    const jsize audio_data_length = env->GetArrayLength(audio_data);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = n_threads;
    params.no_timestamps = true;
    params.print_special = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = false;
    // `detect_language` means "only detect language and then return" - we still want to transcribe.
    params.detect_language = false;
    params.no_context = true;
    params.single_segment = false;

    // Mobile-first performance default:
    params.greedy.best_of = 1;

    params.language = auto_language ? "auto" : language_string.c_str();
    params.abort_callback = WhisperAbortCallback;
    params.abort_callback_user_data = nullptr;

    ProgressCallbackState progress_state;
    if (progress_cb != nullptr) {
        jclass cb_class = env->GetObjectClass(progress_cb);
        if (cb_class != nullptr) {
            jmethodID method_id = env->GetMethodID(cb_class, "onProgress", "(I)V");
            env->DeleteLocalRef(cb_class);

            if (method_id != nullptr) {
                progress_state.env = env;
                progress_state.callback = progress_cb;
                progress_state.method_id = method_id;
                progress_state.last_logged_bucket = -1;

                params.progress_callback = WhisperProgressCallback;
                params.progress_callback_user_data = &progress_state;
            }
        }
    }

    const int rc = whisper_full(context, params, audio_data_arr, audio_data_length);

    env->ReleaseFloatArrayElements(audio_data, audio_data_arr, JNI_ABORT);

    if (rc != 0) {
        ThrowJavaException(
                env,
                "java/lang/RuntimeException",
                "whisper_full failed with error code " + std::to_string(rc));
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_io_morgan_idonthaveyourtime_core_whisper_WhisperJni_getSegmentCount(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong context_ptr) {
    auto * context = reinterpret_cast<whisper_context *>(context_ptr);
    if (context == nullptr) {
        return 0;
    }
    return whisper_full_n_segments(context);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_io_morgan_idonthaveyourtime_core_whisper_WhisperJni_getSegmentText(
        JNIEnv * env,
        jobject /* thiz */,
        jlong context_ptr,
        jint index) {
    auto * context = reinterpret_cast<whisper_context *>(context_ptr);
    if (context == nullptr) {
        ThrowJavaException(env, "java/lang/IllegalStateException", "Whisper context is null");
        return nullptr;
    }

    const char * text = whisper_full_get_segment_text(context, index);
    if (text == nullptr) {
        return env->NewStringUTF("");
    }

    return env->NewStringUTF(text);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_io_morgan_idonthaveyourtime_core_whisper_WhisperJni_getDetectedLanguageCode(
        JNIEnv * env,
        jobject /* thiz */,
        jlong context_ptr) {
    auto * context = reinterpret_cast<whisper_context *>(context_ptr);
    if (context == nullptr) {
        ThrowJavaException(env, "java/lang/IllegalStateException", "Whisper context is null");
        return nullptr;
    }

    const int lang_id = whisper_full_lang_id(context);
    const char * lang_str = whisper_lang_str(lang_id);
    if (lang_str == nullptr) {
        return nullptr;
    }

    return env->NewStringUTF(lang_str);
}

extern "C"
JNIEXPORT void JNICALL
Java_io_morgan_idonthaveyourtime_core_whisper_WhisperJni_requestAbort(
        JNIEnv * /* env */,
        jobject /* thiz */) {
    g_abort_requested.store(true, std::memory_order_relaxed);
}
