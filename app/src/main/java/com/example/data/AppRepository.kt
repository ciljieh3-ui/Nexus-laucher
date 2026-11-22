package com.example.data

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import com.example.core.DeviceCapabilities
import com.example.core.DeviceProfile
import com.example.model.AppInfo
import com.example.model.AppShortcutItem
import com.example.model.PerformanceMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Zero-dependency repository querying real installed apps and LauncherApps shortcuts
 * via single-thread background executor + main Handler.
 */
class AppRepository(
    private val context: Context,
    private val iconCache: IconCache,
    private val storage: LauncherStorage
) {
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var apps: List<AppInfo> = emptyList()
        private set

    @Volatile
    var recentKeys: List<String> = storage.loadRecentComponentKeys()
        private set

    @Volatile
    var isLoading: Boolean = true
        private set

    fun reloadAppsAsync(onLoaded: (List<AppInfo>) -> Unit) {
        ioExecutor.execute {
            val loaded = queryInstalledApps()
            apps = loaded
            isLoading = false
            mainHandler.post { onLoaded(loaded) }
        }
    }

    fun onPackageRemoved(packageName: String) {
        iconCache.removePackage(packageName)
        apps = apps.filterNot { it.packageName == packageName }
        val updatedRecent = recentKeys.filterNot { it.startsWith("$packageName/") }
        if (updatedRecent != recentKeys) {
            recentKeys = updatedRecent
            storage.saveRecentComponentKeys(updatedRecent)
        }
    }

    private fun queryInstalledApps(): List<AppInfo> {
        val selfPackage = context.packageName
        val result = LinkedHashMap<String, AppInfo>()

        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        if (launcherApps != null) {
            try {
                val activities = launcherApps.getActivityList(null, Process.myUserHandle())
                for (activity in activities) {
                    val component = activity.componentName
                    if (component.packageName == selfPackage) continue
                    val key = "${component.packageName}/${component.className}"
                    result[key] = AppInfo(
                        packageName = component.packageName,
                        activityName = component.className,
                        label = activity.label?.toString()?.trim().orEmpty()
                            .ifEmpty { component.packageName }
                    )
                }
            } catch (_: Exception) {
            }
        }

        if (result.isEmpty()) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            for (info in resolved) {
                val pkg = info.activityInfo.packageName
                val cls = info.activityInfo.name
                if (pkg == selfPackage) continue
                val key = "$pkg/$cls"
                if (!result.containsKey(key)) {
                    result[key] = AppInfo(
                        packageName = pkg,
                        activityName = cls,
                        label = info.loadLabel(pm).toString().trim().ifEmpty { pkg }
                    )
                }
            }
        }

        return result.values.sortedBy { it.label.lowercase() }
    }

    fun getShortcutsForPackageAsync(
        packageName: String,
        onResult: (List<AppShortcutItem>) -> Unit
    ) {
        ioExecutor.execute {
            val items = queryShortcutsForPackage(packageName)
            mainHandler.post { onResult(items) }
        }
    }

    private fun queryShortcutsForPackage(packageName: String): List<AppShortcutItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return emptyList()
        val launcherApps =
            context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                ?: return emptyList()
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()

        return try {
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            }
            val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle())
                ?: return emptyList()
            shortcuts
                .filter { it.isEnabled }
                .sortedBy { it.rank }
                .take(5)
                .map { info ->
                    AppShortcutItem(
                        id = info.id,
                        packageName = info.`package`,
                        shortLabel = info.shortLabel?.toString() ?: info.id,
                        longLabel = info.longLabel?.toString()
                            ?: info.shortLabel?.toString()
                            ?: info.id,
                        rank = info.rank
                    )
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun searchShortcutsAsync(
        queryText: String,
        onResult: (List<AppShortcutItem>) -> Unit
    ) {
        if (queryText.trim().length < 2) {
            onResult(emptyList())
            return
        }
        ioExecutor.execute {
            val list = querySearchShortcuts(queryText)
            mainHandler.post { onResult(list) }
        }
    }

    private fun querySearchShortcuts(queryText: String): List<AppShortcutItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return emptyList()
        val launcherApps =
            context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                ?: return emptyList()
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()

        return try {
            val query = LauncherApps.ShortcutQuery().apply {
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            }
            val allShortcuts = launcherApps.getShortcuts(query, Process.myUserHandle())
                ?: return emptyList()
            val needle = queryText.trim().lowercase()
            allShortcuts
                .filter { info ->
                    info.isEnabled && (
                        info.shortLabel?.toString()?.lowercase()?.contains(needle) == true ||
                            info.longLabel?.toString()?.lowercase()?.contains(needle) == true
                        )
                }
                .take(6)
                .map { info ->
                    AppShortcutItem(
                        id = info.id,
                        packageName = info.`package`,
                        shortLabel = info.shortLabel?.toString() ?: info.id,
                        longLabel = info.longLabel?.toString()
                            ?: info.shortLabel?.toString()
                            ?: info.id,
                        rank = info.rank
                    )
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun launchApp(
        packageName: String,
        activityName: String,
        performanceMode: PerformanceMode,
        capabilities: DeviceCapabilities
    ): Boolean {
        return try {
            val component = ComponentName(packageName, activityName)
            recordRecentLaunch("$packageName/$activityName")
            val launcherApps =
                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            val animMs = DeviceProfile.resolveEffectiveAnimationMs(capabilities, performanceMode)
            val bundle = if (animMs == 0L) {
                ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
            } else {
                null
            }
            if (launcherApps != null) {
                launcherApps.startMainActivity(component, Process.myUserHandle(), null, bundle)
            } else {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    this.component = component
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                context.startActivity(intent, bundle)
            }
            true
        } catch (_: Exception) {
            try {
                val fallback = context.packageManager.getLaunchIntentForPackage(packageName)
                    ?: return false
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun launchShortcut(packageName: String, shortcutId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return false
        val launcherApps =
            context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return false
        return try {
            launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openAppInfo(packageName: String): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun uninstallApp(packageName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun recordRecentLaunch(componentKey: String) {
        val updated = listOf(componentKey) + recentKeys.filterNot { it == componentKey }
        val trimmed = updated.take(8)
        recentKeys = trimmed
        storage.saveRecentComponentKeys(trimmed)
    }
}
