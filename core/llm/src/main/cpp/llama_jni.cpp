#include <jni.h>

#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

#include "llama.h"

namespace {

#define LOG_TAG "LlamaJni"
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

struct LlamaInstance {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    int32_t n_ctx = 0;

    std::atomic_bool abort_requested{false};
    std::mutex mutex;
};

bool AbortCallback(void * user_data) {
    auto * instance = static_cast<LlamaInstance *>(user_data);
    return instance != nullptr && instance->abort_requested.load(std::memory_order_relaxed);
}

std::string ApplyChatTemplate(
        const llama_model * model,
        const std::string & system,
        const std::string & user) {
    const char * tmpl = llama_model_chat_template(model, /* name */ nullptr);
    if (tmpl == nullptr) {
        return system + "\n\n" + user;
    }

    llama_chat_message messages[2];
    messages[0] = {"system", system.c_str()};
    messages[1] = {"user", user.c_str()};

    const int32_t initial_size = static_cast<int32_t>(2 * (system.size() + user.size()) + 256);
    std::string buffer;
    buffer.resize(initial_size);

    int32_t written = llama_chat_apply_template(
            tmpl,
            messages,
            /* n_msg */ 2,
            /* add_ass */ true,
            buffer.data(),
            static_cast<int32_t>(buffer.size()));

    if (written <= 0) {
        return system + "\n\n" + user;
    }

    if (written > static_cast<int32_t>(buffer.size())) {
        buffer.resize(written + 1);
        written = llama_chat_apply_template(
                tmpl,
                messages,
                /* n_msg */ 2,
                /* add_ass */ true,
                buffer.data(),
                static_cast<int32_t>(buffer.size()));
        if (written <= 0) {
            return system + "\n\n" + user;
        }
    }

    buffer.resize(written);
    return buffer;
}

std::vector<llama_token> Tokenize(const llama_vocab * vocab, const std::string & text) {
    if (text.empty()) {
        return {};
    }

    int32_t count = llama_tokenize(
            vocab,
            text.c_str(),
            static_cast<int32_t>(text.size()),
            /* tokens */ nullptr,
            /* n_tokens_max */ 0,
            /* add_special */ true,
            /* parse_special */ true);

    if (count == INT32_MIN) {
        throw std::runtime_error("Tokenization overflow");
    }

    if (count < 0) {
        count = -count;
    }

    std::vector<llama_token> tokens(static_cast<size_t>(count));
    const int32_t written = llama_tokenize(
            vocab,
            text.c_str(),
            static_cast<int32_t>(text.size()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            /* add_special */ true,
            /* parse_special */ true);

    if (written < 0) {
        throw std::runtime_error("Tokenization failed");
    }

    tokens.resize(static_cast<size_t>(written));
    return tokens;
}

std::string TokenToPiece(const llama_vocab * vocab, llama_token token) {
    std::string buffer;
    buffer.resize(64);

    int32_t written = llama_token_to_piece(
            vocab,
            token,
            buffer.data(),
            static_cast<int32_t>(buffer.size()),
            /* lstrip */ 0,
            /* special */ false);

    if (written < 0) {
        buffer.resize(static_cast<size_t>(-written) + 1);
        written = llama_token_to_piece(
                vocab,
                token,
                buffer.data(),
                static_cast<int32_t>(buffer.size()),
                /* lstrip */ 0,
                /* special */ false);
    }

    if (written <= 0) {
        return "";
    }

    buffer.resize(static_cast<size_t>(written));
    return buffer;
}

void DecodePrompt(llama_context * ctx, const std::vector<llama_token> & tokens) {
    const uint32_t n_batch = llama_n_batch(ctx);
    size_t cursor = 0;
    while (cursor < tokens.size()) {
        const size_t remaining = tokens.size() - cursor;
        const size_t chunk = std::min<size_t>(remaining, n_batch > 0 ? n_batch : 256);

        llama_batch batch = llama_batch_get_one(
                const_cast<llama_token *>(tokens.data() + cursor),
                static_cast<int32_t>(chunk));

        const int32_t rc = llama_decode(ctx, batch);
        if (rc != 0) {
            throw std::runtime_error("llama_decode failed while processing prompt (rc=" + std::to_string(rc) + ")");
        }

        cursor += chunk;
    }
}

}  // namespace

extern "C"
JNIEXPORT jlong JNICALL
Java_io_morgan_idonthaveyourtime_core_llm_LlamaJni_initContext(
        JNIEnv * env,
        jobject /* thiz */,
        jstring model_path,
        jint n_ctx,
        jint n_threads) {
    const std::string model_path_string = JStringToString(env, model_path);
    if (model_path_string.empty()) {
        ThrowJavaException(env, "java/lang/IllegalArgumentException", "Model path is required");
        return 0;
    }

    auto * instance = new (std::nothrow) LlamaInstance();
    if (instance == nullptr) {
        ThrowJavaException(env, "java/lang/OutOfMemoryError", "Unable to allocate native context");
        return 0;
    }

    try {
        static std::once_flag backend_once;
        std::call_once(backend_once, []() {
            llama_backend_init();
            LOGI("llama_backend_init done");
        });

        const int32_t resolved_threads = std::max(1, static_cast<int32_t>(n_threads));
        const int32_t resolved_ctx = std::max(256, static_cast<int32_t>(n_ctx));

        llama_model_params mparams = llama_model_default_params();
        mparams.n_gpu_layers = 0;
        mparams.use_mmap = true;
        mparams.use_mlock = false;

        instance->model = llama_model_load_from_file(model_path_string.c_str(), mparams);
        if (instance->model == nullptr) {
            throw std::runtime_error("Unable to load model");
        }

        llama_context_params cparams = llama_context_default_params();
        cparams.n_ctx = static_cast<uint32_t>(resolved_ctx);
        cparams.n_batch = 512;
        cparams.n_ubatch = 512;
        cparams.n_seq_max = 1;
        cparams.n_threads = resolved_threads;
        cparams.n_threads_batch = resolved_threads;
        cparams.abort_callback = AbortCallback;
        cparams.abort_callback_data = instance;

        instance->ctx = llama_init_from_model(instance->model, cparams);
        if (instance->ctx == nullptr) {
            throw std::runtime_error("Unable to create context");
        }

        llama_set_abort_callback(instance->ctx, AbortCallback, instance);

        instance->vocab = llama_model_get_vocab(instance->model);
        instance->n_ctx = static_cast<int32_t>(llama_n_ctx(instance->ctx));

        char desc[256];
        llama_model_desc(instance->model, desc, sizeof(desc));
        LOGI(
                "Model loaded desc=%s n_ctx=%d threads=%d",
                desc,
                instance->n_ctx,
                resolved_threads);

        return reinterpret_cast<jlong>(instance);
    } catch (const std::exception & e) {
        LOGE("initContext failed: %s", e.what());
        if (instance->ctx != nullptr) {
            llama_free(instance->ctx);
            instance->ctx = nullptr;
        }
        if (instance->model != nullptr) {
            llama_model_free(instance->model);
            instance->model = nullptr;
        }
        delete instance;
        ThrowJavaException(env, "java/lang/IllegalStateException", e.what());
        return 0;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_io_morgan_idonthaveyourtime_core_llm_LlamaJni_freeContext(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong context_ptr) {
    auto * instance = reinterpret_cast<LlamaInstance *>(context_ptr);
    if (instance == nullptr) {
        return;
    }

    std::lock_guard<std::mutex> lock(instance->mutex);
    if (instance->ctx != nullptr) {
        llama_free(instance->ctx);
        instance->ctx = nullptr;
    }
    if (instance->model != nullptr) {
        llama_model_free(instance->model);
        instance->model = nullptr;
    }

    delete instance;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_io_morgan_idonthaveyourtime_core_llm_LlamaJni_generateChat(
        JNIEnv * env,
        jobject /* thiz */,
        jlong context_ptr,
        jstring system_prompt,
        jstring user_prompt,
        jint max_tokens,
        jfloat temperature,
        jfloat top_p,
        jint top_k) {
    auto * instance = reinterpret_cast<LlamaInstance *>(context_ptr);
    if (instance == nullptr || instance->ctx == nullptr || instance->model == nullptr || instance->vocab == nullptr) {
        ThrowJavaException(env, "java/lang/IllegalStateException", "Context is null");
        return nullptr;
    }

    const std::string system_string = JStringToString(env, system_prompt);
    const std::string user_string = JStringToString(env, user_prompt);

    const int32_t resolved_max_tokens = std::clamp(static_cast<int32_t>(max_tokens), 1, 4096);
    const float resolved_temp = std::clamp(static_cast<float>(temperature), 0.0f, 2.0f);
    const float resolved_top_p = std::clamp(static_cast<float>(top_p), 0.0f, 1.0f);
    const int32_t resolved_top_k = std::clamp(static_cast<int32_t>(top_k), 0, 200);

    std::lock_guard<std::mutex> lock(instance->mutex);
    instance->abort_requested.store(false, std::memory_order_relaxed);

    try {
        llama_memory_clear(llama_get_memory(instance->ctx), /* data */ true);

        const std::string prompt = ApplyChatTemplate(instance->model, system_string, user_string);
        std::vector<llama_token> prompt_tokens = Tokenize(instance->vocab, prompt);

        // Keep some room for generation.
        const int32_t max_prompt_tokens = std::max(64, instance->n_ctx - resolved_max_tokens - 8);
        if (static_cast<int32_t>(prompt_tokens.size()) > max_prompt_tokens) {
            LOGW(
                    "Prompt too long tokens=%d limit=%d; truncating from the front",
                    static_cast<int>(prompt_tokens.size()),
                    max_prompt_tokens);
            prompt_tokens.erase(
                    prompt_tokens.begin(),
                    prompt_tokens.begin() + (prompt_tokens.size() - static_cast<size_t>(max_prompt_tokens)));
        }

        DecodePrompt(instance->ctx, prompt_tokens);

        llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
        llama_sampler * sampler = llama_sampler_chain_init(sparams);
        if (sampler == nullptr) {
            throw std::runtime_error("Unable to create sampler");
        }

        // Typical "factual-ish" decoding chain.
        if (resolved_top_k > 0) {
            llama_sampler_chain_add(sampler, llama_sampler_init_top_k(resolved_top_k));
        }
        if (resolved_top_p > 0.0f && resolved_top_p < 1.0f) {
            llama_sampler_chain_add(sampler, llama_sampler_init_top_p(resolved_top_p, /* min_keep */ 1));
        }
        if (resolved_temp > 0.0f) {
            llama_sampler_chain_add(sampler, llama_sampler_init_temp(resolved_temp));
        }
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(/* seed */ 0));

        std::string output;
        output.reserve(static_cast<size_t>(resolved_max_tokens) * 6);

        for (int32_t i = 0; i < resolved_max_tokens; i++) {
            const llama_token token = llama_sampler_sample(sampler, instance->ctx, /* idx */ -1);
            llama_sampler_accept(sampler, token);

            if (llama_vocab_is_eog(instance->vocab, token)) {
                break;
            }

            output += TokenToPiece(instance->vocab, token);

            llama_batch batch = llama_batch_get_one(const_cast<llama_token *>(&token), 1);
            const int32_t rc = llama_decode(instance->ctx, batch);
            if (rc == 2 || instance->abort_requested.load(std::memory_order_relaxed)) {
                llama_sampler_free(sampler);
                throw std::runtime_error("aborted");
            }
            if (rc != 0) {
                llama_sampler_free(sampler);
                throw std::runtime_error("llama_decode failed while generating (rc=" + std::to_string(rc) + ")");
            }
        }

        llama_sampler_free(sampler);

        return env->NewStringUTF(output.c_str());
    } catch (const std::exception & e) {
        LOGE("generateChat failed: %s", e.what());
        ThrowJavaException(env, "java/lang/RuntimeException", e.what());
        return nullptr;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_io_morgan_idonthaveyourtime_core_llm_LlamaJni_requestAbort(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong context_ptr) {
    auto * instance = reinterpret_cast<LlamaInstance *>(context_ptr);
    if (instance == nullptr) {
        return;
    }
    instance->abort_requested.store(true, std::memory_order_relaxed);
}
