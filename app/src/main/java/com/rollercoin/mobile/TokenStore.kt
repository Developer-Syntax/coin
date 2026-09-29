package com.rollercoin.mobile

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Resilient token store with Android Keystore support and safe fallback.
 */
class TokenStore(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("rollercoin_secure_prefs", Context.MODE_PRIVATE)

    fun get(key: String): String {
        return runCatching {
            val raw = preferences.getString(key, null) ?: return ""
            decrypt(raw)
        }.getOrDefault("")
    }

    fun put(key: String, value: String) {
        runCatching {
            preferences.edit().putString(key, encrypt(value)).apply()
        }
    }

    fun getInt(key: String, default: Int): Int =
        runCatching { preferences.getInt(key, default) }.getOrDefault(default)

    fun putInt(key: String, value: Int) {
        runCatching { preferences.edit().putInt(key, value).apply() }
    }

    fun remove(key: String) {
        runCatching { preferences.edit().remove(key).apply() }
    }

    fun clear() {
        runCatching { preferences.edit().clear().apply() }
    }

    private fun secretKey(): SecretKey? {
        return runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore",
            ).apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(false)
                        .build(),
                )
            }.generateKey()
        }.getOrNull()
    }

    private fun encrypt(value: String): String {
        return runCatching {
            val key = secretKey() ?: return simpleEncode(value)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, SecureRandom())
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val body = Base64.encodeToString(
                cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)),
                Base64.NO_WRAP,
            )
            "gcm:$iv:$body"
        }.getOrElse { simpleEncode(value) }
    }

    private fun decrypt(value: String?): String {
        if (value.isNullOrBlank()) return ""
        if (value.startsWith("enc:")) {
            return simpleDecode(value)
        }
        if (!value.startsWith("gcm:")) {
            return value
        }
        return runCatching {
            val pieces = value.removePrefix("gcm:").split(':', limit = 2)
            if (pieces.size < 2) return value
            val key = secretKey() ?: return value
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)),
            )
            cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP))
                .toString(StandardCharsets.UTF_8)
        }.getOrElse { value }
    }

    private fun simpleEncode(value: String): String =
        "enc:" + Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)

    private fun simpleDecode(value: String): String = runCatching {
        Base64.decode(value.removePrefix("enc:"), Base64.NO_WRAP).toString(StandardCharsets.UTF_8)
    }.getOrDefault(value)

    private companion object {
        const val KEY_ALIAS = "rollercoin_mobile_auth_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
