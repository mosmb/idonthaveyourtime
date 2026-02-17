package io.morgan.idonthaveyourtime.core.data.datasource.audio

import io.morgan.idonthaveyourtime.core.model.WavAudio

/**
 * Converts input audio files into a normalized WAV format used by the processing pipeline.
 *
 * This is a **local** data source: implementations must not perform network I/O.
 */
interface AudioConverterLocalDataSource {
    suspend fun toMono16kWav(
        inputFilePath: String,
        sessionId: String,
    ): Result<WavAudio>
}

