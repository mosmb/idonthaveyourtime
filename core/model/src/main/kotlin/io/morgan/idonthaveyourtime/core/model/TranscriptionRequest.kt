package io.morgan.idonthaveyourtime.core.model

data class TranscriptionRequest(
    val wavFilePath: String,
    val startMs: Long,
    val endMs: Long,
)
