package com.example.ui.view

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.example.R
import com.example.core.NexusColors
import com.example.core.ThemePalette
import com.example.data.IconCache
import com.example.model.AppInfo
import com.example.model.DrawerViewMode
import com.example.model.LauncherSettings
import com.example.model.SortOrder

/**
 * Native View-recycled App Drawer (GridView / ListView with ViewHolder pattern).
 * Zero Compose/RecyclerView dependency; minimal view count in memory regardless of app count.
 */
class AppDrawerView(context: Context) : LinearLayout(context) {

    var onClose: (() -> Unit)? = null
    var onToggleSort: (() -> Unit)? = null
    var onToggleViewMode: (() -> Unit)? = null
    var onOpenSettings: (() -> Unit)? = null
    var onAppClick: ((AppInfo) -> Unit)? = null
    var onAppLongClick: ((AppInfo) -> Unit)? = null

    private val titleView: TextView
    private val closeBtn: ImageView
    private val sortBtn: ImageView
    private val viewModeBtn: ImageView
    private val settingsBtn: ImageView
    private val searchInput: EditText
    private val clearSearchBtn: ImageView
    private val recentLabel: TextView
    private val recentScroll: HorizontalScrollView
    private val recentContainer: LinearLayout
    private val recentDivider: View
    private val emptyText: TextView
    private val gridView: GridView
    private val listView: ListView

    private var allVisibleApps: List<AppInfo> = emptyList()
    private var filteredApps: List<AppInfo> = emptyList()
    private var currentSettings: LauncherSettings = LauncherSettings()
    private var currentIconCache: IconCache? = null
    private var currentBadges: Map<String, Int> = emptyMap()
    private var currentColors: NexusColors = ThemePalette.resolve(context, currentSettings.themeMode)

    private val gridAdapter = DrawerGridAdapter()
    private val listAdapter = DrawerListAdapter()

    init {
        orientation = VERTICAL
        tag = "app_drawer_screen"
        isClickable = true

        val padH = ThemePalette.dp(context, 16f)
        setPadding(padH, ThemePalette.dp(context, 8f), padH, ThemePalette.dp(context, 8f))

        // Top header bar
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, ThemePalette.dp(context, 48f))
        }
        closeBtn = makeIconBtn(R.drawable.ic_back, "Tutup App Drawer", "close_drawer_button") {
            onClose?.invoke()
        }
        titleView = TextView(context).apply {
            text = "Aplikasi"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = ThemePalette.dp(context, 8f)
            }
        }
        sortBtn = makeIconBtn(R.drawable.ic_sort, "Urutkan A-Z / Z-A", "drawer_sort_button") {
            onToggleSort?.invoke()
        }
        viewModeBtn = makeIconBtn(R.drawable.ic_list, "Ubah Grid / Daftar", "drawer_view_mode_button") {
            onToggleViewMode?.invoke()
        }
        settingsBtn = makeIconBtn(R.drawable.ic_settings, "Pengaturan Launcher", "drawer_settings_button") {
            onOpenSettings?.invoke()
        }

        header.addView(closeBtn)
        header.addView(titleView)
        header.addView(sortBtn)
        header.addView(viewModeBtn)
        header.addView(settingsBtn)
        addView(header)

        // Search Box
        val searchBox = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pH = ThemePalette.dp(context, 12f)
            setPadding(pH, 0, ThemePalette.dp(context, 4f), 0)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, ThemePalette.dp(context, 48f)).apply {
                topMargin = ThemePalette.dp(context, 6f)
                bottomMargin = ThemePalette.dp(context, 8f)
            }
            tag = "drawer_search_box"
        }
        val searchIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_search)
            layoutParams = LayoutParams(ThemePalette.dp(context, 20f), ThemePalette.dp(context, 20f))
        }
        searchInput = EditText(context).apply {
            hint = "Cari aplikasi terinstal…"
            background = null
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            tag = "drawer_search_input"
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = ThemePalette.dp(context, 10f)
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    applyFilter(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        clearSearchBtn = makeIconBtn(R.drawable.ic_close, "Hapus pencarian", "clear_drawer_search") {
            searchInput.setText("")
        }.apply { visibility = GONE }

        searchBox.addView(searchIcon)
        searchBox.addView(searchInput)
        searchBox.addView(clearSearchBtn)
        addView(searchBox)

        // Recent apps strip
        recentLabel = TextView(context).apply {
            text = "Terakhir Digunakan"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(ThemePalette.dp(context, 4f), 0, 0, ThemePalette.dp(context, 6f))
        }
        recentContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
        }
        recentScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            tag = "recent_apps_row"
            addView(recentContainer)
        }
        recentDivider = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, ThemePalette.dp(context, 1f)).apply {
                topMargin = ThemePalette.dp(context, 6f)
                bottomMargin = ThemePalette.dp(context, 8f)
            }
        }
        addView(recentLabel)
        addView(recentScroll)
        addView(recentDivider)

        emptyText = TextView(context).apply {
            text = "Tidak ada aplikasi yang cocok"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            visibility = GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        addView(emptyText)

        gridView = GridView(context).apply {
            numColumns = 4
            verticalSpacing = ThemePalette.dp(context, 8f)
            horizontalSpacing = ThemePalette.dp(context, 4f)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, ThemePalette.dp(context, 4f), 0, ThemePalette.dp(context, 18f))
            tag = "drawer_grid"
            adapter = gridAdapter
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        addView(gridView)

        listView = ListView(context).apply {
            divider = null
            dividerHeight = ThemePalette.dp(context, 2f)
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, ThemePalette.dp(context, 4f), 0, ThemePalette.dp(context, 18f))
            tag = "drawer_list"
            adapter = listAdapter
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        addView(listView)
    }

    private fun makeIconBtn(resId: Int, desc: String, btnTag: String, onClick: () -> Unit): ImageView {
        val size = ThemePalette.dp(context, 44f)
        val pad = ThemePalette.dp(context, 10f)
        return ImageView(context).apply {
            setImageResource(resId)
            contentDescription = desc
            tag = btnTag
            setPadding(pad, pad, pad, pad)
            layoutParams = LayoutParams(size, size)
            setOnClickListener { onClick() }
        }
    }

    fun clearSearch() {
        if (searchInput.text.isNotEmpty()) {
            searchInput.setText("")
        }
    }

    fun render(
        apps: List<AppInfo>,
        recentApps: List<AppInfo>,
        settings: LauncherSettings,
        iconCache: IconCache,
        badgeCounts: Map<String, Int>,
        colors: NexusColors
    ) {
        this.allVisibleApps = apps
        this.currentSettings = settings
        this.currentIconCache = iconCache
        this.currentBadges = badgeCounts
        this.currentColors = colors

        val bgColor = Color.argb(
            245,
            Color.red(colors.background),
            Color.green(colors.background),
            Color.blue(colors.background)
        )
        setBackgroundColor(bgColor)

        titleView.setTextColor(colors.textPrimary)
        closeBtn.setColorFilter(colors.textPrimary, PorterDuff.Mode.SRC_IN)
        sortBtn.setColorFilter(
            if (settings.drawerSortOrder == SortOrder.ZA) colors.accent else colors.textSecondary,
            PorterDuff.Mode.SRC_IN
        )
        viewModeBtn.setImageResource(
            if (settings.drawerViewMode == DrawerViewMode.GRID) R.drawable.ic_list else R.drawable.ic_apps_grid
        )
        viewModeBtn.setColorFilter(colors.textPrimary, PorterDuff.Mode.SRC_IN)
        settingsBtn.setColorFilter(colors.textPrimary, PorterDuff.Mode.SRC_IN)

        val searchBox = findViewWithTag<View>("drawer_search_box")
        searchBox?.background = ThemePalette.roundedCard(
            fillColor = colors.surfaceElevated,
            radiusPx = ThemePalette.dpF(context, 22f),
            strokeColor = colors.divider,
            strokeWidthPx = ThemePalette.dp(context, 1f)
        )
        searchInput.setTextColor(colors.textPrimary)
        searchInput.setHintTextColor(colors.textSecondary)
        clearSearchBtn.setColorFilter(colors.textSecondary, PorterDuff.Mode.SRC_IN)
        (searchBox as? ViewGroup)?.getChildAt(0)?.let {
            (it as? ImageView)?.setColorFilter(colors.textSecondary, PorterDuff.Mode.SRC_IN)
        }

        recentLabel.setTextColor(colors.accent)
        recentDivider.setBackgroundColor(colors.divider)
        emptyText.setTextColor(colors.textSecondary)

        val query = searchInput.text?.toString().orEmpty()
        val showRecents = query.isBlank() && settings.showRecentApps && recentApps.isNotEmpty()
        recentLabel.visibility = if (showRecents) VISIBLE else GONE
        recentScroll.visibility = if (showRecents) VISIBLE else GONE
        recentDivider.visibility = if (showRecents) VISIBLE else GONE

        if (showRecents) {
            recentContainer.removeAllViews()
            val itemW = ThemePalette.dp(context, 74f)
            val itemH = ThemePalette.dp(context, 82f)
            recentApps.forEach { app ->
                val iconView = NexusIconView(context).apply {
                    layoutParams = LayoutParams(itemW, itemH)
                    val badges = if (settings.notificationDotsEnabled) badgeCounts[app.packageName] ?: 0 else 0
                    bindApp(
                        packageName = app.packageName,
                        activityName = app.activityName,
                        label = app.label,
                        iconCache = iconCache,
                        iconSize = settings.iconSize,
                        textSize = settings.textSize,
                        shape = settings.iconShape,
                        badgeCount = badges,
                        labelColor = colors.textPrimary,
                        accentColor = colors.accent
                    )
                    setOnClickListener { onAppClick?.invoke(app) }
                    setOnLongClickListener {
                        onAppLongClick?.invoke(app)
                        true
                    }
                }
                recentContainer.addView(iconView)
            }
        }

        gridView.numColumns = settings.drawerColumns.coerceIn(3, 6)
        applyFilter(query)
    }

    private fun applyFilter(rawQuery: String) {
        val q = rawQuery.trim().lowercase()
        clearSearchBtn.visibility = if (rawQuery.isNotEmpty()) VISIBLE else GONE
        filteredApps = if (q.isEmpty()) {
            allVisibleApps
        } else {
            allVisibleApps.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
        titleView.text = "Aplikasi (${filteredApps.size})"

        if (filteredApps.isEmpty()) {
            emptyText.visibility = VISIBLE
            gridView.visibility = GONE
            listView.visibility = GONE
        } else {
            emptyText.visibility = GONE
            if (currentSettings.drawerViewMode == DrawerViewMode.GRID) {
                gridView.visibility = VISIBLE
                listView.visibility = GONE
                gridAdapter.notifyDataSetChanged()
            } else {
                gridView.visibility = GONE
                listView.visibility = VISIBLE
                listAdapter.notifyDataSetChanged()
            }
        }
    }

    private inner class DrawerGridAdapter : BaseAdapter() {
        override fun getCount(): Int = filteredApps.size
        override fun getItem(position: Int): AppInfo = filteredApps[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = (convertView as? NexusIconView) ?: NexusIconView(context).apply {
                layoutParams = android.widget.AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ThemePalette.dp(context, 86f)
                )
            }
            val app = filteredApps[position]
            val cache = currentIconCache ?: return view
            val badges = if (currentSettings.notificationDotsEnabled) {
                currentBadges[app.packageName] ?: 0
            } else 0
            view.bindApp(
                packageName = app.packageName,
                activityName = app.activityName,
                label = app.label,
                iconCache = cache,
                iconSize = currentSettings.iconSize,
                textSize = currentSettings.textSize,
                shape = currentSettings.iconShape,
                badgeCount = badges,
                labelColor = currentColors.textPrimary,
                accentColor = currentColors.accent
            )
            view.setOnClickListener { onAppClick?.invoke(app) }
            view.setOnLongClickListener {
                onAppLongClick?.invoke(app)
                true
            }
            return view
        }
    }

    private class ListRowHolder(
        val root: LinearLayout,
        val icon: NexusIconView,
        val title: TextView,
        val subtitle: TextView
    )

    private inner class DrawerListAdapter : BaseAdapter() {
        override fun getCount(): Int = filteredApps.size
        override fun getItem(position: Int): AppInfo = filteredApps[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val holder: ListRowHolder
            val rowView: View
            if (convertView != null && convertView.tag is ListRowHolder) {
                rowView = convertView
                holder = convertView.tag as ListRowHolder
            } else {
                val root = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val pH = ThemePalette.dp(context, 8f)
                    val pV = ThemePalette.dp(context, 8f)
                    setPadding(pH, pV, pH, pV)
                    minimumHeight = ThemePalette.dp(context, 56f)
                }
                val icon = NexusIconView(context).apply {
                    layoutParams = LayoutParams(ThemePalette.dp(context, 44f), ThemePalette.dp(context, 44f))
                }
                val textCol = LinearLayout(context).apply {
                    orientation = VERTICAL
                    layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = ThemePalette.dp(context, 12f)
                    }
                }
                val title = TextView(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    maxLines = 1
                }
                val sub = TextView(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                    maxLines = 1
                }
                textCol.addView(title)
                textCol.addView(sub)
                root.addView(icon)
                root.addView(textCol)
                holder = ListRowHolder(root, icon, title, sub)
                root.tag = holder
                rowView = root
            }

            val app = filteredApps[position]
            val cache = currentIconCache ?: return rowView
            val badges = if (currentSettings.notificationDotsEnabled) {
                currentBadges[app.packageName] ?: 0
            } else 0
            holder.icon.bindApp(
                packageName = app.packageName,
                activityName = app.activityName,
                label = app.label,
                iconCache = cache,
                iconSize = com.example.model.IconSizePreset.SMALL,
                textSize = com.example.model.TextSizePreset.HIDDEN,
                shape = currentSettings.iconShape,
                badgeCount = badges,
                showLabel = false,
                accentColor = currentColors.accent
            )
            holder.title.text = app.label
            holder.title.setTextColor(currentColors.textPrimary)
            holder.subtitle.text = app.packageName
            holder.subtitle.setTextColor(currentColors.textSecondary)
            holder.root.setOnClickListener { onAppClick?.invoke(app) }
            holder.root.setOnLongClickListener {
                onAppLongClick?.invoke(app)
                true
            }
            return rowView
        }
    }
}
