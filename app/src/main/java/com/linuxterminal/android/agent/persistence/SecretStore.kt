package com.linuxterminal.android.agent.persistence

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Non-exportable Android Keystore key; encrypted preferences only, excluded from backup. */
class SecretStore(context: Context) {
    private val preferences = context.getSharedPreferences("provider-secrets", Context.MODE_PRIVATE)
    private val alias = "linux-terminal-provider-key-v1"
    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun put(provider: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        cipher.updateAAD(provider.toByteArray(Charsets.UTF_8))
        val data = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString(provider, Base64.encodeToString(data, Base64.NO_WRAP)).commit())
    }
    fun get(provider: String): String? {
        val stored = preferences.getString(provider, null) ?: return null
        val data = Base64.decode(stored, Base64.NO_WRAP)
        require(data.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        }
        cipher.updateAAD(provider.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(data.copyOfRange(12, data.size)).toString(Charsets.UTF_8)
    }
    fun remove(provider: String) { check(preferences.edit().remove(provider).commit()) }
}
