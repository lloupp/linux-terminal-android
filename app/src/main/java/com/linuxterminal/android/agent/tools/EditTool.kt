package com.linuxterminal.android.agent.tools

import com.linuxterminal.android.agent.permissions.*

/** Exact, unambiguous replacement; never edits a different file on approval. */
class EditTool(private val files: WorkspaceFiles) : AgentTool {
    override val name = "edit"
    override fun permission(sessionId: String, arguments: Map<String, String>) =
        PermissionRequest(sessionId, name, Access.WRITE, files.resolve(arguments.getValue("path")).path)
    override fun execute(arguments: Map<String, String>): ToolResult {
        val original = files.read(arguments.getValue("path"))
        val old = arguments.getValue("oldText")
        require(old.isNotEmpty())
        val first = original.indexOf(old)
        if (first < 0 || original.indexOf(old, first + 1) >= 0) return ToolResult(false, "Replacement must match exactly once")
        files.write(arguments.getValue("path"), original.substring(0, first) + arguments.getValue("newText") + original.substring(first + old.length))
        return ToolResult(true, "Edited")
    }
}
