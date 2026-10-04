package org.solsticesw.vivlia.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.solsticesw.vivlia.data.local.entity.ExtensionEntity

@Dao
interface ExtensionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtensions(extensions: List<ExtensionEntity>)

    @Update
    suspend fun updateExtension(extension: ExtensionEntity)

    @Query("SELECT * FROM extensions WHERE repoId = :repoId ORDER BY name ASC")
    suspend fun getExtensionsForRepo(repoId: String): List<ExtensionEntity>

    @Query("SELECT * FROM extensions ORDER BY name ASC")
    fun getAllExtensionsFlow(): Flow<List<ExtensionEntity>>

    @Query("SELECT * FROM extensions ORDER BY name ASC")
    suspend fun getAllExtensions(): List<ExtensionEntity>

    @Query("SELECT * FROM extensions WHERE id = :id LIMIT 1")
    suspend fun getExtensionById(id: String): ExtensionEntity?

    @Query("DELETE FROM extensions WHERE repoId = :repoId")
    suspend fun deleteExtensionsForRepo(repoId: String)
}
