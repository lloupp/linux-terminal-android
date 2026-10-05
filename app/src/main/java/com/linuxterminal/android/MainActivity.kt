package com.linuxterminal.android

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.os.*
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.linuxterminal.android.terminal.*

class MainActivity : Activity() {
    lateinit var terminal: TerminalSurface
        private set
    private var service: TerminalService? = null
    private var bound = false
    private var openKeyboardWhenReady = true
    private val keys = ViewClient()
    private val render: () -> Unit = { terminal.onScreenUpdated() }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as TerminalService.LocalBinder).service
            service!!.observe(render)
            terminal.attachSession(service!!.session)
            terminal.requestFocus()
            if (openKeyboardWhenReady) showTerminalKeyboard()
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = LinearLayout(this)
        toolbar.addView(Button(this).apply {
            text = "Teclado"
            setOnClickListener { showTerminalKeyboard() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        toolbar.addView(Button(this).apply {
            text = "Importar projeto (SAF)"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), 2)
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
        root.addView(toolbar)
        root.addView(TextView(this).apply {
            text = "Shell Android • Debian/Pi ainda indisponíveis"
            setTextColor(0xffeeeeee.toInt())
        })
        terminal = TerminalSurface(this).apply {
            setTerminalViewClient(object : ViewClient() {
                override fun readControlKey() = keys.readControlKey()
                override fun readAltKey() = keys.readAltKey()
                override fun onSingleTapUp(e: android.view.MotionEvent) {
                    showTerminalKeyboard()
                }
            })
            setTextSize((14 * resources.displayMetrics.scaledDensity).toInt())
        }
        root.setBackgroundColor(0xff101010.toInt())
        root.addView(terminal, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val row = LinearLayout(this)
        listOf("Ctrl", "Alt", "Esc", "Tab", "↑", "↓", "←", "→", "C-c", "C-d", "C-z", "Colar").forEach { label ->
            row.addView(Button(this).apply {
                text = label
                setOnClickListener {
                    when (label) {
                        "Ctrl" -> keys.ctrl = !keys.ctrl
                        "Alt" -> keys.alt = !keys.alt
                        "Colar" -> confirmPaste()
                        else -> {
                            val code = when (label) {
                                "Esc" -> android.view.KeyEvent.KEYCODE_ESCAPE
                                "Tab" -> android.view.KeyEvent.KEYCODE_TAB
                                "↑" -> android.view.KeyEvent.KEYCODE_DPAD_UP
                                "↓" -> android.view.KeyEvent.KEYCODE_DPAD_DOWN
                                "←" -> android.view.KeyEvent.KEYCODE_DPAD_LEFT
                                "→" -> android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                                else -> null
                            }
                            if (code != null) terminal.onKeyDown(code, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
                            else service?.session?.write(when(label) { "C-c" -> "\u0003"; "C-d" -> "\u0004"; else -> "\u001a" })
                        }
                    }
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
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && openKeyboardWhenReady && service != null) showTerminalKeyboard()
    }

    internal fun showTerminalKeyboard() {
        openKeyboardWhenReady = true
        terminal.requestFocus()
        // A service connection or permission dialog can arrive before window focus.
        // Defer until the editor is attached and Android can bind its InputConnection.
        terminal.post {
            if (!terminal.hasWindowFocus() || service == null) return@post
            val manager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            manager.restartInput(terminal)
            manager.showSoftInput(terminal, InputMethodManager.SHOW_IMPLICIT)
            openKeyboardWhenReady = false
        }
    }

    private fun confirmPaste() {
        val manager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = manager.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: return
        AlertDialog.Builder(this).setTitle("Colar no shell?")
            .setMessage("O texto pode conter comandos e quebras de linha. Confirme antes de enviar.")
            .setNegativeButton("Cancelar", null).setPositiveButton("Colar") { _, _ ->
                service?.session?.emulator?.paste(text)
            }.show()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 2 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        // The picker action authorizes importing this selected tree only.
        Thread {
            try {
                val workspace = com.linuxterminal.android.workspace.SafWorkspaceImporter(contentResolver,
                    java.io.File(filesDir, "workspaces").apply { mkdirs() }).import(uri)
                runOnUiThread {
                    AlertDialog.Builder(this).setTitle("Projeto importado")
                        .setMessage("Snapshot privado criado. No terminal: cd ../${workspace.name}\nAlterações não são enviadas automaticamente à pasta original.")
                        .setPositiveButton("OK", null).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Importação falhou (${e.javaClass.simpleName})", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
    override fun onStart() {
        super.onStart()
        bound = bindService(Intent(this, TerminalService::class.java), connection, BIND_AUTO_CREATE)
    }
    override fun onStop() {
        service?.unobserve(render)
        if (bound) { unbindService(connection); bound = false }
        service = null
        super.onStop()
    }
}
