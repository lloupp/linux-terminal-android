package com.linuxterminal.android.workspace

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.util.UUID

/** SAF is a document API, not a POSIX mount. Import a bounded snapshot into app-private storage.
 * Never overwrite an existing workspace and never automatically sync changes back to the provider.
 */
class SafWorkspaceImporter(private val resolver: ContentResolver, private val workspaceRoot: File) {
    fun import(tree: Uri): File {
        val destination = File(workspaceRoot, "import-${UUID.randomUUID()}")
        check(destination.mkdirs())
        var files = 0
        var bytes = 0L
        fun copy(parentId: String, directory: File, depth: Int) {
            require(depth <= 20) { "Directory tree too deep" }
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)!!.use { cursor ->
                while (cursor.moveToNext()) {
                    require(++files <= 2000) { "Too many documents" }
                    val id = cursor.getString(0)
                    val name = cursor.getString(1)
                    require(name.isNotBlank() && name != "." && name != ".." && !name.contains('/') && !name.contains('\\') && !name.contains('\u0000'))
                    val target = File(directory, name)
                    require(!target.exists()) { "Duplicate document name" }
                    if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        check(target.mkdir()); copy(id, target, depth + 1)
                    } else {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        resolver.openInputStream(uri)!!.use { input ->
                            target.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    bytes += count
                                    require(bytes <= 64L * 1024 * 1024) { "Workspace exceeds 64 MiB" }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                    }
                }
            }
        }
        return try { copy(DocumentsContract.getTreeDocumentId(tree), destination, 0); destination }
        catch (e: Exception) { destination.deleteRecursively(); throw e }
    }
}
