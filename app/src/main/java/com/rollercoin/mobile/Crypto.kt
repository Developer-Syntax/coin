package com.rollercoin.mobile

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Matches the official RollerCoin bot encryption:
 * - Key = lowercase MD5 hex text derived from the user UID
 * - Cipher = AES-256-CBC with the fixed 16-byte IV "dYQ9R99bkKLsLHad"
 * - Output = Base64 encoded ciphertext
 */
object Crypto {
    private const val IV_TEXT = "dYQ9R99bkKLsLHad"

    fun deriveKey(uid: String): ByteArray {
        val chars = uid.toList()
        val digits = chars.filter { it.isDigit() }.map { it.digitToInt() }
        val parts = if (digits.isEmpty()) {
            "defaultrollersid"
        } else {
            val sorted = digits.sorted()
            val firstN = chars.take(sorted.size)
            (sorted + firstN + listOf(digits.sum())).joinToString("")
        }
        val digest = MessageDigest.getInstance("MD5")
            .digest(parts.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
            .toByteArray(StandardCharsets.UTF_8)
    }

    fun encryptJson(payload: JSONObject, uid: String): String {
        return encrypt(payload.toString(), uid)
    }

    fun encrypt(plaintext: String, uid: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(deriveKey(uid), "AES"),
            IvParameterSpec(IV_TEXT.toByteArray(StandardCharsets.UTF_8)),
        )
        return Base64.getEncoder().encodeToString(
            cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8)),
        )
    }

    fun decrypt(base64Ciphertext: String, uid: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(deriveKey(uid), "AES"),
            IvParameterSpec(IV_TEXT.toByteArray(StandardCharsets.UTF_8)),
        )
        return cipher.doFinal(Base64.getDecoder().decode(base64Ciphertext))
            .toString(StandardCharsets.UTF_8)
    }
}
