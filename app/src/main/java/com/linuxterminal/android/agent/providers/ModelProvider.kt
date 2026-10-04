package com.linuxterminal.android.agent.providers

import com.linuxterminal.android.agent.tools.ToolCall

data class ModelReply(val text: String, val calls: List<ToolCall> = emptyList())
interface ModelProvider {
    val id: String
    fun respond(context: List<String>, tools: Set<String>): ModelReply
}
