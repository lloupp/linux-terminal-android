package com.linuxterminal.android

import androidx.test.platform.app.InstrumentationRegistry
import com.linuxterminal.android.agent.runtime.ApprovalBroker
import com.linuxterminal.android.agent.runtime.GatedAgentRuntime
import com.linuxterminal.android.agent.sessions.AgentSession
import com.linuxterminal.android.agent.tools.*
import org.junit.Assert.*
import org.junit.Test

class ApprovalDeviceTest {
    @Test fun explicitDenialPreventsWriteAndApprovalUsesExactParameters() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = instrumentation.targetContext.cacheDir.resolve("approval-proof").apply { mkdirs() }
        val target = root.resolve("proof.txt")
        target.writeText("original")
        val runtime = GatedAgentRuntime(listOf(FileTool(WorkspaceFiles(root))), ApprovalBroker)
        val session = AgentSession(workspace = root.path, provider = "test")
        var allow = false
        var parameters: Map<String, String>? = null
        val observer: (ApprovalBroker.Pending?) -> Unit = { item ->
            if (item != null) { parameters = item.request.parameters; ApprovalBroker.answer(item.id, allow) }
        }
        instrumentation.runOnMainSync { ApprovalBroker.observe(observer) }
        try {
            val call = ToolCall("file", mapOf("operation" to "write", "path" to "proof.txt", "text" to "ação 😀"))
            assertFalse(runtime.execute(session, call).success)
            assertEquals("original", target.readText())
            assertEquals(call.arguments, parameters)
            instrumentation.runOnMainSync { allow = true }
            assertTrue(runtime.execute(session, call).success)
            assertEquals("ação 😀", target.readText())
        } finally {
            instrumentation.runOnMainSync { ApprovalBroker.unobserve(observer); ApprovalBroker.cancelAll() }
            root.deleteRecursively()
        }
    }
}
