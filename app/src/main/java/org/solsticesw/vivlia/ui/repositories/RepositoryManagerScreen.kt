package org.solsticesw.vivlia.ui.repositories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.compose.stringResource
import org.solsticesw.vivlia.data.local.entity.ExtensionRepositoryEntity
import org.solsticesw.vivlia.domain.model.ProviderType
import org.solsticesw.vivlia.i18n.MR
import org.solsticesw.vivlia.ui.theme.VivliaTheme

@Composable
fun RepositoryManagerScreen(
    uiState: RepositoryUiState,
    onOpenAddDialog: () -> Unit,
    onCloseAddDialog: () -> Unit,
    onInputUrlChanged: (String) -> Unit,
    onCustomNameChanged: (String) -> Unit,
    onProviderTypeSelected: (ProviderType?) -> Unit,
    onTestConnection: () -> Unit,
    onAddRepository: () -> Unit,
    onToggleRepositoryEnabled: (ExtensionRepositoryEntity) -> Unit,
    onRefreshRepository: (String) -> Unit,
    onRefreshAllRepositories: () -> Unit,
    onDeleteRepository: (String) -> Unit,
    onClearMessages: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenAddDialog,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(MR.strings.add_repository))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(MR.strings.repository_manager),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(MR.strings.manage_sources_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (uiState.isRefreshingAll) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                } else {
                    Button(onClick = onRefreshAllRepositories) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(MR.strings.refresh_all))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(MR.strings.refresh_all))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Error or Success Banner
            AnimatedVisibility(visible = uiState.errorMessage != null || uiState.successMessage != null) {
                val isError = uiState.errorMessage != null
                Surface(
                    color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.errorMessage ?: uiState.successMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClearMessages) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(MR.strings.dismiss),
                                tint = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Repositories List
            if (uiState.repositories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Source,
                            contentDescription = null,
                            modifier = Modifier.height(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(MR.strings.no_repositories),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.repositories, key = { it.repository.id }) { item ->
                        RepositoryCard(
                            item = item,
                            onToggleEnabled = { onToggleRepositoryEnabled(item.repository) },
                            onRefresh = { onRefreshRepository(item.repository.id) },
                            onDelete = { onDeleteRepository(item.repository.id) }
                        )
                    }
                }
            }
        }

        // Add Repository Dialog
        if (uiState.showAddDialog) {
            AlertDialog(
                onDismissRequest = onCloseAddDialog,
                title = { Text(stringResource(MR.strings.add_repository)) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = uiState.inputUrl,
                            onValueChange = onInputUrlChanged,
                            label = { Text(stringResource(MR.strings.repository_url)) },
                            placeholder = { Text("https://example.com/index.json") },
                            leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        )

                        OutlinedTextField(
                            value = uiState.customName,
                            onValueChange = onCustomNameChanged,
                            label = { Text(stringResource(MR.strings.custom_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        )

                        Text(
                            text = stringResource(MR.strings.repository_format),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.selectedProviderType == ProviderType.MIHON,
                                onClick = { onProviderTypeSelected(ProviderType.MIHON) },
                                label = { Text(stringResource(MR.strings.mihon_manga)) }
                            )
                            FilterChip(
                                selected = uiState.selectedProviderType == ProviderType.LN_READER,
                                onClick = { onProviderTypeSelected(ProviderType.LN_READER) },
                                label = { Text(stringResource(MR.strings.lnreader_novels)) }
                            )
                        }

                        if (uiState.selectedProviderType != null) {
                            Text(
                                text = "Auto-detected / Selected: ${uiState.selectedProviderType.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = onTestConnection,
                                enabled = !uiState.isTestingConnection && uiState.inputUrl.isNotBlank()
                            ) {
                                if (uiState.isTestingConnection) {
                                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                                } else {
                                    Text(stringResource(MR.strings.normalize_test))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onAddRepository,
                        enabled = !uiState.isAddingRepository && uiState.inputUrl.isNotBlank()
                    ) {
                        if (uiState.isAddingRepository) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        } else {
                            Text(stringResource(MR.strings.add_repository))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = onCloseAddDialog) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                }
            )
        }
    }
}

@Composable
fun RepositoryCard(
    item: RepositoryItemUiState,
    onToggleEnabled: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.repository.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(
                            containerColor = if (item.repository.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ) {
                            Text(item.statusBadge)
                        }
                    }
                    Text(
                        text = item.repository.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Switch(
                    checked = item.repository.enabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Format: ${item.repository.type} • ${item.extensionsCount} ext / ${item.sourcesCount} sources",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(MR.strings.action_refresh))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(MR.strings.delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RepositoryManagerScreenPreview() {
    VivliaTheme {
        RepositoryManagerScreen(
            uiState = RepositoryUiState(
                repositories = listOf(
                    RepositoryItemUiState(
                        repository = ExtensionRepositoryEntity(
                            id = "repo1",
                            name = "Official Mihon Extensions",
                            url = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.json",
                            rawUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.json",
                            type = "MIHON",
                            enabled = true
                        ),
                        extensionsCount = 12,
                        sourcesCount = 45,
                        statusBadge = "Active"
                    )
                )
            ),
            onOpenAddDialog = {},
            onCloseAddDialog = {},
            onInputUrlChanged = {},
            onCustomNameChanged = {},
            onProviderTypeSelected = {},
            onTestConnection = {},
            onAddRepository = {},
            onToggleRepositoryEnabled = {},
            onRefreshRepository = {},
            onRefreshAllRepositories = {},
            onDeleteRepository = {},
            onClearMessages = {}
        )
    }
}
