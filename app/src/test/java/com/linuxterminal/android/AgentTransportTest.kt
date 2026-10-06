package com.linuxterminal.android

import com.linuxterminal.android.agent.runtime.JsonLines
import com.linuxterminal.android.agent.tools.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class AgentTransportTest {
    @Test fun jsonlPreservesUnicodeSeparatorsAndAcceptsCrLf() {
        val source = "{\"text\":\"ação\u2028😀\u2029\"}\r\n{\"id\":\"two\"}\n"
        val lines = mutableListOf<String>()
        JsonLines.read(source.toByteArray().inputStream(), consume = lines::add)
        assertEquals(2, lines.size)
        assertEquals("{\"text\":\"ação\u2028😀\u2029\"}", lines[0])
    }
    @Test fun refusesUnboundedOrTruncatedRpcRecord() {
        listOf("12345\n", "{\"id\":1}").forEach { source ->
            try { JsonLines.read(source.byteInputStream(), limit = 4) { }; fail("Accepted invalid record") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun ambiguousEditPreservesOriginal() {
        val root = Files.createTempDirectory("edit").toFile()
        try {
            val files = WorkspaceFiles(root)
            files.write("code.txt", "same same")
            val result = EditTool(files).execute(mapOf("path" to "code.txt", "oldText" to "same", "newText" to "changed"))
            assertFalse(result.success)
            assertEquals("same same", files.read("code.txt"))
            assertTrue(EditTool(files).execute(mapOf("path" to "code.txt", "oldText" to "same same", "newText" to "ação 😀")).success)
            assertEquals("ação 😀", files.read("code.txt"))
        } finally { root.deleteRecursively() }
    }
}
