package org.solsticesw.vivlia.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_sessions",
    indices = [Index("entryId"), Index("chapterId")]
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entryId: Long,
    val chapterId: Long,
    val startTime: Long,
    val endTime: Long,
    val progress: Float = 0f,
    val pagesRead: Int = 0
)
