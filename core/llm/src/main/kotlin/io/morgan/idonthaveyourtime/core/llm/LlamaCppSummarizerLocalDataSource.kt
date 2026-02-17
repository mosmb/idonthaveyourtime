package io.morgan.idonthaveyourtime.core.llm

import io.morgan.idonthaveyourtime.core.data.datasource.model.ModelLocatorLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.summarization.SummarizerLocalDataSource
import io.morgan.idonthaveyourtime.core.data.di.DefaultDispatcher
import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.Summary
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.system.measureTimeMillis
import timber.log.Timber

internal class LlamaCppSummarizerLocalDataSource @Inject constructor(
    private val modelLocator: ModelLocatorLocalDataSource,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : SummarizerLocalDataSource {

    private val lock = Any()
    private var loadedModelPath: String? = null
    private var contextPtr: Long = 0L

    override suspend fun mapChunk(
        transcriptChunk: String,
        languageCode: String?,
    ): Result<String> = withContext(defaultDispatcher) {
        runCatching {
            val chunkText = transcriptChunk.trim()
            if (chunkText.isEmpty()) {
                return@runCatching ""
            }

            val modelPath = modelLocator.getModelPath(ModelId.Llm).getOrThrow()
            val ctxPtr = ensureContextLoaded(modelPath)

            coroutineContext.job.invokeOnCompletion { throwable ->
                if (throwable != null) {
                    runCatching { LlamaJni.requestAbort(ctxPtr) }
                }
            }

            val targetLanguage = resolveTargetLanguage(languageCode)

            val system = "You summarize transcripts. Be accurate. Only use information present. If unsure, omit."
            val user = buildString {
                appendLine("Here is a transcript chunk (may be imperfect). Produce:")
                appendLine("- 3–7 concise bullet points with the most important info")
                appendLine("- Preserve names, dates, numbers exactly")
                appendLine("- Only use information present in the transcript")
                appendLine("- Write in the same language as the transcript chunk")
                appendLine("Transcript chunk:")
                appendLine("<<")
                appendLine(chunkText.take(MAX_CHUNK_CHARS))
                appendLine(">>")
                appendLine("Output language: $targetLanguage.")
            }

            val generated: String
            val elapsedMs = measureTimeMillis {
                generated = generateChat(
                    ctxPtr = ctxPtr,
                    system = system,
                    user = user,
                    maxTokens = MAP_MAX_TOKENS,
                )
            }
            Timber.tag(TAG).i("MAP done length=%d elapsedMs=%d", generated.length, elapsedMs)
            normalizeBullets(generated)
        }
    }

    override suspend fun reduce(
        chunkBulletSummaries: List<String>,
        languageCode: String?,
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

            coroutineContext.job.invokeOnCompletion { throwable ->
                if (throwable != null) {
                    runCatching { LlamaJni.requestAbort(ctxPtr) }
                }
            }

            val targetLanguage = resolveTargetLanguage(languageCode)

            val system = "You summarize transcripts. Be accurate. Only use information present. If unsure, omit."
            val user = buildString {
                appendLine("Combine these chunk bullet summaries into:")
                appendLine("1) Title (1 line)")
                appendLine("2) TL;DR (2–3 sentences)")
                appendLine("3) Key points (5–10 bullets)")
                appendLine("4) Action items (bullets) or “None”")
                appendLine("Chunk summaries:")
                appendLine("<<")
                bullets.forEach { chunk ->
                    appendLine(chunk)
                    appendLine()
                }
                appendLine(">>")
                appendLine("Output language: $targetLanguage.")
                appendLine("Only use information present in the chunk summaries.")
            }

            val generated: String
            val elapsedMs = measureTimeMillis {
                generated = generateChat(
                    ctxPtr = ctxPtr,
                    system = system,
                    user = user,
                    maxTokens = REDUCE_MAX_TOKENS,
                )
            }

            Timber.tag(TAG).i("REDUCE done length=%d elapsedMs=%d", generated.length, elapsedMs)
            Summary(text = generated.trim())
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

            val loadMs = measureTimeMillis {
                contextPtr = LlamaJni.initContext(
                    modelPath = modelPath,
                    nCtx = N_CTX,
                    nThreads = preferredThreadCount,
                )
            }

            require(contextPtr != 0L) { "Failed to initialize llama context" }
            loadedModelPath = modelPath
            Timber.tag(TAG).i(
                "LLM loaded modelPath=%s sizeBytes=%d loadMs=%d",
                modelPath,
                runCatching { File(modelPath).length() }.getOrDefault(-1L),
                loadMs,
            )
            contextPtr
        }

    private fun generateChat(
        ctxPtr: Long,
        system: String,
        user: String,
        maxTokens: Int,
    ): String = synchronized(lock) {
        LlamaJni.generateChat(
            contextPtr = ctxPtr,
            system = system,
            user = user,
            maxTokens = maxTokens,
            temperature = TEMPERATURE,
            topP = TOP_P,
            topK = TOP_K,
        )
    }

    private fun resolveTargetLanguage(languageCode: String?): String {
        val normalized = languageCode?.trim().orEmpty()
        if (normalized.isBlank() || normalized.equals("auto", ignoreCase = true) || normalized.equals("und", ignoreCase = true)) {
            return Locale.getDefault().language.ifBlank { "en" }
        }
        return normalized
    }

    private fun normalizeBullets(raw: String): String {
        val lines = raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val bullets = lines.filter { line ->
            line.startsWith("-") || line.startsWith("•") || line.startsWith("*")
        }

        val selected = if (bullets.isNotEmpty()) bullets else lines
        return selected
            .take(7)
            .joinToString("\n") { line ->
                when {
                    line.startsWith("-") -> line
                    line.startsWith("•") -> "- " + line.drop(1).trimStart()
                    line.startsWith("*") -> "- " + line.drop(1).trimStart()
                    else -> "- $line"
                }
            }
            .trim()
    }

    private companion object {
        const val TAG = "LlamaCppSummarizerDataSource"

        private const val N_CTX = 2048
        private const val MAP_MAX_TOKENS = 256
        private const val REDUCE_MAX_TOKENS = 512

        private const val TEMPERATURE = 0.2f
        private const val TOP_P = 0.9f
        private const val TOP_K = 40

        private const val MAX_CHUNK_CHARS = 12_000

        private val preferredThreadCount: Int
            get() = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    }
}
