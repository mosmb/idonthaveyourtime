package io.morgan.idonthaveyourtime.core.domain.repository

/**
 * Schedules and cancels background processing for sessions.
 *
 * Implementations are responsible for executing processing reliably, even if the app process dies.
 */
interface ProcessingQueueRepository {
    suspend fun enqueue(sessionId: String): Result<Unit>
    suspend fun cancel(sessionId: String): Result<Unit>
}
