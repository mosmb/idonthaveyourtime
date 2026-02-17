package io.morgan.idonthaveyourtime.core.llm.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.morgan.idonthaveyourtime.core.data.datasource.summarization.SummarizerLocalDataSource
import io.morgan.idonthaveyourtime.core.llm.LlamaCppSummarizerLocalDataSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LlmDataSourceModule {

    @Binds
    @Singleton
    abstract fun bindSummarizer(impl: LlamaCppSummarizerLocalDataSource): SummarizerLocalDataSource
}

