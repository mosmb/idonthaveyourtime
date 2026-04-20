package io.morgan.idonthaveyourtime.core.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.ResponseCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Session
import com.google.ai.edge.litertlm.SessionConfig
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

internal class LiteRtLmSummarizerEngineLocalDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelLocator: ModelLocatorLocalDataSource,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : SummarizerEngineLocalDataSource {

    private val lock = Any()
    private var loadedModelPath: String? = null
    private var loadedBackendName: String? = null
    private var engine: Engine? = null

    override fun capability(): SummarizerEngineCapability =
        SummarizerEngineCapability(
            runtime = SummarizerRuntime.LiteRtLm,
            supportedFormats = setOf(SummarizerModelFormat.LiteRtLm),
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
            requestedRuntime = SummarizerRuntime.LiteRtLm,
            selectedRuntime = SummarizerRuntime.LiteRtLm,
            modelFormat = modelFormat,
            modelFileName = modelPath.substringAfterLast('/'),
            supported = supported,
            supportsStreaming = capability().supportsStreaming,
            failureReason = if (supported) null else "LiteRT-LM requires a `.litertlm` model.",
        )
    }

    override suspend fun prewarm(): Result<Unit> = withContext(defaultDispatcher) {
        runCatching {
            val modelPath = modelLocator.getModelPath(ModelId.Llm).getOrThrow()
            ensureEngineLoaded(modelPath)
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
            val loadedEngine = ensureEngineLoaded(
                modelLocator.getModelPath(ModelId.Llm).getOrThrow(),
            )
            val session = loadedEngine.createSession(
                SessionConfig(
                    samplerConfig = SamplerConfig(
                        topK = TOP_K,
                        topP = TOP_P.toDouble(),
                        temperature = TEMPERATURE.toDouble(),
                        seed = RANDOM_SEED,
                    ),
                ),
            )

            try {
                val timed = measureTimeMillisAndReturn {
                    generateStreaming(
                        session = session,
                        prompt = prompt,
                        onPartialResult = onPartialResult,
                    )
                }
                val normalized = SummarizationPromptFactory.normalizeMapOutput(timed.value)
                if (normalized.isNotBlank()) {
                    onPartialResult(normalized)
                }
                Timber.tag(TAG).i(
                    "MAP LiteRT-LM backend=%s length=%d elapsedMs=%d",
                    loadedBackendName,
                    normalized.length,
                    timed.elapsedMs,
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
            val loadedEngine = ensureEngineLoaded(
                modelLocator.getModelPath(ModelId.Llm).getOrThrow(),
            )
            val session = loadedEngine.createSession(
                SessionConfig(
                    samplerConfig = SamplerConfig(
                        topK = TOP_K,
                        topP = TOP_P.toDouble(),
                        temperature = TEMPERATURE.toDouble(),
                        seed = RANDOM_SEED,
                    ),
                ),
            )

            try {
                val timed = measureTimeMillisAndReturn {
                    generateStreaming(
                        session = session,
                        prompt = prompt,
                        onPartialResult = onPartialResult,
                    )
                }
                val normalized = SummarizationPromptFactory.normalizeReduceOutput(timed.value)
                if (normalized.isNotBlank()) {
                    onPartialResult(normalized)
                }
                Timber.tag(TAG).i(
                    "REDUCE LiteRT-LM backend=%s length=%d elapsedMs=%d",
                    loadedBackendName,
                    normalized.length,
                    timed.elapsedMs,
                )
                Summary(text = normalized)
            } finally {
                runCatching { session.close() }
            }
        }
    }

    private suspend fun generateStreaming(
        session: Session,
        prompt: SummarizationPrompt,
        onPartialResult: (String) -> Unit,
    ): String = suspendCancellableCoroutine { continuation ->
        var streamedText = ""

        session.generateContentStream(
            listOf(InputData.Text(prompt.asSinglePrompt())),
            object : ResponseCallback {
                override fun onNext(text: String) {
                    streamedText = StreamingTextAccumulator.append(
                        previous = streamedText,
                        incoming = text,
                    )
                    if (streamedText.isNotBlank()) {
                        onPartialResult(streamedText.trim())
                    }
                }

                override fun onDone() {
                    if (continuation.isActive) {
                        continuation.resume(streamedText.trim())
                    }
                }

                override fun onError(throwable: Throwable) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(throwable)
                    }
                }
            },
        )

        continuation.invokeOnCancellation {
            runCatching { session.cancelProcess() }
        }
    }

    private fun ensureEngineLoaded(modelPath: String): Engine =
        synchronized(lock) {
            if (loadedModelPath == modelPath) {
                engine?.let { existing -> return existing }
            }

            engine?.let { previous ->
                runCatching { previous.close() }
            }
            engine = null
            loadedModelPath = null
            loadedBackendName = null

            val loadErrors = mutableListOf<String>()
            preferredBackends().forEach { backend ->
                try {
                    val created = Engine(
                        EngineConfig(
                            modelPath = modelPath,
                            backend = backend,
                            visionBackend = null,
                            audioBackend = null,
                            maxNumTokens = MAX_TOTAL_TOKENS,
                            cacheDir = context.cacheDir.absolutePath,
                        ),
                    )

                    val loadMs = measureTimeMillis {
                        created.initialize()
                    }
                    engine = created
                    loadedModelPath = modelPath
                    loadedBackendName = backend.javaClass.simpleName
                    Timber.tag(TAG).i(
                        "Loaded LiteRT-LM modelPath=%s backend=%s loadMs=%d",
                        modelPath,
                        loadedBackendName,
                        loadMs,
                    )
                    return created
                } catch (throwable: Throwable) {
                    loadErrors += "${backend.javaClass.simpleName}: ${throwable.message}"
                    Timber.tag(TAG).w(throwable, "LiteRT-LM backend init failed backend=%s", backend.javaClass.simpleName)
                }
            }

            throw IllegalStateException(
                "Unable to initialize LiteRT-LM engine for `$modelPath`: ${loadErrors.joinToString()}",
            )
        }

    private fun preferredBackends(): List<Backend> = buildList {
        add(Backend.NPU(context.applicationInfo.nativeLibraryDir))
        add(Backend.GPU())
        add(Backend.CPU(preferredThreadCount))
        add(Backend.CPU())
    }.distinctBy { backend -> backend.javaClass.name + backend.toString() }

    private data class TimedValue<T>(
        val value: T,
        val elapsedMs: Long,
    )

    private suspend fun <T> measureTimeMillisAndReturn(block: suspend () -> T): TimedValue<T> {
        val startedAt = System.nanoTime()
        val value = block()
        return TimedValue(
            value = value,
            elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L,
        )
    }

    private companion object {
        const val TAG = "LiteRtLmSummarizer"

        private const val MAX_TOTAL_TOKENS = 1_280
        private const val TOP_K = 20
        private const val TOP_P = 0.9f
        private const val TEMPERATURE = 0.1f
        private const val RANDOM_SEED = 0

        private val preferredThreadCount: Int
            get() = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    }
}
