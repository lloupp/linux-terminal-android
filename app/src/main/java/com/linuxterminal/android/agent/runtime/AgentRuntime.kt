package com.linuxterminal.android.agent.runtime

import com.linuxterminal.android.agent.permissions.PermissionGate
import com.linuxterminal.android.agent.sessions.AgentSession
import com.linuxterminal.android.agent.tools.*

interface AgentRuntime {
    fun execute(session: AgentSession, call: ToolCall): ToolResult
}
/** No UI dependency. All registered tools, including read operations, pass through the gate. */
class GatedAgentRuntime(tools: List<AgentTool>, private val gate: PermissionGate) : AgentRuntime {
    private val registry = tools.associateBy { it.name }
    init { require(registry.size == tools.size) { "Duplicate tool name" } }
    override fun execute(session: AgentSession, call: ToolCall): ToolResult {
        val tool = registry[call.name] ?: return ToolResult(false, "Unknown tool")
        return try {
            val arguments = java.util.Collections.unmodifiableMap(java.util.TreeMap(call.arguments))
            val request = tool.permission(session.id, arguments).copy(parameters = arguments)
            if (!gate.allows(request)) ToolResult(false, "Approval required")
            else tool.execute(arguments)
        } catch (e: Exception) {
            // Exception messages and arguments may contain secrets; never return them blindly.
            ToolResult(false, "Tool failed: ${e.javaClass.simpleName}")
        }
    }
}
