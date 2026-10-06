package com.linuxterminal.android.workspace

import android.content.Context
import java.io.File

/** Stable private directories; display names never become executable paths. */
class WorkspaceCatalog(context: Context) {
    val root = File(context.filesDir, "workspaces").apply { mkdirs() }
    private val preferences = context.getSharedPreferences("workspaces", Context.MODE_PRIVATE)
    fun default(): File = File(root, "default").apply { mkdirs() }
    fun resolve(id: String): File {
        require(Regex("[a-zA-Z0-9-]{1,80}").matches(id))
        val file = File(root, id)
        require(file.isDirectory && file.canonicalFile.parentFile == root.canonicalFile)
        return file
    }
    fun recent(): List<File> = root.listFiles()?.filter {
        it.isDirectory && !it.name.startsWith('.') && it.canonicalFile.parentFile == root.canonicalFile
    }?.sortedByDescending { preferences.getLong("used:${it.name}", 0) } ?: emptyList()
    fun title(file: File): String = preferences.getString("title:${file.name}", null)
        ?: if (file.name == "default") "Terminal padrão" else "Projeto ${file.name.takeLast(8)}"
    fun record(file: File, title: String? = null) {
        resolve(file.name)
        preferences.edit().putLong("used:${file.name}", System.currentTimeMillis()).apply {
            if (title != null) putString("title:${file.name}", title.take(120))
        }.apply()
    }
}
