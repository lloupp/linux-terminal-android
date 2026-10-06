package com.linuxterminal.android

import com.linuxterminal.android.workspace.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.zip.ZipInputStream

class WorkspaceExportTest {
    @Test fun exportedProjectRoundTripsUnicodeAndEmptyDirectories() {
        val root = Files.createTempDirectory("export").toFile()
        try {
            root.resolve("vazia").mkdir()
            root.resolve("ação.txt").writeText("😀 conteúdo")
            val bytes = ByteArrayOutputStream()
            WorkspaceExporter().export(root, bytes)
            val entries = mutableMapOf<String, String>()
            ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
            }
            assertEquals("😀 conteúdo", entries["ação.txt"])
            assertTrue(entries.containsKey("vazia/"))
            assertEquals("😀 conteúdo", root.resolve("ação.txt").readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun rejectsSymlinkInsteadOfExportingOutsideData() {
        val root = Files.createTempDirectory("export").toFile()
        val outside = Files.createTempFile("secret", ".txt")
        try {
            Files.createSymbolicLink(root.toPath().resolve("escape"), outside)
            try { WorkspaceExporter().export(root, ByteArrayOutputStream()); fail("Exported symlink") }
            catch (_: IllegalArgumentException) { }
        } finally { root.deleteRecursively(); Files.deleteIfExists(outside) }
    }
    @Test fun cancellationStopsExportWithoutChangingSource() {
        val root = Files.createTempDirectory("export").toFile()
        try {
            root.resolve("file.txt").writeText("original")
            val control = TransferControl().apply { cancel() }
            try { WorkspaceExporter().export(root, ByteArrayOutputStream(), control); fail("Not cancelled") }
            catch (_: CancellationException) { }
            assertEquals("original", root.resolve("file.txt").readText())
        } finally { root.deleteRecursively() }
    }
}
