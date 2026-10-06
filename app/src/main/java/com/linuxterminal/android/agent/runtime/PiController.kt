package com.linuxterminal.android.agent.runtime

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.linuxterminal.android.agent.persistence.SessionStore
import com.linuxterminal.android.agent.persistence.AgentAudit
import com.linuxterminal.android.agent.providers.PiConfiguration
import com.linuxterminal.android.agent.permissions.PermissionGate
import com.linuxterminal.android.agent.sessions.AgentSession
import com.linuxterminal.android.agent.tools.*
import com.linuxterminal.android.runtime.LinuxRuntime
import com.linuxterminal.android.terminal.PtyCommandExecutor
import com.linuxterminal.android.workspace.WorkspaceCatalog
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicLong

/** Single foreground-owned Pi connection. Tools run in Android through the permission gate. */
object PiController {
    data class State(val connected: Boolean = false, val working: Boolean = false,
        val workspace: String = "", val text: String = "", val message: String = "Pi desconectado")
    private val main = Handler(Looper.getMainLooper())
    private val lifecycle = Executors.newSingleThreadExecutor()
    private val writer = Executors.newSingleThreadExecutor()
    private val tools = Executors.newSingleThreadExecutor()
    private val generation = AtomicLong()
    private val jobs = ConcurrentHashMap<String, Future<*>>()
    private val requests = ConcurrentHashMap<String, String>()
    private val listeners = mutableSetOf<(State) -> Unit>()
    private var runFailed = false
    @Volatile private var process: Process? = null
    var state = State()
        private set
    fun observe(listener: (State) -> Unit) { listeners.add(listener); listener(state) }
    fun unobserve(listener: (State) -> Unit) { listeners.remove(listener) }
    private fun publish(value: State) { state = value; listeners.toList().forEach { it(value) } }
    private fun message(text: String) { publish(state.copy(message = text)) }
    fun start(context: Context, workspaceId: String) {
        val app = context.applicationContext
        stop()
        val version = generation.get()
        publish(State(workspace = workspaceId, message = "Conectando Pi…"))
        lifecycle.execute {
            try {
                val linux = LinuxRuntime(app)
                check(linux.ready) { "Prepare o Linux primeiro" }
                val workspace = WorkspaceCatalog(app).resolve(workspaceId)
                val home = File(app.filesDir, "home")
                val androidDir = File(home, ".pi/android").apply { mkdirs() }
                val extension = File(androidDir, "bridge.mjs")
                app.assets.open("pi-android-bridge.mjs").use { input -> extension.outputStream().use { input.copyTo(it) } }
                val prefs = app.getSharedPreferences("pi-sessions", Context.MODE_PRIVATE)
                val id = prefs.getString(workspaceId, null) ?: UUID.randomUUID().toString().also {
                    check(prefs.edit().putString(workspaceId, it).commit())
                }
                val session = AgentSession(id = id, workspace = workspace.path, provider = "pi")
                SessionStore(File(app.filesDir, "agent-sessions")).save(session)
                // Version gate and explicit resources. No automatic project extensions/MCP/skills.
                val command = "test \"$(pi --version)\" = '1.0.4' || exit 64; exec pi --mode rpc --no-builtin-tools --no-mcp --no-extensions --no-skills --no-prompt-templates --no-context-files --extension /root/.pi/android/bridge.mjs --session-id '$id' --session-dir /root/.pi/android/sessions"
                val configuration = PiConfiguration(app)
                val launch = linux.command(workspace, command + configuration.cli())
                val child = ProcessBuilder(*launch.args).directory(launch.cwd).apply {
                    environment().putAll(launch.env.associate { it.substringBefore('=') to it.substringAfter('=') })
                    environment().putAll(configuration.environment())
                }.start()
                if (generation.get() != version) { child.destroy(); return@execute }
                process = child
                val files = WorkspaceFiles(workspace)
                val audit = AgentAudit(File(app.filesDir, "agent-sessions"))
                val gate = PermissionGate { request ->
                    val allowed = ApprovalBroker.allows(request)
                    audit.record(session.id, request.tool, "approval", allowed)
                    allowed
                }
                val runtime = GatedAgentRuntime(listOf(FileTool(files), EditTool(files), WorkspaceTool(files),
                    ShellTool(PtyCommandExecutor(linux, workspace)), ClipboardTool(app), ShareTool(app)), gate)
                Thread({ child.errorStream.use { input -> val buffer = ByteArray(8192); while (input.read(buffer) >= 0) { /* never log potentially sensitive diagnostics */ } } }, "pi-stderr").start()
                main.post { if (generation.get() == version) {
                    send("get_state")
                    main.postDelayed({
                        if (generation.get() == version && !state.connected) {
                            stop(); message("Pi não respondeu em 20 segundos. Verifique a instalação e reconecte.")
                        }
                    }, 20000)
                } }
                child.inputStream.use { input -> JsonLines.read(input) { record ->
                    val event = JSONObject(record)
                    if (generation.get() == version) handle(event, version, runtime, session, audit)
                } }
                val exit = child.waitFor()
                main.post { if (generation.get() == version) {
                    process = null; jobs.values.forEach { it.cancel(true) }; jobs.clear(); requests.clear(); ApprovalBroker.cancelAll()
                    publish(state.copy(connected = false, working = false,
                        message = if (exit == 64) "Instale Pi 1.0.4 no Linux antes de conectar" else "Pi encerrado ($exit). Você pode reconectar."))
                } }
            } catch (_: Exception) {
                main.post { if (generation.get() == version) {
                    process?.destroy(); process = null; jobs.values.forEach { it.cancel(true) }; jobs.clear(); requests.clear(); ApprovalBroker.cancelAll()
                    publish(state.copy(connected = false, working = false, message = "Não foi possível conectar Pi. Verifique Linux, Node e Pi 1.0.4."))
                } }
            }
        }
    }
    private fun handle(event: JSONObject, version: Long, runtime: GatedAgentRuntime, session: AgentSession, audit: AgentAudit) {
        when(event.optString("type")) {
            "response" -> {
                val command = requests.remove(event.optString("id")) ?: return
                main.post { if (generation.get() == version) {
                    if (!event.optBoolean("success")) { message("Pi recusou $command. Verifique a configuração do modelo no terminal."); if (command == "prompt") publish(state.copy(working = false)) }
                    else if (command == "get_state") publish(state.copy(connected = true, message = "Pi conectado • ferramentas exigem aprovação"))
                    else if (command == "prompt" && event.optJSONObject("data")?.optString("disposition") == "handled") publish(state.copy(working = false))
                } }
            }
            "message_update" -> {
                val update = event.optJSONObject("assistantMessageEvent")
                if (update?.optString("type") == "text_delta") main.post { if (generation.get() == version)
                    publish(state.copy(text = (state.text + update.optString("delta")).takeLast(65536))) }
            }
            "agent_settled" -> main.post { if (generation.get() == version) publish(state.copy(working = false,
                message = if (runFailed) "A tarefa falhou. Verifique modelo, chave e rede." else "Pi concluiu • sessão salva")) }
            "message_end" -> {
                if (event.optJSONObject("message")?.optString("stopReason") == "error") main.post {
                    if (generation.get() == version) { runFailed = true; message("O modelo falhou. Verifique modelo, chave e rede.") }
                }
            }
            "extension_ui_request" -> {
                val requestId = event.getString("id")
                if (event.optString("title") != "Android tools" || event.optString("method") != "input") {
                    write(JSONObject().put("type", "extension_ui_response").put("id", requestId).put("cancelled", true), version)
                    return
                }
                if (jobs.size >= 16) { write(JSONObject().put("type", "extension_ui_response").put("id", requestId).put("cancelled", true), version); return }
                val future = FutureTask<Unit>({
                    var toolName = "invalid"
                    val result = try {
                        val call = JSONObject(event.getString("placeholder"))
                        toolName = call.getString("tool").also { require(it in setOf("file", "edit", "shell", "workspace", "clipboard", "share")) }
                        val raw = call.getJSONObject("arguments")
                        val arguments = raw.keys().asSequence().associateWith { key ->
                            require(raw.get(key) is String); raw.getString(key)
                        }
                        check(generation.get() == version)
                        runtime.execute(session, ToolCall(toolName, arguments))
                    } catch (_: Exception) { ToolResult(false, "Tool cancelled or invalid arguments") }
                    try { audit.record(session.id, toolName.take(64), "result", result.success) } catch (_: Exception) { /* tool outcome remains authoritative */ }
                    val text = if (result.text.length > 65536) result.text.take(65536) + "\n[Output truncated at 65536 characters]" else result.text
                    val value = JSONObject().put("success", result.success).put("text", text)
                    write(JSONObject().put("type", "extension_ui_response").put("id", requestId).put("value", value.toString()), version)
                    jobs.remove(requestId)
                    Unit
                })
                jobs[requestId] = future
                tools.execute(future)
            }
        }
    }
    fun prompt(text: String) {
        require(text.isNotBlank() && text.toByteArray(Charsets.UTF_8).size <= 65536)
        if (!state.connected || state.working) { message("Conecte Pi e aguarde a tarefa atual"); return }
        runFailed = false
        publish(state.copy(working = true, text = (state.text + "\nVocê: $text\nPi: ").takeLast(65536), message = "Pi trabalhando…"))
        send("prompt", JSONObject().put("message", text))
    }
    fun cancel() {
        if (process == null) { message("Pi não conectado"); return }
        send("clear_queue"); send("abort")
        jobs.values.forEach { it.cancel(true) }; jobs.clear(); ApprovalBroker.cancelAll()
        message("Cancelamento solicitado")
    }
    fun stop() {
        generation.incrementAndGet()
        jobs.values.forEach { it.cancel(true) }; jobs.clear(); requests.clear()
        process?.destroy(); process = null
        ApprovalBroker.cancelAll()
        publish(State())
    }
    private fun send(command: String, body: JSONObject = JSONObject()) {
        if (process == null) return
        if (requests.size >= 64) { message("Aguarde as respostas pendentes do Pi"); return }
        val id = UUID.randomUUID().toString()
        requests[id] = command
        write(body.put("type", command).put("id", id), generation.get())
    }
    private fun write(record: JSONObject, version: Long) {
        val line = (record.toString() + "\n").toByteArray(Charsets.UTF_8)
        writer.execute { try {
            if (generation.get() == version) process?.outputStream?.let { it.write(line); it.flush() }
        } catch (_: Exception) { main.post { if (generation.get() == version) message("Transporte Pi interrompido") } } }
    }
}
