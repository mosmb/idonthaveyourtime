package io.morgan.idonthaveyourtime.core.data.datasource.transcription.impl

import io.morgan.idonthaveyourtime.core.data.datasource.settings.ProcessingConfigLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.transcription.TranscriptionBackend
import io.morgan.idonthaveyourtime.core.data.datasource.transcription.TranscriptionEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.model.LanguageHint
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import io.morgan.idonthaveyourtime.core.model.TranscriptionEngineCapability
import io.morgan.idonthaveyourtime.core.model.TranscriptionEngineProbeResult
import io.morgan.idonthaveyourtime.core.model.TranscriptionModelFormat
import io.morgan.idonthaveyourtime.core.model.TranscriptionRequest
import io.morgan.idonthaveyourtime.core.model.TranscriptionRuntime
import io.morgan.idonthaveyourtime.core.model.TranscriptionResult
import javax.inject.Inject
import timber.log.Timber

internal class RoutingTranscriptionEngineLocalDataSource @Inject constructor(
    private val processingConfigDataSource: ProcessingConfigLocalDataSource,
    @TranscriptionBackend private val engines: Set<@JvmSuppressWildcards TranscriptionEngineLocalDataSource>,
) : TranscriptionEngineLocalDataSource {

    private val enginesByRuntime: Map<TranscriptionRuntime, TranscriptionEngineLocalDataSource> by lazy {
        engines.associateBy { engine -> engine.capability().runtime }
    }

    override fun capability(): TranscriptionEngineCapability =
        TranscriptionEngineCapability(
            runtime = TranscriptionRuntime.Auto,
            supportedFormats = engines.flatMap { engine -> engine.capability().supportedFormats }.toSet(),
            supportsStreaming = engines.any { engine -> engine.capability().supportsStreaming },
            supportsAsyncGeneration = engines.any { engine -> engine.capability().supportsAsyncGeneration },
            supportsHardwareAcceleration = engines.any { engine -> engine.capability().supportsHardwareAcceleration },
        )

    override suspend fun probe(): Result<TranscriptionEngineProbeResult> =
        runCatching { resolveSelection().probe }

    override suspend fun transcribe(
        request: TranscriptionRequest,
        languageHint: LanguageHint,
        onProgress: suspend (Float) -> Unit,
        onPartialResult: suspend (String) -> Unit,
    ): Result<TranscriptionResult> = runCatching {
        val selection = resolveSelection()
        logSelection(selection.probe, operation = "transcribe")
        val engine = selection.engine ?: error(selection.probe.failureReason ?: "No transcription runtime selected.")
        engine.transcribe(
            request = request,
            languageHint = languageHint,
            onProgress = onProgress,
            onPartialResult = onPartialResult,
        ).getOrThrow().let { result ->
            val fallbackReason = selection.probe.fallbackReason
            if (fallbackReason == null) {
                result
            } else {
                result.copy(
                    metrics = result.metrics.copy(
                        fallbackReason = fallbackReason,
                    ),
                )
            }
        }
    }

    private suspend fun resolveSelection(): ResolvedTranscriptionEngine {
        val config = processingConfigDataSource.getConfig()
        val fileName = config.transcriptionModelFileName.trim().ifEmpty { ProcessingConfig().transcriptionModelFileName }
        val modelFormat = TranscriptionModelFormat.fromFileName(fileName)
        if (modelFormat == null) {
            return ResolvedTranscriptionEngine(
                engine = null,
                probe = TranscriptionEngineProbeResult(
                    requestedRuntime = config.transcriptionRuntime,
                    selectedRuntime = null,
                    modelFormat = null,
                    modelFileName = fileName,
                    supported = false,
                    supportsStreaming = false,
                    failureReason = "Unsupported transcription model format for `$fileName`.",
                ),
            )
        }
        val candidates = candidateRuntimes(config.transcriptionRuntime, modelFormat)

        var firstFailureReason: String? = null
        for (runtime in candidates) {
            val engine = enginesByRuntime[runtime] ?: continue
            val probe = engine.probe().getOrElse { throwable ->
                TranscriptionEngineProbeResult(
                    requestedRuntime = runtime,
                    selectedRuntime = runtime,
                    modelFormat = modelFormat,
                    modelFileName = fileName,
                    supported = false,
                    supportsStreaming = engine.capability().supportsStreaming,
                    failureReason = throwable.message ?: "Probe failed for ${runtime.displayName}.",
                )
            }

            if (!probe.supported) {
                firstFailureReason = firstFailureReason ?: probe.failureReason
                continue
            }

            return ResolvedTranscriptionEngine(
                engine = engine,
                probe = TranscriptionEngineProbeResult(
                    requestedRuntime = config.transcriptionRuntime,
                    selectedRuntime = runtime,
                    modelFormat = modelFormat ?: probe.modelFormat,
                    modelFileName = fileName,
                    supported = true,
                    supportsStreaming = engine.capability().supportsStreaming,
                    fallbackReason = buildFallbackReason(
                        requestedRuntime = config.transcriptionRuntime,
                        selectedRuntime = runtime,
                        modelFormat = modelFormat ?: probe.modelFormat,
                    ),
                ),
            )
        }

        return ResolvedTranscriptionEngine(
            engine = null,
            probe = TranscriptionEngineProbeResult(
                requestedRuntime = config.transcriptionRuntime,
                selectedRuntime = null,
                modelFormat = modelFormat,
                modelFileName = fileName,
                supported = false,
                supportsStreaming = false,
                failureReason = firstFailureReason ?: "No transcription runtime could handle $fileName.",
            ),
        )
    }

    private fun candidateRuntimes(
        requestedRuntime: TranscriptionRuntime,
        modelFormat: TranscriptionModelFormat?,
    ): List<TranscriptionRuntime> = buildList {
        if (requestedRuntime != TranscriptionRuntime.Auto) {
            add(requestedRuntime)
        }

        when (modelFormat) {
            TranscriptionModelFormat.LiteRtLm -> {
                add(TranscriptionRuntime.GoogleAiEdgeLiteRtLm)
            }

            null -> {
                add(TranscriptionRuntime.GoogleAiEdgeLiteRtLm)
            }
        }
    }.distinct()

    private fun buildFallbackReason(
        requestedRuntime: TranscriptionRuntime,
        selectedRuntime: TranscriptionRuntime,
        modelFormat: TranscriptionModelFormat?,
    ): String? {
        val preferredRuntime = when (modelFormat) {
            TranscriptionModelFormat.LiteRtLm -> TranscriptionRuntime.GoogleAiEdgeLiteRtLm
            null -> TranscriptionRuntime.GoogleAiEdgeLiteRtLm
        }

        return when {
            requestedRuntime == TranscriptionRuntime.Auto && selectedRuntime != preferredRuntime ->
                "Auto preferred ${preferredRuntime.displayName} for ${modelFormat?.displayName ?: "unknown format"}, but used ${selectedRuntime.displayName}."

            requestedRuntime != TranscriptionRuntime.Auto && selectedRuntime != requestedRuntime ->
                "Requested ${requestedRuntime.displayName}, but routed to ${selectedRuntime.displayName}."

            else -> null
        }
    }

    private fun logSelection(probe: TranscriptionEngineProbeResult, operation: String) {
        if (probe.supported) {
            Timber.tag(TAG).i(
                "Transcription route op=%s requested=%s selected=%s format=%s streaming=%s fallback=%s",
                operation,
                probe.requestedRuntime.displayName,
                probe.selectedRuntime?.displayName,
                probe.modelFormat?.displayName,
                probe.supportsStreaming,
                probe.fallbackReason ?: "none",
            )
        } else {
            Timber.tag(TAG).w(
                "Transcription route failed op=%s requested=%s format=%s file=%s reason=%s",
                operation,
                probe.requestedRuntime.displayName,
                probe.modelFormat?.displayName ?: "unknown",
                probe.modelFileName ?: "unknown",
                probe.failureReason ?: "unknown",
            )
        }
    }

    private data class ResolvedTranscriptionEngine(
        val engine: TranscriptionEngineLocalDataSource?,
        val probe: TranscriptionEngineProbeResult,
    )

    private companion object {
        const val TAG = "TranscriptionRouter"
    }
}
