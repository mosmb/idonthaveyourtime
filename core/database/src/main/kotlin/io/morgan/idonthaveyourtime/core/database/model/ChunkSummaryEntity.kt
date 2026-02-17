package io.morgan.idonthaveyourtime.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "chunk_summaries",
    primaryKeys = ["session_id", "chunk_index"],
)
data class ChunkSummaryEntity(
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "chunk_index")
    val chunkIndex: Int,
    @ColumnInfo(name = "start_ms")
    val startMs: Long,
    @ColumnInfo(name = "end_ms")
    val endMs: Long,
    @ColumnInfo(name = "bullets_text")
    val bulletsText: String,
)

