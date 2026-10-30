package com.stockfileexplorer.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Verifies the launcher icon of the INSTALLED package exactly the way a
 * launcher loads it (ApplicationInfo.loadIcon), renders it under circle and
 * squircle masks on light and dark wallpapers, and captures the system
 * app-details screen, which shows the same system-composed icon.
 */
@RunWith(AndroidJUnit4::class)
class LauncherIconTest {

    private fun squirclePath(size: Float, radius: Float): Path {
        val p = Path()
        p.addRoundRect(RectF(0f, 0f, size, size), radius, radius, Path.Direction.CW)
        return p
    }

    private fun renderMasked(icon: android.graphics.drawable.Drawable, outPx: Int, circle: Boolean, dark: Boolean): Bitmap {
        val full = 432
        val composed = Bitmap.createBitmap(full, full, Bitmap.Config.ARGB_8888)
        val c = Canvas(composed)
        icon.setBounds(0, 0, full, full)
        icon.draw(c)
        val scaled = Bitmap.createScaledBitmap(composed, outPx, outPx, true)

        val masked = Bitmap.createBitmap(outPx, outPx, Bitmap.Config.ARGB_8888)
        val mc = Canvas(masked)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (circle) {
            mc.drawCircle(outPx / 2f, outPx / 2f, outPx / 2f, paint)
        } else {
            mc.drawPath(squirclePath(outPx.toFloat(), outPx * 0.42f), paint)
        }
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        mc.drawBitmap(scaled, 0f, 0f, paint)

        val wallpaper = if (dark) Color.rgb(36, 36, 44) else Color.rgb(245, 245, 245)
        val framed = Bitmap.createBitmap(outPx, outPx, Bitmap.Config.ARGB_8888)
        val fc = Canvas(framed)
        fc.drawColor(wallpaper)
        fc.drawBitmap(masked, 0f, 0f, null)
        return framed
    }

    private fun save(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun launcherIconRendersFromInstalledPackage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val pm = context.packageManager
        val pkg = context.packageName
        val suffix = InstrumentationRegistry.getArguments().getString("capture", "icon")

        val appInfo = pm.getApplicationInfo(pkg, 0)
        val icon = appInfo.loadIcon(pm) // exactly what launchers call
        assertNotNull("Launcher icon loads from installed package", icon)
        assertTrue(
            "Launcher icon is adaptive (expected on API 35), got $icon",
            icon is AdaptiveIconDrawable
        )

        // Guard against blank / miswired resources: the composed icon center
        // must be the artwork's orange.
        val probe = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        val pc = Canvas(probe)
        icon.setBounds(0, 0, 432, 432)
        icon.draw(pc)
        val bgProbe = probe.getPixel(216, 90) // inside squircle, above the folder
        assertTrue(
            "Icon background should be the artwork's orange gradient, got ${Integer.toHexString(bgProbe)}",
            Color.red(bgProbe) > 200 && Color.green(bgProbe) in 60..235 && Color.blue(bgProbe) < 150
        )
        val center = probe.getPixel(216, 216) // cream folder front
        assertTrue(
            "Icon center should be the bright folder, got ${Integer.toHexString(center)}",
            Color.red(center) > 220 && Color.green(center) > 180
        )

        save(renderMasked(icon, 192, circle = true, dark = false), File("/sdcard/Download/sfe-$suffix-circle-light.png"))
        save(renderMasked(icon, 192, circle = false, dark = false), File("/sdcard/Download/sfe-$suffix-squircle-light.png"))
        save(renderMasked(icon, 192, circle = true, dark = true), File("/sdcard/Download/sfe-$suffix-circle-dark.png"))
        save(renderMasked(icon, 192, circle = false, dark = true), File("/sdcard/Download/sfe-$suffix-squircle-dark.png"))
        save(renderMasked(icon, 48, circle = false, dark = false), File("/sdcard/Download/sfe-$suffix-48px.png"))
    }

    @Test
    fun systemAppDetailsShowsInstalledIcon() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val suffix = InstrumentationRegistry.getArguments().getString("capture", "icon")

        val device = UiDevice.getInstance(instrumentation)
        device.executeShellCommand(
            "am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:${context.packageName}"
        )
        assertTrue(
            "System app details opened",
            device.wait(Until.hasObject(By.pkg("com.android.settings").depth(0)), 15000)
        )
        device.wait(Until.hasObject(By.textContains("File Explorer")), 15000)
        device.takeScreenshot(File("/sdcard/Download/sfe-$suffix-app-details.png"))
        device.pressBack()
    }
}
