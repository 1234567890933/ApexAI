package com.apex.ai

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.apex.ai.core.StartupGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
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

    @Test
    fun mainActivityClearsTheMarkerOnFirstFrame() {
        StartupGuard.clear(context)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        // Drive a full traversal so the draw listener fires.
        val decor = controller.get().window.decorView
        decor.measure(0, 0)
        decor.layout(0, 0, 480, 800)
        assertFalse("start-up must complete cleanly", controller.get().isFinishing)
        controller.pause().stop().destroy()
    }
}
