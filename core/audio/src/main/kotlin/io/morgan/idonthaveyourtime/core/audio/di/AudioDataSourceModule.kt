package io.morgan.idonthaveyourtime.core.audio.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.morgan.idonthaveyourtime.core.audio.EnergyVadSpeechSegmenterLocalDataSource
import io.morgan.idonthaveyourtime.core.audio.MediaCodecAudioConverterLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.audio.AudioConverterLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.audio.SpeechSegmenterLocalDataSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AudioDataSourceModule {

    @Binds
    @Singleton
    abstract fun bindAudioConverter(impl: MediaCodecAudioConverterLocalDataSource): AudioConverterLocalDataSource

    @Binds
    @Singleton
    abstract fun bindSpeechSegmenter(impl: EnergyVadSpeechSegmenterLocalDataSource): SpeechSegmenterLocalDataSource
}

