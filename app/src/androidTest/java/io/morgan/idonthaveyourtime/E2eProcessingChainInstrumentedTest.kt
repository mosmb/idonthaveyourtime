package io.morgan.idonthaveyourtime

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import io.morgan.idonthaveyourtime.core.domain.usecase.ProcessSessionUseCase
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import io.morgan.idonthaveyourtime.core.model.ProcessingSession
import io.morgan.idonthaveyourtime.core.model.ProcessingStage
import io.morgan.idonthaveyourtime.core.model.TranscriptionRuntime
import io.morgan.idonthaveyourtime.core.model.Summary
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class E2eProcessingChainInstrumentedTest {

    @Test
    fun audioFile_runsThroughPipeline_andProducesDeterministicResult() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val entryPoint = EntryPointAccessors.fromApplication(appContext, ProcessingEntryPoint::class.java)
        val processingConfig = ProcessingConfig()

        assumeTranscriptionModelAvailable(
            appContext = appContext,
            fileName = processingConfig.transcriptionModelFileName,
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
        processingConfigRepository.setConfig(processingConfig).getOrThrow()

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
        val diagnostics = completedSession.transcriptionDiagnostics
        assertThat(diagnostics).isNotNull()
        assertThat(diagnostics?.runtime).isEqualTo(TranscriptionRuntime.GoogleAiEdgeLiteRtLm)
        assertThat(diagnostics?.modelFileName).isEqualTo(processingConfig.transcriptionModelFileName)
        assertThat(diagnostics?.fallbackReason).isNull()
        assertThat(diagnostics?.totalMs).isGreaterThan(0L)
        assertThat(diagnostics?.audioDurationMs).isGreaterThan(0L)
        assertThat(diagnostics?.audioSecondsPerWallSecond).isNotNull()
        assertThat(diagnostics?.backendName.orEmpty()).isNotEmpty()

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
}
