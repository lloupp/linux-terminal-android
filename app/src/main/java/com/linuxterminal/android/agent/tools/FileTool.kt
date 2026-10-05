package com.linuxterminal.android.agent.tools

import com.linuxterminal.android.agent.permissions.*

class FileTool(private val files: WorkspaceFiles) : AgentTool {
    override val name = "file"
    override fun permission(sessionId: String, arguments: Map<String, String>): PermissionRequest {
        val access = when(arguments["operation"]) { "read" -> Access.READ; "write" -> Access.WRITE; else -> error("Unsupported operation") }
        val path = files.resolve(arguments.getValue("path"))
        return PermissionRequest(sessionId, name, access, path.absolutePath)
    }
    override fun execute(arguments: Map<String, String>): ToolResult = when(arguments["operation"]) {
        "read" -> ToolResult(true, files.read(arguments.getValue("path")))
        "write" -> { files.write(arguments.getValue("path"), arguments.getValue("text")); ToolResult(true, "Written") }
        else -> ToolResult(false, "Unsupported operation")
    }
}
