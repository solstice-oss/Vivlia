package org.solsticesw.vivlia.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.solsticesw.vivlia.data.local.entity.LocalContentIndexEntity

@Dao
interface LocalContentIndexDao {
    @Query("SELECT * FROM local_content_index")
    suspend fun getAll(): List<LocalContentIndexEntity>

    @Query("SELECT * FROM local_content_index")
    fun getAllFlow(): Flow<List<LocalContentIndexEntity>>

    @Query("SELECT * FROM local_content_index WHERE entryId = :entryId LIMIT 1")
    suspend fun getByEntryId(entryId: Long): LocalContentIndexEntity?

    @Query("SELECT * FROM local_content_index WHERE rootUri = :rootUri")
    suspend fun getForRoot(rootUri: String): List<LocalContentIndexEntity>

    @Query("SELECT * FROM local_content_index WHERE contentUri = :contentUri LIMIT 1")
    suspend fun getByContentUri(contentUri: String): LocalContentIndexEntity?

    @Query("SELECT * FROM local_content_index WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun getByFingerprint(fingerprint: String): LocalContentIndexEntity?

    @Upsert
    suspend fun upsert(entity: LocalContentIndexEntity)

    @Query("UPDATE local_content_index SET available = 0 WHERE rootUri = :rootUri")
    suspend fun markRootUnavailable(rootUri: String)

    @Query("UPDATE local_content_index SET available = 0 WHERE entryId = :entryId")
    suspend fun markUnavailable(entryId: Long)

    @Query("UPDATE local_content_index SET available = 0 WHERE rootUri NOT IN (:rootUris)")
    suspend fun markRootsUnavailableExcept(rootUris: List<String>)

    @Query("DELETE FROM local_content_index WHERE entryId = :entryId")
    suspend fun delete(entryId: Long)
}
