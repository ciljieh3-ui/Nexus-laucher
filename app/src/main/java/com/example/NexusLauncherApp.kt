package com.example

import android.app.Application
import com.example.core.PerformanceMonitor
import com.example.data.AppRepository
import com.example.data.IconCache
import com.example.data.LauncherStorage
import com.example.system.SystemIntegrationHelper
import com.example.viewmodel.LauncherController

/**
 * Lightweight Application singleton container with zero DI framework overhead.
 */
class NexusLauncherApp : Application() {

    val storage: LauncherStorage by lazy { LauncherStorage(this) }
    val iconCache: IconCache by lazy { IconCache(this) }
    val appRepository: AppRepository by lazy { AppRepository(this, iconCache, storage) }
    val systemHelper: SystemIntegrationHelper by lazy { SystemIntegrationHelper(this) }
    val controller: LauncherController by lazy {
        LauncherController(this, storage, iconCache, appRepository, systemHelper)
    }

    override fun onCreate() {
        super.onCreate()
        PerformanceMonitor.markAppProcessStart()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        iconCache.clear()
        controller.releaseTransientWallpaperCache()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW || level >= TRIM_MEMORY_UI_HIDDEN) {
            iconCache.trimToHalf()
        }
    }
}
