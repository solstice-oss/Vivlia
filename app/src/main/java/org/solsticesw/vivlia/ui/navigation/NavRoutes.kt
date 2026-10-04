package org.solsticesw.vivlia.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Source
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavRoute(
    val route: String,
    val title: String,
    val icon: ImageVector? = null,
) {
    data object Home : NavRoute("home", "Home", Icons.Rounded.Home)
    data object Library : NavRoute("library", "Library", Icons.AutoMirrored.Rounded.LibraryBooks)
    data object Browse : NavRoute("browse", "Browse", Icons.Rounded.Explore)
    data object Search : NavRoute("search", "Search", Icons.Rounded.Search)
    data object Repositories : NavRoute("repositories", "Repositories", Icons.Rounded.Source)
    data object History : NavRoute("history", "History", Icons.Rounded.History)
    data object Settings : NavRoute("settings", "Settings", Icons.Rounded.Settings)

    data object Details : NavRoute("details/{entryId}", "Entry Details", Icons.Rounded.AutoStories) {
        fun createRoute(entryId: Long) = "details/$entryId"
    }

    data object Reader : NavRoute("reader/{entryId}/{chapterId}", "Reader") {
        fun createRoute(entryId: Long, chapterId: Long) = "reader/$entryId/$chapterId"
    }

    companion object {
        val TOP_LEVEL_ROUTES: List<NavRoute>
            get() = listOf(
                Home,
                Library,
                Browse,
                Search,
                Repositories,
                History,
                Settings,
            )

        val topLevelDestinations: List<NavRoute>
            get() = TOP_LEVEL_ROUTES

        fun forRoute(route: String?): NavRoute? {
            if (route.isNullOrBlank()) return null
            val baseRoute = route.substringBefore("/")
            return TOP_LEVEL_ROUTES.find { (it.route == route) || (it.route.substringBefore("/") == baseRoute) }
                ?: when (baseRoute) {
                    Details.route.substringBefore("/") -> Details
                    Reader.route.substringBefore("/") -> Reader
                    else -> null
                }
        }
    }
}
