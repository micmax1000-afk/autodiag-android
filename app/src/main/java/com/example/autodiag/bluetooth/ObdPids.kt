package com.example.autodiag.bluetooth

import java.util.Locale

data class LiveReading(
    val pid: Int,
    val name: String,
    val value: String,
    val unit: String,
)

class PidSpec(
    val pid: Int,
    val name: String,
    val unit: String,
    val bytes: Int,
    val format: (IntArray) -> String,
) {
    val command: String get() = "01%02X".format(pid)

    fun decode(raw: String): String? {
        val data = extractMode01(raw, pid, bytes) ?: return null
        return format(data)
    }
}

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
    val hex = upper.replace(Regex("[^0-9A-F]"), "")
    if (Regex("7F01[0-9A-F]{2}").containsMatchIn(hex)) return null
    val marker = "41" + "%02X".format(pid)
    val idx = hex.indexOf(marker)
    if (idx < 0 || idx % 2 != 0) return null
    val data = hex.substring(idx + marker.length)
    if (data.length < nbytes * 2) return null
    return IntArray(nbytes) { i -> data.substring(i * 2, i * 2 + 2).toInt(16) }
}

private fun num(v: Double, digits: Int): String =
    if (digits == 0) "%.0f".format(Locale.ITALY, v) else "%.1f".format(Locale.ITALY, v)

private fun u8(d: IntArray) = d[0]
private fun u16(d: IntArray) = (d[0] shl 8) or d[1]

object ObdPids {
    val all: List<PidSpec> = listOf(
        PidSpec(0x0C, "Giri motore", "giri/min", 2) { num((u16(it) / 4.0), 0) },
        PidSpec(0x0D, "Velocità", "km/h", 1) { num(u8(it).toDouble(), 0) },
        PidSpec(0x05, "Temperatura liquido", "°C", 1) { num((u8(it) - 40).toDouble(), 0) },
        PidSpec(0x04, "Carico motore", "%", 1) { num(u8(it) * 100.0 / 255.0, 0) },
        PidSpec(0x11, "Farfalla", "%", 1) { num(u8(it) * 100.0 / 255.0, 0) },
        PidSpec(0x42, "Tensione centralina", "V", 2) { num(u16(it) / 1000.0, 1) },
        PidSpec(0x0F, "Temperatura aria", "°C", 1) { num((u8(it) - 40).toDouble(), 0) },
        PidSpec(0x0B, "Pressione collettore", "kPa", 1) { num(u8(it).toDouble(), 0) },
        PidSpec(0x10, "Portata aria", "g/s", 2) { num(u16(it) / 100.0, 1) },
        PidSpec(0x06, "Trim breve benzina", "%", 1) { num((u8(it) - 128) * 100.0 / 128.0, 1) },
        PidSpec(0x07, "Trim lungo benzina", "%", 1) { num((u8(it) - 128) * 100.0 / 128.0, 1) },
        PidSpec(0x2F, "Livello carburante", "%", 1) { num(u8(it) * 100.0 / 255.0, 0) },
        PidSpec(0x5C, "Temperatura olio", "°C", 1) { num((u8(it) - 40).toDouble(), 0) },
        PidSpec(0x0E, "Anticipo accensione", "°", 1) { num(u8(it) / 2.0 - 64.0, 1) },
        PidSpec(0x46, "Temperatura ambiente", "°C", 1) { num((u8(it) - 40).toDouble(), 0) },
        PidSpec(0x0A, "Pressione carburante", "kPa", 1) { num(u8(it) * 3.0, 0) },
        PidSpec(0x21, "Distanza con spia accesa", "km", 2) { num(u16(it).toDouble(), 0) },
    )

    val fallback: List<PidSpec> =
        all.filter { it.pid in setOf(0x0C, 0x0D, 0x05, 0x04, 0x11, 0x42) }
}
