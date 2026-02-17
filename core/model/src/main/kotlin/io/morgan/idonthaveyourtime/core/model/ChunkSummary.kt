package io.morgan.idonthaveyourtime.core.model

data class ChunkSummary(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val bulletsText: String,
)
