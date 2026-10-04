package org.solsticesw.vivlia.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pages",
    indices = [Index("chapterId")]
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chapterId: Long,
    val index: Int,
    val imageUrl: String? = null,
    val text: String? = null,
    val headersJson: String? = null
)
