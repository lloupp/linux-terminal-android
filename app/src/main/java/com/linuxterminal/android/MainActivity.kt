package com.linuxterminal.android

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.os.*
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.linuxterminal.android.terminal.*
import com.linuxterminal.android.workspace.*
import com.linuxterminal.android.agent.runtime.*

class MainActivity : Activity() {
    lateinit var terminal: TerminalSurface
        private set
    private var service: TerminalService? = null
    private var bound = false
    private var openKeyboardWhenReady = true
    private val keys = ViewClient()
    private lateinit var status: TextView
    private lateinit var restart: Button
    private lateinit var ctrl: Button
    private lateinit var alt: Button
    private lateinit var transferText: TextView
    private lateinit var transferRow: LinearLayout
    private val catalog by lazy { WorkspaceCatalog(this) }
    private val settings by lazy { getSharedPreferences("terminal-settings", MODE_PRIVATE) }
    private var fontSp = 14f
    private var exportWorkspace: String? = null
    private var pendingWorkspace: String? = null
    private var approvalDialog: AlertDialog? = null
    private var approvalId: String? = null
    private val approvalObserver: (ApprovalBroker.Pending?) -> Unit = { item ->
        if (item?.id != approvalId) {
            approvalDialog?.dismiss(); approvalDialog = null; approvalId = item?.id
            if (item != null) {
                val details = TextView(this).apply {
                    setPadding(24, 12, 24, 12)
                    text = "${item.request.tool} • ${item.request.access}\n${item.request.resource}\n\n" +
                        item.request.parameters.entries.joinToString("\n") { "${it.key}: ${it.value}" } +
                        if (item.request.access == com.linuxterminal.android.agent.permissions.Access.DESTRUCTIVE)
                            "\n\nO shell pode acessar dados do aplicativo. Aprovar não cria isolamento de processos." else ""
                }
                approvalDialog = AlertDialog.Builder(this).setTitle("Permitir operação do Pi?")
                    .setView(ScrollView(this).apply { addView(details) })
                    .setNegativeButton("Negar") { _, _ -> ApprovalBroker.answer(item.id, false) }
                    .setPositiveButton("Permitir uma vez") { _, _ -> ApprovalBroker.answer(item.id, true) }
                    .setOnCancelListener { ApprovalBroker.answer(item.id, false) }.create().also { it.show() }
            }
        }
    }
    private var piDialog: AlertDialog? = null
    private var piOutput: TextView? = null
    private var piObserver: ((PiController.State) -> Unit)? = null
    private val render: () -> Unit = {
        service?.let { owner ->
            terminal.attachSession(owner.session)
            terminal.onScreenUpdated()
            val running = owner.session.isRunning
            status.text = "${catalog.title(owner.workspace)} • " +
                if (running) owner.shellDescription else "Sessão encerrada (${owner.session.exitStatus})"
            restart.visibility = if (running) View.GONE else View.VISIBLE
        }
    }
    private val transferObserver: (WorkspaceTransfers.State) -> Unit = { state ->
        transferRow.visibility = if (state.busy) View.VISIBLE else View.GONE
        transferText.text = "${state.label}: ${state.documents} documentos • ${state.bytes / 1024} KiB"
        if (!state.busy && state.message != null) {
            val project = state.resultWorkspace
            WorkspaceTransfers.consumeResult()
            if (project != null) {
                AlertDialog.Builder(this).setTitle("Projeto importado")
                    .setMessage("Uma cópia privada foi criada. Abra para trabalhar e exporte um ZIP ao terminar.")
                    .setNegativeButton("Depois", null).setPositiveButton("Abrir projeto") { _, _ -> openProject(project) }.show()
            } else toast(state.message)
        }
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as TerminalService.LocalBinder).service
            service!!.observe(render)
            pendingWorkspace?.let { openProject(it); pendingWorkspace = null }
            render()
            terminal.requestFocus()
            if (openKeyboardWhenReady) showTerminalKeyboard()
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        exportWorkspace = state?.getString("export-workspace")
        fontSp = settings.getFloat("font-sp", 14f)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xff101010.toInt()) }
        val toolbar = LinearLayout(this)
        listOf("Teclado" to { showTerminalKeyboard() }, "Projetos" to { showProjects() }, "Mais" to { showActions() }).forEach { (label, action) ->
            toolbar.addView(button(label, action), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(toolbar)
        status = TextView(this).apply { setTextColor(0xffeeeeee.toInt()); text = "Abrindo terminal…"; setPadding(12, 0, 12, 0) }
        root.addView(status)
        restart = button("Nova sessão") { service?.let { it.openWorkspace(it.workspace.name, restart = true); render(); showTerminalKeyboard() } }
            .apply { visibility = View.GONE }
        root.addView(restart)
        transferText = TextView(this).apply { setTextColor(0xffeeeeee.toInt()) }
        transferRow = LinearLayout(this).apply {
            visibility = View.GONE
            addView(transferText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(button("Cancelar") { WorkspaceTransfers.cancel() })
        }
        root.addView(transferRow)
        terminal = TerminalSurface(this).apply {
            contentDescription = "Terminal. Toque para digitar; pressione e segure para selecionar texto."
            setTerminalViewClient(object : ViewClient() {
                override fun readControlKey(): Boolean = keys.readControlKey().also { updateModifiers() }
                override fun readAltKey(): Boolean = keys.readAltKey().also { updateModifiers() }
                override fun onSingleTapUp(e: android.view.MotionEvent) { showTerminalKeyboard() }
                override fun onUserCopy(text: String) {
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Terminal", text))
                    toast("Texto copiado")
                }
                override fun onUserPaste() { confirmPaste() }
                override fun onScale(scale: Float): Float {
                    if (scale > 1.08f || scale < 0.92f) { changeFont(if (scale > 1) 1f else -1f); return 1f }
                    return scale
                }
            })
            setTextSize((fontSp * resources.displayMetrics.scaledDensity).toInt())
        }
        root.addView(terminal, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val row = LinearLayout(this)
        ctrl = button("Ctrl") { keys.ctrl = !keys.ctrl; updateModifiers(); showTerminalKeyboard() }
        alt = button("Alt") { keys.alt = !keys.alt; updateModifiers(); showTerminalKeyboard() }
        row.addView(ctrl); row.addView(alt)
        listOf("Esc", "Tab", "↑", "↓", "←", "→", "Ctrl+C", "Ctrl+D", "Ctrl+Z", "Colar").forEach { label ->
            row.addView(button(label) {
                if (label == "Colar") confirmPaste() else {
                    val code = mapOf("Esc" to KeyEvent.KEYCODE_ESCAPE, "Tab" to KeyEvent.KEYCODE_TAB,
                        "↑" to KeyEvent.KEYCODE_DPAD_UP, "↓" to KeyEvent.KEYCODE_DPAD_DOWN,
                        "←" to KeyEvent.KEYCODE_DPAD_LEFT, "→" to KeyEvent.KEYCODE_DPAD_RIGHT)[label]
                    if (code != null) terminal.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))
                    else service?.session?.write(when(label) { "Ctrl+C" -> "\u0003"; "Ctrl+D" -> "\u0004"; else -> "\u001a" })
                    showTerminalKeyboard()
                }
            })
        }
        root.addView(HorizontalScrollView(this).apply { addView(row) })
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val intent = Intent(this, TerminalService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() } }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    private fun updateModifiers() {
        ctrl.text = if (keys.ctrl) "Ctrl ✓" else "Ctrl"
        alt.text = if (keys.alt) "Alt ✓" else "Alt"
        ctrl.contentDescription = "Control ${if (keys.ctrl) "ativado" else "desativado"}"
        alt.contentDescription = "Alt ${if (keys.alt) "ativado" else "desativado"}"
    }
    private fun changeFont(delta: Float) {
        fontSp = (fontSp + delta).coerceIn(10f, 28f)
        settings.edit().putFloat("font-sp", fontSp).apply()
        terminal.setTextSize((fontSp * resources.displayMetrics.scaledDensity).toInt())
    }
    private fun showProjects() {
        val projects = catalog.recent()
        AlertDialog.Builder(this).setTitle("Projetos recentes")
            .setItems(projects.map { catalog.title(it) }.toTypedArray()) { _, index -> openProject(projects[index].name) }
            .setPositiveButton("Importar pasta") { _, _ ->
                if (WorkspaceTransfers.state.busy) toast("Aguarde ou cancele a transferência atual")
                else startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), 2)
            }.setNegativeButton("Fechar", null).show()
    }
    private fun openProject(id: String) {
        if (service == null) { pendingWorkspace = id; return }
        try { service!!.openWorkspace(id); keys.ctrl = false; keys.alt = false; updateModifiers(); render(); showTerminalKeyboard() }
        catch (_: Exception) { toast("Não foi possível abrir. Encerre uma sessão se já houver oito projetos ativos.") }
    }
    private fun showActions() {
        val options = arrayOf("Rascunho / ditado", "Exportar projeto em ZIP", "Ambiente e versões", "Aumentar fonte", "Diminuir fonte", "Preparar / abrir Linux", "Abrir shell Android", "Pi Agent")
        AlertDialog.Builder(this).setTitle("Terminal").setItems(options) { _, index ->
            when(index) {
                0 -> showDraft()
                1 -> {
                    if (WorkspaceTransfers.state.busy) toast("Aguarde a transferência atual")
                    else service?.let {
                        exportWorkspace = it.workspace.name
                        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip")
                            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "${it.workspace.name}.zip"), 3)
                    }
                }
                2 -> showEnvironment()
                3 -> changeFont(1f)
                4 -> changeFont(-1f)
                5 -> prepareLinux()
                6 -> service?.let { it.openWorkspace(it.workspace.name, linuxMode = false); render() }
                7 -> showPi()
            }
        }.show()
    }
    private fun showDraft() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN or
                if (Build.VERSION.SDK_INT >= 26) android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING else 0
            minLines = 4; maxLines = 10; hint = "Digite ou use o microfone do Gboard. Revise antes de inserir."
        }
        val dialog = AlertDialog.Builder(this).setTitle("Rascunho para o terminal").setView(input)
            .setNegativeButton("Cancelar", null).setPositiveButton("Inserir") { _, _ -> confirmText(input.text.toString()) }.create()
        dialog.setOnShowListener {
            input.requestFocus()
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.show()
    }
    private fun confirmText(text: String) {
        if (text.isEmpty()) { toast("Não há texto para inserir"); return }
        if (text.toByteArray(Charsets.UTF_8).size > 65536) { toast("Texto muito longo (limite: 64 KiB)"); return }
        AlertDialog.Builder(this).setTitle("Inserir no terminal?")
            .setMessage(text.take(1000) + "\n\nQuebras de linha podem executar comandos. Confira também o programa ativo no terminal.")
            .setNegativeButton("Cancelar", null).setPositiveButton("Inserir") { _, _ ->
                val active = service?.session
                if (active?.isRunning == true) { active.emulator?.paste(text); showTerminalKeyboard() }
                else toast("Abra uma nova sessão antes de inserir")
            }.show()
    }
    private fun confirmPaste() {
        val text = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text == null) toast("Área de transferência vazia") else confirmText(text)
    }
    private fun showEnvironment() {
        val app = applicationContext
        val workspace = service?.workspace ?: return
        val runtime = com.linuxterminal.android.runtime.LinuxRuntime(app)
        val details = TextView(this).apply { setPadding(24, 12, 24, 12); setTextIsSelectable(true) }
        val header = "Linux Terminal ${BuildConfig.VERSION_NAME}\nAndroid ${Build.VERSION.RELEASE} • ${Build.SUPPORTED_ABIS.firstOrNull()}\n\nShell Android disponível. Linux: ${if (runtime.ready) "preparado" else "não preparado"}."
        details.text = header + if (runtime.ready) "\nVerificando versões…" else "\nPrepare Linux para instalar Node/Git/Pi."
        val dialog = AlertDialog.Builder(this).setTitle("Ambiente").setView(ScrollView(this).apply { addView(details) })
            .setPositiveButton("OK", null).create()
        dialog.show()
        if (runtime.ready) Thread({
            val result = PtyCommandExecutor(runtime, workspace).execute("for program in node npm git pi; do if command -v \"\$program\" >/dev/null 2>&1; then \"\$program\" --version; else printf '%s: não instalado\\n' \"\$program\"; fi; done")
            runOnUiThread { if (dialog.isShowing) details.text = header + "\n\n" + result.text +
                "\n\nAtualizações com mesma assinatura preservam arquivos. Forçar parada encerra processos. Desinstalar remove arquivos: exporte seus projetos." }
        }, "environment-probe").start()
    }
    private fun prepareLinux() {
        val app = applicationContext
        val runtime = com.linuxterminal.android.runtime.LinuxRuntime(app)
        if (!runtime.supported) { toast("Runtime indisponível para esta arquitetura"); return }
        if (runtime.ready) { service?.let { it.openWorkspace(it.workspace.name, linuxMode = true); render(); showTerminalKeyboard() }; return }
        if (WorkspaceTransfers.state.busy) { toast("Aguarde a transferência atual"); return }
        AlertDialog.Builder(this).setTitle("Preparar Linux?")
            .setMessage("Baixa cerca de 4 MB do Alpine oficial e usa pelo menos 128 MB livres. Não instala agentes automaticamente. A preparação só termina se o shell Linux executar. DNS inicial: Cloudflare e Google; configurável em /etc/resolv.conf.")
            .setNegativeButton("Cancelar", null).setPositiveButton("Preparar") { _, _ ->
                WorkspaceTransfers.start("Preparação Linux") { token -> com.linuxterminal.android.runtime.LinuxRuntime(app).prepare(token); null }
            }.show()
    }
    private fun showPi() {
        val owner = service ?: return
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val output = TextView(this).apply { setPadding(16, 8, 16, 8); setTextIsSelectable(true) }
        piOutput = output
        layout.addView(ScrollView(this).apply { addView(output) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 240))
        val prompt = EditText(this).apply {
            hint = "Digite ou dite seu pedido"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = if (Build.VERSION.SDK_INT >= 26) android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING else 0
            minLines = 2; maxLines = 4
        }
        layout.addView(prompt)
        val actions = LinearLayout(this)
        actions.addView(button("Conectar") { PiController.start(applicationContext, owner.workspace.name) }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(button("Enviar") {
            val value = prompt.text.toString()
            if (value.isBlank() || value.toByteArray(Charsets.UTF_8).size > 65536) toast("Informe um pedido com até 64 KiB")
            else { PiController.prompt(value); if (PiController.state.working) prompt.text.clear() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(button("Cancelar") { PiController.cancel() }, LinearLayout.LayoutParams(0, -2, 1f))
        layout.addView(actions)
        layout.addView(button("Modelo e chave") { configurePi() })
        layout.addView(button("Histórico de operações") {
            val history = com.linuxterminal.android.agent.persistence.AgentAudit(java.io.File(filesDir, "agent-sessions")).recent()
            AlertDialog.Builder(this).setTitle("Últimas 50 operações • todos os projetos")
                .setMessage(history).setPositiveButton("Fechar", null).show()
        })
        val help = TextView(this).apply {
            text = "No Linux, instale manualmente: apk add nodejs npm git ca-certificates\nDepois: npm install -g --ignore-scripts @earendil-works/pi-coding-agent@1.0.4\nConfigure seu modelo no Pi antes de conectar. As sessões ficam salvas no aplicativo."
            setTextIsSelectable(true); setPadding(16, 8, 16, 8)
        }
        layout.addView(help)
        piObserver = { state -> output.text = state.message + "\n" + state.text }
        PiController.observe(piObserver!!)
        piDialog = AlertDialog.Builder(this).setTitle("Pi • ${catalog.title(owner.workspace)}").setView(layout)
            .setNegativeButton("Fechar", null).setNeutralButton("Desconectar") { _, _ -> PiController.stop() }.create().also { dialog ->
                dialog.setOnDismissListener { piObserver?.let(PiController::unobserve); piObserver = null; piOutput = null; piDialog = null }
                dialog.show()
            }
    }
    private fun configurePi() {
        val configuration = com.linuxterminal.android.agent.providers.PiConfiguration(this)
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val provider = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, configuration.providers)
            setSelection(configuration.providers.indexOf(configuration.provider).coerceAtLeast(0))
        }
        val model = EditText(this).apply { hint = "ID do modelo"; setText(configuration.model); setSingleLine(true) }
        val key = EditText(this).apply {
            hint = "Chave API (vazia mantém a anterior)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSaveEnabled = false
            imeOptions = if (Build.VERSION.SDK_INT >= 26) android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING else 0
            if (Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        form.addView(provider); form.addView(model); form.addView(key)
        val dialog = AlertDialog.Builder(this).setTitle("Configurar Pi").setView(form)
            .setNegativeButton("Cancelar", null).setNeutralButton("Remover configuração") { _, _ -> configuration.clear(); PiController.stop() }
            .setPositiveButton("Salvar", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                configuration.save(provider.selectedItem.toString(), model.text.toString().trim(), key.text.toString().trim())
                key.text.clear(); PiController.stop(); dialog.dismiss(); toast("Configuração salva no Keystore. Conecte Pi novamente.")
            } catch (_: Exception) { toast("Verifique o ID do modelo e a chave informada") }
        } }
        dialog.show()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && openKeyboardWhenReady && service != null) showTerminalKeyboard()
    }
    internal fun showTerminalKeyboard() {
        openKeyboardWhenReady = true
        terminal.requestFocus()
        terminal.post {
            if (!terminal.hasWindowFocus() || service == null) return@post
            val manager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            manager.restartInput(terminal); manager.showSoftInput(terminal, InputMethodManager.SHOW_IMPLICIT)
            openKeyboardWhenReady = false
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) { if (requestCode == 3) exportWorkspace = null; return }
        val uri = data?.data ?: return
        if (WorkspaceTransfers.state.busy) { toast("Aguarde a transferência atual"); return }
        if (requestCode == 2) WorkspaceTransfers.import(this, uri)
        if (requestCode == 3) {
            exportWorkspace?.let { WorkspaceTransfers.export(this, it, uri) }
            exportWorkspace = null
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("export-workspace", exportWorkspace)
        super.onSaveInstanceState(outState)
    }
    override fun onStart() {
        super.onStart()
        WorkspaceTransfers.observe(transferObserver)
        ApprovalBroker.observe(approvalObserver)
        bound = bindService(Intent(this, TerminalService::class.java), connection, BIND_AUTO_CREATE)
    }
    override fun onStop() {
        WorkspaceTransfers.unobserve(transferObserver)
        ApprovalBroker.unobserve(approvalObserver)
        approvalDialog?.dismiss(); approvalDialog = null; approvalId = null
        piDialog?.dismiss()
        service?.unobserve(render)
        if (bound) { unbindService(connection); bound = false }
        service = null
        super.onStop()
    }
}
