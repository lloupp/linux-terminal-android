package com.linuxterminal.android

import android.content.*
import androidx.test.platform.app.InstrumentationRegistry
import com.linuxterminal.android.terminal.TerminalService
import com.linuxterminal.android.terminal.SessionClient
import com.linuxterminal.android.workspace.WorkspaceCatalog
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ProductDeviceTest {
    @Test fun projectsRetainLiveShellAndRestartClosedSession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val ready = CountDownLatch(1)
        var owner: TerminalService? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: android.os.IBinder) {
                owner = (binder as TerminalService.LocalBinder).service; ready.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {}
        }
        assertTrue(context.bindService(Intent(context, TerminalService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue(ready.await(15, TimeUnit.SECONDS))
            lateinit var original: com.termux.terminal.TerminalSession
            val id = "test-project-${System.nanoTime()}"
            instrumentation.runOnMainSync {
                val catalog = WorkspaceCatalog(context)
                catalog.root.resolve(id).mkdirs()
                owner!!.openWorkspace("default")
                original = owner!!.session
                owner!!.openWorkspace(id)
                assertNotSame(original, owner!!.session)
                owner!!.openWorkspace("default")
                assertSame(original, owner!!.session)
                owner!!.openWorkspace(id)
                owner!!.session.write("exit\r")
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            var running = true
            while (running && System.nanoTime() < deadline) {
                instrumentation.runOnMainSync { running = owner!!.session.isRunning }; Thread.sleep(50)
            }
            assertFalse("exit must end the shell", running)
            instrumentation.runOnMainSync {
                val closed = owner!!.session
                owner!!.openWorkspace(id, restart = true)
                assertNotSame(closed, owner!!.session)
                assertTrue(owner!!.session.isRunning)
                owner!!.session.finishIfRunning()
                owner!!.openWorkspace("default")
                WorkspaceCatalog(context).root.resolve(id).deleteRecursively()
            }
        } finally { context.unbindService(connection) }
    }
    @Test fun manualCopyWorksWhileProcessClipboardRequestsRemainDenied() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.executeShellCommand("pm grant ${instrumentation.targetContext.packageName} android.permission.POST_NOTIFICATIONS").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
        }
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val manager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                activity.terminal.mClient.onUserCopy("ação 😀")
                assertEquals("ação 😀", manager.primaryClip!!.getItemAt(0).text.toString())
                val session = com.termux.terminal.TerminalSession("/system/bin/sh", activity.filesDir.path,
                    arrayOf("/system/bin/sh"), arrayOf("PATH=/system/bin"), 500, SessionClient())
                val client = SessionClient()
                client.onCopyTextToClipboard(session, "unauthorized")
                client.onPasteTextFromClipboard(session)
                assertEquals("ação 😀", manager.primaryClip!!.getItemAt(0).text.toString())
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
