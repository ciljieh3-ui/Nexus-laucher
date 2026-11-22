package com.example

import com.example.model.AppRef
import com.example.model.DockItem
import com.example.model.HomeItem
import com.example.viewmodel.LayoutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutEngineTest {

    @Test
    fun `drop app onto empty cell moves item`() {
        val app1 = HomeItem.AppItem("id1", "com.a", "com.a.Main", "App A", page = 0, row = 0, col = 0)
        val result = LayoutEngine.dropHomeItemAt(
            items = listOf(app1),
            draggedId = "id1",
            targetPage = 0,
            targetRow = 2,
            targetCol = 3,
            maxRows = 5,
            maxCols = 4
        )
        assertEquals(1, result.size)
        val moved = result.first() as HomeItem.AppItem
        assertEquals(2, moved.row)
        assertEquals(3, moved.col)
    }

    @Test
    fun `drop app onto another app creates a folder`() {
        val app1 = HomeItem.AppItem("id1", "com.a", "com.a.Main", "App A", page = 0, row = 0, col = 0)
        val app2 = HomeItem.AppItem("id2", "com.b", "com.b.Main", "App B", page = 0, row = 1, col = 1)
        val result = LayoutEngine.dropHomeItemAt(
            items = listOf(app1, app2),
            draggedId = "id1",
            targetPage = 0,
            targetRow = 1,
            targetCol = 1,
            maxRows = 5,
            maxCols = 4
        )
        assertEquals(1, result.size)
        val folder = result.first() as HomeItem.FolderItem
        assertEquals(2, folder.apps.size)
        assertEquals(1, folder.row)
        assertEquals(1, folder.col)
    }

    @Test
    fun `prune uninstalled apps collapses single-item folder to AppItem`() {
        val folder = HomeItem.FolderItem(
            id = "f1",
            title = "Tools",
            apps = listOf(
                AppRef("com.keep", "com.keep.Main", "Keep"),
                AppRef("com.removed", "com.removed.Main", "Removed")
            ),
            page = 0,
            row = 0,
            col = 0
        )
        val dock = listOf(
            DockItem.DockApp("d1", "com.keep", "com.keep.Main", "Keep"),
            DockItem.DockApp("d2", "com.removed", "com.removed.Main", "Removed")
        )

        val (nextHome, nextDock) = LayoutEngine.pruneUninstalledApps(
            homeItems = listOf(folder),
            dockItems = dock,
            installedKeys = setOf("com.keep/com.keep.Main"),
            installedPackages = setOf("com.keep")
        )

        assertEquals(1, nextHome.size)
        assertTrue(nextHome.first() is HomeItem.AppItem)
        assertEquals("com.keep", (nextHome.first() as HomeItem.AppItem).packageName)
        assertEquals(1, nextDock.size)
    }

    @Test
    fun `widget placement respects existing occupied cells`() {
        val app1 = HomeItem.AppItem("id1", "com.a", "com.a.Main", "App A", page = 0, row = 0, col = 0)
        val placement = LayoutEngine.findWidgetPlacement(
            items = listOf(app1),
            pageCount = 1,
            rows = 5,
            cols = 4,
            colSpan = 4,
            rowSpan = 2,
            preferredPage = 0
        )
        assertNotNull(placement)
        assertEquals(0, placement!!.first)
        assertEquals(1, placement.second)
        assertEquals(0, placement.third)
        assertFalse(LayoutEngine.isCellOccupied(listOf(app1), 0, 1, 0))
    }
}
