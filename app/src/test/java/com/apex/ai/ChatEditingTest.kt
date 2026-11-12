package com.apex.ai

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.apex.ai.ui.ChatViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Step 2: regenerate and edit-and-resend operate on the transcript correctly. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChatEditingTest {

    private val context: Application
        get() = ApplicationProvider.getApplicationContext()

    private fun newModel(): ChatViewModel {
        val model = ChatViewModel(context)
        model.newSession("general")
        model.session.messages.clear()
        return model
    }

    @Test
    fun regenerateDropsOnlyTheReply() {
        val model = newModel()
        model.send("general", "hello")
        model.recordAssistant("Apex", "first answer")
        assertEquals(2, model.session.messages.size)

        model.dropTrailingAssistant()

        assertEquals(1, model.session.messages.size)
        assertEquals("hello", model.lastUserPrompt())
    }

    @Test
    fun regenerateLeavesToolRowsRemoved() {
        val model = newModel()
        model.send("general", "hello")
        model.session.messages.add(com.apex.ai.data.model.StoredMessage(kind = "TOOL", text = "calc"))
        model.recordAssistant("Apex", "answer")

        model.dropTrailingAssistant()

        assertEquals(1, model.session.messages.size)
        assertEquals("hello", model.lastUserPrompt())
    }

    @Test
    fun userMessageIndexIsOneBased() {
        val model = newModel()
        model.send("general", "one")
        model.recordAssistant("Apex", "a")
        model.send("general", "two")

        assertEquals(0, model.userMessageIndex(1))
        assertEquals(2, model.userMessageIndex(2))
        assertNull(model.userMessageIndex(3))
        assertNull(model.userMessageIndex(0))
    }

    @Test
    fun truncateFromKeepsThePrefix() {
        val model = newModel()
        model.send("general", "one")
        model.recordAssistant("Apex", "a")
        model.send("general", "two")

        model.truncateFrom(2)

        assertEquals(2, model.session.messages.size)
        assertEquals("two", model.session.messages[0].text)
    }

    @Test
    fun resendingWithoutRecordingDoesNotDuplicateThePrompt() {
        val model = newModel()
        model.send("general", "hello")
        model.recordAssistant("Apex", "answer")
        model.dropTrailingAssistant()

        val before = model.session.messages.size
        model.send("general", "hello", recordUser = false)

        assertEquals("a regenerate must not add a second user bubble",
            before, model.session.messages.size)
    }
}
