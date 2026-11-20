/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.fileproperties

import me.zhanghai.android.files.fileproperties.checksum.ChecksumInfo.Algorithm
import me.zhanghai.android.files.provider.common.PosixFileModeBit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilePropertiesLogicTest {
    // Биты прав

    @Test
    fun modeBitNames_checkedInOrderOfBits() {
        val bits = listOf(PosixFileModeBit.OWNER_READ, PosixFileModeBit.OWNER_WRITE, PosixFileModeBit.OWNER_EXECUTE)
        val names = arrayOf("r", "w", "x")
        val mode = setOf(PosixFileModeBit.OWNER_EXECUTE, PosixFileModeBit.OWNER_READ, PosixFileModeBit.GROUP_READ)

        assertEquals(listOf("r", "x"), modeBitNames(mode, bits, names))
        assertEquals(emptyList<String>(), modeBitNames(emptySet(), bits, names))
    }

    // Сравнение контрольной суммы

    private val checksums = mapOf(Algorithm.CRC32 to "0a1b2c3d", Algorithm.MD5 to "0a1b2c3d4e5f")

    @Test
    fun compareChecksum_emptyOrSpaces() {
        assertEquals(ChecksumCompareResult.Empty, compareChecksum(checksums, ""))
        assertEquals(ChecksumCompareResult.Empty, compareChecksum(checksums, "  \n"))
    }

    @Test
    fun compareChecksum_fullMatchIgnoresCaseAndSpaces() {
        assertEquals(ChecksumCompareResult.Match(Algorithm.MD5), compareChecksum(checksums, " 0A1B2C3D4E5F\n"))
    }

    @Test
    fun compareChecksum_fullMatchWinsOverPrefix() {
        // «0a1b2c3d» — вся CRC32 и начало MD5
        assertEquals(ChecksumCompareResult.Match(Algorithm.CRC32), compareChecksum(checksums, "0a1b2c3d"))
    }

    @Test
    fun compareChecksum_prefixAndNoMatch() {
        assertEquals(ChecksumCompareResult.PrefixMatch(Algorithm.CRC32), compareChecksum(checksums, "0a1b"))
        assertEquals(ChecksumCompareResult.NoMatch, compareChecksum(checksums, "ff"))
    }

    // Камера из EXIF

    @Test
    fun equipment_variants() {
        assertEquals(Equipment.One("Canon EOS 5D"), equipment("Canon", "Canon EOS 5D"))
        assertEquals(Equipment.One("canon EOS 5D"), equipment("Canon", "canon EOS 5D"))
        assertEquals(Equipment.MakeAndModel("Google", "Pixel 8"), equipment("Google", "Pixel 8"))
        assertEquals(Equipment.One("Google"), equipment("Google", null))
        assertEquals(Equipment.One("Pixel 8"), equipment(null, "Pixel 8"))
        assertNull(equipment(null, null))
    }

    // Выдержка

    @Test
    fun shutterSpeed_longIsSeconds() {
        assertEquals(ShutterSpeed.Seconds(1.0), shutterSpeed(0.0))
        assertEquals(ShutterSpeed.Seconds(4.0), shutterSpeed(-2.0))
        assertEquals(ShutterSpeed.Seconds(1.4), shutterSpeed(-0.5)) // 2^0,5 ≈ 1,414
    }

    @Test
    fun shutterSpeed_shortIsFraction() {
        assertEquals(ShutterSpeed.Fraction(2), shutterSpeed(0.5)) // 2^0,5 ≈ 1,41 → 1 + 1
        assertEquals(ShutterSpeed.Fraction(129), shutterSpeed(7.0))
    }
}
