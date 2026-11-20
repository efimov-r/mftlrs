/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.flow.drop

// Диалог с полем ввода: заголовок, поле в фокусе с клавиатурой, ОК и «Отмена»; Enter — ОК.
// onConfirm проверяет ввод и возвращает строку ошибки для поля или null; закрывает диалог вызывающий.
// Ошибка пропадает при изменении текста. Под полем — content (переключатели, второе поле)
@Composable
fun MftlrsInputDialog(
    title: String,
    state: TextFieldState,
    onConfirm: () -> Int?,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var errorRes by rememberSaveable { mutableStateOf<Int?>(null) } // @StringRes
    val focusRequester = remember { FocusRequester() }
    val confirm = { errorRes = onConfirm() }

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .drop(1)
            .collect { errorRes = null }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = confirm) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(android.R.string.cancel)) }
        },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    isError = errorRes != null,
                    supportingText = errorRes?.let { { Text(stringResource(it)) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    onKeyboardAction = { confirm() },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                content()
            }
        },
    )
}
