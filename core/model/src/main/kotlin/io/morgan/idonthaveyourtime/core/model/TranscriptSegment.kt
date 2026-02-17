package io.morgan.idonthaveyourtime.core.model

data class TranscriptSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)
