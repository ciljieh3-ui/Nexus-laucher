package com.example.system

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.WallpaperManager
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.provider.Settings
import com.example.R
import com.example.model.DeviceStatus
import com.example.model.WallpaperMode
import com.example.ui.view.NexusWallpaperView

/**
 * Pure Android SDK system integration helper (RoleManager, DevicePolicyManager,
 * NotificationListener access, StatusBarManager, WallpaperManager).
 */
class SystemIntegrationHelper(private val context: Context) {

    fun getDeviceAdminComponent(): ComponentName =
        ComponentName(context, NexusDeviceAdminReceiver::class.java)

    fun isDefaultLauncher(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        return getCurrentDefaultHomePackage() == context.packageName
    }

    fun getCurrentDefaultHomePackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        val pkg = resolveInfo?.activityInfo?.packageName
        return if (pkg == "android") null else pkg
    }

    fun createRequestDefaultHomeIntent(): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return createSystemHomeSettingsIntent()
    }

    fun createSystemHomeSettingsIntent(): Intent {
        val homeIntent = Intent(Settings.ACTION_HOME_SETTINGS)
        return if (homeIntent.resolveActivity(context.packageManager) != null) {
            homeIntent
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    fun isNotificationAccessGranted(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val expected = ComponentName(context, NexusNotificationListenerService::class.java)
            .flattenToString()
        val shortExpected = ComponentName(context, NexusNotificationListenerService::class.java)
            .flattenToShortString()
        return enabledListeners.contains(expected) || enabledListeners.contains(shortExpected)
    }

    fun createNotificationAccessIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    fun isDeviceAdminActive(): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return false
        return dpm.isAdminActive(getDeviceAdminComponent())
    }

    fun createDeviceAdminActivationIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getDeviceAdminComponent())
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.device_admin_description)
            )
        }
    }

    fun removeDeviceAdmin() {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        if (dpm != null && dpm.isAdminActive(getDeviceAdminComponent())) {
            dpm.removeActiveAdmin(getDeviceAdminComponent())
        }
    }

    fun lockScreenNow(): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return false
        return if (dpm.isAdminActive(getDeviceAdminComponent())) {
            try {
                dpm.lockNow()
                true
            } catch (_: SecurityException) {
                false
            }
        } else {
            false
        }
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    fun expandNotificationShade(): Boolean {
        return try {
            val statusBarService = context.getSystemService("statusbar") ?: return false
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expandMethod = statusBarManager.getMethod("expandNotificationsPanel")
            expandMethod.invoke(statusBarService)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openSystemAction(actionIntent: String): Boolean {
        return try {
            val intent = Intent(actionIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openSystemWallpaperPicker(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SET_WALLPAPER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(
                Intent.createChooser(intent, "Pilih Wallpaper Sistem")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    fun applyWallpaperToSystemBlocking(mode: WallpaperMode, customBitmap: Bitmap?): Boolean {
        return try {
            val wm = WallpaperManager.getInstance(context)
            when (mode) {
                WallpaperMode.SYSTEM -> false
                WallpaperMode.CUSTOM_FILE -> {
                    if (customBitmap == null) return false
                    wm.setBitmap(customBitmap)
                    true
                }
                WallpaperMode.BUILTIN_OBSIDIAN,
                WallpaperMode.BUILTIN_AURORA,
                WallpaperMode.BUILTIN_DUNE -> {
                    val dm = context.resources.displayMetrics
                    val w = dm.widthPixels.coerceIn(720, 1440)
                    val h = dm.heightPixels.coerceIn(1280, 2560)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
                    val canvas = Canvas(bmp)
                    NexusWallpaperView.drawProceduralWallpaper(canvas, w, h, mode)
                    wm.setBitmap(bmp)
                    bmp.recycle()
                    true
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getDeviceStatus(): DeviceStatus {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024L * 1024L)
        val runtime = Runtime.getRuntime()
        val usedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)

        return DeviceStatus(
            isDefaultLauncher = isDefaultLauncher(),
            currentDefaultHomePackage = getCurrentDefaultHomePackage(),
            isNotificationAccessGranted = isNotificationAccessGranted(),
            isDeviceAdminActive = isDeviceAdminActive(),
            totalRamMb = totalRamMb,
            appHeapUsedMb = usedHeapMb
        )
    }
}
