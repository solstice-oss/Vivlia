package org.solsticesw.vivlia.ui.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.solsticesw.vivlia.data.local.entity.LibraryEntryEntity
import org.solsticesw.vivlia.data.local.entity.LocalContentIndexEntity
import org.solsticesw.vivlia.local.LocalStorageRepository
import org.solsticesw.vivlia.local.LocalStorageWorker
import java.io.IOException

data class LocalStorageUiState(
    val entries: List<LibraryEntryEntity> = emptyList(),
    val currentRoot: String = "",
    val isRootAccessible: Boolean = true,
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val workPhase: String? = null,
    val progress: Float = 0f,
    val storageBytes: Long = 0L,
    val selectedEntryIds: Set<Long> = emptySet(),
    val pendingDeleteId: Long? = null,
    val errorMessage: String? = null
)

private data class LocalStorageSnapshot(
    val entries: List<LibraryEntryEntity>,
    val rootUri: String?,
    val index: List<LocalContentIndexEntity>
)

private data class LocalStorageActions(
    val selectedIds: Set<Long>,
    val pendingDeleteId: Long?,
    val errorMessage: String?
)

class LocalStorageViewModel(
    context: Context,
    private val repository: LocalStorageRepository
) : ViewModel() {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val selectedEntryIds = MutableStateFlow<Set<Long>>(emptySet())
    private val pendingDeleteId = MutableStateFlow<Long?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val workFlow = workManager
        .getWorkInfosForUniqueWorkLiveData(LocalStorageWorker.UNIQUE_WORK_NAME)
        .asFlow()

    private val storageFlow = combine(
        repository.localEntries,
        repository.selectedRootUri,
        repository.indexedContent
    ) { entries, rootUri, index ->
        LocalStorageSnapshot(entries, rootUri, index)
    }

    private val actionsFlow = combine(
        selectedEntryIds,
        pendingDeleteId,
        errorMessage
    ) { selected, deleting, error ->
        LocalStorageActions(selected, deleting, error)
    }

    val uiState: StateFlow<LocalStorageUiState> = combine(
        storageFlow,
        actionsFlow,
        workFlow
    ) { storage, actions, works ->
        val activeWork = works.firstOrNull { !it.state.isFinished }
        val finishedWork = works.lastOrNull { it.state.isFinished }
        val complete = activeWork?.progress?.getInt(LocalStorageWorker.KEY_COMPLETE, 0) ?: 0
        val total = activeWork?.progress?.getInt(LocalStorageWorker.KEY_TOTAL, 0) ?: 0
        val inaccessible = finishedWork?.outputData
            ?.getStringArray(LocalStorageWorker.KEY_INACCESSIBLE_ROOTS)
            ?.toSet()
            .orEmpty()
        val workError = finishedWork?.outputData?.getString(LocalStorageWorker.KEY_ERROR)
        val workMessage = when {
            !workError.isNullOrBlank() -> workError
            storage.rootUri != null && storage.rootUri in inaccessible ->
                "The selected storage folder is unavailable. Re-select it to restore access."
            else -> null
        }
        LocalStorageUiState(
            entries = storage.entries,
            currentRoot = rootDisplayName(appContext, storage.rootUri),
            isRootAccessible = storage.rootUri == null || storage.rootUri !in inaccessible,
            isLoading = false,
            isWorking = activeWork != null,
            workPhase = activeWork?.progress?.getString(LocalStorageWorker.KEY_PHASE),
            progress = if (total > 0) (complete.toFloat() / total).coerceIn(0f, 1f) else 0f,
            storageBytes = storage.index.filter { it.available }.sumOf { it.sizeBytes },
            selectedEntryIds = actions.selectedIds,
            pendingDeleteId = actions.pendingDeleteId,
            errorMessage = actions.errorMessage ?: workMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LocalStorageUiState()
    )

    init {
        LocalStorageWorker.enqueueScan(appContext)
    }

    fun scan() {
        LocalStorageWorker.enqueueScan(appContext)
    }

    fun setRoot(uri: Uri) {
        viewModelScope.launch {
            errorMessage.value = null
            try {
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
                repository.setStorageRoot(uri)
                scan()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SecurityException) {
                errorMessage.value = error.message ?: "Access to the selected folder was denied."
            } catch (error: IOException) {
                errorMessage.value = error.message ?: "The selected folder cannot be used."
            }
        }
    }

    fun resetRoot() {
        viewModelScope.launch {
            errorMessage.value = null
            try {
                repository.resetStorageRoot()
                scan()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SecurityException) {
                errorMessage.value = error.message ?: "The storage permission could not be released."
            }
        }
    }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            errorMessage.value = null
            try {
                withContext(Dispatchers.IO) {
                    uris.forEach { uri ->
                        appContext.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
                LocalStorageWorker.enqueueImport(appContext, uris)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SecurityException) {
                errorMessage.value = error.message ?: "The selected files cannot be accessed."
            } catch (error: IllegalArgumentException) {
                errorMessage.value = error.message ?: "The selected files cannot be imported."
            }
        }
    }

    fun toggleSelected(entryId: Long) {
        selectedEntryIds.update { current ->
            if (entryId in current) current - entryId else current + entryId
        }
    }

    fun clearSelection() {
        selectedEntryIds.value = emptySet()
    }

    fun toggleFavorite(entryId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(entryId, !isFavorite)
        }
    }

    fun askDelete(entryId: Long) {
        pendingDeleteId.value = entryId
    }

    fun dismissDelete() {
        pendingDeleteId.value = null
    }

    fun confirmDelete() {
        val entryId = pendingDeleteId.value ?: return
        viewModelScope.launch {
            errorMessage.value = null
            try {
                repository.deleteLocalEntry(entryId)
                pendingDeleteId.value = null
                selectedEntryIds.update { it - entryId }
                scan()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                errorMessage.value = error.message ?: "The local content could not be deleted."
            } catch (error: SecurityException) {
                errorMessage.value = error.message ?: "Permission to delete this content was denied."
            }
        }
    }

    fun dismissError() {
        errorMessage.value = null
    }

    private companion object {
        fun rootDisplayName(context: Context, uriString: String?): String {
            if (uriString == null) return "App storage"
            val uri = Uri.parse(uriString)
            return DocumentFile.fromTreeUri(context, uri)?.name?.takeIf(String::isNotBlank)
                ?: DocumentFile.fromSingleUri(context, uri)?.name?.takeIf(String::isNotBlank)
                ?: "Selected folder"
        }
    }
}
