package com.example.viewmodel

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.example.core.DeviceCapabilities
import com.example.core.DeviceProfile
import com.example.data.AppRepository
import com.example.data.IconCache
import com.example.data.LauncherStorage
import com.example.model.AppInfo
import com.example.model.AppRef
import com.example.model.AppShortcutItem
import com.example.model.BatteryStatus
import com.example.model.DeviceStatus
import com.example.model.DockItem
import com.example.model.DrawerViewMode
import com.example.model.HomeItem
import com.example.model.LauncherSettings
import com.example.model.QuickSystemAction
import com.example.model.RestoreReport
import com.example.model.SearchResults
import com.example.model.SortOrder
import com.example.model.SwipeDownAction
import com.example.model.SwipeUpAction
import com.example.model.WallpaperMode
import com.example.system.NexusNotificationListenerService
import com.example.system.SystemIntegrationHelper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Pure Android SDK state controller replacing AndroidViewModel/StateFlow/Coroutines
 * to eliminate unnecessary reactive framework allocations.
 */
class LauncherController(
    private val context: Context,
    private val storage: LauncherStorage,
    val iconCache: IconCache,
    val appRepository: AppRepository,
    val systemHelper: SystemIntegrationHelper
) {
    interface StateListener {
        fun onLauncherStateChanged()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val listeners = CopyOnWriteArrayList<StateListener>()

    var capabilities: DeviceCapabilities = DeviceProfile.probe(context)
        private set

    var settings: LauncherSettings = storage.loadSettings()
        private set

    var pageCount: Int = 1
        private set

    var homeItems: List<HomeItem> = emptyList()
        private set

    var dockItems: List<DockItem> = emptyList()
        private set

    var allApps: List<AppInfo> = emptyList()
        private set

    var batteryStatus: BatteryStatus = BatteryStatus()
        private set

    var deviceStatus: DeviceStatus = systemHelper.getDeviceStatus()
        private set

    var customWallpaperBitmap: Bitmap? = null
        private set

    var badgeCounts: Map<String, Int> = emptyMap()
        private set

    var isEditMode: Boolean = false
        private set

    var bannerMessage: String? = null
        private set

    private val badgeObserver: (Map<String, Int>) -> Unit = { map ->
        badgeCounts = map
        notifyListeners()
    }

    init {
        iconCache.refreshAdaptiveProfile(settings.performanceMode)
        NexusNotificationListenerService.addBadgeListener(badgeObserver)
        loadInitialData()
    }

    fun addListener(listener: StateListener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: StateListener) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        mainHandler.post {
            for (listener in listeners) {
                listener.onLauncherStateChanged()
            }
        }
    }

    private fun loadInitialData() {
        ioExecutor.execute {
            val saved = storage.loadLayout()
            if (saved != null) {
                pageCount = saved.pageCount
                homeItems = saved.homeItems
                dockItems = saved.dockItems
            }
            if (settings.wallpaperMode == WallpaperMode.CUSTOM_FILE) {
                customWallpaperBitmap = storage.loadCustomWallpaperBitmapBlocking()
            }
            appRepository.reloadAppsAsync { loaded ->
                allApps = loaded
                if (!storage.hasSavedLayout() && loaded.isNotEmpty()) {
                    val (seededHome, seededDock) = LayoutEngine.seedInitialLayoutFromInstalledApps(loaded)
                    pageCount = 1
                    homeItems = seededHome
                    dockItems = seededDock
                    persistLayout()
                } else if (loaded.isNotEmpty()) {
                    pruneMissingPackages(loaded)
                }
                notifyListeners()
            }
        }
    }

    fun refreshAdaptiveCapabilities() {
        capabilities = DeviceProfile.probe(context)
        iconCache.refreshAdaptiveProfile(settings.performanceMode)
        deviceStatus = systemHelper.getDeviceStatus()
        notifyListeners()
    }

    fun refreshAppsAndStatus() {
        deviceStatus = systemHelper.getDeviceStatus()
        appRepository.reloadAppsAsync { loaded ->
            allApps = loaded
            pruneMissingPackages(loaded)
            notifyListeners()
        }
    }

    fun onPackageRemoved(packageName: String) {
        appRepository.onPackageRemoved(packageName)
        allApps = appRepository.apps
        pruneMissingPackages(allApps)
        notifyListeners()
    }

    private fun pruneMissingPackages(installed: List<AppInfo>) {
        if (installed.isEmpty()) return
        val keys = installed.map { it.componentKey }.toSet()
        val pkgs = installed.map { it.packageName }.toSet()
        val (nextHome, nextDock) = LayoutEngine.pruneUninstalledApps(
            homeItems,
            dockItems,
            keys,
            pkgs
        )
        if (nextHome != homeItems || nextDock != dockItems) {
            homeItems = nextHome
            dockItems = nextDock
            persistLayout()
        }
    }

    fun visibleDrawerApps(): List<AppInfo> {
        val filtered = allApps.filterNot { it.componentKey in settings.hiddenComponentKeys }
        return when (settings.drawerSortOrder) {
            SortOrder.AZ -> filtered.sortedBy { it.label.lowercase() }
            SortOrder.ZA -> filtered.sortedByDescending { it.label.lowercase() }
        }
    }

    fun recentApps(): List<AppInfo> {
        if (!settings.showRecentApps) return emptyList()
        val byKey = allApps.associateBy { it.componentKey }
        return appRepository.recentKeys.mapNotNull { key ->
            if (key in settings.hiddenComponentKeys) null else byKey[key]
        }.take(5)
    }

    fun searchLocal(
        query: String,
        shortcuts: List<AppShortcutItem> = emptyList()
    ): SearchResults {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return SearchResults()
        val matchedApps = allApps.filter { appInfo ->
            val isHidden = appInfo.componentKey in settings.hiddenComponentKeys
            val matches = appInfo.label.lowercase().contains(trimmed) ||
                appInfo.packageName.lowercase().contains(trimmed)
            matches && (!isHidden || trimmed.length >= 3)
        }.take(18)

        val matchedActions = quickActionsCatalog.filter {
            it.title.lowercase().contains(trimmed) || it.subtitle.lowercase().contains(trimmed)
        }
        return SearchResults(matchedApps, shortcuts, matchedActions)
    }

    fun reloadSettingsFromStorage() {
        settings = storage.loadSettings()
        capabilities = DeviceProfile.probe(context)
        iconCache.refreshAdaptiveProfile(settings.performanceMode)
        if (settings.wallpaperMode == WallpaperMode.CUSTOM_FILE && customWallpaperBitmap == null) {
            ioExecutor.execute {
                customWallpaperBitmap = storage.loadCustomWallpaperBitmapBlocking()
                notifyListeners()
            }
        }
        deviceStatus = systemHelper.getDeviceStatus()
        notifyListeners()
    }

    fun releaseTransientWallpaperCache() {
        if (settings.wallpaperMode != WallpaperMode.CUSTOM_FILE) {
            customWallpaperBitmap?.recycle()
            customWallpaperBitmap = null
        }
    }

    fun updateBatteryStatus(level: Int, isCharging: Boolean) {
        if (batteryStatus.level != level || batteryStatus.isCharging != isCharging) {
            batteryStatus = BatteryStatus(level, isCharging)
            notifyListeners()
        }
    }

    fun setEditMode(edit: Boolean) {
        if (edit && settings.lockHomeLayout) {
            showBanner("Tata letak Home terkunci. Buka kunci di Pengaturan > Home Screen.")
            return
        }
        if (isEditMode != edit) {
            isEditMode = edit
            notifyListeners()
        }
    }

    fun showBanner(message: String) {
        bannerMessage = message
        notifyListeners()
    }

    fun clearBanner() {
        if (bannerMessage != null) {
            bannerMessage = null
            notifyListeners()
        }
    }

    fun launchApp(packageName: String, activityName: String) {
        val ok = appRepository.launchApp(
            packageName,
            activityName,
            settings.performanceMode,
            capabilities
        )
        if (!ok) showBanner("Gagal membuka aplikasi")
    }

    fun launchShortcut(packageName: String, shortcutId: String) {
        val ok = appRepository.launchShortcut(packageName, shortcutId)
        if (!ok) showBanner("Pintasan membutuhkan Nexus Launcher sebagai Home default")
    }

    fun pinAppToHome(appInfo: AppInfo, targetPage: Int = 0) {
        if (settings.lockHomeLayout) {
            showBanner("Tata letak Home terkunci")
            return
        }
        var placement = LayoutEngine.findFirstFreeCell(
            homeItems,
            pageCount,
            settings.homeRows,
            settings.homeColumns,
            preferredPage = targetPage
        )
        if (placement == null) {
            val newPage = pageCount
            pageCount = newPage + 1
            placement = Triple(newPage, 0, 0)
        }
        val newItem = HomeItem.AppItem(
            id = LayoutEngine.nextId("app"),
            packageName = appInfo.packageName,
            activityName = appInfo.activityName,
            label = appInfo.label,
            page = placement.first,
            row = placement.second,
            col = placement.third
        )
        homeItems = homeItems + newItem
        persistLayout()
        showBanner("${appInfo.label} ditambahkan ke Home (Hal. ${placement.first + 1})")
    }

    fun pinShortcutToHome(shortcut: AppShortcutItem, targetPage: Int = 0) {
        if (settings.lockHomeLayout) {
            showBanner("Tata letak Home terkunci")
            return
        }
        var placement = LayoutEngine.findFirstFreeCell(
            homeItems,
            pageCount,
            settings.homeRows,
            settings.homeColumns,
            preferredPage = targetPage
        )
        if (placement == null) {
            val newPage = pageCount
            pageCount = newPage + 1
            placement = Triple(newPage, 0, 0)
        }
        val item = HomeItem.ShortcutItem(
            id = LayoutEngine.nextId("shortcut"),
            packageName = shortcut.packageName,
            shortcutId = shortcut.id,
            label = shortcut.shortLabel,
            page = placement.first,
            row = placement.second,
            col = placement.third
        )
        homeItems = homeItems + item
        persistLayout()
        showBanner("Pintasan ${shortcut.shortLabel} ditambahkan ke Home")
    }

    fun pinAppToDock(appInfo: AppInfo) {
        if (settings.lockHomeLayout) {
            showBanner("Tata letak Home terkunci")
            return
        }
        if (dockItems.size >= 5) {
            showBanner("Dock penuh (maksimal 5 item)")
            return
        }
        if (dockItems.any { it is DockItem.DockApp && it.componentKey == appInfo.componentKey }) {
            showBanner("${appInfo.label} sudah ada di Dock")
            return
        }
        dockItems = dockItems + DockItem.DockApp(
            id = LayoutEngine.nextId("dock"),
            packageName = appInfo.packageName,
            activityName = appInfo.activityName,
            label = appInfo.label
        )
        persistLayout()
        showBanner("${appInfo.label} ditambahkan ke Dock")
    }

    fun removeHomeItem(itemId: String) {
        homeItems = homeItems.filterNot { it.id == itemId }
        persistLayout()
        notifyListeners()
    }

    fun removeDockItem(dockId: String) {
        dockItems = dockItems.filterNot { it.id == dockId }
        persistLayout()
        notifyListeners()
    }

    fun dropHomeItem(draggedId: String, targetPage: Int, targetRow: Int, targetCol: Int) {
        if (settings.lockHomeLayout) return
        val updated = LayoutEngine.dropHomeItemAt(
            items = homeItems,
            draggedId = draggedId,
            targetPage = targetPage,
            targetRow = targetRow,
            targetCol = targetCol,
            maxRows = settings.homeRows,
            maxCols = settings.homeColumns
        )
        if (updated != homeItems) {
            homeItems = updated
            persistLayout()
            notifyListeners()
        }
    }

    fun addAppToFolderOrCreate(
        sourceApp: AppInfo,
        targetFolderId: String? = null,
        targetAppItemId: String? = null
    ) {
        if (settings.lockHomeLayout) {
            showBanner("Tata letak Home terkunci")
            return
        }
        val ref = AppRef(sourceApp.packageName, sourceApp.activityName, sourceApp.label)
        if (targetFolderId != null) {
            homeItems = homeItems.map { item ->
                if (item is HomeItem.FolderItem && item.id == targetFolderId) {
                    if (item.apps.any { it.componentKey == ref.componentKey }) item
                    else item.copy(apps = item.apps + ref)
                } else item
            }
            persistLayout()
            notifyListeners()
            return
        }
        if (targetAppItemId != null) {
            val target = homeItems.firstOrNull { it.id == targetAppItemId } as? HomeItem.AppItem
                ?: return
            val folder = HomeItem.FolderItem(
                id = LayoutEngine.nextId("folder"),
                title = "Folder",
                apps = listOf(
                    AppRef(target.packageName, target.activityName, target.label),
                    ref
                ),
                page = target.page,
                row = target.row,
                col = target.col
            )
            homeItems = homeItems.filterNot { it.id == target.id } + folder
            persistLayout()
            notifyListeners()
        }
    }

    fun renameFolder(folderId: String, newTitle: String) {
        val clean = newTitle.trim().ifEmpty { "Folder" }
        homeItems = homeItems.map { item ->
            if (item is HomeItem.FolderItem && item.id == folderId) item.copy(title = clean)
            else item
        }
        dockItems = dockItems.map { dock ->
            if (dock is DockItem.DockFolder && dock.id == folderId) dock.copy(title = clean)
            else dock
        }
        persistLayout()
        notifyListeners()
    }

    fun removeAppFromFolder(folderId: String, componentKey: String) {
        homeItems = homeItems.mapNotNull { item ->
            if (item is HomeItem.FolderItem && item.id == folderId) {
                val remaining = item.apps.filterNot { it.componentKey == componentKey }
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
            } else item
        }
        persistLayout()
        notifyListeners()
    }

    fun addHomePage(): Int {
        val newIndex = pageCount
        pageCount = newIndex + 1
        persistLayout()
        notifyListeners()
        return newIndex
    }

    fun removeEmptyPage(pageIndex: Int): Boolean {
        if (pageCount <= 1) return false
        if (homeItems.any { it.page == pageIndex }) {
            showBanner("Kosongkan halaman terlebih dahulu sebelum menghapus")
            return false
        }
        homeItems = homeItems.map { item ->
            if (item.page > pageIndex) {
                when (item) {
                    is HomeItem.AppItem -> item.copy(page = item.page - 1)
                    is HomeItem.ShortcutItem -> item.copy(page = item.page - 1)
                    is HomeItem.FolderItem -> item.copy(page = item.page - 1)
                    is HomeItem.WidgetItem -> item.copy(page = item.page - 1)
                }
            } else item
        }
        pageCount = (pageCount - 1).coerceAtLeast(1)
        persistLayout()
        notifyListeners()
        return true
    }

    fun addWidgetItem(
        appWidgetId: Int,
        info: AppWidgetProviderInfo,
        label: String,
        preferredPage: Int
    ) {
        val colSpan = (info.minWidth / 80).coerceIn(2, settings.homeColumns)
        val rowSpan = (info.minHeight / 80).coerceIn(1, (settings.homeRows - 1).coerceAtLeast(1))
        var placement = LayoutEngine.findWidgetPlacement(
            items = homeItems,
            pageCount = pageCount,
            rows = settings.homeRows,
            cols = settings.homeColumns,
            colSpan = colSpan,
            rowSpan = rowSpan,
            preferredPage = preferredPage
        )
        if (placement == null) {
            val newPage = pageCount
            pageCount = newPage + 1
            placement = Triple(newPage, 0, 0)
        }
        val widgetItem = HomeItem.WidgetItem(
            id = LayoutEngine.nextId("widget"),
            appWidgetId = appWidgetId,
            providerPackage = info.provider.packageName,
            providerClass = info.provider.className,
            label = label,
            page = placement.first,
            row = placement.second,
            col = placement.third,
            colSpan = colSpan.coerceAtMost(settings.homeColumns - placement.third),
            rowSpan = rowSpan.coerceAtMost(settings.homeRows - placement.second)
        )
        homeItems = homeItems + widgetItem
        persistLayout()
        notifyListeners()
    }

    fun resizeWidget(widgetId: String, deltaCol: Int, deltaRow: Int) {
        val widget = homeItems.firstOrNull { it.id == widgetId } as? HomeItem.WidgetItem ?: return
        val newColSpan = (widget.colSpan + deltaCol).coerceIn(1, settings.homeColumns - widget.col)
        val newRowSpan = (widget.rowSpan + deltaRow).coerceIn(1, settings.homeRows - widget.row)
        for (dr in 0 until newRowSpan) {
            for (dc in 0 until newColSpan) {
                if (LayoutEngine.isCellOccupied(
                        homeItems,
                        widget.page,
                        widget.row + dr,
                        widget.col + dc,
                        widget.id
                    )
                ) {
                    showBanner("Ruang grid tidak cukup untuk memperbesar widget")
                    return
                }
            }
        }
        homeItems = homeItems.map {
            if (it.id == widgetId) widget.copy(colSpan = newColSpan, rowSpan = newRowSpan) else it
        }
        persistLayout()
        notifyListeners()
    }

    fun toggleHiddenApp(componentKey: String) {
        val current = settings.hiddenComponentKeys
        val updated = if (componentKey in current) current - componentKey else current + componentKey
        updateSettings { it.copy(hiddenComponentKeys = updated) }
    }

    fun toggleDrawerViewMode() {
        val next = if (settings.drawerViewMode == DrawerViewMode.GRID) {
            DrawerViewMode.LIST
        } else DrawerViewMode.GRID
        updateSettings { it.copy(drawerViewMode = next) }
    }

    fun toggleDrawerSortOrder() {
        val next = if (settings.drawerSortOrder == SortOrder.AZ) SortOrder.ZA else SortOrder.AZ
        updateSettings { it.copy(drawerSortOrder = next) }
    }

    fun dismissDefaultLauncherBanner() {
        updateSettings { it.copy(defaultBannerDismissed = true) }
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        val next = transform(settings)
        settings = next
        storage.saveSettings(next)
        iconCache.refreshAdaptiveProfile(next.performanceMode)
        notifyListeners()
    }

    fun applyGalleryWallpaper(uri: Uri, onComplete: (Boolean) -> Unit) {
        ioExecutor.execute {
            val saved = storage.saveCustomWallpaperFromUriBlocking(uri)
            if (saved) {
                customWallpaperBitmap = storage.loadCustomWallpaperBitmapBlocking()
                mainHandler.post {
                    updateSettings { it.copy(wallpaperMode = WallpaperMode.CUSTOM_FILE) }
                    onComplete(true)
                }
            } else {
                mainHandler.post { onComplete(false) }
            }
        }
    }

    fun applyCurrentWallpaperToSystem(onComplete: (Boolean) -> Unit) {
        val mode = settings.wallpaperMode
        val bmp = customWallpaperBitmap
        ioExecutor.execute {
            val ok = systemHelper.applyWallpaperToSystemBlocking(mode, bmp)
            mainHandler.post { onComplete(ok) }
        }
    }

    fun handleSwipeUp(openDrawer: () -> Unit, openSearch: () -> Unit): Boolean {
        return when (settings.swipeUpAction) {
            SwipeUpAction.OPEN_DRAWER -> { openDrawer(); true }
            SwipeUpAction.SMART_SEARCH -> { openSearch(); true }
            SwipeUpAction.NONE -> false
        }
    }

    fun handleSwipeDown(openSearch: () -> Unit): Boolean {
        return when (settings.swipeDownAction) {
            SwipeDownAction.SMART_SEARCH -> { openSearch(); true }
            SwipeDownAction.NOTIFICATION_SHADE -> {
                val ok = systemHelper.expandNotificationShade()
                if (!ok) {
                    openSearch()
                    showBanner("Panel notifikasi dibatasi sistem; membuka Smart Search")
                }
                true
            }
            SwipeDownAction.NONE -> false
        }
    }

    fun handleDoubleTapEmptySpace(): Boolean {
        if (!settings.doubleTapLockEnabled) return false
        val locked = systemHelper.lockScreenNow()
        if (!locked) {
            showBanner("Aktifkan izin Kunci Layar (Device Admin) di Pengaturan > Gestures")
        }
        return locked
    }

    fun exportBackupJson(): String {
        return storage.exportBackupJson(settings, pageCount, homeItems, dockItems)
    }

    fun importBackupJson(rawJson: String): RestoreReport {
        val parsed = storage.importBackupJson(rawJson)
            ?: return RestoreReport(false, "Format file backup tidak valid.")
        val skippedWidgets = storage.countWidgetsInBackupJson(rawJson)
        val (restoredSettings, restoredLayout) = parsed
        settings = restoredSettings
        storage.saveSettings(restoredSettings)
        pageCount = restoredLayout.pageCount
        homeItems = restoredLayout.homeItems
        dockItems = restoredLayout.dockItems
        pruneMissingPackages(appRepository.apps)
        persistLayout()
        notifyListeners()
        return RestoreReport(
            success = true,
            message = "Konfigurasi dipulihkan.",
            restoredHomeItems = homeItems.size,
            restoredDockItems = dockItems.size,
            skippedWidgets = skippedWidgets
        )
    }

    fun resetLauncherSettings() {
        storage.resetAll()
        settings = LauncherSettings()
        customWallpaperBitmap = null
        iconCache.clear()
        val apps = appRepository.apps
        val (seededHome, seededDock) = LayoutEngine.seedInitialLayoutFromInstalledApps(apps)
        pageCount = 1
        homeItems = seededHome
        dockItems = seededDock
        persistLayout()
        notifyListeners()
    }

    private fun persistLayout() {
        storage.saveLayout(pageCount, homeItems, dockItems)
    }

    companion object {
        val quickActionsCatalog = listOf(
            QuickSystemAction(
                id = "qa_wifi",
                title = "Pengaturan Wi-Fi",
                subtitle = "Buka jaringan nirkabel sistem",
                actionIntent = Settings.ACTION_WIFI_SETTINGS
            ),
            QuickSystemAction(
                id = "qa_bluetooth",
                title = "Pengaturan Bluetooth",
                subtitle = "Kelola perangkat terhubung",
                actionIntent = Settings.ACTION_BLUETOOTH_SETTINGS
            ),
            QuickSystemAction(
                id = "qa_display",
                title = "Layar & Kecerahan",
                subtitle = "Atur kecerahan dan mode gelap",
                actionIntent = Settings.ACTION_DISPLAY_SETTINGS
            ),
            QuickSystemAction(
                id = "qa_battery",
                title = "Pengaturan Baterai",
                subtitle = "Optimasi daya & status baterai",
                actionIntent = Intent.ACTION_POWER_USAGE_SUMMARY
            ),
            QuickSystemAction(
                id = "qa_home",
                title = "Aplikasi Home Default",
                subtitle = "Pilih atau kembalikan launcher bawaan",
                actionIntent = Settings.ACTION_HOME_SETTINGS
            )
        )
    }
}
