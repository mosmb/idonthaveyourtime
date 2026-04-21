package io.morgan.idonthaveyourtime.core.data.repository

import com.google.common.truth.Truth.assertThat
import io.morgan.idonthaveyourtime.core.database.dao.ChunkSummaryDao
import io.morgan.idonthaveyourtime.core.database.dao.SessionDao
import io.morgan.idonthaveyourtime.core.database.dao.TranscriptSegmentDao
import io.morgan.idonthaveyourtime.core.database.model.ChunkSummaryEntity
import io.morgan.idonthaveyourtime.core.database.model.SessionEntity
import io.morgan.idonthaveyourtime.core.database.model.TranscriptSegmentEntity
import io.morgan.idonthaveyourtime.core.model.ProcessingSession
import io.morgan.idonthaveyourtime.core.model.ProcessingStage
import io.morgan.idonthaveyourtime.core.model.SessionTranscriptionDiagnostics
import io.morgan.idonthaveyourtime.core.model.Summary
import io.morgan.idonthaveyourtime.core.model.TranscriptionRuntime
import io.morgan.idonthaveyourtime.core.model.Transcript
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomSessionRepositoryTest {

    @Test
    fun `createSession persists and exposes initial state`() = runTest {
        val repository = RoomSessionRepository(FakeSessionDao(), FakeTranscriptSegmentDao(), FakeChunkSummaryDao())
        val session = baseSession(id = "s1")

        repository.createSession(
            session = session,
            inputFilePath = "/tmp/import.ogg",
        ).getOrThrow()

        val observed = repository.observeSession("s1").first()
        assertThat(observed?.stage).isEqualTo(ProcessingStage.Queued)
        assertThat(repository.getInputFilePath("s1")).isEqualTo("/tmp/import.ogg")
    }

    @Test
    fun `setSuccess stores transcript summary and language`() = runTest {
        val repository = RoomSessionRepository(FakeSessionDao(), FakeTranscriptSegmentDao(), FakeChunkSummaryDao())
        repository.createSession(baseSession(id = "s2"), "/tmp/import.ogg").getOrThrow()
        repository.updateStage("s2", ProcessingStage.Transcribing, 0.7f).getOrThrow()
        repository.setWavFilePath("s2", "/tmp/converted.wav").getOrThrow()

        repository.setSuccess(
            sessionId = "s2",
            transcript = Transcript(text = "Bonjour", languageCode = "fr"),
            summary = Summary(text = "- Point clé"),
        ).getOrThrow()

        val saved = repository.getSession("s2")
        assertThat(saved?.stage).isEqualTo(ProcessingStage.Success)
        assertThat(saved?.progress).isEqualTo(1f)
        assertThat(saved?.transcript).isEqualTo("Bonjour")
        assertThat(saved?.summary).isEqualTo("- Point clé")
        assertThat(saved?.languageCode).isEqualTo("fr")
        assertThat(repository.getWavFilePath("s2")).isEqualTo("/tmp/converted.wav")
    }

    @Test
    fun `setTranscript stores transcript and detected language before summary`() = runTest {
        val repository = RoomSessionRepository(FakeSessionDao(), FakeTranscriptSegmentDao(), FakeChunkSummaryDao())
        repository.createSession(baseSession(id = "s3"), "/tmp/import.ogg").getOrThrow()

        repository.setTranscript(
            sessionId = "s3",
            transcript = Transcript(text = "Partial transcript", languageCode = "en"),
        ).getOrThrow()

        val saved = repository.getSession("s3")
        assertThat(saved?.transcript).isEqualTo("Partial transcript")
        assertThat(saved?.languageCode).isEqualTo("en")
        assertThat(saved?.summary).isNull()
    }

    @Test
    fun `setTranscriptionDiagnostics stores and exposes diagnostics`() = runTest {
        val repository = RoomSessionRepository(FakeSessionDao(), FakeTranscriptSegmentDao(), FakeChunkSummaryDao())
        repository.createSession(baseSession(id = "s4"), "/tmp/import.ogg").getOrThrow()

        repository.setTranscriptionDiagnostics(
            sessionId = "s4",
            diagnostics = baseDiagnostics(),
        ).getOrThrow()

        val saved = repository.getSession("s4")
        assertThat(saved?.transcriptionDiagnostics).isEqualTo(baseDiagnostics())
    }

    @Test
    fun `setError preserves existing transcription diagnostics`() = runTest {
        val repository = RoomSessionRepository(FakeSessionDao(), FakeTranscriptSegmentDao(), FakeChunkSummaryDao())
        repository.createSession(baseSession(id = "s5"), "/tmp/import.ogg").getOrThrow()
        repository.setTranscriptionDiagnostics("s5", baseDiagnostics()).getOrThrow()

        repository.setError(
            sessionId = "s5",
            errorCode = "TRANSCRIPTION_FAILED",
            errorMessage = "boom",
        ).getOrThrow()

        val saved = repository.getSession("s5")
        assertThat(saved?.stage).isEqualTo(ProcessingStage.Error)
        assertThat(saved?.transcriptionDiagnostics).isEqualTo(baseDiagnostics())
    }

    private fun baseSession(id: String) = ProcessingSession(
        id = id,
        createdAtEpochMs = 1L,
        sourceName = "voice.ogg",
        mimeType = "audio/ogg",
        durationMs = null,
        stage = ProcessingStage.Queued,
        progress = 0f,
        transcript = null,
        summary = null,
        languageCode = null,
        errorCode = null,
        errorMessage = null,
    )

    private fun baseDiagnostics() = SessionTranscriptionDiagnostics(
        runtime = TranscriptionRuntime.GoogleAiEdgeLiteRtLm,
        backendName = "GPU",
        modelFileName = "gemma-4-E2B-it.litertlm",
        warmStart = false,
        modelLoadMs = 120L,
        firstTextMs = 35L,
        totalMs = 640L,
        audioDurationMs = 4_000L,
        audioSecondsPerWallSecond = 6.25,
        fallbackReason = null,
        failureReason = null,
        deviceLabel = "Test Device",
    )
}

private class FakeSessionDao : SessionDao {
    private val sessions = MutableStateFlow<Map<String, SessionEntity>>(emptyMap())

    override fun observeById(sessionId: String): Flow<SessionEntity?> =
        sessions.map { entities -> entities[sessionId] }

    override fun observeRecent(limit: Int): Flow<List<SessionEntity>> =
        sessions.map { entities ->
            entities.values
                .sortedByDescending { it.createdAtEpochMs }
                .take(limit)
        }

    override suspend fun getById(sessionId: String): SessionEntity? = sessions.value[sessionId]

    override suspend fun upsert(entity: SessionEntity) {
        sessions.value = sessions.value + (entity.id to entity)
    }

    override suspend fun updateStage(sessionId: String, stage: String, progress: Float) {
        val existing = sessions.value[sessionId] ?: return
        sessions.value = sessions.value + (
            sessionId to existing.copy(
                stage = stage,
                progress = progress,
                errorCode = null,
                errorMessage = null,
            )
        )
    }

    override suspend fun updateWavPath(sessionId: String, wavFilePath: String) {
        val existing = sessions.value[sessionId] ?: return
        sessions.value = sessions.value + (sessionId to existing.copy(wavFilePath = wavFilePath))
    }

    override suspend fun updateTranscript(sessionId: String, transcript: String, languageCode: String?) {
        val existing = sessions.value[sessionId] ?: return
        sessions.value = sessions.value + (
            sessionId to existing.copy(
                transcript = transcript,
                languageCode = languageCode,
            )
        )
    }

    override suspend fun updateTranscriptionDiagnostics(
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
    ) {
        val existing = sessions.value[sessionId] ?: return
        sessions.value = sessions.value + (
            sessionId to existing.copy(
                transcriptionRuntime = runtime,
                transcriptionBackendName = backendName,
                transcriptionModelFileName = modelFileName,
                transcriptionWarmStart = warmStart,
                transcriptionModelLoadMs = modelLoadMs,
                transcriptionFirstTextMs = firstTextMs,
                transcriptionTotalMs = totalMs,
                transcriptionAudioDurationMs = audioDurationMs,
                transcriptionAudioSecondsPerWallSecond = audioSecondsPerWallSecond,
                transcriptionFallbackReason = fallbackReason,
                transcriptionFailureReason = failureReason,
                transcriptionDeviceLabel = deviceLabel,
            )
        )
    }

    override suspend fun updateSummary(sessionId: String, summary: String) {
        val existing = sessions.value[sessionId] ?: return
        sessions.value = sessions.value + (sessionId to existing.copy(summary = summary))
    }

    override suspend fun getInputFilePath(sessionId: String): String? = sessions.value[sessionId]?.inputFilePath

    override suspend fun getWavFilePath(sessionId: String): String? = sessions.value[sessionId]?.wavFilePath

    override suspend fun updateSuccess(
        sessionId: String,
        stage: String,
        transcript: String,
        summary: String,
        languageCode: String?,
    ) {
        val existing = sessions.value[sessionId] ?: return
        sessions.value = sessions.value + (
            sessionId to existing.copy(
                stage = stage,
                progress = 1f,
                transcript = transcript,
                summary = summary,
                languageCode = languageCode,
                errorCode = null,
                errorMessage = null,
            )
        )
    }

    override suspend fun updateError(
        sessionId: String,
        stage: String,
        errorCode: String,
        errorMessage: String,
    ) {
        val existing = sessions.value[sessionId] ?: return
        sessions.value = sessions.value + (
            sessionId to existing.copy(
                stage = stage,
                errorCode = errorCode,
                errorMessage = errorMessage,
            )
        )
    }
}

private class FakeTranscriptSegmentDao : TranscriptSegmentDao {
    override fun observeBySession(sessionId: String): Flow<List<TranscriptSegmentEntity>> = emptyFlow()

    override suspend fun upsert(entity: TranscriptSegmentEntity) = Unit
}

private class FakeChunkSummaryDao : ChunkSummaryDao {
    override fun observeBySession(sessionId: String): Flow<List<ChunkSummaryEntity>> = emptyFlow()

    override suspend fun upsert(entity: ChunkSummaryEntity) = Unit
}
