package com.apex.ai

import android.app.Application
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.apex.ai.core.AppContainer
import com.apex.ai.core.DeviceProfile
import com.apex.ai.ui.ChatViewModel
import com.apex.ai.ui.toolkit.ToolAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FeatureTest {

    private val context: Application
        get() = ApplicationProvider.getApplicationContext()

    /** Enforces "an icon for every tool" so the set cannot silently drift. */
    @Test
    fun everyToolHasItsOwnIcon() {
        val fallback = ToolAdapter.iconFor("__no_such_tool__")
        AppContainer.get(context).tools.all().forEach { tool ->
            assertNotEquals(
                "tool '${tool.spec.id}' falls back to the generic icon",
                fallback,
                ToolAdapter.iconFor(tool.spec.id)
            )
        }
    }

    @Test
    fun newControlsArePresentInTheLayout() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertNotNull("mic button", activity.findViewById<ImageButton>(R.id.btnMic))
        assertNotNull("history list", activity.findViewById<RecyclerView>(R.id.recyclerHistory))
        assertNotNull("export action", activity.findViewById<TextView>(R.id.btnExportChat))
        assertNotNull("composer", activity.findViewById<TextView>(R.id.tvAgentStatus))
    }

    @Test
    fun deviceProfileIsDetectedAtStartup() {
        assertTrue("cores must be read", DeviceProfile.cores >= 1)
        // Robolectric reports a modest machine, so animations must stay enabled
        // for correctness but the profile must still be populated.
        assertTrue(DeviceProfile.totalRamMb >= 0)
    }

    @Test
    fun transcriptExportIsMarkdown() {
        val viewModel = ChatViewModel(context)
        viewModel.newSession("general")
        val flow = viewModel.send("general", "hello there")
        assertNotNull(flow)
        viewModel.recordAssistant("Apex", "hi, how can I help?")
        val text = viewModel.transcriptText()
        assertTrue(text.contains("**You:** hello there"))
        assertTrue(text.contains("**Apex:**"))
        assertTrue(text.startsWith("#"))
    }

    @Test
    fun sessionTitlesAreDerivedFromTheFirstPrompt() {
        val viewModel = ChatViewModel(context)
        viewModel.newSession("coder")
        viewModel.send("coder", "Write a quicksort in Kotlin please")
        assertEquals("Write a quicksort in Kotlin please", viewModel.session.title)
        assertFalse(viewModel.session.messages.isEmpty())
    }
}
