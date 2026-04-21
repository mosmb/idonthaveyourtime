package io.morgan.idonthaveyourtime.core.model

data class SessionTranscriptionDiagnostics(
    val runtime: TranscriptionRuntime,
    val backendName: String?,
    val modelFileName: String?,
    val warmStart: Boolean,
    val modelLoadMs: Long?,
    val firstTextMs: Long?,
    val totalMs: Long,
    val audioDurationMs: Long,
    val audioSecondsPerWallSecond: Double?,
    val fallbackReason: String? = null,
    val failureReason: String? = null,
    val deviceLabel: String? = null,
)
