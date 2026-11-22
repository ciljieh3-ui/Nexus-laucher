package com.example

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.example.core.DeviceProfile
import com.example.core.NexusColors
import com.example.core.PerformanceMonitor
import com.example.core.ThemePalette
import com.example.model.DrawerViewMode
import com.example.model.IconShapePreset
import com.example.model.IconSizePreset
import com.example.model.PerformanceMode
import com.example.model.SortOrder
import com.example.model.SwipeDownAction
import com.example.model.SwipeUpAction
import com.example.model.TextSizePreset
import com.example.model.ThemeMode
import com.example.model.WallpaperMode
import com.example.ui.view.SettingsAppearancePreviewView
import com.example.ui.view.SettingsUiHelper
import com.example.viewmodel.LauncherController
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Pure native android.app.Activity for Nexus Launcher Settings.
 * Zero Jetpack Compose, AppCompat, or ViewModel library overhead.
 */
class SettingsActivity : Activity(), LauncherController.StateListener {

    private lateinit var controller: LauncherController
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var rootContainer: LinearLayout
    private lateinit var headerBar: LinearLayout
    private lateinit var headerTitle: TextView
    private lateinit var backButton: ImageView
    private lateinit var bannerBox: LinearLayout
    private lateinit var bannerText: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var contentColumn: LinearLayout

    private var activeSectionId: String? = null
    private var hiddenSearchQuery: String = ""
    private var previewView: SettingsAppearancePreviewView? = null
    private var backCallback: OnBackInvokedCallback? = null

    private val hideBannerRunnable = Runnable {
        bannerBox.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()

        controller = (application as NexusLauncherApp).controller
        activeSectionId = intent.getStringExtra(EXTRA_INITIAL_SECTION)

        buildShellUi()
        setContentView(rootContainer)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val cb = OnBackInvokedCallback { handleBack() }
            backCallback = cb
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                cb
            )
        }

        renderSection()
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

    private fun buildShellUi() {
        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "settings_root"
        }

        headerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pH = ThemePalette.dp(this@SettingsActivity, 12f)
            val pV = ThemePalette.dp(this@SettingsActivity, 10f)
            setPadding(pH, pV, pH, pV)
        }

        backButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            contentDescription = "Kembali"
            tag = "settings_back_button"
            val p = ThemePalette.dp(this@SettingsActivity, 10f)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(
                ThemePalette.dp(this@SettingsActivity, 48f),
                ThemePalette.dp(this@SettingsActivity, 48f)
            )
            setOnClickListener { handleBack() }
        }

        headerTitle = TextView(this).apply {
            text = "Pengaturan Nexus"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = ThemePalette.dp(this@SettingsActivity, 8f)
            }
        }

        headerBar.addView(backButton)
        headerBar.addView(headerTitle)

        bannerBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            val p = ThemePalette.dp(this@SettingsActivity, 12f)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val m = ThemePalette.dp(this@SettingsActivity, 16f)
                setMargins(m, ThemePalette.dp(this@SettingsActivity, 4f), m, ThemePalette.dp(this@SettingsActivity, 4f))
            }
        }
        bannerText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        bannerBox.addView(bannerText)

        contentColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = ThemePalette.dp(this@SettingsActivity, 16f)
            setPadding(p, ThemePalette.dp(this@SettingsActivity, 6f), p, ThemePalette.dp(this@SettingsActivity, 24f))
            // Keep tablet width comfortable while still full-width on phones
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        scrollView = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(contentColumn)
        }

        rootContainer.addView(headerBar)
        rootContainer.addView(bannerBox)
        rootContainer.addView(scrollView)

        rootContainer.setOnApplyWindowInsetsListener { v, insets ->
            val top: Int
            val bottom: Int
            val left: Int
            val right: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sys = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                top = sys.top
                bottom = sys.bottom
                left = sys.left
                right = sys.right
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
                @Suppress("DEPRECATION")
                left = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION")
                right = insets.systemWindowInsetRight
            }
            v.setPadding(left, top, right, bottom)
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        controller.addListener(this)
        controller.refreshAdaptiveCapabilities()
    }

    override fun onStop() {
        super.onStop()
        controller.removeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        ioExecutor.shutdown()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        }
    }

    override fun onLauncherStateChanged() {
        val pv = previewView
        if (pv != null && activeSectionId == SECTION_APPEARANCE) {
            val colors = ThemePalette.resolve(this, controller.settings.themeMode)
            pv.updatePreview(controller.settings, controller.customWallpaperBitmap, colors.accent)
        }
    }

    private fun handleBack() {
        if (activeSectionId != null) {
            activeSectionId = null
            previewView = null
            renderSection()
        } else {
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBack()
    }

    private fun openSection(id: String) {
        activeSectionId = id
        previewView = null
        scrollView.scrollTo(0, 0)
        renderSection()
    }

    private fun renderSection() {
        val colors = ThemePalette.resolve(this, controller.settings.themeMode)
        rootContainer.setBackgroundColor(colors.background)
        headerBar.setBackgroundColor(colors.surface)
        backButton.setColorFilter(colors.textPrimary, PorterDuff.Mode.SRC_IN)
        headerTitle.setTextColor(colors.textPrimary)

        bannerBox.background = ThemePalette.roundedCard(
            colors.surfaceElevated,
            ThemePalette.dpF(this, 14f),
            colors.accent,
            ThemePalette.dp(this, 1f)
        )
        bannerText.setTextColor(colors.textPrimary)

        contentColumn.removeAllViews()
        previewView = null

        val section = activeSectionId
        if (section == null) {
            headerTitle.text = "Pengaturan Nexus"
            renderCategoryMenu(colors)
        } else {
            headerTitle.text = sectionTitle(section)
            when (section) {
                SECTION_GENERAL -> renderGeneral(colors)
                SECTION_APPEARANCE -> renderAppearance(colors)
                SECTION_HOME -> renderHome(colors)
                SECTION_DRAWER -> renderDrawer(colors)
                SECTION_GESTURES -> renderGestures(colors)
                SECTION_PERFORMANCE -> renderPerformance(colors)
                SECTION_BACKUP -> renderBackup(colors)
                SECTION_ABOUT -> renderAbout(colors)
                else -> renderCategoryMenu(colors)
            }
        }
    }

    private fun sectionTitle(id: String): String = when (id) {
        SECTION_GENERAL -> "Umum & Izin Sistem"
        SECTION_APPEARANCE -> "Tampilan & Wallpaper"
        SECTION_HOME -> "Layar Utama (Home)"
        SECTION_DRAWER -> "App Drawer & Sembunyikan"
        SECTION_GESTURES -> "Kontrol Gestur"
        SECTION_PERFORMANCE -> "Performa & Mesin Adaptif"
        SECTION_BACKUP -> "Backup & Reset"
        SECTION_ABOUT -> "Tentang & Privasi"
        else -> "Pengaturan Nexus"
    }

    private fun renderCategoryMenu(colors: NexusColors) {
        val dev = controller.deviceStatus
        val caps = controller.capabilities
        val s = controller.settings

        contentColumn.addView(SettingsUiHelper.makeCategoryRow(
            this, R.drawable.ic_home, "Umum & Home Default",
            if (dev.isDefaultLauncher) "Nexus aktif sebagai Home default" else "Ketuk untuk mengatur launcher default & notifikasi",
            colors, "cat_general"
        ) { openSection(SECTION_GENERAL) })

        contentColumn.addView(SettingsUiHelper.makeCategoryRow(
            this, R.drawable.ic_wallpaper, "Tampilan & Wallpaper",
            "Tema ${s.themeMode.name}, Wallpaper ${s.wallpaperMode.name}, Ikon ${s.iconShape.name}",
            colors, "cat_appearance"
        ) { openSection(SECTION_APPEARANCE) })

        contentColumn.addView(SettingsUiHelper.makeCategoryRow(
            this, R.drawable.ic_apps_grid, "Layar Utama (Home)",
            "Grid ${s.homeColumns}x${s.homeRows}, jam, baterai, dock & search bar",
            colors, "cat_home"
        ) { openSection(SECTION_HOME) })

        contentColumn.addView(SettingsUiHelper.makeCategoryRow(
            this, R.drawable.ic_list, "App Drawer & Sembunyikan",
            "Tampilan ${s.drawerViewMode.name}, ${s.hiddenComponentKeys.size} aplikasi tersembunyi",
            colors, "cat_drawer"
        ) { openSection(SECTION_DRAWER) })

        contentColumn.addView(SettingsUiHelper.makeCategoryRow(
            this, R.drawable.ic_bolt, "Kontrol Gestur",
            "Swipe atas/bawah & ketuk dua kali untuk mengunci layar",
            colors, "cat_gestures"
        ) { openSection(SECTION_GESTURES) })

        contentColumn.addView(SettingsUiHelper.makeCategoryRow(
            this, R.drawable.ic_settings, "Performa & Telemetri",
            "Mode ${s.performanceMode.name} • Tier ${caps.tier.name} • Cache ${controller.iconCache.currentCacheSizeKb()} KB",
            colors, "cat_performance"
        ) { openSection(SECTION_PERFORMANCE) })

        contentColumn.addView(SettingsUiHelper.makeCategoryRow(
            this, R.drawable.ic_add, "Backup & Reset",
            "Ekspor atau impor JSON tata letak & setelan",
            colors, "cat_backup"
        ) { openSection(SECTION_BACKUP) })

        contentColumn.addView(SettingsUiHelper.makeCategoryRow(
            this, R.drawable.ic_check, "Tentang & Privasi",
            "Nexus Micro ${BuildConfig.VERSION_NAME} • Zero dependencies • 100% offline",
            colors, "cat_about"
        ) { openSection(SECTION_ABOUT) })
    }

    private fun renderGeneral(colors: NexusColors) {
        val status = controller.deviceStatus
        val s = controller.settings

        contentColumn.addView(SettingsUiHelper.makeSectionHeader(this, "Launcher Utama", colors))
        val homeCard = SettingsUiHelper.makeCard(this, colors)
        homeCard.addView(SettingsUiHelper.makeKeyValueRow(
            this, "Status Home Default",
            if (status.isDefaultLauncher) "Aktif (Nexus)" else "Belum Default",
            colors
        ))
        status.currentDefaultHomePackage?.let { pkg ->
            homeCard.addView(SettingsUiHelper.makeKeyValueRow(this, "Paket Home Saat Ini", pkg, colors))
        }
        homeCard.addView(SettingsUiHelper.makeActionButton(
            this, "Pilih Nexus sebagai Home Default", colors, isPrimary = true, tagName = "btn_set_default_home"
        ) {
            try {
                startActivityForResult(controller.systemHelper.createRequestDefaultHomeIntent(), REQ_DEFAULT_HOME)
            } catch (_: Exception) {
                startActivity(controller.systemHelper.createSystemHomeSettingsIntent())
            }
        })
        homeCard.addView(SettingsUiHelper.makeActionButton(
            this, "Buka Setelan Home Sistem", colors
        ) {
            startActivity(controller.systemHelper.createSystemHomeSettingsIntent())
        })
        contentColumn.addView(homeCard)

        contentColumn.addView(SettingsUiHelper.makeSectionHeader(this, "Lencana Notifikasi (Opsional)", colors))
        val notifCard = SettingsUiHelper.makeCard(this, colors)
        notifCard.addView(SettingsUiHelper.makeKeyValueRow(
            this, "Izin Akses Notifikasi",
            if (status.isNotificationAccessGranted) "Diizinkan" else "Belum Diizinkan",
            colors
        ))
        notifCard.addView(SettingsUiHelper.makeSwitchRow(
            this, "Tampilkan Notification Dots", "Menghitung badge lokal di memori tanpa mengirim data",
            s.notificationDotsEnabled, colors
        ) { enabled ->
            controller.updateSettings { it.copy(notificationDotsEnabled = enabled) }
        })
        notifCard.addView(SettingsUiHelper.makeActionButton(
            this, "Atur Akses Notifikasi Android", colors, tagName = "btn_notification_access"
        ) {
            try {
                startActivityForResult(controller.systemHelper.createNotificationAccessIntent(), REQ_NOTIFICATION_ACCESS)
            } catch (_: Exception) {
                showBanner("Tidak dapat membuka setelan notifikasi sistem")
            }
        })
        contentColumn.addView(notifCard)
    }

    private fun renderAppearance(colors: NexusColors) {
        val s = controller.settings

        contentColumn.addView(SettingsUiHelper.makeSectionHeader(this, "Pratinjau Langsung (Canvas)", colors))
        val pv = SettingsAppearancePreviewView(this).apply {
            updatePreview(s, controller.customWallpaperBitmap, colors.accent)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = ThemePalette.dp(this@SettingsActivity, 12f)
            }
        }
        previewView = pv
        contentColumn.addView(pv)

        contentColumn.addView(SettingsUiHelper.makeSectionHeader(this, "Tema & Wallpaper", colors))
        val themeCard = SettingsUiHelper.makeCard(this, colors)
        themeCard.addView(SettingsUiHelper.makeChipSelectorRow(
            this, "Mode Tema",
            listOf("Otomatis" to ThemeMode.SYSTEM, "Terang" to ThemeMode.LIGHT, "Gelap" to ThemeMode.DARK),
            s.themeMode, colors
        ) { mode ->
            controller.updateSettings { it.copy(themeMode = mode) }
            renderSection()
        })
        themeCard.addView(SettingsUiHelper.makeDivider(this, colors))
        themeCard.addView(SettingsUiHelper.makeChipSelectorRow(
            this, "Sumber Wallpaper",
            listOf(
                "Obsidian" to WallpaperMode.BUILTIN_OBSIDIAN,
                "Aurora" to WallpaperMode.BUILTIN_AURORA,
                "Dune" to WallpaperMode.BUILTIN_DUNE,
                "Sistem" to WallpaperMode.SYSTEM,
                "Galeri" to WallpaperMode.CUSTOM_FILE
            ),
            s.wallpaperMode, colors
        ) { wp ->
            if (wp == WallpaperMode.CUSTOM_FILE && controller.customWallpaperBitmap == null) {
                launchGalleryPicker()
            } else {
                controller.updateSettings { it.copy(wallpaperMode = wp) }
                renderSection()
            }
        })
        themeCard.addView(SettingsUiHelper.makeActionButton(
            this, "Pilih Foto Wallpaper dari Galeri", colors, isPrimary = true, tagName = "btn_pick_gallery_wallpaper"
        ) {
            launchGalleryPicker()
        })
        themeCard.addView(SettingsUiHelper.makeActionButton(
            this, "Terapkan ke Wallpaper Sistem Android", colors
        ) {
            controller.applyCurrentWallpaperToSystem { ok ->
                showBanner(
                    if (ok) "Wallpaper diterapkan ke sistem Android"
                    else "Pilih Obsidian/Aurora/Dune atau Galeri untuk menerapkan ke sistem"
                )
            }
        })
        themeCard.addView(SettingsUiHelper.makeActionButton(
            this, "Pemilih Wallpaper Bawaan Perangkat", colors
        ) {
            controller.systemHelper.openSystemWallpaperPicker()
        })
        themeCard.addView(SettingsUiHelper.makeDivider(this, colors))
        themeCard.addView(SettingsUiHelper.makeSliderRow(
            this, "Peredupan Wallpaper (Dim)",
            (s.wallpaperDim * 100f).toInt(), 70, colors
        ) { pct ->
            controller.updateSettings { it.copy(wallpaperDim = pct / 100f) }
        })
        contentColumn.addView(themeCard)

        contentColumn.addView(SettingsUiHelper.makeSectionHeader(this, "Ikon, Label & Dock", colors))
        val iconCard = SettingsUiHelper.makeCard(this, colors)
        iconCard.addView(SettingsUiHelper.makeChipSelectorRow(
            this, "Bentuk Ikon",
            listOf(
                "Sistem" to IconShapePreset.SYSTEM,
                "Squircle" to IconShapePreset.SQUIRCLE,
                "Lingkaran" to IconShapePreset.CIRCLE,
                "Kotak" to IconShapePreset.ROUNDED_SQUARE,
                "Tetes Air" to IconShapePreset.TEARDROP
            ),
            s.iconShape, colors
        ) { shape ->
            controller.updateSettings { it.copy(iconShape = shape) }
            renderSection()
        })
        iconCard.addView(SettingsUiHelper.makeChipSelectorRow(
            this, "Ukuran Ikon",
            listOf("Kecil" to IconSizePreset.SMALL, "Sedang" to IconSizePreset.MEDIUM, "Besar" to IconSizePreset.LARGE),
            s.iconSize, colors
        ) { size ->
            controller.updateSettings { it.copy(iconSize = size) }
            renderSection()
        })
        iconCard.addView(SettingsUiHelper.makeChipSelectorRow(
            this, "Ukuran Teks Label",
            listOf("Sembunyi" to TextSizePreset.HIDDEN, "Kecil" to TextSizePreset.SMALL, "Sedang" to TextSizePreset.MEDIUM, "Besar" to TextSizePreset.LARGE),
            s.textSize, colors
        ) { ts ->
            controller.updateSettings { it.copy(textSize = ts) }
            renderSection()
        })
        iconCard.addView(SettingsUiHelper.makeSliderRow(
            this, "Opasitas Latar Dock",
            (s.dockTransparency * 100f).toInt(), 85, colors
        ) { pct ->
            controller.updateSettings { it.copy(dockTransparency = pct / 100f) }
        })
        contentColumn.addView(iconCard)
    }

    private fun renderHome(colors: NexusColors) {
        val s = controller.settings
        contentColumn.addView(SettingsUiHelper.makeSectionHeader(this, "Grid Home Screen", colors))
        val gridCard = SettingsUiHelper.makeCard(this, colors)
        gridCard.addView(SettingsUiHelper.makeChipSelectorRow(
            this, "Jumlah Kolom Home",
            listOf("3" to 3, "4" to 4, "5" to 5, "6" to 6),
            s.homeColumns, colors
        ) { cols ->
            controller.updateSettings { it.copy(homeColumns = cols) }
            renderSection()
        })
        gridCard.addView(SettingsUiHelper.makeChipSelectorRow(
            this, "Jumlah Baris Home",
            listOf("4" to 4, "5" to 5, "6" to 6),
            s.homeRows, colors
        ) { rows ->
            controller.updateSettings { it.copy(homeRows = rows) }
            renderSection()
        })
        gridCard.addView(SettingsUiHelper.makeSwitchRow(
            this, "Kunci Tata Letak Home", "Mencegah perpindahan atau penghapusan ikon tidak sengaja",
            s.lockHomeLayout, colors
        ) { lock ->
            controller.updateSettings { it.copy(lockHomeLayout = lock) }
        })
        gridCard.addView(SettingsUiHelper.makeSwitchRow(
            this, "Tampilkan Bilah Pencarian", "Bilah pencarian pintar cepat di bawah grid Home",
            s.showSearchBar, colors
        ) { show ->
            controller.updateSettings { it.copy(showSearchBar = show) }
        })
        gridCard.addView(SettingsUiHelper.makeSwitchRow(
            this, "Tampilkan Dock Aplikasi", "Bilah aplikasi favorit di bagian bawah",
            s.showDock, colors
        ) { show ->
            controller.updateSettings { it.copy(showDock = show) }
        })
        contentColumn.addView(gridCard)

        contentColumn.addView(SettingsUiHelper.makeSectionHeader(this, "Jam & Baterai", colors))
        val clockCard = SettingsUiHelper.makeCard(this, colors)
        clockCard.addView(SettingsUiHelper.makeChipSelectorRow(
            this, "Format Jam",
            listOf("Ikuti Sistem" to null, "24 Jam" to true, "12 Jam" to false),
            s.use24HourClock, colors
        ) { v ->
            controller.updateSettings { it.copy(use24HourClock = v) }
            renderSection()
        })
        clockCard.addView(SettingsUiHelper.makeSwitchRow(
            this, "Tampilkan Detik Jam", "Menampilkan detik secara langsung (menonaktifkan menghemat baterai)",
            s.showClockSeconds, colors
        ) { sec ->
            controller.updateSettings { it.copy(showClockSeconds = sec) }
        })
        clockCard.addView(SettingsUiHelper.makeSwitchRow(
            this, "Tampilkan Info Baterai", "Menampilkan persentase dan status pengisian baterai di header",
            s.showBatteryInfo, colors
        ) { bat ->
            controller.updateSettings { it.copy(showBatteryInfo = bat) }
        })
        contentColumn.addView(clockCard)
    }

    private fun renderDrawer(colors: NexusColors) {
        val s = controller.settings
        contentColumn.addView(SettingsUiHelper.makeSectionHeader(this, "Tampilan App Drawer", colors))
        val drawerCard = SettingsUiHelper.makeCard(this, colors)
        drawerCard.addView(SettingsUiHelper.makeChipSelectorRow(
            this, "Mode Daftar",
            listOf("Grid" to DrawerViewMode.GRID, "Daftar (List)" to DrawerViewMode.LIST),
            s.drawerViewMode, colors
        ) { mode ->
            controller.updateSettings { it.copy(drawerViewMode = mode) }
            renderSection()
        })
        drawerCard.addView(SettingsUiHelper.makeChipSelectorRow(
            this, "Kolom App Drawer (Mode Grid)",
            listOf("3" to 3, "4" to 4, "5" to 5),
            s.drawerColumns, colors
        ) { cols ->
            controller.updateSettings { it.copy(drawerColumns = cols) }
            renderSection()
        })
        drawerCard.addView(SettingsUiHelper.makeChipSelectorRow(
            this, "Urutan Aplikasi",
            listOf("A - Z" to SortOrder.AZ, "Z - A" to SortOrder.ZA),
            s.drawerSortOrder, colors
        ) { order ->
            controller.updateSettings { it.copy(drawerSortOrder = order) }
            renderSection()
        })
        drawerCard.addView(SettingsUiHelper.makeSwitchRow(
            this, "Tampilkan Aplikasi Terbaru", "Baris pintasan 5 aplikasi terakhir digunakan di atas Drawer",
            s.showRecentApps, colors
        ) { show ->
            controller.updateSettings { it.copy(showRecentApps = show) }
        })
        contentColumn.addView(drawerCard)

        contentColumn.addView(SettingsUiHelper.makeSectionHeader(
            this, "Sembunyikan Aplikasi (${s.hiddenComponentKeys.size} tersembunyi)", colors
        ))
        val hideCard = SettingsUiHelper.makeCard(this, colors)
        val filterInput = EditText(this).apply {
            hint = "Filter nama aplikasi..."
            setText(hiddenSearchQuery)
            setTextColor(colors.textPrimary)
            setHintTextColor(colors.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            val p = ThemePalette.dp(this@SettingsActivity, 10f)
            setPadding(p, p, p, p)
            background = ThemePalette.roundedCard(colors.surfaceElevated, ThemePalette.dpF(this@SettingsActivity, 12f))
        }
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ThemePalette.dp(this@SettingsActivity, 8f), 0, 0)
        }
        fun rebuildAppsList(q: String) {
            listContainer.removeAllViews()
            val norm = q.trim().lowercase()
            val matches = controller.allApps.filter {
                norm.isEmpty() || it.label.lowercase().contains(norm)
            }.take(60)
            if (matches.isEmpty()) {
                listContainer.addView(TextView(this@SettingsActivity).apply {
                    text = "Tidak ada aplikasi terpasang yang cocok"
                    setTextColor(colors.textSecondary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(0, ThemePalette.dp(this@SettingsActivity, 8f), 0, ThemePalette.dp(this@SettingsActivity, 8f))
                })
                return
            }
            for (app in matches) {
                val cb = CheckBox(this@SettingsActivity).apply {
                    text = "${app.label}  (${app.packageName})"
                    isChecked = app.componentKey in controller.settings.hiddenComponentKeys
                    setTextColor(colors.textPrimary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                    minimumHeight = ThemePalette.dp(this@SettingsActivity, 46f)
                    setOnCheckedChangeListener { _, _ ->
                        controller.toggleHiddenApp(app.componentKey)
                    }
                }
                listContainer.addView(cb)
            }
        }
        filterInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                hiddenSearchQuery = s?.toString().orEmpty()
                rebuildAppsList(hiddenSearchQuery)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        rebuildAppsList(hiddenSearchQuery)
        hideCard.addView(filterInput)
        hideCard.addView(listContainer)
        contentColumn.addView(hideCard)
    }

    private fun renderGestures(colors: NexusColors) {
        val s = controller.settings
        val adminActive = controller.deviceStatus.isDeviceAdminActive

        contentColumn.addView(SettingsUiHelper.makeSectionHeader(this, "Gestur Layar Utama", colors))
        val gestCard = SettingsUiHelper.makeCard(this, colors)
        gestCard.addView(SettingsUiHelper.makeChipSelectorRow(
            this, "Gesek ke Atas (Swipe Up)",
            listOf("App Drawer" to SwipeUpAction.OPEN_DRAWER, "Smart Search" to SwipeUpAction.SMART_SEARCH, "Nonaktif" to SwipeUpAction.NONE),
            s.swipeUpAction, colors
        ) { act ->
            controller.updateSettings { it.copy(swipeUpAction = act) }
            renderSection()
        })
        gestCard.addView(SettingsUiHelper.makeChipSelectorRow(
            this, "Gesek ke Bawah (Swipe Down)",
            listOf("Smart Search" to SwipeDownAction.SMART_SEARCH, "Panel Notifikasi" to SwipeDownAction.NOTIFICATION_SHADE, "Nonaktif" to SwipeDownAction.NONE),
            s.swipeDownAction, colors
        ) { act ->
            controller.updateSettings { it.copy(swipeDownAction = act) }
            renderSection()
        })
        contentColumn.addView(gestCard)

        contentColumn.addView(SettingsUiHelper.makeSectionHeader(this, "Kunci Layar Ketuk Dua Kali", colors))
        val lockCard = SettingsUiHelper.makeCard(this, colors)
        lockCard.addView(SettingsUiHelper.makeKeyValueRow(
            this, "Izin Device Admin",
            if (adminActive) "Aktif (Siap mengunci)" else "Belum Aktif",
            colors
        ))
        lockCard.addView(SettingsUiHelper.makeSwitchRow(
            this, "Aktifkan Gestur Ketuk Dua Kali", "Ketuk dua kali ruang kosong di Home untuk mengunci layar",
            s.doubleTapLockEnabled, colors
        ) { en ->
            controller.updateSettings { it.copy(doubleTapLockEnabled = en) }
        })
        if (adminActive) {
            lockCard.addView(SettingsUiHelper.makeActionButton(
                this, "Nonaktifkan Izin Device Admin", colors, isDanger = true
            ) {
                controller.systemHelper.removeDeviceAdmin()
                controller.refreshAdaptiveCapabilities()
                showBanner("Izin Device Admin dinonaktifkan")
                renderSection()
            })
        } else {
            lockCard.addView(SettingsUiHelper.makeActionButton(
                this, "Aktifkan Izin Device Admin untuk Kunci Layar", colors, isPrimary = true, tagName = "btn_device_admin"
            ) {
                try {
                    startActivityForResult(
                        controller.systemHelper.createDeviceAdminActivationIntent(),
                        REQ_DEVICE_ADMIN
                    )
                } catch (_: Exception) {
                    showBanner("Tidak dapat membuka dialog Device Admin")
                }
            })
        }
        contentColumn.addView(lockCard)
    }

    private fun renderPerformance(colors: NexusColors) {
        val s = controller.settings
        val caps = controller.capabilities
        val telemetry = PerformanceMonitor.snapshot()
        val cache = controller.iconCache

        contentColumn.addView(SettingsUiHelper.makeSectionHeader(this, "Profil Performa Adaptif", colors))
        val modeCard = SettingsUiHelper.makeCard(this, colors)
        modeCard.addView(SettingsUiHelper.makeChipSelectorRow(
            this, "Mode Performa",
            listOf(
                "Auto Adaptif" to PerformanceMode.AUTO_ADAPTIVE,
                "Hemat Ultra" to PerformanceMode.ULTRA_BATTERY_SAVER,
                "Seimbang" to PerformanceMode.BALANCED,
                "Halus" to PerformanceMode.SMOOTH
            ),
            s.performanceMode, colors
        ) { mode ->
            controller.updateSettings { it.copy(performanceMode = mode) }
            renderSection()
        })
        contentColumn.addView(modeCard)

        contentColumn.addView(SettingsUiHelper.makeSectionHeader(this, "Telemetri Runtime Langsung", colors))
        val telCard = SettingsUiHelper.makeCard(this, colors)
        telCard.addView(SettingsUiHelper.makeKeyValueRow(this, "Latensi Startup Frame 1", "${telemetry.startupLatencyMs} ms", colors))
        telCard.addView(SettingsUiHelper.makeKeyValueRow(this, "PSS RAM Proses", String.format(Locale.US, "%.1f MB", telemetry.totalPssMb), colors))
        telCard.addView(SettingsUiHelper.makeKeyValueRow(this, "Native / JVM Heap", String.format(Locale.US, "%.1f / %.1f MB", telemetry.nativeHeapMb, telemetry.jvmHeapUsedMb), colors))
        telCard.addView(SettingsUiHelper.makeKeyValueRow(this, "Frame Jank (>16.6ms)", String.format(Locale.US, "%.1f%% (%d/%d)", telemetry.jankPercent, telemetry.jankyFramesCount, telemetry.totalFramesMeasured), colors))
        telCard.addView(SettingsUiHelper.makeActionButton(this, "Muat Ulang Telemetri", colors) {
            controller.refreshAdaptiveCapabilities()
            renderSection()
        })
        contentColumn.addView(telCard)

        contentColumn.addView(SettingsUiHelper.makeSectionHeader(this, "Deteksi Kemampuan Perangkat (Universal)", colors))
        val capCard = SettingsUiHelper.makeCard(this, colors)
        capCard.addView(SettingsUiHelper.makeKeyValueRow(this, "Tier Adaptif Terdeteksi", caps.tier.name, colors))
        capCard.addView(SettingsUiHelper.makeKeyValueRow(this, "RAM Total / Low-RAM Flag", "${caps.totalRamMb} MB / ${caps.isLowRamDevice}", colors))
        capCard.addView(SettingsUiHelper.makeKeyValueRow(this, "Core CPU / Workers Ikon", "${caps.cpuCores} core / ${caps.concurrentWorkers} thread", colors))
        capCard.addView(SettingsUiHelper.makeKeyValueRow(this, "Resolusi & DPI", "${caps.screenWidthPx}x${caps.screenHeightPx} @ ${caps.densityDpi} dpi", colors))
        capCard.addView(SettingsUiHelper.makeKeyValueRow(this, "Ukuran Raster Ikon Efektif", "${DeviceProfile.resolveEffectiveIconPx(caps, s.performanceMode)} px", colors))
        capCard.addView(SettingsUiHelper.makeKeyValueRow(this, "Durasi Animasi Efektif", "${DeviceProfile.resolveEffectiveAnimationMs(caps, s.performanceMode)} ms", colors))
        capCard.addView(SettingsUiHelper.makeKeyValueRow(this, "Battery Saver Sistem", if (caps.isPowerSaveMode) "Aktif" else "Nonaktif", colors))
        contentColumn.addView(capCard)

        contentColumn.addView(SettingsUiHelper.makeSectionHeader(this, "Cache Ikon Terbatas (LruCache)", colors))
        val cacheCard = SettingsUiHelper.makeCard(this, colors)
        cacheCard.addView(SettingsUiHelper.makeKeyValueRow(this, "Ikon Tersimpan di Cache", "${cache.entryCount()} item", colors))
        cacheCard.addView(SettingsUiHelper.makeKeyValueRow(this, "Memori Cache / Batas", "${cache.currentCacheSizeKb()} / ${cache.maxCacheSizeKb()} KB", colors))
        cacheCard.addView(SettingsUiHelper.makeKeyValueRow(this, "Cache Hit Ratio", "${cache.hitRatioPercent()}%", colors))
        cacheCard.addView(SettingsUiHelper.makeActionButton(this, "Bersihkan Cache Ikon", colors, tagName = "btn_clear_cache") {
            controller.iconCache.clear()
            showBanner("Cache ikon dibersihkan")
            renderSection()
        })
        contentColumn.addView(cacheCard)
    }

    private fun renderBackup(colors: NexusColors) {
        contentColumn.addView(SettingsUiHelper.makeSectionHeader(this, "Cadangkan & Pulihkan (JSON Lokal)", colors))
        val card = SettingsUiHelper.makeCard(this, colors)
        card.addView(TextView(this).apply {
            text = "Simpan tata letak layar utama, folder, dock, dan semua setelan Nexus ke file JSON lokal tanpa memerlukan internet."
            setTextColor(colors.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, 0, 0, ThemePalette.dp(this@SettingsActivity, 8f))
        })
        card.addView(SettingsUiHelper.makeActionButton(
            this, "Ekspor Backup ke File JSON", colors, isPrimary = true, tagName = "btn_export_backup"
        ) {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "nexus_launcher_backup.json")
            }
            try {
                startActivityForResult(intent, REQ_EXPORT_BACKUP)
            } catch (_: Exception) {
                showBanner("Pemilih file sistem tidak tersedia")
            }
        })
        card.addView(SettingsUiHelper.makeActionButton(
            this, "Impor Backup dari File JSON", colors, tagName = "btn_import_backup"
        ) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain"))
            }
            try {
                startActivityForResult(intent, REQ_IMPORT_BACKUP)
            } catch (_: Exception) {
                showBanner("Pemilih file sistem tidak tersedia")
            }
        })
        card.addView(SettingsUiHelper.makeDivider(this, colors))
        card.addView(SettingsUiHelper.makeActionButton(
            this, "Reset Semua Setelan & Tata Letak", colors, isDanger = true, tagName = "btn_reset_launcher"
        ) {
            AlertDialog.Builder(this)
                .setTitle("Reset Nexus Launcher?")
                .setMessage("Semua pengaturan, folder, dan posisi ikon Home akan dikembalikan ke bawaan awal.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Reset") { _, _ ->
                    controller.resetLauncherSettings()
                    showBanner("Pengaturan Nexus Launcher direset ke bawaan awal")
                    renderSection()
                }
                .show()
        })
        contentColumn.addView(card)
    }

    private fun renderAbout(colors: NexusColors) {
        contentColumn.addView(SettingsUiHelper.makeSectionHeader(this, "Arsitektur Nexus Micro", colors))
        val infoCard = SettingsUiHelper.makeCard(this, colors)
        infoCard.addView(SettingsUiHelper.makeKeyValueRow(this, "Aplikasi", "Nexus Launcher", colors))
        infoCard.addView(SettingsUiHelper.makeKeyValueRow(this, "Versi", "${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})", colors))
        infoCard.addView(SettingsUiHelper.makeKeyValueRow(this, "Lingkungan OS", DeviceProfile.sdkName(), colors))
        infoCard.addView(SettingsUiHelper.makeKeyValueRow(this, "Dependency Eksternal", "0 (Zero Runtime Dependencies)", colors))
        infoCard.addView(SettingsUiHelper.makeKeyValueRow(this, "Izin Internet", "Tidak Ada (100% Offline)", colors))
        infoCard.addView(SettingsUiHelper.makeDivider(this, colors))
        infoCard.addView(TextView(this).apply {
            text = "Jaminan Privasi & Kontrol:\n• Tidak ada telemetri cloud, tidak ada pelacak analitik, dan tidak ada layanan latar belakang permanen.\n• Untuk kembali ke launcher bawaan perangkat kapan saja: buka Setelan Android > Aplikasi > Aplikasi Default > Layar Utama, lalu pilih launcher bawaan."
            setTextColor(colors.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        })
        infoCard.addView(SettingsUiHelper.makeActionButton(
            this, "Buka Setelan Aplikasi Home Default", colors
        ) {
            startActivity(controller.systemHelper.createSystemHomeSettingsIntent())
        })
        contentColumn.addView(infoCard)
    }

    private fun launchGalleryPicker() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                type = "image/*"
            }
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
        }
        try {
            startActivityForResult(intent, REQ_PICK_WALLPAPER)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            try {
                startActivityForResult(fallback, REQ_PICK_WALLPAPER)
            } catch (_: Exception) {
                showBanner("Pemilih galeri tidak tersedia di perangkat ini")
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_DEFAULT_HOME, REQ_DEVICE_ADMIN, REQ_NOTIFICATION_ACCESS -> {
                controller.refreshAdaptiveCapabilities()
                renderSection()
            }
            REQ_PICK_WALLPAPER -> {
                val uri = data?.data
                if (resultCode == RESULT_OK && uri != null) {
                    controller.applyGalleryWallpaper(uri) { ok ->
                        showBanner(if (ok) "Wallpaper galeri diterapkan" else "Gagal memuat gambar galeri")
                        renderSection()
                    }
                }
            }
            REQ_EXPORT_BACKUP -> {
                val uri = data?.data
                if (resultCode == RESULT_OK && uri != null) {
                    ioExecutor.execute {
                        val ok = try {
                            val json = controller.exportBackupJson()
                            contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                            true
                        } catch (_: Exception) {
                            false
                        }
                        mainHandler.post {
                            showBanner(if (ok) "Backup JSON berhasil disimpan" else "Gagal menyimpan file backup")
                        }
                    }
                }
            }
            REQ_IMPORT_BACKUP -> {
                val uri = data?.data
                if (resultCode == RESULT_OK && uri != null) {
                    ioExecutor.execute {
                        val raw = try {
                            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        } catch (_: Exception) {
                            null
                        }
                        mainHandler.post {
                            if (raw == null) {
                                showBanner("Gagal membaca file backup")
                            } else {
                                val report = controller.importBackupJson(raw)
                                val summary = if (report.success) {
                                    val note = if (report.skippedWidgets > 0) {
                                        " (${report.skippedWidgets} widget perlu dipasang ulang)"
                                    } else ""
                                    "Dipulihkan: ${report.restoredHomeItems} item Home, ${report.restoredDockItems} item Dock$note"
                                } else {
                                    report.message
                                }
                                showBanner(summary)
                                renderSection()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showBanner(message: String) {
        bannerText.text = message
        bannerBox.visibility = View.VISIBLE
        mainHandler.removeCallbacks(hideBannerRunnable)
        mainHandler.postDelayed(hideBannerRunnable, 3500L)
    }

    companion object {
        const val EXTRA_INITIAL_SECTION = "initial_section"
        const val SECTION_GENERAL = "general"
        const val SECTION_APPEARANCE = "appearance"
        const val SECTION_HOME = "home"
        const val SECTION_DRAWER = "drawer"
        const val SECTION_GESTURES = "gestures"
        const val SECTION_PERFORMANCE = "performance"
        const val SECTION_BACKUP = "backup"
        const val SECTION_ABOUT = "about"

        private const val REQ_DEFAULT_HOME = 201
        private const val REQ_DEVICE_ADMIN = 202
        private const val REQ_NOTIFICATION_ACCESS = 203
        private const val REQ_PICK_WALLPAPER = 204
        private const val REQ_EXPORT_BACKUP = 205
        private const val REQ_IMPORT_BACKUP = 206
    }
}
