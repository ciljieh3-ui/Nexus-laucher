package com.example.core

import android.app.Activity
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class RuntimeTelemetry(
    val startupLatencyMs: Long,
    val totalPssMb: Float,
    val nativeHeapMb: Float,
    val jvmHeapUsedMb: Float,
    val totalFramesMeasured: Int,
    val jankyFramesCount: Int,
    val jankPercent: Float
)

/**
 * Proof-based runtime telemetry using Android Debug.MemoryInfo, SystemClock,
 * and Window.OnFrameMetricsAvailableListener (zero fake numbers).
 */
object PerformanceMonitor {

    private val appStartElapsedMs = AtomicLong(SystemClock.elapsedRealtime())
    private val firstFrameLatencyMs = AtomicLong(-1L)

    private val totalFrames = AtomicInteger(0)
    private val jankyFrames = AtomicInteger(0)

    private var activeListener: Window.OnFrameMetricsAvailableListener? = null

    fun markAppProcessStart() {
        appStartElapsedMs.compareAndSet(0L, SystemClock.elapsedRealtime())
    }

    fun markFirstFrameDrawn() {
        val elapsed = SystemClock.elapsedRealtime() - appStartElapsedMs.get()
        firstFrameLatencyMs.compareAndSet(-1L, elapsed.coerceAtLeast(1L))
    }

    fun attachFrameMonitor(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        if (activeListener != null) return
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropCountSinceLast ->
            val totalDurationNs = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            totalFrames.incrementAndGet()
            // 16.67ms threshold for 60Hz frame budget
            if (totalDurationNs > 16_670_000L || dropCountSinceLast > 0) {
                jankyFrames.incrementAndGet()
            }
        }
        activeListener = listener
        try {
            activity.window.addOnFrameMetricsAvailableListener(
                listener,
                Handler(Looper.getMainLooper())
            )
        } catch (_: Exception) {
            activeListener = null
        }
    }

    fun detachFrameMonitor(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val listener = activeListener ?: return
        activeListener = null
        try {
            activity.window.removeOnFrameMetricsAvailableListener(listener)
        } catch (_: Exception) {
        }
    }

    fun snapshot(): RuntimeTelemetry {
        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)
        val pssMb = memInfo.totalPss / 1024f
        val nativeMb = Debug.getNativeHeapAllocatedSize() / (1024f * 1024f)

        val runtime = Runtime.getRuntime()
        val jvmUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)

        val frames = totalFrames.get()
        val jank = jankyFrames.get()
        val jankPct = if (frames > 0) (jank * 100f) / frames else 0f

        return RuntimeTelemetry(
            startupLatencyMs = firstFrameLatencyMs.get().coerceAtLeast(0L),
            totalPssMb = pssMb,
            nativeHeapMb = nativeMb,
            jvmHeapUsedMb = jvmUsedMb,
            totalFramesMeasured = frames,
            jankyFramesCount = jank,
            jankPercent = jankPct
        )
    }
}
