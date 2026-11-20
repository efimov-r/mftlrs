/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.fileproperties.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.format
import me.zhanghai.android.files.file.isAudio
import me.zhanghai.android.files.fileproperties.FilePropertiesTab
import me.zhanghai.android.files.fileproperties.FilePropertyItem
import me.zhanghai.android.files.util.isMediaMetadataRetrieverCompatible

fun isAudioTabAvailable(file: FileItem): Boolean = file.mimeType.isAudio && file.path.isMediaMetadataRetrieverCompatible

// Вкладка «Аудио», была FilePropertiesAudioTabFragment: теги и параметры потока
@Composable
internal fun FilePropertiesAudioTab(file: FileItem) {
    val viewModel = viewModel { FilePropertiesAudioTabViewModel(file.path) }
    val stateful by viewModel.audioInfoLiveData.observeAsState()

    FilePropertiesTab(stateful, onRefresh = viewModel::reload) { audioInfo ->
        audioInfo.title?.let { FilePropertyItem(stringResource(R.string.file_properties_media_title), it) }
        audioInfo.artist?.let { FilePropertyItem(stringResource(R.string.file_properties_audio_artist), it) }
        audioInfo.album?.let { FilePropertyItem(stringResource(R.string.file_properties_audio_album), it) }
        audioInfo.albumArtist?.let { FilePropertyItem(stringResource(R.string.file_properties_audio_album_artist), it) }
        audioInfo.composer?.let { FilePropertyItem(stringResource(R.string.file_properties_audio_composer), it) }
        audioInfo.discNumber?.let { FilePropertyItem(stringResource(R.string.file_properties_audio_disc_number), it) }
        audioInfo.trackNumber?.let { FilePropertyItem(stringResource(R.string.file_properties_audio_track_number), it) }
        audioInfo.year?.let { FilePropertyItem(stringResource(R.string.file_properties_audio_year), it) }
        audioInfo.genre?.let { FilePropertyItem(stringResource(R.string.file_properties_audio_genre), it) }
        audioInfo.duration?.let { FilePropertyItem(stringResource(R.string.file_properties_media_duration), it.format()) }
        audioInfo.bitRate?.let {
            FilePropertyItem(
                stringResource(R.string.file_properties_media_bit_rate),
                stringResource(R.string.file_properties_media_bit_rate_format, it / 1000),
            )
        }
        audioInfo.sampleRate?.let {
            FilePropertyItem(
                stringResource(R.string.file_properties_audio_sample_rate),
                stringResource(R.string.file_properties_audio_sample_rate_format, it),
            )
        }
    }
}
