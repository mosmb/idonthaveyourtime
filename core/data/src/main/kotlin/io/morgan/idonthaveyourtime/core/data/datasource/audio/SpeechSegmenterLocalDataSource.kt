package io.morgan.idonthaveyourtime.core.data.datasource.audio

import io.morgan.idonthaveyourtime.core.model.AudioSegment
import io.morgan.idonthaveyourtime.core.model.SegmentationConfig

/**
 * Splits a normalized WAV file into speech segments for transcription.
 *
 * This is a **local** data source: implementations must not perform network I/O.
 */
interface SpeechSegmenterLocalDataSource {
    suspend fun segment16kMonoWav(
        wavFilePath: String,
        config: SegmentationConfig = SegmentationConfig(),
        onProgress: suspend (Float) -> Unit = {},
    ): Result<List<AudioSegment>>
}

