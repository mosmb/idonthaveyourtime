package io.morgan.idonthaveyourtime.core.whisper

internal fun interface ProgressCb {
    fun onProgress(percent: Int)
}

internal object WhisperJni {
    init {
        WhisperLibraryLoader.ensureLoaded()
    }

    external fun initContext(modelPath: String): Long
    external fun freeContext(contextPtr: Long)
    external fun fullTranscribe(
        contextPtr: Long,
        numThreads: Int,
        audioData: FloatArray,
        language: String?,
        progressCb: ProgressCb?,
    )

    external fun getSegmentCount(contextPtr: Long): Int
    external fun getSegmentText(contextPtr: Long, index: Int): String

    external fun getDetectedLanguageCode(contextPtr: Long): String?
    external fun requestAbort()
}
