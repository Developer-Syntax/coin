package com.rollercoin.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test
    fun formatsHashPowerUsingPhpUnits() {
        assertEquals("999 h/s", Formatters.hashPower(999))
        assertEquals("1.50 Kh/s", Formatters.hashPower(1500))
        assertEquals("1.50 Gh/s", Formatters.hashPower(1_500_000_000))
    }

    @Test
    fun formatsCooldownInMinutes() {
        assertEquals("Tersedia", Formatters.cooldown(0))
        assertEquals("1 menit", Formatters.cooldown(1))
        assertEquals("3 menit", Formatters.cooldown(121))
    }
}
