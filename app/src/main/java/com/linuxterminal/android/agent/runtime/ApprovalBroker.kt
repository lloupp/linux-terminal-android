package com.linuxterminal.android.agent.runtime

import android.os.Handler
import android.os.Looper
import com.linuxterminal.android.agent.permissions.*
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** No activity is held while an agent awaits a decision. Background defaults to waiting/denial. */
object ApprovalBroker : PermissionGate {
    data class Pending(val id: String, val request: PermissionRequest, val answer: CompletableFuture<Boolean>)
    private val main = Handler(Looper.getMainLooper())
    private val queue = mutableListOf<Pending>()
    private val listeners = mutableSetOf<(Pending?) -> Unit>()
    fun observe(listener: (Pending?) -> Unit) { listeners.add(listener); listener(queue.firstOrNull()) }
    fun unobserve(listener: (Pending?) -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() { listeners.toList().forEach { it(queue.firstOrNull()) } }
    fun answer(id: String, allowed: Boolean) {
        queue.firstOrNull { it.id == id }?.let { it.answer.complete(allowed); queue.remove(it); notifyListeners() }
    }
    fun cancelAll() { queue.toList().forEach { it.answer.complete(false) }; queue.clear(); notifyListeners() }
    override fun allows(request: PermissionRequest): Boolean {
        check(Looper.myLooper() != Looper.getMainLooper())
        val item = Pending(UUID.randomUUID().toString(), request, CompletableFuture())
        main.post { if (!item.answer.isDone) { queue.add(item); notifyListeners() } }
        return try { item.answer.get(120, TimeUnit.SECONDS) } catch (_: Exception) { false }
        finally { item.answer.complete(false); main.post { queue.remove(item); notifyListeners() } }
    }
}
