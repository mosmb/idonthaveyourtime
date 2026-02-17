package io.morgan.idonthaveyourtime.core.whisper

import io.morgan.idonthaveyourtime.core.data.datasource.audio.AudioSampleReaderLocalDataSource
import io.morgan.idonthaveyourtime.core.data.di.IoDispatcher
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class WavAudioSampleReaderLocalDataSource @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AudioSampleReaderLocalDataSource {
    override suspend fun read16kMonoFloats(
        wavFilePath: String,
        startMs: Long,
        endMs: Long,
    ): Result<FloatArray> = withContext(ioDispatcher) {
        runCatching {
            WavSegmentReader(File(wavFilePath)).use { reader ->
                reader.readFloats(startMs = startMs, endMs = endMs)
            }
        }
    }
}
