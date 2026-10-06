package com.linuxterminal.android.agent.persistence

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Bounded operation metadata only: no tool arguments, file contents or keys. */
class AgentAudit(directory: File) {
    private val file = AtomicFile(File(directory.apply { mkdirs() }, "audit.json"))
    @Synchronized fun record(session: String, tool: String, stage: String, success: Boolean) {
        val previous = try { JSONArray(file.readFully().toString(Charsets.UTF_8)) } catch (_: Exception) { JSONArray() }
        val next = JSONArray()
        for (index in maxOf(0, previous.length() - 499) until previous.length()) next.put(previous.getJSONObject(index))
        next.put(JSONObject().put("at", System.currentTimeMillis()).put("session", session)
            .put("tool", tool).put("stage", stage).put("success", success))
        val stream = file.startWrite()
        try { stream.write(next.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
}
