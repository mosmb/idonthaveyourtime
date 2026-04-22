package io.morgan.idonthaveyourtime.core.data.datasource.transcription.impl

import com.google.common.truth.Truth.assertThat
import io.morgan.idonthaveyourtime.core.data.datasource.settings.ProcessingConfigLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.transcription.TranscriptionEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.model.LanguageHint
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import io.morgan.idonthaveyourtime.core.model.TranscriptionEngineCapability
import io.morgan.idonthaveyourtime.core.model.TranscriptionEngineProbeResult
import io.morgan.idonthaveyourtime.core.model.TranscriptionMetrics
import io.morgan.idonthaveyourtime.core.model.TranscriptionModelFormat
import io.morgan.idonthaveyourtime.core.model.TranscriptionRequest
import io.morgan.idonthaveyourtime.core.model.TranscriptionResult
import io.morgan.idonthaveyourtime.core.model.TranscriptionRuntime
import io.morgan.idonthaveyourtime.core.model.Transcript
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoutingTranscriptionEngineLocalDataSourceTest {

    @Test
    fun `probe auto selects Google AI Edge for litertlm transcription models`() = runTest {
        val configDataSource = FakeProcessingConfigLocalDataSource(
            ProcessingConfig(
                transcriptionRuntime = TranscriptionRuntime.Auto,
                transcriptionModelFileName = "gemma-4-E2B-it.litertlm",
            ),
        )

        val selector = RoutingTranscriptionEngineLocalDataSource(
            processingConfigDataSource = configDataSource,
            engines = setOf(
                FakeTranscriptionEngine(
                    runtime = TranscriptionRuntime.GoogleAiEdgeLiteRtLm,
                    supportedFormats = setOf(TranscriptionModelFormat.LiteRtLm),
                ),
            ),
        )

        val probe = selector.probe().getOrThrow()

        assertThat(probe.supported).isTrue()
        assertThat(probe.selectedRuntime).isEqualTo(TranscriptionRuntime.GoogleAiEdgeLiteRtLm)
        assertThat(probe.modelFormat).isEqualTo(TranscriptionModelFormat.LiteRtLm)
        assertThat(probe.fallbackReason).isNull()
    }

    @Test
    fun `probe fails when Google transcription cannot open configured litertlm model`() = runTest {
        val configDataSource = FakeProcessingConfigLocalDataSource(
            ProcessingConfig(
                transcriptionRuntime = TranscriptionRuntime.Auto,
                transcriptionModelFileName = "gemma-4-E2B-it.litertlm",
            ),
        )

        val selector = RoutingTranscriptionEngineLocalDataSource(
            processingConfigDataSource = configDataSource,
            engines = setOf(
                FakeTranscriptionEngine(
                    runtime = TranscriptionRuntime.GoogleAiEdgeLiteRtLm,
                    supportedFormats = emptySet(),
                ),
            ),
        )

        val probe = selector.probe().getOrThrow()

        assertThat(probe.supported).isFalse()
        assertThat(probe.selectedRuntime).isNull()
        assertThat(probe.failureReason).contains("No transcription runtime could handle")
    }

    @Test
    fun `probe rejects whisper bin transcription models`() = runTest {
        val configDataSource = FakeProcessingConfigLocalDataSource(
            ProcessingConfig(
                transcriptionRuntime = TranscriptionRuntime.Auto,
                transcriptionModelFileName = "ggml-base-q5_1.bin",
            ),
        )

        val selector = RoutingTranscriptionEngineLocalDataSource(
            processingConfigDataSource = configDataSource,
            engines = setOf(
                FakeTranscriptionEngine(
                    runtime = TranscriptionRuntime.GoogleAiEdgeLiteRtLm,
                    supportedFormats = setOf(TranscriptionModelFormat.LiteRtLm),
                ),
            ),
        )

        val probe = selector.probe().getOrThrow()

        assertThat(probe.supported).isFalse()
        assertThat(probe.selectedRuntime).isNull()
        assertThat(probe.modelFormat).isNull()
        assertThat(probe.failureReason).contains("Unsupported transcription model format")
    }

    @Test
    fun `transcribe delegates to selected runtime using request`() = runTest {
        val configDataSource = FakeProcessingConfigLocalDataSource(
            ProcessingConfig(
                transcriptionRuntime = TranscriptionRuntime.Auto,
                transcriptionModelFileName = "gemma-4-E2B-it.litertlm",
            ),
        )
        val google = FakeTranscriptionEngine(
            runtime = TranscriptionRuntime.GoogleAiEdgeLiteRtLm,
            supportedFormats = setOf(TranscriptionModelFormat.LiteRtLm),
            transcriptText = "google transcript",
        )
        val selector = RoutingTranscriptionEngineLocalDataSource(
            processingConfigDataSource = configDataSource,
            engines = setOf(google),
        )

        val request = TranscriptionRequest(
            wavFilePath = "/tmp/sample.wav",
            startMs = 1_000L,
            endMs = 2_500L,
        )

        val result = selector.transcribe(
            request = request,
            languageHint = LanguageHint.Fixed("en"),
            onProgress = {},
            onPartialResult = {},
        ).getOrThrow()

        assertThat(result.transcript.text).isEqualTo("google transcript")
        assertThat(google.transcribeCalls).isEqualTo(1)
        assertThat(google.recordedRequests.single()).isEqualTo(request)
    }

    @Test
    fun `transcribe fails when no runtime can handle litertlm model`() = runTest {
        val configDataSource = FakeProcessingConfigLocalDataSource(
            ProcessingConfig(
                transcriptionRuntime = TranscriptionRuntime.Auto,
                transcriptionModelFileName = "gemma-4-E2B-it.litertlm",
            ),
        )
        val google = FakeTranscriptionEngine(
            runtime = TranscriptionRuntime.GoogleAiEdgeLiteRtLm,
            supportedFormats = emptySet(),
        )

        val selector = RoutingTranscriptionEngineLocalDataSource(
            processingConfigDataSource = configDataSource,
            engines = setOf(google),
        )

        val request = TranscriptionRequest(
            wavFilePath = "/tmp/sample.wav",
            startMs = 1_000L,
            endMs = 2_500L,
        )

        val result = selector.transcribe(
            request = request,
            languageHint = LanguageHint.Fixed("en"),
            onProgress = {},
            onPartialResult = {},
        )

        assertThat(result.isFailure).isTrue()
        assertThat(google.transcribeCalls).isEqualTo(0)
    }

    private class FakeProcessingConfigLocalDataSource(
        config: ProcessingConfig,
    ) : ProcessingConfigLocalDataSource {
        private val state = MutableStateFlow(config)

        override fun observeConfig(): Flow<ProcessingConfig> = state

        override suspend fun getConfig(): ProcessingConfig = state.value

        override suspend fun setConfig(config: ProcessingConfig): Result<Unit> = runCatching {
            state.value = config
        }
    }

    private class FakeTranscriptionEngine(
        private val runtime: TranscriptionRuntime,
        private val supportedFormats: Set<TranscriptionModelFormat>,
        private val transcriptText: String = runtime.displayName,
    ) : TranscriptionEngineLocalDataSource {
        var transcribeCalls = 0
            private set

        val recordedRequests = mutableListOf<TranscriptionRequest>()

        override fun capability(): TranscriptionEngineCapability =
            TranscriptionEngineCapability(
                runtime = runtime,
                supportedFormats = supportedFormats,
                supportsStreaming = runtime == TranscriptionRuntime.GoogleAiEdgeLiteRtLm,
                supportsAsyncGeneration = runtime == TranscriptionRuntime.GoogleAiEdgeLiteRtLm,
                supportsHardwareAcceleration = true,
            )

        override suspend fun probe(): Result<TranscriptionEngineProbeResult> = Result.success(
            TranscriptionEngineProbeResult(
                requestedRuntime = runtime,
                selectedRuntime = runtime,
                modelFormat = supportedFormats.firstOrNull(),
                modelFileName = "fake-model",
                supported = supportedFormats.isNotEmpty(),
                supportsStreaming = capability().supportsStreaming,
            ),
        )

        override suspend fun transcribe(
            request: TranscriptionRequest,
            languageHint: LanguageHint,
            onProgress: suspend (Float) -> Unit,
            onPartialResult: suspend (String) -> Unit,
        ): Result<TranscriptionResult> = runCatching {
            transcribeCalls += 1
            recordedRequests += request
            onProgress(1f)
            onPartialResult(transcriptText)
            TranscriptionResult(
                transcript = Transcript(
                    text = transcriptText,
                    languageCode = when (languageHint) {
                        LanguageHint.Auto -> null
                        is LanguageHint.Fixed -> languageHint.languageCode
                    },
                ),
                metrics = TranscriptionMetrics(
                    runtime = runtime,
                    backendName = runtime.displayName,
                    modelFileName = "fake-model",
                    warmStart = true,
                    modelLoadMs = null,
                    firstTextMs = 0L,
                    totalMs = 0L,
                    audioDurationMs = (request.endMs - request.startMs).coerceAtLeast(0L),
                    audioSecondsPerWallSecond = null,
                ),
            )
        }
    }
}
