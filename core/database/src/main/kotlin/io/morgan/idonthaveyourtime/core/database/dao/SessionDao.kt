package io.morgan.idonthaveyourtime.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.morgan.idonthaveyourtime.core.database.model.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM processing_sessions WHERE id = :sessionId LIMIT 1")
    fun observeById(sessionId: String): Flow<SessionEntity?>

    @Query("SELECT * FROM processing_sessions ORDER BY created_at_epoch_ms DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SessionEntity>>

    @Query("SELECT * FROM processing_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getById(sessionId: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SessionEntity)

    @Query("UPDATE processing_sessions SET stage = :stage, progress = :progress, error_code = NULL, error_message = NULL WHERE id = :sessionId")
    suspend fun updateStage(sessionId: String, stage: String, progress: Float)

    @Query("UPDATE processing_sessions SET wav_file_path = :wavFilePath WHERE id = :sessionId")
    suspend fun updateWavPath(sessionId: String, wavFilePath: String)

    @Query(
        """
        UPDATE processing_sessions
        SET transcript = :transcript,
            language_code = :languageCode
        WHERE id = :sessionId
        """
    )
    suspend fun updateTranscript(
        sessionId: String,
        transcript: String,
        languageCode: String?,
    )

    @Query(
        """
        UPDATE processing_sessions
        SET transcription_runtime = :runtime,
            transcription_backend_name = :backendName,
            transcription_model_file_name = :modelFileName,
            transcription_warm_start = :warmStart,
            transcription_model_load_ms = :modelLoadMs,
            transcription_first_text_ms = :firstTextMs,
            transcription_total_ms = :totalMs,
            transcription_audio_duration_ms = :audioDurationMs,
            transcription_audio_seconds_per_wall_second = :audioSecondsPerWallSecond,
            transcription_fallback_reason = :fallbackReason,
            transcription_failure_reason = :failureReason,
            transcription_device_label = :deviceLabel
        WHERE id = :sessionId
        """
    )
    suspend fun updateTranscriptionDiagnostics(
        sessionId: String,
        runtime: String,
        backendName: String?,
        modelFileName: String?,
        warmStart: Boolean,
        modelLoadMs: Long?,
        firstTextMs: Long?,
        totalMs: Long,
        audioDurationMs: Long,
        audioSecondsPerWallSecond: Double?,
        fallbackReason: String?,
        failureReason: String?,
        deviceLabel: String?,
    )

    @Query("UPDATE processing_sessions SET summary = :summary WHERE id = :sessionId")
    suspend fun updateSummary(sessionId: String, summary: String)

    @Query("SELECT input_file_path FROM processing_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getInputFilePath(sessionId: String): String?

    @Query("SELECT wav_file_path FROM processing_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getWavFilePath(sessionId: String): String?

    @Query(
        """
        UPDATE processing_sessions
        SET stage = :stage,
            progress = 1.0,
            transcript = :transcript,
            summary = :summary,
            language_code = :languageCode,
            error_code = NULL,
            error_message = NULL
        WHERE id = :sessionId
        """
    )
    suspend fun updateSuccess(
        sessionId: String,
        stage: String,
        transcript: String,
        summary: String,
        languageCode: String?,
    )

    @Query(
        """
        UPDATE processing_sessions
        SET stage = :stage,
            error_code = :errorCode,
            error_message = :errorMessage
        WHERE id = :sessionId
        """
    )
    suspend fun updateError(
        sessionId: String,
        stage: String,
        errorCode: String,
        errorMessage: String,
    )
}
