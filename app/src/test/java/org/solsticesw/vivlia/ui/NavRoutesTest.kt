package org.solsticesw.vivlia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.solsticesw.vivlia.ui.navigation.NavRoute

class NavRoutesTest {

    @Test
    fun testTopLevelDestinationsList() {
        val topLevel = NavRoute.topLevelDestinations
        assertEquals(8, topLevel.size)
        assertTrue(topLevel.contains(NavRoute.Home))
        assertTrue(topLevel.contains(NavRoute.Library))
        assertTrue(topLevel.contains(NavRoute.Browse))
        assertTrue(topLevel.contains(NavRoute.Search))
        assertTrue(topLevel.contains(NavRoute.Repositories))
        assertTrue(topLevel.contains(NavRoute.History))
        assertTrue(topLevel.contains(NavRoute.Local))
        assertTrue(topLevel.contains(NavRoute.Settings))
    }

    @Test
    fun testRouteCreationHelpers() {
        assertEquals("details/123", NavRoute.Details.createRoute(123L))
        assertEquals("reader/123/456", NavRoute.Reader.createRoute(123L, 456L))
    }

    @Test
    fun testNavRouteProperties() {
        assertEquals("home", NavRoute.Home.route)
        assertNotNull(NavRoute.Home.titleRes)
        assertNotNull(NavRoute.Home.icon)

        assertEquals("library", NavRoute.Library.route)
        assertEquals("browse", NavRoute.Browse.route)
        assertEquals("search", NavRoute.Search.route)
        assertEquals("repositories", NavRoute.Repositories.route)
        assertEquals("history", NavRoute.History.route)
        assertEquals("local", NavRoute.Local.route)
        assertEquals("settings", NavRoute.Settings.route)
    }

    @Test
    fun testForRoute() {
        assertEquals(NavRoute.Home, NavRoute.forRoute("home"))
        assertEquals(NavRoute.Library, NavRoute.forRoute("library"))
        assertEquals(NavRoute.Browse, NavRoute.forRoute("browse"))
        assertEquals(NavRoute.Search, NavRoute.forRoute("search"))
        assertEquals(NavRoute.Repositories, NavRoute.forRoute("repositories"))
        assertEquals(NavRoute.History, NavRoute.forRoute("history"))
        assertEquals(NavRoute.Local, NavRoute.forRoute("local"))
        assertEquals(NavRoute.Settings, NavRoute.forRoute("settings"))
        assertEquals(NavRoute.Details, NavRoute.forRoute("details/123"))
        assertEquals(NavRoute.Reader, NavRoute.forRoute("reader/123/456"))

        org.junit.Assert.assertNull(NavRoute.forRoute(null))
        org.junit.Assert.assertNull(NavRoute.forRoute(""))
        org.junit.Assert.assertNull(NavRoute.forRoute("unknown/route"))
    }
}
