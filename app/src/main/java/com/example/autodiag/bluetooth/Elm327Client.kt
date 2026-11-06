package com.example.autodiag.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class Elm327Client {

    private var socket: BluetoothSocket? = null
    val isConnected: Boolean get() = socket?.isConnected == true

    suspend fun connect(device: BluetoothDevice) = withContext(Dispatchers.IO) {
        BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        val s = try {
            device.createRfcommSocketToServiceRecord(uuid).also { it.connect() }
        } catch (e: IOException) {
            // Fallback reflection: alcuni stack richiedono la porta 1
            val fallback = device.javaClass
                .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                .invoke(device, 1) as BluetoothSocket
            fallback.connect()
            fallback
        }
        socket = s

        // Inizializzazione ELM327
        sendCommand("ATZ", 4000)
        sendCommand("ATE0", 2000)
        sendCommand("ATL0", 2000)
        sendCommand("ATS0", 2000)
        sendCommand("ATSP0", 3000)
    }

    suspend fun sendCommand(cmd: String, timeoutMs: Long = 5000): String = withContext(Dispatchers.IO) {
        val out = socket?.outputStream ?: throw IOException("Non connesso")
        val inp = socket?.inputStream ?: throw IOException("Non connesso")

        out.write("$cmd\r".toByteArray())
        out.flush()

        val sb = StringBuilder()
        val buffer = ByteArray(1024)
        val start = System.currentTimeMillis()

        while (System.currentTimeMillis() - start < timeoutMs) {
            val avail = inp.available()
            if (avail > 0) {
                val n = inp.read(buffer, 0, minOf(avail, buffer.size))
                sb.append(String(buffer, 0, n))
                if (sb.contains(">")) break
            } else {
                delay(30)
            }
        }
        sb.toString().replace("\r", "\n").replace(">", "").trim()
    }

    suspend fun readDtcs(): List<String> {
        val raw = sendCommand("03", 8000)
        return parseDtcs(raw)
    }

    suspend fun clearDtcs(): Boolean {
        val raw = sendCommand("04", 8000)
        return raw.isNotEmpty() && !raw.contains("ERROR", ignoreCase = true)
    }

    fun disconnect() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private fun parseDtcs(raw: String): List<String> {
        if (raw.contains("NO DATA", ignoreCase = true)) return emptyList()
        val clean = raw.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
        val idx = clean.indexOf("43")
        if (idx < 0) return emptyList()
        val hex = clean.substring(idx + 2)

        val out = mutableListOf<String>()
        var i = 0
        while (i + 4 <= hex.length) {
            val pair = hex.substring(i, i + 4)
            if (pair != "0000") out.add(decodeDtc(pair))
            i += 4
        }
        return out.distinct()
    }

    private fun decodeDtc(pair: String): String {
        val v1 = pair.substring(0, 2).toInt(16)
        val v2 = pair.substring(2, 4).toInt(16)
        val letter = when ((v1 shr 6) and 0x03) {
            0 -> 'P'
            1 -> 'C'
            2 -> 'B'
            else -> 'U'
        }
        val code = ((v1 and 0x3F) shl 8) or v2
        return "%c%04X".format(letter, code)
    }
}
