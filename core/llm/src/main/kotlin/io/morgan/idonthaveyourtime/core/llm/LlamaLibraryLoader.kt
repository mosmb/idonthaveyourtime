package io.morgan.idonthaveyourtime.core.llm

import timber.log.Timber

internal object LlamaLibraryLoader {
    private const val TAG = "LlamaLibraryLoader"
    private const val LIB_NAME = "llm"

    private val loadResult: Result<Unit> = runCatching {
        Timber.tag(TAG).d("Loading native library %s", LIB_NAME)
        System.loadLibrary(LIB_NAME)
        Timber.tag(TAG).i("Loaded native library %s", LIB_NAME)
    }.onFailure { throwable ->
        Timber.tag(TAG).e(throwable, "Failed to load llama native libraries")
    }

    val isLoaded: Boolean get() = loadResult.isSuccess
    val loadError: Throwable? get() = loadResult.exceptionOrNull()

    fun ensureLoaded() {
        if (!isLoaded) {
            throw IllegalStateException("Failed to load llama native libraries: ${loadError?.message ?: "unknown error"}")
        }
    }
}

