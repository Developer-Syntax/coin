package com.rollercoin.mobile

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Formatters {
    private val decimal = DecimalFormat(
        "#,##0.00",
        DecimalFormatSymbols(Locale.US),
    )

    private val intFormat = DecimalFormat(
        "#,###",
        DecimalFormatSymbols(Locale.US),
    )

    fun hashPower(value: Int): String {
        val number = value.toDouble()
        return when {
            number < 1_000 -> "${number.toInt()} h/s"
            number < 1_000_000 -> "${decimal.format(number / 1_000)} Kh/s"
            number < 1_000_000_000 -> "${decimal.format(number / 1_000_000)} Mh/s"
            number < 1_000_000_000_000 -> "${decimal.format(number / 1_000_000_000)} Gh/s"
            number < 1e15 -> "${decimal.format(number / 1e12)} Th/s"
            number < 1e18 -> "${decimal.format(number / 1e15)} Ph/s"
            else -> "${decimal.format(number / 1e18)} Eh/s"
        }
    }

    fun hashPowerGh(value: Long): String {
        val number = value.toDouble()
        return when {
            number < 1_000 -> "${decimal.format(number)} Gh/s"
            number < 1_000_000 -> "${decimal.format(number / 1_000)} Th/s"
            number < 1e9 -> "${decimal.format(number / 1_000_000)} Ph/s"
            number < 1e12 -> "${decimal.format(number / 1e9)} Eh/s"
            else -> "${decimal.format(number / 1e12)} Zh/s"
        }
    }

    fun cooldown(seconds: Int): String {
        if (seconds <= 0) return "Tersedia"
        val minutes = (seconds + 59) / 60
        return if (minutes == 1) "1 menit" else "$minutes menit"
    }

    fun timeRemaining(seconds: Int): String {
        val m = (seconds.coerceAtLeast(0) / 60).toString().padStart(2, '0')
        val s = (seconds.coerceAtLeast(0) % 60).toString().padStart(2, '0')
        return "$m:$s"
    }

    fun currencyBalance(raw: Double, toSmall: Double, precision: Int): String {
        val converted = if (toSmall > 0) raw / toSmall else raw
        return String.format(Locale.US, "%.${precision.coerceIn(0, 10)}f", converted)
    }

    fun countFormatted(value: Int): String = intFormat.format(value)
}
