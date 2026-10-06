package com.linuxterminal.android.workspace

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors

/** Process-owned transfers. Activities are never captured by worker threads. */
object WorkspaceTransfers {
    private class IncompleteExport(cause: Exception) : Exception(cause)
    data class State(val busy: Boolean = false, val label: String = "", val documents: Int = 0,
        val bytes: Long = 0, val resultWorkspace: String? = null, val message: String? = null)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val listeners = mutableSetOf<(State) -> Unit>()
    var state = State()
        private set
    private var control: TransferControl? = null
    fun observe(listener: (State) -> Unit) { listeners.add(listener); listener(state) }
    fun unobserve(listener: (State) -> Unit) { listeners.remove(listener) }
    private fun publish(value: State) { state = value; listeners.toList().forEach { it(value) } }
    fun consumeResult() { if (!state.busy) publish(State()) }
    fun cancel() { control?.cancel() }
    fun start(label: String, operation: (TransferControl) -> String?) {
        check(!state.busy)
        publish(State(busy = true, label = label))
        var lastUpdate = 0L
        val token = TransferControl { documents, bytes ->
            val now = System.currentTimeMillis()
            if (now - lastUpdate > 100) {
                lastUpdate = now
                main.post { if (state.busy) publish(State(true, label, documents, bytes)) }
            }
        }
        control = token
        worker.execute {
            try {
                val result = operation(token)
                main.post { control = null; publish(State(resultWorkspace = result, message = "$label concluída")) }
            } catch (e: Exception) {
                val message = when (e) {
                    is IncompleteExport -> "Exportação incompleta. Remova o ZIP parcial no destino antes de tentar novamente."
                    is CancellationException -> "$label cancelada"
                    is SecurityException -> "Sem permissão. Selecione novamente a pasta ou o destino."
                    is IllegalArgumentException -> "Projeto incompatível: limite de 2.000 documentos, 64 MiB ou estrutura inválida."
                    else -> "Não foi possível concluir. Verifique espaço livre e acesso aos arquivos."
                }
                main.post { control = null; publish(State(message = message)) }
            }
        }
    }
    fun import(context: Context, uri: Uri) {
        val app = context.applicationContext
        start("Importação") { token ->
            val catalog = WorkspaceCatalog(app)
            val file = SafWorkspaceImporter(app.contentResolver, catalog.root).import(uri, token)
            val title = try {
                val document = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                app.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0)?.take(100)?.filter { !it.isISOControl() } else null
                }
            } catch (_: Exception) { null }
            catalog.record(file, title?.takeIf { it.isNotBlank() }?.let { "$it • ${file.name.takeLast(8)}" })
            file.name
        }
    }
    fun export(context: Context, id: String, uri: Uri) {
        val app = context.applicationContext
        start("Exportação") { token ->
            val temporary = File.createTempFile("workspace-", ".zip", app.cacheDir)
            try {
                WorkspaceExporter().export(WorkspaceCatalog(app).resolve(id), temporary.outputStream(), token)
                token.check()
                app.contentResolver.openOutputStream(uri, "wt")!!.use { output ->
                    temporary.inputStream().use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            token.check()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                }
                null
            } catch (e: Exception) {
                val removed = try { DocumentsContract.deleteDocument(app.contentResolver, uri) } catch (_: Exception) { false }
                if (!removed) throw IncompleteExport(e)
                throw e
            } finally { temporary.delete() }
        }
    }
}
