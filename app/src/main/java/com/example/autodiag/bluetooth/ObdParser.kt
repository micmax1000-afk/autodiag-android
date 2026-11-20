package com.example.autodiag.bluetooth

import java.util.Locale

/**
 * Parsing puro (senza dipendenze Android) delle risposte ELM327.
 * Presuppone ATE0, ATS0 (spazi tolleranti comunque) e ATH0.
 */
object ObdParser {

    private val hexOnly = Regex("^[0-9A-F]+$")
    private val frameLine = Regex("^([0-9A-F]):([0-9A-F]*)$")
    private val lengthLine = Regex("^[0-9A-F]{3}$")

    /** Righe utili della risposta: maiuscole, senza spazi, senza messaggi di stato dell'ELM. */
    fun cleanLines(raw: String): List<String> =
        raw.uppercase(Locale.US)
            .replace("\r", "\n")
            .lineSequence()
            .map { it.replace(" ", "").trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("SEARCHING") || it.startsWith("BUSINIT") || it == "OK" }
            .toList()

    /** Protocolli CAN secondo ATDPN: 6-9 (ISO 15765-4) e A-C (J1939 / CAN utente). */
    fun isCanProtocol(atdpn: String): Boolean {
        val line = cleanLines(atdpn).lastOrNull() ?: return false
        val digit = line.removePrefix("A").lastOrNull() ?: return false
        return digit in "6789ABC"
    }

    fun hasNegativeResponse(raw: String, service: String): String? {
        cleanLines(raw).forEach { line ->
            if (!hexOnly.matches(line)) return@forEach
            val m = Regex("7F$service([0-9A-F]{2})").find(line)
            if (m != null && m.range.first % 2 == 0) return m.groupValues[1]
        }
        return null
    }

    /** Estrae [nbytes] byte di dati della risposta Mode 01 al [pid], cercando riga per riga. */
    fun extractMode01(raw: String, pid: Int, nbytes: Int): IntArray? {
        val upper = raw.uppercase(Locale.US)
        if (
            upper.contains("NO DATA") ||
            upper.contains("UNABLE") ||
            upper.contains("ERROR") ||
            upper.contains("STOPPED")
        ) {
            return null
        }
        if (hasNegativeResponse(raw, "01") != null) return null
        val marker = "41" + "%02X".format(pid)
        for (line in cleanLines(raw)) {
            if (!hexOnly.matches(line)) continue
            var idx = line.indexOf(marker)
            while (idx >= 0 && idx % 2 != 0) idx = line.indexOf(marker, idx + 1)
            if (idx < 0) continue
            val data = line.substring(idx + marker.length)
            if (data.length < nbytes * 2) continue
            return IntArray(nbytes) { i -> data.substring(i * 2, i * 2 + 2).toInt(16) }
        }
        return null
    }

    /**
     * Messaggi completi della risposta. Su CAN i frame multipli ISO-TP
     * ("00A" / "0:..." / "1:...") vengono riassemblati in un unico messaggio.
     */
    fun messages(raw: String): List<String> {
        val out = mutableListOf<String>()
        var current: StringBuilder? = null
        var expectedBytes = 0
        fun flush() {
            current?.let { sb ->
                val s = sb.toString()
                out.add(if (expectedBytes > 0 && s.length > expectedBytes * 2) s.substring(0, expectedBytes * 2) else s)
            }
            current = null
            expectedBytes = 0
        }
        for (line in cleanLines(raw)) {
            val frame = frameLine.matchEntire(line)
            when {
                lengthLine.matches(line) -> {
                    flush()
                    current = StringBuilder()
                    expectedBytes = line.toInt(16)
                }
                frame != null -> {
                    if (frame.groupValues[1] == "0" && current == null) current = StringBuilder()
                    current?.append(frame.groupValues[2])
                }
                hexOnly.matches(line) -> {
                    flush()
                    out.add(line)
                }
                else -> flush()
            }
        }
        flush()
        return out
    }

    /**
     * Codici DTC da una risposta Mode 03/07/0A.
     * Su CAN il byte dopo il servizio è il numero di codici; sui protocolli
     * più vecchi ogni riga contiene il servizio seguito da tre coppie (00 00 = vuoto).
     */
    fun parseDtcs(raw: String, positive: String, isCan: Boolean): List<String> {
        val out = mutableListOf<String>()
        for (msg in messages(raw)) {
            if (!msg.startsWith(positive)) continue
            var chunk = msg.substring(positive.length)
            if (isCan && chunk.length >= 2) {
                val count = chunk.substring(0, 2).toInt(16)
                chunk = chunk.substring(2)
                val maxPairs = chunk.length / 4
                chunk = chunk.substring(0, minOf(count, maxPairs) * 4)
            }
            var i = 0
            while (i + 4 <= chunk.length) {
                val pair = chunk.substring(i, i + 4)
                if (pair != "0000") out.add(decodeDtc(pair))
                i += 4
            }
        }
        return out.distinct()
    }

    fun decodeDtc(pair: String): String {
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
