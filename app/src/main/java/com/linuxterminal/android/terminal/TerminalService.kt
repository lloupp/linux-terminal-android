package com.linuxterminal.android.terminal

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.linuxterminal.android.MainActivity
import com.termux.terminal.TerminalSession
import java.io.File

/** Owns PTY beyond Activity recreation. Android may still kill the whole process. */
class TerminalService : Service() {
    inner class LocalBinder : Binder() { val service get() = this@TerminalService }
    private val binder = LocalBinder()
    lateinit var session: TerminalSession
        private set
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
        val workspace = File(filesDir, "workspaces/default").apply { mkdirs() }
        val launch = backend.launch(workspace)
        session = TerminalSession(launch.executable, launch.cwd.absolutePath, launch.args, launch.env,
            5000, object : SessionClient() {
                override fun onTextChanged(session: TerminalSession) { listeners.toList().forEach { it() } }
                override fun onColorsChanged(session: TerminalSession) { listeners.toList().forEach { it() } }
                override fun onSessionFinished(session: TerminalSession) { listeners.toList().forEach { it() } }
            })
    }
    fun observe(listener: () -> Unit) { listeners.add(listener) }
    fun unobserve(listener: () -> Unit) { listeners.remove(listener) }
    override fun onBind(intent: Intent): IBinder = binder
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") stopSelf()
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        session.finishIfRunning()
        listeners.clear()
        super.onDestroy()
    }
}
