package io.morgan.idonthaveyourtime.core.llm.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.morgan.idonthaveyourtime.core.data.datasource.transcription.TranscriptionBackend
import io.morgan.idonthaveyourtime.core.data.datasource.transcription.TranscriptionEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.llm.GoogleAiEdgeTranscriptionEngineLocalDataSource
import dagger.multibindings.IntoSet
import io.morgan.idonthaveyourtime.core.data.datasource.summarization.SummarizerBackend
import io.morgan.idonthaveyourtime.core.data.datasource.summarization.SummarizerEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.llm.LiteRtLmSummarizerEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.llm.MediaPipeLlmInferenceSummarizerEngineLocalDataSource

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LlmDataSourceModule {

    @Binds
    @IntoSet
    @TranscriptionBackend
    abstract fun bindGoogleAiEdgeTranscription(
        impl: GoogleAiEdgeTranscriptionEngineLocalDataSource,
    ): TranscriptionEngineLocalDataSource

    @Binds
    @IntoSet
    @SummarizerBackend
    abstract fun bindLiteRtLmSummarizer(
        impl: LiteRtLmSummarizerEngineLocalDataSource,
    ): SummarizerEngineLocalDataSource

    @Binds
    @IntoSet
    @SummarizerBackend
    abstract fun bindMediaPipeSummarizer(
        impl: MediaPipeLlmInferenceSummarizerEngineLocalDataSource,
    ): SummarizerEngineLocalDataSource
}
