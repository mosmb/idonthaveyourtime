package io.morgan.idonthaveyourtime.core.data.datasource.summarization.impl

import io.morgan.idonthaveyourtime.core.data.datasource.settings.ProcessingConfigLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.summarization.SummarizerBackend
import io.morgan.idonthaveyourtime.core.data.datasource.summarization.SummarizerEngineLocalDataSource
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
    @SummarizerBackend private val engines: Set<@JvmSuppressWildcards SummarizerEngineLocalDataSource>,
) : SummarizerEngineLocalDataSource {

    private val enginesByRuntime: Map<SummarizerRuntime, SummarizerEngineLocalDataSource> by lazy {
        engines.associateBy { engine -> engine.capability().runtime }
    }

    override fun capability(): SummarizerEngineCapability =
        SummarizerEngineCapability(
            runtime = SummarizerRuntime.Auto,
            supportedFormats = engines
                .flatMap { engine -> engine.capability().supportedFormats }
                .toSet(),
            supportsStreaming = engines.any { engine -> engine.capability().supportsStreaming },
            supportsAsyncGeneration = engines.any { engine -> engine.capability().supportsAsyncGeneration },
            supportsHardwareAcceleration = engines.any { engine -> engine.capability().supportsHardwareAcceleration },
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
                    requestedRuntime = config.summarizerRuntime,
                    selectedRuntime = null,
                    modelFormat = null,
                    modelFileName = fileName,
                    supported = false,
                    supportsStreaming = false,
                    failureReason = "Unsupported summarizer model format for `$fileName`.",
                ),
            )
        }

        val candidates = candidateRuntimes(
            requestedRuntime = config.summarizerRuntime,
            modelFormat = modelFormat,
        )

        var firstFailureReason: String? = null
        for (runtime in candidates) {
            val engine = enginesByRuntime[runtime] ?: continue
            val engineProbe = engine.probe().getOrElse { throwable ->
                SummarizerEngineProbeResult(
                    requestedRuntime = runtime,
                    selectedRuntime = runtime,
                    modelFormat = modelFormat,
                    modelFileName = fileName,
                    supported = false,
                    supportsStreaming = engine.capability().supportsStreaming,
                    failureReason = throwable.message ?: "Probe failed for ${runtime.displayName}.",
                )
            }

            if (!engineProbe.supported) {
                if (firstFailureReason == null) {
                    firstFailureReason = engineProbe.failureReason
                }
                continue
            }

            val fallbackReason = buildFallbackReason(
                requestedRuntime = config.summarizerRuntime,
                selectedRuntime = runtime,
                modelFormat = modelFormat,
            )

            return ResolvedSummarizerEngine(
                engine = engine,
                probe = SummarizerEngineProbeResult(
                    requestedRuntime = config.summarizerRuntime,
                    selectedRuntime = runtime,
                    modelFormat = modelFormat,
                    modelFileName = fileName,
                    supported = true,
                    supportsStreaming = engine.capability().supportsStreaming,
                    fallbackReason = fallbackReason,
                ),
            )
        }

        return ResolvedSummarizerEngine(
            engine = null,
            probe = SummarizerEngineProbeResult(
                requestedRuntime = config.summarizerRuntime,
                selectedRuntime = null,
                modelFormat = modelFormat,
                modelFileName = fileName,
                supported = false,
                supportsStreaming = false,
                failureReason = firstFailureReason
                    ?: "No summarizer runtime could handle ${modelFormat.displayName}.",
            ),
        )
    }

    private fun candidateRuntimes(
        requestedRuntime: SummarizerRuntime,
        modelFormat: SummarizerModelFormat,
    ): List<SummarizerRuntime> {
        val preferred = when (modelFormat) {
            SummarizerModelFormat.LiteRtLm -> SummarizerRuntime.LiteRtLm
            SummarizerModelFormat.Task -> SummarizerRuntime.MediaPipeLlmInference
        }

        return buildList {
            if (requestedRuntime != SummarizerRuntime.Auto) {
                add(requestedRuntime)
            }
            add(preferred)
            add(SummarizerRuntime.LiteRtLm)
            add(SummarizerRuntime.MediaPipeLlmInference)
        }.distinct()
    }

    private fun buildFallbackReason(
        requestedRuntime: SummarizerRuntime,
        selectedRuntime: SummarizerRuntime,
        modelFormat: SummarizerModelFormat,
    ): String? {
        val preferredRuntime = when (modelFormat) {
            SummarizerModelFormat.LiteRtLm -> SummarizerRuntime.LiteRtLm
            SummarizerModelFormat.Task -> SummarizerRuntime.MediaPipeLlmInference
        }

        return when {
            requestedRuntime == SummarizerRuntime.Auto && selectedRuntime != preferredRuntime ->
                "Auto preferred ${preferredRuntime.displayName} for ${modelFormat.displayName}, but used ${selectedRuntime.displayName}."

            requestedRuntime != SummarizerRuntime.Auto && selectedRuntime != requestedRuntime ->
                "Requested ${requestedRuntime.displayName}, but `${modelFormat.displayName}` selected ${selectedRuntime.displayName}."

            else -> null
        }
    }

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
