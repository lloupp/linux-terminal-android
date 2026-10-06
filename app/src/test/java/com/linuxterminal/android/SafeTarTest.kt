package com.linuxterminal.android

import com.linuxterminal.android.runtime.SafeTar
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class SafeTarTest {
    private fun archive(name: String, content: String = "", type: Char = '0', link: String = ""): ByteArray {
        val header = ByteArray(512)
        fun field(offset: Int, size: Int, text: String) { text.toByteArray().copyInto(header, offset, 0, minOf(size, text.toByteArray().size)) }
        val body = content.toByteArray()
        field(0, 100, name); field(100, 8, "0000755\u0000"); field(124, 12, body.size.toString(8).padStart(11, '0') + "\u0000")
        header[156] = type.code.toByte(); field(157, 100, link)
        for (i in 148..155) header[i] = 32
        field(148, 8, header.sumOf { it.toInt() and 255 }.toString(8).padStart(6, '0') + "\u0000 ")
        return header + body + ByteArray((512 - body.size % 512) % 512) + ByteArray(1024)
    }
    private fun root(test: (File) -> Unit) {
        val root = Files.createTempDirectory("safe-tar").toFile()
        try { test(root) } finally { root.deleteRecursively() }
    }
    @Test fun extractsExecutableUtf8File() = root { root ->
        SafeTar { _, _ -> fail("Unexpected link") }.extract(ByteArrayInputStream(archive("bin/proof", "ação 😀")), root)
        assertEquals("ação 😀", root.resolve("bin/proof").readText())
        assertTrue(root.resolve("bin/proof").canExecute())
    }
    @Test fun rejectsTraversalAndEscapingLink() = root { root ->
        for (bytes in listOf(archive("../escape"), archive("link", type = '2', link = "../../outside"))) {
            assertThrows(IllegalArgumentException::class.java) { SafeTar { _, _ -> fail("Unsafe link") }.extract(ByteArrayInputStream(bytes), root) }
        }
    }
    @Test fun rejectsCorruptionTruncationAndRootPayload() = root { root ->
        val corrupt = archive("proof").apply { this[0] = 99 }
        for (bytes in listOf(corrupt, ByteArray(23), archive("./", "hidden-payload", '5'))) {
            assertThrows(IllegalArgumentException::class.java) { SafeTar { _, _ -> }.extract(ByteArrayInputStream(bytes), root) }
        }
    }
    @Test fun convertsAbsoluteGuestLinkToContainedRelativeLink() = root { root ->
        var destination = ""
        SafeTar { target, file -> destination = target; assertEquals(root.resolve("bin/sh"), file) }
            .extract(ByteArrayInputStream(archive("bin/sh", type = '2', link = "/bin/busybox")), root)
        assertEquals("../bin/busybox", destination)
    }
}
