package com.stockfileexplorer.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HomeTilesTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private fun node(tag: String) = device.wait(Until.findObject(By.res(tag)), 15000)
        ?: throw AssertionError("Missing $tag")
    private fun shot(name: String) {
        val dir = File(context.getExternalFilesDir(null), "home-evidence").apply { mkdirs() }
        val lang = context.resources.configuration.locales[0].language
        device.takeScreenshot(File(dir, "$lang-$name.png"))
        device.dumpWindowHierarchy(File(dir, "$lang-$name.xml"))
    }
    private fun launch() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        device.waitForIdle()
    }
    @Test fun tilesKeepTopIconsAndOpenExistingScreens() {
        launch()
        for (tag in listOf("home_favorites", "home_shortcuts", "home_search", "home_settings")) node(tag)
        val settings = node("home_settings_tile").visibleBounds
        val recent = node("home_recent_tile").visibleBounds
        assertTrue("Settings tile left of recent", settings.centerX() < recent.centerX())
        assertTrue("Tiles share row", kotlin.math.abs(settings.centerY() - recent.centerY()) < 8)
        shot("home")
        node("home_settings_tile").click()
        node("settings_back")
        shot("settings")
        device.pressBack()
        node("home_recent_tile").click()
        assertNotNull(device.wait(Until.findObject(By.text(context.getString(R.string.cat_recent))), 15000))
        shot("recent")
        device.pressBack()
        node("home_recent_tile")
        node("home_settings").click()
        node("settings_back")
        device.pressBack()
        node("home_settings_tile")
        shot("returned-home")
        device.findObject(By.text(context.getString(R.string.tab_library))).click()
        device.waitForIdle()
        assertFalse("Recent moved out of Library", device.hasObject(By.text(context.getString(R.string.cat_recent))))
        assertNotNull(device.wait(Until.findObject(By.text(context.getString(R.string.cat_apk))), 15000))
        shot("library-without-recent")
    }
}
