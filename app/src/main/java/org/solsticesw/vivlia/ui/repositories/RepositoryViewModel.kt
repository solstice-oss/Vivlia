package org.solsticesw.vivlia.ui.repositories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.solsticesw.vivlia.data.local.AppDatabase
import org.solsticesw.vivlia.data.local.entity.ExtensionRepositoryEntity
import org.solsticesw.vivlia.data.network.ExtensionRepositoryManager
import org.solsticesw.vivlia.data.network.UrlNormalizer
import org.solsticesw.vivlia.domain.model.ProviderType

data class RepositoryItemUiState(
    val repository: ExtensionRepositoryEntity,
    val extensionsCount: Int = 0,
    val sourcesCount: Int = 0,
    val statusBadge: String = "Active"
)

data class RepositoryUiState(
    val repositories: List<RepositoryItemUiState> = emptyList(),
    val inputUrl: String = "",
    val customName: String = "",
    val selectedProviderType: ProviderType? = null,
    val isTestingConnection: Boolean = false,
    val isAddingRepository: Boolean = false,
    val isRefreshingAll: Boolean = false,
    val showAddDialog: Boolean = false,
    val editingRepository: ExtensionRepositoryEntity? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class RepositoryViewModel(
    private val database: AppDatabase,
    private val repositoryManager: ExtensionRepositoryManager = ExtensionRepositoryManager(database)
) : ViewModel() {

    private val repositoryDao = database.repositoryDao()
    private val extensionDao = database.extensionDao()
    private val sourceDao = database.sourceDao()

    private val _inputUrl = MutableStateFlow("")
    private val _customName = MutableStateFlow("")
    private val _selectedProviderType = MutableStateFlow<ProviderType?>(null)
    private val _isTestingConnection = MutableStateFlow(false)
    private val _isAddingRepository = MutableStateFlow(false)
    private val _isRefreshingAll = MutableStateFlow(false)
    private val _showAddDialog = MutableStateFlow(false)
    private val _editingRepository = MutableStateFlow<ExtensionRepositoryEntity?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _successMessage = MutableStateFlow<String?>(null)

    private val _inputState = combine(_inputUrl, _customName, _selectedProviderType) { url, name, provider ->
        Triple(url, name, provider)
    }

    private val _flagsState = combine(_isTestingConnection, _isAddingRepository, _isRefreshingAll, _showAddDialog) { testing, adding, refreshing, show ->
        listOf(testing, adding, refreshing, show)
    }

    private val _messagesState = combine(_editingRepository, _errorMessage, _successMessage) { editing, err, success ->
        Triple(editing, err, success)
    }

    private val _dialogState = combine(_inputState, _flagsState, _messagesState) { input, flags, messages ->
        RepositoryUiState(
            inputUrl = input.first,
            customName = input.second,
            selectedProviderType = input.third,
            isTestingConnection = flags[0] as Boolean,
            isAddingRepository = flags[1] as Boolean,
            isRefreshingAll = flags[2] as Boolean,
            showAddDialog = flags[3] as Boolean,
            editingRepository = messages.first,
            errorMessage = messages.second,
            successMessage = messages.third
        )
    }

    val uiState: StateFlow<RepositoryUiState> = combine(
        repositoryDao.getAllRepositoriesFlow(),
        extensionDao.getAllExtensionsFlow(),
        sourceDao.getAllSourcesFlow(),
        _dialogState
    ) { repos, extensions, sources, state ->
        val items = repos.map { repo ->
            val extCount = extensions.count { it.repoId == repo.id }
            val srcCount = sources.count { it.repoId == repo.id }
            val status = if (repo.enabled) "Active" else "Disabled"
            RepositoryItemUiState(
                repository = repo,
                extensionsCount = extCount,
                sourcesCount = srcCount,
                statusBadge = status
            )
        }
        state.copy(repositories = items)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RepositoryUiState()
    )

    fun onInputUrlChanged(url: String) {
        _inputUrl.value = url
        // Auto-detect provider type based on URL
        if (url.contains("plugins.min.json")) {
            _selectedProviderType.value = ProviderType.LN_READER
        } else if (url.contains("index.json")) {
            _selectedProviderType.value = ProviderType.MIHON
        }
    }

    fun onCustomNameChanged(name: String) {
        _customName.value = name
    }

    fun onProviderTypeSelected(type: ProviderType?) {
        _selectedProviderType.value = type
    }

    fun openAddDialog() {
        _inputUrl.value = ""
        _customName.value = ""
        _selectedProviderType.value = null
        _errorMessage.value = null
        _showAddDialog.value = true
    }

    fun closeAddDialog() {
        _showAddDialog.value = false
    }

    fun addRepository() {
        val rawUrl = _inputUrl.value.trim()
        if (rawUrl.isBlank()) {
            _errorMessage.value = "URL cannot be empty"
            return
        }

        viewModelScope.launch {
            _isAddingRepository.value = true
            _errorMessage.value = null

            val result = repositoryManager.addRepository(
                inputUrl = rawUrl,
                customName = _customName.value.ifBlank { null },
                forcedProviderType = _selectedProviderType.value
            )

            _isAddingRepository.value = false
            result.onSuccess {
                _showAddDialog.value = false
                _successMessage.value = "Repository added successfully"
            }.onFailure { e ->
                _errorMessage.value = e.message ?: "Failed to add repository"
            }
        }
    }

    fun toggleRepositoryEnabled(repo: ExtensionRepositoryEntity) {
        viewModelScope.launch {
            val updated = repo.copy(enabled = !repo.enabled)
            repositoryDao.updateRepository(updated)
        }
    }

    fun refreshRepository(repoId: String) {
        viewModelScope.launch {
            _errorMessage.value = null
            val result = repositoryManager.refreshRepository(repoId)
            result.onSuccess {
                _successMessage.value = "Repository refreshed"
            }.onFailure { e ->
                _errorMessage.value = e.message ?: "Failed to refresh repository"
            }
        }
    }

    fun refreshAllRepositories() {
        viewModelScope.launch {
            _isRefreshingAll.value = true
            _errorMessage.value = null
            val results = repositoryManager.refreshAllRepositories()
            _isRefreshingAll.value = false
            val failures = results.count { it.isFailure }
            if (failures == 0) {
                _successMessage.value = "All repositories refreshed"
            } else {
                _errorMessage.value = "$failures repository refresh(es) failed"
            }
        }
    }

    fun deleteRepository(repoId: String) {
        viewModelScope.launch {
            repositoryManager.deleteRepository(repoId)
            _successMessage.value = "Repository deleted"
        }
    }

    fun testConnection() {
        val rawUrl = _inputUrl.value.trim()
        if (rawUrl.isBlank()) {
            _errorMessage.value = "Please enter a URL to test"
            return
        }

        viewModelScope.launch {
            _isTestingConnection.value = true
            _errorMessage.value = null
            try {
                val defaultIndex = if (_selectedProviderType.value == ProviderType.LN_READER || rawUrl.contains("plugins.min.json")) {
                    "plugins.min.json"
                } else {
                    "index.json"
                }
                val normalized = UrlNormalizer.normalize(rawUrl, defaultIndex)
                _successMessage.value = "URL normalized: ${normalized.indexUrl}"
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Invalid URL format"
            } finally {
                _isTestingConnection.value = false
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }
}
