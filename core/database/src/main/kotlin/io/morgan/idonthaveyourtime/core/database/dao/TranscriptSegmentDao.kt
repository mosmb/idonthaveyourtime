package io.morgan.idonthaveyourtime.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.morgan.idonthaveyourtime.core.database.model.TranscriptSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptSegmentDao {
    @Query("SELECT * FROM transcript_segments WHERE session_id = :sessionId ORDER BY segment_index ASC")
    fun observeBySession(sessionId: String): Flow<List<TranscriptSegmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TranscriptSegmentEntity)
}

