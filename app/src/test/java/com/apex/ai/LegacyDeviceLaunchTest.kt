package com.apex.ai

import com.apex.ai.core.AppContainer
import com.apex.ai.core.LocalVault
import androidx.test.core.app.ApplicationProvider
import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The same start-up path on the oldest OS the app claims to support.
 * Android 10 is the floor most budget phones in the field still run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class Android10LaunchTest {

    @Test
    fun mainActivityStartsOnAndroid10() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertFalse("MainActivity finished during startup", controller.get().isFinishing)
        controller.pause().stop().destroy()
    }

    @Test
    fun toolkitStartsOnAndroid10() {
        val controller = Robolectric.buildActivity(com.apex.ai.ui.ToolkitActivity::class.java).setup()
        assertFalse(controller.get().isFinishing)
        controller.pause().stop().destroy()
    }

    @Test
    fun vaultReadsNeverThrowOnAndroid10() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val vault = LocalVault(context)
        // Nothing stored yet: a read must return null, never throw.
        assertNull(vault.getApiKey("gemini"))
        assertNotNull(vault.selectedAgentId)
        assertTrue(vault.maxAgentSteps >= 1)

        // A round-trip through whichever store the device gave us.
        vault.saveApiKey("gemini", "test-key")
        assertEquals("test-key", vault.getApiKey("gemini"))
        vault.customSystemPrompt = "be terse"
        assertEquals("be terse", vault.customSystemPrompt)
        vault.clearVault()
    }
}
