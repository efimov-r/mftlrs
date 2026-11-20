/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.fileproperties

import me.zhanghai.android.files.fileproperties.checksum.ChecksumInfo
import me.zhanghai.android.files.provider.common.PosixFileModeBit
import kotlin.math.pow
import kotlin.math.roundToInt

// Расчёты «Свойств» без Android и ресурсов — проверяются unit-тестами; текст собирает Compose

// Названия отмеченных битов из набора, в порядке набора
internal fun modeBitNames(mode: Set<PosixFileModeBit>, bits: List<PosixFileModeBit>, names: Array<String>): List<String> =
    bits.indices.filter { bits[it] in mode }.map { names[it] }

internal sealed interface ChecksumCompareResult {
    data object Empty : ChecksumCompareResult

    data class Match(val algorithm: ChecksumInfo.Algorithm) : ChecksumCompareResult

    data class PrefixMatch(val algorithm: ChecksumInfo.Algorithm) : ChecksumCompareResult

    data object NoMatch : ChecksumCompareResult
}

// Сравнение введённой суммы с посчитанными: без учёта регистра и пробелов по краям; полное совпадение
// важнее совпадения начала
internal fun compareChecksum(checksums: Map<ChecksumInfo.Algorithm, String>, input: String): ChecksumCompareResult {
    val text = input.trim()
    if (text.isEmpty()) return ChecksumCompareResult.Empty
    checksums.entries.firstOrNull { it.value.equals(text, true) }
        ?.let { return ChecksumCompareResult.Match(it.key) }
    checksums.entries.firstOrNull { it.value.startsWith(text, true) }
        ?.let { return ChecksumCompareResult.PrefixMatch(it.key) }
    return ChecksumCompareResult.NoMatch
}

// Камера из EXIF: модель, если она уже начинается с производителя, иначе производитель и модель
internal sealed interface Equipment {
    data class One(val name: String) : Equipment

    data class MakeAndModel(val make: String, val model: String) : Equipment
}

internal fun equipment(make: String?, model: String?): Equipment? =
    when {
        make != null && model != null ->
            if (model.startsWith(make, true)) Equipment.One(model) else Equipment.MakeAndModel(make, model)
        make != null -> Equipment.One(make)
        model != null -> Equipment.One(model)
        else -> null
    }

// Выдержка из EXIF ShutterSpeedValue (APEX): от секунды и длиннее — секунды с одним знаком,
// короче — 1/N. @see com.android.documentsui.inspector.MediaView.formatShutterSpeed
internal sealed interface ShutterSpeed {
    data class Seconds(val seconds: Double) : ShutterSpeed

    data class Fraction(val denominator: Int) : ShutterSpeed
}

internal fun shutterSpeed(value: Double): ShutterSpeed =
    if (value <= 0) {
        ShutterSpeed.Seconds((2.0.pow(-value) * 10.0).roundToInt() / 10.0)
    } else {
        ShutterSpeed.Fraction(2.0.pow(value).toInt() + 1)
    }
