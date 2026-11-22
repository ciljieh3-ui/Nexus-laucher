package com.example.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.example.core.NexusColors
import com.example.core.ThemePalette
import com.example.model.IconShapePreset
import com.example.model.LauncherSettings
import com.example.model.TextSizePreset
import com.example.model.WallpaperMode
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Custom Canvas live-preview widget for Settings > Appearance.
 * Draws the active wallpaper + dim overlay + sample icons (shape/size/label) + dock transparency.
 */
class SettingsAppearancePreviewView(context: Context) : View(context) {

    private var settings: LauncherSettings = LauncherSettings()
    private var customWallpaper: Bitmap? = null
    private var accentColor: Int = 0xFF38BDF8.toInt()

    private val density = context.resources.displayMetrics.density
    private val bgClip = Path()
    private val previewRect = RectF()
    private val dimPaint = Paint()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bmpSrc = Rect()
    private val bmpDst = RectF()
    private val dockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setShadowLayer(2f, 0f, 1f, 0x88000000.toInt())
    }
    private val shapePath = Path()
    private val iconRect = RectF()

    fun updatePreview(next: LauncherSettings, wallpaper: Bitmap?, accent: Int) {
        settings = next
        customWallpaper = wallpaper
        accentColor = accent
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (176f * density).toInt()
        setMeasuredDimension(w, resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val wf = width.toFloat()
        val hf = height.toFloat()
        if (wf <= 0f || hf <= 0f) return

        previewRect.set(0f, 0f, wf, hf)
        bgClip.reset()
        bgClip.addRoundRect(previewRect, 20f * density, 20f * density, Path.Direction.CW)

        canvas.save()
        canvas.clipPath(bgClip)

        when (settings.wallpaperMode) {
            WallpaperMode.SYSTEM -> {
                canvas.drawColor(0xFF1E293B.toInt())
            }
            WallpaperMode.BUILTIN_OBSIDIAN,
            WallpaperMode.BUILTIN_AURORA,
            WallpaperMode.BUILTIN_DUNE -> {
                NexusWallpaperView.drawProceduralWallpaper(canvas, width, height, settings.wallpaperMode)
            }
            WallpaperMode.CUSTOM_FILE -> {
                val bmp = customWallpaper
                if (bmp != null && !bmp.isRecycled) {
                    val scale = max(wf / bmp.width, hf / bmp.height)
                    val dw = bmp.width * scale
                    val dh = bmp.height * scale
                    val dx = (wf - dw) * 0.5f
                    val dy = (hf - dh) * 0.5f
                    bmpSrc.set(0, 0, bmp.width, bmp.height)
                    bmpDst.set(dx, dy, dx + dw, dy + dh)
                    canvas.drawBitmap(bmp, bmpSrc, bmpDst, bitmapPaint)
                } else {
                    canvas.drawColor(0xFF0A101C.toInt())
                }
            }
        }

        if (settings.wallpaperDim > 0.01f) {
            dimPaint.color = Color.argb((settings.wallpaperDim * 255f).toInt().coerceIn(0, 200), 0, 0, 0)
            canvas.drawRect(previewRect, dimPaint)
        }

        // Miniature clock
        textPaint.textSize = 22f * density
        textPaint.color = Color.WHITE
        canvas.drawText("09:41", wf * 0.5f, 36f * density, textPaint)

        // 4 sample shaped icons
        val iconPx = settings.iconSize.dpSize * 0.64f * density
        val cols = 4
        val totalSpan = wf - 40f * density
        val step = totalSpan / cols
        val colors = intArrayOf(accentColor, 0xFF2DD4BF.toInt(), 0xFFF59E0B.toInt(), 0xFFA855F7.toInt())
        val labels = arrayOf("Kamera", "Pesan", "Musik", "Catatan")

        for (i in 0 until cols) {
            val cx = 20f * density + step * (i + 0.5f)
            val top = 56f * density
            iconRect.set(cx - iconPx * 0.5f, top, cx + iconPx * 0.5f, top + iconPx)
            buildPreviewShapePath(shapePath, iconRect, settings.iconShape)
            iconPaint.color = colors[i]
            canvas.drawPath(shapePath, iconPaint)

            iconInnerPaint.color = 0x55FFFFFF
            canvas.drawCircle(cx, top + iconPx * 0.5f, iconPx * 0.18f, iconInnerPaint)

            if (settings.textSize != TextSizePreset.HIDDEN) {
                textPaint.textSize = (settings.textSize.spSize * 0.78f) * density
                canvas.drawText(labels[i], cx, top + iconPx + 14f * density, textPaint)
            }
        }

        // Miniature dock
        if (settings.showDock) {
            val dockH = 34f * density
            val dockTop = hf - dockH - 10f * density
            dockPaint.color = Color.argb(
                (settings.dockTransparency.coerceIn(0.05f, 0.9f) * 255f).toInt(),
                18,
                28,
                46
            )
            canvas.drawRoundRect(
                18f * density,
                dockTop,
                wf - 18f * density,
                dockTop + dockH,
                16f * density,
                16f * density,
                dockPaint
            )
            val dotR = 9f * density
            for (i in 0 until 4) {
                val dx = 48f * density + ((wf - 96f * density) / 3f) * i
                iconPaint.color = colors[(i + 1) % colors.size]
                canvas.drawCircle(dx, dockTop + dockH * 0.5f, dotR, iconPaint)
            }
        }

        canvas.restore()
    }

    private fun buildPreviewShapePath(out: Path, rect: RectF, shape: IconShapePreset) {
        out.reset()
        val w = rect.width()
        when (shape) {
            IconShapePreset.SYSTEM -> out.addRoundRect(rect, w * 0.22f, w * 0.22f, Path.Direction.CW)
            IconShapePreset.SQUIRCLE -> out.addRoundRect(rect, w * 0.30f, w * 0.30f, Path.Direction.CW)
            IconShapePreset.CIRCLE -> out.addOval(rect, Path.Direction.CW)
            IconShapePreset.ROUNDED_SQUARE -> out.addRoundRect(rect, w * 0.18f, w * 0.18f, Path.Direction.CW)
            IconShapePreset.TEARDROP -> {
                val r = w * 0.45f
                val s = w * 0.14f
                out.addRoundRect(rect, floatArrayOf(r, r, r, r, s, s, r, r), Path.Direction.CW)
            }
        }
    }
}

/**
 * Pure Android View factories for modular, lightweight Settings cards & controls.
 */
object SettingsUiHelper {

    fun makeSectionHeader(context: Context, title: String, colors: NexusColors): TextView {
        return TextView(context).apply {
            text = title
            setTextColor(colors.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(
                ThemePalette.dp(context, 4f),
                ThemePalette.dp(context, 14f),
                ThemePalette.dp(context, 4f),
                ThemePalette.dp(context, 6f)
            )
        }
    }

    fun makeCard(context: Context, colors: NexusColors): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = ThemePalette.dp(context, 14f)
            setPadding(p, p, p, p)
            background = ThemePalette.roundedCard(
                colors.surface,
                ThemePalette.dpF(context, 18f),
                colors.divider,
                ThemePalette.dp(context, 1f)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = ThemePalette.dp(context, 12f)
            }
        }
    }

    fun makeCategoryRow(
        context: Context,
        iconRes: Int,
        title: String,
        subtitle: String,
        colors: NexusColors,
        tagName: String,
        onClick: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            tag = tagName
            minimumHeight = ThemePalette.dp(context, 68f)
            val pH = ThemePalette.dp(context, 16f)
            val pV = ThemePalette.dp(context, 14f)
            setPadding(pH, pV, pH, pV)
            background = ThemePalette.rippleRounded(
                colors.surface,
                (colors.accent and 0x00FFFFFF) or 0x26000000,
                ThemePalette.dpF(context, 18f),
                colors.divider,
                ThemePalette.dp(context, 1f)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = ThemePalette.dp(context, 10f)
            }
            setOnClickListener { onClick() }
        }

        val badge = ImageView(context).apply {
            setImageResource(iconRes)
            setColorFilter(colors.accent, PorterDuff.Mode.SRC_IN)
            contentDescription = title
            val p = ThemePalette.dp(context, 9f)
            setPadding(p, p, p, p)
            background = ThemePalette.roundedCard(colors.surfaceElevated, ThemePalette.dpF(context, 12f))
            layoutParams = LinearLayout.LayoutParams(
                ThemePalette.dp(context, 42f),
                ThemePalette.dp(context, 42f)
            )
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = ThemePalette.dp(context, 14f)
            }
            addView(TextView(context).apply {
                text = title
                setTextColor(colors.textPrimary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
            addView(TextView(context).apply {
                text = subtitle
                setTextColor(colors.textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            })
        }

        row.addView(badge)
        row.addView(textCol)
        return row
    }

    fun makeSwitchRow(
        context: Context,
        title: String,
        subtitle: String,
        checked: Boolean,
        colors: NexusColors,
        tagName: String? = null,
        onToggle: (Boolean) -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ThemePalette.dp(context, 54f)
            setPadding(0, ThemePalette.dp(context, 6f), 0, ThemePalette.dp(context, 6f))
        }
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = ThemePalette.dp(context, 12f)
            }
            addView(TextView(context).apply {
                text = title
                setTextColor(colors.textPrimary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            })
            if (subtitle.isNotEmpty()) {
                addView(TextView(context).apply {
                    text = subtitle
                    setTextColor(colors.textSecondary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                })
            }
        }
        val sw = Switch(context).apply {
            isChecked = checked
            if (tagName != null) tag = tagName
            setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        }
        row.setOnClickListener { sw.toggle() }
        row.addView(col)
        row.addView(sw)
        return row
    }

    fun <T> makeChipSelectorRow(
        context: Context,
        title: String,
        options: List<Pair<String, T>>,
        selectedValue: T,
        colors: NexusColors,
        onSelect: (T) -> Unit
    ): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ThemePalette.dp(context, 8f), 0, ThemePalette.dp(context, 8f))
        }
        container.addView(TextView(context).apply {
            text = title
            setTextColor(colors.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            setPadding(0, 0, 0, ThemePalette.dp(context, 8f))
        })

        // Flow across rows (max 3 chips per row so all stay >=48dp touch height and readable)
        var currentRow = newChipRow(context)
        container.addView(currentRow)

        options.forEachIndexed { idx, (label, value) ->
            if (idx > 0 && idx % 3 == 0) {
                currentRow = newChipRow(context)
                container.addView(currentRow)
            }
            val selected = value == selectedValue
            val chip = TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                minimumHeight = ThemePalette.dp(context, 42f)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTextColor(if (selected) colors.onAccent else colors.textPrimary)
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                val pH = ThemePalette.dp(context, 10f)
                val pV = ThemePalette.dp(context, 8f)
                setPadding(pH, pV, pH, pV)
                background = ThemePalette.rippleRounded(
                    if (selected) colors.accent else colors.surfaceElevated,
                    0x33FFFFFF,
                    ThemePalette.dpF(context, 12f),
                    if (selected) colors.accent else colors.divider,
                    ThemePalette.dp(context, 1f)
                )
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginEnd = ThemePalette.dp(context, 6f)
                    bottomMargin = ThemePalette.dp(context, 6f)
                }
                setOnClickListener { onSelect(value) }
            }
            currentRow.addView(chip)
        }
        return container
    }

    private fun newChipRow(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    fun makeSliderRow(
        context: Context,
        title: String,
        initialPercent: Int,
        maxPercent: Int,
        colors: NexusColors,
        onProgressChanged: (Int) -> Unit
    ): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ThemePalette.dp(context, 8f), 0, ThemePalette.dp(context, 8f))
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleTv = TextView(context).apply {
            text = title
            setTextColor(colors.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valueTv = TextView(context).apply {
            text = "$initialPercent%"
            setTextColor(colors.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(titleTv)
        header.addView(valueTv)

        val seek = SeekBar(context).apply {
            max = maxPercent
            progress = initialPercent.coerceIn(0, maxPercent)
            minimumHeight = ThemePalette.dp(context, 44f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    valueTv.text = "$progress%"
                    if (fromUser) onProgressChanged(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(header)
        container.addView(seek)
        return container
    }

    fun makeActionButton(
        context: Context,
        label: String,
        colors: NexusColors,
        isDanger: Boolean = false,
        isPrimary: Boolean = false,
        tagName: String? = null,
        onClick: () -> Unit
    ): TextView {
        val bg = when {
            isDanger -> colors.danger
            isPrimary -> colors.accent
            else -> colors.surfaceElevated
        }
        val fg = when {
            isDanger -> Color.WHITE
            isPrimary -> colors.onAccent
            else -> colors.textPrimary
        }
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            if (tagName != null) tag = tagName
            minimumHeight = ThemePalette.dp(context, 48f)
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            val p = ThemePalette.dp(context, 12f)
            setPadding(p, p, p, p)
            background = ThemePalette.rippleRounded(
                bg,
                0x33FFFFFF,
                ThemePalette.dpF(context, 14f),
                if (isDanger || isPrimary) bg else colors.divider,
                ThemePalette.dp(context, 1f)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = ThemePalette.dp(context, 8f)
            }
            setOnClickListener { onClick() }
        }
    }

    fun makeDivider(context: Context, colors: NexusColors): View {
        return View(context).apply {
            setBackgroundColor(colors.divider)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ThemePalette.dp(context, 1f).coerceAtLeast(1)
            ).apply {
                val m = ThemePalette.dp(context, 6f)
                setMargins(0, m, 0, m)
            }
        }
    }

    fun makeKeyValueRow(
        context: Context,
        key: String,
        value: String,
        colors: NexusColors
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, ThemePalette.dp(context, 4f), 0, ThemePalette.dp(context, 4f))
            addView(TextView(context).apply {
                text = key
                setTextColor(colors.textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = value
                setTextColor(colors.textPrimary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
            })
        }
    }
}
