package com.example.model

import android.content.ComponentName

data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String
) {
    val componentKey: String get() = "$packageName/$activityName"
    val componentName: ComponentName get() = ComponentName(packageName, activityName)
}

data class AppRef(
    val packageName: String,
    val activityName: String,
    val label: String
) {
    val componentKey: String get() = "$packageName/$activityName"
}

data class AppShortcutItem(
    val id: String,
    val packageName: String,
    val shortLabel: String,
    val longLabel: String,
    val rank: Int
)

sealed interface HomeItem {
    val id: String
    val page: Int
    val row: Int
    val col: Int

    data class AppItem(
        override val id: String,
        val packageName: String,
        val activityName: String,
        val label: String,
        override val page: Int,
        override val row: Int,
        override val col: Int
    ) : HomeItem {
        val componentKey: String get() = "$packageName/$activityName"
    }

    data class ShortcutItem(
        override val id: String,
        val packageName: String,
        val shortcutId: String,
        val label: String,
        override val page: Int,
        override val row: Int,
        override val col: Int
    ) : HomeItem

    data class FolderItem(
        override val id: String,
        val title: String,
        val apps: List<AppRef>,
        override val page: Int,
        override val row: Int,
        override val col: Int
    ) : HomeItem

    data class WidgetItem(
        override val id: String,
        val appWidgetId: Int,
        val providerPackage: String,
        val providerClass: String,
        val label: String,
        override val page: Int,
        override val row: Int,
        override val col: Int,
        val colSpan: Int = 4,
        val rowSpan: Int = 2
    ) : HomeItem
}

sealed interface DockItem {
    val id: String

    data class DockApp(
        override val id: String,
        val packageName: String,
        val activityName: String,
        val label: String
    ) : DockItem {
        val componentKey: String get() = "$packageName/$activityName"
    }

    data class DockFolder(
        override val id: String,
        val title: String,
        val apps: List<AppRef>
    ) : DockItem
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class PerformanceMode { AUTO_ADAPTIVE, ULTRA_BATTERY_SAVER, BALANCED, SMOOTH }
enum class IconSizePreset(val dpSize: Int) { SMALL(44), MEDIUM(52), LARGE(60) }
enum class TextSizePreset(val spSize: Int) { HIDDEN(0), SMALL(10), MEDIUM(12), LARGE(14) }
enum class IconShapePreset { SYSTEM, SQUIRCLE, CIRCLE, ROUNDED_SQUARE, TEARDROP }
enum class DrawerViewMode { GRID, LIST }
enum class SortOrder { AZ, ZA }
enum class SwipeUpAction { OPEN_DRAWER, SMART_SEARCH, NONE }
enum class SwipeDownAction { SMART_SEARCH, NOTIFICATION_SHADE, NONE }
enum class WallpaperMode { SYSTEM, BUILTIN_OBSIDIAN, BUILTIN_AURORA, BUILTIN_DUNE, CUSTOM_FILE }

data class LauncherSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val performanceMode: PerformanceMode = PerformanceMode.AUTO_ADAPTIVE,
    val iconSize: IconSizePreset = IconSizePreset.MEDIUM,
    val textSize: TextSizePreset = TextSizePreset.MEDIUM,
    val iconShape: IconShapePreset = IconShapePreset.SQUIRCLE,
    val homeColumns: Int = 4,
    val homeRows: Int = 5,
    val showSearchBar: Boolean = true,
    val showDock: Boolean = true,
    val dockTransparency: Float = 0.28f,
    val lockHomeLayout: Boolean = false,
    val showClockSeconds: Boolean = false,
    val use24HourClock: Boolean? = null,
    val showBatteryInfo: Boolean = true,
    val drawerViewMode: DrawerViewMode = DrawerViewMode.GRID,
    val drawerColumns: Int = 4,
    val drawerSortOrder: SortOrder = SortOrder.AZ,
    val showRecentApps: Boolean = true,
    val swipeUpAction: SwipeUpAction = SwipeUpAction.OPEN_DRAWER,
    val swipeDownAction: SwipeDownAction = SwipeDownAction.SMART_SEARCH,
    val doubleTapLockEnabled: Boolean = true,
    val wallpaperMode: WallpaperMode = WallpaperMode.BUILTIN_OBSIDIAN,
    val wallpaperDim: Float = 0.12f,
    val hiddenComponentKeys: Set<String> = emptySet(),
    val notificationDotsEnabled: Boolean = true,
    val defaultBannerDismissed: Boolean = false
)

data class BatteryStatus(
    val level: Int = 100,
    val isCharging: Boolean = false
)

data class DeviceStatus(
    val isDefaultLauncher: Boolean = false,
    val currentDefaultHomePackage: String? = null,
    val isNotificationAccessGranted: Boolean = false,
    val isDeviceAdminActive: Boolean = false,
    val totalRamMb: Long = 0L,
    val appHeapUsedMb: Long = 0L
)

data class QuickSystemAction(
    val id: String,
    val title: String,
    val subtitle: String,
    val actionIntent: String
)

data class SearchResults(
    val apps: List<AppInfo> = emptyList(),
    val shortcuts: List<AppShortcutItem> = emptyList(),
    val quickActions: List<QuickSystemAction> = emptyList()
)

data class RestoreReport(
    val success: Boolean,
    val message: String,
    val restoredHomeItems: Int = 0,
    val restoredDockItems: Int = 0,
    val skippedWidgets: Int = 0
)
