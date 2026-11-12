package com.apex.ai.data

import android.content.Context
import com.apex.ai.core.ApexLog
import com.apex.ai.data.model.Session
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Conversation persistence without Room.
 *
 * Rationale: the schema is a single append-only list per session and we rewrite
 * at most 40 sessions. A relational database plus its annotation processor would
 * cost more build time and APK size than it saves. Writes are atomic
 * (temp file + rename) so a process death mid-save cannot corrupt history.
 */
class SessionStore(context: Context) {

    private val dir = File(context.filesDir, "sessions").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val gson = Gson()
    private val maxSessions = 40

    private val type = object : TypeToken<MutableList<Session>>() {}.type

    fun loadAll(): MutableList<Session> {
        if (!indexFile.exists()) return mutableListOf()
        return runCatching {
            val raw = indexFile.readText()
            gson.fromJson<MutableList<Session>>(raw, type) ?: mutableListOf()
        }.getOrElse {
            ApexLog.e("SessionStore", "index unreadable, starting fresh", it)
            mutableListOf()
        }
    }

    fun saveAll(sessions: List<Session>) {
        runCatching {
            val trimmed = sessions.sortedByDescending { it.updatedAt }.take(maxSessions)
            val tmp = File(dir, "index.json.tmp")
            tmp.writeText(gson.toJson(trimmed, type))
            if (indexFile.exists()) indexFile.delete()
            if (!tmp.renameTo(indexFile)) {
                indexFile.writeText(tmp.readText())
                tmp.delete()
            }
        }.onFailure { ApexLog.e("SessionStore", "save failed", it) }
    }

    fun upsert(session: Session) {
        val all = loadAll()
        val idx = all.indexOfFirst { it.id == session.id }
        if (idx >= 0) all[idx] = session else all.add(0, session)
        saveAll(all)
    }

    fun delete(sessionId: String) {
        saveAll(loadAll().filterNot { it.id == sessionId })
    }

    fun clear() {
        saveAll(emptyList())
    }
}
