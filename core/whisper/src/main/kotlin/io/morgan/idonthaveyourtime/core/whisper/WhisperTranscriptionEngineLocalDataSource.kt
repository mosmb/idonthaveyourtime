package io.morgan.idonthaveyourtime.core.whisper

import io.morgan.idonthaveyourtime.core.data.datasource.model.ModelLocatorLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.audio.AudioSampleReaderLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.transcription.TranscriptionEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.data.di.DefaultDispatcher
import io.morgan.idonthaveyourtime.core.model.LanguageHint
import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.TranscriptionEngineCapability
import io.morgan.idonthaveyourtime.core.model.TranscriptionEngineProbeResult
import io.morgan.idonthaveyourtime.core.model.TranscriptionMetrics
import io.morgan.idonthaveyourtime.core.model.TranscriptionModelFormat
import io.morgan.idonthaveyourtime.core.model.TranscriptionRequest
import io.morgan.idonthaveyourtime.core.model.TranscriptionResult
import io.morgan.idonthaveyourtime.core.model.TranscriptionRuntime
import io.morgan.idonthaveyourtime.core.model.Transcript
import java.io.File
import javax.inject.Inject
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class WhisperTranscriptionEngineLocalDataSource @Inject constructor(
    private val modelLocator: ModelLocatorLocalDataSource,
    private val audioSampleReader: AudioSampleReaderLocalDataSource,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : TranscriptionEngineLocalDataSource {

    private val nativeMutex = Mutex()
    private var cachedModelPath: String? = null
    private var cachedContextPtr: Long = 0L

    override fun capability(): TranscriptionEngineCapability =
        TranscriptionEngineCapability(
            runtime = TranscriptionRuntime.WhisperCpp,
            supportedFormats = setOf(TranscriptionModelFormat.WhisperBin),
            supportsStreaming = false,
            supportsAsyncGeneration = false,
            supportsHardwareAcceleration = false,
        )

    override suspend fun probe(): Result<TranscriptionEngineProbeResult> = runCatching {
        val modelPath = modelLocator.getModelPath(ModelId.Whisper).getOrThrow()
        val fileName = modelPath.substringAfterLast('/')
        val modelFormat = TranscriptionModelFormat.fromFileName(fileName)
        TranscriptionEngineProbeResult(
            requestedRuntime = TranscriptionRuntime.WhisperCpp,
            selectedRuntime = TranscriptionRuntime.WhisperCpp,
            modelFormat = modelFormat,
            modelFileName = fileName,
            supported = modelFormat == TranscriptionModelFormat.WhisperBin,
            supportsStreaming = capability().supportsStreaming,
            failureReason = if (modelFormat == TranscriptionModelFormat.WhisperBin) null else "Whisper requires a `.bin` model.",
        )
    }

    override suspend fun transcribe(
        request: TranscriptionRequest,
        languageHint: LanguageHint,
        onProgress: suspend (Float) -> Unit,
        onPartialResult: suspend (String) -> Unit,
    ): Result<TranscriptionResult> = withContext(defaultDispatcher) {
        Timber.tag(TAG).i(
            "Transcription start wavFile=%s startMs=%d endMs=%d languageHint=%s",
            request.wavFilePath,
            request.startMs,
            request.endMs,
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
                    runCatching { File(resolvedModelPath).length() }.getOrDefault(-1L),
                    modelResolveMs,
                )
            }

            val audioData = readAudioData(request)
            val audioDurationMs = (request.endMs - request.startMs).coerceAtLeast(0L)
            val audioSeconds = audioData.size.toDouble() / 16_000.0
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

                    val transcript = Transcript(
                        text = if (mergedText.isBlank()) "[empty transcription]" else mergedText,
                        languageCode = detectedLanguage,
                        segments = emptyList(),
                    )
                    onPartialResult(transcript.text)

                    TranscriptionResult(
                        transcript = transcript,
                        metrics = TranscriptionMetrics(
                            runtime = TranscriptionRuntime.WhisperCpp,
                            backendName = "whisper.cpp",
                            modelFileName = resolvedModelPath.substringAfterLast(File.separatorChar),
                            warmStart = cachedModelPathSnapshot != null,
                            modelLoadMs = if (cachedModelPathSnapshot == null) modelResolveMs else null,
                            firstTextMs = nativeElapsedMs,
                            totalMs = nativeElapsedMs,
                            audioDurationMs = audioDurationMs,
                            audioSecondsPerWallSecond = if (nativeElapsedMs > 0L) {
                                audioDurationMs.toDouble() / nativeElapsedMs.toDouble()
                            } else {
                                null
                            },
                        ),
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

    private suspend fun readAudioData(request: TranscriptionRequest): FloatArray =
        audioSampleReader.read16kMonoFloats(
            wavFilePath = request.wavFilePath,
            startMs = request.startMs,
            endMs = request.endMs,
        ).getOrThrow()

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
