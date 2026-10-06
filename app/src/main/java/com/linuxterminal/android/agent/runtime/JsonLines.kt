package com.linuxterminal.android.agent.runtime

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** JSONL splits only LF. U+2028/U+2029 inside JSON strings are valid. */
object JsonLines {
    fun read(input: InputStream, limit: Int = 1_048_576, consume: (String) -> Unit) {
        val line = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) { require(line.size() == 0) { "Truncated JSONL" }; return }
            for (index in 0 until count) {
                if (buffer[index] == 10.toByte()) {
                    val text = line.toByteArray().toString(Charsets.UTF_8).removeSuffix("\r")
                    line.reset(); if (text.isNotBlank()) consume(text)
                } else { require(line.size() < limit) { "RPC record too large" }; line.write(buffer[index].toInt()) }
            }
        }
    }
}
