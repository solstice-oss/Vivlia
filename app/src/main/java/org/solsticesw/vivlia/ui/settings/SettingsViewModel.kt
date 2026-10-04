package org.solsticesw.vivlia.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.solsticesw.vivlia.data.repository.AppSettings
import org.solsticesw.vivlia.data.repository.SettingsRepository
import org.solsticesw.vivlia.data.repository.ThemeMode

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val cacheClearedMessage: String? = null
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _cacheMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsFlow,
        _cacheMessage
    ) { appSettings, cacheMsg ->
        SettingsUiState(
            settings = appSettings,
            cacheClearedMessage = cacheMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(themeMode)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicColor(enabled)
        }
    }

    fun setExpressiveMotion(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setExpressiveMotion(enabled)
        }
    }

    fun setReducedMotion(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReducedMotion(enabled)
        }
    }

    fun setAutoRefreshRepos(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoRefreshRepos(enabled)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            settingsRepository.clearCache()
            _cacheMessage.value = "Cache cleared successfully"
        }
    }

    fun dismissCacheMessage() {
        _cacheMessage.value = null
    }
}
