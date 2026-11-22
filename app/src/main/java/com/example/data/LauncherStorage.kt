package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.model.AppRef
import com.example.model.DockItem
import com.example.model.DrawerViewMode
import com.example.model.HomeItem
import com.example.model.IconShapePreset
import com.example.model.IconSizePreset
import com.example.model.LauncherSettings
import com.example.model.PerformanceMode
import com.example.model.SortOrder
import com.example.model.SwipeDownAction
import com.example.model.SwipeUpAction
import com.example.model.TextSizePreset
import com.example.model.ThemeMode
import com.example.model.WallpaperMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Zero-dependency offline persistence using SharedPreferences, org.json,
 * and adaptive DisplayMetrics bitmap sampling.
 */
class LauncherStorage(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val layoutFile: File
        get() = File(context.filesDir, LAYOUT_FILE_NAME)

    val customWallpaperFile: File
        get() = File(context.filesDir, CUSTOM_WALLPAPER_FILE_NAME)

    fun loadSettings(): LauncherSettings {
        return LauncherSettings(
            themeMode = enumOrDefault(prefs.getString(KEY_THEME_MODE, null), ThemeMode.SYSTEM),
            performanceMode = enumOrDefault(
                prefs.getString(KEY_PERFORMANCE_MODE, null),
                PerformanceMode.AUTO_ADAPTIVE
            ),
            iconSize = enumOrDefault(prefs.getString(KEY_ICON_SIZE, null), IconSizePreset.MEDIUM),
            textSize = enumOrDefault(prefs.getString(KEY_TEXT_SIZE, null), TextSizePreset.MEDIUM),
            iconShape = enumOrDefault(prefs.getString(KEY_ICON_SHAPE, null), IconShapePreset.SQUIRCLE),
            homeColumns = prefs.getInt(KEY_HOME_COLS, 4).coerceIn(4, 5),
            homeRows = prefs.getInt(KEY_HOME_ROWS, 5).coerceIn(4, 6),
            showSearchBar = prefs.getBoolean(KEY_SHOW_SEARCH_BAR, true),
            showDock = prefs.getBoolean(KEY_SHOW_DOCK, true),
            dockTransparency = prefs.getFloat(KEY_DOCK_TRANSPARENCY, 0.28f).coerceIn(0f, 1f),
            lockHomeLayout = prefs.getBoolean(KEY_LOCK_HOME_LAYOUT, false),
            showClockSeconds = prefs.getBoolean(KEY_SHOW_CLOCK_SECONDS, false),
            use24HourClock = when (prefs.getInt(KEY_USE_24H_CLOCK, -1)) {
                1 -> true
                0 -> false
                else -> null
            },
            showBatteryInfo = prefs.getBoolean(KEY_SHOW_BATTERY_INFO, true),
            drawerViewMode = enumOrDefault(
                prefs.getString(KEY_DRAWER_VIEW_MODE, null),
                DrawerViewMode.GRID
            ),
            drawerColumns = prefs.getInt(KEY_DRAWER_COLS, 4).coerceIn(3, 6),
            drawerSortOrder = enumOrDefault(
                prefs.getString(KEY_DRAWER_SORT, null),
                SortOrder.AZ
            ),
            showRecentApps = prefs.getBoolean(KEY_SHOW_RECENT_APPS, true),
            swipeUpAction = enumOrDefault(
                prefs.getString(KEY_SWIPE_UP, null),
                SwipeUpAction.OPEN_DRAWER
            ),
            swipeDownAction = enumOrDefault(
                prefs.getString(KEY_SWIPE_DOWN, null),
                SwipeDownAction.SMART_SEARCH
            ),
            doubleTapLockEnabled = prefs.getBoolean(KEY_DOUBLE_TAP_LOCK, true),
            wallpaperMode = enumOrDefault(
                prefs.getString(KEY_WALLPAPER_MODE, null),
                WallpaperMode.BUILTIN_OBSIDIAN
            ),
            wallpaperDim = prefs.getFloat(KEY_WALLPAPER_DIM, 0.12f).coerceIn(0f, 0.7f),
            hiddenComponentKeys = prefs.getStringSet(KEY_HIDDEN_APPS, emptySet())?.toSet()
                ?: emptySet(),
            notificationDotsEnabled = prefs.getBoolean(KEY_NOTIFICATION_DOTS, true),
            defaultBannerDismissed = prefs.getBoolean(KEY_DEFAULT_BANNER_DISMISSED, false)
        )
    }

    fun saveSettings(settings: LauncherSettings) {
        prefs.edit()
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putString(KEY_PERFORMANCE_MODE, settings.performanceMode.name)
            .putString(KEY_ICON_SIZE, settings.iconSize.name)
            .putString(KEY_TEXT_SIZE, settings.textSize.name)
            .putString(KEY_ICON_SHAPE, settings.iconShape.name)
            .putInt(KEY_HOME_COLS, settings.homeColumns)
            .putInt(KEY_HOME_ROWS, settings.homeRows)
            .putBoolean(KEY_SHOW_SEARCH_BAR, settings.showSearchBar)
            .putBoolean(KEY_SHOW_DOCK, settings.showDock)
            .putFloat(KEY_DOCK_TRANSPARENCY, settings.dockTransparency)
            .putBoolean(KEY_LOCK_HOME_LAYOUT, settings.lockHomeLayout)
            .putBoolean(KEY_SHOW_CLOCK_SECONDS, settings.showClockSeconds)
            .putInt(
                KEY_USE_24H_CLOCK,
                when (settings.use24HourClock) {
                    true -> 1
                    false -> 0
                    null -> -1
                }
            )
            .putBoolean(KEY_SHOW_BATTERY_INFO, settings.showBatteryInfo)
            .putString(KEY_DRAWER_VIEW_MODE, settings.drawerViewMode.name)
            .putInt(KEY_DRAWER_COLS, settings.drawerColumns)
            .putString(KEY_DRAWER_SORT, settings.drawerSortOrder.name)
            .putBoolean(KEY_SHOW_RECENT_APPS, settings.showRecentApps)
            .putString(KEY_SWIPE_UP, settings.swipeUpAction.name)
            .putString(KEY_SWIPE_DOWN, settings.swipeDownAction.name)
            .putBoolean(KEY_DOUBLE_TAP_LOCK, settings.doubleTapLockEnabled)
            .putString(KEY_WALLPAPER_MODE, settings.wallpaperMode.name)
            .putFloat(KEY_WALLPAPER_DIM, settings.wallpaperDim)
            .putStringSet(KEY_HIDDEN_APPS, settings.hiddenComponentKeys)
            .putBoolean(KEY_NOTIFICATION_DOTS, settings.notificationDotsEnabled)
            .putBoolean(KEY_DEFAULT_BANNER_DISMISSED, settings.defaultBannerDismissed)
            .apply()
    }

    fun hasSavedLayout(): Boolean = layoutFile.exists()

    fun loadRecentComponentKeys(): List<String> {
        val raw = prefs.getString(KEY_RECENT_APPS, null) ?: return emptyList()
        return raw.split("|").filter { it.isNotBlank() }
    }

    fun saveRecentComponentKeys(keys: List<String>) {
        prefs.edit().putString(KEY_RECENT_APPS, keys.take(8).joinToString("|")).apply()
    }

    data class PersistedLayout(
        val pageCount: Int,
        val homeItems: List<HomeItem>,
        val dockItems: List<DockItem>
    )

    fun loadLayout(): PersistedLayout? {
        if (!layoutFile.exists()) return null
        return try {
            val json = JSONObject(layoutFile.readText())
            parseLayoutJson(json, includeWidgets = true)
        } catch (_: Exception) {
            null
        }
    }

    fun saveLayout(pageCount: Int, homeItems: List<HomeItem>, dockItems: List<DockItem>) {
        val json = serializeLayoutJson(pageCount, homeItems, dockItems)
        layoutFile.writeText(json.toString())
    }

    fun exportBackupJson(
        settings: LauncherSettings,
        pageCount: Int,
        homeItems: List<HomeItem>,
        dockItems: List<DockItem>
    ): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("exportedAtMillis", System.currentTimeMillis())
        root.put("settings", serializeSettingsJson(settings))
        root.put("layout", serializeLayoutJson(pageCount, homeItems, dockItems))
        return root.toString(2)
    }

    fun importBackupJson(rawJson: String): Pair<LauncherSettings, PersistedLayout>? {
        return try {
            val root = JSONObject(rawJson)
            val settingsJson = root.getJSONObject("settings")
            val layoutJson = root.getJSONObject("layout")
            val settings = parseSettingsJson(settingsJson)
            val layout = parseLayoutJson(layoutJson, includeWidgets = false)
            settings to layout
        } catch (_: Exception) {
            null
        }
    }

    fun countWidgetsInBackupJson(rawJson: String): Int {
        return try {
            val root = JSONObject(rawJson)
            val arr = root.getJSONObject("layout").optJSONArray("homeItems") ?: return 0
            var count = 0
            for (i in 0 until arr.length()) {
                if (arr.getJSONObject(i).optString("type") == "widget") count++
            }
            count
        } catch (_: Exception) {
            0
        }
    }

    fun resetAll() {
        prefs.edit().clear().apply()
        if (layoutFile.exists()) layoutFile.delete()
        if (customWallpaperFile.exists()) customWallpaperFile.delete()
    }

    /**
     * Samples the selected image to the device's actual display bounds using RGB_565
     * so wallpaper memory matches the device dynamically without heap spikes.
     */
    fun saveCustomWallpaperFromUriBlocking(uri: Uri): Boolean {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val dm = context.resources.displayMetrics
            val reqW = dm.widthPixels.coerceIn(720, 1440)
            val reqH = dm.heightPixels.coerceIn(1280, 2560)
            var inSampleSize = 1
            val (w, h) = boundsOptions.outWidth to boundsOptions.outHeight
            if (h > reqH || w > reqW) {
                val halfH = h / 2
                val halfW = w / 2
                while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                    inSampleSize *= 2
                }
            }
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                this.inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return false

            FileOutputStream(customWallpaperFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun loadCustomWallpaperBitmapBlocking(): Bitmap? {
        if (!customWallpaperFile.exists()) return null
        return try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(customWallpaperFile.absolutePath, options)
        } catch (_: Exception) {
            null
        }
    }

    private fun serializeLayoutJson(
        pageCount: Int,
        homeItems: List<HomeItem>,
        dockItems: List<DockItem>
    ): JSONObject {
        val root = JSONObject()
        root.put("pageCount", pageCount.coerceAtLeast(1))

        val homeArray = JSONArray()
        homeItems.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("page", item.page)
            obj.put("row", item.row)
            obj.put("col", item.col)
            when (item) {
                is HomeItem.AppItem -> {
                    obj.put("type", "app")
                    obj.put("packageName", item.packageName)
                    obj.put("activityName", item.activityName)
                    obj.put("label", item.label)
                }
                is HomeItem.ShortcutItem -> {
                    obj.put("type", "shortcut")
                    obj.put("packageName", item.packageName)
                    obj.put("shortcutId", item.shortcutId)
                    obj.put("label", item.label)
                }
                is HomeItem.FolderItem -> {
                    obj.put("type", "folder")
                    obj.put("title", item.title)
                    obj.put("apps", serializeAppRefs(item.apps))
                }
                is HomeItem.WidgetItem -> {
                    obj.put("type", "widget")
                    obj.put("appWidgetId", item.appWidgetId)
                    obj.put("providerPackage", item.providerPackage)
                    obj.put("providerClass", item.providerClass)
                    obj.put("label", item.label)
                    obj.put("colSpan", item.colSpan)
                    obj.put("rowSpan", item.rowSpan)
                }
            }
            homeArray.put(obj)
        }
        root.put("homeItems", homeArray)

        val dockArray = JSONArray()
        dockItems.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            when (item) {
                is DockItem.DockApp -> {
                    obj.put("type", "app")
                    obj.put("packageName", item.packageName)
                    obj.put("activityName", item.activityName)
                    obj.put("label", item.label)
                }
                is DockItem.DockFolder -> {
                    obj.put("type", "folder")
                    obj.put("title", item.title)
                    obj.put("apps", serializeAppRefs(item.apps))
                }
            }
            dockArray.put(obj)
        }
        root.put("dockItems", dockArray)
        return root
    }

    private fun parseLayoutJson(root: JSONObject, includeWidgets: Boolean): PersistedLayout {
        val pageCount = root.optInt("pageCount", 1).coerceAtLeast(1)
        val homeItems = mutableListOf<HomeItem>()
        val homeArray = root.optJSONArray("homeItems") ?: JSONArray()
        for (i in 0 until homeArray.length()) {
            val obj = homeArray.getJSONObject(i)
            val id = obj.optString("id")
            val page = obj.optInt("page", 0)
            val row = obj.optInt("row", 0)
            val col = obj.optInt("col", 0)
            when (obj.optString("type")) {
                "app" -> homeItems.add(
                    HomeItem.AppItem(
                        id = id,
                        packageName = obj.getString("packageName"),
                        activityName = obj.getString("activityName"),
                        label = obj.optString("label"),
                        page = page,
                        row = row,
                        col = col
                    )
                )
                "shortcut" -> homeItems.add(
                    HomeItem.ShortcutItem(
                        id = id,
                        packageName = obj.getString("packageName"),
                        shortcutId = obj.getString("shortcutId"),
                        label = obj.optString("label"),
                        page = page,
                        row = row,
                        col = col
                    )
                )
                "folder" -> homeItems.add(
                    HomeItem.FolderItem(
                        id = id,
                        title = obj.optString("title", "Folder"),
                        apps = parseAppRefs(obj.optJSONArray("apps")),
                        page = page,
                        row = row,
                        col = col
                    )
                )
                "widget" -> if (includeWidgets) {
                    homeItems.add(
                        HomeItem.WidgetItem(
                            id = id,
                            appWidgetId = obj.getInt("appWidgetId"),
                            providerPackage = obj.optString("providerPackage"),
                            providerClass = obj.optString("providerClass"),
                            label = obj.optString("label", "Widget"),
                            page = page,
                            row = row,
                            col = col,
                            colSpan = obj.optInt("colSpan", 4),
                            rowSpan = obj.optInt("rowSpan", 2)
                        )
                    )
                }
            }
        }

        val dockItems = mutableListOf<DockItem>()
        val dockArray = root.optJSONArray("dockItems") ?: JSONArray()
        for (i in 0 until dockArray.length()) {
            val obj = dockArray.getJSONObject(i)
            val id = obj.optString("id")
            when (obj.optString("type")) {
                "app" -> dockItems.add(
                    DockItem.DockApp(
                        id = id,
                        packageName = obj.getString("packageName"),
                        activityName = obj.getString("activityName"),
                        label = obj.optString("label")
                    )
                )
                "folder" -> dockItems.add(
                    DockItem.DockFolder(
                        id = id,
                        title = obj.optString("title", "Folder"),
                        apps = parseAppRefs(obj.optJSONArray("apps"))
                    )
                )
            }
        }
        return PersistedLayout(pageCount, homeItems, dockItems)
    }

    private fun serializeAppRefs(apps: List<AppRef>): JSONArray {
        val arr = JSONArray()
        apps.forEach { ref ->
            arr.put(
                JSONObject().apply {
                    put("packageName", ref.packageName)
                    put("activityName", ref.activityName)
                    put("label", ref.label)
                }
            )
        }
        return arr
    }

    private fun parseAppRefs(arr: JSONArray?): List<AppRef> {
        if (arr == null) return emptyList()
        val list = mutableListOf<AppRef>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                AppRef(
                    packageName = obj.getString("packageName"),
                    activityName = obj.getString("activityName"),
                    label = obj.optString("label")
                )
            )
        }
        return list
    }

    private fun serializeSettingsJson(s: LauncherSettings): JSONObject {
        return JSONObject().apply {
            put("themeMode", s.themeMode.name)
            put("performanceMode", s.performanceMode.name)
            put("iconSize", s.iconSize.name)
            put("textSize", s.textSize.name)
            put("iconShape", s.iconShape.name)
            put("homeColumns", s.homeColumns)
            put("homeRows", s.homeRows)
            put("showSearchBar", s.showSearchBar)
            put("showDock", s.showDock)
            put("dockTransparency", s.dockTransparency.toDouble())
            put("lockHomeLayout", s.lockHomeLayout)
            put("showClockSeconds", s.showClockSeconds)
            put("use24HourClock", s.use24HourClock?.toString() ?: "system")
            put("showBatteryInfo", s.showBatteryInfo)
            put("drawerViewMode", s.drawerViewMode.name)
            put("drawerColumns", s.drawerColumns)
            put("drawerSortOrder", s.drawerSortOrder.name)
            put("showRecentApps", s.showRecentApps)
            put("swipeUpAction", s.swipeUpAction.name)
            put("swipeDownAction", s.swipeDownAction.name)
            put("doubleTapLockEnabled", s.doubleTapLockEnabled)
            put(
                "wallpaperMode",
                if (s.wallpaperMode == WallpaperMode.CUSTOM_FILE) WallpaperMode.SYSTEM.name else s.wallpaperMode.name
            )
            put("wallpaperDim", s.wallpaperDim.toDouble())
            put("hiddenComponentKeys", JSONArray(s.hiddenComponentKeys.toList()))
            put("notificationDotsEnabled", s.notificationDotsEnabled)
        }
    }

    private fun parseSettingsJson(obj: JSONObject): LauncherSettings {
        val hiddenArr = obj.optJSONArray("hiddenComponentKeys")
        val hiddenSet = mutableSetOf<String>()
        if (hiddenArr != null) {
            for (i in 0 until hiddenArr.length()) {
                hiddenSet.add(hiddenArr.getString(i))
            }
        }
        val clockSetting = when (obj.optString("use24HourClock", "system")) {
            "true" -> true
            "false" -> false
            else -> null
        }
        return LauncherSettings(
            themeMode = enumOrDefault(obj.optString("themeMode"), ThemeMode.SYSTEM),
            performanceMode = enumOrDefault(obj.optString("performanceMode"), PerformanceMode.AUTO_ADAPTIVE),
            iconSize = enumOrDefault(obj.optString("iconSize"), IconSizePreset.MEDIUM),
            textSize = enumOrDefault(obj.optString("textSize"), TextSizePreset.MEDIUM),
            iconShape = enumOrDefault(obj.optString("iconShape"), IconShapePreset.SQUIRCLE),
            homeColumns = obj.optInt("homeColumns", 4).coerceIn(4, 5),
            homeRows = obj.optInt("homeRows", 5).coerceIn(4, 6),
            showSearchBar = obj.optBoolean("showSearchBar", true),
            showDock = obj.optBoolean("showDock", true),
            dockTransparency = obj.optDouble("dockTransparency", 0.28).toFloat().coerceIn(0f, 1f),
            lockHomeLayout = obj.optBoolean("lockHomeLayout", false),
            showClockSeconds = obj.optBoolean("showClockSeconds", false),
            use24HourClock = clockSetting,
            showBatteryInfo = obj.optBoolean("showBatteryInfo", true),
            drawerViewMode = enumOrDefault(obj.optString("drawerViewMode"), DrawerViewMode.GRID),
            drawerColumns = obj.optInt("drawerColumns", 4).coerceIn(3, 6),
            drawerSortOrder = enumOrDefault(obj.optString("drawerSortOrder"), SortOrder.AZ),
            showRecentApps = obj.optBoolean("showRecentApps", true),
            swipeUpAction = enumOrDefault(obj.optString("swipeUpAction"), SwipeUpAction.OPEN_DRAWER),
            swipeDownAction = enumOrDefault(obj.optString("swipeDownAction"), SwipeDownAction.SMART_SEARCH),
            doubleTapLockEnabled = obj.optBoolean("doubleTapLockEnabled", true),
            wallpaperMode = enumOrDefault(obj.optString("wallpaperMode"), WallpaperMode.BUILTIN_OBSIDIAN),
            wallpaperDim = obj.optDouble("wallpaperDim", 0.12).toFloat().coerceIn(0f, 0.7f),
            hiddenComponentKeys = hiddenSet,
            notificationDotsEnabled = obj.optBoolean("notificationDotsEnabled", true),
            defaultBannerDismissed = false
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, fallback: T): T {
        if (name.isNullOrBlank()) return fallback
        return try {
            enumValueOf<T>(name)
        } catch (_: IllegalArgumentException) {
            fallback
        }
    }

    companion object {
        private const val PREFS_NAME = "nexus_launcher_prefs"
        private const val LAYOUT_FILE_NAME = "nexus_layout.json"
        private const val CUSTOM_WALLPAPER_FILE_NAME = "custom_wallpaper.jpg"

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PERFORMANCE_MODE = "performance_mode"
        private const val KEY_ICON_SIZE = "icon_size"
        private const val KEY_TEXT_SIZE = "text_size"
        private const val KEY_ICON_SHAPE = "icon_shape"
        private const val KEY_HOME_COLS = "home_cols"
        private const val KEY_HOME_ROWS = "home_rows"
        private const val KEY_SHOW_SEARCH_BAR = "show_search_bar"
        private const val KEY_SHOW_DOCK = "show_dock"
        private const val KEY_DOCK_TRANSPARENCY = "dock_transparency"
        private const val KEY_LOCK_HOME_LAYOUT = "lock_home_layout"
        private const val KEY_SHOW_CLOCK_SECONDS = "show_clock_seconds"
        private const val KEY_USE_24H_CLOCK = "use_24h_clock"
        private const val KEY_SHOW_BATTERY_INFO = "show_battery_info"
        private const val KEY_DRAWER_VIEW_MODE = "drawer_view_mode"
        private const val KEY_DRAWER_COLS = "drawer_cols"
        private const val KEY_DRAWER_SORT = "drawer_sort"
        private const val KEY_SHOW_RECENT_APPS = "show_recent_apps"
        private const val KEY_SWIPE_UP = "swipe_up"
        private const val KEY_SWIPE_DOWN = "swipe_down"
        private const val KEY_DOUBLE_TAP_LOCK = "double_tap_lock"
        private const val KEY_WALLPAPER_MODE = "wallpaper_mode"
        private const val KEY_WALLPAPER_DIM = "wallpaper_dim"
        private const val KEY_HIDDEN_APPS = "hidden_apps"
        private const val KEY_NOTIFICATION_DOTS = "notification_dots"
        private const val KEY_DEFAULT_BANNER_DISMISSED = "default_banner_dismissed"
        private const val KEY_RECENT_APPS = "recent_apps"
    }
}
