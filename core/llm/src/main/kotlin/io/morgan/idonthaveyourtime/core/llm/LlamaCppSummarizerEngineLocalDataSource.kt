package io.morgan.idonthaveyourtime.core.llm

import io.morgan.idonthaveyourtime.core.data.datasource.model.ModelLocatorLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.summarization.SummarizerEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.data.di.DefaultDispatcher
import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.SummarizerEngineCapability
import io.morgan.idonthaveyourtime.core.model.SummarizerEngineProbeResult
import io.morgan.idonthaveyourtime.core.model.SummarizerModelFormat
import io.morgan.idonthaveyourtime.core.model.SummarizerRuntime
import io.morgan.idonthaveyourtime.core.model.Summary
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis
import timber.log.Timber

internal class LlamaCppSummarizerEngineLocalDataSource @Inject constructor(
    private val modelLocator: ModelLocatorLocalDataSource,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : SummarizerEngineLocalDataSource {

    private val lock = Any()
    private var loadedModelPath: String? = null
    private var contextPtr: Long = 0L

    override fun capability(): SummarizerEngineCapability =
        SummarizerEngineCapability(
            runtime = SummarizerRuntime.LlamaCpp,
            supportedFormats = setOf(SummarizerModelFormat.Gguf),
            supportsStreaming = false,
            supportsAsyncGeneration = false,
            supportsHardwareAcceleration = false,
        )

    override suspend fun probe(): Result<SummarizerEngineProbeResult> = runCatching {
        val modelPath = modelLocator.getModelPath(ModelId.Llm).getOrThrow()
        val modelFile = File(modelPath)
        val modelFormat = SummarizerModelFormat.fromFileName(modelFile.name)
        val supported = SummarizationPromptFactory.supportsModelFormat(
            format = modelFormat,
            supportedFormats = capability().supportedFormats,
        )

        SummarizerEngineProbeResult(
            requestedRuntime = SummarizerRuntime.LlamaCpp,
            selectedRuntime = SummarizerRuntime.LlamaCpp,
            modelFormat = modelFormat,
            modelFileName = modelFile.name,
            supported = supported,
            supportsStreaming = capability().supportsStreaming,
            failureReason = if (supported) null else "llama.cpp requires a `.gguf` model.",
        )
    }

    override suspend fun prewarm(): Result<Unit> = withContext(defaultDispatcher) {
        runCatching {
            val modelPath = modelLocator.getModelPath(ModelId.Llm).getOrThrow()
            ensureContextLoaded(modelPath)
            Unit
        }
    }

    override suspend fun mapChunk(
        transcriptChunk: String,
        languageCode: String?,
        onPartialResult: (String) -> Unit,
    ): Result<String> = withContext(defaultDispatcher) {
        runCatching {
            val chunkText = transcriptChunk.trim()
            if (chunkText.isEmpty()) {
                return@runCatching ""
            }

            val modelPath = modelLocator.getModelPath(ModelId.Llm).getOrThrow()
            val ctxPtr = ensureContextLoaded(modelPath)
            registerAbortOnCancellation(ctxPtr)

            val prompt = SummarizationPromptFactory.mapChunk(
                transcriptChunk = chunkText,
                languageCode = languageCode,
            )

            val generated: String
            val elapsedMs = measureTimeMillis {
                generated = generateChat(
                    ctxPtr = ctxPtr,
                    prompt = prompt,
                )
            }
            val normalized = SummarizationPromptFactory.normalizeMapOutput(generated)
            if (normalized.isNotBlank()) {
                onPartialResult(normalized)
            }
            Timber.tag(TAG).i("MAP llama.cpp length=%d elapsedMs=%d", normalized.length, elapsedMs)
            normalized
        }
    }

    override suspend fun reduce(
        chunkBulletSummaries: List<String>,
        languageCode: String?,
        onPartialResult: (String) -> Unit,
    ): Result<Summary> = withContext(defaultDispatcher) {
        runCatching {
            val bullets = chunkBulletSummaries
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (bullets.isEmpty()) {
                return@runCatching Summary(text = "")
            }

            val modelPath = modelLocator.getModelPath(ModelId.Llm).getOrThrow()
            val ctxPtr = ensureContextLoaded(modelPath)
            registerAbortOnCancellation(ctxPtr)

            val prompt = SummarizationPromptFactory.reduce(
                chunkBulletSummaries = bullets,
                languageCode = languageCode,
            )

            val generated: String
            val elapsedMs = measureTimeMillis {
                generated = generateChat(
                    ctxPtr = ctxPtr,
                    prompt = prompt,
                )
            }
            val normalized = SummarizationPromptFactory.normalizeReduceOutput(generated)
            if (normalized.isNotBlank()) {
                onPartialResult(normalized)
            }
            Timber.tag(TAG).i("REDUCE llama.cpp length=%d elapsedMs=%d", normalized.length, elapsedMs)
            Summary(text = normalized)
        }
    }

    private suspend fun registerAbortOnCancellation(ctxPtr: Long) {
        currentCoroutineContext().job.invokeOnCompletion { throwable ->
            if (throwable != null) {
                runCatching { LlamaJni.requestAbort(ctxPtr) }
            }
        }
    }

    private fun ensureContextLoaded(modelPath: String): Long =
        synchronized(lock) {
            if (contextPtr != 0L && loadedModelPath == modelPath) {
                return contextPtr
            }

            if (contextPtr != 0L) {
                runCatching { LlamaJni.freeContext(contextPtr) }
                contextPtr = 0L
                loadedModelPath = null
            }

            LlamaLibraryLoader.ensureLoaded()

            val loadMs = measureTimeMillis {
                contextPtr = LlamaJni.initContext(
                    modelPath = modelPath,
                    nCtx = N_CTX,
                    nThreads = preferredThreadCount,
                )
            }

            require(contextPtr != 0L) { "Failed to initialize llama.cpp context" }
            loadedModelPath = modelPath
            Timber.tag(TAG).i(
                "Loaded llama.cpp modelPath=%s sizeBytes=%d loadMs=%d",
                modelPath,
                runCatching { File(modelPath).length() }.getOrDefault(-1L),
                loadMs,
            )
            contextPtr
        }

    private fun generateChat(
        ctxPtr: Long,
        prompt: SummarizationPrompt,
    ): String = synchronized(lock) {
        LlamaJni.generateChat(
            contextPtr = ctxPtr,
            system = prompt.systemPrompt,
            user = prompt.userPrompt,
            maxTokens = prompt.maxOutputTokens,
            temperature = TEMPERATURE,
            topP = TOP_P,
            topK = TOP_K,
        )
    }

    private companion object {
        const val TAG = "LlamaCppSummarizer"

        private const val N_CTX = 2_048
        private const val TEMPERATURE = 0.1f
        private const val TOP_P = 0.9f
        private const val TOP_K = 20

        private val preferredThreadCount: Int
            get() = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    }
}
