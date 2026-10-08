package org.solsticesw.vivlia.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.solsticesw.vivlia.data.local.entity.SourceEntity

@Dao
interface SourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<SourceEntity>)

    @Upsert
    suspend fun upsertSources(sources: List<SourceEntity>)

    @Update
    suspend fun updateSource(source: SourceEntity)

    @Query("SELECT * FROM sources WHERE extensionId = :extensionId ORDER BY name ASC")
    suspend fun getSourcesForExtension(extensionId: String): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE repoId = :repoId ORDER BY name ASC")
    suspend fun getSourcesForRepo(repoId: String): List<SourceEntity>

    @Query("SELECT * FROM sources ORDER BY pinned DESC, name ASC")
    fun getAllSourcesFlow(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources ORDER BY pinned DESC, name ASC")
    suspend fun getAllSources(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id LIMIT 1")
    suspend fun getSourceById(id: String): SourceEntity?

    @Query("UPDATE sources SET pinned = :pinned WHERE id = :id")
    suspend fun updatePinned(id: String, pinned: Boolean)

    @Query("UPDATE sources SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM sources WHERE repoId = :repoId")
    suspend fun deleteSourcesForRepo(repoId: String)
}
