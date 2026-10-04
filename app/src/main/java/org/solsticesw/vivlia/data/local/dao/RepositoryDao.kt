package org.solsticesw.vivlia.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.solsticesw.vivlia.data.local.entity.CachedManifestEntity
import org.solsticesw.vivlia.data.local.entity.ExtensionRepositoryEntity

@Dao
interface RepositoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepository(repo: ExtensionRepositoryEntity)

    @Update
    suspend fun updateRepository(repo: ExtensionRepositoryEntity)

    @Query("DELETE FROM extension_repositories WHERE id = :id")
    suspend fun deleteRepository(id: String)

    @Query("SELECT * FROM extension_repositories WHERE id = :id LIMIT 1")
    suspend fun getRepositoryById(id: String): ExtensionRepositoryEntity?

    @Query("SELECT * FROM extension_repositories ORDER BY name ASC")
    fun getAllRepositoriesFlow(): Flow<List<ExtensionRepositoryEntity>>

    @Query("SELECT * FROM extension_repositories ORDER BY name ASC")
    suspend fun getAllRepositories(): List<ExtensionRepositoryEntity>

    // Cached Manifest operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedManifest(manifest: CachedManifestEntity)

    @Query("SELECT * FROM cached_manifests WHERE repoUrl = :repoUrl LIMIT 1")
    suspend fun getCachedManifest(repoUrl: String): CachedManifestEntity?

    @Query("DELETE FROM cached_manifests WHERE repoUrl = :repoUrl")
    suspend fun deleteCachedManifest(repoUrl: String)
}
