package io.morgan.idonthaveyourtime.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import io.morgan.idonthaveyourtime.core.domain.repository.AudioProcessingRepository
import io.morgan.idonthaveyourtime.core.domain.repository.ProcessingConfigRepository
import io.morgan.idonthaveyourtime.core.domain.repository.SessionRepository
import io.morgan.idonthaveyourtime.core.domain.repository.SummarizationRepository
import io.morgan.idonthaveyourtime.core.domain.repository.TranscriptionRepository
import io.morgan.idonthaveyourtime.core.model.AudioSegment
import io.morgan.idonthaveyourtime.core.model.ChunkSummary
import io.morgan.idonthaveyourtime.core.model.LanguageHint
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import io.morgan.idonthaveyourtime.core.model.ProcessingSession
import io.morgan.idonthaveyourtime.core.model.ProcessingStage
import io.morgan.idonthaveyourtime.core.model.SegmentationConfig
import io.morgan.idonthaveyourtime.core.model.Summary
import io.morgan.idonthaveyourtime.core.model.Transcript
import io.morgan.idonthaveyourtime.core.model.TranscriptSegment
import io.morgan.idonthaveyourtime.core.model.WavAudio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProcessSessionUseCaseTest {

    @Test
    fun `invoke completes session with summary`() = runTest {
        val repository = InMemorySessionRepository()
        repository.createSession(
            session = ProcessingSession(
                id = SESSION_ID,
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
            ),
            inputFilePath = "/tmp/import.ogg",
        ).getOrThrow()

        val useCase = ProcessSessionUseCase(
            sessionRepository = repository,
            audioProcessingRepository = FakeAudioProcessingRepository(
                segments = listOf(AudioSegment(startMs = 0L, endMs = 2_000L)),
            ),
            processingConfigRepository = FakeProcessingConfigRepository(),
            transcriptionRepository = FixedTranscriptionRepository(
                transcript = Transcript(text = "Transcribed text", languageCode = "fr"),
            ),
            summarizationRepository = FixedSummarizationRepository(
                mapResult = "- Summary bullet",
                reduceResult = Summary(text = "FINAL SUMMARY"),
            ),
        )

        val result = useCase(SESSION_ID)

        assertThat(result.isSuccess).isTrue()
        val processed = repository.getSession(SESSION_ID)
        assertThat(processed?.stage).isEqualTo(ProcessingStage.Success)
        assertThat(processed?.transcript).isEqualTo("Transcribed text")
        assertThat(processed?.summary).isEqualTo("FINAL SUMMARY")
        assertThat(repository.getWavFilePath(SESSION_ID)).isEqualTo("/tmp/converted.wav")
        assertThat(repository.chunkSummaries.value).isNotEmpty()
    }

    @Test
    fun `invoke returns failure when conversion fails`() = runTest {
        val repository = InMemorySessionRepository()
        repository.createSession(
            session = ProcessingSession(
                id = SESSION_ID,
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
            ),
            inputFilePath = "/tmp/import.ogg",
        ).getOrThrow()

        val useCase = ProcessSessionUseCase(
            sessionRepository = repository,
            audioProcessingRepository = FakeAudioProcessingRepository(
                segments = listOf(AudioSegment(startMs = 0L, endMs = 2_000L)),
                conversionError = IllegalStateException("decoder failure"),
            ),
            processingConfigRepository = FakeProcessingConfigRepository(),
            transcriptionRepository = FixedTranscriptionRepository(
                transcript = Transcript(text = "Transcribed text", languageCode = "fr"),
            ),
            summarizationRepository = FixedSummarizationRepository(
                mapResult = "- Summary bullet",
                reduceResult = Summary(text = "FINAL SUMMARY"),
            ),
        )

        val result = useCase(SESSION_ID)

        assertThat(result.isFailure).isTrue()
        assertThat(repository.getSession(SESSION_ID)?.stage).isEqualTo(ProcessingStage.Converting)
    }

    @Test
    fun `invoke persists transcript even when final reduce fails`() = runTest {
        val repository = InMemorySessionRepository()
        repository.createSession(
            session = ProcessingSession(
                id = SESSION_ID,
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
            ),
            inputFilePath = "/tmp/import.ogg",
        ).getOrThrow()

        val useCase = ProcessSessionUseCase(
            sessionRepository = repository,
            audioProcessingRepository = FakeAudioProcessingRepository(
                segments = listOf(AudioSegment(startMs = 0L, endMs = 2_000L)),
            ),
            processingConfigRepository = FakeProcessingConfigRepository(),
            transcriptionRepository = FixedTranscriptionRepository(
                transcript = Transcript(text = "Transcribed text", languageCode = "fr"),
            ),
            summarizationRepository = ReduceFailingSummarizationRepository(),
        )

        val result = useCase(SESSION_ID)

        assertThat(result.isFailure).isTrue()
        val failedSession = repository.getSession(SESSION_ID)
        assertThat(failedSession?.transcript).isEqualTo("Transcribed text")
        assertThat(failedSession?.languageCode).isEqualTo("fr")
        assertThat(failedSession?.summary).isEqualTo("- Summary bullet")
    }

    @Test
    fun `invoke maps summaries every configured segment window`() = runTest {
        val repository = InMemorySessionRepository()
        repository.createSession(
            session = ProcessingSession(
                id = SESSION_ID,
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
            ),
            inputFilePath = "/tmp/import.ogg",
        ).getOrThrow()

        val segments = listOf(
            AudioSegment(startMs = 0L, endMs = 1_000L),
            AudioSegment(startMs = 1_000L, endMs = 2_000L),
            AudioSegment(startMs = 2_000L, endMs = 3_000L),
            AudioSegment(startMs = 3_000L, endMs = 4_000L),
            AudioSegment(startMs = 4_000L, endMs = 5_000L),
        )

        val summarizer = CountingSummarizationRepository()

        val useCase = ProcessSessionUseCase(
            sessionRepository = repository,
            audioProcessingRepository = FakeAudioProcessingRepository(
                durationMs = 5_000L,
                segments = segments,
            ),
            processingConfigRepository = FakeProcessingConfigRepository(
                config = ProcessingConfig(mapEverySegments = 2),
            ),
            transcriptionRepository = SequencedTranscriptionRepository(
                texts = listOf("A", "B", "C", "D", "E"),
                languageCode = "en",
            ),
            summarizationRepository = summarizer,
        )

        val result = useCase(SESSION_ID)

        assertThat(result.isSuccess).isTrue()
        assertThat(summarizer.mapCalls).isEqualTo(3)
        assertThat(repository.chunkSummaries.value).hasSize(3)
    }

    @Test
    fun `invoke emits progressive summary updates from in-flight callbacks`() = runTest {
        val repository = InMemorySessionRepository()
        repository.createSession(
            session = ProcessingSession(
                id = SESSION_ID,
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
            ),
            inputFilePath = "/tmp/import.ogg",
        ).getOrThrow()

        val useCase = ProcessSessionUseCase(
            sessionRepository = repository,
            audioProcessingRepository = FakeAudioProcessingRepository(
                durationMs = 2_000L,
                segments = listOf(AudioSegment(startMs = 0L, endMs = 2_000L)),
            ),
            processingConfigRepository = FakeProcessingConfigRepository(),
            transcriptionRepository = FixedTranscriptionRepository(
                transcript = Transcript(text = "Transcribed text", languageCode = "en"),
            ),
            summarizationRepository = StreamingSummarizationRepository(),
        )

        val result = useCase(SESSION_ID)

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.partialSummaries).containsAtLeast(
            "- final map",
            "Title: partial reduce",
        )
        assertThat(repository.getSession(SESSION_ID)?.summary).isEqualTo("FINAL SUMMARY")
    }

    private class FakeAudioProcessingRepository(
        private val durationMs: Long = 2_000L,
        private val segments: List<AudioSegment>,
        private val conversionError: Throwable? = null,
    ) : AudioProcessingRepository {
        override suspend fun toMono16kWav(inputFilePath: String, sessionId: String): Result<WavAudio> =
            if (conversionError != null) {
                Result.failure(conversionError)
            } else {
                Result.success(
                    WavAudio(
                        filePath = "/tmp/converted.wav",
                        sampleRate = 16_000,
                        channels = 1,
                        durationMs = durationMs,
                    )
                )
            }

        override suspend fun segment16kMonoWav(
            wavFilePath: String,
            config: SegmentationConfig,
            onProgress: suspend (Float) -> Unit,
        ): Result<List<AudioSegment>> = runCatching {
            onProgress(1f)
            segments
        }

        override suspend fun read16kMonoFloats(wavFilePath: String, startMs: Long, endMs: Long): Result<FloatArray> =
            Result.success(floatArrayOf(0f, 0f, 0f))
    }

    private class FakeProcessingConfigRepository(
        private val config: ProcessingConfig = ProcessingConfig(),
    ) : ProcessingConfigRepository {
        override fun observeConfig(): Flow<ProcessingConfig> = flowOf(config)

        override suspend fun getConfig(): ProcessingConfig = config

        override suspend fun setConfig(config: ProcessingConfig): Result<Unit> = Result.success(Unit)
    }

    private class FixedTranscriptionRepository(
        private val transcript: Transcript,
    ) : TranscriptionRepository {
        override suspend fun transcribe(
            audioData: FloatArray,
            languageHint: LanguageHint,
            onProgress: suspend (Float) -> Unit,
        ): Result<Transcript> = runCatching {
            onProgress(1f)
            transcript
        }
    }

    private class SequencedTranscriptionRepository(
        private val texts: List<String>,
        private val languageCode: String,
    ) : TranscriptionRepository {
        private var index = 0

        override suspend fun transcribe(
            audioData: FloatArray,
            languageHint: LanguageHint,
            onProgress: suspend (Float) -> Unit,
        ): Result<Transcript> = runCatching {
            onProgress(1f)
            val nextText = texts.getOrNull(index) ?: ""
            index += 1
            Transcript(text = nextText, languageCode = languageCode)
        }
    }

    private class FixedSummarizationRepository(
        private val mapResult: String,
        private val reduceResult: Summary,
    ) : SummarizationRepository {
        override suspend fun prewarm(): Result<Unit> = Result.success(Unit)

        override suspend fun mapChunk(
            transcriptChunk: String,
            languageCode: String?,
            onPartialResult: (String) -> Unit,
        ): Result<String> =
            Result.success(mapResult)

        override suspend fun reduce(
            chunkBulletSummaries: List<String>,
            languageCode: String?,
            onPartialResult: (String) -> Unit,
        ): Result<Summary> =
            Result.success(reduceResult)
    }

    private class CountingSummarizationRepository : SummarizationRepository {
        var mapCalls = 0
            private set

        override suspend fun prewarm(): Result<Unit> = Result.success(Unit)

        override suspend fun mapChunk(
            transcriptChunk: String,
            languageCode: String?,
            onPartialResult: (String) -> Unit,
        ): Result<String> {
            mapCalls += 1
            return Result.success("- Map $mapCalls")
        }

        override suspend fun reduce(
            chunkBulletSummaries: List<String>,
            languageCode: String?,
            onPartialResult: (String) -> Unit,
        ): Result<Summary> =
            Result.success(Summary(text = "FINAL SUMMARY"))
    }

    private class ReduceFailingSummarizationRepository : SummarizationRepository {
        override suspend fun prewarm(): Result<Unit> = Result.success(Unit)

        override suspend fun mapChunk(
            transcriptChunk: String,
            languageCode: String?,
            onPartialResult: (String) -> Unit,
        ): Result<String> =
            Result.success("- Summary bullet")

        override suspend fun reduce(
            chunkBulletSummaries: List<String>,
            languageCode: String?,
            onPartialResult: (String) -> Unit,
        ): Result<Summary> =
            Result.failure(IllegalStateException("model unavailable"))
    }

    private class StreamingSummarizationRepository : SummarizationRepository {
        override suspend fun prewarm(): Result<Unit> = Result.success(Unit)

        override suspend fun mapChunk(
            transcriptChunk: String,
            languageCode: String?,
            onPartialResult: (String) -> Unit,
        ): Result<String> = runCatching {
            onPartialResult("- partial map")
            "- final map"
        }

        override suspend fun reduce(
            chunkBulletSummaries: List<String>,
            languageCode: String?,
            onPartialResult: (String) -> Unit,
        ): Result<Summary> = runCatching {
            onPartialResult("Title: partial reduce")
            Summary(text = "FINAL SUMMARY")
        }
    }

    private class InMemorySessionRepository : SessionRepository {
        private val sessions = MutableStateFlow<Map<String, ProcessingSession>>(emptyMap())
        private val inputPaths = mutableMapOf<String, String>()
        private val wavPaths = mutableMapOf<String, String>()

        val chunkSummaries = MutableStateFlow<List<ChunkSummary>>(emptyList())
        val partialSummaries = mutableListOf<String>()

        override fun observeSession(sessionId: String): Flow<ProcessingSession?> =
            sessions.map { state -> state[sessionId] }

        override fun observeRecentSessions(limit: Int): Flow<List<ProcessingSession>> =
            sessions.map { state ->
                state.values
                    .sortedByDescending { it.createdAtEpochMs }
                    .take(limit)
            }

        override fun observeChunkSummaries(sessionId: String): Flow<List<ChunkSummary>> = chunkSummaries

        override suspend fun getSession(sessionId: String): ProcessingSession? = sessions.value[sessionId]

        override suspend fun createSession(session: ProcessingSession, inputFilePath: String): Result<Unit> = runCatching {
            inputPaths[session.id] = inputFilePath
            sessions.value = sessions.value + (session.id to session)
        }

        override suspend fun updateStage(sessionId: String, stage: ProcessingStage, progress: Float): Result<Unit> = runCatching {
            val existing = sessions.value[sessionId] ?: return@runCatching
            sessions.value = sessions.value + (sessionId to existing.copy(stage = stage, progress = progress))
        }

        override suspend fun setWavFilePath(sessionId: String, wavFilePath: String): Result<Unit> = runCatching {
            wavPaths[sessionId] = wavFilePath
        }

        override suspend fun setTranscript(sessionId: String, transcript: Transcript): Result<Unit> = runCatching {
            val existing = sessions.value[sessionId] ?: return@runCatching
            sessions.value = sessions.value + (
                sessionId to existing.copy(
                    transcript = transcript.text,
                    languageCode = transcript.languageCode,
                )
            )
        }

        override suspend fun upsertTranscriptSegment(sessionId: String, index: Int, segment: TranscriptSegment): Result<Unit> =
            Result.success(Unit)

        override suspend fun upsertChunkSummary(sessionId: String, chunk: ChunkSummary): Result<Unit> = runCatching {
            chunkSummaries.value = chunkSummaries.value + chunk
        }

        override suspend fun setSummaryPartial(sessionId: String, summaryText: String): Result<Unit> = runCatching {
            val existing = sessions.value[sessionId] ?: return@runCatching
            partialSummaries += summaryText
            sessions.value = sessions.value + (sessionId to existing.copy(summary = summaryText))
        }

        override suspend fun getInputFilePath(sessionId: String): String? = inputPaths[sessionId]

        override suspend fun getWavFilePath(sessionId: String): String? = wavPaths[sessionId]

        override suspend fun setSuccess(sessionId: String, transcript: Transcript, summary: Summary): Result<Unit> = runCatching {
            val existing = sessions.value[sessionId] ?: return@runCatching
            sessions.value = sessions.value + (
                sessionId to existing.copy(
                    stage = ProcessingStage.Success,
                    progress = 1f,
                    transcript = transcript.text,
                    summary = summary.text,
                    languageCode = transcript.languageCode,
                    errorCode = null,
                    errorMessage = null,
                )
            )
        }

        override suspend fun setError(sessionId: String, errorCode: String, errorMessage: String): Result<Unit> = runCatching {
            val existing = sessions.value[sessionId] ?: return@runCatching
            sessions.value = sessions.value + (
                sessionId to existing.copy(
                    stage = ProcessingStage.Error,
                    errorCode = errorCode,
                    errorMessage = errorMessage,
                )
            )
        }

        override suspend fun markCancelled(sessionId: String): Result<Unit> = runCatching {
            val existing = sessions.value[sessionId] ?: return@runCatching
            sessions.value = sessions.value + (sessionId to existing.copy(stage = ProcessingStage.Cancelled))
        }
    }

    private companion object {
        const val SESSION_ID = "session-id"
    }
}
