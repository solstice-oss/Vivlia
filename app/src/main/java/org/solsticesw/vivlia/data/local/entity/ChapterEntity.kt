package org.solsticesw.vivlia.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapters",
    indices = [
        Index("entryId"),
        Index(value = ["entryId", "url"], unique = true)
    ]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entryId: Long,
    val url: String,
    val name: String,
    val chapterNumber: Float = -1f,
    val dateUploaded: Long = 0L,
    val scanlator: String? = null,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val lastPageRead: Int = 0,
    val totalPages: Int = 0,
    val fetchedAt: Long = 0L,
    val downloaded: Boolean = false
)
