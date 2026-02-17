package io.morgan.idonthaveyourtime.core.model

data class WavAudio(
    val filePath: String,
    val sampleRate: Int = 16_000,
    val channels: Int = 1,
    val durationMs: Long? = null,
)
