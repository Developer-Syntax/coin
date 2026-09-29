package com.rollercoin.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CryptoTest {
    @Test
    fun deriveKeyMatchesPhpAlgorithmForNumericUid() {
        val key = Crypto.deriveKey("u12345").toString(Charsets.UTF_8)
        assertEquals("772d19a2468971dace4889621de9c156", key)
    }

    @Test
    fun deriveKeyUsesDefaultSeedWhenUidHasNoDigits() {
        val key = Crypto.deriveKey("roller").toString(Charsets.UTF_8)
        assertEquals("1509fa0bcf93e7bfe8038fc0886e84d4", key)
    }

    @Test
    fun encryptAndDecryptRoundTrip() {
        val input = """{"game_number":13}"""
        val encrypted = Crypto.encrypt(input, "user123")
        assertNotEquals(input, encrypted)
        assertEquals(
            "66TdeqchcTYikP3G9t5JVjiKd7jYHpYOv+ZKU4Hpo3Y=",
            encrypted,
        )
        assertEquals(input, Crypto.decrypt(encrypted, "user123"))
    }
}
