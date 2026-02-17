package io.morgan.idonthaveyourtime.core.data.repository

import io.morgan.idonthaveyourtime.core.data.datasource.audio.AudioConverterLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.audio.AudioSampleReaderLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.audio.SpeechSegmenterLocalDataSource
import io.morgan.idonthaveyourtime.core.domain.repository.AudioProcessingRepository
import io.morgan.idonthaveyourtime.core.model.AudioSegment
import io.morgan.idonthaveyourtime.core.model.SegmentationConfig
import io.morgan.idonthaveyourtime.core.model.WavAudio
import javax.inject.Inject

internal class DefaultAudioProcessingRepository @Inject constructor(
    private val audioConverter: AudioConverterLocalDataSource,
    private val speechSegmenter: SpeechSegmenterLocalDataSource,
    private val audioSampleReader: AudioSampleReaderLocalDataSource,
) : AudioProcessingRepository {
    override suspend fun toMono16kWav(
        inputFilePath: String,
        sessionId: String,
    ): Result<WavAudio> = audioConverter.toMono16kWav(
        inputFilePath = inputFilePath,
        sessionId = sessionId,
    )

    override suspend fun segment16kMonoWav(
        wavFilePath: String,
        config: SegmentationConfig,
        onProgress: suspend (Float) -> Unit,
    ): Result<List<AudioSegment>> = speechSegmenter.segment16kMonoWav(
        wavFilePath = wavFilePath,
        config = config,
        onProgress = onProgress,
    )

    override suspend fun read16kMonoFloats(
        wavFilePath: String,
        startMs: Long,
        endMs: Long,
    ): Result<FloatArray> = audioSampleReader.read16kMonoFloats(
        wavFilePath = wavFilePath,
        startMs = startMs,
        endMs = endMs,
    )
}
