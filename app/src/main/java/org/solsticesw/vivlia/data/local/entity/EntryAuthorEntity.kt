package org.solsticesw.vivlia.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "entry_authors",
    indices = [Index("entryId")]
)
data class EntryAuthorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entryId: Long,
    val name: String,
    val role: String? = null
)
