package io.morgan.idonthaveyourtime.core.llm.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.morgan.idonthaveyourtime.core.data.di.LiteRtLmSummarizerEngine
import io.morgan.idonthaveyourtime.core.data.di.MediaPipeSummarizerEngine
import io.morgan.idonthaveyourtime.core.data.datasource.transcription.TranscriptionEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.llm.GoogleAiEdgeTranscriptionEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.summarization.SummarizerEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.llm.LiteRtLmSummarizerEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.llm.MediaPipeLlmInferenceSummarizerEngineLocalDataSource

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LlmDataSourceModule {

    @Binds
    abstract fun bindGoogleAiEdgeTranscription(
        impl: GoogleAiEdgeTranscriptionEngineLocalDataSource,
    ): TranscriptionEngineLocalDataSource

    @Binds
    @LiteRtLmSummarizerEngine
    abstract fun bindLiteRtLmSummarizer(
        impl: LiteRtLmSummarizerEngineLocalDataSource,
    ): SummarizerEngineLocalDataSource

    @Binds
    @MediaPipeSummarizerEngine
    abstract fun bindMediaPipeSummarizer(
        impl: MediaPipeLlmInferenceSummarizerEngineLocalDataSource,
    ): SummarizerEngineLocalDataSource
}
