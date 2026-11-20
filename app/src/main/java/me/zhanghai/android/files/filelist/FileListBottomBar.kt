/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R
import me.zhanghai.android.files.navigation.NavigationRootMapLiveData
import me.zhanghai.android.files.util.valueCompat

// Нижняя панель главного экрана — перенос тулбара с menu/file_list_paste.xml и file_list_pick_bottom.xml:
// крестик, заголовок или поле имени файла, кнопка действия. Фон, тень и выезд снизу — у FileListScreen

internal enum class FileListBottomBarAction(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
) {
    PASTE(R.string.paste, R.drawable.paste_icon_control_normal_24dp),
    EXTRACT_HERE(R.string.file_list_paste_action_extract_here, R.drawable.paste_icon_control_normal_24dp), // вставка из архива
    OPEN(R.string.select, R.drawable.check_icon_control_normal_24dp),
    CREATE(R.string.save, R.drawable.save_icon_control_normal_24dp),
}

@Composable
internal fun FileListBottomBar(
    state: FileListUiState.BottomBar,
    fileName: TextFieldState, // поле имени — во фрагменте: он задаёт имя и читает его по «Сохранить»
    isFileNameFocusRequested: Boolean,
    onFileNameFocused: () -> Unit, // поле забрало фокус, флаг можно сбросить
    onClose: () -> Unit,
    onAction: (FileListBottomBarAction) -> Unit,
) {
    val paddingStart = dimensionResource(R.dimen.file_list_toolbar_padding_start)
    val title = state.title

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = paddingStart,
                end = dimensionResource(R.dimen.file_list_toolbar_padding_end_no_overflow),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarIcon(
            iconRes = R.drawable.close_icon_control_normal_24dp,
            descriptionRes = R.string.close,
            onClick = onClose,
        )
        Spacer(modifier = Modifier.width(TITLE_START - 48.dp - paddingStart))
        if (title is FileListUiState.BottomBarTitle.FileName) {
            FileNameField(
                state = fileName,
                isFocusRequested = isFileNameFocusRequested,
                onFocused = onFileNameFocused,
                onConfirm = { onAction(FileListBottomBarAction.CREATE) },
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                text = titleText(title),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        val action = state.action
        BarIcon(
            iconRes = action.iconRes,
            descriptionRes = action.titleRes,
            enabled = state.isActionEnabled,
            onClick = { onAction(action) },
        )
    }
}

// «Перемещение: N» и др. или «Открыть «<папка>»»: имя хранилища, если папка — его корень
@Composable
private fun titleText(title: FileListUiState.BottomBarTitle): String =
    when (title) {
        FileListUiState.BottomBarTitle.FileName -> ""
        is FileListUiState.BottomBarTitle.Paste -> stringResource(title.formatRes, title.count)
        is FileListUiState.BottomBarTitle.OpenDirectory -> {
            val context = LocalContext.current
            val name = remember(title.path) {
                NavigationRootMapLiveData.valueCompat[title.path]?.getName(context) ?: title.path.name
            }
            stringResource(R.string.file_list_open_current_directory_format, name)
        }
    }

// Поле имени без рамки, как EditText с background="@null"; Enter — «Сохранить»
@Composable
private fun FileNameField(
    state: TextFieldState,
    isFocusRequested: Boolean,
    onFocused: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val textStyle = MaterialTheme.typography.bodyLarge

    LaunchedEffect(isFocusRequested) {
        if (!isFocusRequested) return@LaunchedEffect

        focusRequester.requestFocus()
        onFocused()
    }

    BasicTextField(
        state = state,
        modifier = modifier.focusRequester(focusRequester),
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        lineLimits = TextFieldLineLimits.SingleLine,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        onKeyboardAction = { onConfirm() },
        decorator = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (state.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.file_list_create_file_name_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        style = textStyle,
                    )
                }
                innerTextField()
            }
        },
    )
}
