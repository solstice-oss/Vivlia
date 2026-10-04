package org.solsticesw.vivlia.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_manifests")
data class CachedManifestEntity(
    @PrimaryKey
    val repoUrl: String,
    val contentJson: String,
    val fetchedAt: Long = System.currentTimeMillis(),
    val eTag: String? = null
)
