package com.linuxterminal.android.runtime

import java.io.File
import java.io.InputStream

/** Bounded ustar extraction into a fresh staging directory. Links are created last. */
class SafeTar(private val symlink: (String, File) -> Unit) {
    fun extract(input: InputStream, root: File, cancelled: () -> Unit = {}) {
        val base = root.canonicalFile
        require(base.isDirectory && base.listFiles()?.isEmpty() == true)
        val links = mutableListOf<Pair<File, String>>()
        var entries = 0
        var total = 0L
        fun field(header: ByteArray, start: Int, size: Int): String =
            header.copyOfRange(start, start + size).takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)
        fun readExactly(bytes: ByteArray) {
            var offset = 0
            while (offset < bytes.size) {
                cancelled()
                val count = input.read(bytes, offset, bytes.size - offset)
                require(count > 0) { "Truncated archive" }; offset += count
            }
        }
        while (true) {
            cancelled()
            val header = ByteArray(512); readExactly(header)
            if (header.all { it == 0.toByte() }) break
            require(++entries <= 20000)
            val expected = field(header, 148, 8).trim().toLong(8)
            val checksum = header.indices.sumOf { if (it in 148..155) 32 else header[it].toInt() and 255 }
            require(checksum.toLong() == expected) { "Invalid tar checksum" }
            val prefix = field(header, 345, 155)
            val name = (if (prefix.isEmpty()) "" else "$prefix/") + field(header, 0, 100)
            require(!name.startsWith('/') && !name.contains('\\'))
            val clean = name.removePrefix("./").trimEnd('/')
            if (clean.isEmpty() || clean == ".") continue
            require(clean.split('/').none { it == ".." || it.isEmpty() })
            val target = File(base, clean).canonicalFile
            require(target.path.startsWith(base.path + File.separator))
            val size = field(header, 124, 12).trim().ifEmpty { "0" }.toLong(8)
            require(size in 0..128L * 1024 * 1024)
            total += size; require(total <= 512L * 1024 * 1024)
            require(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
            val type = header[156].toInt().toChar()
            when (type) {
                '0', '\u0000' -> {
                    require(!target.exists())
                    target.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var remaining = size
                        while (remaining > 0) {
                            cancelled()
                            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                            require(count > 0); output.write(buffer, 0, count); remaining -= count
                        }
                    }
                    val mode = field(header, 100, 8).trim().toInt(8)
                    if (mode and 73 != 0) require(target.setExecutable(true, false))
                }
                '5' -> { require(size == 0L); require(target.mkdirs() || target.isDirectory) }
                '2' -> {
                    require(size == 0L && !target.exists())
                    val destination = field(header, 157, 100)
                    require(destination.isNotEmpty() && !destination.contains('\\'))
                    // Absolute guest links are translated to safe relative links inside staging.
                    val resolved = if (destination.startsWith('/')) File(base, destination.removePrefix("/"))
                        else File(target.parentFile, destination)
                    require(resolved.canonicalPath == base.path || resolved.canonicalPath.startsWith(base.path + File.separator))
                    val parent = target.parentFile!!
                    val parentDepth = if (parent == base) 0 else parent.relativeTo(base).invariantSeparatorsPath.split('/').size
                    links.add(target to ("../".repeat(parentDepth) + resolved.canonicalFile.relativeTo(base).invariantSeparatorsPath))
                }
                else -> error("Unsupported tar entry")
            }
            val padding = ((512 - size % 512) % 512).toInt()
            if (padding > 0) readExactly(ByteArray(padding))
        }
        links.forEach { (file, destination) ->
            cancelled()
            require(!file.exists() && file.canonicalPath.startsWith(base.path + File.separator))
            symlink(destination, file)
        }
    }
}
