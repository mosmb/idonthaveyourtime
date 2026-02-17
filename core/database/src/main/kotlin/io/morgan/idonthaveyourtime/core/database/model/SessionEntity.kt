package io.morgan.idonthaveyourtime.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processing_sessions")
data class SessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "source_name")
    val sourceName: String?,
    @ColumnInfo(name = "mime_type")
    val mimeType: String?,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long?,
    @ColumnInfo(name = "stage")
    val stage: String,
    @ColumnInfo(name = "progress")
    val progress: Float,
    @ColumnInfo(name = "transcript")
    val transcript: String?,
    @ColumnInfo(name = "summary")
    val summary: String?,
    @ColumnInfo(name = "language_code")
    val languageCode: String?,
    @ColumnInfo(name = "error_code")
    val errorCode: String?,
    @ColumnInfo(name = "error_message")
    val errorMessage: String?,
    @ColumnInfo(name = "input_file_path")
    val inputFilePath: String,
    @ColumnInfo(name = "wav_file_path")
    val wavFilePath: String?,
)
