package com.example.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.example.model.PerformanceMode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class DeviceTier {
    CONSTRAINED,
    STANDARD,
    HIGH
}

data class DeviceCapabilities(
    val tier: DeviceTier,
    val totalRamMb: Long,
    val isLowRamDevice: Boolean,
    val cpuCores: Int,
    val densityDpi: Int,
    val density: Float,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val isPowerSaveMode: Boolean,
    val iconCacheMaxKb: Int,
    val iconRenderPx: Int,
    val concurrentWorkers: Int,
    val defaultAnimDurationMs: Long,
    val enableTranslucentLayers: Boolean
)

/**
 * Universal Adaptive Device Engine.
 * Probes actual device RAM, CPU cores, display density, and battery saver state at runtime
 * without hardcoding any device brand, model, or fixed screen dimensions.
 */
object DeviceProfile {

    fun probe(context: Context): DeviceCapabilities {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val totalRamMb = (memInfo.totalMem / (1024L * 1024L)).coerceAtLeast(512L)
        val isLowRam = am?.isLowRamDevice == true
        val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val dm = context.resources.displayMetrics
        val density = dm.density.coerceAtLeast(1f)
        val densityDpi = dm.densityDpi

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = pm?.isPowerSaveMode == true

        val tier = when {
            isLowRam || totalRamMb <= 3200L -> DeviceTier.CONSTRAINED
            totalRamMb <= 6144L -> DeviceTier.STANDARD
            else -> DeviceTier.HIGH
        }

        val maxHeapKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt().coerceAtLeast(16 * 1024)
        val cacheBudgetKb = when (tier) {
            DeviceTier.CONSTRAINED -> min(maxHeapKb / 20, 6 * 1024)
            DeviceTier.STANDARD -> min(maxHeapKb / 16, 10 * 1024)
            DeviceTier.HIGH -> min(maxHeapKb / 12, 16 * 1024)
        }

        // Adapt icon raster resolution to screen density & memory tier
        val baseIconPx = (52f * density).roundToInt()
        val iconRenderPx = when (tier) {
            DeviceTier.CONSTRAINED -> baseIconPx.coerceIn(64, 96)
            DeviceTier.STANDARD -> baseIconPx.coerceIn(72, 128)
            DeviceTier.HIGH -> baseIconPx.coerceIn(84, 160)
        }

        val workers = when {
            isPowerSave || tier == DeviceTier.CONSTRAINED -> 1
            cpuCores <= 4 -> 2
            else -> min(3, max(2, cpuCores / 3))
        }

        val sysAnimScale = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (_: Exception) {
            1f
        }

        val animMs = when {
            isPowerSave || sysAnimScale == 0f -> 0L
            tier == DeviceTier.CONSTRAINED -> 120L
            tier == DeviceTier.STANDARD -> 170L
            else -> 210L
        }

        return DeviceCapabilities(
            tier = tier,
            totalRamMb = totalRamMb,
            isLowRamDevice = isLowRam,
            cpuCores = cpuCores,
            densityDpi = densityDpi,
            density = density,
            screenWidthPx = dm.widthPixels,
            screenHeightPx = dm.heightPixels,
            isPowerSaveMode = isPowerSave,
            iconCacheMaxKb = cacheBudgetKb,
            iconRenderPx = iconRenderPx,
            concurrentWorkers = workers,
            defaultAnimDurationMs = animMs,
            enableTranslucentLayers = !isPowerSave && !isLowRam
        )
    }

    fun resolveEffectiveAnimationMs(
        capabilities: DeviceCapabilities,
        mode: PerformanceMode
    ): Long {
        if (capabilities.isPowerSaveMode) return 0L
        return when (mode) {
            PerformanceMode.AUTO_ADAPTIVE -> capabilities.defaultAnimDurationMs
            PerformanceMode.ULTRA_BATTERY_SAVER -> 0L
            PerformanceMode.BALANCED -> min(170L, capabilities.defaultAnimDurationMs.coerceAtLeast(120L))
            PerformanceMode.SMOOTH -> 220L
        }
    }

    fun resolveEffectiveIconPx(
        capabilities: DeviceCapabilities,
        mode: PerformanceMode
    ): Int {
        return when (mode) {
            PerformanceMode.AUTO_ADAPTIVE -> capabilities.iconRenderPx
            PerformanceMode.ULTRA_BATTERY_SAVER -> (capabilities.iconRenderPx * 0.8f).roundToInt().coerceAtLeast(56)
            PerformanceMode.BALANCED -> capabilities.iconRenderPx
            PerformanceMode.SMOOTH -> (capabilities.iconRenderPx * 1.15f).roundToInt().coerceAtMost(192)
        }
    }

    fun sdkName(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
}
