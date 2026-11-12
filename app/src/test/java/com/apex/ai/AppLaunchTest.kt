package com.apex.ai

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.apex.ai.core.AdvancedGate
import com.apex.ai.core.AppContainer
import com.apex.ai.ui.SettingsActivity
import com.apex.ai.ui.ToolkitActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real lifecycle tests, not mocks: Robolectric inflates the actual layouts with
 * the actual theme and drives create → start → resume → destroy. This is what
 * catches the "opens and immediately closes" class of bug before a user does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppLaunchTest {

    private val context: Application
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun mainActivitySurvivesTheFullLifecycle() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)
        assertFalse("MainActivity finished during startup", activity.isFinishing)
        controller.pause().stop().destroy()
    }

    @Test
    fun settingsActivityStarts() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        assertFalse(controller.get().isFinishing)
        controller.pause().stop().destroy()
    }

    @Test
    fun toolkitActivityStarts() {
        val controller = Robolectric.buildActivity(ToolkitActivity::class.java).setup()
        assertFalse(controller.get().isFinishing)
        controller.pause().stop().destroy()
    }

    @Test
    fun containerWiresTheWholeToolSuite() {
        val container = AppContainer.get(context)
        assertTrue("expected the full tool suite", container.tools.size() >= 25)
        val ids = container.tools.all().map { it.spec.id }
        assertEquals("tool ids must be unique", ids.size, ids.toSet().size)
        assertTrue(container.tools.all().all { it.spec.summary.isNotBlank() })
    }

    @Test
    fun everyAgentOnlyReferencesRealTools() {
        val container = AppContainer.get(context)
        val known = container.tools.all().map { it.spec.id }.toSet()
        container.agents.all.forEach { agent ->
            agent.toolIds.forEach { id ->
                assertTrue("agent '${agent.id}' references unknown tool '$id'", id in known)
            }
        }
    }

    @Test
    fun agentIdsAreUnique() {
        val container = AppContainer.get(context)
        val ids = container.agents.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue("expected the expanded roster", ids.size >= 15)
    }

    @Test
    fun advancedGateRejectsAWrongPasscode() {
        AdvancedGate.lock(context)
        assertFalse(AdvancedGate.tryUnlock(context, "1234567890"))
        assertFalse(AdvancedGate.isUnlocked(context))
    }

    @Test
    fun crashReporterIsInstalled() {
        assertNotNull(Thread.getDefaultUncaughtExceptionHandler())
    }
}
