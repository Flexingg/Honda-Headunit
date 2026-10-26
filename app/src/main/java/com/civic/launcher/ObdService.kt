package com.civic.launcher

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Live values, all metric internally. NaN = unknown. */
data class CarData(
    val state: String = "OBD offline",
    val speedKmh: Float = Float.NaN, val rpm: Float = Float.NaN,
    val coolantC: Float = Float.NaN, val intakeC: Float = Float.NaN,
    val throttle: Float = Float.NaN, val load: Float = Float.NaN,
    val fuel: Float = Float.NaN, val volts: Float = Float.NaN
)

object CarBus {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<(CarData) -> Unit>()
    @Volatile var data = CarData(); private set
    fun post(d: CarData) { data = d; main.post { listeners.forEach { it(d) } } }
    fun add(l: (CarData) -> Unit) { listeners += l; l(data) }
    fun remove(l: (CarData) -> Unit) { listeners -= l }

    fun speed(c: Context, d: CarData) = if (d.speedKmh.isNaN()) "--"
        else if (Prefs.imperial(c)) "%.0f".format(d.speedKmh * 0.621371f) else "%.0f".format(d.speedKmh)
    fun speedUnit(c: Context) = if (Prefs.imperial(c)) "mph" else "km/h"
    fun temp(c: Context, v: Float) = if (v.isNaN()) "--"
        else if (Prefs.imperial(c)) "%.0f°F".format(v * 9 / 5 + 32) else "%.0f°C".format(v)
}

/**
 * ELM327 over Bluetooth SPP. One background thread, polls fast PIDs every loop and
 * slow ones every 10th loop. Reconnects automatically (car start / adapter sleep).
 */
class ObdService : Service() {
    @Volatile private var running = true
    private var socket: BluetoothSocket? = null

    override fun onBind(i: Intent?) = null

    override fun onCreate() {
        Thread({ loop() }, "obd").apply { priority = Thread.MIN_PRIORITY; start() }
    }

    override fun onDestroy() { running = false; runCatching { socket?.close() } }

    private fun loop() {
        while (running) {
            try { session() } catch (t: Throwable) {
                CarBus.post(CarData(state = "OBD: ${t.message ?: "disconnected"}"))
            }
            runCatching { socket?.close() }
            Thread.sleep(5000)
        }
    }

    private fun session() {
        val bt = BluetoothAdapter.getDefaultAdapter() ?: throw Exception("no bluetooth")
        if (!bt.isEnabled) throw Exception("bluetooth off")
        val mac = Prefs.obdMac(this)
        val dev = (if (mac != null) bt.getRemoteDevice(mac) else
            bt.bondedDevices.firstOrNull { (it.name ?: "").contains("OBD", true) || (it.name ?: "").contains("ELM", true) })
            ?: throw Exception("pair an adapter in Settings")
        CarBus.post(CarBus.data.copy(state = "Connecting ${dev.name}…"))
        bt.cancelDiscovery()
        val s = dev.createInsecureRfcommSocketToServiceRecord(SPP)
        socket = s; s.connect()
        val inp = s.inputStream; val out = s.outputStream
        listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0").forEach { cmd(inp, out, it) }
        cmd(inp, out, "0100") // wakes protocol auto-detect
        var d = CarData(state = "OBD connected")
        var tick = 0
        while (running) {
            d = d.copy(
                speedKmh = pid(inp, out, "010D") { it[0].toFloat() } ?: d.speedKmh,
                rpm = pid(inp, out, "010C") { (it[0] * 256 + it[1]) / 4f } ?: d.rpm,
                throttle = pid(inp, out, "0111") { it[0] * 100f / 255 } ?: d.throttle
            )
            if (tick % 10 == 0) d = d.copy(
                coolantC = pid(inp, out, "0105") { it[0] - 40f } ?: d.coolantC,
                intakeC = pid(inp, out, "010F") { it[0] - 40f } ?: d.intakeC,
                load = pid(inp, out, "0104") { it[0] * 100f / 255 } ?: d.load,
                fuel = pid(inp, out, "012F") { it[0] * 100f / 255 } ?: d.fuel,
                volts = cmd(inp, out, "ATRV").replace("V", "").trim().toFloatOrNull() ?: d.volts
            )
            CarBus.post(d); tick++
            Thread.sleep(150)
        }
    }

    private inline fun pid(i: InputStream, o: OutputStream, c: String, f: (IntArray) -> Float): Float? {
        val r = cmd(i, o, c).replace(" ", "")
        val head = "4" + c.substring(1)
        val idx = r.indexOf(head); if (idx < 0) return null
        val hex = r.substring(idx + 4)
        val bytes = IntArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toIntOrNull(16) ?: 0 }
        return if (bytes.isEmpty()) null else runCatching { f(bytes) }.getOrNull()
    }

    private fun cmd(i: InputStream, o: OutputStream, c: String): String {
        o.write((c + "\r").toByteArray()); o.flush()
        val sb = StringBuilder()
        while (true) {
            val b = i.read(); if (b < 0) throw Exception("socket closed")
            if (b.toChar() == '>') break
            sb.append(b.toChar())
        }
        return sb.toString().replace("SEARCHING...", "").replace("\r", "").replace("\n", "").trim()
    }

    companion object {
        private val SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        fun restart(c: Context) {
            c.stopService(Intent(c, ObdService::class.java))
            c.startService(Intent(c, ObdService::class.java))
        }
    }
}
