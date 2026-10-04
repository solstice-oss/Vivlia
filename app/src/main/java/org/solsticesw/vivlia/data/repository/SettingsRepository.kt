package org.solsticesw.vivlia.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.solsticesw.vivlia.data.local.AppDatabase
import org.solsticesw.vivlia.data.local.entity.AppPreferenceEntity

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromString(value: String?): ThemeMode {
            return when (value?.uppercase()) {
                "LIGHT" -> LIGHT
                "DARK" -> DARK
                else -> SYSTEM
            }
        }
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val expressiveMotion: Boolean = true,
    val reducedMotion: Boolean = false,
    val autoRefreshRepos: Boolean = true
)

class SettingsRepository(private val database: AppDatabase) {
    private val preferenceDao = database.appPreferenceDao()

    val settingsFlow: Flow<AppSettings> = preferenceDao.getAllPreferencesFlow().map { prefs ->
        val map = prefs.associate { it.key to it.value }
        AppSettings(
            themeMode = ThemeMode.fromString(map["theme_mode"]),
            dynamicColor = map["dynamic_color"]?.toBooleanStrictOrNull() ?: true,
            expressiveMotion = map["expressive_motion"]?.toBooleanStrictOrNull() ?: true,
            reducedMotion = map["reduced_motion"]?.toBooleanStrictOrNull() ?: false,
            autoRefreshRepos = map["auto_refresh_repos"]?.toBooleanStrictOrNull() ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        setPref("theme_mode", mode.name)
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        setPref("dynamic_color", enabled.toString())
    }

    suspend fun setExpressiveMotion(enabled: Boolean) {
        setPref("expressive_motion", enabled.toString())
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        setPref("reduced_motion", enabled.toString())
    }

    suspend fun setAutoRefreshRepos(enabled: Boolean) {
        setPref("auto_refresh_repos", enabled.toString())
    }

    suspend fun clearCache() {
        database.repositoryDao().run {
            // clear cached manifests
        }
    }

    private suspend fun setPref(key: String, value: String) {
        preferenceDao.setPreference(
            AppPreferenceEntity(
                key = key,
                value = value,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
