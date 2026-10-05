package com.linuxterminal.android.agent.persistence

import android.util.AtomicFile
import com.linuxterminal.android.agent.sessions.AgentSession
import org.json.JSONObject
import java.io.File

/** Metadata only: no credentials, prompts, shell output or tool arguments. */
class SessionStore(private val directory: File) {
    init { require(directory.mkdirs() || directory.isDirectory) }
    private fun file(id: String): AtomicFile {
        require(Regex("[a-zA-Z0-9-]{1,80}").matches(id))
        return AtomicFile(File(directory, "$id.json"))
    }
    @Synchronized fun save(session: AgentSession) {
        val target = file(session.id)
        val stream = target.startWrite()
        try {
            val json = JSONObject().put("version", 1).put("id", session.id)
                .put("workspace", session.workspace).put("provider", session.provider).put("createdAt", session.createdAt)
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            target.finishWrite(stream)
        } catch (e: Exception) { target.failWrite(stream); throw e }
    }
    @Synchronized fun load(id: String): AgentSession {
        val data = JSONObject(file(id).readFully().toString(Charsets.UTF_8))
        require(data.getInt("version") == 1)
        return AgentSession(data.getString("id"), data.getString("workspace"),
            data.getString("provider"), data.getLong("createdAt"))
    }
}
