package com.apex.ai

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.apex.ai.core.PackManager
import com.apex.ai.data.SessionStore
import com.apex.ai.data.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PacksAndHistoryTest {

    private val context: Application
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun packsAreAbsentUntilInstalled() {
        val packs = PackManager(context)
        packs.clear()
        assertFalse(packs.isInstalled())
        assertTrue("no pack means no prompts, not a crash", packs.prompts().isEmpty())
        assertEquals(0, packs.installedCount())
    }

    @Test
    fun sessionCanBeDeleted() {
        val store = SessionStore(context)
        store.clear()
        val session = Session(title = "to delete")
        store.upsert(session)
        assertTrue(store.loadAll().any { it.id == session.id })

        store.delete(session.id)
        assertFalse("delete must remove it from disk", store.loadAll().any { it.id == session.id })
    }

    @Test
    fun sessionStoreCapsTheTranscript() {
        val store = SessionStore(context)
        store.clear()
        val session = Session(title = "long")
        repeat(450) { index ->
            session.messages.add(com.apex.ai.data.model.StoredMessage(text = "m$index"))
        }
        store.upsert(session)
        assertEquals("transcripts are capped at 400", 400, session.messages.size)
    }
}
