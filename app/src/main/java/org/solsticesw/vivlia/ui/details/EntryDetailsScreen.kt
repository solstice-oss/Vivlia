package org.solsticesw.vivlia.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import org.solsticesw.vivlia.data.local.entity.ChapterEntity
import org.solsticesw.vivlia.data.local.entity.LibraryEntryEntity
import org.solsticesw.vivlia.ui.theme.VivliaTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryDetailsScreen(
    uiState: EntryDetailsUiState,
    onBackClick: () -> Unit,
    onRefreshMetadata: () -> Unit,
    onToggleInLibrary: () -> Unit,
    onToggleChapterRead: (ChapterEntity) -> Unit,
    onToggleChapterBookmark: (ChapterEntity) -> Unit,
    onChapterClick: (ChapterEntity) -> Unit,
    onDismissErrorBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = uiState.entry?.title ?: "Entry Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (uiState.isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp)
                )
            } else {
                IconButton(onClick = onRefreshMetadata) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Metadata")
                }
            }
        }

        // Non-destructive Error Banner
        AnimatedVisibility(visible = uiState.errorBannerMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.errorBannerMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismissErrorBanner) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        val entry = uiState.entry
        if (entry == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Info Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp, 170.dp)
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
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = entry.title.take(2).uppercase(),
                                            style = MaterialTheme.typography.headlineLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            if (uiState.authors.isNotEmpty()) {
                                Text(
                                    text = "By ${uiState.authors.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                text = "Status: ${entry.status ?: "Unknown"} • ${entry.mediaType}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = onToggleInLibrary,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = if (entry.inLibrary) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (entry.inLibrary) "In Library" else "Add to Library")
                            }
                        }
                    }
                }

                // Genres Section
                if (uiState.genres.isNotEmpty()) {
                    item {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            uiState.genres.forEach { genre ->
                                FilterChip(
                                    selected = false,
                                    onClick = {},
                                    label = { Text(genre, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }

                // Summary Section
                if (!entry.summary.isNullOrBlank()) {
                    item {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Summary",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = entry.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Chapters Section Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chapters (${uiState.chapters.size})",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${uiState.chapters.count { !it.read }} unread",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Chapters List
                if (uiState.chapters.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No chapters found. Tap refresh to load chapters.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(uiState.chapters, key = { it.id }) { chapter ->
                        ChapterItemRow(
                            chapter = chapter,
                            onChapterClick = { onChapterClick(chapter) },
                            onToggleRead = { onToggleChapterRead(chapter) },
                            onToggleBookmark = { onToggleChapterBookmark(chapter) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChapterItemRow(
    chapter: ChapterEntity,
    onChapterClick: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChapterClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (chapter.read) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleRead) {
                Icon(
                    imageVector = if (chapter.read) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = "Read Status",
                    tint = if (chapter.read) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (chapter.read) FontWeight.Normal else FontWeight.Bold,
                    color = if (chapter.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!chapter.scanlator.isNullOrBlank()) {
                    Text(
                        text = chapter.scanlator,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onToggleBookmark) {
                Icon(
                    imageVector = if (chapter.bookmark) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = "Bookmark",
                    tint = if (chapter.bookmark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EntryDetailsScreenPreview() {
    VivliaTheme {
        EntryDetailsScreen(
            uiState = EntryDetailsUiState(
                entryId = 1,
                entry = LibraryEntryEntity(
                    id = 1,
                    sourceId = "manga_dex",
                    url = "http://example.com/1",
                    title = "Sample Epic Story",
                    summary = "This is a detailed summary of the story...",
                    mediaType = "MANGA",
                    inLibrary = true,
                    status = "ONGOING"
                ),
                chapters = listOf(
                    ChapterEntity(
                        id = 10,
                        entryId = 1,
                        url = "http://example.com/1/ch1",
                        name = "Chapter 1: The Beginning",
                        chapterNumber = 1.0f,
                        read = true
                    ),
                    ChapterEntity(
                        id = 11,
                        entryId = 1,
                        url = "http://example.com/1/ch2",
                        name = "Chapter 2: The Journey",
                        chapterNumber = 2.0f,
                        read = false,
                        bookmark = true
                    )
                ),
                authors = listOf("Famous Author"),
                genres = listOf("Action", "Fantasy", "Adventure")
            ),
            onBackClick = {},
            onRefreshMetadata = {},
            onToggleInLibrary = {},
            onToggleChapterRead = {},
            onToggleChapterBookmark = {},
            onChapterClick = {},
            onDismissErrorBanner = {}
        )
    }
}
