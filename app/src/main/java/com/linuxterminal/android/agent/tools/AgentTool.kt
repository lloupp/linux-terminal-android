package com.linuxterminal.android.agent.tools

import com.linuxterminal.android.agent.permissions.PermissionRequest

data class ToolCall(val name: String, val arguments: Map<String, String>)
data class ToolResult(val success: Boolean, val text: String)
interface AgentTool {
    val name: String
    fun permission(sessionId: String, arguments: Map<String, String>): PermissionRequest
    fun execute(arguments: Map<String, String>): ToolResult
}
