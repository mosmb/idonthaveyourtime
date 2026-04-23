package io.morgan.idonthaveyourtime.core.data.datasource.summarization.impl

import com.google.common.truth.Truth.assertThat
import io.morgan.idonthaveyourtime.core.data.datasource.settings.ProcessingConfigLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.summarization.SummarizerEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import io.morgan.idonthaveyourtime.core.model.SummarizerEngineCapability
import io.morgan.idonthaveyourtime.core.model.SummarizerEngineProbeResult
import io.morgan.idonthaveyourtime.core.model.SummarizerModelFormat
import io.morgan.idonthaveyourtime.core.model.SummarizerRuntime
import io.morgan.idonthaveyourtime.core.model.Summary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoutingSummarizerEngineLocalDataSourceTest {

    @Test
    fun `summarizer runtime contracts do not expose auto`() {
        assertThat(SummarizerRuntime.entries.map { it.name }).doesNotContain("Auto")
    }

    @Test
    fun `router capability does not claim a single runtime`() {
        val configDataSource = FakeProcessingConfigLocalDataSource(ProcessingConfig())

        val selector = RoutingSummarizerEngineLocalDataSource(
            processingConfigDataSource = configDataSource,
            liteRtLmEngine = FakeSummarizerEngine(
                processingConfigDataSource = configDataSource,
                runtime = SummarizerRuntime.LiteRtLm,
                supportedFormats = setOf(SummarizerModelFormat.LiteRtLm),
            ),
            mediaPipeEngine = FakeSummarizerEngine(
                processingConfigDataSource = configDataSource,
                runtime = SummarizerRuntime.MediaPipeLlmInference,
                supportedFormats = setOf(SummarizerModelFormat.Task),
            ),
        )

        val capability = selector.capability()

        assertThat(capability.runtime).isNull()
        assertThat(capability.supportedFormats)
            .containsExactly(SummarizerModelFormat.LiteRtLm, SummarizerModelFormat.Task)
    }

    @Test
    fun `probe selects LiteRT-LM for litertlm models even if MediaPipe engine claims it supports LiteRT-LM`() = runTest {
        val configDataSource = FakeProcessingConfigLocalDataSource(
            ProcessingConfig(
                llmModelFileName = "gemma-4-E2B-it.litertlm",
            ),
        )

        val selector = RoutingSummarizerEngineLocalDataSource(
            processingConfigDataSource = configDataSource,
            liteRtLmEngine = FakeSummarizerEngine(
                processingConfigDataSource = configDataSource,
                runtime = SummarizerRuntime.LiteRtLm,
                supportedFormats = setOf(SummarizerModelFormat.LiteRtLm),
            ),
            mediaPipeEngine = FakeSummarizerEngine(
                processingConfigDataSource = configDataSource,
                runtime = SummarizerRuntime.MediaPipeLlmInference,
                supportedFormats = setOf(SummarizerModelFormat.LiteRtLm, SummarizerModelFormat.Task),
            ),
        )

        val probe = selector.probe().getOrThrow()

        assertThat(probe.supported).isTrue()
        assertThat(probe.requestedRuntime).isEqualTo(SummarizerRuntime.LiteRtLm)
        assertThat(probe.selectedRuntime).isEqualTo(SummarizerRuntime.LiteRtLm)
        assertThat(probe.modelFormat).isEqualTo(SummarizerModelFormat.LiteRtLm)
    }

    @Test
    fun `probe selects MediaPipe for task models even if LiteRT-LM engine claims it supports task`() = runTest {
        val configDataSource = FakeProcessingConfigLocalDataSource(
            ProcessingConfig(
                llmModelFileName = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            ),
        )

        val selector = RoutingSummarizerEngineLocalDataSource(
            processingConfigDataSource = configDataSource,
            liteRtLmEngine = FakeSummarizerEngine(
                processingConfigDataSource = configDataSource,
                runtime = SummarizerRuntime.LiteRtLm,
                supportedFormats = setOf(SummarizerModelFormat.LiteRtLm, SummarizerModelFormat.Task),
            ),
            mediaPipeEngine = FakeSummarizerEngine(
                processingConfigDataSource = configDataSource,
                runtime = SummarizerRuntime.MediaPipeLlmInference,
                supportedFormats = setOf(SummarizerModelFormat.Task),
            ),
        )

        val probe = selector.probe().getOrThrow()

        assertThat(probe.supported).isTrue()
        assertThat(probe.requestedRuntime).isEqualTo(SummarizerRuntime.MediaPipeLlmInference)
        assertThat(probe.selectedRuntime).isEqualTo(SummarizerRuntime.MediaPipeLlmInference)
        assertThat(probe.modelFormat).isEqualTo(SummarizerModelFormat.Task)
    }

    @Test
    fun `probe selects LiteRT-LM for litertlm models`() = runTest {
        val configDataSource = FakeProcessingConfigLocalDataSource(
            ProcessingConfig(
                llmModelFileName = "gemma-4-E2B-it.litertlm",
            ),
        )

        val selector = RoutingSummarizerEngineLocalDataSource(
            processingConfigDataSource = configDataSource,
            liteRtLmEngine = FakeSummarizerEngine(
                configDataSource,
                SummarizerRuntime.LiteRtLm,
                setOf(SummarizerModelFormat.LiteRtLm),
            ),
            mediaPipeEngine = FakeSummarizerEngine(
                configDataSource,
                SummarizerRuntime.MediaPipeLlmInference,
                setOf(SummarizerModelFormat.Task),
            ),
        )

        val probe = selector.probe().getOrThrow()

        assertThat(probe.supported).isTrue()
        assertThat(probe.requestedRuntime).isEqualTo(SummarizerRuntime.LiteRtLm)
        assertThat(probe.selectedRuntime).isEqualTo(SummarizerRuntime.LiteRtLm)
        assertThat(probe.modelFormat).isEqualTo(SummarizerModelFormat.LiteRtLm)
        assertThat(probe.fallbackReason).isNull()
    }

    @Test
    fun `probe rejects unsupported model extensions`() = runTest {
        val configDataSource = FakeProcessingConfigLocalDataSource(
            ProcessingConfig(
                llmModelFileName = "summary.invalid",
            ),
        )

        val selector = RoutingSummarizerEngineLocalDataSource(
            processingConfigDataSource = configDataSource,
            liteRtLmEngine = FakeSummarizerEngine(
                configDataSource,
                SummarizerRuntime.LiteRtLm,
                setOf(SummarizerModelFormat.LiteRtLm),
            ),
            mediaPipeEngine = FakeSummarizerEngine(
                configDataSource,
                SummarizerRuntime.MediaPipeLlmInference,
                setOf(SummarizerModelFormat.Task),
            ),
        )

        val probe = selector.probe().getOrThrow()

        assertThat(probe.supported).isFalse()
        assertThat(probe.selectedRuntime).isNull()
        assertThat(probe.modelFormat).isNull()
        assertThat(probe.failureReason).contains("Unsupported summarizer model format")
    }

    @Test
    fun `mapChunk delegates to the runtime selected by file format`() = runTest {
        val configDataSource = FakeProcessingConfigLocalDataSource(
            ProcessingConfig(
                llmModelFileName = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            ),
        )
        val liteRt = FakeSummarizerEngine(
            processingConfigDataSource = configDataSource,
            runtime = SummarizerRuntime.LiteRtLm,
            supportedFormats = setOf(SummarizerModelFormat.LiteRtLm),
        )
        val mediaPipe = FakeSummarizerEngine(
            processingConfigDataSource = configDataSource,
            runtime = SummarizerRuntime.MediaPipeLlmInference,
            supportedFormats = setOf(SummarizerModelFormat.Task),
        )

        val selector = RoutingSummarizerEngineLocalDataSource(
            processingConfigDataSource = configDataSource,
            liteRtLmEngine = liteRt,
            mediaPipeEngine = mediaPipe,
        )

        val result = selector.mapChunk(
            transcriptChunk = "hello world",
            languageCode = "en",
        ).getOrThrow()

        assertThat(result).isEqualTo("MediaPipe LLM Inference-map")
        assertThat(mediaPipe.mapCalls).isEqualTo(1)
        assertThat(liteRt.mapCalls).isEqualTo(0)
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

    private class FakeSummarizerEngine(
        private val processingConfigDataSource: ProcessingConfigLocalDataSource,
        private val runtime: SummarizerRuntime,
        private val supportedFormats: Set<SummarizerModelFormat>,
    ) : SummarizerEngineLocalDataSource {
        var mapCalls = 0
            private set

        override fun capability(): SummarizerEngineCapability =
            SummarizerEngineCapability(
                runtime = runtime,
                supportedFormats = supportedFormats,
                supportsStreaming = true,
                supportsAsyncGeneration = true,
                supportsHardwareAcceleration = true,
            )

        override suspend fun probe(): Result<SummarizerEngineProbeResult> = runCatching {
            val fileName = processingConfigDataSource.getConfig().llmModelFileName
            val format = SummarizerModelFormat.fromFileName(fileName)
            val supported = format != null && supportedFormats.contains(format)
            SummarizerEngineProbeResult(
                requestedRuntime = runtime,
                selectedRuntime = runtime,
                modelFormat = format,
                modelFileName = fileName,
                supported = supported,
                supportsStreaming = capability().supportsStreaming,
                failureReason = if (supported) null else "${runtime.displayName} cannot open $fileName",
            )
        }

        override suspend fun prewarm(): Result<Unit> = Result.success(Unit)

        override suspend fun mapChunk(
            transcriptChunk: String,
            languageCode: String?,
            onPartialResult: (String) -> Unit,
        ): Result<String> = runCatching {
            mapCalls += 1
            "${runtime.displayName}-map"
        }

        override suspend fun reduce(
            chunkBulletSummaries: List<String>,
            languageCode: String?,
            onPartialResult: (String) -> Unit,
        ): Result<Summary> = Result.success(Summary(text = runtime.displayName))
    }
}
