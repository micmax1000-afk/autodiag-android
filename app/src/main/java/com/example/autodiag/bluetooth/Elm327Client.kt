package com.example.autodiag.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.UUID

class Elm327Client {

    private val io = Mutex()
    private var socket: BluetoothSocket? = null
    private val supportGroups = mutableMapOf<Int, ByteArray>()

    val isConnected: Boolean get() = socket?.isConnected == true

    fun hasSupportMap(): Boolean = supportGroups.isNotEmpty()

    fun isPidSupported(pid: Int): Boolean {
        if (pid <= 0) return false
        val group = ((pid - 1) / 0x20) * 0x20
        val bytes = supportGroups[group] ?: return false
        val offset = (pid - 1) % 0x20
        val value = bytes[offset / 8].toInt() and 0xFF
        val bit = 7 - (offset % 8)
        return ((value shr bit) and 1) == 1
    }

    suspend fun connect(device: BluetoothDevice): String = withContext(Dispatchers.IO) {
        BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val s = try {
            device.createRfcommSocketToServiceRecord(uuid).also { it.connect() }
        } catch (_: IOException) {
            val fallback = device.javaClass
                .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                .invoke(device, 1) as BluetoothSocket
            fallback.connect()
            fallback
        }
        socket = s
        supportGroups.clear()
        try {
            delay(300)
            sendCommand("ATZ", 3000)
            delay(700)
            initQuiet("ATE0", required = true)
            initQuiet("ATL0", required = true)
            initQuiet("ATS0", required = true)
            initQuiet("ATH0", required = true)
            initQuiet("ATAT1", required = false)
            initQuiet("ATAL", required = false)
            initQuiet("ATSP0", required = true)
            val wakeup = try {
                sendCommand("0100", 15000)
            } catch (e: IOException) {
                throw IOException("La centralina non risponde. Quadro acceso? ${e.message}")
            }
            if (!wakeup.uppercase(Locale.US).contains("41")) {
                throw IOException(explainLink(wakeup))
            }
            discoverSupport()
            val proto = sendCommand("ATDP", 2500)
            proto.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() } ?: "OBD-II"
        } catch (e: Exception) {
            disconnect()
            throw e
        }
    }

    suspend fun discoverSupport() {
        supportGroups.clear()
        var base = 0x00
        repeat(4) {
            val raw = sendCommand("01%02X".format(base), 5000)
            val bytes = extractMode01(raw, base, 4) ?: return
            supportGroups[base] = byteArrayOf(
                bytes[0].toByte(),
                bytes[1].toByte(),
                bytes[2].toByte(),
                bytes[3].toByte(),
            )
            val next = base + 0x20
            if (next > 0x60 || !isPidSupported(next)) return
            base = next
        }
    }

    suspend fun readPid(spec: PidSpec): String? {
        val raw = sendCommand(spec.command, 2000)
        return spec.decode(raw)
    }

    data class ModeRead(val codes: List<String>, val note: String?)

    suspend fun readStored(): ModeRead = readMode("03", "43")
    suspend fun readPending(): ModeRead = readMode("07", "47")
    suspend fun readPermanent(): ModeRead = readMode("0A", "4A")

    data class ClearOutcome(val accepted: Boolean, val detail: String)

    suspend fun clearDtcs(): ClearOutcome {
        val raw = sendCommand("04", 8000)
        return interpretClear(raw)
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }

    private suspend fun initQuiet(cmd: String, required: Boolean) {
        val raw = try {
            sendCommand(cmd, 3000)
        } catch (e: IOException) {
            if (required) throw e
            return
        }
        if (required && raw.contains("?")) {
            throw IOException("L'adattatore non accetta $cmd")
        }
    }

    private fun explainLink(raw: String): String {
        val u = raw.uppercase(Locale.US)
        return when {
            u.contains("UNABLE") ->
                "Centralina non raggiunta. Quadro acceso, adattatore inserito. I modelli solo Bluetooth LE non funzionano."
            u.contains("NO DATA") ->
                "Adattatore collegato, ma la centralina non risponde. Tieni il quadro acceso."
            u.contains("ERROR") || u.contains("STOPPED") ->
                "Bus non inizializzato (${raw.lineSequence().firstOrNull()?.take(80) ?: "errore"})."
            raw.isBlank() -> "Nessuna risposta dalla centralina."
            else -> "Risposta inattesa: ${raw.take(80)}"
        }
    }

    private suspend fun readMode(cmd: String, positive: String): ModeRead {
        val raw = sendCommand(cmd, 8000)
        val u = raw.uppercase(Locale.US)
        if (u.contains("UNABLE TO CONNECT") || u.contains("BUS ERROR") || u.contains("CAN ERROR")) {
            throw IOException(explainLink(raw))
        }
        if (u.contains("NO DATA")) return ModeRead(emptyList(), null)
        val hex = u.replace(Regex("[^0-9A-F]"), "")
        val nrc = Regex("7F$cmd([0-9A-F]{2})").find(hex)
        if (nrc != null) {
            val code = nrc.groupValues[1]
            val note = if (code == "11" || code == "12") {
                "Non supportati da questa centralina."
            } else {
                "Lettura rifiutata (NRC $code)."
            }
            return ModeRead(emptyList(), note)
        }
        if (u.contains("ERROR") || u.contains("STOPPED") || u.trim() == "?") {
            throw IOException(raw.lineSequence().firstOrNull()?.take(100) ?: "Errore OBD")
        }
        return ModeRead(parseDtcs(raw, positive), null)
    }

    private fun interpretClear(raw: String): ClearOutcome {
        val u = raw.uppercase(Locale.US)
        if (u.contains("UNABLE") || u.contains("STOPPED") || u.contains("CAN ERROR") || u.contains("BUS ERROR")) {
            return ClearOutcome(false, "Centralina non raggiunta.")
        }
        val hex = u.replace(Regex("[^0-9A-F]"), "")
        val nrc = Regex("7F04([0-9A-F]{2})").find(hex)
        if (nrc != null) {
            val detail = when (nrc.groupValues[1]) {
                "11" -> "La centralina non accetta la cancellazione OBD standard."
                "22" -> "Condizioni non corrette: spegni il motore e lascia il quadro acceso."
                "33" -> "Accesso negato: questo modulo non si cancella con l'ELM327."
                else -> "Cancellazione rifiutata (NRC ${nrc.groupValues[1]})."
            }
            return ClearOutcome(false, detail)
        }
        if (hex.contains("44")) {
            return ClearOutcome(true, "La centralina ha accettato la cancellazione.")
        }
        if (u.contains("NO DATA")) {
            return ClearOutcome(false, "Nessuna conferma (NO DATA). I codici potrebbero essere ancora presenti.")
        }
        if (u.isBlank()) return ClearOutcome(false, "Timeout: nessuna risposta.")
        return ClearOutcome(false, "Risposta non riconosciuta: ${raw.take(80)}")
    }

    suspend fun sendCommand(cmd: String, timeoutMs: Long = 4000): String = io.withLock {
        withContext(Dispatchers.IO) {
            val out = socket?.outputStream ?: throw IOException("Non connesso")
            val inp = socket?.inputStream ?: throw IOException("Non connesso")
            drain(inp)
            out.write("$cmd\r".toByteArray(Charsets.US_ASCII))
            out.flush()

            val sb = StringBuilder()
            val buffer = ByteArray(1024)
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeoutMs) {
                val avail = inp.available()
                if (avail > 0) {
                    val n = inp.read(buffer, 0, minOf(avail, buffer.size))
                    if (n > 0) {
                        sb.append(String(buffer, 0, n, Charsets.ISO_8859_1))
                        if (sb.indexOf('>') >= 0) break
                    }
                } else {
                    delay(20)
                }
            }
            if (sb.indexOf('>') < 0) throw IOException("Timeout su $cmd")
            sb.toString()
                .replace("\r", "\n")
                .replace(">", "")
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals(cmd, ignoreCase = true) }
                .joinToString("\n")
                .trim()
        }
    }

    private fun drain(inp: InputStream) {
        val buffer = ByteArray(256)
        while (inp.available() > 0) {
            val n = inp.read(buffer)
            if (n <= 0) break
        }
    }

    private fun parseDtcs(raw: String, positive: String): List<String> {
        val out = mutableListOf<String>()
        raw.lineSequence().forEach { line ->
            val hex = line.uppercase(Locale.US).replace(Regex("[^0-9A-F]"), "")
            if (hex.isEmpty()) return@forEach
            val idx = hex.indexOf(positive)
            if (idx < 0) return@forEach
            val chunk = hex.substring(idx + positive.length)
            val usable = chunk.length - (chunk.length % 4)
            var i = 0
            while (i + 4 <= usable) {
                val pair = chunk.substring(i, i + 4)
                if (pair != "0000") out.add(decodeDtc(pair))
                i += 4
            }
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
