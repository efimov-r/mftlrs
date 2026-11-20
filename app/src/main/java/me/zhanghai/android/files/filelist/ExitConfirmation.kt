/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R
import me.zhanghai.android.files.ui.compose.MftlrsBottomSheet

// «Назад» спрашивает: закрыть приложение, свернуть или остаться.
// Ставить раньше остальных BackHandler экрана: позже добавленный срабатывает первым
@Composable
internal fun FileListExitConfirmation(enabled: Boolean) {
    val activity = requireNotNull(LocalActivity.current)
    var visible by remember { mutableStateOf(false) }

    BackHandler(enabled) { visible = true }

    ExitSheet(
        visible = visible,
        onClose = { activity.finishAndRemoveTask() }, // и убрать из недавних
        onMinimize = {
            visible = false
            activity.moveTaskToBack(true)
        },
        onDismissRequest = { visible = false },
    )
}

@Composable
private fun ExitSheet(
    visible: Boolean,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    MftlrsBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.file_list_exit_title, stringResource(R.string.app_name)),
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )

            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.close))
            }

            FilledTonalButton(
                onClick = onMinimize,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.file_list_exit_minimize))
            }

            OutlinedButton(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    }
}