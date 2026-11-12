package com.apex.ai

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.apex.ai.core.AppFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Step 1: the Java platform layer must answer without throwing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlatformLayerTest {

    private val context: Application
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun javaLayerReportsInstallFacts() {
        val facts = AppFacts.self(context)
        assertFalse("layer reported an error: ${facts["error"]}", facts.containsKey("error"))
        assertEquals(context.packageName, facts["package"])
        assertNotNull(facts["versionName"])
        assertNotNull(facts["abis"])
        assertTrue("signature must be resolved or explicitly none",
            facts["signingSha256"] == "none" || (facts["signingSha256"]?.length ?: 0) >= 32)
    }

    @Test
    fun prettyPrintsAsLines() {
        val text = AppFacts.pretty(context)
        assertTrue(text.contains("package ="))
        assertTrue(text.contains("\n"))
    }

    @Test
    fun helpAssetIsPackaged() {
        val stream = context.assets.open("help/index.html")
        val html = stream.bufferedReader().use { it.readText() }
        assertTrue("help page must ship in the APK", html.contains("Apex AI"))
        assertTrue(html.contains("<script>"))
    }
}
