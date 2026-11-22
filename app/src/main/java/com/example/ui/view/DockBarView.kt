package com.example.ui.view

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import com.example.core.ThemePalette
import com.example.data.IconCache
import com.example.model.DockItem
import com.example.model.LauncherSettings

/**
 * Minimal translucent Dock holding up to 5 pinned apps/folders + quick App Drawer trigger.
 */
class DockBarView(context: Context) : LinearLayout(context) {

    var onLaunchApp: ((String, String) -> Unit)? = null
    var onOpenFolder: ((String) -> Unit)? = null
    var onLongClickDockApp: ((DockItem.DockApp) -> Unit)? = null
    var onOpenDrawer: (() -> Unit)? = null

    private var drawerButton: ImageView? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val padH = ThemePalette.dp(context, 10f)
        val padV = ThemePalette.dp(context, 6f)
        setPadding(padH, padV, padH, padV)
        tag = "home_dock"
    }

    fun render(
        items: List<DockItem>,
        settings: LauncherSettings,
        iconCache: IconCache,
        badgeCounts: Map<String, Int>,
        accentColor: Int
    ) {
        visibility = if (settings.showDock) VISIBLE else GONE
        if (!settings.showDock) return

        val bgAlpha = ((1f - settings.dockTransparency).coerceIn(0f, 0.92f) * 0.65f * 255f).toInt()
        val strokeAlpha = ((1f - settings.dockTransparency).coerceIn(0f, 0.92f) * 0.25f * 255f).toInt()
        background = ThemePalette.roundedCard(
            fillColor = Color.argb(bgAlpha, 8, 12, 22),
            radiusPx = ThemePalette.dpF(context, 26f),
            strokeColor = Color.argb(strokeAlpha, 255, 255, 255),
            strokeWidthPx = if (strokeAlpha > 12) ThemePalette.dp(context, 1f) else 0
        )

        removeAllViews()
        items.take(5).forEach { dock ->
            val iconView = NexusIconView(context).apply {
                layoutParams = LayoutParams(0, ThemePalette.dp(context, 62f), 1f)
            }
            when (dock) {
                is DockItem.DockApp -> {
                    val badges = if (settings.notificationDotsEnabled) badgeCounts[dock.packageName] ?: 0 else 0
                    iconView.bindApp(
                        packageName = dock.packageName,
                        activityName = dock.activityName,
                        label = dock.label,
                        iconCache = iconCache,
                        iconSize = settings.iconSize,
                        textSize = settings.textSize,
                        shape = settings.iconShape,
                        badgeCount = badges,
                        showLabel = false,
                        accentColor = accentColor
                    )
                    iconView.setOnClickListener { onLaunchApp?.invoke(dock.packageName, dock.activityName) }
                    iconView.setOnLongClickListener {
                        onLongClickDockApp?.invoke(dock)
                        true
                    }
                }
                is DockItem.DockFolder -> {
                    val totalBadges = if (settings.notificationDotsEnabled) {
                        dock.apps.sumOf { badgeCounts[it.packageName] ?: 0 }
                    } else 0
                    iconView.bindFolder(
                        folderId = dock.id,
                        title = dock.title,
                        apps = dock.apps,
                        iconCache = iconCache,
                        iconSize = settings.iconSize,
                        textSize = settings.textSize,
                        shape = settings.iconShape,
                        totalBadges = totalBadges,
                        showLabel = false,
                        accentColor = accentColor
                    )
                    iconView.setOnClickListener { onOpenFolder?.invoke(dock.id) }
                    iconView.setOnLongClickListener {
                        onOpenFolder?.invoke(dock.id)
                        true
                    }
                }
            }
            addView(iconView)
        }

        if (items.size < 5) {
            val btnSize = ThemePalette.dp(context, 48f)
            val btn = ImageView(context).apply {
                tag = "open_drawer_button"
                contentDescription = "Buka App Drawer"
                setImageResource(com.example.R.drawable.ic_apps_grid)
                setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val p = ThemePalette.dp(context, 11f)
                setPadding(p, p, p, p)
                background = ThemePalette.rippleRounded(
                    fillColor = 0x2EFFFFFF,
                    rippleColor = 0x55FFFFFF,
                    radiusPx = btnSize / 2f
                )
                layoutParams = LayoutParams(btnSize, btnSize).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    marginStart = ThemePalette.dp(context, 4f)
                    marginEnd = ThemePalette.dp(context, 4f)
                }
                setOnClickListener { onOpenDrawer?.invoke() }
            }
            drawerButton = btn
            addView(btn)
        } else {
            drawerButton = null
        }
    }
}
