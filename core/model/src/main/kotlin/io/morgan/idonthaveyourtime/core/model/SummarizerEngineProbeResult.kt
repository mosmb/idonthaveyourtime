package io.morgan.idonthaveyourtime.core.model

data class SummarizerEngineProbeResult(
    val requestedRuntime: SummarizerRuntime,
    val selectedRuntime: SummarizerRuntime?,
    val modelFormat: SummarizerModelFormat?,
    val modelFileName: String?,
    val supported: Boolean,
    val supportsStreaming: Boolean,
    val fallbackReason: String? = null,
    val failureReason: String? = null,
)
