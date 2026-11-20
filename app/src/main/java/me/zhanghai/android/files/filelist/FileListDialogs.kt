/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.ui.compose.MftlrsInputDialog

// Диалоги главного экрана, были DialogFragment в filelist. Какой показан — FileListUiState.dialog,
// результат — во FileListViewModel. «Назад» и тап вне диалога — закрыть без действия
@Composable
internal fun FileListDialogHost(dialog: FileListDialog, viewModel: FileListViewModel) {
    val dismiss = { viewModel.dismissDialog() }
    // Новый диалог — новые поля, даже если прежний не успел уйти из композиции
    key(dialog) {
        when (dialog) {
            FileListDialog.CreateFile ->
                NameDialog(
                    title = stringResource(R.string.file_create_file_title),
                    state = rememberTextFieldState(),
                    onConfirm = { viewModel.onCreateFileOk(it) },
                    onDismissRequest = dismiss,
                )
            FileListDialog.CreateDirectory ->
                NameDialog(
                    title = stringResource(R.string.file_create_directory_title),
                    state = rememberTextFieldState(),
                    onConfirm = { viewModel.onCreateDirectoryOk(it) },
                    onDismissRequest = dismiss,
                )
            is FileListDialog.RenameFile ->
                NameDialog(
                    title = stringResource(R.string.rename),
                    // Выделено имя без расширения
                    state = rememberTextFieldState(dialog.file.name, TextRange(0, dialog.file.baseName.length)),
                    onConfirm = { viewModel.onRenameFileOk(dialog.file, it) },
                    onDismissRequest = dismiss,
                )
            is FileListDialog.NavigateToPath -> {
                val path = dialog.path.toUserFriendlyString()
                NameDialog(
                    title = stringResource(R.string.file_list_navigate_to_title),
                    state = rememberTextFieldState(path, TextRange(0, path.length)),
                    onConfirm = { viewModel.onNavigateToPathOk(dialog.path, it) },
                    onDismissRequest = dismiss,
                )
            }
            is FileListDialog.CreateArchive -> CreateArchiveDialog(dialog.files, viewModel)
            is FileListDialog.ConfirmDeleteFiles ->
                ConfirmDialog(
                    message = deleteMessageText(dialog.files),
                    onConfirm = {
                        dismiss()
                        viewModel.deleteFiles(dialog.files)
                    },
                    onDismissRequest = dismiss,
                )
            is FileListDialog.ConfirmReplaceFile ->
                ConfirmDialog(
                    message = stringResource(R.string.file_replace_message_format, dialog.file.name),
                    onConfirm = {
                        dismiss()
                        viewModel.replaceFile(dialog.file)
                    },
                    onDismissRequest = dismiss,
                )
            is FileListDialog.OpenApk -> OpenApkDialog(dialog.file, viewModel)
        }
    }
}

// Объяснение перед запросом разрешения; «Отмена», «Назад» и тап вне диалога — shouldRequest = false
@Composable
internal fun FileListPermissionDialogHost(
    dialog: FileListPermissionDialog,
    onResult: (shouldRequest: Boolean) -> Unit,
) {
    ConfirmDialog(
        message = stringResource(dialog.messageRes),
        confirmText = stringResource(dialog.confirmRes),
        onConfirm = { onResult(true) },
        onDismissRequest = { onResult(false) },
    )
}

@Composable
private fun NameDialog(
    title: String,
    state: TextFieldState,
    onConfirm: (String) -> Int?,
    onDismissRequest: () -> Unit,
) {
    MftlrsInputDialog(
        title = title,
        state = state,
        onConfirm = { onConfirm(state.text.toString()) },
        onDismissRequest = onDismissRequest,
    )
}

@Composable
private fun CreateArchiveDialog(files: FileItemSet, viewModel: FileListViewModel) {
    val name = remember(files) { defaultArchiveName(files).orEmpty() }
    val nameState = rememberTextFieldState(name, TextRange(0, name.length))
    val passwordState = rememberTextFieldState()
    var type by rememberSaveable { mutableStateOf(viewModel.createArchiveType) }

    MftlrsInputDialog(
        title = stringResource(R.string.file_create_archive_title),
        state = nameState,
        onConfirm = {
            viewModel.onCreateArchiveOk(files, nameState.text.toString(), type, passwordState.text.toString())
        },
        onDismissRequest = { viewModel.dismissDialog() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .selectableGroup(),
        ) {
            for (option in CreateArchiveType.entries) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = option == type,
                            role = Role.RadioButton,
                            onClick = {
                                type = option
                                viewModel.createArchiveType = option // как RadioGroup: запоминается сразу
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == type, onClick = null)
                    Text(
                        text = stringResource(option.titleRes),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        if (type.isPasswordSupported) {
            PasswordField(passwordState)
        }
    }
}

@Composable
private fun PasswordField(state: TextFieldState) {
    var isVisible by rememberSaveable { mutableStateOf(false) }
    OutlinedSecureTextField(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        label = { Text(stringResource(R.string.file_create_archive_password)) },
        textObfuscationMode = if (isVisible) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
        trailingIcon = {
            IconButton(onClick = { isVisible = !isVisible }) {
                Icon(
                    painter = painterResource(
                        if (isVisible) {
                            com.google.android.material.R.drawable.design_ic_visibility_off
                        } else {
                            com.google.android.material.R.drawable.design_ic_visibility
                        }
                    ),
                    contentDescription = stringResource(
                        com.google.android.material.R.string.password_toggle_content_description
                    ),
                )
            }
        },
    )
}

@Composable
private fun OpenApkDialog(file: FileItem, viewModel: FileListViewModel) {
    val dismiss = { viewModel.dismissDialog() }
    AlertDialog(
        onDismissRequest = dismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    dismiss()
                    viewModel.installApk(file)
                },
            ) { Text(stringResource(R.string.install)) }
        },
        // «Отмена» и «Просмотр» — слева от «Установить», как было: два главных действия рядом
        dismissButton = {
            Row {
                TextButton(onClick = dismiss) { Text(stringResource(android.R.string.cancel)) }
                TextButton(
                    onClick = {
                        dismiss()
                        viewModel.viewApk(file)
                    },
                ) { Text(stringResource(R.string.view)) }
            }
        },
        text = { Text(stringResource(R.string.file_open_apk_message)) },
    )
}

@Composable
private fun ConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    confirmText: String = stringResource(android.R.string.ok),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(android.R.string.cancel)) }
        },
        text = { Text(message) },
    )
}

@Composable
private fun deleteMessageText(files: FileItemSet): String =
    when (val message = deleteMessage(files)) {
        is DeleteMessage.One -> stringResource(message.formatRes, message.name)
        is DeleteMessage.Many -> pluralStringResource(message.formatRes, message.count, message.count)
    }
