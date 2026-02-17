package io.morgan.idonthaveyourtime.core.domain.repository

import io.morgan.idonthaveyourtime.core.model.ChunkSummary
import io.morgan.idonthaveyourtime.core.model.ProcessingSession
import io.morgan.idonthaveyourtime.core.model.ProcessingStage
import io.morgan.idonthaveyourtime.core.model.Summary
import io.morgan.idonthaveyourtime.core.model.Transcript
import io.morgan.idonthaveyourtime.core.model.TranscriptSegment
import kotlinx.coroutines.flow.Flow

/**
 * Persists and exposes processing sessions and their derived artifacts (transcript, summaries, progress).
 *
 * Implementations must be safe to call from background workers and should be resilient to process death.
 */
interface SessionRepository {
    fun observeSession(sessionId: String): Flow<ProcessingSession?>
    fun observeRecentSessions(limit: Int): Flow<List<ProcessingSession>>
    fun observeChunkSummaries(sessionId: String): Flow<List<ChunkSummary>>

    suspend fun getSession(sessionId: String): ProcessingSession?
    suspend fun createSession(session: ProcessingSession, inputFilePath: String): Result<Unit>
    suspend fun updateStage(sessionId: String, stage: ProcessingStage, progress: Float = 0f): Result<Unit>
    suspend fun setWavFilePath(sessionId: String, wavFilePath: String): Result<Unit>
    suspend fun setTranscript(sessionId: String, transcript: Transcript): Result<Unit>
    suspend fun upsertTranscriptSegment(sessionId: String, index: Int, segment: TranscriptSegment): Result<Unit>
    suspend fun upsertChunkSummary(sessionId: String, chunk: ChunkSummary): Result<Unit>
    suspend fun setSummaryPartial(sessionId: String, summaryText: String): Result<Unit>
    suspend fun getInputFilePath(sessionId: String): String?
    suspend fun getWavFilePath(sessionId: String): String?
    suspend fun setSuccess(sessionId: String, transcript: Transcript, summary: Summary): Result<Unit>
    suspend fun setError(sessionId: String, errorCode: String, errorMessage: String): Result<Unit>
    suspend fun markCancelled(sessionId: String): Result<Unit>
}
