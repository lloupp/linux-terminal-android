package com.linuxterminal.android.terminal

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.linuxterminal.android.MainActivity
import com.termux.terminal.TerminalSession
import java.io.File
import com.linuxterminal.android.workspace.WorkspaceCatalog

/** Owns PTY beyond Activity recreation. Android may still kill the whole process. */
class TerminalService : Service() {
    inner class LocalBinder : Binder() { val service get() = this@TerminalService }
    private val binder = LocalBinder()
    lateinit var session: TerminalSession
        private set
    lateinit var workspace: File
        private set
    private val sessions = linkedMapOf<String, TerminalSession>()
    private val catalog by lazy { WorkspaceCatalog(this) }
    private val linux by lazy { com.linuxterminal.android.runtime.LinuxRuntime(this) }
    var useLinux = false
        private set
    val shellDescription get() = if (useLinux) "Linux Alpine" else "Shell Android"
    private val listeners = mutableSetOf<() -> Unit>()
    val backend: ShellBackend by lazy { AndroidShellBackend(File(filesDir, "home").apply { mkdirs() }) }

    override fun onCreate() {
        super.onCreate()
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("terminal", "Terminal em execução", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, TerminalService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = if (android.os.Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "terminal") else Notification.Builder(this)
        startForeground(1, builder.setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Terminal ativo").setContentText("Shell Android • toque para voltar")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(
                null, "Encerrar", stop).build()).build())
        openWorkspace(catalog.default().name)
    }
    /** Project changes attach a separate shell and preserve existing live sessions. */
    fun openWorkspace(id: String, restart: Boolean = false, linuxMode: Boolean = useLinux) {
        val target = catalog.resolve(id)
        if (linuxMode) check(linux.ready)
        val key = "$id:${if (linuxMode) "linux" else "android"}"
        val previous = sessions[key]
        if (restart && previous?.isRunning == true) error("Close this session before restarting")
        if (previous == null && sessions.size >= 8) {
            val expired = sessions.entries.firstOrNull { !it.value.isRunning }
                ?: error("Eight sessions are active. End one before opening another project.")
            sessions.remove(expired.key)
        }
        val next = if (previous != null && !restart) previous else createSession(target, linuxMode).also { sessions[key] = it }
        workspace = target
        useLinux = linuxMode
        session = next
        catalog.record(target)
        listeners.toList().forEach { it() }
    }
    private fun createSession(workspace: File, linuxMode: Boolean): TerminalSession {
        val launch = (if (linuxMode) linux else backend).launch(workspace)
        return TerminalSession(launch.executable, launch.cwd.absolutePath, launch.args, launch.env,
            5000, object : SessionClient() {
                override fun onTextChanged(session: TerminalSession) { listeners.toList().forEach { it() } }
                override fun onColorsChanged(session: TerminalSession) { listeners.toList().forEach { it() } }
                override fun onSessionFinished(session: TerminalSession) { listeners.toList().forEach { it() } }
            }).apply { initializeEmulator(80, 24, 0, 0) }
    }
    fun observe(listener: () -> Unit) { listeners.add(listener) }
    fun unobserve(listener: () -> Unit) { listeners.remove(listener) }
    override fun onBind(intent: Intent): IBinder = binder
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") stopSelf()
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        com.linuxterminal.android.agent.runtime.PiController.stop()
        sessions.values.forEach { it.finishIfRunning() }
        sessions.clear()
        listeners.clear()
        super.onDestroy()
    }
}
