/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.fileproperties.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.formatLong
import me.zhanghai.android.files.file.isImage
import me.zhanghai.android.files.file.name
import me.zhanghai.android.files.fileproperties.Equipment
import me.zhanghai.android.files.fileproperties.FileLocationItems
import me.zhanghai.android.files.fileproperties.FilePropertiesTab
import me.zhanghai.android.files.fileproperties.FilePropertyItem
import me.zhanghai.android.files.fileproperties.ShutterSpeed
import me.zhanghai.android.files.fileproperties.equipment
import me.zhanghai.android.files.fileproperties.shutterSpeed

fun isImageTabAvailable(file: FileItem): Boolean = file.mimeType.isImage

// Вкладка «Изображение», была FilePropertiesImageTabFragment: размеры и EXIF
@Composable
internal fun FilePropertiesImageTab(file: FileItem) {
    val viewModel = viewModel { FilePropertiesImageTabViewModel(file.path, file.mimeType) }
    val stateful by viewModel.imageInfoLiveData.observeAsState()

    FilePropertiesTab(stateful, onRefresh = viewModel::reload) { imageInfo ->
        FilePropertyItem(
            stringResource(R.string.file_properties_media_dimensions),
            imageInfo.dimensions?.let {
                stringResource(R.string.file_properties_media_dimensions_format, it.width, it.height)
            } ?: stringResource(R.string.unknown),
        )
        val exifInfo = imageInfo.exifInfo ?: return@FilePropertiesTab
        exifInfo.dateTimeOriginal?.let {
            FilePropertyItem(stringResource(R.string.file_properties_media_date_time), it.formatLong())
        }
        exifInfo.gpsCoordinates?.let { (latitude, longitude) ->
            FileLocationItems(latitude, longitude, file.path.name)
        }
        exifInfo.gpsAltitude?.let {
            FilePropertyItem(
                stringResource(R.string.file_properties_image_gps_altitude),
                stringResource(R.string.file_properties_image_gps_altitude_format, it),
            )
        }
        equipment(exifInfo.make, exifInfo.model)?.let {
            FilePropertyItem(
                stringResource(R.string.file_properties_image_equipment),
                when (it) {
                    is Equipment.One -> it.name
                    is Equipment.MakeAndModel ->
                        stringResource(R.string.file_properties_image_equipment_format, it.make, it.model)
                },
            )
        }
        exifInfo.fNumber?.let {
            FilePropertyItem(
                stringResource(R.string.file_properties_image_f_number),
                stringResource(R.string.file_properties_image_f_number_format, it),
            )
        }
        exifInfo.shutterSpeedValue?.let {
            FilePropertyItem(
                stringResource(R.string.file_properties_image_shutter_speed),
                when (val speed = shutterSpeed(it)) {
                    is ShutterSpeed.Seconds -> speed.seconds.toString()
                    is ShutterSpeed.Fraction ->
                        stringResource(R.string.file_properties_image_shutter_speed_with_denominator_format, speed.denominator)
                },
            )
        }
        exifInfo.focalLength?.let {
            FilePropertyItem(
                stringResource(R.string.file_properties_image_focal_length),
                stringResource(R.string.file_properties_image_focal_length_format, it),
            )
        }
        exifInfo.photographicSensitivity?.let {
            FilePropertyItem(
                stringResource(R.string.file_properties_image_photographic_sensitivity),
                stringResource(R.string.file_properties_image_photographic_sensitivity_format, it),
            )
        }
        exifInfo.software?.let { FilePropertyItem(stringResource(R.string.file_properties_image_software), it) }
        exifInfo.description?.let { FilePropertyItem(stringResource(R.string.file_properties_image_description), it) }
        exifInfo.artist?.let { FilePropertyItem(stringResource(R.string.file_properties_image_artist), it) }
        exifInfo.copyright?.let { FilePropertyItem(stringResource(R.string.file_properties_image_copyright), it) }
    }
}
