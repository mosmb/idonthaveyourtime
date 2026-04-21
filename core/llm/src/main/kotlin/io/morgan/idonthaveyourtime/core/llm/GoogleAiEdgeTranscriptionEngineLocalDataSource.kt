package io.morgan.idonthaveyourtime.core.llm

import android.content.Context
import android.os.Build
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.Contents
import dagger.hilt.android.qualifiers.ApplicationContext
import io.morgan.idonthaveyourtime.core.data.datasource.audio.AudioSampleReaderLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.model.ModelLocatorLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.transcription.TranscriptionEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.data.di.DefaultDispatcher
import io.morgan.idonthaveyourtime.core.model.LanguageHint
import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.Transcript
import io.morgan.idonthaveyourtime.core.model.TranscriptionEngineCapability
import io.morgan.idonthaveyourtime.core.model.TranscriptionEngineProbeResult
import io.morgan.idonthaveyourtime.core.model.TranscriptionMetrics
import io.morgan.idonthaveyourtime.core.model.TranscriptionModelFormat
import io.morgan.idonthaveyourtime.core.model.TranscriptionRequest
import io.morgan.idonthaveyourtime.core.model.TranscriptionResult
import io.morgan.idonthaveyourtime.core.model.TranscriptionRuntime
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class GoogleAiEdgeTranscriptionEngineLocalDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelLocator: ModelLocatorLocalDataSource,
    private val audioSampleReader: AudioSampleReaderLocalDataSource,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : TranscriptionEngineLocalDataSource {

    private val lock = Any()
    private var loadedModelPath: String? = null
    private var loadedBackendName: String? = null
    private var engine: Engine? = null

    override fun capability(): TranscriptionEngineCapability =
        TranscriptionEngineCapability(
            runtime = TranscriptionRuntime.GoogleAiEdgeLiteRtLm,
            supportedFormats = setOf(TranscriptionModelFormat.LiteRtLm),
            supportsStreaming = true,
            supportsAsyncGeneration = true,
            supportsHardwareAcceleration = true,
        )

    override suspend fun probe(): Result<TranscriptionEngineProbeResult> = runCatching {
        val modelPath = modelLocator.getModelPath(ModelId.Transcription).getOrThrow()
        val fileName = modelPath.substringAfterLast('/')
        val modelFormat = TranscriptionModelFormat.fromFileName(fileName)
        val supported = modelFormat == TranscriptionModelFormat.LiteRtLm
        TranscriptionEngineProbeResult(
            requestedRuntime = TranscriptionRuntime.GoogleAiEdgeLiteRtLm,
            selectedRuntime = TranscriptionRuntime.GoogleAiEdgeLiteRtLm,
            modelFormat = modelFormat,
            modelFileName = fileName,
            supported = supported,
            supportsStreaming = capability().supportsStreaming,
            failureReason = if (supported) null else "Google AI Edge LiteRT-LM requires a `.litertlm` model.",
        )
    }

    override suspend fun transcribe(
        request: TranscriptionRequest,
        languageHint: LanguageHint,
        onProgress: suspend (Float) -> Unit,
        onPartialResult: suspend (String) -> Unit,
    ): Result<TranscriptionResult> = withContext(defaultDispatcher) {
        runCatching {
            val audioBytes = audioSampleReader.read16kMonoWavBytes(
                wavFilePath = request.wavFilePath,
                startMs = request.startMs,
                endMs = request.endMs,
            ).getOrThrow()
            val modelPath = modelLocator.getModelPath(ModelId.Transcription).getOrThrow()
            val loadedEngine = ensureEngineLoaded(modelPath)
            val prompt = GoogleAiEdgeTranscriptionPromptFactory.userPrompt(languageHint)
            val conversation = loadedEngine.engine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.Companion.of(prompt),
                ),
            )
            val startedAtNanos = System.nanoTime()
            var firstTextMs: Long? = null
            var streamedText = ""

            try {
                onProgress(0f)
                val coroutineContext = currentCoroutineContext()
                coroutineContext.job.invokeOnCompletion { throwable ->
                    if (throwable is CancellationException) {
                        runCatching { conversation.cancelProcess() }
                    }
                }

                conversation.sendMessageAsync(
                    Contents.Companion.of(
                        Content.AudioBytes(audioBytes),
                        Content.Text(prompt),
                    ),
                    emptyMap<String, Any>(),
                ).collect { message ->
                    val nextText = extractText(message)
                    if (nextText.isBlank()) {
                        return@collect
                    }
                    streamedText = StreamingTextAccumulator.append(
                        previous = streamedText,
                        incoming = nextText,
                    )
                    if (streamedText.isBlank()) {
                        return@collect
                    }
                    if (firstTextMs == null) {
                        firstTextMs = elapsedSince(startedAtNanos)
                    }
                    onPartialResult(streamedText)
                }

                onProgress(1f)
                val totalMs = elapsedSince(startedAtNanos)
                val audioDurationMs = (request.endMs - request.startMs).coerceAtLeast(0L)
                val modelFileName = modelPath.substringAfterLast(File.separatorChar)
                val finalText = streamedText.trim().ifBlank { "[empty transcription]" }

                Timber.tag(TAG).i(
                    "LiteRT-LM transcription backend=%s warmStart=%s totalMs=%d ttftMs=%s",
                    loadedEngine.backendName,
                    loadedEngine.warmStart,
                    totalMs,
                    firstTextMs,
                )

                TranscriptionResult(
                    transcript = Transcript(
                        text = finalText,
                        languageCode = (languageHint as? LanguageHint.Fixed)?.languageCode,
                    ),
                    metrics = TranscriptionMetrics(
                        runtime = TranscriptionRuntime.GoogleAiEdgeLiteRtLm,
                        backendName = loadedEngine.backendName,
                        modelFileName = modelFileName,
                        warmStart = loadedEngine.warmStart,
                        modelLoadMs = loadedEngine.modelLoadMs,
                        firstTextMs = firstTextMs,
                        totalMs = totalMs,
                        audioDurationMs = audioDurationMs,
                        audioSecondsPerWallSecond = if (totalMs > 0L) {
                            audioDurationMs.toDouble() / totalMs.toDouble()
                        } else {
                            null
                        },
                        deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    ),
                )
            } finally {
                runCatching { conversation.close() }
            }
        }.onFailure { throwable ->
            Timber.tag(TAG).e(throwable, "Google AI Edge transcription failed")
        }
    }

    private fun ensureEngineLoaded(modelPath: String): LoadedEngine =
        synchronized(lock) {
            if (loadedModelPath == modelPath) {
                engine?.let { existing ->
                    return LoadedEngine(
                        engine = existing,
                        backendName = loadedBackendName ?: "unknown",
                        warmStart = true,
                        modelLoadMs = null,
                    )
                }
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
                    val created: Engine
                    val loadStartedAtNanos = System.nanoTime()
                    created = Engine(
                        EngineConfig(
                            modelPath = modelPath,
                            backend = backend,
                            visionBackend = null,
                            audioBackend = Backend.CPU(preferredThreadCount),
                            maxNumTokens = MAX_TOTAL_TOKENS,
                            maxNumImages = null,
                            cacheDir = context.cacheDir.absolutePath,
                        ),
                    )
                    created.initialize()
                    val loadMs = elapsedSince(loadStartedAtNanos)

                    engine = created
                    loadedModelPath = modelPath
                    loadedBackendName = backend.javaClass.simpleName
                    Timber.tag(TAG).i(
                        "Loaded Google AI Edge transcription modelPath=%s backend=%s loadMs=%d",
                        modelPath,
                        loadedBackendName,
                        loadMs,
                    )
                    return LoadedEngine(
                        engine = created,
                        backendName = loadedBackendName ?: backend.javaClass.simpleName,
                        warmStart = false,
                        modelLoadMs = loadMs,
                    )
                } catch (throwable: Throwable) {
                    loadErrors += "${backend.javaClass.simpleName}: ${throwable.message}"
                    Timber.tag(TAG).w(
                        throwable,
                        "Google AI Edge transcription backend init failed backend=%s",
                        backend.javaClass.simpleName,
                    )
                }
            }

            throw IllegalStateException(
                "Unable to initialize Google AI Edge transcription engine for `$modelPath`: ${loadErrors.joinToString()}",
            )
        }

    private fun preferredBackends(): List<Backend> = buildList {
        add(Backend.NPU(context.applicationInfo.nativeLibraryDir))
        add(Backend.GPU())
        add(Backend.CPU(preferredThreadCount))
        add(Backend.CPU())
    }.distinctBy { backend -> backend.javaClass.name + backend.toString() }

    private fun extractText(message: Message): String {
        val text = message.contents.contents
            .mapNotNull { content -> (content as? Content.Text)?.text }
            .joinToString(separator = "")
            .trim()
        return if (text.isNotEmpty()) text else message.toString().trim()
    }

    private fun elapsedSince(startedAtNanos: Long): Long = (System.nanoTime() - startedAtNanos) / 1_000_000L

    private data class LoadedEngine(
        val engine: Engine,
        val backendName: String,
        val warmStart: Boolean,
        val modelLoadMs: Long?,
    )

    private companion object {
        const val TAG = "GoogleAiEdgeTranscription"
        private const val MAX_TOTAL_TOKENS = 1_280

        private val preferredThreadCount: Int
            get() = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    }
}
