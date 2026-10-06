package org.solsticesw.vivlia.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_content_index",
    foreignKeys = [
        ForeignKey(
            entity = LibraryEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("rootUri"),
        Index("contentUri"),
        Index("fingerprint")
    ]
)
data class LocalContentIndexEntity(
    @PrimaryKey
    val entryId: Long,
    val rootUri: String,
    val contentUri: String,
    val relativePath: String,
    val lastModified: Long,
    val sizeBytes: Long,
    val fingerprint: String,
    val available: Boolean = true
)
