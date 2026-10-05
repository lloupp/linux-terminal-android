package com.linuxterminal.android

import com.linuxterminal.android.agent.permissions.*
import com.linuxterminal.android.agent.runtime.GatedAgentRuntime
import com.linuxterminal.android.agent.sessions.AgentSession
import com.linuxterminal.android.agent.tools.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class FoundationTest {
    @Test fun blocksTraversalAndSymlinkEscape() {
        val root = Files.createTempDirectory("workspace").toFile()
        val outside = Files.createTempDirectory("outside").toFile()
        try {
            val files = WorkspaceFiles(root)
            listOf("../outside", outside.absolutePath).forEach { path ->
                try { files.resolve(path); fail("Escaped workspace") } catch (_: IllegalArgumentException) {}
            }
            Files.createSymbolicLink(root.toPath().resolve("escape"), outside.toPath())
            try { files.resolve("escape/file"); fail("Symlink escaped") } catch (_: IllegalArgumentException) {}
        } finally { root.deleteRecursively(); outside.deleteRecursively() }
    }
    @Test fun deniesWriteWithoutChangingFile() {
        val root = Files.createTempDirectory("workspace").toFile()
        try {
            val files = WorkspaceFiles(root)
            files.write("hello.txt", "original")
            val runtime = GatedAgentRuntime(listOf(FileTool(files)), ReadOnlyGate())
            val session = AgentSession(workspace = root.path, provider = "unconfigured")
            val read = runtime.execute(session, ToolCall("file", mapOf("operation" to "read", "path" to "hello.txt")))
            assertTrue(read.success); assertEquals("original", read.text)
            val write = runtime.execute(session, ToolCall("file", mapOf("operation" to "write", "path" to "hello.txt", "text" to "changed")))
            assertFalse(write.success); assertEquals("original", files.read("hello.txt"))
            assertFalse(runtime.execute(session, ToolCall("unknown", emptyMap())).success)
        } finally { root.deleteRecursively() }
    }
    @Test fun gateReceivesActualWriteResource() {
        val root = Files.createTempDirectory("workspace").toFile()
        try {
            var observed: PermissionRequest? = null
            val runtime = GatedAgentRuntime(listOf(FileTool(WorkspaceFiles(root))), PermissionGate { request ->
                observed = request; true
            })
            val result = runtime.execute(AgentSession(workspace = root.path, provider = "unconfigured"),
                ToolCall("file", mapOf("operation" to "write", "path" to "new.txt", "text" to "ação 😀")))
            assertTrue(result.success)
            assertEquals(Access.WRITE, observed!!.access)
            assertEquals(root.resolve("new.txt").canonicalPath, observed!!.resource)
            assertEquals("ação 😀", root.resolve("new.txt").readText())
        } finally { root.deleteRecursively() }
    }
}
