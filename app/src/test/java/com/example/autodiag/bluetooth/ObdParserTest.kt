package com.example.autodiag.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdParserTest {

    @Test
    fun decodeDtcLetters() {
        assertEquals("P0133", ObdParser.decodeDtc("0133"))
        assertEquals("C0035", ObdParser.decodeDtc("4035"))
        assertEquals("B1000", ObdParser.decodeDtc("9000"))
        assertEquals("U0100", ObdParser.decodeDtc("C100"))
    }

    @Test
    fun canSingleFrameSkipsCountByte() {
        // 43, 2 codici, P0133, P0420
        assertEquals(listOf("P0133", "P0420"), ObdParser.parseDtcs("4302 0133 0420", "43", isCan = true))
    }

    @Test
    fun canNoCodes() {
        assertEquals(emptyList<String>(), ObdParser.parseDtcs("4300", "43", isCan = true))
    }

    @Test
    fun canMultiFrameIsReassembled() {
        val raw = "00B\n0:430401330420\n1:0300017100000000"
        assertEquals(
            listOf("P0133", "P0420", "P0300", "P0171"),
            ObdParser.parseDtcs(raw, "43", isCan = true)
        )
    }

    @Test
    fun canTwoEcusResponding() {
        val raw = "43010133\n4700\n43010700"
        assertEquals(listOf("P0133", "P0700"), ObdParser.parseDtcs(raw, "43", isCan = true))
    }

    @Test
    fun legacyProtocolUsesPaddedTriples() {
        val raw = "SEARCHING...\n43 01 33 04 20 00 00\n43 03 00 00 00 00 00"
        assertEquals(listOf("P0133", "P0420", "P0300"), ObdParser.parseDtcs(raw, "43", isCan = false))
    }

    @Test
    fun mode01IgnoresSearchingLine() {
        // "SEARCHING" contiene lettere esadecimali: non deve spostare l'allineamento
        val data = ObdParser.extractMode01("SEARCHING...\n4100BE3EB813", 0x00, 4)
        assertArrayEquals(intArrayOf(0xBE, 0x3E, 0xB8, 0x13), data)
    }

    @Test
    fun mode01Rpm() {
        assertArrayEquals(intArrayOf(0x1A, 0xF8), ObdParser.extractMode01("41 0C 1A F8", 0x0C, 2))
    }

    @Test
    fun mode01NoDataAndNegative() {
        assertNull(ObdParser.extractMode01("NO DATA", 0x0C, 2))
        assertNull(ObdParser.extractMode01("7F0112", 0x0C, 2))
        assertNull(ObdParser.extractMode01("410C1A", 0x0C, 2))
    }

    @Test
    fun negativeResponseCode() {
        assertEquals("22", ObdParser.hasNegativeResponse("7F 04 22", "04"))
        assertNull(ObdParser.hasNegativeResponse("44", "04"))
    }

    @Test
    fun canProtocolDetection() {
        assertTrue(ObdParser.isCanProtocol("A6"))
        assertTrue(ObdParser.isCanProtocol("8"))
        assertFalse(ObdParser.isCanProtocol("A3"))
        assertFalse(ObdParser.isCanProtocol("5"))
        assertFalse(ObdParser.isCanProtocol(""))
    }
}
