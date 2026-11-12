package com.apex.ai

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.apex.ai.core.BootTrace
import com.apex.ai.core.LocalVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The start-up hang, pinned down as a test.
 *
 * Before warm-up the vault must answer instantly from defaults and queue writes.
 * If this ever regresses to touching the Keystore eagerly, the app goes back to
 * showing a splash and dying, and this test fails first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VaultOffMainThreadTest {

    private val context: Application
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun readsBeforeWarmUpReturnDefaultsWithoutBlocking() {
        val vault = LocalVault(context)
        assertEquals("general", vault.selectedAgentId)
        assertEquals("", vault.modelId)
        assertEquals(8, vault.maxAgentSteps)
        assertNull(vault.getApiKey("gemini"))
    }

    @Test
    fun writesBeforeWarmUpAreQueuedNotLost() {
        val vault = LocalVault(context)
        vault.saveApiKey("gemini", "queued-key")
        assertNull("not visible until the store exists", vault.getApiKey("gemini"))

        vault.warmUp()
        assertTrue(vault.isReady)
        assertEquals("queued-key", vault.getApiKey("gemini"))
    }

    @Test
    fun warmUpIsIdempotent() {
        val vault = LocalVault(context)
        vault.warmUp()
        vault.warmUp()
        vault.saveApiKey("groq", "k2")
        assertEquals("k2", vault.getApiKey("groq"))
    }

    @Test
    fun bootTraceRecordsSteps() {
        BootTrace.begin(context)
        BootTrace.step(context, "unit-test-step")
        val trace = BootTrace.lastRun(context)
        assertNotNull(trace)
        assertTrue(trace!!.contains("unit-test-step"))
    }
}
