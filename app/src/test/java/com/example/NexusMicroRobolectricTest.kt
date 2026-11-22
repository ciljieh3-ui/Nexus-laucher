package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.example.core.DeviceProfile
import com.example.core.PerformanceMonitor
import com.example.model.PerformanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NexusMicroRobolectricTest {

    @Test
    fun `manifest declares HOME intent filter and omits INTERNET permission`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager

        val pkgInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val perms = pkgInfo.requestedPermissions?.toSet() ?: emptySet()
        assertFalse("Nexus Launcher must not request INTERNET", Manifest.permission.INTERNET in perms)
        assertTrue(Manifest.permission.QUERY_ALL_PACKAGES in perms)

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = pm.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        assertTrue(resolved.any { it.activityInfo.packageName == context.packageName })
    }

    @Test
    fun `universal adaptive device engine adapts without hardcoded device profiles`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val caps = DeviceProfile.probe(context)

        assertTrue(caps.totalRamMb >= 512L)
        assertTrue(caps.cpuCores >= 1)
        assertTrue(caps.iconCacheMaxKb >= 2048)
        assertTrue(caps.iconRenderPx in 48..256)

        assertEquals(0L, DeviceProfile.resolveEffectiveAnimationMs(caps, PerformanceMode.ULTRA_BATTERY_SAVER))
        assertTrue(DeviceProfile.resolveEffectiveIconPx(caps, PerformanceMode.ULTRA_BATTERY_SAVER) <= caps.iconRenderPx)
        assertTrue(DeviceProfile.resolveEffectiveIconPx(caps, PerformanceMode.SMOOTH) >= caps.iconRenderPx)
    }

    @Test
    fun `main launcher activity builds native home views and overlays cleanly`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val root = activity.window.decorView
        assertNotNull(root.findViewWithTag<View>("home_screen"))
        assertNotNull(root.findViewWithTag<View>("home_workspace"))
        assertNotNull(root.findViewWithTag<View>("home_search_bar"))
        assertNotNull(root.findViewWithTag<View>("home_dock"))

        val drawer = root.findViewWithTag<View>("app_drawer_screen")
        assertNotNull(drawer)
        assertEquals(View.GONE, drawer.visibility)

        // Tap drawer button inside dock or search bar to open smart search
        val searchPill = root.findViewWithTag<View>("home_search_bar")
        searchPill.performClick()
        val searchOverlay = root.findViewWithTag<View>("smart_search_overlay")
        assertNotNull(searchOverlay)
        assertEquals(View.VISIBLE, searchOverlay.visibility)

        val telemetry = PerformanceMonitor.snapshot()
        assertTrue(telemetry.totalPssMb >= 0f)
    }

    @Test
    fun `settings activity renders categories and opens appearance preview`() {
        val menuCtrl = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val menuRoot = menuCtrl.get().window.decorView

        assertNotNull(menuRoot.findViewWithTag<View>("cat_general"))
        assertNotNull(menuRoot.findViewWithTag<View>("cat_appearance"))
        assertNotNull(menuRoot.findViewWithTag<View>("cat_performance"))
        assertNotNull(menuRoot.findViewWithTag<View>("cat_backup"))
        assertNotNull(menuRoot.findViewWithTag<View>("cat_about"))

        menuRoot.findViewWithTag<View>("cat_performance").performClick()
        assertNotNull(menuRoot.findViewWithTag<View>("btn_clear_cache"))

        val intent = Intent(ApplicationProvider.getApplicationContext(), SettingsActivity::class.java).apply {
            putExtra(SettingsActivity.EXTRA_INITIAL_SECTION, SettingsActivity.SECTION_APPEARANCE)
        }
        val appCtrl = Robolectric.buildActivity(SettingsActivity::class.java, intent).setup()
        val appRoot = appCtrl.get().window.decorView
        assertNotNull(appRoot.findViewWithTag<View>("btn_pick_gallery_wallpaper"))
    }
}
