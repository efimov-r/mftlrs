/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.fileproperties.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.format
import me.zhanghai.android.files.file.formatLong
import me.zhanghai.android.files.file.isVideo
import me.zhanghai.android.files.file.name
import me.zhanghai.android.files.fileproperties.FileLocationItems
import me.zhanghai.android.files.fileproperties.FilePropertiesTab
import me.zhanghai.android.files.fileproperties.FilePropertyItem
import me.zhanghai.android.files.util.isMediaMetadataRetrieverCompatible

fun isVideoTabAvailable(file: FileItem): Boolean = file.mimeType.isVideo && file.path.isMediaMetadataRetrieverCompatible

// Вкладка «Видео», была FilePropertiesVideoTabFragment
@Composable
internal fun FilePropertiesVideoTab(file: FileItem) {
    val viewModel = viewModel { FilePropertiesVideoTabViewModel(file.path) }
    val stateful by viewModel.videoInfoLiveData.observeAsState()

    FilePropertiesTab(stateful, onRefresh = viewModel::reload) { videoInfo ->
        videoInfo.title?.let { FilePropertyItem(stringResource(R.string.file_properties_media_title), it) }
        videoInfo.dimensions?.let {
            FilePropertyItem(
                stringResource(R.string.file_properties_media_dimensions),
                stringResource(R.string.file_properties_media_dimensions_format, it.width, it.height),
            )
        }
        videoInfo.duration?.let { FilePropertyItem(stringResource(R.string.file_properties_media_duration), it.format()) }
        videoInfo.date?.let { FilePropertyItem(stringResource(R.string.file_properties_media_date_time), it.formatLong()) }
        videoInfo.location?.let { (latitude, longitude) ->
            FileLocationItems(latitude.toDouble(), longitude.toDouble(), file.path.name)
        }
        videoInfo.bitRate?.let {
            FilePropertyItem(
                stringResource(R.string.file_properties_media_bit_rate),
                stringResource(R.string.file_properties_media_bit_rate_format, it / 1000),
            )
        }
    }
}
