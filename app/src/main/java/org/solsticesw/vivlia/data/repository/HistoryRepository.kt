package org.solsticesw.vivlia.data.repository

import kotlinx.coroutines.flow.Flow
import org.solsticesw.vivlia.data.local.AppDatabase
import org.solsticesw.vivlia.data.local.entity.ReadingSessionEntity

class HistoryRepository(private val database: AppDatabase) {
    private val readingSessionDao = database.readingSessionDao()

    fun getRecentHistoryFlow(limit: Int = 30): Flow<List<ReadingSessionEntity>> {
        return readingSessionDao.getRecentSessionsFlow(limit)
    }

    suspend fun recordReadingSession(entryId: Long, chapterId: Long, progress: Float) {
        val now = System.currentTimeMillis()
        val session = ReadingSessionEntity(
            entryId = entryId,
            chapterId = chapterId,
            startTime = now,
            endTime = now,
            progress = progress
        )
        readingSessionDao.insertSession(session)
    }

    suspend fun clearHistory() {
        database.runInTransaction {
            database.openHelper.writableDatabase.execSQL("DELETE FROM reading_sessions")
        }
    }
}
