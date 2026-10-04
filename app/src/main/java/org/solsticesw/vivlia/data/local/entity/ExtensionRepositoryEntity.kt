package org.solsticesw.vivlia.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "extension_repositories")
data class ExtensionRepositoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val url: String,
    val rawUrl: String,
    val type: String,
    val lastRefreshedAt: Long = 0L,
    val isCustom: Boolean = true,
    val enabled: Boolean = true,
    val metaJson: String? = null
)
