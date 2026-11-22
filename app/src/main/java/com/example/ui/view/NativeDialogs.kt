package com.example.ui.view

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import com.example.R
import com.example.core.NexusColors
import com.example.core.ThemePalette
import com.example.data.AppRepository
import com.example.data.IconCache
import com.example.model.AppInfo
import com.example.model.AppRef
import com.example.model.AppShortcutItem
import com.example.model.HomeItem
import com.example.model.LauncherSettings
import com.example.widget.InstalledWidgetProvider

/**
 * Pure native Android Dialog builders for Folder popover, App Action & Shortcuts menu,
 * and Real Android Widget Picker (zero AppCompat/Material Dialog dependencies).
 */
object NativeDialogs {

    fun showFolderDialog(
        context: Context,
        folder: HomeItem.FolderItem,
        settings: LauncherSettings,
        iconCache: IconCache,
        badgeCounts: Map<String, Int>,
        colors: NexusColors,
        onRename: (String) -> Unit,
        onAppClick: (AppRef) -> Unit,
        onRemoveAppFromFolder: (AppRef) -> Unit
    ): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = ThemePalette.dp(context, 18f)
            setPadding(pad, pad, pad, pad)
            background = ThemePalette.roundedCard(
                colors.surface,
                ThemePalette.dpF(context, 26f),
                colors.divider,
                ThemePalette.dp(context, 1f)
            )
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleInput = EditText(context).apply {
            setText(folder.title)
            setTextColor(colors.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = null
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val saveBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_check)
            setColorFilter(colors.accent, PorterDuff.Mode.SRC_IN)
            contentDescription = "Simpan nama folder"
            val p = ThemePalette.dp(context, 10f)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(ThemePalette.dp(context, 44f), ThemePalette.dp(context, 44f))
            setOnClickListener {
                onRename(titleInput.text?.toString().orEmpty())
            }
        }
        val closeBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(colors.textSecondary, PorterDuff.Mode.SRC_IN)
            contentDescription = "Tutup folder"
            val p = ThemePalette.dp(context, 10f)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(ThemePalette.dp(context, 44f), ThemePalette.dp(context, 44f))
            setOnClickListener {
                onRename(titleInput.text?.toString().orEmpty())
                dialog.dismiss()
            }
        }
        header.addView(titleInput)
        header.addView(saveBtn)
        header.addView(closeBtn)
        root.addView(header)

        val grid = GridView(context).apply {
            numColumns = 3
            verticalSpacing = ThemePalette.dp(context, 10f)
            horizontalSpacing = ThemePalette.dp(context, 8f)
            val gridH = ThemePalette.dp(context, if (folder.apps.size > 6) 260f else 180f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                gridH
            ).apply {
                topMargin = ThemePalette.dp(context, 10f)
            }
            adapter = object : BaseAdapter() {
                override fun getCount(): Int = folder.apps.size
                override fun getItem(position: Int): AppRef = folder.apps[position]
                override fun getItemId(position: Int): Long = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val iconView = (convertView as? NexusIconView) ?: NexusIconView(context).apply {
                        layoutParams = android.widget.AbsListView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ThemePalette.dp(context, 84f)
                        )
                    }
                    val ref = folder.apps[position]
                    val badges = if (settings.notificationDotsEnabled) badgeCounts[ref.packageName] ?: 0 else 0
                    iconView.bindApp(
                        packageName = ref.packageName,
                        activityName = ref.activityName,
                        label = ref.label,
                        iconCache = iconCache,
                        iconSize = settings.iconSize,
                        textSize = settings.textSize,
                        shape = settings.iconShape,
                        badgeCount = badges,
                        labelColor = colors.textPrimary,
                        accentColor = colors.accent
                    )
                    iconView.setOnClickListener {
                        dialog.dismiss()
                        onAppClick(ref)
                    }
                    iconView.setOnLongClickListener {
                        onRemoveAppFromFolder(ref)
                        dialog.dismiss()
                        true
                    }
                    return iconView
                }
            }
        }
        root.addView(grid)

        val hint = TextView(context).apply {
            text = "Tekan lama ikon untuk mengeluarkan dari folder"
            setTextColor(colors.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.END
            setPadding(0, ThemePalette.dp(context, 8f), 0, 0)
        }
        root.addView(hint)

        dialog.setContentView(root)
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.90f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
        return dialog
    }

    fun showAppActionDialog(
        context: Context,
        appInfo: AppInfo,
        homeItemId: String?,
        dockItemId: String?,
        appRepository: AppRepository,
        iconCache: IconCache,
        settings: LauncherSettings,
        existingFolders: List<HomeItem.FolderItem>,
        existingHomeApps: List<HomeItem.AppItem>,
        colors: NexusColors,
        onLaunchShortcut: (AppShortcutItem) -> Unit,
        onPinShortcutToHome: (AppShortcutItem) -> Unit,
        onPinToHome: () -> Unit,
        onRemoveFromHome: (() -> Unit)?,
        onPinToDock: () -> Unit,
        onRemoveFromDock: (() -> Unit)?,
        onAddToFolder: (String) -> Unit,
        onCreateFolderWithApp: (String) -> Unit,
        onToggleHidden: () -> Unit,
        onEnterEditMode: () -> Unit,
        onOpenAppInfo: () -> Unit,
        onUninstall: () -> Unit
    ): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val scroll = ScrollView(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = ThemePalette.dp(context, 18f)
            setPadding(pad, pad, pad, pad)
            background = ThemePalette.roundedCard(
                colors.surface,
                ThemePalette.dpF(context, 24f),
                colors.divider,
                ThemePalette.dp(context, 1f)
            )
        }
        scroll.addView(root)

        // Header
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, ThemePalette.dp(context, 10f))
        }
        val iconView = NexusIconView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ThemePalette.dp(context, 46f), ThemePalette.dp(context, 46f))
            bindApp(
                packageName = appInfo.packageName,
                activityName = appInfo.activityName,
                label = appInfo.label,
                iconCache = iconCache,
                iconSize = com.example.model.IconSizePreset.SMALL,
                textSize = com.example.model.TextSizePreset.HIDDEN,
                shape = settings.iconShape,
                badgeCount = 0,
                showLabel = false
            )
        }
        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = ThemePalette.dp(context, 12f)
            }
        }
        titleCol.addView(TextView(context).apply {
            text = appInfo.label
            setTextColor(colors.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })
        titleCol.addView(TextView(context).apply {
            text = appInfo.packageName
            setTextColor(colors.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        })
        header.addView(iconView)
        header.addView(titleCol)
        root.addView(header)

        // Async LauncherApps shortcuts container
        val shortcutsHolder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        root.addView(shortcutsHolder)

        appRepository.getShortcutsForPackageAsync(appInfo.packageName) { shortcuts ->
            if (shortcuts.isNotEmpty()) {
                shortcutsHolder.visibility = View.VISIBLE
                shortcutsHolder.addView(makeDivider(context, colors))
                shortcutsHolder.addView(TextView(context).apply {
                    text = "Pintasan Aplikasi"
                    setTextColor(colors.accent)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    setPadding(0, ThemePalette.dp(context, 6f), 0, ThemePalette.dp(context, 4f))
                })
                shortcuts.forEach { sc ->
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        minimumHeight = ThemePalette.dp(context, 46f)
                    }
                    val txt = TextView(context).apply {
                        text = "⚡ ${sc.shortLabel}"
                        setTextColor(colors.textPrimary)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener {
                            dialog.dismiss()
                            onLaunchShortcut(sc)
                        }
                    }
                    val pinBtn = TextView(context).apply {
                        text = "+ Home"
                        setTextColor(colors.accent)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                        setPadding(ThemePalette.dp(context, 10f), ThemePalette.dp(context, 8f), ThemePalette.dp(context, 6f), ThemePalette.dp(context, 8f))
                        setOnClickListener {
                            onPinShortcutToHome(sc)
                            dialog.dismiss()
                        }
                    }
                    row.addView(txt)
                    row.addView(pinBtn)
                    shortcutsHolder.addView(row)
                }
            }
        }

        root.addView(makeDivider(context, colors))

        if (onRemoveFromHome != null) {
            root.addView(makeActionTextRow(context, "Geser / Atur Posisi (Mode Edit)", colors.textPrimary) {
                dialog.dismiss()
                onEnterEditMode()
            })
            root.addView(makeActionTextRow(context, "Hapus dari Layar Utama", colors.textPrimary) {
                onRemoveFromHome()
                dialog.dismiss()
            })
        } else {
            root.addView(makeActionTextRow(context, "Tambahkan ke Layar Utama", colors.textPrimary) {
                onPinToHome()
                dialog.dismiss()
            })
        }

        if (onRemoveFromDock != null) {
            root.addView(makeActionTextRow(context, "Hapus dari Dock", colors.textPrimary) {
                onRemoveFromDock()
                dialog.dismiss()
            })
        } else {
            root.addView(makeActionTextRow(context, "Sematkan ke Dock", colors.textPrimary) {
                onPinToDock()
                dialog.dismiss()
            })
        }

        // Folder options
        val folderSubContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(ThemePalette.dp(context, 12f), 0, 0, 0)
        }
        val otherApps = existingHomeApps.filter {
            it.id != homeItemId && it.componentKey != appInfo.componentKey
        }.take(6)
        if (existingFolders.isNotEmpty() || otherApps.isNotEmpty()) {
            root.addView(makeActionTextRow(context, "Masukkan ke Folder / Buat Folder", colors.textPrimary) {
                folderSubContainer.visibility = if (folderSubContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            })
            existingFolders.forEach { folder ->
                folderSubContainer.addView(makeActionTextRow(context, "→ Folder: ${folder.title}", colors.accent) {
                    onAddToFolder(folder.id)
                    dialog.dismiss()
                })
            }
            otherApps.forEach { other ->
                folderSubContainer.addView(makeActionTextRow(context, "→ Gabung dgn ${other.label}", colors.accent) {
                    onCreateFolderWithApp(other.id)
                    dialog.dismiss()
                })
            }
            root.addView(folderSubContainer)
        }

        val isHidden = appInfo.componentKey in settings.hiddenComponentKeys
        root.addView(
            makeActionTextRow(
                context,
                if (isHidden) "Tampilkan di App Drawer" else "Sembunyikan dari App Drawer",
                colors.textPrimary
            ) {
                onToggleHidden()
                dialog.dismiss()
            }
        )
        root.addView(makeActionTextRow(context, "Info Aplikasi", colors.textPrimary) {
            onOpenAppInfo()
            dialog.dismiss()
        })
        root.addView(makeActionTextRow(context, "Copot Pemasangan (Uninstall)", colors.danger) {
            onUninstall()
            dialog.dismiss()
        })

        dialog.setContentView(scroll)
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.88f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
        return dialog
    }

    fun showWidgetPickerDialog(
        context: Context,
        providers: List<InstalledWidgetProvider>,
        colors: NexusColors,
        onSelect: (InstalledWidgetProvider) -> Unit
    ): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = ThemePalette.dp(context, 18f)
            setPadding(pad, pad, pad, pad)
            background = ThemePalette.roundedCard(
                colors.surface,
                ThemePalette.dpF(context, 24f),
                colors.divider,
                ThemePalette.dp(context, 1f)
            )
        }
        root.addView(TextView(context).apply {
            text = "Tambah Widget Android"
            setTextColor(colors.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })
        root.addView(TextView(context).apply {
            text = "Pilih widget asli dari aplikasi yang terinstal."
            setTextColor(colors.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, ThemePalette.dp(context, 2f), 0, ThemePalette.dp(context, 10f))
        })

        var filtered = providers
        val search = EditText(context).apply {
            hint = "Cari widget…"
            setTextColor(colors.textPrimary)
            setHintTextColor(colors.textSecondary)
            setSingleLine(true)
            setPadding(
                ThemePalette.dp(context, 12f),
                ThemePalette.dp(context, 10f),
                ThemePalette.dp(context, 12f),
                ThemePalette.dp(context, 10f)
            )
            background = ThemePalette.roundedCard(colors.surfaceElevated, ThemePalette.dpF(context, 14f))
        }
        root.addView(search)

        val listView = ListView(context).apply {
            divider = ColorDrawable(colors.divider)
            dividerHeight = 1
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ThemePalette.dp(context, 340f)
            ).apply {
                topMargin = ThemePalette.dp(context, 10f)
            }
        }
        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = filtered.size
            override fun getItem(position: Int): InstalledWidgetProvider = filtered[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val col = (convertView as? LinearLayout) ?: LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(
                        ThemePalette.dp(context, 6f),
                        ThemePalette.dp(context, 10f),
                        ThemePalette.dp(context, 6f),
                        ThemePalette.dp(context, 10f)
                    )
                    addView(TextView(context).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f) })
                    addView(TextView(context).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f) })
                }
                val item = filtered[position]
                (col.getChildAt(0) as TextView).apply {
                    text = item.label
                    setTextColor(colors.textPrimary)
                }
                (col.getChildAt(1) as TextView).apply {
                    text = item.appLabel
                    setTextColor(colors.textSecondary)
                }
                col.setOnClickListener {
                    dialog.dismiss()
                    onSelect(item)
                }
                return col
            }
        }
        listView.adapter = adapter
        root.addView(listView)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString().orEmpty().trim().lowercase()
                filtered = if (q.isEmpty()) providers else providers.filter {
                    it.label.lowercase().contains(q) || it.appLabel.lowercase().contains(q)
                }
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        dialog.setContentView(root)
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.90f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
        return dialog
    }

    private fun makeDivider(context: Context, colors: NexusColors): View {
        return View(context).apply {
            setBackgroundColor(colors.divider)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ThemePalette.dp(context, 1f)
            ).apply {
                topMargin = ThemePalette.dp(context, 6f)
                bottomMargin = ThemePalette.dp(context, 4f)
            }
        }
    }

    private fun makeActionTextRow(
        context: Context,
        label: String,
        textColor: Int,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ThemePalette.dp(context, 46f)
            setPadding(ThemePalette.dp(context, 4f), 0, ThemePalette.dp(context, 4f), 0)
            setOnClickListener { onClick() }
        }
    }
}
