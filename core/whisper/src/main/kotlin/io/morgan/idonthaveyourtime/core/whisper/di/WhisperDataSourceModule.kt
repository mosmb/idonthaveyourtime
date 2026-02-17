package io.morgan.idonthaveyourtime.core.whisper.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.morgan.idonthaveyourtime.core.data.datasource.audio.AudioSampleReaderLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.transcription.TranscriptionEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.whisper.WavAudioSampleReaderLocalDataSource
import io.morgan.idonthaveyourtime.core.whisper.WhisperTranscriptionEngineLocalDataSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WhisperDataSourceModule {

    @Binds
    @Singleton
    abstract fun bindAudioSampleReader(impl: WavAudioSampleReaderLocalDataSource): AudioSampleReaderLocalDataSource

    @Binds
    @Singleton
    abstract fun bindTranscriptionEngine(impl: WhisperTranscriptionEngineLocalDataSource): TranscriptionEngineLocalDataSource
}

