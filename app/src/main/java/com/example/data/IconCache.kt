package com.example.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.LruCache
import com.example.core.DeviceCapabilities
import com.example.core.DeviceProfile
import com.example.model.PerformanceMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Pure Android Bitmap LruCache with adaptive capacity and worker concurrency.
 * Zero Compose or Coroutines dependencies.
 */
class IconCache(private val context: Context) {

    private var capabilities: DeviceCapabilities = DeviceProfile.probe(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var cache: LruCache<String, Bitmap> = createLruCache(capabilities.iconCacheMaxKb)

    @Volatile
    private var executor: ExecutorService = Executors.newFixedThreadPool(capabilities.concurrentWorkers)

    @Volatile
    private var targetPx: Int = capabilities.iconRenderPx

    @Volatile
    private var hitCount: Int = 0

    @Volatile
    private var missCount: Int = 0

    private fun createLruCache(maxKb: Int): LruCache<String, Bitmap> {
        return object : LruCache<String, Bitmap>(maxKb.coerceAtLeast(2048)) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return (value.byteCount / 1024).coerceAtLeast(1)
            }
        }
    }

    fun refreshAdaptiveProfile(mode: PerformanceMode) {
        val nextCaps = DeviceProfile.probe(context)
        val prevWorkers = capabilities.concurrentWorkers
        capabilities = nextCaps
        targetPx = DeviceProfile.resolveEffectiveIconPx(nextCaps, mode)

        if (nextCaps.concurrentWorkers != prevWorkers) {
            val old = executor
            executor = Executors.newFixedThreadPool(nextCaps.concurrentWorkers)
            old.shutdown()
        }
    }

    fun getCached(componentKey: String): Bitmap? {
        val bmp = cache.get(componentKey)
        if (bmp != null && !bmp.isRecycled) {
            hitCount++
            return bmp
        }
        return null
    }

    fun loadAsync(
        packageName: String,
        activityName: String,
        onLoaded: (String, Bitmap?) -> Unit
    ): Future<*>? {
        val key = "$packageName/$activityName"
        getCached(key)?.let { cached ->
            onLoaded(key, cached)
            return null
        }
        missCount++
        return try {
            executor.submit {
                val bmp = cache.get(key) ?: run {
                    val drawable = resolveDrawable(packageName, activityName) ?: return@run null
                    val raster = drawableToBitmap(drawable, targetPx)
                    cache.put(key, raster)
                    raster
                }
                mainHandler.post {
                    onLoaded(key, bmp)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun removePackage(packageName: String) {
        val snapshot = cache.snapshot()
        snapshot.keys.forEach { key ->
            if (key.startsWith("$packageName/")) {
                cache.remove(key)
            }
        }
    }

    fun clear() {
        cache.evictAll()
    }

    fun trimToHalf() {
        cache.trimToSize(cache.size() / 2)
    }

    fun entryCount(): Int = cache.snapshot().size

    fun currentCacheSizeKb(): Int = cache.size()

    fun maxCacheSizeKb(): Int = cache.maxSize()

    fun hitRatioPercent(): Int {
        val total = hitCount + missCount
        return if (total > 0) (hitCount * 100) / total else 100
    }

    private fun resolveDrawable(packageName: String, activityName: String): Drawable? {
        return try {
            if (activityName.isNotEmpty()) {
                val launcherApps =
                    context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                val list = launcherApps?.getActivityList(packageName, Process.myUserHandle())
                val match = list?.firstOrNull { it.componentName.className == activityName }
                if (match != null) {
                    return match.getBadgedIcon(0)
                }
                context.packageManager.getActivityIcon(ComponentName(packageName, activityName))
            } else {
                context.packageManager.getApplicationIcon(packageName)
            }
        } catch (_: Exception) {
            try {
                context.packageManager.getApplicationIcon(packageName)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val safeSize = sizePx.coerceIn(48, 256)
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val src = drawable.bitmap
            if (src.width <= safeSize && src.height <= safeSize && src.config == Bitmap.Config.ARGB_8888) {
                return src
            }
            return Bitmap.createScaledBitmap(src, safeSize, safeSize, true)
        }
        val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, safeSize, safeSize)
        drawable.draw(canvas)
        return bitmap
    }
}
