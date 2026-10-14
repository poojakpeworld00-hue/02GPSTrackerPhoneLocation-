package io.homedeck.grid.canvas

import org.junit.Assert.assertEquals
import org.junit.Test

class StrayWidgetViewsTest {

    @Test
    fun viewWithoutItsRowIsOrphan() {
        val views = mapOf("search" to 7L, "time" to 3L)
        assertEquals(listOf("search"), DeskGrid.orphanWidgetViews(views, liveItemIds = setOf(3L)))
    }

    @Test
    fun viewWithLiveRowIsKept() {
        val views = mapOf("search" to 7L, "time" to 3L)
        assertEquals(emptyList<String>(), DeskGrid.orphanWidgetViews(views, liveItemIds = setOf(3L, 7L)))
    }

    @Test
    fun placedViewIsFoundByRowIdWhenWidgetIdIsStale() {
        val views = listOf("search" to (42 to 7L), "time" to (5 to 3L))
        val found = DeskGrid.placedWidgetView(views, tagOf = { it.second.first }, itemIdOf = { it.second.second }, widgetId = -1, itemId = 7L)
        assertEquals("search", found?.first)
    }

    @Test
    fun placedViewFallsBackToWidgetId() {
        val views = listOf("search" to (42 to null as Long?))
        val found = DeskGrid.placedWidgetView(views, tagOf = { it.second.first }, itemIdOf = { it.second.second }, widgetId = 42, itemId = 7L)
        assertEquals("search", found?.first)
    }

    @Test
    fun untrackedViewIsKept() {
        val views = mapOf<String, Long?>("unknown" to null)
        assertEquals(emptyList<String>(), DeskGrid.orphanWidgetViews(views, liveItemIds = emptySet()))
    }
}
