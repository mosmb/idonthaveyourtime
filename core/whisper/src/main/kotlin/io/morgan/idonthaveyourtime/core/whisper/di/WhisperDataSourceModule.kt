package io.morgan.idonthaveyourtime.core.whisper.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.morgan.idonthaveyourtime.core.data.datasource.transcription.TranscriptionEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.transcription.TranscriptionBackend
import io.morgan.idonthaveyourtime.core.whisper.WhisperTranscriptionEngineLocalDataSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WhisperDataSourceModule {

    @Binds
    @IntoSet
    @TranscriptionBackend
    @Singleton
    abstract fun bindTranscriptionEngine(impl: WhisperTranscriptionEngineLocalDataSource): TranscriptionEngineLocalDataSource
}
