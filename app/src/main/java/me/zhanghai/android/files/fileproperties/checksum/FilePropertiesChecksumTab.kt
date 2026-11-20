/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.fileproperties.checksum

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.fileproperties.ChecksumCompareResult
import me.zhanghai.android.files.fileproperties.FilePropertiesTab
import me.zhanghai.android.files.fileproperties.FilePropertyItem
import me.zhanghai.android.files.fileproperties.compareChecksum

fun isChecksumTabAvailable(file: FileItem): Boolean = file.attributes.isRegularFile

// Вкладка «Контрольные суммы», была FilePropertiesChecksumTabFragment: суммы и поле для сравнения
// с любой из них
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilePropertiesChecksumTab(file: FileItem) {
    val viewModel = viewModel { FilePropertiesChecksumTabViewModel(file.path) }
    val stateful by viewModel.checksumInfoLiveData.observeAsState()
    val compareState = rememberTextFieldState()

    FilePropertiesTab(stateful, onRefresh = viewModel::reload) { checksumInfo ->
        checksumInfo.checksums.forEach { (algorithm, checksum) ->
            FilePropertyItem(stringResource(algorithm.nameRes), checksum)
        }
        val result = compareChecksum(checksumInfo.checksums, compareState.text.toString())
        OutlinedTextField(
            state = compareState,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.file_properties_checksum_compare)) },
            labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
            placeholder = { Text(stringResource(R.string.file_properties_checksum_compare_placeholder)) },
            isError = result == ChecksumCompareResult.NoMatch,
            supportingText = {
                when (result) {
                    ChecksumCompareResult.Empty -> {}
                    is ChecksumCompareResult.Match ->
                        Text(
                            stringResource(
                                R.string.file_properties_checksum_compare_match_format,
                                stringResource(result.algorithm.nameRes),
                            )
                        )
                    is ChecksumCompareResult.PrefixMatch ->
                        Text(
                            stringResource(
                                R.string.file_properties_checksum_compare_prefix_match_format,
                                stringResource(result.algorithm.nameRes),
                            )
                        )
                    ChecksumCompareResult.NoMatch -> Text(stringResource(R.string.file_properties_checksum_compare_no_match))
                }
            },
        )
    }
}
