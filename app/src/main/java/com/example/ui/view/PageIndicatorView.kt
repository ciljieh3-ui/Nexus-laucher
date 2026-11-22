package com.example.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * Lightweight Canvas page indicator dots (zero allocations in onDraw).
 */
class PageIndicatorView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private var pageCount = 1
    private var currentPage = 0

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }

    fun update(count: Int, current: Int) {
        val safeCount = count.coerceAtLeast(1)
        val safeCurrent = current.coerceIn(0, safeCount - 1)
        if (pageCount != safeCount || currentPage != safeCurrent) {
            pageCount = safeCount
            currentPage = safeCurrent
            visibility = if (safeCount > 1) VISIBLE else GONE
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = (18f * density).toInt()
        setMeasuredDimension(resolveSize(widthMeasureSpec, widthMeasureSpec), h)
    }

    override fun onDraw(canvas: Canvas) {
        if (pageCount <= 1) return
        val spacing = 14f * density
        val totalW = (pageCount - 1) * spacing
        val startX = (width - totalW) / 2f
        val cy = height / 2f
        for (i in 0 until pageCount) {
            val active = i == currentPage
            val r = (if (active) 4f else 2.8f) * density
            canvas.drawCircle(startX + i * spacing, cy, r, if (active) activePaint else inactivePaint)
        }
    }
}
