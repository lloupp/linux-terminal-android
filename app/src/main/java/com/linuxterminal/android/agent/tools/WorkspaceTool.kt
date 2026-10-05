package com.linuxterminal.android.agent.tools

import com.linuxterminal.android.agent.permissions.*

class WorkspaceTool(private val files: WorkspaceFiles) : AgentTool {
    override val name = "workspace"
    override fun permission(sessionId: String, arguments: Map<String, String>) =
        PermissionRequest(sessionId, name, Access.READ, files.root.absolutePath)
    override fun execute(arguments: Map<String, String>) = ToolResult(true,
        files.root.listFiles()?.sortedBy { it.name }?.take(1000)?.joinToString("\n") { it.name } ?: "")
}
