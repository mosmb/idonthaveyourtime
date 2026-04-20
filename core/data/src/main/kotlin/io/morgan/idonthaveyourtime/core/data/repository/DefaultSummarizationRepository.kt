package io.morgan.idonthaveyourtime.core.data.repository

import io.morgan.idonthaveyourtime.core.data.datasource.summarization.SummarizerEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.domain.repository.SummarizationRepository
import io.morgan.idonthaveyourtime.core.model.Summary
import javax.inject.Inject

internal class DefaultSummarizationRepository @Inject constructor(
    private val summarizer: SummarizerEngineLocalDataSource,
) : SummarizationRepository {
    override suspend fun prewarm(): Result<Unit> = summarizer.prewarm()

    override suspend fun mapChunk(
        transcriptChunk: String,
        languageCode: String?,
        onPartialResult: (String) -> Unit,
    ): Result<String> = summarizer.mapChunk(
        transcriptChunk = transcriptChunk,
        languageCode = languageCode,
        onPartialResult = onPartialResult,
    )

    override suspend fun reduce(
        chunkBulletSummaries: List<String>,
        languageCode: String?,
        onPartialResult: (String) -> Unit,
    ): Result<Summary> = summarizer.reduce(
        chunkBulletSummaries = chunkBulletSummaries,
        languageCode = languageCode,
        onPartialResult = onPartialResult,
    )
}
