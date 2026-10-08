package org.solsticesw.vivlia.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.icerock.moko.resources.compose.stringResource
import org.solsticesw.vivlia.data.local.AppDatabase
import org.solsticesw.vivlia.data.repository.LibraryRepository
import org.solsticesw.vivlia.data.repository.ThemeMode
import org.solsticesw.vivlia.domain.model.MediaType
import org.solsticesw.vivlia.ui.AppViewModelFactory
import org.solsticesw.vivlia.ui.browse.BrowseSourcesScreen
import org.solsticesw.vivlia.ui.browse.BrowseViewModel
import org.solsticesw.vivlia.ui.details.EntryDetailsScreen
import org.solsticesw.vivlia.ui.details.EntryDetailsViewModel
import org.solsticesw.vivlia.ui.history.HistoryScreen
import org.solsticesw.vivlia.ui.history.HistoryViewModel
import org.solsticesw.vivlia.ui.home.HomeScreen
import org.solsticesw.vivlia.ui.home.HomeViewModel
import org.solsticesw.vivlia.ui.library.LibraryScreen
import org.solsticesw.vivlia.ui.library.LibraryViewModel
import org.solsticesw.vivlia.ui.local.LocalStorageScreen
import org.solsticesw.vivlia.ui.local.LocalStorageViewModel
import org.solsticesw.vivlia.ui.reader.ln.LightNovelReaderScreen
import org.solsticesw.vivlia.ui.reader.ln.LightNovelReaderViewModel
import org.solsticesw.vivlia.ui.reader.manga.MangaReaderScreen
import org.solsticesw.vivlia.ui.reader.manga.MangaReaderViewModel
import org.solsticesw.vivlia.ui.repositories.RepositoryManagerScreen
import org.solsticesw.vivlia.ui.repositories.RepositoryViewModel
import org.solsticesw.vivlia.ui.search.GlobalSearchScreen
import org.solsticesw.vivlia.ui.search.SearchViewModel
import org.solsticesw.vivlia.ui.settings.SettingsScreen
import org.solsticesw.vivlia.ui.settings.SettingsViewModel
import org.solsticesw.vivlia.ui.theme.VivliaTheme

sealed interface ScreenState {
    data class TopLevel(val route: NavRoute) : ScreenState
    data class Details(val entryId: Long) : ScreenState
    data class Reader(val entryId: Long, val chapterId: Long) : ScreenState
}

@Composable
fun VivliaAppScaffold() {
    val context = LocalContext.current

    // Settings ViewModel for global Theme & Motion configuration
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = AppViewModelFactory(context)
    )
    val settingsUiState by settingsViewModel.uiState.collectAsState()
    val appSettings = settingsUiState.settings

    val darkTheme = when (appSettings.themeMode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    VivliaTheme(
        darkTheme = darkTheme,
        dynamicColor = appSettings.dynamicColor,
        expressiveMotion = appSettings.expressiveMotion,
        reducedMotion = appSettings.reducedMotion
    ) {
        val backStack = remember { mutableStateListOf<ScreenState>(ScreenState.TopLevel(NavRoute.Home)) }
        val currentScreen = backStack.lastOrNull() ?: ScreenState.TopLevel(NavRoute.Home)

        BackHandler(enabled = backStack.size > 1) {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.size - 1)
            }
        }

        fun navigateToTopLevel(route: NavRoute) {
            backStack.clear()
            backStack.add(ScreenState.TopLevel(route))
        }

        fun navigateToDetails(entryId: Long) {
            backStack.add(ScreenState.Details(entryId))
        }

        fun navigateToReader(entryId: Long, chapterId: Long) {
            backStack.add(ScreenState.Reader(entryId, chapterId))
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedScreen = maxWidth >= 600.dp

            Scaffold(
                bottomBar = {
                    if (!isExpandedScreen && currentScreen is ScreenState.TopLevel) {
                        val activeRoute = currentScreen.route
                        NavigationBar {
                            NavRoute.TOP_LEVEL_ROUTES.forEach { dest ->
                                val iconVector = dest.icon ?: Icons.Rounded.Home
                                val title = stringResource(dest.titleRes)
                                NavigationBarItem(
                                    selected = activeRoute == dest,
                                    onClick = { navigateToTopLevel(dest) },
                                    icon = {
                                        Icon(iconVector, contentDescription = title)
                                    },
                                    label = { Text(title) }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isExpandedScreen && currentScreen is ScreenState.TopLevel) {
                        val activeRoute = currentScreen.route
                        NavigationRail {
                            NavRoute.TOP_LEVEL_ROUTES.forEach { dest ->
                                val iconVector = dest.icon ?: Icons.Rounded.Home
                                val title = stringResource(dest.titleRes)
                                NavigationRailItem(
                                    selected = activeRoute == dest,
                                    onClick = { navigateToTopLevel(dest) },
                                    icon = {
                                        Icon(iconVector, contentDescription = title)
                                    },
                                    label = { Text(title) }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "ScreenTransition"
                        ) { screen ->
                            when (screen) {
                                is ScreenState.TopLevel -> {
                                    when (screen.route) {
                                        NavRoute.Home -> {
                                            val vm: HomeViewModel = viewModel(factory = AppViewModelFactory(context))
                                            val uiState by vm.uiState.collectAsState()
                                            HomeScreen(
                                                uiState = uiState,
                                                onNavigateToDetails = { navigateToDetails(it) },
                                                onNavigateToReader = { eId, cId -> navigateToReader(eId, cId) },
                                                onNavigateToBrowse = { navigateToTopLevel(NavRoute.Browse) },
                                                onNavigateToSearch = { navigateToTopLevel(NavRoute.Search) },
                                                onNavigateToRepositories = { navigateToTopLevel(NavRoute.Repositories) }
                                            )
                                        }
                                        NavRoute.Library -> {
                                            val vm: LibraryViewModel = viewModel(factory = AppViewModelFactory(context))
                                            val uiState by vm.uiState.collectAsState()
                                            LibraryScreen(
                                                uiState = uiState,
                                                onTabSelected = vm::onTabSelected,
                                                onSearchQueryChanged = vm::onSearchQueryChanged,
                                                onSortOptionChanged = vm::onSortOptionChanged,
                                                onToggleLayoutMode = vm::toggleLayoutMode,
                                                onNavigateToDetails = { navigateToDetails(it) }
                                            )
                                        }
                                        NavRoute.Browse -> {
                                            val vm: BrowseViewModel = viewModel(factory = AppViewModelFactory(context))
                                            val uiState by vm.uiState.collectAsState()
                                            BrowseSourcesScreen(
                                                uiState = uiState,
                                                onSelectSource = { src -> vm.selectSource(src) },
                                                onSearchQueryChanged = vm::onSearchQueryChanged,
                                                onRefreshCatalog = vm::refreshCatalog,
                                                onRefreshExtensions = vm::refreshExtensions,
                                                onTogglePinSource = vm::togglePinSource,
                                                onNavigateToDetails = { navigateToDetails(it) }
                                            )
                                        }
                                        NavRoute.Search -> {
                                            val vm: SearchViewModel = viewModel(factory = AppViewModelFactory(context))
                                            val uiState by vm.uiState.collectAsState()
                                            GlobalSearchScreen(
                                                uiState = uiState,
                                                onQueryChanged = vm::onQueryChanged,
                                                onSearch = vm::performSearch,
                                                onAddToLibrary = vm::addToLibrary
                                            )
                                        }
                                        NavRoute.Repositories -> {
                                            val vm: RepositoryViewModel = viewModel(factory = AppViewModelFactory(context))
                                            val uiState by vm.uiState.collectAsState()
                                            RepositoryManagerScreen(
                                                uiState = uiState,
                                                onOpenAddDialog = vm::openAddDialog,
                                                onCloseAddDialog = vm::closeAddDialog,
                                                onInputUrlChanged = vm::onInputUrlChanged,
                                                onCustomNameChanged = vm::onCustomNameChanged,
                                                onProviderTypeSelected = vm::onProviderTypeSelected,
                                                onTestConnection = vm::testConnection,
                                                onAddRepository = vm::addRepository,
                                                onToggleRepositoryEnabled = vm::toggleRepositoryEnabled,
                                                onRefreshRepository = vm::refreshRepository,
                                                onRefreshAllRepositories = vm::refreshAllRepositories,
                                                onDeleteRepository = vm::deleteRepository,
                                                onClearMessages = vm::clearMessages
                                            )
                                        }
                                        NavRoute.History -> {
                                            val vm: HistoryViewModel = viewModel(factory = AppViewModelFactory(context))
                                            val uiState by vm.uiState.collectAsState()
                                            HistoryScreen(
                                                uiState = uiState,
                                                onAskClearHistory = vm::askClearHistory,
                                                onDismissClearHistory = vm::dismissClearHistory,
                                                onConfirmClearHistory = vm::confirmClearHistory,
                                                onNavigateToDetails = { navigateToDetails(it) }
                                            )
                                        }
                                        NavRoute.Local -> {
                                            val vm: LocalStorageViewModel = viewModel(
                                                factory = AppViewModelFactory(context)
                                            )
                                            val uiState by vm.uiState.collectAsState()
                                            LocalStorageScreen(
                                                uiState = uiState,
                                                onScan = vm::scan,
                                                onChangeRoot = vm::setRoot,
                                                onResetRoot = vm::resetRoot,
                                                onImport = vm::import,
                                                onToggleFavorite = vm::toggleFavorite,
                                                onNavigateToDetails = { navigateToDetails(it) },
                                                onToggleSelected = vm::toggleSelected,
                                                onAskDelete = vm::askDelete,
                                                onDismissDelete = vm::dismissDelete,
                                                onConfirmDelete = vm::confirmDelete,
                                                onDismissError = vm::dismissError
                                            )
                                        }
                                        NavRoute.Settings -> {
                                            val uiState by settingsViewModel.uiState.collectAsState()
                                            SettingsScreen(
                                                uiState = uiState,
                                                onSetThemeMode = settingsViewModel::setThemeMode,
                                                onSetDynamicColor = settingsViewModel::setDynamicColor,
                                                onSetExpressiveMotion = settingsViewModel::setExpressiveMotion,
                                                onSetReducedMotion = settingsViewModel::setReducedMotion,
                                                onSetAutoRefreshRepos = settingsViewModel::setAutoRefreshRepos,
                                                onClearCache = settingsViewModel::clearCache,
                                                onDismissCacheMessage = settingsViewModel::dismissCacheMessage
                                            )
                                        }
                                        else -> {}
                                    }
                                }
                                is ScreenState.Details -> {
                                    val vm: EntryDetailsViewModel = viewModel(
                                        key = "EntryDetails_${screen.entryId}",
                                        factory = AppViewModelFactory(context, entryId = screen.entryId)
                                    )
                                    val uiState by vm.uiState.collectAsState()
                                    EntryDetailsScreen(
                                        uiState = uiState,
                                        onBackClick = {
                                            if (backStack.size > 1) {
                                                backStack.removeAt(backStack.size - 1)
                                            }
                                        },
                                        onRefreshMetadata = vm::refreshMetadata,
                                        onToggleInLibrary = vm::toggleInLibrary,
                                        onToggleChapterRead = vm::toggleChapterRead,
                                        onToggleChapterBookmark = vm::toggleChapterBookmark,
                                        onChapterClick = { chapter ->
                                            backStack.add(ScreenState.Reader(screen.entryId, chapter.id))
                                        },
                                        onDismissErrorBanner = vm::dismissErrorBanner
                                    )
                                }
                                is ScreenState.Reader -> {
                                    val libraryRepo = remember { LibraryRepository(AppDatabase.getInstance(context)) }
                                    val entryState = libraryRepo.getEntryFlow(screen.entryId).collectAsState(initial = null)
                                    val entry = entryState.value
                                    val mediaType = MediaType.fromString(entry?.mediaType)

                                    if (mediaType == MediaType.NOVEL) {
                                        val vm: LightNovelReaderViewModel = viewModel(
                                            key = "LNReader_${screen.entryId}_${screen.chapterId}",
                                            factory = AppViewModelFactory(context, entryId = screen.entryId, chapterId = screen.chapterId)
                                        )
                                        val uiState by vm.uiState.collectAsState()
                                        LightNovelReaderScreen(
                                            uiState = uiState,
                                            onBackClick = {
                                                if (backStack.size > 1) {
                                                    backStack.removeAt(backStack.size - 1)
                                                }
                                            },
                                            onScrollProgressChanged = vm::updateScrollProgress,
                                            onTypographySettingsChanged = vm::updateTypographySettings,
                                            onToggleOverlay = vm::toggleOverlay,
                                            onSetCustomizationVisible = vm::setCustomizationSheetVisible,
                                            onSetBookmarkDialogVisible = vm::setBookmarkDialogVisible,
                                            onAddBookmark = vm::addBookmark,
                                            onDeleteBookmark = vm::deleteBookmark,
                                            onNavigateChapter = { nextChapId ->
                                                if (backStack.size > 1) {
                                                    backStack.removeAt(backStack.size - 1)
                                                }
                                                backStack.add(ScreenState.Reader(screen.entryId, nextChapId))
                                            }
                                        )
                                    } else {
                                        val vm: MangaReaderViewModel = viewModel(
                                            key = "MangaReader_${screen.entryId}_${screen.chapterId}",
                                            factory = AppViewModelFactory(context, entryId = screen.entryId, chapterId = screen.chapterId)
                                        )
                                        val uiState by vm.uiState.collectAsState()
                                        MangaReaderScreen(
                                            uiState = uiState,
                                            onBackClick = {
                                                if (backStack.size > 1) {
                                                    backStack.removeAt(backStack.size - 1)
                                                }
                                            },
                                            onPageChanged = vm::onPageChanged,
                                            onReaderModeChanged = vm::setReaderMode,
                                            onToggleOverlay = vm::toggleOverlay,
                                            onNavigateChapter = { nextChapId ->
                                                if (backStack.size > 1) {
                                                    backStack.removeAt(backStack.size - 1)
                                                }
                                                backStack.add(ScreenState.Reader(screen.entryId, nextChapId))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
