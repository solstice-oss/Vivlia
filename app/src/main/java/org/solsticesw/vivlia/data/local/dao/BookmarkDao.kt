package org.solsticesw.vivlia.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.solsticesw.vivlia.data.local.entity.BookmarkEntity

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    @Query("SELECT * FROM bookmarks WHERE entryId = :entryId ORDER BY createdAt DESC")
    fun getBookmarksForEntryFlow(entryId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE chapterId = :chapterId ORDER BY pageIndex ASC")
    suspend fun getBookmarksForChapter(chapterId: Long): List<BookmarkEntity>
}
