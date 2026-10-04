package com.linuxterminal.android.agent.sessions

import java.util.UUID

data class AgentSession(val id: String = UUID.randomUUID().toString(), val workspace: String,
    val provider: String, val createdAt: Long = System.currentTimeMillis())
