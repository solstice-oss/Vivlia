package org.solsticesw.vivlia.ui.reader.manga

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch
import org.solsticesw.vivlia.data.local.entity.PageEntity

@Composable
fun MangaReaderScreen(
    uiState: MangaReaderUiState,
    onBackClick: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onReaderModeChanged: (MangaReaderMode) -> Unit,
    onToggleOverlay: () -> Unit,
    onNavigateChapter: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var modeMenuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        } else if (uiState.errorMessage != null && uiState.pages.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.BrokenImage,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = uiState.errorMessage,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            // Main Reader Content
            when (uiState.readerMode) {
                MangaReaderMode.HORIZONTAL_PAGER -> {
                    val pagerState = rememberPagerState(
                        initialPage = uiState.currentPageIndex.coerceIn(0, (uiState.pages.size - 1).coerceAtLeast(0)),
                        pageCount = { uiState.pages.size }
                    )

                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.currentPage }.collect { page ->
                            onPageChanged(page)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val width = size.width
                                        val x = offset.x
                                        when {
                                            x < width * 0.3f -> {
                                                // Tap Left -> Previous Page
                                                if (pagerState.currentPage > 0) {
                                                    coroutineScope.launch {
                                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                    }
                                                }
                                            }
                                            x > width * 0.7f -> {
                                                // Tap Right -> Next Page
                                                if (pagerState.currentPage < uiState.pages.size - 1) {
                                                    coroutineScope.launch {
                                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                    }
                                                }
                                            }
                                            else -> {
                                                // Tap Center -> Toggle Overlay Controls
                                                onToggleOverlay()
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { pageIdx ->
                            val page = uiState.pages[pageIdx]
                            MangaPageImage(
                                page = page,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                MangaReaderMode.VERTICAL_SCROLL, MangaReaderMode.WEBTOON -> {
                    val listState = rememberLazyListState(
                        initialFirstVisibleItemIndex = uiState.currentPageIndex.coerceIn(0, (uiState.pages.size - 1).coerceAtLeast(0))
                    )

                    LaunchedEffect(listState) {
                        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
                            onPageChanged(index)
                        }
                    }

                    val spacing = if (uiState.readerMode == MangaReaderMode.VERTICAL_SCROLL) 8.dp else 0.dp

                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(spacing),
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val width = size.width
                                        val x = offset.x
                                        if (x in (width * 0.35f)..(width * 0.65f)) {
                                            onToggleOverlay()
                                        }
                                    }
                                )
                            }
                    ) {
                        itemsIndexed(uiState.pages) { _, page ->
                            MangaPageImage(
                                page = page,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = uiState.showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Overlay Bar
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
                    }
                }

                // Bottom Overlay Bar
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
                        // Slider & Page Counter
                        if (uiState.pages.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Page ${uiState.currentPageIndex + 1} / ${uiState.pages.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Box {
                                    IconButton(onClick = { modeMenuExpanded = true }) {
                                        val icon = when (uiState.readerMode) {
                                            MangaReaderMode.HORIZONTAL_PAGER -> Icons.Rounded.ViewCarousel
                                            MangaReaderMode.VERTICAL_SCROLL -> Icons.Rounded.ViewDay
                                            MangaReaderMode.WEBTOON -> Icons.Rounded.ViewStream
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = "Reader Mode",
                                            tint = Color.White
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = modeMenuExpanded,
                                        onDismissRequest = { modeMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Horizontal Pager") },
                                            onClick = {
                                                onReaderModeChanged(MangaReaderMode.HORIZONTAL_PAGER)
                                                modeMenuExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Vertical Scroll") },
                                            onClick = {
                                                onReaderModeChanged(MangaReaderMode.VERTICAL_SCROLL)
                                                modeMenuExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Continuous Webtoon") },
                                            onClick = {
                                                onReaderModeChanged(MangaReaderMode.WEBTOON)
                                                modeMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Slider(
                                value = uiState.currentPageIndex.toFloat(),
                                onValueChange = { onPageChanged(it.toInt()) },
                                valueRange = 0f..(uiState.pages.size - 1).coerceAtLeast(1).toFloat(),
                                steps = (uiState.pages.size - 2).coerceAtLeast(0)
                            )
                        }

                        // Prev / Next Chapter Buttons
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
    }
}

@Composable
fun MangaPageImage(
    page: PageEntity,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (!page.imageUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = page.imageUrl,
                contentDescription = "Page ${page.index + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.BrokenImage,
                                contentDescription = "Error loading page",
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Failed to load page ${page.index + 1}",
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            )
        } else {
            Surface(
                color = Color.DarkGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Page ${page.index + 1}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
