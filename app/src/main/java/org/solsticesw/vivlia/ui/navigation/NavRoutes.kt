package org.solsticesw.vivlia.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Source
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icerock.moko.resources.StringResource
import org.solsticesw.vivlia.i18n.MR

sealed class NavRoute(
    val route: String,
    val titleRes: StringResource,
    val icon: ImageVector? = null,
) {
    data object Home : NavRoute("home", MR.strings.nav_home, Icons.Rounded.Home)
    data object Library : NavRoute("library", MR.strings.nav_library, Icons.AutoMirrored.Rounded.LibraryBooks)
    data object Browse : NavRoute("browse", MR.strings.nav_browse, Icons.Rounded.Explore)
    data object Search : NavRoute("search", MR.strings.nav_search, Icons.Rounded.Search)
    data object Repositories : NavRoute("repositories", MR.strings.nav_repositories, Icons.Rounded.Source)
    data object History : NavRoute("history", MR.strings.nav_history, Icons.Rounded.History)
    data object Settings : NavRoute("settings", MR.strings.nav_settings, Icons.Rounded.Settings)
    data object Local : NavRoute("local", MR.strings.nav_local, Icons.Rounded.Storage)

    data object Details : NavRoute("details/{entryId}", MR.strings.nav_details, Icons.Rounded.AutoStories) {
        fun createRoute(entryId: Long) = "details/$entryId"
    }

    data object Reader : NavRoute("reader/{entryId}/{chapterId}", MR.strings.nav_reader) {
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
                Local,
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
