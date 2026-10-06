package org.solsticesw.vivlia.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.solsticesw.vivlia.data.local.entity.ChapterEntity

@Dao
interface ChapterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity): Long

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Delete
    suspend fun deleteChapter(chapter: ChapterEntity)

    @Query("SELECT * FROM chapters WHERE entryId = :entryId ORDER BY chapterNumber DESC, id DESC")
    fun getChaptersForEntryFlow(entryId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE entryId = :entryId ORDER BY chapterNumber DESC, id DESC")
    suspend fun getChaptersForEntry(entryId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id LIMIT 1")
    suspend fun getChapterById(id: Long): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE entryId = :entryId AND url = :url LIMIT 1")
    suspend fun getChapterByUrl(entryId: Long, url: String): ChapterEntity?

    @Query("UPDATE chapters SET read = :read WHERE id = :chapterId")
    suspend fun markAsRead(chapterId: Long, read: Boolean)

    @Query("UPDATE chapters SET read = :read WHERE entryId = :entryId")
    suspend fun markAllAsReadForEntry(entryId: Long, read: Boolean)

    @Query("UPDATE chapters SET bookmark = :bookmark WHERE id = :chapterId")
    suspend fun updateBookmark(chapterId: Long, bookmark: Boolean)

    @Query("UPDATE chapters SET lastPageRead = :lastPage WHERE id = :chapterId")
    suspend fun updateLastPageRead(chapterId: Long, lastPage: Int)

    @Query("DELETE FROM chapters WHERE entryId = :entryId")
    suspend fun deleteChaptersForEntry(entryId: Long)

    @Query("DELETE FROM chapters WHERE id = :chapterId")
    suspend fun deleteChapterById(chapterId: Long)
}
