package org.solsticesw.vivlia.ui.history

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import org.solsticesw.vivlia.data.local.entity.ReadingSessionEntity
import org.solsticesw.vivlia.ui.theme.VivliaTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onAskClearHistory: () -> Unit,
    onDismissClearHistory: () -> Unit,
    onConfirmClearHistory: () -> Unit,
    onNavigateToDetails: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Reading History",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Recently read titles and sessions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.historyItems.isNotEmpty()) {
                IconButton(onClick = onAskClearHistory) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = "Clear History",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.historyItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No reading history yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Titles you read will automatically appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.historyItems, key = { it.session.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        onClick = {
                            if (item.entry != null) {
                                onNavigateToDetails(item.entry.id)
                            }
                        }
                    )
                }
            }
        }

        // Confirmation Dialog
        if (uiState.showClearConfirmation) {
            AlertDialog(
                onDismissRequest = onDismissClearHistory,
                title = { Text("Clear Reading History?") },
                text = { Text("This will permanently remove all reading history sessions. Your library entries will not be affected.") },
                confirmButton = {
                    Button(onClick = onConfirmClearHistory) {
                        Text("Clear All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissClearHistory) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun HistoryItemCard(
    item: HistoryItemUiState,
    onClick: () -> Unit
) {
    val entry = item.entry
    val session = item.session
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val formattedTime = dateFormat.format(Date(session.startTime))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp, 70.dp)
                    .clip(MaterialTheme.shapes.small)
            ) {
                if (entry != null && !entry.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = entry.coverUrl,
                        contentDescription = entry.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(entry?.title?.take(1)?.uppercase() ?: "?")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry?.title ?: "Unknown Entry #${session.entryId}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Progress: ${(session.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HistoryScreenPreview() {
    VivliaTheme {
        HistoryScreen(
            uiState = HistoryUiState(
                historyItems = listOf(
                    HistoryItemUiState(
                        session = ReadingSessionEntity(
                            id = 1,
                            entryId = 10,
                            chapterId = 100,
                            startTime = System.currentTimeMillis(),
                            endTime = System.currentTimeMillis(),
                            progress = 0.75f
                        ),
                        entry = LibraryEntryEntity(
                            id = 10,
                            sourceId = "s1",
                            url = "",
                            title = "Solo Leveling",
                            mediaType = "MANGA"
                        )
                    )
                )
            ),
            onAskClearHistory = {},
            onDismissClearHistory = {},
            onConfirmClearHistory = {},
            onNavigateToDetails = {}
        )
    }
}
