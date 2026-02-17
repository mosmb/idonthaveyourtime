package io.morgan.idonthaveyourtime

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.morgan.idonthaveyourtime.core.domain.repository.AudioProcessingRepository
import io.morgan.idonthaveyourtime.core.domain.repository.ProcessingConfigRepository
import io.morgan.idonthaveyourtime.core.domain.repository.SessionRepository
import io.morgan.idonthaveyourtime.core.domain.repository.SummarizationRepository
import io.morgan.idonthaveyourtime.core.domain.repository.TranscriptionRepository
import io.morgan.idonthaveyourtime.core.domain.usecase.ProcessSessionUseCase
import io.morgan.idonthaveyourtime.core.model.ChunkSummary
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import io.morgan.idonthaveyourtime.core.model.ProcessingSession
import io.morgan.idonthaveyourtime.core.model.ProcessingStage
import io.morgan.idonthaveyourtime.core.model.Summary
import io.morgan.idonthaveyourtime.core.model.Transcript
import io.morgan.idonthaveyourtime.core.model.TranscriptSegment
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class E2eProcessingChainInstrumentedTest {

    @Test
    fun audioFile_runsThroughPipeline_andProducesDeterministicResult() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val entryPoint = EntryPointAccessors.fromApplication(appContext, ProcessingEntryPoint::class.java)

        assumeModelAssetAvailable(
            appContext = appContext,
            modelName = "Whisper",
            fileNames = listOf(
                "ggml-base-q5_1.bin",
                "ggml-base.en-q5_1.bin",
                "ggml-base.en.bin",
                "ggml-base.bin",
            ),
        )

        val inputAudio = copyTestAssetToAppCache(
            appContext = appContext,
            assetName = "e2e_sample.m4a",
            outputFileName = "e2e_sample.m4a",
        )

        val sessionId = "e2e_session"
        val repository = InMemorySessionRepository(
            initialSession = ProcessingSession(
                id = sessionId,
                createdAtEpochMs = System.currentTimeMillis(),
                sourceName = "e2e_sample.m4a",
                mimeType = "audio/mpeg",
                durationMs = null,
                stage = ProcessingStage.Idle,
                progress = 0f,
                transcript = null,
                summary = null,
                languageCode = null,
                errorCode = null,
                errorMessage = null,
            ),
            inputFilePath = inputAudio.absolutePath,
        )

        val processingConfigRepository = entryPoint.processingConfigRepository()
        processingConfigRepository.setConfig(ProcessingConfig()).getOrThrow()

        val audioProcessingRepository = entryPoint.audioProcessingRepository()
        val transcriptionRepository = entryPoint.transcriptionRepository()
        val summarizationRepository = DeterministicSummarizationRepository()

        val useCase = ProcessSessionUseCase(
            sessionRepository = repository,
            audioProcessingRepository = audioProcessingRepository,
            processingConfigRepository = processingConfigRepository,
            transcriptionRepository = transcriptionRepository,
            summarizationRepository = summarizationRepository,
        )

        val result = withTimeout(10 * 60 * 1000L) {
            useCase(sessionId)
        }

        assertThat(result.isSuccess).isTrue()

        val completedSession = repository.requireSession(sessionId)
        val transcriptText = completedSession.transcript.orEmpty().trim()
        val summaryText = completedSession.summary.orEmpty().trim()
        val expectedSummary = summarizationRepository.expectedSummaryFor(transcriptText)

        assertThat(completedSession.stage).isEqualTo(ProcessingStage.Success)
        assertThat(transcriptText).isNotEmpty()
        assertThat(transcriptText.length).isAtLeast(8)

        assertThat(summaryText).isEqualTo(expectedSummary)
        assertThat(summaryText.length).isAtLeast(10)

        assertStageProgression(
            actual = repository.stageHistory,
            expected = listOf(
                ProcessingStage.Converting,
                ProcessingStage.Transcribing,
                ProcessingStage.Summarizing,
                ProcessingStage.Success,
            ),
        )
    }

    private fun assumeModelAssetAvailable(
        appContext: Context,
        modelName: String,
        fileNames: List<String>,
    ) {
        val candidateAssetPaths = fileNames.flatMap { fileName ->
            listOf(fileName, "models/$fileName")
        }

        val found = candidateAssetPaths.any { assetPath ->
            runCatching { appContext.assets.open(assetPath).use { } }.isSuccess
        }

        assumeTrue(
            "$modelName model missing. Download/import one of ${fileNames.joinToString()} into the app, or add it to app/src/debug/assets/models/ and run debug instrumentation tests again.",
            found,
        )
    }

    private fun copyTestAssetToAppCache(
        appContext: Context,
        assetName: String,
        outputFileName: String,
    ): File {
        val instrumentationAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val destination = File(appContext.cacheDir, outputFileName)
        instrumentationAssets.open(assetName).use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        return destination
    }

    private fun assertStageProgression(
        actual: List<ProcessingStage>,
        expected: List<ProcessingStage>,
    ) {
        var expectedIndex = 0
        actual.forEach { stage ->
            if (expectedIndex < expected.size && stage == expected[expectedIndex]) {
                expectedIndex += 1
            }
        }
        assertThat(expectedIndex).isEqualTo(expected.size)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface ProcessingEntryPoint {
    fun audioProcessingRepository(): AudioProcessingRepository
    fun transcriptionRepository(): TranscriptionRepository
    fun processingConfigRepository(): ProcessingConfigRepository
}

private class DeterministicSummarizationRepository : SummarizationRepository {

    private val chunks = mutableListOf<String>()

    override suspend fun mapChunk(transcriptChunk: String, languageCode: String?): Result<String> = runCatching {
        if (transcriptChunk.isNotBlank()) {
            chunks += transcriptChunk
        }
        "- Chunk summary"
    }

    override suspend fun reduce(chunkBulletSummaries: List<String>, languageCode: String?): Result<Summary> = runCatching {
        val transcriptText = chunks.joinToString(separator = "\n")
        Summary(text = expectedSummaryFor(transcriptText))
    }

    fun expectedSummaryFor(transcriptText: String): String {
        val normalizedTranscript = transcriptText
            .lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotEmpty() }
            .joinToString(separator = " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

        val excerpt = normalizedTranscript.take(200)
        return "Summary: $excerpt"
    }
}

private class InMemorySessionRepository(
    initialSession: ProcessingSession,
    inputFilePath: String,
) : SessionRepository {

    private val sessions = MutableStateFlow(mapOf(initialSession.id to initialSession))
    private val inputPaths = mutableMapOf(initialSession.id to inputFilePath)
    private val wavPaths = mutableMapOf<String, String>()
    private val chunkSummaries = MutableStateFlow<List<ChunkSummary>>(emptyList())

    val stageHistory = mutableListOf(initialSession.stage)

    override fun observeSession(sessionId: String): Flow<ProcessingSession?> =
        sessions.map { map -> map[sessionId] }

    override fun observeRecentSessions(limit: Int): Flow<List<ProcessingSession>> =
        sessions.map { map -> map.values.take(limit) }

    override fun observeChunkSummaries(sessionId: String): Flow<List<ChunkSummary>> = chunkSummaries

    override suspend fun getSession(sessionId: String): ProcessingSession? = sessions.value[sessionId]

    override suspend fun createSession(session: ProcessingSession, inputFilePath: String): Result<Unit> = runCatching {
        sessions.update { existing -> existing + (session.id to session) }
        inputPaths[session.id] = inputFilePath
    }

    override suspend fun updateStage(sessionId: String, stage: ProcessingStage, progress: Float): Result<Unit> = runCatching {
        mutate(sessionId) { current ->
            current.copy(stage = stage, progress = progress.coerceIn(0f, 1f))
        }
        stageHistory += stage
    }

    override suspend fun setWavFilePath(sessionId: String, wavFilePath: String): Result<Unit> = runCatching {
        ensureSessionExists(sessionId)
        wavPaths[sessionId] = wavFilePath
    }

    override suspend fun setTranscript(sessionId: String, transcript: Transcript): Result<Unit> = runCatching {
        mutate(sessionId) { current ->
            current.copy(
                transcript = transcript.text,
                languageCode = transcript.languageCode,
            )
        }
    }

    override suspend fun upsertTranscriptSegment(sessionId: String, index: Int, segment: TranscriptSegment): Result<Unit> =
        Result.success(Unit)

    override suspend fun upsertChunkSummary(sessionId: String, chunk: ChunkSummary): Result<Unit> = runCatching {
        chunkSummaries.value = chunkSummaries.value + chunk
    }

    override suspend fun setSummaryPartial(sessionId: String, summaryText: String): Result<Unit> = runCatching {
        mutate(sessionId) { current ->
            current.copy(summary = summaryText)
        }
    }

    override suspend fun getInputFilePath(sessionId: String): String? = inputPaths[sessionId]

    override suspend fun getWavFilePath(sessionId: String): String? = wavPaths[sessionId]

    override suspend fun setSuccess(sessionId: String, transcript: Transcript, summary: Summary): Result<Unit> = runCatching {
        mutate(sessionId) { current ->
            current.copy(
                stage = ProcessingStage.Success,
                progress = 1f,
                transcript = transcript.text,
                summary = summary.text,
                languageCode = transcript.languageCode,
                errorCode = null,
                errorMessage = null,
            )
        }
        stageHistory += ProcessingStage.Success
    }

    override suspend fun setError(sessionId: String, errorCode: String, errorMessage: String): Result<Unit> = runCatching {
        mutate(sessionId) { current ->
            current.copy(
                stage = ProcessingStage.Error,
                errorCode = errorCode,
                errorMessage = errorMessage,
            )
        }
        stageHistory += ProcessingStage.Error
    }

    override suspend fun markCancelled(sessionId: String): Result<Unit> = runCatching {
        mutate(sessionId) { current ->
            current.copy(stage = ProcessingStage.Cancelled)
        }
        stageHistory += ProcessingStage.Cancelled
    }

    fun requireSession(sessionId: String): ProcessingSession =
        sessions.value[sessionId] ?: error("Session not found: $sessionId")

    private fun ensureSessionExists(sessionId: String) {
        if (sessions.value[sessionId] == null) {
            error("Session not found: $sessionId")
        }
    }

    private fun mutate(
        sessionId: String,
        transform: (ProcessingSession) -> ProcessingSession,
    ) {
        sessions.update { currentMap ->
            val current = currentMap[sessionId] ?: error("Session not found: $sessionId")
            currentMap + (sessionId to transform(current))
        }
    }
}
