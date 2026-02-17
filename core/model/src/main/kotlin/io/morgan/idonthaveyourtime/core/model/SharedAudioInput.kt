package io.morgan.idonthaveyourtime.core.model

data class SharedAudioInput(
    val uriToken: String,
    val mimeType: String?,
    val displayName: String?,
    val sizeBytes: Long?,
)
