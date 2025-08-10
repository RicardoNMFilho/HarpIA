
package com.example.harpia.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock

class EnergyMonitor(private val context: Context) {
    data class Sample(val voltage: Double, val current: Double, val time: Long)
    private val samples = mutableListOf<Sample>()
    @Volatile private var collecting = false
    private var collectorThread: Thread? = null

    fun start() {
        collecting = true
        samples.clear()
        collectorThread = Thread {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            while (collecting) {
                val batteryStatus = context.registerReceiver(null, intentFilter)
                val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.toDouble() ?: -1.0
                val current = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).toDouble() / 1_000_000.0
                val time = SystemClock.elapsedRealtime()
                if (voltage > 0 && current != 0.0) {
                    samples.add(Sample(voltage / 1000.0, current, time))
                }
                Thread.sleep(100)
            }
        }
        collectorThread?.start()
    }

    fun stopAndGetEnergy(): Pair<Double, Long> {
        collecting = false
        collectorThread?.join()
        var energy = 0.0
        for (i in 1 until samples.size) {
            val (v, c, t) = samples[i]
            val (vPrev, cPrev, tPrev) = samples[i - 1]
            val dt = (t - tPrev) / 1000.0
            val vAvg = (v + vPrev) / 2.0
            val cAvg = (c + cPrev) / 2.0
            energy += vAvg * cAvg * dt
        }
        val duration = if (samples.isNotEmpty()) (samples.last().time - samples.first().time) else 0L
        return Pair(energy, duration)
    }
}
