package io.morgan.idonthaveyourtime.core.model

data class TranscriptionEngineProbeResult(
    val requestedRuntime: TranscriptionRuntime,
    val selectedRuntime: TranscriptionRuntime?,
    val modelFormat: TranscriptionModelFormat?,
    val modelFileName: String?,
    val supported: Boolean,
    val supportsStreaming: Boolean,
    val fallbackReason: String? = null,
    val failureReason: String? = null,
)
