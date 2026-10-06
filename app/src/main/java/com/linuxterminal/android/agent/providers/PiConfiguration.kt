package com.linuxterminal.android.agent.providers

import android.content.Context
import com.linuxterminal.android.agent.persistence.SecretStore

class PiConfiguration(private val context: Context) {
    private val prefs = context.getSharedPreferences("pi-model", Context.MODE_PRIVATE)
    val provider get() = prefs.getString("provider", "") ?: ""
    val model get() = prefs.getString("model", "") ?: ""
    val providers = listOf("openai", "anthropic", "google")
    fun save(provider: String, model: String, key: String) {
        require(provider in providers)
        require(Regex("[a-zA-Z0-9_./:-]{1,160}").matches(model))
        require(key.length <= 8192)
        if (key.isNotBlank()) SecretStore(context).put(provider, key)
        check(prefs.edit().putString("provider", provider).putString("model", model).commit())
    }
    fun clear() { if (provider.isNotEmpty()) SecretStore(context).remove(provider); prefs.edit().clear().commit() }
    fun cli(): String = if (provider in providers && Regex("[a-zA-Z0-9_./:-]{1,160}").matches(model))
        " --provider '$provider' --model '$model'" else ""
    fun environment(): Map<String, String> {
        val name = mapOf("openai" to "OPENAI_API_KEY", "anthropic" to "ANTHROPIC_API_KEY", "google" to "GEMINI_API_KEY")[provider] ?: return emptyMap()
        val value = SecretStore(context).get(provider) ?: return emptyMap()
        return mapOf(name to value)
    }
}
