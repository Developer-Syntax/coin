package com.rollercoin.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class BatterySaverTest {

    @Test
    fun testForegroundIntervalAlwaysThirtySeconds() {
        // When in foreground, polling is always 30s regardless of battery saver toggle
        val withSaver = RollerCoinViewModel.calculateInterval(
            inForeground = true,
            batterySaverEnabled = true,
            systemPowerSave = false
        )
        val withoutSaver = RollerCoinViewModel.calculateInterval(
            inForeground = true,
            batterySaverEnabled = false,
            systemPowerSave = false
        )
        assertEquals(30, withSaver)
        assertEquals(30, withoutSaver)
    }

    @Test
    fun testBackgroundIntervalWithBatterySaverEnabled() {
        // When in background with battery saver on, polling is reduced to 180s (3 minutes)
        val interval = RollerCoinViewModel.calculateInterval(
            inForeground = false,
            batterySaverEnabled = true,
            systemPowerSave = false
        )
        assertEquals(180, interval)
    }

    @Test
    fun testBackgroundIntervalWithBatterySaverDisabled() {
        // When in background with battery saver off, normal background polling is 60s
        val interval = RollerCoinViewModel.calculateInterval(
            inForeground = false,
            batterySaverEnabled = false,
            systemPowerSave = false
        )
        assertEquals(60, interval)
    }

    @Test
    fun testBackgroundIntervalWhenSystemPowerSaveModeIsActive() {
        // Even if the user battery saver toggle is false, system power save mode enforces 180s in background
        val interval = RollerCoinViewModel.calculateInterval(
            inForeground = false,
            batterySaverEnabled = false,
            systemPowerSave = true
        )
        assertEquals(180, interval)
    }
}
