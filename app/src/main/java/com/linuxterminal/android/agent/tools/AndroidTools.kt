package com.linuxterminal.android.agent.tools

import android.content.*
import com.linuxterminal.android.agent.permissions.*

class ClipboardTool(private val context: Context) : AgentTool {
    override val name = "clipboard"
    override fun permission(sessionId: String, arguments: Map<String, String>) =
        // Even reading clipboard discloses another app's content; explicit approval required.
        PermissionRequest(sessionId, name,
            if (arguments["operation"] == "read") Access.READ else Access.WRITE,
            "Android clipboard", requiresApproval = true)
    override fun execute(arguments: Map<String, String>): ToolResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return when(arguments["operation"]) {
            "read" -> ToolResult(true, clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: "")
            "write" -> { clipboard.setPrimaryClip(ClipData.newPlainText("Agent", arguments.getValue("text"))); ToolResult(true, "Copied") }
            else -> ToolResult(false, "Unsupported operation")
        }
    }
}
class ShareTool(private val context: Context) : AgentTool {
    override val name = "share"
    override fun permission(sessionId: String, arguments: Map<String, String>) =
        PermissionRequest(sessionId, name, Access.WRITE, "Android share sheet")
    override fun execute(arguments: Map<String, String>): ToolResult {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, arguments.getValue("text"))
        context.startActivity(Intent.createChooser(intent, "Compartilhar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return ToolResult(true, "Share sheet opened; user selects destination")
    }
}
