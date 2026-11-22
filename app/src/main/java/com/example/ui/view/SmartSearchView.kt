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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.example.R
import com.example.core.NexusColors
import com.example.core.ThemePalette
import com.example.data.AppRepository
import com.example.data.IconCache
import com.example.model.AppInfo
import com.example.model.AppShortcutItem
import com.example.model.IconShapePreset
import com.example.model.IconSizePreset
import com.example.model.QuickSystemAction
import com.example.model.SearchResults
import com.example.model.TextSizePreset

/**
 * Instant offline Smart Search overlay backed by a ViewHolder-recycled ListView.
 */
class SmartSearchView(context: Context) : LinearLayout(context) {

    var onClose: (() -> Unit)? = null
    var onQueryChanged: ((String) -> SearchResults)? = null
    var onAppClick: ((AppInfo) -> Unit)? = null
    var onShortcutClick: ((AppShortcutItem) -> Unit)? = null
    var onQuickActionClick: ((QuickSystemAction) -> Unit)? = null

    private sealed interface RowItem {
        data class Header(val title: String) : RowItem
        data class AppRow(val app: AppInfo) : RowItem
        data class ShortcutRow(val shortcut: AppShortcutItem) : RowItem
        data class QuickRow(val action: QuickSystemAction) : RowItem
        data class InfoRow(val message: String) : RowItem
    }

    private val backBtn: ImageView
    private val searchInput: EditText
    private val clearBtn: ImageView
    private val listView: ListView

    private var rows: List<RowItem> = emptyList()
    private var currentIconCache: IconCache? = null
    private var currentAppRepo: AppRepository? = null
    private var currentRecentApps: List<AppInfo> = emptyList()
    private var currentShape: IconShapePreset = IconShapePreset.SQUIRCLE
    private var currentColors: NexusColors = ThemePalette.resolve(context, com.example.model.ThemeMode.SYSTEM)
    private var cachedShortcuts: List<AppShortcutItem> = emptyList()

    private val adapter = SearchAdapter()

    init {
        orientation = VERTICAL
        tag = "smart_search_overlay"
        isClickable = true
        val pad = ThemePalette.dp(context, 16f)
        setPadding(pad, ThemePalette.dp(context, 10f), pad, pad)

        val topBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, ThemePalette.dp(context, 52f))
        }
        backBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_back)
            contentDescription = "Tutup pencarian"
            tag = "close_search_button"
            val p = ThemePalette.dp(context, 10f)
            setPadding(p, p, p, p)
            layoutParams = LayoutParams(ThemePalette.dp(context, 48f), ThemePalette.dp(context, 48f))
            setOnClickListener { onClose?.invoke() }
        }
        val searchBox = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            tag = "smart_search_box"
            setPadding(ThemePalette.dp(context, 12f), 0, ThemePalette.dp(context, 4f), 0)
            layoutParams = LayoutParams(0, ThemePalette.dp(context, 46f), 1f).apply {
                marginStart = ThemePalette.dp(context, 6f)
            }
        }
        searchInput = EditText(context).apply {
            hint = "Cari aplikasi, pintasan, pengaturan…"
            background = null
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            tag = "smart_search_input"
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val q = s?.toString().orEmpty()
                    clearBtn.visibility = if (q.isNotEmpty()) VISIBLE else GONE
                    triggerSearch(q)
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        clearBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_close)
            contentDescription = "Bersihkan"
            visibility = GONE
            val p = ThemePalette.dp(context, 8f)
            setPadding(p, p, p, p)
            layoutParams = LayoutParams(ThemePalette.dp(context, 40f), ThemePalette.dp(context, 40f))
            setOnClickListener { searchInput.setText("") }
        }
        searchBox.addView(searchInput)
        searchBox.addView(clearBtn)
        topBar.addView(backBtn)
        topBar.addView(searchBox)
        addView(topBar)

        listView = ListView(context).apply {
            divider = null
            isVerticalScrollBarEnabled = false
            setPadding(0, ThemePalette.dp(context, 10f), 0, ThemePalette.dp(context, 18f))
            clipToPadding = false
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        listView.adapter = adapter
        addView(listView)
    }

    fun clearSearch() {
        if (searchInput.text.isNotEmpty()) searchInput.setText("")
        cachedShortcuts = emptyList()
    }

    fun render(
        recentApps: List<AppInfo>,
        iconCache: IconCache,
        appRepository: AppRepository,
        shape: IconShapePreset,
        colors: NexusColors
    ) {
        this.currentRecentApps = recentApps
        this.currentIconCache = iconCache
        this.currentAppRepo = appRepository
        this.currentShape = shape
        this.currentColors = colors

        val bgColor = Color.argb(
            246,
            Color.red(colors.background),
            Color.green(colors.background),
            Color.blue(colors.background)
        )
        setBackgroundColor(bgColor)
        backBtn.setColorFilter(colors.textPrimary, PorterDuff.Mode.SRC_IN)
        clearBtn.setColorFilter(colors.textSecondary, PorterDuff.Mode.SRC_IN)
        findViewWithTag<View>("smart_search_box")?.background = ThemePalette.roundedCard(
            fillColor = colors.surfaceElevated,
            radiusPx = ThemePalette.dpF(context, 22f),
            strokeColor = colors.divider,
            strokeWidthPx = ThemePalette.dp(context, 1f)
        )
        searchInput.setTextColor(colors.textPrimary)
        searchInput.setHintTextColor(colors.textSecondary)

        rebuildRows(searchInput.text?.toString().orEmpty())
    }

    private fun triggerSearch(q: String) {
        rebuildRows(q)
        val repo = currentAppRepo ?: return
        if (q.trim().length >= 2) {
            repo.searchShortcutsAsync(q) { shortcuts ->
                if (searchInput.text?.toString().orEmpty() == q) {
                    cachedShortcuts = shortcuts
                    rebuildRows(q)
                }
            }
        } else {
            cachedShortcuts = emptyList()
        }
    }

    private fun rebuildRows(query: String) {
        val list = mutableListOf<RowItem>()
        if (query.isBlank()) {
            if (currentRecentApps.isNotEmpty()) {
                list.add(RowItem.Header("Terakhir Dibuka"))
                currentRecentApps.forEach { list.add(RowItem.AppRow(it)) }
            } else {
                list.add(
                    RowItem.InfoRow(
                        "Ketik nama aplikasi, pintasan, atau pengaturan sistem (wifi, bluetooth, baterai). 100% offline."
                    )
                )
            }
        } else {
            val res = onQueryChanged?.invoke(query) ?: SearchResults()
            if (res.apps.isNotEmpty()) {
                list.add(RowItem.Header("Aplikasi"))
                res.apps.forEach { list.add(RowItem.AppRow(it)) }
            }
            if (cachedShortcuts.isNotEmpty()) {
                list.add(RowItem.Header("Pintasan Aplikasi"))
                cachedShortcuts.forEach { list.add(RowItem.ShortcutRow(it)) }
            }
            if (res.quickActions.isNotEmpty()) {
                list.add(RowItem.Header("Pintasan Sistem"))
                res.quickActions.forEach { list.add(RowItem.QuickRow(it)) }
            }
            if (list.isEmpty()) {
                list.add(RowItem.InfoRow("Tidak ditemukan hasil untuk \"$query\""))
            }
        }
        rows = list
        adapter.notifyDataSetChanged()
    }

    private class RowHolder(
        val root: LinearLayout,
        val icon: NexusIconView,
        val glyph: ImageView,
        val title: TextView,
        val subtitle: TextView
    )

    private inner class SearchAdapter : BaseAdapter() {
        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): RowItem = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val holder: RowHolder
            val rowView: View
            if (convertView != null && convertView.tag is RowHolder) {
                rowView = convertView
                holder = convertView.tag as RowHolder
            } else {
                val root = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(
                        ThemePalette.dp(context, 8f),
                        ThemePalette.dp(context, 8f),
                        ThemePalette.dp(context, 8f),
                        ThemePalette.dp(context, 8f)
                    )
                }
                val icon = NexusIconView(context).apply {
                    layoutParams = LayoutParams(ThemePalette.dp(context, 40f), ThemePalette.dp(context, 40f))
                }
                val glyph = ImageView(context).apply {
                    val p = ThemePalette.dp(context, 8f)
                    setPadding(p, p, p, p)
                    layoutParams = LayoutParams(ThemePalette.dp(context, 40f), ThemePalette.dp(context, 40f))
                }
                val col = LinearLayout(context).apply {
                    orientation = VERTICAL
                    layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = ThemePalette.dp(context, 12f)
                    }
                }
                val title = TextView(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }
                val sub = TextView(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                }
                col.addView(title)
                col.addView(sub)
                root.addView(icon)
                root.addView(glyph)
                root.addView(col)
                holder = RowHolder(root, icon, glyph, title, sub)
                root.tag = holder
                rowView = root
            }

            when (val item = rows[position]) {
                is RowItem.Header -> {
                    holder.icon.visibility = GONE
                    holder.glyph.visibility = GONE
                    holder.subtitle.visibility = GONE
                    holder.title.text = item.title
                    holder.title.setTextColor(currentColors.accent)
                    holder.title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    holder.root.setOnClickListener(null)
                }
                is RowItem.InfoRow -> {
                    holder.icon.visibility = GONE
                    holder.glyph.visibility = GONE
                    holder.subtitle.visibility = GONE
                    holder.title.text = item.message
                    holder.title.setTextColor(currentColors.textSecondary)
                    holder.title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    holder.root.setOnClickListener(null)
                }
                is RowItem.AppRow -> {
                    holder.icon.visibility = VISIBLE
                    holder.glyph.visibility = GONE
                    holder.subtitle.visibility = GONE
                    currentIconCache?.let { cache ->
                        holder.icon.bindApp(
                            packageName = item.app.packageName,
                            activityName = item.app.activityName,
                            label = item.app.label,
                            iconCache = cache,
                            iconSize = IconSizePreset.SMALL,
                            textSize = TextSizePreset.HIDDEN,
                            shape = currentShape,
                            badgeCount = 0,
                            showLabel = false
                        )
                    }
                    holder.title.text = item.app.label
                    holder.title.setTextColor(currentColors.textPrimary)
                    holder.title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    holder.root.setOnClickListener {
                        onAppClick?.invoke(item.app)
                        onClose?.invoke()
                    }
                }
                is RowItem.ShortcutRow -> {
                    holder.icon.visibility = GONE
                    holder.glyph.visibility = VISIBLE
                    holder.glyph.setImageResource(R.drawable.ic_bolt)
                    holder.glyph.setColorFilter(currentColors.accent, PorterDuff.Mode.SRC_IN)
                    holder.subtitle.visibility = VISIBLE
                    holder.title.text = item.shortcut.shortLabel
                    holder.title.setTextColor(currentColors.textPrimary)
                    holder.title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    holder.subtitle.text = item.shortcut.packageName
                    holder.subtitle.setTextColor(currentColors.textSecondary)
                    holder.root.setOnClickListener {
                        onShortcutClick?.invoke(item.shortcut)
                        onClose?.invoke()
                    }
                }
                is RowItem.QuickRow -> {
                    holder.icon.visibility = GONE
                    holder.glyph.visibility = VISIBLE
                    holder.glyph.setImageResource(R.drawable.ic_settings)
                    holder.glyph.setColorFilter(currentColors.accent, PorterDuff.Mode.SRC_IN)
                    holder.subtitle.visibility = VISIBLE
                    holder.title.text = item.action.title
                    holder.title.setTextColor(currentColors.textPrimary)
                    holder.title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    holder.subtitle.text = item.action.subtitle
                    holder.subtitle.setTextColor(currentColors.textSecondary)
                    holder.root.setOnClickListener {
                        onQuickActionClick?.invoke(item.action)
                        onClose?.invoke()
                    }
                }
            }
            return rowView
        }
    }
}
