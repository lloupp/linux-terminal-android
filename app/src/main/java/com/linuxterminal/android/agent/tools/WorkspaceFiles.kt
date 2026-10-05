package com.linuxterminal.android.agent.tools

import java.io.File

/** Canonical containment rejects ../, absolute paths and symlinks escaping the workspace. */
class WorkspaceFiles(root: File) {
    val root: File = root.canonicalFile
    init { require(this.root.isDirectory) }
    fun resolve(relative: String): File {
        require(relative.isNotBlank() && !File(relative).isAbsolute)
        val candidate = File(root, relative).canonicalFile
        require(candidate == root || candidate.path.startsWith(root.path + File.separator)) { "Outside workspace" }
        return candidate
    }
    fun read(relative: String): String {
        val file = resolve(relative)
        require(file.isFile && file.length() <= 1_048_576) { "Not a bounded text file" }
        return file.readText(Charsets.UTF_8)
    }
    fun write(relative: String, text: String) {
        require(text.toByteArray(Charsets.UTF_8).size <= 1_048_576)
        val target = resolve(relative)
        val parent = requireNotNull(target.parentFile)
        require(target != root && parent.isDirectory)
        val temporary = File.createTempFile(".agent-write-", ".tmp", parent)
        try {
            java.io.FileOutputStream(temporary).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8)); output.fd.sync()
            }
            check(temporary.renameTo(target)) { "Atomic replace failed" }
        } finally { temporary.delete() }
    }
}
