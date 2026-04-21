package io.morgan.idonthaveyourtime.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import io.morgan.idonthaveyourtime.core.domain.repository.ProcessingQueueRepository
import io.morgan.idonthaveyourtime.core.domain.repository.SessionRepository
import io.morgan.idonthaveyourtime.core.model.ChunkSummary
import io.morgan.idonthaveyourtime.core.model.ProcessingSession
import io.morgan.idonthaveyourtime.core.model.ProcessingStage
import io.morgan.idonthaveyourtime.core.model.SessionTranscriptionDiagnostics
import io.morgan.idonthaveyourtime.core.model.Summary
import io.morgan.idonthaveyourtime.core.model.Transcript
import io.morgan.idonthaveyourtime.core.model.TranscriptSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CancelSessionUseCaseTest {

    @Test
    fun `invoke cancels queue and marks session cancelled`() = runTest {
        val repository = FakeSessionRepository()
        val queue = FakeProcessingQueueRepository()

        val result = CancelSessionUseCase(
            processingQueueRepository = queue,
            sessionRepository = repository,
        )("session-1")

        assertThat(result.isSuccess).isTrue()
        assertThat(queue.cancelledSessionId).isEqualTo("session-1")
        assertThat(repository.cancelledSessionId).isEqualTo("session-1")
    }
}

private class FakeProcessingQueueRepository : ProcessingQueueRepository {
    var cancelledSessionId: String? = null

    override suspend fun enqueue(sessionId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun cancel(sessionId: String): Result<Unit> =
        runCatching {
            cancelledSessionId = sessionId
        }
}

private class FakeSessionRepository : SessionRepository {
    var cancelledSessionId: String? = null

    override fun observeSession(sessionId: String): Flow<ProcessingSession?> = emptyFlow()

    override fun observeRecentSessions(limit: Int): Flow<List<ProcessingSession>> = emptyFlow()

    override fun observeChunkSummaries(sessionId: String): Flow<List<ChunkSummary>> = emptyFlow()

    override suspend fun getSession(sessionId: String): ProcessingSession? = null

    override suspend fun createSession(session: ProcessingSession, inputFilePath: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun updateStage(sessionId: String, stage: ProcessingStage, progress: Float): Result<Unit> =
        Result.success(Unit)

    override suspend fun setWavFilePath(sessionId: String, wavFilePath: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun setTranscript(sessionId: String, transcript: Transcript): Result<Unit> =
        Result.success(Unit)

    override suspend fun setTranscriptionDiagnostics(
        sessionId: String,
        diagnostics: SessionTranscriptionDiagnostics,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun upsertTranscriptSegment(sessionId: String, index: Int, segment: TranscriptSegment): Result<Unit> =
        Result.success(Unit)

    override suspend fun upsertChunkSummary(sessionId: String, chunk: ChunkSummary): Result<Unit> =
        Result.success(Unit)

    override suspend fun setSummaryPartial(sessionId: String, summaryText: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun getInputFilePath(sessionId: String): String? = null

    override suspend fun getWavFilePath(sessionId: String): String? = null

    override suspend fun setSuccess(sessionId: String, transcript: Transcript, summary: Summary): Result<Unit> =
        Result.success(Unit)

    override suspend fun setError(sessionId: String, errorCode: String, errorMessage: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun markCancelled(sessionId: String): Result<Unit> = runCatching {
        cancelledSessionId = sessionId
    }
}
