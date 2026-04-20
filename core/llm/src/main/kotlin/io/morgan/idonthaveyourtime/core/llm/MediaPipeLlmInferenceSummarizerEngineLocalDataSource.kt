package io.morgan.idonthaveyourtime.core.llm

import android.content.Context
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import io.morgan.idonthaveyourtime.core.data.datasource.model.ModelLocatorLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.summarization.SummarizerEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.data.di.DefaultDispatcher
import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.SummarizerEngineCapability
import io.morgan.idonthaveyourtime.core.model.SummarizerEngineProbeResult
import io.morgan.idonthaveyourtime.core.model.SummarizerModelFormat
import io.morgan.idonthaveyourtime.core.model.SummarizerRuntime
import io.morgan.idonthaveyourtime.core.model.Summary
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.system.measureTimeMillis
import timber.log.Timber

internal class MediaPipeLlmInferenceSummarizerEngineLocalDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelLocator: ModelLocatorLocalDataSource,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : SummarizerEngineLocalDataSource {

    private val lock = Any()
    private var loadedModelPath: String? = null
    private var loadedBackendName: String? = null
    private var llmInference: LlmInference? = null

    override fun capability(): SummarizerEngineCapability =
        SummarizerEngineCapability(
            runtime = SummarizerRuntime.MediaPipeLlmInference,
            supportedFormats = setOf(SummarizerModelFormat.Task),
            supportsStreaming = true,
            supportsAsyncGeneration = true,
            supportsHardwareAcceleration = true,
        )

    override suspend fun probe(): Result<SummarizerEngineProbeResult> = runCatching {
        val modelPath = modelLocator.getModelPath(ModelId.Llm).getOrThrow()
        val modelFormat = SummarizerModelFormat.fromFileName(modelPath)
        val supported = SummarizationPromptFactory.supportsModelFormat(
            format = modelFormat,
            supportedFormats = capability().supportedFormats,
        )
        SummarizerEngineProbeResult(
            requestedRuntime = SummarizerRuntime.MediaPipeLlmInference,
            selectedRuntime = SummarizerRuntime.MediaPipeLlmInference,
            modelFormat = modelFormat,
            modelFileName = modelPath.substringAfterLast('/'),
            supported = supported,
            supportsStreaming = capability().supportsStreaming,
            failureReason = if (supported) null else "MediaPipe LLM Inference requires a `.task` model.",
        )
    }

    override suspend fun prewarm(): Result<Unit> = withContext(defaultDispatcher) {
        runCatching {
            val modelPath = modelLocator.getModelPath(ModelId.Llm).getOrThrow()
            ensureInferenceLoaded(modelPath)
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

            val prompt = SummarizationPromptFactory.mapChunk(
                transcriptChunk = chunkText,
                languageCode = languageCode,
            )
            val inference = ensureInferenceLoaded(
                modelLocator.getModelPath(ModelId.Llm).getOrThrow(),
            )
            val session = createSession(inference)

            try {
                val generated: String
                val elapsedMs = measureTimeMillis {
                    generated = generateStreaming(
                        session = session,
                        prompt = prompt,
                        onPartialResult = onPartialResult,
                    )
                }
                val normalized = SummarizationPromptFactory.normalizeMapOutput(generated)
                if (normalized.isNotBlank()) {
                    onPartialResult(normalized)
                }
                Timber.tag(TAG).i(
                    "MAP MediaPipe backend=%s length=%d elapsedMs=%d",
                    loadedBackendName,
                    normalized.length,
                    elapsedMs,
                )
                normalized
            } finally {
                runCatching { session.close() }
            }
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

            val prompt = SummarizationPromptFactory.reduce(
                chunkBulletSummaries = bullets,
                languageCode = languageCode,
            )
            val inference = ensureInferenceLoaded(
                modelLocator.getModelPath(ModelId.Llm).getOrThrow(),
            )
            val session = createSession(inference)

            try {
                val generated: String
                val elapsedMs = measureTimeMillis {
                    generated = generateStreaming(
                        session = session,
                        prompt = prompt,
                        onPartialResult = onPartialResult,
                    )
                }
                val normalized = SummarizationPromptFactory.normalizeReduceOutput(generated)
                if (normalized.isNotBlank()) {
                    onPartialResult(normalized)
                }
                Timber.tag(TAG).i(
                    "REDUCE MediaPipe backend=%s length=%d elapsedMs=%d",
                    loadedBackendName,
                    normalized.length,
                    elapsedMs,
                )
                Summary(text = normalized)
            } finally {
                runCatching { session.close() }
            }
        }
    }

    private fun createSession(inference: LlmInference): LlmInferenceSession =
        LlmInferenceSession.createFromOptions(
            inference,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(TOP_K)
                .setTopP(TOP_P)
                .setTemperature(TEMPERATURE)
                .setRandomSeed(RANDOM_SEED)
                .build(),
        )

    private suspend fun generateStreaming(
        session: LlmInferenceSession,
        prompt: SummarizationPrompt,
        onPartialResult: (String) -> Unit,
    ): String {
        var streamedText = ""
        session.addQueryChunk(prompt.asSinglePrompt())

        val future = session.generateResponseAsync(
            ProgressListener<String> { partial, _ ->
                streamedText = StreamingTextAccumulator.append(
                    previous = streamedText,
                    incoming = partial,
                )
                if (streamedText.isNotBlank()) {
                    onPartialResult(streamedText.trim())
                }
            },
        )

        coroutineContext.job.invokeOnCompletion { throwable ->
            if (throwable != null) {
                runCatching { session.cancelGenerateResponseAsync() }
                future.cancel(true)
            }
        }

        return future.awaitDirect().trim()
    }

    private fun ensureInferenceLoaded(modelPath: String): LlmInference =
        synchronized(lock) {
            if (loadedModelPath == modelPath) {
                llmInference?.let { existing -> return existing }
            }

            llmInference?.let { previous ->
                runCatching { previous.close() }
            }
            llmInference = null
            loadedModelPath = null
            loadedBackendName = null

            val loadErrors = mutableListOf<String>()
            preferredBackends().forEach { backend ->
                try {
                    val created: LlmInference
                    val loadMs = measureTimeMillis {
                        created = LlmInference.createFromOptions(
                            context,
                            LlmInference.LlmInferenceOptions.builder()
                                .setModelPath(modelPath)
                                .setMaxTokens(MAX_TOTAL_TOKENS)
                                .setMaxNumImages(0)
                                .setMaxTopK(TOP_K)
                                .setSupportedLoraRanks(emptyList())
                                .setPreferredBackend(backend)
                                .build(),
                        )
                    }
                    llmInference = created
                    loadedModelPath = modelPath
                    loadedBackendName = backend.name
                    Timber.tag(TAG).i(
                        "Loaded MediaPipe LLM modelPath=%s backend=%s loadMs=%d",
                        modelPath,
                        loadedBackendName,
                        loadMs,
                    )
                    return created
                } catch (throwable: Throwable) {
                    loadErrors += "${backend.name}: ${throwable.message}"
                    Timber.tag(TAG).w(throwable, "MediaPipe backend init failed backend=%s", backend.name)
                }
            }

            throw IllegalStateException(
                "Unable to initialize MediaPipe LLM Inference for `$modelPath`: ${loadErrors.joinToString()}",
            )
        }

    private fun preferredBackends(): List<LlmInference.Backend> = listOf(
        LlmInference.Backend.GPU,
        LlmInference.Backend.CPU,
    )

    private suspend fun <T> ListenableFuture<T>.awaitDirect(): T =
        suspendCancellableCoroutine { continuation ->
            addListener(
                Runnable {
                    try {
                        continuation.resume(get())
                    } catch (throwable: Throwable) {
                        continuation.resumeWithException(throwable)
                    }
                },
                directExecutor,
            )
            continuation.invokeOnCancellation {
                cancel(true)
            }
        }

    private companion object {
        const val TAG = "MediaPipeLlmSummarizer"

        private const val MAX_TOTAL_TOKENS = 1_280
        private const val TOP_K = 20
        private const val TOP_P = 0.9f
        private const val TEMPERATURE = 0.1f
        private const val RANDOM_SEED = 0

        val directExecutor = Executor { runnable -> runnable.run() }
    }
}
