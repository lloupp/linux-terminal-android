package com.linuxterminal.android

import androidx.test.platform.app.InstrumentationRegistry
import com.linuxterminal.android.runtime.LinuxRuntime
import com.linuxterminal.android.agent.runtime.PiController
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in network proof. Default CI never calls a paid model or installs agents silently. */
class NetworkRuntimeProof {
    @Test fun installsNodeGitAndPinnedPiThenConnectsRealRpc() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runtimeNetworkProof") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val runtime = LinuxRuntime(context)
        if (!runtime.ready) runtime.install(instrumentation.context.assets.open("alpine-x86.tar.gz"))
        val workspace = context.filesDir.resolve("workspaces/network-proof").apply { mkdirs() }
        val launch = runtime.command(workspace,
            "apk add --no-cache nodejs npm git ca-certificates && npm install -g --ignore-scripts @earendil-works/pi-coding-agent@1.0.4 && node --version && git --version && pi --version")
        val process = ProcessBuilder(*launch.args).redirectErrorStream(true).apply {
            environment().putAll(launch.env.associate { it.substringBefore('=') to it.substringAfter('=') })
        }.start()
        val output = StringBuilder()
        val reader = Thread {
            process.inputStream.bufferedReader().use { input ->
                val buffer = CharArray(4096)
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    synchronized(output) { output.append(buffer, 0, count); if (output.length > 65536) output.delete(0, output.length - 65536) }
                }
            }
        }.apply { start() }
        try {
            assertTrue("Network proof timed out", process.waitFor(8, TimeUnit.MINUTES))
            reader.join(2000)
            val text = synchronized(output) { output.toString() }
            assertEquals(text, 0, process.exitValue())
            assertTrue(text, text.contains("git version"))
            assertTrue(text, text.contains("1.0.4"))
            assertTrue(text, Regex("v(2[4-9]|[3-9][0-9])\\.").containsMatchIn(text))
            val connected = CountDownLatch(1)
            val observer: (PiController.State) -> Unit = { if (it.connected) connected.countDown() }
            instrumentation.runOnMainSync { PiController.observe(observer); PiController.start(context, "network-proof") }
            try { assertTrue("Real Android Pi RPC did not connect", connected.await(60, TimeUnit.SECONDS)) }
            finally { instrumentation.runOnMainSync { PiController.unobserve(observer); PiController.stop() } }
        } finally { process.destroy() }
    }
}
