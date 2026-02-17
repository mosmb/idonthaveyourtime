package io.morgan.idonthaveyourtime.core.domain.usecase

import io.morgan.idonthaveyourtime.core.common.ProcessingException
import io.morgan.idonthaveyourtime.core.domain.repository.AudioProcessingRepository
import io.morgan.idonthaveyourtime.core.domain.repository.ProcessingConfigRepository
import io.morgan.idonthaveyourtime.core.domain.repository.SessionRepository
import io.morgan.idonthaveyourtime.core.domain.repository.SummarizationRepository
import io.morgan.idonthaveyourtime.core.domain.repository.TranscriptionRepository
import io.morgan.idonthaveyourtime.core.domain.transcript.TranscriptOverlapMerger
import io.morgan.idonthaveyourtime.core.model.ChunkSummary
import io.morgan.idonthaveyourtime.core.model.LanguageHint
import io.morgan.idonthaveyourtime.core.model.ProcessingStage
import io.morgan.idonthaveyourtime.core.model.SegmentationConfig
import io.morgan.idonthaveyourtime.core.model.Summary
import io.morgan.idonthaveyourtime.core.model.Transcript
import io.morgan.idonthaveyourtime.core.model.TranscriptSegment
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class ProcessSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val audioProcessingRepository: AudioProcessingRepository,
    private val processingConfigRepository: ProcessingConfigRepository,
    private val transcriptionRepository: TranscriptionRepository,
    private val summarizationRepository: SummarizationRepository,
) {
    suspend operator fun invoke(sessionId: String): Result<Unit> = try {
        process(sessionId)
        Result.success(Unit)
    } catch (ce: CancellationException) {
        withContext(NonCancellable) {
            sessionRepository.markCancelled(sessionId)
        }
        Result.failure(ce)
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }

    private suspend fun process(sessionId: String) {
        val inputFilePath = sessionRepository.getInputFilePath(sessionId)
            ?: throw ProcessingException("MISSING_INPUT", "Session input file not found")

        val processingConfig = processingConfigRepository.getConfig()
        val segmentationConfig = SegmentationConfig(
            targetSpeechMs = processingConfig.segmentationTargetSpeechMs.coerceIn(10_000, 20_000),
            overlapMs = processingConfig.segmentationOverlapMs.coerceIn(300, 800),
        )
        val mapEverySegments = processingConfig.mapEverySegments.coerceIn(2, 4)

        sessionRepository.updateStage(
            sessionId = sessionId,
            stage = ProcessingStage.Converting,
            progress = 0f,
        )

        val wavAudio = audioProcessingRepository.toMono16kWav(
            inputFilePath = inputFilePath,
            sessionId = sessionId,
        ).getOrElse { throwable ->
            throw ProcessingException(
                code = "CONVERSION_FAILED",
                message = throwable.message ?: "Audio conversion failed",
                cause = throwable,
            )
        }

        sessionRepository.setWavFilePath(sessionId, wavAudio.filePath)
            .getOrElse { throwable ->
                throw ProcessingException(
                    code = "SESSION_UPDATE_FAILED",
                    message = throwable.message ?: "Unable to persist wav path",
                    cause = throwable,
                )
            }

        sessionRepository.updateStage(
            sessionId = sessionId,
            stage = ProcessingStage.Transcribing,
            progress = 0f,
        )

        val segments = audioProcessingRepository.segment16kMonoWav(
            wavFilePath = wavAudio.filePath,
            config = segmentationConfig,
            onProgress = { progress ->
                sessionRepository.updateStage(
                    sessionId = sessionId,
                    stage = ProcessingStage.Transcribing,
                    progress = (progress * 0.05f).coerceIn(0f, 0.05f),
                )
            },
        ).getOrElse { throwable ->
            throw ProcessingException(
                code = "SEGMENTATION_FAILED",
                message = throwable.message ?: "Unable to segment audio",
                cause = throwable,
            )
        }

        val totalMs = (wavAudio.durationMs ?: segments.maxOfOrNull { it.endMs } ?: 1L).coerceAtLeast(1L)

        var detectedLanguageCode: String? = null
        val transcriptSegmentsForSummary = mutableListOf<TranscriptSegment>()
        val chunkBullets = mutableListOf<String>()

        val transcriptBuilder = StringBuilder()
        var previousRawText: String? = null

        if (segments.isEmpty()) {
            sessionRepository.setTranscript(
                sessionId = sessionId,
                transcript = Transcript(
                    text = "[no speech detected]",
                    languageCode = null,
                    segments = emptyList(),
                ),
            ).getOrElse { throwable ->
                throw ProcessingException(
                    code = "SESSION_UPDATE_FAILED",
                    message = throwable.message ?: "Unable to persist transcript",
                    cause = throwable,
                )
            }

            sessionRepository.updateStage(
                sessionId = sessionId,
                stage = ProcessingStage.Transcribing,
                progress = 1f,
            )
        } else {
            segments.forEachIndexed { index, segment ->
                val audioData = audioProcessingRepository.read16kMonoFloats(
                    wavFilePath = wavAudio.filePath,
                    startMs = segment.startMs,
                    endMs = segment.endMs,
                ).getOrElse { throwable ->
                    throw ProcessingException(
                        code = "AUDIO_READ_FAILED",
                        message = throwable.message ?: "Unable to read audio segment",
                        cause = throwable,
                    )
                }

                val languageHint = detectedLanguageCode?.let { code ->
                    LanguageHint.Fixed(code)
                } ?: LanguageHint.Auto

                val segmentTranscript = transcriptionRepository.transcribe(
                    audioData = audioData,
                    languageHint = languageHint,
                    onProgress = {},
                ).getOrElse { throwable ->
                    throw ProcessingException(
                        code = "TRANSCRIPTION_FAILED",
                        message = throwable.message ?: "Transcription failed",
                        cause = throwable,
                    )
                }

                if (detectedLanguageCode.isNullOrBlank()) {
                    detectedLanguageCode = segmentTranscript.languageCode
                }

                val rawText = segmentTranscript.text.trim()
                val mergedText = previousRawText?.let { prev ->
                    TranscriptOverlapMerger.dropBestOverlapPrefix(prev, rawText)
                } ?: rawText
                previousRawText = rawText

                if (mergedText.isNotBlank()) {
                    if (transcriptBuilder.isNotEmpty()) {
                        transcriptBuilder.append('\n')
                    }
                    transcriptBuilder.append(mergedText)
                }

                val transcriptSegment = TranscriptSegment(
                    startMs = segment.startMs,
                    endMs = segment.endMs,
                    text = mergedText,
                )
                transcriptSegmentsForSummary += transcriptSegment

                sessionRepository.upsertTranscriptSegment(sessionId, index, transcriptSegment)
                    .getOrElse { throwable ->
                        throw ProcessingException(
                            code = "SESSION_UPDATE_FAILED",
                            message = throwable.message ?: "Unable to persist transcript segment",
                            cause = throwable,
                        )
                    }

                sessionRepository.setTranscript(
                    sessionId = sessionId,
                    transcript = Transcript(
                        text = transcriptBuilder.toString(),
                        languageCode = detectedLanguageCode,
                    ),
                ).getOrElse { throwable ->
                    throw ProcessingException(
                        code = "SESSION_UPDATE_FAILED",
                        message = throwable.message ?: "Unable to persist transcript",
                        cause = throwable,
                    )
                }

                val ratio = (segment.endMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                sessionRepository.updateStage(
                    sessionId = sessionId,
                    stage = ProcessingStage.Transcribing,
                    progress = (0.05f + (0.95f * ratio)).coerceIn(0.05f, 1f),
                )
            }
        }

        sessionRepository.updateStage(
            sessionId = sessionId,
            stage = ProcessingStage.Summarizing,
            progress = 0f,
        )

        val nonBlankCount = transcriptSegmentsForSummary.count { segment ->
            segment.text.trim().isNotBlank()
        }
        val chunkCount = if (nonBlankCount == 0) 0 else (nonBlankCount + mapEverySegments - 1) / mapEverySegments

        var chunkIndex = 0
        var segmentCount = 0
        var chunkStartMs: Long? = null
        var chunkEndMs: Long? = null
        val chunkText = StringBuilder()

        suspend fun flushChunk() {
            val text = chunkText.toString().trim()
            val start = chunkStartMs
            val end = chunkEndMs
            if (text.isBlank() || start == null || end == null) {
                chunkText.setLength(0)
                chunkStartMs = null
                chunkEndMs = null
                return
            }

            val bullets = summarizationRepository.mapChunk(
                transcriptChunk = text,
                languageCode = detectedLanguageCode,
            ).getOrElse { throwable ->
                throw ProcessingException(
                    code = "SUMMARY_MAP_FAILED",
                    message = throwable.message ?: "Unable to summarize transcript chunk",
                    cause = throwable,
                )
            }.trim()

            val chunk = ChunkSummary(
                index = chunkIndex,
                startMs = start,
                endMs = end,
                bulletsText = bullets,
            )

            sessionRepository.upsertChunkSummary(sessionId, chunk)
                .getOrElse { throwable ->
                    throw ProcessingException(
                        code = "SUMMARY_CACHE_FAILED",
                        message = throwable.message ?: "Unable to persist chunk summary",
                        cause = throwable,
                    )
                }

            if (bullets.isNotBlank()) {
                chunkBullets += bullets
                val liveBullets = chunkBullets.joinToString(separator = "\n\n") { it.trim() }.trim()
                sessionRepository.setSummaryPartial(
                    sessionId = sessionId,
                    summaryText = liveBullets,
                ).getOrElse { throwable ->
                    throw ProcessingException(
                        code = "SUMMARY_PARTIAL_FAILED",
                        message = throwable.message ?: "Unable to persist partial summary",
                        cause = throwable,
                    )
                }
            }

            chunkIndex += 1
            if (chunkCount > 0) {
                val progress = (0.85f * (chunkIndex.toFloat() / chunkCount.toFloat())).coerceIn(0f, 0.85f)
                sessionRepository.updateStage(
                    sessionId = sessionId,
                    stage = ProcessingStage.Summarizing,
                    progress = progress,
                )
            }

            chunkText.setLength(0)
            chunkStartMs = null
            chunkEndMs = null
        }

        for (segment in transcriptSegmentsForSummary) {
            val segmentText = segment.text.trim()
            if (segmentText.isBlank()) continue

            if (chunkText.isNotEmpty()) {
                chunkText.append('\n')
            }
            if (chunkStartMs == null) {
                chunkStartMs = segment.startMs
            }
            chunkEndMs = segment.endMs
            chunkText.append(segmentText)

            segmentCount += 1
            if (segmentCount % mapEverySegments == 0) {
                flushChunk()
            }
        }

        if (chunkText.isNotEmpty()) {
            flushChunk()
        }

        sessionRepository.updateStage(
            sessionId = sessionId,
            stage = ProcessingStage.Summarizing,
            progress = 0.85f,
        )

        val summary = summarizationRepository.reduce(
            chunkBulletSummaries = chunkBullets.toList(),
            languageCode = detectedLanguageCode,
        ).getOrElse { throwable ->
            throw ProcessingException(
                code = "SUMMARY_REDUCE_FAILED",
                message = throwable.message ?: "Unable to generate final summary",
                cause = throwable,
            )
        }

        sessionRepository.updateStage(
            sessionId = sessionId,
            stage = ProcessingStage.Summarizing,
            progress = 0.95f,
        )

        val finalTranscript = Transcript(
            text = transcriptBuilder.toString().ifBlank { "[empty transcription]" },
            languageCode = detectedLanguageCode,
        )

        sessionRepository.setSuccess(sessionId, finalTranscript, summary)
            .getOrElse { throwable ->
                throw ProcessingException(
                    code = "SESSION_COMPLETE_FAILED",
                    message = throwable.message ?: "Unable to persist success result",
                    cause = throwable,
                )
            }
    }
}
