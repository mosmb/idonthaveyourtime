package io.morgan.idonthaveyourtime.core.data.repository

import io.morgan.idonthaveyourtime.core.data.datasource.summarization.SummarizerLocalDataSource
import io.morgan.idonthaveyourtime.core.domain.repository.SummarizationRepository
import io.morgan.idonthaveyourtime.core.model.Summary
import javax.inject.Inject

internal class DefaultSummarizationRepository @Inject constructor(
    private val summarizer: SummarizerLocalDataSource,
) : SummarizationRepository {
    override suspend fun mapChunk(
        transcriptChunk: String,
        languageCode: String?,
    ): Result<String> = summarizer.mapChunk(
        transcriptChunk = transcriptChunk,
        languageCode = languageCode,
    )

    override suspend fun reduce(
        chunkBulletSummaries: List<String>,
        languageCode: String?,
    ): Result<Summary> = summarizer.reduce(
        chunkBulletSummaries = chunkBulletSummaries,
        languageCode = languageCode,
    )
}
