package com.apex.ai

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.apex.ai.core.StartupGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The marker is what turns a silent start-up death into a recovery screen.
 * 1.4 crashed inside the draw listener that clears it, so this pins the contract.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StartupGuardTest {

    private val context: Application
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun markerLifecycle() {
        StartupGuard.clear(context)
        assertFalse(StartupGuard.crashedLastLaunch(context))

        StartupGuard.begin(context)
        assertTrue("a start that never finishes must be detectable",
            StartupGuard.crashedLastLaunch(context))

        StartupGuard.clear(context)
        assertFalse(StartupGuard.crashedLastLaunch(context))
    }

    /**
     * Actually draws the window: every custom view runs its onDraw, which is
     * where the 1.4 crash lived. A shader or listener mistake fails here.
     */
    @Test
    fun mainActivitySurvivesAFullTraversalAndDraw() {
        StartupGuard.clear(context)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val decor = controller.get().window.decorView

        val width = View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
        decor.measure(width, height)
        decor.layout(0, 0, 480, 800)

        val bitmap = Bitmap.createBitmap(480, 800, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))

        assertFalse("start-up must complete cleanly", controller.get().isFinishing)
        controller.pause().stop().destroy()
    }
}
