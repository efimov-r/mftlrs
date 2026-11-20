/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.fileproperties

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.name
import me.zhanghai.android.files.fileproperties.apk.FilePropertiesApkTab
import me.zhanghai.android.files.fileproperties.apk.isApkTabAvailable
import me.zhanghai.android.files.fileproperties.audio.FilePropertiesAudioTab
import me.zhanghai.android.files.fileproperties.audio.isAudioTabAvailable
import me.zhanghai.android.files.fileproperties.basic.FilePropertiesBasicTab
import me.zhanghai.android.files.fileproperties.checksum.FilePropertiesChecksumTab
import me.zhanghai.android.files.fileproperties.checksum.isChecksumTabAvailable
import me.zhanghai.android.files.fileproperties.image.FilePropertiesImageTab
import me.zhanghai.android.files.fileproperties.image.isImageTabAvailable
import me.zhanghai.android.files.fileproperties.permission.FilePropertiesPermissionTab
import me.zhanghai.android.files.fileproperties.permission.isPermissionTabAvailable
import me.zhanghai.android.files.fileproperties.video.FilePropertiesVideoTab
import me.zhanghai.android.files.fileproperties.video.isVideoTabAvailable
import me.zhanghai.android.files.ui.compose.MftlrsDialog
import me.zhanghai.android.files.ui.compose.MftlrsViewModelScope

// Диалог «Свойства», был FilePropertiesDialogFragment: вкладки по типу файла, ViewModel вкладок живут,
// пока диалог открыт. Вкладка загружает данные, когда её впервые открыли (как фрагменты в ViewPager)
@Composable
fun FilePropertiesDialog(file: FileItem, onDismissRequest: () -> Unit) {
    MftlrsViewModelScope("FilePropertiesDialog") { finish ->
        val dismiss = {
            finish()
            onDismissRequest()
        }
        val fileViewModel = viewModel { FilePropertiesFileViewModel(file) }
        val tabs = remember(file) { filePropertiesTabs(file) }
        val pagerState = rememberPagerState { tabs.size }
        val coroutineScope = rememberCoroutineScope()
        var openedPages by rememberSaveable { mutableIntStateOf(1) } // битовая маска

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { openedPages = openedPages or (1 shl it) }
        }

        MftlrsDialog(
            title = stringResource(R.string.file_properties_title_format, file.name),
            onDismissRequest = dismiss,
            buttons = {
                TextButton(onClick = dismiss) { Text(stringResource(android.R.string.ok)) }
            },
        ) {
            Box {
                HorizontalDivider(Modifier.align(Alignment.BottomStart))
                PrimaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 8.dp,
                    divider = {},
                ) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = index == pagerState.currentPage,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(stringResource(tab.titleRes)) },
                        )
                    }
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                beyondViewportPageCount = tabs.size - 1, // как offscreenPageLimit: подсчёт папки не начинается заново
                verticalAlignment = Alignment.Top,
            ) { page ->
                if (openedPages and (1 shl page) == 0) return@HorizontalPager
                when (tabs[page]) {
                    FilePropertiesTabType.BASIC -> FilePropertiesBasicTab(fileViewModel)
                    FilePropertiesTabType.PERMISSION -> FilePropertiesPermissionTab(fileViewModel)
                    FilePropertiesTabType.IMAGE -> FilePropertiesImageTab(file)
                    FilePropertiesTabType.AUDIO -> FilePropertiesAudioTab(file)
                    FilePropertiesTabType.VIDEO -> FilePropertiesVideoTab(file)
                    FilePropertiesTabType.APK -> FilePropertiesApkTab(file)
                    FilePropertiesTabType.CHECKSUM -> FilePropertiesChecksumTab(file)
                }
            }
        }
    }
}

private enum class FilePropertiesTabType(@StringRes val titleRes: Int) {
    BASIC(R.string.file_properties_basic),
    PERMISSION(R.string.file_properties_permission),
    IMAGE(R.string.file_properties_image),
    AUDIO(R.string.file_properties_audio),
    VIDEO(R.string.file_properties_video),
    APK(R.string.file_properties_apk),
    CHECKSUM(R.string.file_properties_checksum),
}

private fun filePropertiesTabs(file: FileItem): List<FilePropertiesTabType> =
    buildList {
        add(FilePropertiesTabType.BASIC)
        if (isPermissionTabAvailable(file)) add(FilePropertiesTabType.PERMISSION)
        if (isImageTabAvailable(file)) add(FilePropertiesTabType.IMAGE)
        if (isAudioTabAvailable(file)) add(FilePropertiesTabType.AUDIO)
        if (isVideoTabAvailable(file)) add(FilePropertiesTabType.VIDEO)
        if (isApkTabAvailable(file)) add(FilePropertiesTabType.APK)
        if (isChecksumTabAvailable(file)) add(FilePropertiesTabType.CHECKSUM)
    }
