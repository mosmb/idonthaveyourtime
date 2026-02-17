package io.morgan.idonthaveyourtime.core.domain.repository

import io.morgan.idonthaveyourtime.core.model.AudioSegment
import io.morgan.idonthaveyourtime.core.model.SegmentationConfig
import io.morgan.idonthaveyourtime.core.model.WavAudio

/**
 * Performs local audio processing needed by the pipeline.
 *
 * This repository encapsulates audio decoding, conversion, segmentation, and sample extraction.
 */
interface AudioProcessingRepository {
    suspend fun toMono16kWav(
        inputFilePath: String,
        sessionId: String,
    ): Result<WavAudio>

    suspend fun segment16kMonoWav(
        wavFilePath: String,
        config: SegmentationConfig = SegmentationConfig(),
        onProgress: suspend (Float) -> Unit = {},
    ): Result<List<AudioSegment>>

    suspend fun read16kMonoFloats(
        wavFilePath: String,
        startMs: Long,
        endMs: Long,
    ): Result<FloatArray>
}
