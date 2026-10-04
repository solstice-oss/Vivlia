package org.solsticesw.vivlia.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sources",
    indices = [Index("extensionId"), Index("repoId")]
)
data class SourceEntity(
    @PrimaryKey
    val id: String,
    val extensionId: String,
    val repoId: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val iconUrl: String? = null,
    val providerType: String,
    val mediaType: String,
    val supportsLatest: Boolean = true,
    val isNsfw: Boolean = false,
    val pinned: Boolean = false,
    val enabled: Boolean = true
)
