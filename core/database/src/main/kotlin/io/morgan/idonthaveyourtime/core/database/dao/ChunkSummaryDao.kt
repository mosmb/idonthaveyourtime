package io.morgan.idonthaveyourtime.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.morgan.idonthaveyourtime.core.database.model.ChunkSummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChunkSummaryDao {
    @Query("SELECT * FROM chunk_summaries WHERE session_id = :sessionId ORDER BY chunk_index ASC")
    fun observeBySession(sessionId: String): Flow<List<ChunkSummaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChunkSummaryEntity)
}

