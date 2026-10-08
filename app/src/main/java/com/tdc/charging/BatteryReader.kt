package com.tdc.charging

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.util.Locale
import kotlin.math.abs

/**
 * Reads the battery state. Android only reports whole percents, so the decimals
 * come from the charge counter (µAh): how much charge went in or out since the
 * last whole-percent step, divided by the size of one percent.
 */
class BatteryReader(context: Context) {
    private val ctx = context.applicationContext
    private val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    private var baseLevel = -1
    private var basePlugged: Boolean? = null
    private var baseCounter = 0L
    private var capacityUah = 0.0

    @Synchronized
    fun snapshot(): String {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return "{}"
        val raw = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).let { if (it <= 0) 100 else it }
        val level = (raw * 100 / scale).coerceIn(0, 100)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val full = plugged && (status == BatteryManager.BATTERY_STATUS_FULL || level >= 100)
        val millivolts = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

        val counter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        var current = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (current == Long.MIN_VALUE || current == Long.MAX_VALUE) current = 0
        if (abs(current) in 1L..14_999L) current *= 1000 // some phones report mA instead of µA
        val watts = abs(current) / 1_000_000.0 * millivolts / 1000.0

        val hasCounter = counter > 0 && counter != Long.MAX_VALUE && level in 1..99
        var precise = level.toDouble()
        if (hasCounter) {
            val estimate = counter * 100.0 / level
            capacityUah = if (capacityUah <= 0) estimate else capacityUah * 0.95 + estimate * 0.05
            if (level != baseLevel || plugged != basePlugged) {
                baseLevel = level
                basePlugged = plugged
                baseCounter = counter
            }
            val frac = (counter - baseCounter) / (capacityUah / 100.0)
            precise = if (plugged) level + frac.coerceIn(0.0, 0.999) else level + frac.coerceIn(-0.999, 0.0)
        }
        if (full) precise = 100.0
        if (precise.isNaN()) precise = level.toDouble()

        return String.format(
            Locale.US,
            "{\"p\":%.4f,\"level\":%d,\"plugged\":%b,\"full\":%b,\"w\":%.2f,\"has\":%b}",
            precise, level, plugged, full, if (watts.isNaN()) 0.0 else watts, hasCounter
        )
    }

    fun isPlugged(): Boolean {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        return i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    }
}
