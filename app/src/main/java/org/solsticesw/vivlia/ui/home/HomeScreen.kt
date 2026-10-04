package org.solsticesw.vivlia.ui.home

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.solsticesw.vivlia.data.local.entity.LibraryEntryEntity
import org.solsticesw.vivlia.ui.theme.VivliaTheme

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onNavigateToDetails: (Long) -> Unit,
    onNavigateToReader: (Long, Long) -> Unit,
    onNavigateToBrowse: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToRepositories: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // App Title Banner
        Text(
            text = "Vivlia",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Continue Reading Card
        if (uiState.continueReadingEntry != null) {
            Text(
                text = "Continue Reading",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToDetails(uiState.continueReadingEntry.id) },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!uiState.continueReadingEntry.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = uiState.continueReadingEntry.coverUrl,
                            contentDescription = uiState.continueReadingEntry.title,
                            modifier = Modifier
                                .size(72.dp, 100.dp)
                                .clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.continueReadingEntry.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uiState.continueReadingEntry.mediaType} • ${uiState.continueReadingEntry.unreadChapters} unread chapters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val lastChapId = uiState.continueReadingEntry.lastReadChapterId
                                if (lastChapId != null) {
                                    onNavigateToReader(uiState.continueReadingEntry.id, lastChapId)
                                } else {
                                    onNavigateToDetails(uiState.continueReadingEntry.id)
                                }
                            },
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Resume")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resume")
                        }
                    }
                }
            }
        }

        // Quick Actions Row
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionButton(
                title = "Browse",
                icon = Icons.Rounded.Explore,
                onClick = onNavigateToBrowse,
                modifier = Modifier.weight(1f)
            )
            QuickActionButton(
                title = "Search",
                icon = Icons.Rounded.Search,
                onClick = onNavigateToSearch,
                modifier = Modifier.weight(1f)
            )
            QuickActionButton(
                title = "Repos",
                icon = Icons.Rounded.Source,
                onClick = onNavigateToRepositories,
                modifier = Modifier.weight(1f)
            )
        }

        // Reading Statistics Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(
                    label = "Library Size",
                    value = uiState.totalEntriesCount.toString(),
                    icon = Icons.AutoMirrored.Rounded.MenuBook
                )
                StatItem(
                    label = "Unread Chapters",
                    value = uiState.unreadChaptersCount.toString(),
                    icon = Icons.Rounded.Bookmark
                )
            }
        }

        // Recently Added Section
        if (uiState.recentlyAdded.isNotEmpty()) {
            Text(
                text = "Recently Added",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                items(uiState.recentlyAdded) { entry ->
                    EntryCoverItem(entry = entry, onClick = { onNavigateToDetails(entry.id) })
                }
            }
        }

        // Empty State
        if (uiState.isEmptyState && !uiState.isLoading) {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Explore,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Your library is empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Add extensions or browse sources to discover books and manga to read.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onNavigateToBrowse) {
                        Text("Explore Sources")
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EntryCoverItem(
    entry: LibraryEntryEntity,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(110.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(110.dp, 150.dp)
                    .clip(MaterialTheme.shapes.medium)
            ) {
                if (!entry.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = entry.coverUrl,
                        contentDescription = entry.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = entry.title.take(2).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(6.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    VivliaTheme {
        HomeScreen(
            uiState = HomeUiState(
                totalEntriesCount = 5,
                unreadChaptersCount = 12,
                isEmptyState = false
            ),
            onNavigateToDetails = {},
            onNavigateToReader = { _, _ -> },
            onNavigateToBrowse = {},
            onNavigateToSearch = {},
            onNavigateToRepositories = {}
        )
    }
}
