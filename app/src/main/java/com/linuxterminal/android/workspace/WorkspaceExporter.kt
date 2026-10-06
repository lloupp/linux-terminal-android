package com.linuxterminal.android.workspace

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Export a bounded copy. Never follow symlinks or silently omit files. */
class WorkspaceExporter {
    fun export(root: File, output: OutputStream, control: TransferControl = TransferControl()) {
        val base = root.canonicalFile
        var documents = 0
        var bytes = 0L
        ZipOutputStream(output).use { zip ->
            fun visit(directory: File, prefix: String, depth: Int) {
                require(depth <= 20) { "Directory tree too deep" }
                val children = directory.listFiles() ?: error("Cannot list directory")
                for (file in children.sortedBy { it.name }) {
                    control.check()
                    require(++documents <= 2000) { "Too many documents" }
                    require(file.absoluteFile == file.canonicalFile && file.canonicalPath.startsWith(base.path + File.separator)) {
                        "Symbolic links are not exportable"
                    }
                    require(!file.name.contains('\\') && !file.name.contains('\u0000'))
                    val name = prefix + file.name
                    if (file.isDirectory) {
                        zip.putNextEntry(ZipEntry("$name/")); zip.closeEntry()
                        visit(file, "$name/", depth + 1)
                    } else {
                        require(file.isFile) { "Unsupported file" }
                        zip.putNextEntry(ZipEntry(name))
                        file.inputStream().use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                control.check()
                                val count = input.read(buffer)
                                if (count < 0) break
                                bytes += count
                                require(bytes <= 64L * 1024 * 1024) { "Workspace exceeds 64 MiB" }
                                zip.write(buffer, 0, count)
                                control.report(documents, bytes)
                            }
                        }
                        zip.closeEntry()
                    }
                    control.report(documents, bytes)
                }
            }
            visit(base, "", 0)
        }
    }
}
