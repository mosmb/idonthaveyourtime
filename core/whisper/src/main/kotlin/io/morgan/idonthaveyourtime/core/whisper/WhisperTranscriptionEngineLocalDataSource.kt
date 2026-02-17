package io.morgan.idonthaveyourtime.core.whisper

import io.morgan.idonthaveyourtime.core.data.datasource.model.ModelLocatorLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.transcription.TranscriptionEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.data.di.DefaultDispatcher
import io.morgan.idonthaveyourtime.core.model.LanguageHint
import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.Transcript
import kotlin.system.measureTimeMillis
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

internal class WhisperTranscriptionEngineLocalDataSource @Inject constructor(
    private val modelLocator: ModelLocatorLocalDataSource,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : TranscriptionEngineLocalDataSource {

    private val nativeMutex = Mutex()
    private var cachedModelPath: String? = null
    private var cachedContextPtr: Long = 0L

    override suspend fun transcribe(
        audioData: FloatArray,
        languageHint: LanguageHint,
        onProgress: suspend (Float) -> Unit,
    ): Result<Transcript> = withContext(defaultDispatcher) {
        Timber.tag(TAG).i(
            "Transcription start audioSamples=%d languageHint=%s",
            audioData.size,
            languageHint,
        )

        runCatching {
            WhisperLibraryLoader.ensureLoaded()

            val cachedModelPathSnapshot = nativeMutex.withLock {
                cachedModelPath?.takeIf { cachedContextPtr != 0L }
            }

            val resolvedModelPath: String
            val modelResolveMs = measureTimeMillis {
                resolvedModelPath = cachedModelPathSnapshot
                    ?: modelLocator.getModelPath(ModelId.Whisper).getOrThrow()
            }

            if (cachedModelPathSnapshot == null) {
                Timber.tag(TAG).i(
                    "Whisper model ready path=%s sizeBytes=%d resolvedInMs=%d",
                    resolvedModelPath,
                    runCatching { java.io.File(resolvedModelPath).length() }.getOrDefault(-1L),
                    modelResolveMs,
                )
            }

            val language = resolveLanguage(languageHint)

            coroutineScope {
                val progressChannel = Channel<Float>(capacity = Channel.CONFLATED)
                val progressJob = launch(defaultDispatcher) {
                    for (progress in progressChannel) {
                        onProgress(progress.coerceIn(0f, 1f))
                    }
                }

                val progressCb = ProgressCb { percent ->
                    val normalized = (percent.coerceIn(0, 100) / 100f).coerceIn(0f, 1f)
                    progressChannel.trySend(normalized)
                }

                coroutineContext.job.invokeOnCompletion { throwable ->
                    if (throwable != null) {
                        runCatching { WhisperJni.requestAbort() }
                    }
                }

                try {
                    val numThreads = WhisperCpuConfig.preferredThreadCount
                    val audioSeconds = audioData.size.toDouble() / 16_000.0

                    var nativeElapsedMs = 0L
                    val (mergedText, detectedLanguage) = nativeMutex.withLock {
                        val contextPtr = ensureContextLocked(resolvedModelPath)

                        var resolvedLanguageCode: String? = null
                        var mergedText = ""

                        nativeElapsedMs = measureTimeMillis {
                            WhisperJni.fullTranscribe(
                                contextPtr = contextPtr,
                                numThreads = numThreads,
                                audioData = audioData,
                                language = language,
                                progressCb = progressCb,
                            )

                            resolvedLanguageCode = when (languageHint) {
                                LanguageHint.Auto -> WhisperJni.getDetectedLanguageCode(contextPtr)?.takeIf { it.isNotBlank() }
                                is LanguageHint.Fixed -> languageHint.languageCode.trim().takeIf { it.isNotEmpty() }
                            }

                            val segmentCount = WhisperJni.getSegmentCount(contextPtr)
                            mergedText = buildString {
                                for (index in 0 until segmentCount) {
                                    val segment = WhisperJni.getSegmentText(contextPtr, index).trim()
                                    if (segment.isEmpty()) continue
                                    if (isNotEmpty()) append('\n')
                                    append(segment)
                                }
                            }.trim()
                        }

                        mergedText to resolvedLanguageCode
                    }

                    val rtf = if (audioSeconds > 0.0) {
                        nativeElapsedMs.toDouble() / (audioSeconds * 1000.0)
                    } else {
                        Double.POSITIVE_INFINITY
                    }
                    Timber.tag(TAG).i(
                        "Whisper transcribe done threads=%d audioSec=%.2f elapsedMs=%d rtf=%.2f",
                        numThreads,
                        audioSeconds,
                        nativeElapsedMs,
                        rtf,
                    )

                    progressChannel.trySend(1f)

                    Transcript(
                        text = if (mergedText.isBlank()) "[empty transcription]" else mergedText,
                        languageCode = detectedLanguage,
                        segments = emptyList(),
                    )
                } finally {
                    progressChannel.close()
                    progressJob.join()
                }
            }
        }.onFailure { throwable ->
            Timber.tag(TAG).e(throwable, "Transcription failed")
        }
    }

    private fun resolveLanguage(languageHint: LanguageHint): String? = when (languageHint) {
        LanguageHint.Auto -> "auto"
        is LanguageHint.Fixed -> languageHint.languageCode.trim().takeIf { it.isNotEmpty() }
    }

    private fun ensureContextLocked(modelPath: String): Long {
        if (cachedContextPtr != 0L && cachedModelPath == modelPath) {
            return cachedContextPtr
        }

        if (cachedContextPtr != 0L) {
            runCatching { WhisperJni.freeContext(cachedContextPtr) }
            cachedContextPtr = 0L
            cachedModelPath = null
        }

        val contextPtr = WhisperJni.initContext(modelPath)
        require(contextPtr != 0L) { "Failed to initialize whisper context" }
        cachedContextPtr = contextPtr
        cachedModelPath = modelPath
        return contextPtr
    }

    private companion object {
        const val TAG = "WhisperTranscriptionEngineDataSource"
    }
}
