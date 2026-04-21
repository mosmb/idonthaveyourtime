package io.morgan.idonthaveyourtime.core.model

data class TranscriptionResult(
    val transcript: Transcript,
    val metrics: TranscriptionMetrics,
)
