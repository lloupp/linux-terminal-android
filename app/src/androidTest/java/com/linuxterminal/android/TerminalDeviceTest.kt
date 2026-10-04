package com.linuxterminal.android

import android.app.Instrumentation
import android.content.Intent
import android.os.Looper
import android.view.inputmethod.EditorInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.linuxterminal.android.terminal.*
import com.termux.terminal.TerminalSession
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real native PTY on an Android emulator/device. No mocked shell or transport. */
class TerminalDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    @Test fun ptyExecutesAndRetainsFiles() {
        val context = instrumentation.targetContext
        val workspace = context.filesDir.resolve("test-workspace").apply { mkdirs() }
        val executor = PtyCommandExecutor(AndroidShellBackend(context.filesDir), workspace)
        val first = executor.execute("printf 'ação 😀' > retained.txt; cat retained.txt; tty; test -t 0")
        assertTrue(first.text, first.success)
        assertTrue(first.text, first.text.contains("ação 😀"))
        assertTrue(first.text, first.text.contains("/dev/pts/"))
        val second = executor.execute("cat retained.txt")
        assertTrue(second.success); assertTrue(second.text.contains("ação 😀"))
    }
    @Test fun sessionMetadataAndKeystorePersistWithoutPlaintextSecret() {
        val context = instrumentation.targetContext
        val store = com.linuxterminal.android.agent.persistence.SessionStore(context.filesDir.resolve("agent-sessions"))
        val session = com.linuxterminal.android.agent.sessions.AgentSession(workspace = "default", provider = "test")
        store.save(session)
        assertEquals(session, store.load(session.id))
        val secret = com.linuxterminal.android.agent.persistence.SecretStore(context)
        try {
            secret.put("test", "device-test-value")
            assertEquals("device-test-value", secret.get("test"))
            val encrypted = context.getSharedPreferences("provider-secrets", 0).getString("test", "")!!
            assertFalse(encrypted.contains("device-test-value"))
        } finally { secret.remove("test") }
        assertNull(secret.get("test"))
    }
    @Test fun controlCInterruptsRealForegroundProcess() {
        val finished = CountDownLatch(1)
        var result = ""
        var exit = 0
        var sent = false
        var active: TerminalSession? = null
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            active = TerminalSession("/system/bin/sh", context.filesDir.path,
                arrayOf("/system/bin/sh", "-c", "trap 'printf interrupted; exit 7' INT; printf ready; sleep 30"),
                arrayOf("PATH=/system/bin", "TERM=xterm-256color"), 500,
                object : SessionClient() {
                    override fun onTextChanged(session: TerminalSession) {
                        if (!sent && session.emulator.screen.transcriptText.contains("ready")) {
                            sent = true; session.write("\u0003")
                        }
                    }
                    override fun onSessionFinished(session: TerminalSession) {
                        exit = session.exitStatus
                        result = session.emulator.screen.transcriptText
                        finished.countDown()
                    }
                })
            active!!.initializeEmulator(80, 24, 0, 0)
        }
        try {
            assertTrue("Ctrl+C did not interrupt", finished.await(15, TimeUnit.SECONDS))
            assertEquals(result, 7, exit)
            assertTrue(result, result.contains("interrupted"))
        } finally { instrumentation.runOnMainSync { active?.finishIfRunning() } }
    }
    @Test fun resizeReachesPty() {
        val finished = CountDownLatch(1)
        var result = ""
        var session: TerminalSession? = null
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            session = TerminalSession("/system/bin/sh", context.filesDir.path,
                arrayOf("/system/bin/sh", "-c", "read line; stty size"),
                arrayOf("PATH=/system/bin", "TERM=xterm-256color"), 500,
                object : SessionClient() {
                    override fun onSessionFinished(done: TerminalSession) {
                        result = done.emulator.screen.transcriptText; finished.countDown()
                    }
                })
            session!!.initializeEmulator(80, 24, 0, 0)
            session!!.updateSize(100, 30, 0, 0)
            session!!.write("go\r")
        }
        try {
            assertTrue(finished.await(15, TimeUnit.SECONDS))
            assertTrue(result, result.contains("30 100"))
        } finally { instrumentation.runOnMainSync { session?.finishIfRunning() } }
    }
    @Test fun inputConnectionComposesUnicodeWithoutDuplicateWrites() {
        val finished = CountDownLatch(1)
        var output = ""
        var session: TerminalSession? = null
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            session = TerminalSession("/system/bin/sh", context.filesDir.path,
                arrayOf("/system/bin/sh", "-c", "read line; printf '__RESULT__%s__END__' \"\$line\""),
                arrayOf("PATH=/system/bin", "TERM=xterm-256color"), 500,
                object : SessionClient() {
                    override fun onSessionFinished(done: TerminalSession) {
                        output = done.emulator.screen.transcriptText
                        finished.countDown()
                    }
                })
            val view = TerminalSurface(context)
            view.setTerminalViewClient(ViewClient())
            view.setTextSize(20)
            view.layout(0, 0, 800, 480)
            view.attachSession(session!!)
            assertNotNull(session!!.emulator)
            val attrs = EditorInfo()
            val input = view.onCreateInputConnection(attrs)!!
            assertEquals(android.text.InputType.TYPE_CLASS_TEXT,
                attrs.inputType and android.text.InputType.TYPE_MASK_CLASS)
            input.setComposingText("parc", 1)
            input.setComposingText("ação 😀", 1)
            input.commitText("ação 😀", 1)
            input.finishComposingText()
            input.commitText("\n", 1)
        }
        try {
            assertTrue("PTY did not finish", finished.await(15, TimeUnit.SECONDS))
            assertTrue(output, output.contains("__RESULT__ação 😀__END__"))
            assertFalse(output, output.contains("parc"))
        } finally { instrumentation.runOnMainSync { session?.finishIfRunning() } }
    }
}
