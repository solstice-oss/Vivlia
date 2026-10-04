package org.solsticesw.vivlia.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [Index("entryId"), Index("chapterId")]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entryId: Long,
    val chapterId: Long,
    val pageIndex: Int,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
