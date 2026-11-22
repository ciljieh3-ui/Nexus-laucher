package com.example.core

import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import com.example.model.ThemeMode

data class NexusColors(
    val isDark: Boolean,
    val background: Int,
    val surface: Int,
    val surfaceElevated: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val accent: Int,
    val onAccent: Int,
    val divider: Int,
    val danger: Int,
    val success: Int
)

/**
 * Pure Android SDK color, dimension, and drawable helpers (zero Compose/Material library overhead).
 */
object ThemePalette {

    fun resolve(context: Context, mode: ThemeMode): NexusColors {
        val sysNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val isDark = when (mode) {
            ThemeMode.SYSTEM -> sysNight
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

        val dynamicAccent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                context.getColor(
                    if (isDark) android.R.color.system_accent1_200
                    else android.R.color.system_accent1_600
                )
            } catch (_: Exception) {
                if (isDark) 0xFF38BDF8.toInt() else 0xFF0284C7.toInt()
            }
        } else {
            if (isDark) 0xFF38BDF8.toInt() else 0xFF0284C7.toInt()
        }

        return if (isDark) {
            NexusColors(
                isDark = true,
                background = 0xFF0A101C.toInt(),
                surface = 0xFF121B2E.toInt(),
                surfaceElevated = 0xFF1A263E.toInt(),
                textPrimary = 0xFFF1F5F9.toInt(),
                textSecondary = 0xFF94A3B8.toInt(),
                accent = dynamicAccent,
                onAccent = 0xFF08101C.toInt(),
                divider = 0xFF25344E.toInt(),
                danger = 0xFFEF4444.toInt(),
                success = 0xFF10B981.toInt()
            )
        } else {
            NexusColors(
                isDark = false,
                background = 0xFFF4F7FB.toInt(),
                surface = 0xFFFFFFFF.toInt(),
                surfaceElevated = 0xFFEAF0F8.toInt(),
                textPrimary = 0xFF0F172A.toInt(),
                textSecondary = 0xFF475569.toInt(),
                accent = dynamicAccent,
                onAccent = Color.WHITE,
                divider = 0xFFD6E0EC.toInt(),
                danger = 0xFFDC2626.toInt(),
                success = 0xFF059669.toInt()
            )
        }
    }

    fun dp(context: Context, value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics
        ).toInt()
    }

    fun dpF(context: Context, value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics
        )
    }

    fun roundedCard(
        fillColor: Int,
        radiusPx: Float,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidthPx: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(fillColor)
            if (strokeWidthPx > 0) {
                setStroke(strokeWidthPx, strokeColor)
            }
        }
    }

    fun rippleRounded(
        fillColor: Int,
        rippleColor: Int,
        radiusPx: Float,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidthPx: Int = 0
    ): RippleDrawable {
        val content = roundedCard(fillColor, radiusPx, strokeColor, strokeWidthPx)
        val mask = roundedCard(Color.WHITE, radiusPx)
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }
}
