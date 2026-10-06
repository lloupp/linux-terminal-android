package com.linuxterminal.android.terminal

import java.io.File

/** Both interactive terminals and future agent jobs must obtain launches here. */
data class ShellLaunch(val executable: String, val cwd: File, val args: Array<String>, val env: Array<String>)
interface ShellBackend {
    fun launch(workspace: File): ShellLaunch
    fun command(workspace: File, command: String): ShellLaunch {
        val launch = launch(workspace)
        return launch.copy(args = arrayOf(launch.executable, "-c", command))
    }
}
class AndroidShellBackend(private val home: File) : ShellBackend {
    override fun launch(workspace: File) = ShellLaunch("/system/bin/sh", workspace,
        arrayOf("/system/bin/sh", "-i"), arrayOf(
            "HOME=${home.absolutePath}", "PATH=/system/bin:/system/xbin", "TERM=xterm-256color",
            "LANG=C.UTF-8", "TMPDIR=${File(home, "tmp").apply { mkdirs() }.absolutePath}"
        ))
}
