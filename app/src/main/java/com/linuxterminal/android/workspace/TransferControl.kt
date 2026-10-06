package com.linuxterminal.android.workspace

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class TransferControl(private val progress: (Int, Long) -> Unit = { _, _ -> }) {
    private val cancelled = AtomicBoolean(false)
    fun cancel() { cancelled.set(true) }
    fun check() { if (cancelled.get() || Thread.currentThread().isInterrupted) throw CancellationException() }
    fun report(documents: Int, bytes: Long) { check(); progress(documents, bytes) }
}
