package com.example.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import com.example.data.IconCache
import com.example.model.AppRef
import com.example.model.IconShapePreset
import com.example.model.IconSizePreset
import com.example.model.TextSizePreset
import java.util.concurrent.Future
import kotlin.math.min

/**
 * Ultra-lightweight custom View rendering app icon / 2x2 folder preview / shortcut badge /
 * notification dot / label directly on Canvas in a single View pass.
 * Zero child Views per tile, zero object allocations in onDraw().
 */
class NexusIconView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics.scaledDensity

    private var componentKey: String = ""
    private var primaryBitmap: Bitmap? = null
    private val folderBitmaps = arrayOfNulls<Bitmap>(4)
    private var isFolderMode: Boolean = false
    private var isShortcutMode: Boolean = false

    private var iconSizeDp: Int = IconSizePreset.MEDIUM.dpSize
    private var iconShape: IconShapePreset = IconShapePreset.SQUIRCLE
    private var badgeCount: Int = 0
    private var labelText: String = ""
    private var showLabel: Boolean = true
    private var labelSp: Int = TextSizePreset.MEDIUM.spSize
    private var labelColor: Int = Color.WHITE
    private var showRemoveCross: Boolean = false

    private var pendingLoad: Future<*>? = null
    private val folderPendingLoads = arrayOfNulls<Future<*>>(4)

    // Pre-allocated drawing structures (zero GC pressure in onDraw)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val folderBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x38FFFFFF
        style = Paint.Style.FILL
    }
    private val folderBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x48FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF38BDF8.toInt()
        style = Paint.Style.FILL
    }
    private val badgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0A101C.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0A101C.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = 8.5f * scaledDensity
    }
    private val removeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFEF4444.toInt()
        style = Paint.Style.FILL
    }
    private val removeCrossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 1.8f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val shortcutBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2DD4BF.toInt()
        style = Paint.Style.FILL
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setShadowLayer(3f, 0f, 1f, 0x77000000)
    }

    private val iconRectF = RectF()
    private val srcRect = Rect()
    private val dstRectF = RectF()
    private val miniRectF = RectF()
    private val clipPath = Path()
    private val miniClipPath = Path()
    private var ellipsizedLabel: String = ""
    private var lastEllipsizeWidth: Int = -1

    init {
        isClickable = true
        isFocusable = true
    }

    fun bindApp(
        packageName: String,
        activityName: String,
        label: String,
        iconCache: IconCache,
        iconSize: IconSizePreset,
        textSize: TextSizePreset,
        shape: IconShapePreset,
        badgeCount: Int,
        labelColor: Int = Color.WHITE,
        showLabel: Boolean = textSize != TextSizePreset.HIDDEN,
        isShortcut: Boolean = false,
        accentColor: Int = 0xFF38BDF8.toInt(),
        showRemoveCross: Boolean = false
    ) {
        cancelPendingLoads()
        isFolderMode = false
        isShortcutMode = isShortcut
        val nextKey = "$packageName/$activityName"
        componentKey = nextKey
        applyCommonStyle(label, iconSize, textSize, shape, badgeCount, labelColor, showLabel, accentColor, showRemoveCross)

        val cached = iconCache.getCached(nextKey)
        primaryBitmap = cached
        if (cached == null) {
            pendingLoad = iconCache.loadAsync(packageName, activityName) { loadedKey, bmp ->
                if (componentKey == loadedKey && !isFolderMode) {
                    primaryBitmap = bmp
                    invalidate()
                }
            }
        }
        invalidate()
    }

    fun bindFolder(
        folderId: String,
        title: String,
        apps: List<AppRef>,
        iconCache: IconCache,
        iconSize: IconSizePreset,
        textSize: TextSizePreset,
        shape: IconShapePreset,
        totalBadges: Int,
        labelColor: Int = Color.WHITE,
        showLabel: Boolean = textSize != TextSizePreset.HIDDEN,
        accentColor: Int = 0xFF38BDF8.toInt(),
        showRemoveCross: Boolean = false
    ) {
        cancelPendingLoads()
        isFolderMode = true
        isShortcutMode = false
        componentKey = folderId
        primaryBitmap = null
        applyCommonStyle(title, iconSize, textSize, shape, totalBadges, labelColor, showLabel, accentColor, showRemoveCross)

        for (i in 0 until 4) {
            val ref = apps.getOrNull(i)
            if (ref == null) {
                folderBitmaps[i] = null
            } else {
                val cached = iconCache.getCached(ref.componentKey)
                folderBitmaps[i] = cached
                if (cached == null) {
                    val slot = i
                    val expectedFolder = folderId
                    folderPendingLoads[slot] = iconCache.loadAsync(ref.packageName, ref.activityName) { _, bmp ->
                        if (componentKey == expectedFolder && isFolderMode) {
                            folderBitmaps[slot] = bmp
                            invalidate()
                        }
                    }
                }
            }
        }
        invalidate()
    }

    private fun applyCommonStyle(
        label: String,
        iconSize: IconSizePreset,
        textSize: TextSizePreset,
        shape: IconShapePreset,
        badgeCount: Int,
        labelColor: Int,
        showLabel: Boolean,
        accentColor: Int,
        showRemoveCross: Boolean
    ) {
        this.iconSizeDp = iconSize.dpSize
        this.iconShape = shape
        this.badgeCount = badgeCount
        this.labelText = label
        this.showLabel = showLabel && textSize != TextSizePreset.HIDDEN
        this.labelSp = textSize.spSize
        this.labelColor = labelColor
        this.showRemoveCross = showRemoveCross
        this.contentDescription = label
        this.badgeBgPaint.color = accentColor
        this.textPaint.color = labelColor
        if (labelColor == Color.WHITE) {
            this.textPaint.setShadowLayer(3f, 0f, 1f, 0x80000000.toInt())
        } else {
            this.textPaint.clearShadowLayer()
        }
        this.textPaint.textSize = (labelSp.coerceAtLeast(10)) * scaledDensity
        lastEllipsizeWidth = -1
        updateClipPath()
    }

    private fun cancelPendingLoads() {
        pendingLoad?.cancel(false)
        pendingLoad = null
        for (i in 0 until 4) {
            folderPendingLoads[i]?.cancel(false)
            folderPendingLoads[i] = null
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelPendingLoads()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        lastEllipsizeWidth = -1
        updateClipPath()
    }

    private fun updateClipPath() {
        if (width <= 0 || height <= 0) return
        val iconPx = min(iconSizeDp * density, width - 8f * density)
        val topPad = if (showLabel) 6f * density else (height - iconPx) / 2f
        val left = (width - iconPx) / 2f
        iconRectF.set(left, topPad, left + iconPx, topPad + iconPx)
        buildShapePath(clipPath, iconRectF, iconShape)
    }

    private fun buildShapePath(out: Path, rect: RectF, shape: IconShapePreset) {
        out.reset()
        val w = rect.width()
        val radii: FloatArray
        when (shape) {
            IconShapePreset.SYSTEM -> out.addRoundRect(rect, w * 0.22f, w * 0.22f, Path.Direction.CW)
            IconShapePreset.SQUIRCLE -> out.addRoundRect(rect, w * 0.30f, w * 0.30f, Path.Direction.CW)
            IconShapePreset.CIRCLE -> out.addOval(rect, Path.Direction.CW)
            IconShapePreset.ROUNDED_SQUARE -> out.addRoundRect(rect, w * 0.18f, w * 0.18f, Path.Direction.CW)
            IconShapePreset.TEARDROP -> {
                val r = w * 0.45f
                val s = w * 0.14f
                radii = floatArrayOf(r, r, r, r, s, s, r, r)
                out.addRoundRect(rect, radii, Path.Direction.CW)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minW = (48f * density).toInt()
        val iconPx = (iconSizeDp * density).toInt()
        val labelExtra = if (showLabel) (24f * density).toInt() else (12f * density).toInt()
        val desiredH = (iconPx + labelExtra).coerceAtLeast(minW)
        val w = resolveSize(maxOf(minW, iconPx + (20f * density).toInt()), widthMeasureSpec)
        val h = resolveSize(desiredH, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return

        if (isFolderMode) {
            canvas.drawPath(clipPath, folderBgPaint)
            canvas.drawPath(clipPath, folderBorderPaint)
            val pad = iconRectF.width() * 0.13f
            val gap = iconRectF.width() * 0.06f
            val miniSize = (iconRectF.width() - pad * 2f - gap) / 2f
            for (i in 0 until 4) {
                val bmp = folderBitmaps[i] ?: continue
                if (bmp.isRecycled) continue
                val row = i / 2
                val col = i % 2
                val x = iconRectF.left + pad + col * (miniSize + gap)
                val y = iconRectF.top + pad + row * (miniSize + gap)
                miniRectF.set(x, y, x + miniSize, y + miniSize)
                buildShapePath(miniClipPath, miniRectF, iconShape)
                canvas.save()
                canvas.clipPath(miniClipPath)
                srcRect.set(0, 0, bmp.width, bmp.height)
                canvas.drawBitmap(bmp, srcRect, miniRectF, bitmapPaint)
                canvas.restore()
            }
        } else {
            val bmp = primaryBitmap
            if (bmp != null && !bmp.isRecycled) {
                canvas.save()
                canvas.clipPath(clipPath)
                srcRect.set(0, 0, bmp.width, bmp.height)
                dstRectF.set(iconRectF)
                canvas.drawBitmap(bmp, srcRect, dstRectF, bitmapPaint)
                canvas.restore()
            } else {
                canvas.drawPath(clipPath, folderBgPaint)
            }
        }

        // Shortcut indicator at bottom-end
        if (isShortcutMode) {
            val sr = 6.5f * density
            val cx = iconRectF.right - sr * 0.6f
            val cy = iconRectF.bottom - sr * 0.6f
            canvas.drawCircle(cx, cy, sr, shortcutBadgePaint)
            canvas.drawCircle(cx, cy, sr, badgeStrokePaint)
        }

        // Notification dot badge at top-end
        if (badgeCount > 0 && !showRemoveCross) {
            val br = (if (badgeCount > 9) 8.5f else 7.2f) * density
            val cx = iconRectF.right - 2f * density
            val cy = iconRectF.top + 2f * density
            canvas.drawCircle(cx, cy, br, badgeBgPaint)
            canvas.drawCircle(cx, cy, br, badgeStrokePaint)
            val txt = if (badgeCount > 99) "99" else badgeCount.toString()
            val ty = cy - (badgeTextPaint.ascent() + badgeTextPaint.descent()) / 2f
            canvas.drawText(txt, cx, ty, badgeTextPaint)
        }

        // Edit mode remove corner badge
        if (showRemoveCross) {
            val rr = 9f * density
            val cx = iconRectF.right
            val cy = iconRectF.top
            canvas.drawCircle(cx, cy, rr, removeBgPaint)
            val off = 3.5f * density
            canvas.drawLine(cx - off, cy - off, cx + off, cy + off, removeCrossPaint)
            canvas.drawLine(cx - off, cy + off, cx + off, cy - off, removeCrossPaint)
        }

        // Label drawn directly on Canvas
        if (showLabel && labelText.isNotEmpty()) {
            val availW = (width - 8f * density).coerceAtLeast(10f)
            if (lastEllipsizeWidth != width) {
                lastEllipsizeWidth = width
                ellipsizedLabel = TextUtils.ellipsize(
                    labelText,
                    textPaint,
                    availW,
                    TextUtils.TruncateAt.END
                ).toString()
            }
            val textY = iconRectF.bottom + 14f * density
            canvas.drawText(ellipsizedLabel, width / 2f, textY, textPaint)
        }
    }
}
