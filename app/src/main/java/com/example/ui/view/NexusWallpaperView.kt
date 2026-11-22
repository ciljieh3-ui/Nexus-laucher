package com.example.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import com.example.model.WallpaperMode
import kotlin.math.max

/**
 * Custom Canvas Wallpaper renderer with zero allocations inside onDraw().
 * Supports transparent System Wallpaper, 3 procedural gradient/arc wallpapers (0 KB APK cost),
 * and sampled custom gallery bitmaps with adjustable dim overlay.
 */
class NexusWallpaperView(context: Context) : View(context) {

    private var mode: WallpaperMode = WallpaperMode.BUILTIN_OBSIDIAN
    private var dimAlpha: Float = 0.12f
    private var customBitmap: Bitmap? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint()
    private val arcPath = Path()
    private val drawMatrix = Matrix()

    fun configure(nextMode: WallpaperMode, nextDim: Float, nextBitmap: Bitmap?) {
        val modeChanged = mode != nextMode
        val bmpChanged = customBitmap !== nextBitmap
        mode = nextMode
        dimAlpha = nextDim.coerceIn(0f, 0.7f)
        customBitmap = nextBitmap

        if (mode == WallpaperMode.SYSTEM && dimAlpha < 0.01f) {
            visibility = GONE
        } else {
            visibility = VISIBLE
            if (modeChanged || bmpChanged) {
                rebuildShaders(width, height)
            }
            dimPaint.color = Color.argb((dimAlpha * 255f).toInt().coerceIn(0, 200), 0, 0, 0)
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShaders(w, h)
    }

    private fun rebuildShaders(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val wf = w.toFloat()
        val hf = h.toFloat()

        when (mode) {
            WallpaperMode.BUILTIN_OBSIDIAN -> {
                bgPaint.shader = LinearGradient(
                    0f, 0f, wf * 0.4f, hf,
                    intArrayOf(0xFF060B14.toInt(), 0xFF0E1B30.toInt(), 0xFF091322.toInt()),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
                accentPaint.shader = RadialGradient(
                    wf * 0.52f, hf * 0.84f, max(wf, hf) * 0.55f,
                    intArrayOf(0x5538BDF8, 0x1814B8A6, 0x00000000),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
                arcPath.reset()
                arcPath.moveTo(0f, hf * 0.78f)
                arcPath.quadTo(wf * 0.5f, hf * 0.62f, wf, hf * 0.74f)
                arcPath.lineTo(wf, hf)
                arcPath.lineTo(0f, hf)
                arcPath.close()
            }
            WallpaperMode.BUILTIN_AURORA -> {
                bgPaint.shader = LinearGradient(
                    0f, 0f, wf * 0.3f, hf,
                    intArrayOf(0xFF131B36.toInt(), 0xFF0F3B47.toInt(), 0xFF28203B.toInt()),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
                accentPaint.shader = RadialGradient(
                    wf * 0.75f, hf * 0.72f, max(wf, hf) * 0.6f,
                    intArrayOf(0x66F59E0B, 0x332DD4BF, 0x00000000),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                arcPath.reset()
                arcPath.moveTo(0f, hf * 0.68f)
                arcPath.cubicTo(wf * 0.35f, hf * 0.58f, wf * 0.65f, hf * 0.80f, wf, hf * 0.64f)
                arcPath.lineTo(wf, hf)
                arcPath.lineTo(0f, hf)
                arcPath.close()
            }
            WallpaperMode.BUILTIN_DUNE -> {
                bgPaint.shader = LinearGradient(
                    0f, 0f, 0f, hf,
                    intArrayOf(0xFF36312C.toInt(), 0xFF7B6652.toInt(), 0xFFC7A889.toInt()),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
                accentPaint.shader = LinearGradient(
                    0f, hf * 0.60f, wf, hf,
                    intArrayOf(0x44FDE68A, 0x66574435),
                    null,
                    Shader.TileMode.CLAMP
                )
                arcPath.reset()
                arcPath.moveTo(0f, hf * 0.70f)
                arcPath.quadTo(wf * 0.42f, hf * 0.58f, wf, hf * 0.75f)
                arcPath.lineTo(wf, hf)
                arcPath.lineTo(0f, hf)
                arcPath.close()
            }
            WallpaperMode.CUSTOM_FILE -> {
                val bmp = customBitmap
                if (bmp != null && !bmp.isRecycled) {
                    val scale = max(wf / bmp.width, hf / bmp.height)
                    val dx = (wf - bmp.width * scale) * 0.5f
                    val dy = (hf - bmp.height * scale) * 0.5f
                    drawMatrix.reset()
                    drawMatrix.setScale(scale, scale)
                    drawMatrix.postTranslate(dx, dy)
                }
            }
            WallpaperMode.SYSTEM -> {
                bgPaint.shader = null
                accentPaint.shader = null
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        when (mode) {
            WallpaperMode.SYSTEM -> Unit
            WallpaperMode.BUILTIN_OBSIDIAN,
            WallpaperMode.BUILTIN_AURORA,
            WallpaperMode.BUILTIN_DUNE -> {
                canvas.drawRect(0f, 0f, w, h, bgPaint)
                canvas.drawPath(arcPath, accentPaint)
            }
            WallpaperMode.CUSTOM_FILE -> {
                val bmp = customBitmap
                if (bmp != null && !bmp.isRecycled) {
                    canvas.drawBitmap(bmp, drawMatrix, bitmapPaint)
                } else {
                    canvas.drawColor(0xFF0A101C.toInt())
                }
            }
        }

        if (dimAlpha >= 0.01f) {
            canvas.drawRect(0f, 0f, w, h, dimPaint)
        }
    }

    companion object {
        fun drawProceduralWallpaper(canvas: Canvas, w: Int, h: Int, mode: WallpaperMode) {
            val wf = w.toFloat()
            val hf = h.toFloat()
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val path = Path()

            when (mode) {
                WallpaperMode.BUILTIN_OBSIDIAN -> {
                    bgPaint.shader = LinearGradient(
                        0f, 0f, wf * 0.4f, hf,
                        intArrayOf(0xFF060B14.toInt(), 0xFF0E1B30.toInt(), 0xFF091322.toInt()),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    accentPaint.shader = RadialGradient(
                        wf * 0.52f, hf * 0.84f, max(wf, hf) * 0.55f,
                        intArrayOf(0x5538BDF8, 0x1814B8A6, 0x00000000),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    path.moveTo(0f, hf * 0.78f)
                    path.quadTo(wf * 0.5f, hf * 0.62f, wf, hf * 0.74f)
                }
                WallpaperMode.BUILTIN_AURORA -> {
                    bgPaint.shader = LinearGradient(
                        0f, 0f, wf * 0.3f, hf,
                        intArrayOf(0xFF131B36.toInt(), 0xFF0F3B47.toInt(), 0xFF28203B.toInt()),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    accentPaint.shader = RadialGradient(
                        wf * 0.75f, hf * 0.72f, max(wf, hf) * 0.6f,
                        intArrayOf(0x66F59E0B, 0x332DD4BF, 0x00000000),
                        floatArrayOf(0f, 0.5f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    path.moveTo(0f, hf * 0.68f)
                    path.cubicTo(wf * 0.35f, hf * 0.58f, wf * 0.65f, hf * 0.80f, wf, hf * 0.64f)
                }
                else -> {
                    bgPaint.shader = LinearGradient(
                        0f, 0f, 0f, hf,
                        intArrayOf(0xFF36312C.toInt(), 0xFF7B6652.toInt(), 0xFFC7A889.toInt()),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    accentPaint.shader = LinearGradient(
                        0f, hf * 0.60f, wf, hf,
                        intArrayOf(0x44FDE68A, 0x66574435),
                        null,
                        Shader.TileMode.CLAMP
                    )
                    path.moveTo(0f, hf * 0.70f)
                    path.quadTo(wf * 0.42f, hf * 0.58f, wf, hf * 0.75f)
                }
            }
            path.lineTo(wf, hf)
            path.lineTo(0f, hf)
            path.close()
            canvas.drawRect(0f, 0f, wf, hf, bgPaint)
            canvas.drawPath(path, accentPaint)
        }
    }
}
