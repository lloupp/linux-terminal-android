package com.linuxterminal.android.runtime

import android.content.Context
import android.os.Build
import android.os.StatFs
import android.system.Os
import com.linuxterminal.android.terminal.ShellBackend
import com.linuxterminal.android.terminal.ShellLaunch
import com.linuxterminal.android.workspace.TransferControl
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** Pinned Alpine userspace. PRoot is not an OS security boundary. */
class LinuxRuntime(private val context: Context) : ShellBackend {
    data class Distribution(val arch: String, val sha256: String)
    val distribution = when(Build.SUPPORTED_ABIS.firstOrNull()) {
        "x86_64" -> Distribution("x86_64", "ce8f782f1628d046fb6360eff880b898e5205ed91106d9d14ff4fcb97431bbde")
        "arm64-v8a" -> Distribution("aarch64", "5552106ac866be0c46fdff7a2991a1ed85c0464301a1ac87454c41739d5b6431")
        else -> Distribution("unsupported", "")
    }
    private val parent = File(context.filesDir, "linux").apply { mkdirs() }
    val root = File(parent, "alpine-3.23.0-${distribution.arch}")
    private val proot = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
    private val loader = File(context.applicationInfo.nativeLibraryDir, "libproot-loader.so")
    val ready: Boolean get() = File(root, ".runtime-ready").isFile && proot.isFile && loader.isFile
    val supported: Boolean get() = distribution.arch != "unsupported" && proot.isFile && loader.isFile
    fun environment(): Array<String> = arrayOf("HOME=/root", "PATH=/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin",
        "TERM=xterm-256color", "LANG=C.UTF-8", "TMPDIR=/tmp", "PROOT_LOADER=${loader.path}",
        "PROOT_TMP_DIR=${context.cacheDir.path}", "PROOT_NO_SECCOMP=1")
    private fun arguments(root: File, workspace: File): Array<String> = arrayOf(proot.path,
        "--kill-on-exit", "-0", "-r", root.path, "-b", "/dev", "-b", "/proc", "-b", "/sys",
        "-b", "${workspace.path}:/workspace", "-b", "${File(context.filesDir, "home").apply { mkdirs() }.path}:/root", "-w", "/workspace")
    override fun launch(workspace: File): ShellLaunch {
        check(ready) { "Linux not prepared" }
        return ShellLaunch(proot.path, workspace, arguments(root, workspace) + arrayOf("/bin/sh", "-l"), environment())
    }
    override fun command(workspace: File, command: String): ShellLaunch {
        check(ready)
        return ShellLaunch(proot.path, workspace, arguments(root, workspace) + arrayOf("/bin/sh", "-c", command), environment())
    }
    @Synchronized fun prepare(control: TransferControl = TransferControl()) {
        if (ready) return
        check(supported) { "Unsupported ABI" }
        require(StatFs(parent.path).availableBytes >= 128L * 1024 * 1024) { "Not enough free storage" }
        val archive = File.createTempFile("linux-", ".tar.gz", context.cacheDir)
        try {
            val address = "https://dl-cdn.alpinelinux.org/alpine/v3.23/releases/${distribution.arch}/alpine-minirootfs-3.23.0-${distribution.arch}.tar.gz"
            val connection = URL(address).openConnection().apply { connectTimeout = 15000; readTimeout = 15000 }
            connection.getInputStream().use { input -> archive.outputStream().use { output ->
                val buffer = ByteArray(8192); var total = 0L
                while (true) {
                    control.check(); val count = input.read(buffer); if (count < 0) break
                    total += count; require(total <= 8L * 1024 * 1024)
                    output.write(buffer, 0, count); control.report(0, total)
                }
            } }
            install(archive.inputStream(), control)
        } finally { archive.delete() }
    }
    /** Device tests use the exact official, hash-pinned archive. */
    @Synchronized fun install(input: InputStream, control: TransferControl = TransferControl()) {
        check(supported)
        val archive = File.createTempFile("rootfs-", ".tar.gz", context.cacheDir)
        val staging = File(parent, ".stage-${UUID.randomUUID()}").apply { check(mkdir()) }
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            input.use { source -> archive.outputStream().use { output ->
                val buffer = ByteArray(8192); var bytes = 0L
                while (true) {
                    control.check(); val count = source.read(buffer); if (count < 0) break
                    bytes += count; require(bytes <= 8L * 1024 * 1024)
                    digest.update(buffer, 0, count); output.write(buffer, 0, count)
                }
            } }
            require(digest.digest().joinToString("") { "%02x".format(it) } == distribution.sha256) { "Rootfs checksum mismatch" }
            GZIPInputStream(archive.inputStream()).use { stream ->
                SafeTar { destination, file -> Os.symlink(destination, file.path) }.extract(stream, staging, control::check)
            }
            File(staging, "etc/resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
            val probe = ProcessBuilder(*(arguments(staging, staging) + arrayOf("/bin/sh", "-c", "printf runtime-start && /bin/busybox uname -m && printf runtime-ok")))
                .redirectErrorStream(true).apply { environment().putAll(this@LinuxRuntime.environment().associate { it.substringBefore('=') to it.substringAfter('=') }) }.start()
            try {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
                var exited = false
                while (System.nanoTime() < deadline) {
                    control.check()
                    try { probe.exitValue(); exited = true; break } catch (_: IllegalThreadStateException) { Thread.sleep(25) }
                }
                check(exited) { "Runtime probe timeout" }
                val output = probe.inputStream.bufferedReader().readText().take(4096)
                check(probe.exitValue() == 0 && output.contains("runtime-ok")) { "Runtime probe failed: ${output.take(2048)}" }
            } finally { probe.destroy() }
            control.check()
            check(!root.exists()) { "Existing Linux data preserved" }
            File(staging, ".runtime-ready").writeText(distribution.sha256)
            check(staging.renameTo(root))
        } finally { archive.delete(); if (staging.exists()) staging.deleteRecursively() }
    }
}
