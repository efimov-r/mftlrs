/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.fileproperties

import android.content.Intent
import android.location.Geocoder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.awaitGetFromLocation
import me.zhanghai.android.files.util.createViewLocation
import me.zhanghai.android.files.util.isGeocoderPresent
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.userFriendlyString

// Координаты снимка или видео (нажатие — открыть на карте) и адрес по ним, если есть Geocoder
@Composable
internal fun FileLocationItems(latitude: Double, longitude: Double, label: String) {
    val context = LocalContext.current

    FilePropertyItem(
        stringResource(R.string.file_properties_media_coordinates),
        stringResource(R.string.file_properties_media_coordinates_format, latitude, longitude),
    ) {
        context.startActivitySafe(Intent::class.createViewLocation(latitude.toFloat(), longitude.toFloat(), label))
    }
    if (!isGeocoderPresent) return
    // null — ещё ищется; пустая строка — не нашёлся
    val address by produceState<String?>(null, latitude, longitude) {
        value = try {
            Geocoder(context).awaitGetFromLocation(latitude, longitude, 1).first().userFriendlyString
        } catch (e: Exception) {
            null
        }.orEmpty()
    }
    FilePropertyItem(
        stringResource(R.string.file_properties_media_address),
        when (val address = address) {
            null -> stringResource(R.string.loading)
            "" -> stringResource(R.string.unknown)
            else -> address
        },
    )
}
