package org.solsticesw.vivlia.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.solsticesw.vivlia.data.local.entity.ReadingSessionEntity

@Dao
interface ReadingSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReadingSessionEntity): Long

    @Query("SELECT * FROM reading_sessions WHERE entryId = :entryId ORDER BY startTime DESC")
    fun getSessionsForEntryFlow(entryId: Long): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions ORDER BY startTime DESC LIMIT :limit")
    fun getRecentSessionsFlow(limit: Int = 20): Flow<List<ReadingSessionEntity>>

    @Query("DELETE FROM reading_sessions WHERE entryId = :entryId")
    suspend fun deleteForEntry(entryId: Long)
}
