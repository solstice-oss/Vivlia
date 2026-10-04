package org.solsticesw.vivlia.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "extensions",
    indices = [Index("repoId")]
)
data class ExtensionEntity(
    @PrimaryKey
    val id: String,
    val repoId: String,
    val name: String,
    val pkgName: String,
    val versionName: String,
    val versionCode: Int,
    val lang: String,
    val isNsfw: Boolean = false,
    val iconUrl: String? = null,
    val apkUrl: String? = null,
    val hasUpdate: Boolean = false,
    val installed: Boolean = false
)
