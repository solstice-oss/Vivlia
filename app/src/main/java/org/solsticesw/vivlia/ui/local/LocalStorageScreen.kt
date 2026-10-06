package org.solsticesw.vivlia.ui.local

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.compose.stringResource
import org.solsticesw.vivlia.data.local.entity.LibraryEntryEntity
import org.solsticesw.vivlia.i18n.MR

@Composable
fun LocalStorageScreen(
    uiState: LocalStorageUiState,
    onScan: () -> Unit,
    onChangeRoot: (android.net.Uri) -> Unit,
    onResetRoot: () -> Unit,
    onImport: (List<android.net.Uri>) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onNavigateToDetails: (Long) -> Unit,
    onToggleSelected: (Long) -> Unit,
    onAskDelete: (Long) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rootPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(onChangeRoot)
    }
    val filesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onImport(uris)
    }
    val folderImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { onImport(listOf(it)) }
    }
    var showImportChoices by remember { mutableStateOf(false) }
    val selectedEntry = uiState.entries.firstOrNull { it.id == uiState.pendingDeleteId }

    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(MR.strings.local_storage_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                IconButton(onClick = onScan, enabled = !uiState.isWorking) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(MR.strings.local_scan))
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(MR.strings.local_storage_location), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = uiState.currentRoot,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.isRootAccessible) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { rootPicker.launch(null) },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(MR.strings.local_change_location)) }
                        if (uiState.currentRoot != stringResource(MR.strings.local_app_storage)) {
                            TextButton(onClick = onResetRoot) {
                                Text(stringResource(MR.strings.local_reset_location))
                            }
                        }
                    }
                }
            }

            if (uiState.isWorking) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { uiState.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = if (uiState.workPhase == "import") {
                            stringResource(MR.strings.local_importing)
                        } else {
                            stringResource(MR.strings.local_scanning)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            uiState.errorMessage?.let { message ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissError) { Text(stringResource(MR.strings.dismiss)) }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(MR.strings.local_storage_usage, formatBytes(uiState.storageBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { showImportChoices = true }, enabled = !uiState.isWorking) {
                    Text(stringResource(MR.strings.local_import))
                }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(40.dp))
                        Text(stringResource(MR.strings.local_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(MR.strings.local_empty_message),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.entries, key = { it.id }) { entry ->
                        LocalMangaRow(
                            entry = entry,
                            selected = entry.id in uiState.selectedEntryIds,
                            onClick = {
                                if (uiState.selectedEntryIds.isEmpty()) onNavigateToDetails(entry.id)
                                else onToggleSelected(entry.id)
                            },
                            onFavorite = { onToggleFavorite(entry.id, entry.inLibrary) },
                            onDelete = { onAskDelete(entry.id) },
                            onSelect = { onToggleSelected(entry.id) }
                        )
                    }
                }
            }
        }
    }

    if (showImportChoices) {
        AlertDialog(
            onDismissRequest = { showImportChoices = false },
            title = { Text(stringResource(MR.strings.local_import)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showImportChoices = false
                            filesPicker.launch(arrayOf("application/zip", "application/x-cbz", "application/pdf", "application/epub+zip", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(MR.strings.local_import_files)) }
                    OutlinedButton(
                        onClick = {
                            showImportChoices = false
                            folderImportPicker.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(MR.strings.local_import_folder)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportChoices = false }) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            }
        )
    }

    if (uiState.pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(MR.strings.local_delete_title)) },
            text = {
                Text(
                    stringResource(
                        MR.strings.local_delete_message,
                        selectedEntry?.title.orEmpty()
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) { Text(stringResource(MR.strings.delete)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) { Text(stringResource(MR.strings.action_cancel)) }
            }
        )
    }
}

@Composable
private fun LocalMangaRow(
    entry: LibraryEntryEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit
) {
    val favoriteDescription = stringResource(
        if (entry.inLibrary) MR.strings.local_remove_library else MR.strings.local_add_library,
        entry.title
    )
    val selectionDescription = stringResource(
        if (selected) MR.strings.local_selected_item else MR.strings.local_select_item,
        entry.title
    )
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!entry.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = entry.coverUrl,
                    contentDescription = stringResource(MR.strings.local_cover_description, entry.title),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp, 72.dp)
                )
            } else {
                Box(
                    modifier = Modifier.size(52.dp, 72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(entry.title.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(MR.strings.local_chapter_count, entry.totalChapters),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onFavorite,
                modifier = Modifier.semantics {
                    contentDescription = favoriteDescription
                }
            ) {
                Icon(
                    if (entry.inLibrary) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = if (entry.inLibrary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Checkbox(
                checked = selected,
                onCheckedChange = { onSelect() },
                modifier = Modifier.semantics {
                    contentDescription = selectionDescription
                }
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(MR.strings.local_delete_title))
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format("%.1f %s", value, units[unit])
}
