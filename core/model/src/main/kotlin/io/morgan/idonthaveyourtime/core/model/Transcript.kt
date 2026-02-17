package io.morgan.idonthaveyourtime.core.model

data class Transcript(
    val text: String,
    val languageCode: String?,
    val segments: List<TranscriptSegment> = emptyList(),
)
