package org.solsticesw.vivlia.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.solsticesw.vivlia.data.local.entity.PageEntity

@Dao
interface PageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<PageEntity>): List<Long>

    @Query("SELECT * FROM pages WHERE chapterId = :chapterId ORDER BY `index` ASC")
    suspend fun getPagesForChapter(chapterId: Long): List<PageEntity>

    @Query("SELECT * FROM pages WHERE chapterId = :chapterId ORDER BY `index` ASC")
    fun getPagesForChapterFlow(chapterId: Long): Flow<List<PageEntity>>

    @Query("DELETE FROM pages WHERE chapterId = :chapterId")
    suspend fun deletePagesForChapter(chapterId: Long)
}
