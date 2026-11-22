package com.example

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.example.core.DeviceProfile
import com.example.core.PerformanceMonitor
import com.example.core.ThemePalette
import com.example.model.AppInfo
import com.example.model.DockItem
import com.example.model.HomeItem
import com.example.ui.view.AppDrawerView
import com.example.ui.view.DockBarView
import com.example.ui.view.NativeDialogs
import com.example.ui.view.NexusClockBatteryView
import com.example.ui.view.NexusWallpaperView
import com.example.ui.view.PageIndicatorView
import com.example.ui.view.PagedHomeWorkspaceView
import com.example.ui.view.SmartSearchView
import com.example.viewmodel.LauncherController
import com.example.widget.InstalledWidgetProvider
import com.example.widget.NexusAppWidgetHost

/**
 * Pure native android.app.Activity Home Launcher (zero Jetpack Compose / AppCompat overhead).
 */
class MainActivity : Activity(), LauncherController.StateListener {

    private lateinit var controller: LauncherController
    private lateinit var widgetHost: NexusAppWidgetHost
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var rootFrame: FrameLayout
    private lateinit var wallpaperView: NexusWallpaperView
    private lateinit var safeAreaRoot: LinearLayout
    private lateinit var homeContentColumn: LinearLayout
    private lateinit var landscapeLeftPane: LinearLayout
    private lateinit var mainRow: LinearLayout

    private lateinit var clockBatteryView: NexusClockBatteryView
    private lateinit var defaultBanner: LinearLayout
    private lateinit var statusBanner: LinearLayout
    private lateinit var statusBannerText: TextView
    private lateinit var workspaceView: PagedHomeWorkspaceView
    private lateinit var pageIndicatorView: PageIndicatorView
    private lateinit var searchPill: LinearLayout
    private lateinit var dockBarView: DockBarView
    private lateinit var editToolbar: LinearLayout

    private lateinit var appDrawerView: AppDrawerView
    private lateinit var smartSearchView: SmartSearchView

    private var isDrawerOpen = false
    private var isSearchOpen = false
    private var currentTimeMillis = System.currentTimeMillis()
    private var lastIsLandscape = false

    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingWidgetProvider: InstalledWidgetProvider? = null
    private var pendingWidgetPage = 0

    private var backCallback: OnBackInvokedCallback? = null

    private val secondsTicker = object : Runnable {
        override fun run() {
            if (controller.settings.showClockSeconds) {
                currentTimeMillis = System.currentTimeMillis()
                updateClockOnly()
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    private val hideBannerRunnable = Runnable {
        controller.clearBanner()
    }

    private val systemEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_TIME_TICK,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_DATE_CHANGED -> {
                    currentTimeMillis = System.currentTimeMillis()
                    updateClockOnly()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 100
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                    controller.updateBatteryStatus(pct, charging)
                }
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    controller.refreshAdaptiveCapabilities()
                }
            }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val pkg = intent?.data?.schemeSpecificPart
            when (intent?.action) {
                Intent.ACTION_PACKAGE_REMOVED -> {
                    val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    if (!replacing && pkg != null) {
                        controller.onPackageRemoved(pkg)
                    }
                }
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_CHANGED,
                Intent.ACTION_PACKAGE_REPLACED -> {
                    controller.refreshAppsAndStatus()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()

        val app = application as NexusLauncherApp
        controller = app.controller
        widgetHost = NexusAppWidgetHost(this)

        buildUi()
        setContentView(rootFrame)

        rootFrame.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                rootFrame.viewTreeObserver.removeOnPreDrawListener(this)
                PerformanceMonitor.markFirstFrameDrawn()
                return true
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val cb = OnBackInvokedCallback {
                handleBackAction()
            }
            backCallback = cb
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                cb
            )
        }

        renderAll()
    }

    @Suppress("DEPRECATION")
    private fun setupEdgeToEdge() {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }

    private fun buildUi() {
        rootFrame = FrameLayout(this)

        wallpaperView = NexusWallpaperView(this)
        rootFrame.addView(
            wallpaperView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        safeAreaRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "home_screen"
        }
        rootFrame.addView(
            safeAreaRoot,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        clockBatteryView = NexusClockBatteryView(this)
        defaultBanner = buildDefaultBanner()
        statusBanner = buildStatusBanner()

        workspaceView = PagedHomeWorkspaceView(this).apply {
            onPageChanged = { page -> pageIndicatorView.update(controller.pageCount, page) }
            onSwipeUp = { controller.handleSwipeUp({ setDrawerOpen(true) }, { setSearchOpen(true) }) }
            onSwipeDown = { controller.handleSwipeDown { setSearchOpen(true) } }
            onDoubleTapEmpty = { controller.handleDoubleTapEmptySpace() }
            onLongPressEmpty = { controller.setEditMode(!controller.isEditMode) }
            onLaunchApp = { pkg, cls -> controller.launchApp(pkg, cls) }
            onLaunchShortcut = { pkg, id -> controller.launchShortcut(pkg, id) }
            onOpenFolder = { folderId -> openFolderDialog(folderId) }
            onLongClickHomeApp = { appItem ->
                openAppActionDialog(
                    AppInfo(appItem.packageName, appItem.activityName, appItem.label),
                    homeItemId = appItem.id,
                    dockItemId = null
                )
            }
            onRemoveHomeItem = { id ->
                val removingWidget = controller.homeItems.firstOrNull { it.id == id } as? HomeItem.WidgetItem
                controller.removeHomeItem(id)
                if (removingWidget != null) {
                    widgetHost.deleteWidgetId(removingWidget.appWidgetId)
                }
            }
            onResizeWidget = { id, dc, dr -> controller.resizeWidget(id, dc, dr) }
            onDropHomeItem = { id, p, r, c -> controller.dropHomeItem(id, p, r, c) }
        }

        pageIndicatorView = PageIndicatorView(this)
        searchPill = buildSearchPill()
        dockBarView = DockBarView(this).apply {
            onLaunchApp = { pkg, cls -> controller.launchApp(pkg, cls) }
            onOpenFolder = { id -> openFolderDialog(id) }
            onLongClickDockApp = { dockApp ->
                openAppActionDialog(
                    AppInfo(dockApp.packageName, dockApp.activityName, dockApp.label),
                    homeItemId = null,
                    dockItemId = dockApp.id
                )
            }
            onOpenDrawer = { setDrawerOpen(true) }
        }
        editToolbar = buildEditToolbar()

        // Responsive containers for Portrait & Landscape
        mainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        landscapeLeftPane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ThemePalette.dp(this@MainActivity, 210f),
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        homeContentColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
        }

        arrangeResponsiveContainers(isLandscapeNow())

        // Overlays: AppDrawerView & SmartSearchView
        appDrawerView = AppDrawerView(this).apply {
            visibility = View.GONE
            onClose = { setDrawerOpen(false) }
            onToggleSort = { controller.toggleDrawerSortOrder() }
            onToggleViewMode = { controller.toggleDrawerViewMode() }
            onOpenSettings = { openSettings(null) }
            onAppClick = { appInfo -> controller.launchApp(appInfo.packageName, appInfo.activityName) }
            onAppLongClick = { appInfo -> openAppActionDialog(appInfo, null, null) }
        }
        smartSearchView = SmartSearchView(this).apply {
            visibility = View.GONE
            onClose = { setSearchOpen(false) }
            onQueryChanged = { q -> controller.searchLocal(q) }
            onAppClick = { appInfo -> controller.launchApp(appInfo.packageName, appInfo.activityName) }
            onShortcutClick = { sc -> controller.launchShortcut(sc.packageName, sc.id) }
            onQuickActionClick = { qa -> controller.systemHelper.openSystemAction(qa.actionIntent) }
        }

        rootFrame.addView(
            appDrawerView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        rootFrame.addView(
            smartSearchView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Safe System Window Insets for Camera Notch & Navigation Bar
        rootFrame.setOnApplyWindowInsetsListener { _, insets ->
            val topInset: Int
            val bottomInset: Int
            val leftInset: Int
            val rightInset: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sys = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                topInset = sys.top
                bottomInset = sys.bottom
                leftInset = sys.left
                rightInset = sys.right
            } else {
                @Suppress("DEPRECATION")
                topInset = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottomInset = insets.systemWindowInsetBottom
                @Suppress("DEPRECATION")
                leftInset = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION")
                rightInset = insets.systemWindowInsetRight
            }
            safeAreaRoot.setPadding(leftInset, topInset, rightInset, bottomInset)
            appDrawerView.setPadding(
                leftInset + ThemePalette.dp(this, 16f),
                topInset + ThemePalette.dp(this, 8f),
                rightInset + ThemePalette.dp(this, 16f),
                bottomInset + ThemePalette.dp(this, 8f)
            )
            smartSearchView.setPadding(
                leftInset + ThemePalette.dp(this, 16f),
                topInset + ThemePalette.dp(this, 10f),
                rightInset + ThemePalette.dp(this, 16f),
                bottomInset + ThemePalette.dp(this, 10f)
            )
            insets
        }
    }

    private fun isLandscapeNow(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun arrangeResponsiveContainers(landscape: Boolean) {
        lastIsLandscape = landscape
        safeAreaRoot.removeAllViews()
        mainRow.removeAllViews()
        landscapeLeftPane.removeAllViews()
        homeContentColumn.removeAllViews()

        detatchFromParent(clockBatteryView)
        detatchFromParent(defaultBanner)
        detatchFromParent(statusBanner)
        detatchFromParent(workspaceView)
        detatchFromParent(pageIndicatorView)
        detatchFromParent(searchPill)
        detatchFromParent(dockBarView)
        detatchFromParent(editToolbar)

        clockBatteryView.setCompactMode(landscape)

        if (landscape) {
            landscapeLeftPane.addView(clockBatteryView)
            landscapeLeftPane.addView(searchPill)
            landscapeLeftPane.addView(makeLandscapeActionButtons())

            homeContentColumn.addView(defaultBanner)
            homeContentColumn.addView(statusBanner)
            homeContentColumn.addView(
                workspaceView,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            )
            homeContentColumn.addView(pageIndicatorView)

            mainRow.addView(landscapeLeftPane)
            mainRow.addView(homeContentColumn)
            safeAreaRoot.addView(mainRow)
            safeAreaRoot.addView(editToolbar)
            safeAreaRoot.addView(
                dockBarView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    val m = ThemePalette.dp(this@MainActivity, 12f)
                    setMargins(m, ThemePalette.dp(this@MainActivity, 4f), m, ThemePalette.dp(this@MainActivity, 6f))
                }
            )
        } else {
            safeAreaRoot.addView(clockBatteryView)
            safeAreaRoot.addView(defaultBanner)
            safeAreaRoot.addView(statusBanner)
            safeAreaRoot.addView(
                workspaceView,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            )
            safeAreaRoot.addView(pageIndicatorView)
            safeAreaRoot.addView(editToolbar)
            safeAreaRoot.addView(searchPill)
            safeAreaRoot.addView(
                dockBarView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    val m = ThemePalette.dp(this@MainActivity, 14f)
                    setMargins(m, ThemePalette.dp(this@MainActivity, 4f), m, ThemePalette.dp(this@MainActivity, 8f))
                }
            )
        }
    }

    private fun detatchFromParent(v: View) {
        (v.parent as? ViewGroup)?.removeView(v)
    }

    private fun makeLandscapeActionButtons(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val pad = ThemePalette.dp(this@MainActivity, 12f)
            setPadding(pad, ThemePalette.dp(this@MainActivity, 6f), pad, pad)
            addView(makeChipBtn(R.drawable.ic_apps_grid, "App Drawer") { setDrawerOpen(true) })
            addView(View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(ThemePalette.dp(this@MainActivity, 8f), 1)
            })
            addView(makeChipBtn(R.drawable.ic_settings, "Pengaturan") { openSettings(null) })
        }
    }

    private fun makeChipBtn(iconRes: Int, desc: String, onClick: () -> Unit): ImageView {
        val h = ThemePalette.dp(this, 44f)
        val p = ThemePalette.dp(this, 10f)
        return ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            contentDescription = desc
            setPadding(p, p, p, p)
            background = ThemePalette.rippleRounded(
                0x30FFFFFF,
                0x55FFFFFF,
                ThemePalette.dpF(this@MainActivity, 14f)
            )
            layoutParams = LinearLayout.LayoutParams(0, h, 1f)
            setOnClickListener { onClick() }
        }
    }

    private fun buildDefaultBanner(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            tag = "default_launcher_banner"
            val pH = ThemePalette.dp(this@MainActivity, 14f)
            val pV = ThemePalette.dp(this@MainActivity, 10f)
            setPadding(pH, pV, pH, pV)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val m = ThemePalette.dp(this@MainActivity, 16f)
                setMargins(m, ThemePalette.dp(this@MainActivity, 4f), m, ThemePalette.dp(this@MainActivity, 4f))
            }
            setOnClickListener { requestDefaultHomeLauncher() }
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_home)
            layoutParams = LinearLayout.LayoutParams(ThemePalette.dp(this@MainActivity, 24f), ThemePalette.dp(this@MainActivity, 24f))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = ThemePalette.dp(this@MainActivity, 10f)
            }
            addView(TextView(this@MainActivity).apply {
                text = "Jadikan Nexus Launcher Utama"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Ketuk untuk mengatur agar tombol Home membuka Nexus"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
        }
        val close = ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            contentDescription = "Tutup saran"
            tag = "dismiss_default_banner_button"
            val p = ThemePalette.dp(this@MainActivity, 8f)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(ThemePalette.dp(this@MainActivity, 36f), ThemePalette.dp(this@MainActivity, 36f))
            setOnClickListener { controller.dismissDefaultLauncherBanner() }
        }
        row.addView(icon)
        row.addView(col)
        row.addView(close)
        return row
    }

    private fun buildStatusBanner(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            val pH = ThemePalette.dp(this@MainActivity, 14f)
            val pV = ThemePalette.dp(this@MainActivity, 8f)
            setPadding(pH, pV, pH, pV)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val m = ThemePalette.dp(this@MainActivity, 16f)
                setMargins(m, ThemePalette.dp(this@MainActivity, 4f), m, ThemePalette.dp(this@MainActivity, 4f))
            }
        }
        statusBannerText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val close = ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            contentDescription = "Tutup pesan"
            val p = ThemePalette.dp(this@MainActivity, 6f)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(ThemePalette.dp(this@MainActivity, 32f), ThemePalette.dp(this@MainActivity, 32f))
            setOnClickListener { controller.clearBanner() }
        }
        row.addView(statusBannerText)
        row.addView(close)
        return row
    }

    private fun buildSearchPill(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            tag = "home_search_bar"
            val pH = ThemePalette.dp(this@MainActivity, 16f)
            val pV = ThemePalette.dp(this@MainActivity, 12f)
            setPadding(pH, pV, pH, pV)
            background = ThemePalette.rippleRounded(
                0x29FFFFFF,
                0x44FFFFFF,
                ThemePalette.dpF(this@MainActivity, 26f),
                0x38FFFFFF,
                ThemePalette.dp(this@MainActivity, 1f)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val mH = ThemePalette.dp(this@MainActivity, 18f)
                val mV = ThemePalette.dp(this@MainActivity, 6f)
                setMargins(mH, mV, mH, mV)
            }
            setOnClickListener { setSearchOpen(true) }

            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_search)
                setColorFilter(0xE6FFFFFF.toInt(), PorterDuff.Mode.SRC_IN)
                contentDescription = "Cari aplikasi"
                layoutParams = LinearLayout.LayoutParams(
                    ThemePalette.dp(this@MainActivity, 20f),
                    ThemePalette.dp(this@MainActivity, 20f)
                )
            })
            addView(TextView(this@MainActivity).apply {
                text = "Cari aplikasi & pintasan…"
                setTextColor(0xD9FFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = ThemePalette.dp(this@MainActivity, 10f)
                }
            })
        }
    }

    private fun buildEditToolbar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            tag = "edit_mode_toolbar"
            val p = ThemePalette.dp(this@MainActivity, 8f)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val m = ThemePalette.dp(this@MainActivity, 12f)
                setMargins(m, ThemePalette.dp(this@MainActivity, 6f), m, ThemePalette.dp(this@MainActivity, 8f))
            }
        }
        bar.addView(makeEditAction(R.drawable.ic_widgets, "Widget") {
            openWidgetPicker(workspaceView.currentPage)
        })
        bar.addView(makeEditAction(R.drawable.ic_wallpaper, "Wallpaper") {
            openSettings(SettingsActivity.SECTION_APPEARANCE)
        })
        bar.addView(makeEditAction(R.drawable.ic_add, "Halaman") {
            val newPage = controller.addHomePage()
            workspaceView.scrollToPage(newPage, animate = true)
        })
        bar.addView(makeEditAction(R.drawable.ic_delete, "Hapus Hal.") {
            controller.removeEmptyPage(workspaceView.currentPage)
        })
        bar.addView(makeEditAction(R.drawable.ic_settings, "Setelan") {
            openSettings(null)
        })
        bar.addView(makeEditAction(R.drawable.ic_check, "Selesai") {
            controller.setEditMode(false)
        })
        return bar
    }

    private fun makeEditAction(iconRes: Int, label: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, ThemePalette.dp(this@MainActivity, 4f), 0, ThemePalette.dp(this@MainActivity, 4f))
            setOnClickListener { onClick() }
            addView(ImageView(this@MainActivity).apply {
                setImageResource(iconRes)
                contentDescription = label
                layoutParams = LinearLayout.LayoutParams(
                    ThemePalette.dp(this@MainActivity, 22f),
                    ThemePalette.dp(this@MainActivity, 22f)
                )
            })
            addView(TextView(this@MainActivity).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(0, ThemePalette.dp(this@MainActivity, 2f), 0, 0)
            })
        }
    }

    override fun onLauncherStateChanged() {
        renderAll()
    }

    private fun updateClockOnly() {
        val s = controller.settings
        clockBatteryView.update(
            timeMillis = currentTimeMillis,
            showSeconds = s.showClockSeconds,
            use24HourOverride = s.use24HourClock,
            showBattery = s.showBatteryInfo,
            batteryStatus = controller.batteryStatus
        )
    }

    private fun renderAll() {
        val s = controller.settings
        val caps = controller.capabilities
        val colors = ThemePalette.resolve(this, s.themeMode)
        val animMs = DeviceProfile.resolveEffectiveAnimationMs(caps, s.performanceMode)

        wallpaperView.configure(s.wallpaperMode, s.wallpaperDim, controller.customWallpaperBitmap)
        updateClockOnly()

        // Default banner
        val showDefault = !controller.deviceStatus.isDefaultLauncher &&
            !s.defaultBannerDismissed && !controller.isEditMode
        defaultBanner.visibility = if (showDefault) View.VISIBLE else View.GONE
        if (showDefault) {
            defaultBanner.background = ThemePalette.roundedCard(
                colors.surface,
                ThemePalette.dpF(this, 18f),
                colors.divider,
                ThemePalette.dp(this, 1f)
            )
            (defaultBanner.getChildAt(0) as ImageView).setColorFilter(colors.accent, PorterDuff.Mode.SRC_IN)
            val col = defaultBanner.getChildAt(1) as LinearLayout
            (col.getChildAt(0) as TextView).setTextColor(colors.textPrimary)
            (col.getChildAt(1) as TextView).setTextColor(colors.textSecondary)
            (defaultBanner.getChildAt(2) as ImageView).setColorFilter(colors.textSecondary, PorterDuff.Mode.SRC_IN)
        }

        // Transient banner
        val msg = controller.bannerMessage
        if (msg != null) {
            statusBanner.visibility = View.VISIBLE
            statusBanner.background = ThemePalette.roundedCard(colors.surface, ThemePalette.dpF(this, 16f))
            statusBannerText.text = msg
            statusBannerText.setTextColor(colors.textPrimary)
            (statusBanner.getChildAt(1) as ImageView).setColorFilter(colors.textSecondary, PorterDuff.Mode.SRC_IN)
            mainHandler.removeCallbacks(hideBannerRunnable)
            mainHandler.postDelayed(hideBannerRunnable, 3500L)
        } else {
            statusBanner.visibility = View.GONE
        }

        workspaceView.render(
            pageCount = controller.pageCount,
            homeItems = controller.homeItems,
            settings = s,
            iconCache = controller.iconCache,
            widgetHost = widgetHost,
            badgeCounts = controller.badgeCounts,
            isEditMode = controller.isEditMode,
            accentColor = colors.accent,
            animDurationMs = animMs
        )

        pageIndicatorView.update(controller.pageCount, workspaceView.currentPage)

        if (controller.isEditMode) {
            editToolbar.visibility = View.VISIBLE
            editToolbar.background = ThemePalette.roundedCard(
                colors.surface,
                ThemePalette.dpF(this, 22f),
                colors.divider,
                ThemePalette.dp(this, 1f)
            )
            for (i in 0 until editToolbar.childCount) {
                val item = editToolbar.getChildAt(i) as LinearLayout
                (item.getChildAt(0) as ImageView).setColorFilter(colors.accent, PorterDuff.Mode.SRC_IN)
                (item.getChildAt(1) as TextView).setTextColor(colors.textPrimary)
            }
            val canDeletePage = controller.pageCount > 1 &&
                controller.homeItems.none { it.page == workspaceView.currentPage }
            editToolbar.getChildAt(3).visibility = if (canDeletePage) View.VISIBLE else View.GONE
            searchPill.visibility = View.GONE
            dockBarView.visibility = View.GONE
        } else {
            editToolbar.visibility = View.GONE
            searchPill.visibility = if (s.showSearchBar) View.VISIBLE else View.GONE
            dockBarView.render(
                items = controller.dockItems,
                settings = s,
                iconCache = controller.iconCache,
                badgeCounts = controller.badgeCounts,
                accentColor = colors.accent
            )
        }

        if (isDrawerOpen) {
            appDrawerView.render(
                apps = controller.visibleDrawerApps(),
                recentApps = controller.recentApps(),
                settings = s,
                iconCache = controller.iconCache,
                badgeCounts = controller.badgeCounts,
                colors = colors
            )
        }
        if (isSearchOpen) {
            smartSearchView.render(
                recentApps = controller.recentApps(),
                iconCache = controller.iconCache,
                appRepository = controller.appRepository,
                shape = s.iconShape,
                colors = colors
            )
        }
    }

    private fun setDrawerOpen(open: Boolean) {
        if (isDrawerOpen == open) return
        isDrawerOpen = open
        if (open) {
            setSearchOpen(false)
            controller.setEditMode(false)
            val s = controller.settings
            val colors = ThemePalette.resolve(this, s.themeMode)
            appDrawerView.render(
                apps = controller.visibleDrawerApps(),
                recentApps = controller.recentApps(),
                settings = s,
                iconCache = controller.iconCache,
                badgeCounts = controller.badgeCounts,
                colors = colors
            )
            val animMs = DeviceProfile.resolveEffectiveAnimationMs(controller.capabilities, s.performanceMode)
            if (animMs > 0L) {
                appDrawerView.alpha = 0f
                appDrawerView.translationY = ThemePalette.dpF(this, 40f)
                appDrawerView.visibility = View.VISIBLE
                appDrawerView.animate().alpha(1f).translationY(0f).setDuration(animMs).start()
            } else {
                appDrawerView.alpha = 1f
                appDrawerView.translationY = 0f
                appDrawerView.visibility = View.VISIBLE
            }
        } else {
            appDrawerView.clearSearch()
            appDrawerView.visibility = View.GONE
        }
    }

    private fun setSearchOpen(open: Boolean) {
        if (isSearchOpen == open) return
        isSearchOpen = open
        if (open) {
            setDrawerOpen(false)
            val s = controller.settings
            val colors = ThemePalette.resolve(this, s.themeMode)
            smartSearchView.render(
                recentApps = controller.recentApps(),
                iconCache = controller.iconCache,
                appRepository = controller.appRepository,
                shape = s.iconShape,
                colors = colors
            )
            val animMs = DeviceProfile.resolveEffectiveAnimationMs(controller.capabilities, s.performanceMode)
            if (animMs > 0L) {
                smartSearchView.alpha = 0f
                smartSearchView.visibility = View.VISIBLE
                smartSearchView.animate().alpha(1f).setDuration(animMs).start()
            } else {
                smartSearchView.alpha = 1f
                smartSearchView.visibility = View.VISIBLE
            }
        } else {
            smartSearchView.clearSearch()
            smartSearchView.visibility = View.GONE
        }
    }

    private fun openFolderDialog(folderId: String) {
        val homeFolder = controller.homeItems.firstOrNull { it.id == folderId } as? HomeItem.FolderItem
        val dockFolder = controller.dockItems.firstOrNull { it.id == folderId } as? DockItem.DockFolder
        val folder = homeFolder ?: dockFolder?.let {
            HomeItem.FolderItem(it.id, it.title, it.apps, 0, 0, 0)
        } ?: return

        val colors = ThemePalette.resolve(this, controller.settings.themeMode)
        NativeDialogs.showFolderDialog(
            context = this,
            folder = folder,
            settings = controller.settings,
            iconCache = controller.iconCache,
            badgeCounts = controller.badgeCounts,
            colors = colors,
            onRename = { newTitle -> controller.renameFolder(folder.id, newTitle) },
            onAppClick = { ref -> controller.launchApp(ref.packageName, ref.activityName) },
            onRemoveAppFromFolder = { ref ->
                controller.removeAppFromFolder(folder.id, ref.componentKey)
            }
        )
    }

    private fun openAppActionDialog(appInfo: AppInfo, homeItemId: String?, dockItemId: String?) {
        val colors = ThemePalette.resolve(this, controller.settings.themeMode)
        NativeDialogs.showAppActionDialog(
            context = this,
            appInfo = appInfo,
            homeItemId = homeItemId,
            dockItemId = dockItemId,
            appRepository = controller.appRepository,
            iconCache = controller.iconCache,
            settings = controller.settings,
            existingFolders = controller.homeItems.filterIsInstance<HomeItem.FolderItem>(),
            existingHomeApps = controller.homeItems.filterIsInstance<HomeItem.AppItem>(),
            colors = colors,
            onLaunchShortcut = { sc -> controller.launchShortcut(sc.packageName, sc.id) },
            onPinShortcutToHome = { sc -> controller.pinShortcutToHome(sc, workspaceView.currentPage) },
            onPinToHome = { controller.pinAppToHome(appInfo, workspaceView.currentPage) },
            onRemoveFromHome = homeItemId?.let { id -> { controller.removeHomeItem(id) } },
            onPinToDock = { controller.pinAppToDock(appInfo) },
            onRemoveFromDock = dockItemId?.let { id -> { controller.removeDockItem(id) } },
            onAddToFolder = { fId -> controller.addAppToFolderOrCreate(appInfo, targetFolderId = fId) },
            onCreateFolderWithApp = { otherId -> controller.addAppToFolderOrCreate(appInfo, targetAppItemId = otherId) },
            onToggleHidden = { controller.toggleHiddenApp(appInfo.componentKey) },
            onEnterEditMode = { controller.setEditMode(true) },
            onOpenAppInfo = { controller.appRepository.openAppInfo(appInfo.packageName) },
            onUninstall = { controller.appRepository.uninstallApp(appInfo.packageName) }
        )
    }

    private fun openWidgetPicker(targetPage: Int) {
        val providers = widgetHost.getInstalledWidgetProviders()
        val colors = ThemePalette.resolve(this, controller.settings.themeMode)
        NativeDialogs.showWidgetPickerDialog(
            context = this,
            providers = providers,
            colors = colors
        ) { provider ->
            beginAddWidget(provider, targetPage)
        }
    }

    @Suppress("DEPRECATION")
    private fun beginAddWidget(provider: InstalledWidgetProvider, targetPage: Int) {
        val appWidgetId = widgetHost.allocateWidgetId()
        pendingWidgetId = appWidgetId
        pendingWidgetProvider = provider
        pendingWidgetPage = targetPage

        if (widgetHost.bindAppWidgetIfAllowed(appWidgetId, provider.info)) {
            configureOrAttachWidget(appWidgetId, provider)
        } else {
            try {
                startActivityForResult(
                    widgetHost.createBindIntent(appWidgetId, provider.info),
                    REQ_BIND_WIDGET
                )
            } catch (_: Exception) {
                widgetHost.deleteWidgetId(appWidgetId)
                clearPendingWidget()
                controller.showBanner("Perangkat tidak mengizinkan binding widget ini")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun configureOrAttachWidget(appWidgetId: Int, provider: InstalledWidgetProvider) {
        val info: AppWidgetProviderInfo = provider.info
        if (info.configure != null) {
            try {
                val cfgIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = info.configure
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                startActivityForResult(cfgIntent, REQ_CONFIGURE_WIDGET)
                return
            } catch (_: Exception) {
            }
        }
        controller.addWidgetItem(appWidgetId, info, provider.label, pendingWidgetPage)
        clearPendingWidget()
    }

    private fun clearPendingWidget() {
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingWidgetProvider = null
        pendingWidgetPage = 0
    }

    @Suppress("DEPRECATION")
    private fun requestDefaultHomeLauncher() {
        try {
            startActivityForResult(
                controller.systemHelper.createRequestDefaultHomeIntent(),
                REQ_DEFAULT_HOME
            )
        } catch (_: Exception) {
            try {
                startActivity(controller.systemHelper.createSystemHomeSettingsIntent())
            } catch (_: Exception) {
                controller.showBanner("Buka Pengaturan > Aplikasi Default > Layar Utama")
            }
        }
    }

    private fun openSettings(section: String?) {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            if (section != null) putExtra(SettingsActivity.EXTRA_INITIAL_SECTION, section)
        }
        startActivity(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_DEFAULT_HOME -> controller.refreshAdaptiveCapabilities()
            REQ_BIND_WIDGET -> {
                val id = pendingWidgetId
                val provider = pendingWidgetProvider
                if (resultCode == RESULT_OK && id != AppWidgetManager.INVALID_APPWIDGET_ID && provider != null) {
                    configureOrAttachWidget(id, provider)
                } else {
                    if (id != AppWidgetManager.INVALID_APPWIDGET_ID) widgetHost.deleteWidgetId(id)
                    clearPendingWidget()
                }
            }
            REQ_CONFIGURE_WIDGET -> {
                val id = pendingWidgetId
                val provider = pendingWidgetProvider
                if (resultCode == RESULT_OK && id != AppWidgetManager.INVALID_APPWIDGET_ID && provider != null) {
                    controller.addWidgetItem(id, provider.info, provider.label, pendingWidgetPage)
                } else {
                    if (id != AppWidgetManager.INVALID_APPWIDGET_ID) widgetHost.deleteWidgetId(id)
                }
                clearPendingWidget()
            }
        }
    }

    private fun handleBackAction(): Boolean {
        return when {
            isDrawerOpen -> { setDrawerOpen(false); true }
            isSearchOpen -> { setSearchOpen(false); true }
            controller.isEditMode -> { controller.setEditMode(false); true }
            else -> false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!handleBackAction()) {
            // Home launcher stays on Home when Back is pressed at root
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            val hadOverlay = isDrawerOpen || isSearchOpen || controller.isEditMode
            setDrawerOpen(false)
            setSearchOpen(false)
            controller.setEditMode(false)
            if (!hadOverlay) {
                workspaceView.scrollToPage(0, animate = true)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape != lastIsLandscape) {
            arrangeResponsiveContainers(landscape)
        }
        controller.refreshAdaptiveCapabilities()
    }

    override fun onStart() {
        super.onStart()
        controller.addListener(this)
        widgetHost.startListening()
        PerformanceMonitor.attachFrameMonitor(this)
        currentTimeMillis = System.currentTimeMillis()
        registerReceivers()
        controller.reloadSettingsFromStorage()
        controller.refreshAppsAndStatus()
        if (controller.settings.showClockSeconds) {
            mainHandler.removeCallbacks(secondsTicker)
            mainHandler.postDelayed(secondsTicker, 1000L)
        }
    }

    override fun onStop() {
        super.onStop()
        controller.removeListener(this)
        widgetHost.stopListening()
        PerformanceMonitor.detachFrameMonitor(this)
        mainHandler.removeCallbacks(secondsTicker)
        unregisterReceiversSafe()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        }
    }

    private fun registerReceivers() {
        val sysFilter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(systemEventReceiver, sysFilter, RECEIVER_NOT_EXPORTED)
            registerReceiver(packageReceiver, pkgFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(systemEventReceiver, sysFilter)
            registerReceiver(packageReceiver, pkgFilter)
        }
    }

    private fun unregisterReceiversSafe() {
        try { unregisterReceiver(systemEventReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
    }

    companion object {
        private const val REQ_DEFAULT_HOME = 101
        private const val REQ_BIND_WIDGET = 102
        private const val REQ_CONFIGURE_WIDGET = 103
    }
}
