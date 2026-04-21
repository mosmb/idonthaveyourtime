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
    @ColumnInfo(name = "transcription_runtime")
    val transcriptionRuntime: String?,
    @ColumnInfo(name = "transcription_backend_name")
    val transcriptionBackendName: String?,
    @ColumnInfo(name = "transcription_model_file_name")
    val transcriptionModelFileName: String?,
    @ColumnInfo(name = "transcription_warm_start")
    val transcriptionWarmStart: Boolean?,
    @ColumnInfo(name = "transcription_model_load_ms")
    val transcriptionModelLoadMs: Long?,
    @ColumnInfo(name = "transcription_first_text_ms")
    val transcriptionFirstTextMs: Long?,
    @ColumnInfo(name = "transcription_total_ms")
    val transcriptionTotalMs: Long?,
    @ColumnInfo(name = "transcription_audio_duration_ms")
    val transcriptionAudioDurationMs: Long?,
    @ColumnInfo(name = "transcription_audio_seconds_per_wall_second")
    val transcriptionAudioSecondsPerWallSecond: Double?,
    @ColumnInfo(name = "transcription_fallback_reason")
    val transcriptionFallbackReason: String?,
    @ColumnInfo(name = "transcription_failure_reason")
    val transcriptionFailureReason: String?,
    @ColumnInfo(name = "transcription_device_label")
    val transcriptionDeviceLabel: String?,
    @ColumnInfo(name = "input_file_path")
    val inputFilePath: String,
    @ColumnInfo(name = "wav_file_path")
    val wavFilePath: String?,
)
