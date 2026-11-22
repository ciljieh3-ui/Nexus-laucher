package com.example.viewmodel

import com.example.model.AppInfo
import com.example.model.AppRef
import com.example.model.DockItem
import com.example.model.HomeItem
import java.util.UUID

/**
 * Pure, side-effect-free layout operations for Home Screen grid, folders, widgets, and dock.
 * Designed for deterministic unit testing and minimal object churn.
 */
object LayoutEngine {

    fun nextId(prefix: String): String = "${prefix}_${UUID.randomUUID().toString().take(8)}"

    fun isCellOccupied(
        items: List<HomeItem>,
        page: Int,
        row: Int,
        col: Int,
        ignoreItemId: String? = null
    ): Boolean {
        return items.any { item ->
            if (item.id == ignoreItemId || item.page != page) return@any false
            when (item) {
                is HomeItem.WidgetItem ->
                    row in item.row until (item.row + item.rowSpan) &&
                        col in item.col until (item.col + item.colSpan)
                else -> item.row == row && item.col == col
            }
        }
    }

    fun findFirstFreeCell(
        items: List<HomeItem>,
        pageCount: Int,
        rows: Int,
        cols: Int,
        preferredPage: Int = 0
    ): Triple<Int, Int, Int>? {
        val pageOrder = (listOf(preferredPage) + (0 until pageCount).filter { it != preferredPage })
            .filter { it in 0 until pageCount }
        for (p in pageOrder) {
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (!isCellOccupied(items, p, r, c)) {
                        return Triple(p, r, c)
                    }
                }
            }
        }
        return null
    }

    fun findWidgetPlacement(
        items: List<HomeItem>,
        pageCount: Int,
        rows: Int,
        cols: Int,
        colSpan: Int,
        rowSpan: Int,
        preferredPage: Int
    ): Triple<Int, Int, Int>? {
        val clampedColSpan = colSpan.coerceIn(1, cols)
        val clampedRowSpan = rowSpan.coerceIn(1, rows)
        val pageOrder = (listOf(preferredPage) + (0 until pageCount).filter { it != preferredPage })
            .filter { it in 0 until pageCount }

        for (p in pageOrder) {
            for (r in 0..(rows - clampedRowSpan)) {
                for (c in 0..(cols - clampedColSpan)) {
                    var fits = true
                    for (dr in 0 until clampedRowSpan) {
                        for (dc in 0 until clampedColSpan) {
                            if (isCellOccupied(items, p, r + dr, c + dc)) {
                                fits = false
                                break
                            }
                        }
                        if (!fits) break
                    }
                    if (fits) return Triple(p, r, c)
                }
            }
        }
        return null
    }

    /**
     * Moves an item to (targetPage, targetRow, targetCol).
     * If an AppItem is dropped onto another AppItem or FolderItem, merges into a Folder!
     * If dropped on another occupied cell of an incompatible type, swaps or cancels cleanly.
     */
    fun dropHomeItemAt(
        items: List<HomeItem>,
        draggedId: String,
        targetPage: Int,
        targetRow: Int,
        targetCol: Int,
        maxRows: Int,
        maxCols: Int
    ): List<HomeItem> {
        val dragged = items.firstOrNull { it.id == draggedId } ?: return items
        val row = targetRow.coerceIn(0, maxRows - 1)
        val col = targetCol.coerceIn(0, maxCols - 1)

        if (dragged.page == targetPage && dragged.row == row && dragged.col == col) {
            return items
        }

        // Check target occupant at exact cell
        val targetItem = items.firstOrNull { item ->
            item.id != draggedId && item.page == targetPage && when (item) {
                is HomeItem.WidgetItem ->
                    row in item.row until (item.row + item.rowSpan) &&
                        col in item.col until (item.col + item.colSpan)
                else -> item.row == row && item.col == col
            }
        }

        // Widget movement: ensure span fits cleanly without colliding
        if (dragged is HomeItem.WidgetItem) {
            val safeCol = col.coerceIn(0, (maxCols - dragged.colSpan).coerceAtLeast(0))
            val safeRow = row.coerceIn(0, (maxRows - dragged.rowSpan).coerceAtLeast(0))
            for (dr in 0 until dragged.rowSpan) {
                for (dc in 0 until dragged.colSpan) {
                    if (isCellOccupied(items, targetPage, safeRow + dr, safeCol + dc, dragged.id)) {
                        return items
                    }
                }
            }
            return items.map {
                if (it.id == dragged.id) {
                    dragged.copy(page = targetPage, row = safeRow, col = safeCol)
                } else it
            }
        }

        if (targetItem == null) {
            return items.map {
                if (it.id == draggedId) moveItemPosition(it, targetPage, row, col) else it
            }
        }

        // Merge AppItem onto AppItem -> FolderItem
        if (dragged is HomeItem.AppItem && targetItem is HomeItem.AppItem) {
            val folder = HomeItem.FolderItem(
                id = nextId("folder"),
                title = "Folder",
                apps = listOf(
                    AppRef(targetItem.packageName, targetItem.activityName, targetItem.label),
                    AppRef(dragged.packageName, dragged.activityName, dragged.label)
                ),
                page = targetPage,
                row = row,
                col = col
            )
            return items.filterNot { it.id == dragged.id || it.id == targetItem.id } + folder
        }

        // Merge AppItem onto FolderItem -> add into FolderItem
        if (dragged is HomeItem.AppItem && targetItem is HomeItem.FolderItem) {
            val newRef = AppRef(dragged.packageName, dragged.activityName, dragged.label)
            val updatedApps = if (targetItem.apps.any { it.componentKey == newRef.componentKey }) {
                targetItem.apps
            } else {
                targetItem.apps + newRef
            }
            val updatedFolder = targetItem.copy(apps = updatedApps)
            return items.filterNot { it.id == dragged.id }.map {
                if (it.id == targetItem.id) updatedFolder else it
            }
        }

        // Swap two non-widget items
        if (targetItem !is HomeItem.WidgetItem) {
            return items.map { item ->
                when (item.id) {
                    dragged.id -> moveItemPosition(item, targetPage, row, col)
                    targetItem.id -> moveItemPosition(item, dragged.page, dragged.row, dragged.col)
                    else -> item
                }
            }
        }

        return items
    }

    private fun moveItemPosition(item: HomeItem, page: Int, row: Int, col: Int): HomeItem {
        return when (item) {
            is HomeItem.AppItem -> item.copy(page = page, row = row, col = col)
            is HomeItem.ShortcutItem -> item.copy(page = page, row = row, col = col)
            is HomeItem.FolderItem -> item.copy(page = page, row = row, col = col)
            is HomeItem.WidgetItem -> item.copy(page = page, row = row, col = col)
        }
    }

    fun pruneUninstalledApps(
        homeItems: List<HomeItem>,
        dockItems: List<DockItem>,
        installedKeys: Set<String>,
        installedPackages: Set<String>
    ): Pair<List<HomeItem>, List<DockItem>> {
        val nextHome = homeItems.mapNotNull { item ->
            when (item) {
                is HomeItem.AppItem -> if (item.componentKey in installedKeys) item else null
                is HomeItem.ShortcutItem -> if (item.packageName in installedPackages) item else null
                is HomeItem.WidgetItem -> if (item.providerPackage in installedPackages) item else null
                is HomeItem.FolderItem -> {
                    val remaining = item.apps.filter { it.componentKey in installedKeys }
                    when {
                        remaining.isEmpty() -> null
                        remaining.size == 1 -> {
                            val single = remaining.first()
                            HomeItem.AppItem(
                                id = item.id,
                                packageName = single.packageName,
                                activityName = single.activityName,
                                label = single.label,
                                page = item.page,
                                row = item.row,
                                col = item.col
                            )
                        }
                        else -> item.copy(apps = remaining)
                    }
                }
            }
        }

        val nextDock = dockItems.mapNotNull { dock ->
            when (dock) {
                is DockItem.DockApp -> if (dock.componentKey in installedKeys) dock else null
                is DockItem.DockFolder -> {
                    val remaining = dock.apps.filter { it.componentKey in installedKeys }
                    when {
                        remaining.isEmpty() -> null
                        remaining.size == 1 -> {
                            val single = remaining.first()
                            DockItem.DockApp(
                                id = dock.id,
                                packageName = single.packageName,
                                activityName = single.activityName,
                                label = single.label
                            )
                        }
                        else -> dock.copy(apps = remaining)
                    }
                }
            }
        }

        return nextHome to nextDock
    }

    /**
     * Seeds initial Dock & Page 0 ONLY from genuine installed apps on the device.
     */
    fun seedInitialLayoutFromInstalledApps(installed: List<AppInfo>): Pair<List<HomeItem>, List<DockItem>> {
        if (installed.isEmpty()) return emptyList<HomeItem>() to emptyList()

        val dockPriorityKeywords = listOf("dialer", "phone", "contacts", "messaging", "mms", "sms", "chrome", "browser", "camera")
        val dockApps = mutableListOf<AppInfo>()
        for (keyword in dockPriorityKeywords) {
            if (dockApps.size >= 4) break
            val match = installed.firstOrNull { app ->
                app !in dockApps && (
                    app.packageName.lowercase().contains(keyword) ||
                        app.label.lowercase().contains(keyword)
                    )
            }
            if (match != null) dockApps.add(match)
        }
        for (app in installed) {
            if (dockApps.size >= 4) break
            if (app !in dockApps) dockApps.add(app)
        }

        val dockItems = dockApps.map {
            DockItem.DockApp(
                id = nextId("dock"),
                packageName = it.packageName,
                activityName = it.activityName,
                label = it.label
            )
        }

        val homeCandidates = installed.filterNot { it in dockApps }.take(8)
        val homeItems = homeCandidates.mapIndexed { index, app ->
            HomeItem.AppItem(
                id = nextId("app"),
                packageName = app.packageName,
                activityName = app.activityName,
                label = app.label,
                page = 0,
                row = 2 + (index / 4),
                col = index % 4
            )
        }

        return homeItems to dockItems
    }
}
