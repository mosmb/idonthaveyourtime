package io.morgan.idonthaveyourtime.core.data.datasource.audio

/**
 * Reads audio samples from a normalized WAV file.
 *
 * This is a **local** data source: implementations must not perform network I/O.
 */
interface AudioSampleReaderLocalDataSource {
    suspend fun read16kMonoFloats(
        wavFilePath: String,
        startMs: Long,
        endMs: Long,
    ): Result<FloatArray>

    suspend fun read16kMonoWavBytes(
        wavFilePath: String,
        startMs: Long,
        endMs: Long,
    ): Result<ByteArray>
}
