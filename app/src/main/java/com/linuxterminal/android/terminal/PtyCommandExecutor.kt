package com.linuxterminal.android.terminal

import android.os.Handler
import android.os.Looper
import com.linuxterminal.android.agent.tools.ToolResult
import com.termux.terminal.TerminalSession
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Uses the same backend and PTY engine as the terminal, with a dedicated session per job.
 * PTYs merge stdout/stderr; result is rendered transcript, not a byte-exact machine protocol.
 * Must run on a worker thread. RPC will need a separate pipe-based transport later.
 */
class PtyCommandExecutor(private val backend: ShellBackend, private val workspace: File) {
    @Synchronized fun execute(command: String): ToolResult {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Worker thread required" }
        require(command.length <= 65536 && !command.contains('\u0000'))
        val future = CompletableFuture<ToolResult>()
        val handler = Handler(Looper.getMainLooper())
        var session: TerminalSession? = null
        handler.post {
            try {
                val launch = backend.command(workspace, command)
                session = TerminalSession(launch.executable, launch.cwd.absolutePath,
                    launch.args, launch.env, 2000,
                    object : SessionClient() {
                        override fun onSessionFinished(finished: TerminalSession) {
                            future.complete(ToolResult(finished.exitStatus == 0,
                                finished.emulator?.screen?.transcriptText?.takeLast(65536) ?: ""))
                        }
                    })
                session!!.initializeEmulator(120, 40, 0, 0)
            } catch (e: Exception) { future.complete(ToolResult(false, "PTY launch failed")) }
        }
        return try { future.get(30, TimeUnit.SECONDS) }
        catch (e: Exception) { ToolResult(false, "Command timed out or interrupted") }
        finally { handler.post { session?.finishIfRunning() } }
    }
}
