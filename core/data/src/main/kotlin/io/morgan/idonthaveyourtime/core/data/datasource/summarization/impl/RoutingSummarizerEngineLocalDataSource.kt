package io.morgan.idonthaveyourtime.core.data.datasource.summarization.impl

import io.morgan.idonthaveyourtime.core.data.datasource.settings.ProcessingConfigLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.summarization.SummarizerEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.data.di.LiteRtLmSummarizerEngine
import io.morgan.idonthaveyourtime.core.data.di.MediaPipeSummarizerEngine
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import io.morgan.idonthaveyourtime.core.model.SummarizerEngineCapability
import io.morgan.idonthaveyourtime.core.model.SummarizerEngineProbeResult
import io.morgan.idonthaveyourtime.core.model.SummarizerModelFormat
import io.morgan.idonthaveyourtime.core.model.SummarizerRuntime
import io.morgan.idonthaveyourtime.core.model.Summary
import javax.inject.Inject
import timber.log.Timber

internal class RoutingSummarizerEngineLocalDataSource @Inject constructor(
    private val processingConfigDataSource: ProcessingConfigLocalDataSource,
    @LiteRtLmSummarizerEngine private val liteRtLmEngine: SummarizerEngineLocalDataSource,
    @MediaPipeSummarizerEngine private val mediaPipeEngine: SummarizerEngineLocalDataSource,
) : SummarizerEngineLocalDataSource {

    private val liteRtLmCapability by lazy { liteRtLmEngine.capability() }
    private val mediaPipeCapability by lazy { mediaPipeEngine.capability() }

    override fun capability(): SummarizerEngineCapability =
        SummarizerEngineCapability(
            runtime = null,
            supportedFormats = liteRtLmCapability.supportedFormats + mediaPipeCapability.supportedFormats,
            supportsStreaming = liteRtLmCapability.supportsStreaming || mediaPipeCapability.supportsStreaming,
            supportsAsyncGeneration = liteRtLmCapability.supportsAsyncGeneration ||
                mediaPipeCapability.supportsAsyncGeneration,
            supportsHardwareAcceleration = liteRtLmCapability.supportsHardwareAcceleration ||
                mediaPipeCapability.supportsHardwareAcceleration,
        )

    override suspend fun probe(): Result<SummarizerEngineProbeResult> =
        runCatching {
            resolveSelection().probe
        }

    override suspend fun prewarm(): Result<Unit> = runCatching {
        val selection = resolveSelection()
        logSelection(selection.probe, operation = "prewarm")
        val engine = selection.engine ?: error(selection.probe.failureReason ?: "No summarizer runtime selected.")
        engine.prewarm().getOrThrow()
    }

    override suspend fun mapChunk(
        transcriptChunk: String,
        languageCode: String?,
        onPartialResult: (String) -> Unit,
    ): Result<String> = runCatching {
        val selection = resolveSelection()
        logSelection(selection.probe, operation = "map")
        val engine = selection.engine ?: error(selection.probe.failureReason ?: "No summarizer runtime selected.")
        engine.mapChunk(
            transcriptChunk = transcriptChunk,
            languageCode = languageCode,
            onPartialResult = onPartialResult,
        ).getOrThrow()
    }

    override suspend fun reduce(
        chunkBulletSummaries: List<String>,
        languageCode: String?,
        onPartialResult: (String) -> Unit,
    ): Result<Summary> = runCatching {
        val selection = resolveSelection()
        logSelection(selection.probe, operation = "reduce")
        val engine = selection.engine ?: error(selection.probe.failureReason ?: "No summarizer runtime selected.")
        engine.reduce(
            chunkBulletSummaries = chunkBulletSummaries,
            languageCode = languageCode,
            onPartialResult = onPartialResult,
        ).getOrThrow()
    }

    private suspend fun resolveSelection(): ResolvedSummarizerEngine {
        val config = processingConfigDataSource.getConfig()
        val fileName = config.llmModelFileName.trim().ifEmpty { ProcessingConfig().llmModelFileName }
        val modelFormat = SummarizerModelFormat.fromFileName(fileName)

        if (modelFormat == null) {
            return ResolvedSummarizerEngine(
                engine = null,
                probe = SummarizerEngineProbeResult(
                    requestedRuntime = defaultRuntime(),
                    selectedRuntime = null,
                    modelFormat = null,
                    modelFileName = fileName,
                    supported = false,
                    supportsStreaming = false,
                    failureReason = "Unsupported summarizer model format for `$fileName`.",
                ),
            )
        }

        val selectedRuntime = runtimeFor(modelFormat)
        val engine = engineFor(selectedRuntime)
        val engineProbe = engine.probe().getOrElse { throwable ->
            SummarizerEngineProbeResult(
                requestedRuntime = selectedRuntime,
                selectedRuntime = selectedRuntime,
                modelFormat = modelFormat,
                modelFileName = fileName,
                supported = false,
                supportsStreaming = engine.capability().supportsStreaming,
                failureReason = throwable.message ?: "Probe failed for ${selectedRuntime.displayName}.",
            )
        }

        if (!engineProbe.supported) {
            return ResolvedSummarizerEngine(
                engine = null,
                probe = SummarizerEngineProbeResult(
                    requestedRuntime = selectedRuntime,
                    selectedRuntime = null,
                    modelFormat = modelFormat,
                    modelFileName = fileName,
                    supported = false,
                    supportsStreaming = false,
                    failureReason = engineProbe.failureReason ?: "No summarizer runtime could handle ${modelFormat.displayName}.",
                ),
            )
        }

        return ResolvedSummarizerEngine(
            engine = engine,
            probe = SummarizerEngineProbeResult(
                requestedRuntime = selectedRuntime,
                selectedRuntime = selectedRuntime,
                modelFormat = modelFormat,
                modelFileName = fileName,
                supported = true,
                supportsStreaming = engine.capability().supportsStreaming,
                fallbackReason = null,
            ),
        )
    }

    private fun runtimeFor(modelFormat: SummarizerModelFormat): SummarizerRuntime = when (modelFormat) {
        SummarizerModelFormat.LiteRtLm -> SummarizerRuntime.LiteRtLm
        SummarizerModelFormat.Task -> SummarizerRuntime.MediaPipeLlmInference
    }

    private fun engineFor(runtime: SummarizerRuntime): SummarizerEngineLocalDataSource = when (runtime) {
        SummarizerRuntime.LiteRtLm -> liteRtLmEngine
        SummarizerRuntime.MediaPipeLlmInference -> mediaPipeEngine
    }

    private fun defaultRuntime(): SummarizerRuntime = SummarizerRuntime.LiteRtLm

    private fun logSelection(probe: SummarizerEngineProbeResult, operation: String) {
        if (probe.supported) {
            Timber.tag(TAG).i(
                "Summarizer route op=%s requested=%s selected=%s format=%s streaming=%s fallback=%s",
                operation,
                probe.requestedRuntime.displayName,
                probe.selectedRuntime?.displayName,
                probe.modelFormat?.displayName,
                probe.supportsStreaming,
                probe.fallbackReason ?: "none",
            )
        } else {
            Timber.tag(TAG).w(
                "Summarizer route failed op=%s requested=%s format=%s file=%s reason=%s",
                operation,
                probe.requestedRuntime.displayName,
                probe.modelFormat?.displayName ?: "unknown",
                probe.modelFileName ?: "unknown",
                probe.failureReason ?: "unknown",
            )
        }
    }

    private data class ResolvedSummarizerEngine(
        val engine: SummarizerEngineLocalDataSource?,
        val probe: SummarizerEngineProbeResult,
    )

    private companion object {
        const val TAG = "SummarizerRouter"
    }
}
