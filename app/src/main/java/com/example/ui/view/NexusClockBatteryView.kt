package com.example.ui.view

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.core.ThemePalette
import com.example.model.BatteryStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight native Clock, Date & Canvas-drawn Battery badge.
 * Invalidates ONLY when minute tick or battery change events arrive.
 */
class NexusClockBatteryView(context: Context) : LinearLayout(context) {

    private val clockTextView: TextView
    private val dateTextView: TextView
    private val batteryPill: BatteryPillView

    private var lastTimeFormatted: String = ""
    private var lastDateFormatted: String = ""
    private var isCompact: Boolean = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val padH = ThemePalette.dp(context, 20f)
        val padV = ThemePalette.dp(context, 8f)
        setPadding(padH, padV, padH, padV)

        val leftCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        clockTextView = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 46f)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            fontFeatureSettings = "tnum"
            includeFontPadding = false
            setShadowLayer(6f, 0f, 2f, 0x55000000)
            contentDescription = "Jam digital"
            setOnClickListener { openSystemClock(context) }
        }

        dateTextView = TextView(context).apply {
            setTextColor(0xE0FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(ThemePalette.dp(context, 2f), ThemePalette.dp(context, 2f), 0, 0)
            setShadowLayer(4f, 0f, 1f, 0x44000000)
            contentDescription = "Tanggal hari ini"
            setOnClickListener { openSystemCalendar(context) }
        }

        leftCol.addView(clockTextView)
        leftCol.addView(dateTextView)

        batteryPill = BatteryPillView(context)

        addView(leftCol)
        addView(batteryPill)
    }

    fun setCompactMode(compact: Boolean) {
        if (isCompact == compact) return
        isCompact = compact
        clockTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 34f else 46f)
        dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 12f else 14f)
        val padH = ThemePalette.dp(context, if (compact) 12f else 20f)
        val padV = ThemePalette.dp(context, if (compact) 4f else 8f)
        setPadding(padH, padV, padH, padV)
    }

    fun update(
        timeMillis: Long,
        showSeconds: Boolean,
        use24HourOverride: Boolean?,
        showBattery: Boolean,
        batteryStatus: BatteryStatus
    ) {
        val is24Hour = use24HourOverride ?: DateFormat.is24HourFormat(context)
        val pattern = when {
            is24Hour && showSeconds -> "HH:mm:ss"
            is24Hour -> "HH:mm"
            showSeconds -> "hh:mm:ss a"
            else -> "hh:mm a"
        }
        val dateObj = Date(timeMillis)
        val formattedTime = SimpleDateFormat(pattern, Locale.getDefault()).format(dateObj)
        if (formattedTime != lastTimeFormatted) {
            lastTimeFormatted = formattedTime
            clockTextView.text = formattedTime
        }

        val formattedDate = SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(dateObj)
        if (formattedDate != lastDateFormatted) {
            lastDateFormatted = formattedDate
            dateTextView.text = formattedDate
        }

        batteryPill.visibility = if (showBattery) View.VISIBLE else View.GONE
        if (showBattery) {
            batteryPill.update(batteryStatus.level, batteryStatus.isCharging)
        }
    }

    private fun openSystemClock(ctx: Context) {
        try {
            ctx.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    private fun openSystemCalendar(ctx: Context) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private class BatteryPillView(context: Context) : View(context) {
        private var level = 100
        private var charging = false
        private val density = context.resources.displayMetrics.density

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55000000
            style = Paint.Style.FILL
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }
        private val iconOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * density
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12.5f * density
            typeface = Typeface.create("monospace", Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }

        private val pillRect = RectF()
        private val battRect = RectF()
        private val battFillRect = RectF()
        private val tipRect = RectF()

        fun update(nextLevel: Int, nextCharging: Boolean) {
            if (level != nextLevel || charging != nextCharging) {
                level = nextLevel.coerceIn(0, 100)
                charging = nextCharging
                fillPaint.color = when {
                    charging -> 0xFF34D399.toInt()
                    level <= 20 -> 0xFFF87171.toInt()
                    else -> Color.WHITE
                }
                contentDescription = "Baterai $level%"
                invalidate()
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = (74f * density).toInt()
            val h = (30f * density).toInt()
            setMeasuredDimension(w, h)
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val r = h / 2f
            pillRect.set(1f, 1f, w - 1f, h - 1f)
            canvas.drawRoundRect(pillRect, r, r, bgPaint)
            canvas.drawRoundRect(pillRect, r, r, borderPaint)

            // Mini horizontal battery drawn cleanly on Canvas
            val bx = 11f * density
            val by = (h - 10f * density) / 2f
            val bw = 16f * density
            val bh = 10f * density
            battRect.set(bx, by, bx + bw, by + bh)
            canvas.drawRoundRect(battRect, 2f * density, 2f * density, iconOutlinePaint)

            tipRect.set(bx + bw, by + 3f * density, bx + bw + 2f * density, by + bh - 3f * density)
            canvas.drawRect(tipRect, fillPaint)

            val pad = 2f * density
            val fillW = ((bw - pad * 2f) * (level / 100f)).coerceAtLeast(2f * density)
            battFillRect.set(bx + pad, by + pad, bx + pad + fillW, by + bh - pad)
            canvas.drawRect(battFillRect, fillPaint)

            val label = if (charging) "⚡$level%" else "$level%"
            val textX = bx + bw + 6f * density
            val textY = (h - (textPaint.ascent() + textPaint.descent())) / 2f
            canvas.drawText(label, textX, textY, textPaint)
        }
    }
}
