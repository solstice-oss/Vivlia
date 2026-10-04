package org.solsticesw.vivlia.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.solsticesw.vivlia.data.local.entity.AppPreferenceEntity

@Dao
interface AppPreferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreference(preference: AppPreferenceEntity)

    @Query("SELECT value FROM app_preferences WHERE `key` = :key LIMIT 1")
    suspend fun getPreference(key: String): String?

    @Query("SELECT value FROM app_preferences WHERE `key` = :key LIMIT 1")
    fun getPreferenceFlow(key: String): Flow<String?>

    @Query("SELECT * FROM app_preferences")
    fun getAllPreferencesFlow(): Flow<List<AppPreferenceEntity>>

    @Query("DELETE FROM app_preferences WHERE `key` = :key")
    suspend fun deletePreference(key: String)

    @Query("DELETE FROM app_preferences")
    suspend fun clearAllPreferences()
}
