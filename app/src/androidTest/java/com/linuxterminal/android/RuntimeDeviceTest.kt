package com.linuxterminal.android

import androidx.test.platform.app.InstrumentationRegistry
import com.linuxterminal.android.runtime.LinuxRuntime
import com.linuxterminal.android.terminal.PtyCommandExecutor
import org.junit.Assert.*
import org.junit.Test

class RuntimeDeviceTest {
    @Test fun pinnedAlpineExecutesFromPrivateRootfsOnTarget34() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val runtime = LinuxRuntime(context)
        assertTrue("Packaged PRoot and loader missing", runtime.supported)
        if (!runtime.ready) runtime.install(instrumentation.context.assets.open("alpine-x86.bin"))
        val workspace = context.filesDir.resolve("runtime-test").apply { mkdirs() }
        val executor = PtyCommandExecutor(runtime, workspace)
        val result = executor.execute("printf 'ação 😀' > /workspace/proof.txt; cat /workspace/proof.txt; cat /etc/alpine-release; /bin/busybox uname -m; test -t 0")
        assertTrue(result.text, result.success)
        assertTrue(result.text, result.text.contains("ação 😀"))
        assertTrue(result.text, result.text.contains("3.23.0"))
        assertEquals("ação 😀", workspace.resolve("proof.txt").readText())
    }
}
