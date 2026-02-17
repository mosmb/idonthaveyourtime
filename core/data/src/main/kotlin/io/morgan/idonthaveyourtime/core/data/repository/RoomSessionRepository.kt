package io.morgan.idonthaveyourtime.core.data.repository

import io.morgan.idonthaveyourtime.core.data.mapper.asEntity
import io.morgan.idonthaveyourtime.core.data.mapper.asModel
import io.morgan.idonthaveyourtime.core.database.dao.ChunkSummaryDao
import io.morgan.idonthaveyourtime.core.database.dao.SessionDao
import io.morgan.idonthaveyourtime.core.database.dao.TranscriptSegmentDao
import io.morgan.idonthaveyourtime.core.database.model.ChunkSummaryEntity
import io.morgan.idonthaveyourtime.core.database.model.TranscriptSegmentEntity
import io.morgan.idonthaveyourtime.core.domain.repository.SessionRepository
import io.morgan.idonthaveyourtime.core.model.ChunkSummary
import io.morgan.idonthaveyourtime.core.model.ProcessingSession
import io.morgan.idonthaveyourtime.core.model.ProcessingStage
import io.morgan.idonthaveyourtime.core.model.Summary
import io.morgan.idonthaveyourtime.core.model.Transcript
import io.morgan.idonthaveyourtime.core.model.TranscriptSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import timber.log.Timber

internal class RoomSessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val transcriptSegmentDao: TranscriptSegmentDao,
    private val chunkSummaryDao: ChunkSummaryDao,
) : SessionRepository {

    override fun observeSession(sessionId: String): Flow<ProcessingSession?> =
        sessionDao.observeById(sessionId).map { entity -> entity?.asModel() }

    override fun observeRecentSessions(limit: Int): Flow<List<ProcessingSession>> =
        sessionDao.observeRecent(limit).map { entities -> entities.map { it.asModel() } }

    override fun observeChunkSummaries(sessionId: String): Flow<List<ChunkSummary>> =
        chunkSummaryDao.observeBySession(sessionId).map { entities ->
            entities.map { entity ->
                ChunkSummary(
                    index = entity.chunkIndex,
                    startMs = entity.startMs,
                    endMs = entity.endMs,
                    bulletsText = entity.bulletsText,
                )
            }
        }

    override suspend fun getSession(sessionId: String): ProcessingSession? =
        sessionDao.getById(sessionId)?.asModel()

    override suspend fun createSession(session: ProcessingSession, inputFilePath: String): Result<Unit> =
        runCatching {
            Timber.tag(TAG).i(
                "Session create id=%s stage=%s input=%s sourceName=%s mime=%s durationMs=%s",
                session.id,
                session.stage,
                inputFilePath,
                session.sourceName,
                session.mimeType,
                session.durationMs,
            )
            sessionDao.upsert(session.asEntity(inputFilePath = inputFilePath, wavFilePath = null))
        }.onFailure { throwable ->
            Timber.tag(TAG).e(throwable, "Session create failed id=%s", session.id)
        }

    override suspend fun updateStage(sessionId: String, stage: ProcessingStage, progress: Float): Result<Unit> =
        runCatching {
            Timber.tag(TAG).d("Session stage id=%s stage=%s progress=%.2f", sessionId, stage, progress)
            sessionDao.updateStage(sessionId = sessionId, stage = stage.name, progress = progress)
        }.onFailure { throwable ->
            Timber.tag(TAG).e(throwable, "Session stage update failed id=%s stage=%s", sessionId, stage)
        }

    override suspend fun setWavFilePath(sessionId: String, wavFilePath: String): Result<Unit> =
        runCatching {
            Timber.tag(TAG).i("Session wavPath id=%s wav=%s", sessionId, wavFilePath)
            sessionDao.updateWavPath(sessionId, wavFilePath)
        }.onFailure { throwable ->
            Timber.tag(TAG).e(throwable, "Session wavPath update failed id=%s wav=%s", sessionId, wavFilePath)
        }

    override suspend fun setTranscript(sessionId: String, transcript: Transcript): Result<Unit> =
        runCatching {
            Timber.tag(TAG).i(
                "Session transcript id=%s textLength=%d language=%s",
                sessionId,
                transcript.text.length,
                transcript.languageCode,
            )
            sessionDao.updateTranscript(
                sessionId = sessionId,
                transcript = transcript.text,
                languageCode = transcript.languageCode,
            )
        }.onFailure { throwable ->
            Timber.tag(TAG).e(throwable, "Session transcript update failed id=%s", sessionId)
        }

    override suspend fun upsertTranscriptSegment(sessionId: String, index: Int, segment: TranscriptSegment): Result<Unit> =
        runCatching {
            transcriptSegmentDao.upsert(
                TranscriptSegmentEntity(
                    sessionId = sessionId,
                    segmentIndex = index,
                    startMs = segment.startMs,
                    endMs = segment.endMs,
                    text = segment.text,
                )
            )
        }.onFailure { throwable ->
            Timber.tag(TAG).e(throwable, "Session segment upsert failed id=%s index=%d", sessionId, index)
        }

    override suspend fun upsertChunkSummary(sessionId: String, chunk: ChunkSummary): Result<Unit> =
        runCatching {
            chunkSummaryDao.upsert(
                ChunkSummaryEntity(
                    sessionId = sessionId,
                    chunkIndex = chunk.index,
                    startMs = chunk.startMs,
                    endMs = chunk.endMs,
                    bulletsText = chunk.bulletsText,
                )
            )
        }.onFailure { throwable ->
            Timber.tag(TAG).e(throwable, "Session chunk summary upsert failed id=%s index=%d", sessionId, chunk.index)
        }

    override suspend fun setSummaryPartial(sessionId: String, summaryText: String): Result<Unit> =
        runCatching {
            sessionDao.updateSummary(sessionId, summaryText)
        }.onFailure { throwable ->
            Timber.tag(TAG).e(throwable, "Session summary update failed id=%s", sessionId)
        }

    override suspend fun getInputFilePath(sessionId: String): String? = sessionDao.getInputFilePath(sessionId)

    override suspend fun getWavFilePath(sessionId: String): String? = sessionDao.getWavFilePath(sessionId)

    override suspend fun setSuccess(sessionId: String, transcript: Transcript, summary: Summary): Result<Unit> =
        runCatching {
            Timber.tag(TAG).i(
                "Session success id=%s transcriptLength=%d summaryLength=%d language=%s",
                sessionId,
                transcript.text.length,
                summary.text.length,
                transcript.languageCode,
            )
            sessionDao.updateSuccess(
                sessionId = sessionId,
                stage = ProcessingStage.Success.name,
                transcript = transcript.text,
                summary = summary.text,
                languageCode = transcript.languageCode,
            )
        }.onFailure { throwable ->
            Timber.tag(TAG).e(throwable, "Session success update failed id=%s", sessionId)
        }

    override suspend fun setError(sessionId: String, errorCode: String, errorMessage: String): Result<Unit> =
        runCatching {
            Timber.tag(TAG).e("Session error id=%s code=%s message=%s", sessionId, errorCode, errorMessage)
            sessionDao.updateError(
                sessionId = sessionId,
                stage = ProcessingStage.Error.name,
                errorCode = errorCode,
                errorMessage = errorMessage,
            )
        }.onFailure { throwable ->
            Timber.tag(TAG).e(throwable, "Session error update failed id=%s code=%s", sessionId, errorCode)
        }

    override suspend fun markCancelled(sessionId: String): Result<Unit> =
        runCatching {
            Timber.tag(TAG).i("Session cancelled id=%s", sessionId)
            sessionDao.updateStage(
                sessionId = sessionId,
                stage = ProcessingStage.Cancelled.name,
                progress = 0f,
            )
        }.onFailure { throwable ->
            Timber.tag(TAG).e(throwable, "Session cancel update failed id=%s", sessionId)
        }

    private companion object {
        const val TAG = "SessionRepository"
    }
}
