package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.LauncherStorage
import com.example.model.DockItem
import com.example.model.DrawerViewMode
import com.example.model.HomeItem
import com.example.model.LauncherSettings
import com.example.model.PerformanceMode
import com.example.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LauncherStorageTest {

    private lateinit var storage: LauncherStorage

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        storage = LauncherStorage(context)
        storage.resetAll()
    }

    @Test
    fun `save and load settings round-trip`() {
        val custom = LauncherSettings(
            themeMode = ThemeMode.DARK,
            performanceMode = PerformanceMode.ULTRA_BATTERY_SAVER,
            homeColumns = 5,
            homeRows = 6,
            drawerViewMode = DrawerViewMode.LIST,
            hiddenComponentKeys = setOf("com.test/com.test.Main")
        )
        storage.saveSettings(custom)
        val loaded = storage.loadSettings()
        assertEquals(ThemeMode.DARK, loaded.themeMode)
        assertEquals(PerformanceMode.ULTRA_BATTERY_SAVER, loaded.performanceMode)
        assertEquals(5, loaded.homeColumns)
        assertEquals(6, loaded.homeRows)
        assertEquals(DrawerViewMode.LIST, loaded.drawerViewMode)
        assertTrue("com.test/com.test.Main" in loaded.hiddenComponentKeys)
    }

    @Test
    fun `export and import backup preserves layout and skips stale widgets`() {
        val settings = LauncherSettings(themeMode = ThemeMode.LIGHT, dockTransparency = 0.5f)
        val home = listOf(
            HomeItem.AppItem("a1", "com.foo", "com.foo.Main", "Foo", page = 0, row = 1, col = 2),
            HomeItem.WidgetItem("w1", 42, "com.w", "com.w.Prov", "Clock", page = 0, row = 0, col = 0)
        )
        val dock = listOf(
            DockItem.DockApp("d1", "com.bar", "com.bar.Main", "Bar")
        )

        val json = storage.exportBackupJson(settings, pageCount = 2, homeItems = home, dockItems = dock)
        assertEquals(1, storage.countWidgetsInBackupJson(json))

        val restored = storage.importBackupJson(json)
        assertNotNull(restored)
        val (restoredSettings, restoredLayout) = restored!!
        assertEquals(ThemeMode.LIGHT, restoredSettings.themeMode)
        assertEquals(2, restoredLayout.pageCount)
        assertEquals(1, restoredLayout.homeItems.size)
        assertEquals(1, restoredLayout.dockItems.size)
    }
}
