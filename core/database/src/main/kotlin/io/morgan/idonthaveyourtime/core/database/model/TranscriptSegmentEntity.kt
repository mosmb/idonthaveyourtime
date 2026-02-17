package io.morgan.idonthaveyourtime.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "transcript_segments",
    primaryKeys = ["session_id", "segment_index"],
)
data class TranscriptSegmentEntity(
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "segment_index")
    val segmentIndex: Int,
    @ColumnInfo(name = "start_ms")
    val startMs: Long,
    @ColumnInfo(name = "end_ms")
    val endMs: Long,
    @ColumnInfo(name = "text")
    val text: String,
)

