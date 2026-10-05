package com.linuxterminal.android.agent.tools

import com.linuxterminal.android.agent.permissions.*
import com.linuxterminal.android.terminal.PtyCommandExecutor

class ShellTool(private val executor: PtyCommandExecutor) : AgentTool {
    override val name = "shell"
    override fun permission(sessionId: String, arguments: Map<String, String>) =
        // Arbitrary shell commands can delete files or exfiltrate keys. Never regex-allowlist them.
        PermissionRequest(sessionId, name, Access.DESTRUCTIVE, arguments.getValue("command"))
    override fun execute(arguments: Map<String, String>) = executor.execute(arguments.getValue("command"))
}
