package io.morgan.idonthaveyourtime.core.model

data class ImportedAudio(
    val sessionId: String,
    val cachedFilePath: String,
    val sourceName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
)
