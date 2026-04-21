package io.morgan.idonthaveyourtime

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import io.morgan.idonthaveyourtime.core.domain.usecase.ProcessSessionUseCase
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import io.morgan.idonthaveyourtime.core.model.ProcessingSession
import io.morgan.idonthaveyourtime.core.model.ProcessingStage
import io.morgan.idonthaveyourtime.core.model.SessionTranscriptionDiagnostics
import io.morgan.idonthaveyourtime.core.model.TranscriptionRuntime
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class GoogleAiEdgeTranscriptionBenchmarkInstrumentedTest {

    @Test
    fun audioFile_recordsColdAndWarmGoogleAiEdgeDiagnostics() = runBlocking {
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
            outputFileName = "benchmark_sample.m4a",
        )
        entryPoint.processingConfigRepository().setConfig(processingConfig).getOrThrow()

        val coldSession = runProcessing(
            entryPoint = entryPoint,
            inputAudio = inputAudio,
            sessionId = "benchmark-cold",
        )
        val warmSession = runProcessing(
            entryPoint = entryPoint,
            inputAudio = inputAudio,
            sessionId = "benchmark-warm",
        )

        val coldDiagnostics = requireNotNull(coldSession.transcriptionDiagnostics)
        val warmDiagnostics = requireNotNull(warmSession.transcriptionDiagnostics)

        assertGoogleAiEdgeDiagnostics(coldDiagnostics, processingConfig)
        assertGoogleAiEdgeDiagnostics(warmDiagnostics, processingConfig)
        assertThat(warmDiagnostics.warmStart || warmDiagnostics.modelLoadMs == null).isTrue()

        Log.i(TAG, formatDiagnostics("cold", coldDiagnostics))
        Log.i(TAG, formatDiagnostics("warm", warmDiagnostics))
    }

    private suspend fun runProcessing(
        entryPoint: ProcessingEntryPoint,
        inputAudio: File,
        sessionId: String,
    ): ProcessingSession {
        val repository = InMemorySessionRepository(
            initialSession = ProcessingSession(
                id = sessionId,
                createdAtEpochMs = System.currentTimeMillis(),
                sourceName = inputAudio.name,
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

        val useCase = ProcessSessionUseCase(
            sessionRepository = repository,
            audioProcessingRepository = entryPoint.audioProcessingRepository(),
            processingConfigRepository = entryPoint.processingConfigRepository(),
            transcriptionRepository = entryPoint.transcriptionRepository(),
            summarizationRepository = DeterministicSummarizationRepository(),
        )

        val result = withTimeout(10 * 60 * 1000L) {
            useCase(sessionId)
        }

        assertThat(result.isSuccess).isTrue()
        return repository.requireSession(sessionId)
    }

    private fun assertGoogleAiEdgeDiagnostics(
        diagnostics: SessionTranscriptionDiagnostics,
        processingConfig: ProcessingConfig,
    ) {
        assertThat(diagnostics.runtime).isEqualTo(TranscriptionRuntime.GoogleAiEdgeLiteRtLm)
        assertThat(diagnostics.modelFileName).isEqualTo(processingConfig.transcriptionModelFileName)
        assertThat(diagnostics.backendName.orEmpty()).isNotEmpty()
        assertThat(diagnostics.totalMs).isGreaterThan(0L)
        assertThat(diagnostics.audioDurationMs).isGreaterThan(0L)
        assertThat(diagnostics.audioSecondsPerWallSecond).isNotNull()
        assertThat(diagnostics.deviceLabel.orEmpty()).isNotEmpty()
    }

    private fun formatDiagnostics(
        label: String,
        diagnostics: SessionTranscriptionDiagnostics,
    ): String = buildString {
        append("run=")
        append(label)
        append(" runtime=")
        append(diagnostics.runtime.name)
        append(" backend=")
        append(diagnostics.backendName)
        append(" model=")
        append(diagnostics.modelFileName)
        append(" warmStart=")
        append(diagnostics.warmStart)
        append(" modelLoadMs=")
        append(diagnostics.modelLoadMs)
        append(" firstTextMs=")
        append(diagnostics.firstTextMs)
        append(" totalMs=")
        append(diagnostics.totalMs)
        append(" audioDurationMs=")
        append(diagnostics.audioDurationMs)
        append(" audioSecondsPerWallSecond=")
        append(diagnostics.audioSecondsPerWallSecond)
        append(" device=")
        append(diagnostics.deviceLabel)
    }

    private companion object {
        const val TAG = "GoogleAiEdgeBenchmark"
    }
}
