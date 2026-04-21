package io.morgan.idonthaveyourtime.core.model

data class ProcessingSession(
    val id: String,
    val createdAtEpochMs: Long,
    val sourceName: String?,
    val mimeType: String?,
    val durationMs: Long?,
    val stage: ProcessingStage,
    val progress: Float,
    val transcript: String?,
    val summary: String?,
    val languageCode: String?,
    val errorCode: String?,
    val errorMessage: String?,
    val transcriptionDiagnostics: SessionTranscriptionDiagnostics? = null,
)
