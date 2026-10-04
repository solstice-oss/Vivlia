package org.solsticesw.vivlia.ui.reader.ln

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.TextFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.solsticesw.vivlia.data.local.entity.BookmarkEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightNovelReaderScreen(
    uiState: LightNovelReaderUiState,
    onBackClick: () -> Unit,
    onScrollProgressChanged: (Float, Boolean) -> Unit,
    onTypographySettingsChanged: (LightNovelTypographySettings) -> Unit,
    onToggleOverlay: () -> Unit,
    onSetCustomizationVisible: (Boolean) -> Unit,
    onSetBookmarkDialogVisible: (Boolean) -> Unit,
    onAddBookmark: (String?) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onNavigateChapter: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = uiState.typographySettings.theme
    val listState = rememberLazyListState()

    LaunchedEffect(listState, uiState.paragraphs) {
        snapshotFlow {
            val totalItems = listState.layoutInfo.totalItemsCount
            val firstVisible = listState.firstVisibleItemIndex
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val isAtEnd = totalItems > 0 && lastVisible >= totalItems - 1
            val percent = if (totalItems > 1) firstVisible.toFloat() / (totalItems - 1) else 0f
            Pair(percent, isAtEnd)
        }.collect { (percent, isAtEnd) ->
            onScrollProgressChanged(percent, isAtEnd)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = theme.textColor
            )
        } else if (uiState.errorMessage != null && uiState.paragraphs.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = uiState.errorMessage,
                    color = theme.textColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            // Novel Paragraphs List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                val width = size.width
                                val x = offset.x
                                if (x in (width * 0.3f)..(width * 0.7f)) {
                                    onToggleOverlay()
                                }
                            }
                        )
                    }
            ) {
                // Chapter Header
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp, bottom = 24.dp)
                    ) {
                        Text(
                            text = uiState.entryTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.textColor.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.chapterName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = theme.textColor
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = theme.textColor.copy(alpha = 0.2f))
                    }
                }

                // Paragraph Items
                itemsIndexed(uiState.paragraphs) { index, paragraph ->
                    Text(
                        text = paragraph,
                        color = theme.textColor,
                        fontSize = uiState.typographySettings.fontSizeSp.sp,
                        lineHeight = (uiState.typographySettings.fontSizeSp * uiState.typographySettings.lineHeightMultiplier).sp,
                        textAlign = uiState.typographySettings.alignment.align,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = uiState.typographySettings.paragraphSpacingDp.dp)
                    )
                }

                // Chapter Footer & Chapter Switcher
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp, bottom = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HorizontalDivider(color = theme.textColor.copy(alpha = 0.2f))
                        Text(
                            text = "End of ${uiState.chapterName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.textColor.copy(alpha = 0.7f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { uiState.prevChapterId?.let { onNavigateChapter(it) } },
                                enabled = uiState.prevChapterId != null
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.NavigateBefore, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Prev Chapter")
                            }

                            OutlinedButton(
                                onClick = { uiState.nextChapterId?.let { onNavigateChapter(it) } },
                                enabled = uiState.nextChapterId != null
                            ) {
                                Text("Next Chapter")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Rounded.NavigateNext, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }

        // Overlay Top & Bottom Bars
        AnimatedVisibility(
            visible = uiState.showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Overlay
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.entryTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = uiState.chapterName,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onSetBookmarkDialogVisible(true) }) {
                            Icon(
                                imageVector = if (uiState.isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { onSetCustomizationVisible(true) }) {
                            Icon(
                                imageVector = Icons.Rounded.TextFormat,
                                contentDescription = "Customize Reader",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Bottom Overlay Bar with Progress
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${(uiState.currentScrollPercent * 100).toInt()}% completed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = uiState.chapterName,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray
                            )
                        }
                        LinearProgressIndicator(
                            progress = { uiState.currentScrollPercent },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.DarkGray
                        )
                    }
                }
            }
        }

        // Typography & Theme Customization Bottom Sheet
        if (uiState.showCustomizationSheet) {
            ModalBottomSheet(
                onDismissRequest = { onSetCustomizationVisible(false) },
                sheetState = rememberModalBottomSheetState()
            ) {
                ReaderCustomizationSheetContent(
                    settings = uiState.typographySettings,
                    onSettingsChanged = onTypographySettingsChanged,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }

        // Bookmark Dialog & Notes
        if (uiState.showBookmarkDialog) {
            BookmarkNoteDialog(
                bookmarks = uiState.bookmarks,
                onAddBookmark = onAddBookmark,
                onDeleteBookmark = onDeleteBookmark,
                onDismiss = { onSetBookmarkDialogVisible(false) }
            )
        }
    }
}

@Composable
fun ReaderCustomizationSheetContent(
    settings: LightNovelTypographySettings,
    onSettingsChanged: (LightNovelTypographySettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Reader Customization",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Theme Background Selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Theme Palette",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ReaderTheme.entries.forEachIndexed { index, t ->
                    SegmentedButton(
                        selected = settings.theme == t,
                        onClick = { onSettingsChanged(settings.copy(theme = t)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ReaderTheme.entries.size)
                    ) {
                        Text(t.title)
                    }
                }
            }
        }

        // Font Size Adjustment
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Font Size", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("${settings.fontSizeSp.toInt()} sp", style = MaterialTheme.typography.bodyMedium)
            }
            Slider(
                value = settings.fontSizeSp,
                onValueChange = { onSettingsChanged(settings.copy(fontSizeSp = it)) },
                valueRange = 12f..32f,
                steps = 19
            )
        }

        // Line Height Multiplier
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Line Height", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(String.format("%.1fx", settings.lineHeightMultiplier), style = MaterialTheme.typography.bodyMedium)
            }
            Slider(
                value = settings.lineHeightMultiplier,
                onValueChange = { onSettingsChanged(settings.copy(lineHeightMultiplier = it)) },
                valueRange = 1.2f..2.2f
            )
        }

        // Paragraph Spacing
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Paragraph Spacing", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("${settings.paragraphSpacingDp.toInt()} dp", style = MaterialTheme.typography.bodyMedium)
            }
            Slider(
                value = settings.paragraphSpacingDp,
                onValueChange = { onSettingsChanged(settings.copy(paragraphSpacingDp = it)) },
                valueRange = 4f..32f
            )
        }

        // Text Alignment Selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Text Alignment", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                LnTextAlignment.entries.forEachIndexed { index, align ->
                    SegmentedButton(
                        selected = settings.alignment == align,
                        onClick = { onSettingsChanged(settings.copy(alignment = align)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = LnTextAlignment.entries.size)
                    ) {
                        Text(align.title)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun BookmarkNoteDialog(
    bookmarks: List<BookmarkEntity>,
    onAddBookmark: (String?) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var noteText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chapter Bookmarks") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Bookmark Note (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { onAddBookmark(noteText.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Bookmark, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Bookmark")
                }

                if (bookmarks.isNotEmpty()) {
                    Text(
                        text = "Saved Bookmarks",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(bookmarks) { _, bookmark ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bookmark.note ?: "Bookmark",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                IconButton(onClick = { onDeleteBookmark(bookmark.id) }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "Delete Bookmark",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
