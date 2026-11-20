/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.drop
import me.zhanghai.android.files.R
import me.zhanghai.android.files.filelist.FileListUiState
import me.zhanghai.android.files.filelist.data.PickOptions
import me.zhanghai.android.files.settings.FileSortOptions.By
import me.zhanghai.android.files.settings.FileSortOptions.Order
import me.zhanghai.android.files.settings.FileViewType

// Верхняя панель главного экрана — перенос CrossfadeSubtitleToolbar и menu/file_list.xml; поверх неё —
// панель выделения, перенос OverlayToolbarActionMode с menu/file_list_select.xml и file_list_pick.xml

internal val TITLE_START = 72.dp // contentInsetStartWithNavigation тулбара; нужен и нижней панели
private val SUBTITLE_SIZE = 12.sp // TextAppearance.AppCompat.Widget.ActionBar.Subtitle.Small, там 12dp

// Пункты панели выделения: с иконкой — кнопка на панели, без — пункт меню ⋮
internal enum class FileListSelectionAction(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int?,
) {
    OPEN(R.string.select, R.drawable.check_icon_control_normal_24dp),
    CREATE(R.string.save, R.drawable.save_icon_control_normal_24dp),
    CUT(R.string.cut, R.drawable.cut_icon_control_normal_24dp),
    COPY(R.string.copy, R.drawable.copy_icon_control_normal_24dp),
    COPY_EXTRACT(R.string.file_list_select_action_extract, R.drawable.extract_icon_control_normal_24dp), // «Копировать» для файлов в архиве
    DELETE(R.string.delete, R.drawable.delete_icon_control_normal_24dp),
    EXTRACT(R.string.file_list_select_action_extract, null),
    ARCHIVE(R.string.file_list_select_action_archive, null),
    SHARE(R.string.share, null),
    SELECT_ALL(R.string.select_all, null),
}

// Пункты меню панели, вместо id из menu/file_list.xml
internal enum class FileListTopBarAction {
    VIEW_LIST,
    VIEW_GRID,
    SORT_BY_NAME,
    SORT_BY_TYPE,
    SORT_BY_SIZE,
    SORT_BY_LAST_MODIFIED,
    SORT_ORDER_ASCENDING,
    SORT_DIRECTORIES_FIRST,
    VIEW_SORT_PATH_SPECIFIC,
    NEW_TASK,
    NAVIGATE_UP,
    NAVIGATE_TO,
    REFRESH,
    SELECT_ALL,
    SHOW_HIDDEN_FILES,
    SHARE,
    COPY_PATH,
    OPEN_IN_TERMINAL,
    ADD_BOOKMARK,
    CREATE_SHORTCUT,
}

// Заголовок экрана по режиму выбора; его же фрагмент ставит заголовком Activity
@Composable
internal fun fileListTitle(pickOptions: PickOptions?): String {
    if (pickOptions == null) return stringResource(R.string.file_list_title)

    val count = if (pickOptions.allowMultiple) Int.MAX_VALUE else 1
    return when (pickOptions.mode) {
        PickOptions.Mode.OPEN_FILE -> pluralStringResource(R.plurals.file_list_title_open_file, count)
        PickOptions.Mode.CREATE_FILE -> stringResource(R.string.file_list_title_create_file)
        PickOptions.Mode.OPEN_DIRECTORY -> pluralStringResource(R.plurals.file_list_title_open_directory, count)
    }
}

// «Загрузка…», «Ошибка», «N папок, M файлов» или «Пусто», как FileListFragment.getSubtitle
@Composable
private fun subtitleText(subtitle: FileListUiState.Subtitle?): String =
    when (subtitle) {
        null -> ""
        FileListUiState.Subtitle.Loading -> stringResource(R.string.loading)
        FileListUiState.Subtitle.Error -> stringResource(R.string.error)
        is FileListUiState.Subtitle.Counts -> {
            val directories = subtitle.directoryCount
            val files = subtitle.fileCount
            val directoriesText = if (directories > 0) {
                pluralStringResource(R.plurals.file_list_subtitle_directory_count_format, directories, directories)
            } else {
                null
            }
            val filesText = if (files > 0) {
                pluralStringResource(R.plurals.file_list_subtitle_file_count_format, files, files)
            } else {
                null
            }
            when {
                directoriesText != null && filesText != null ->
                    directoriesText + stringResource(R.string.file_list_subtitle_separator) + filesText
                directoriesText != null -> directoriesText
                filesText != null -> filesText
                else -> stringResource(R.string.empty)
            }
        }
    }

@Composable
internal fun FileListTopBar(
    state: FileListUiState.TopBar,
    selectionBar: FileListUiState.SelectionBar, // при скрытии гаснет со старыми заголовком и пунктами
    onNavigationClick: () -> Unit,
    onSearchExpand: () -> Unit,
    onSearchCollapse: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onAction: (FileListTopBarAction) -> Unit,
    onSelectionClose: () -> Unit,
    onSelectionAction: (FileListSelectionAction) -> Unit,
) {
    val fadeDuration = integerResource(android.R.integer.config_shortAnimTime)

    Box(modifier = Modifier.fillMaxSize()) {
        MainBar(
            state = state,
            onNavigationClick = onNavigationClick,
            onSearchExpand = onSearchExpand,
            onSearchCollapse = onSearchCollapse,
            onSearchQueryChange = onSearchQueryChange,
            onSearchSubmit = onSearchSubmit,
            onAction = onAction,
            // Под панелью выделения основная остаётся (поиск не теряет текст), но скрыта от TalkBack и тестов
            modifier = if (selectionBar.isVisible) Modifier.clearAndSetSemantics {} else Modifier,
        )
        AnimatedVisibility(
            visible = selectionBar.isVisible,
            enter = fadeIn(tween(fadeDuration)),
            exit = fadeOut(tween(fadeDuration)),
        ) {
            SelectionBar(
                title = stringResource(R.string.file_list_select_title_format, selectionBar.count),
                actions = selectionBar.actions,
                onClose = onSelectionClose,
                onAction = onSelectionAction,
            )
        }
    }
}

@Composable
private fun MainBar(
    state: FileListUiState.TopBar,
    onNavigationClick: () -> Unit,
    onSearchExpand: () -> Unit,
    onSearchCollapse: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onAction: (FileListTopBarAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = dimensionResource(R.dimen.file_list_toolbar_padding_start),
                end = dimensionResource(R.dimen.file_list_toolbar_padding_end_with_overflow),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.isSearchExpanded) {
            BarIcon(
                iconRes = androidx.appcompat.R.drawable.abc_ic_ab_back_material,
                descriptionRes = androidx.appcompat.R.string.abc_toolbar_collapse_description,
                onClick = onSearchCollapse,
            )
            SearchField(
                initialQuery = state.searchQuery,
                onQueryChange = onSearchQueryChange,
                onSubmit = onSearchSubmit,
                modifier = Modifier.weight(1f),
            )
        } else {
            BarIcon(
                iconRes = R.drawable.menu_icon_control_normal_24dp,
                descriptionRes = R.string.open_navigation_drawer,
                onClick = onNavigationClick,
            )
            Spacer(modifier = Modifier.width(TITLE_START - 48.dp - dimensionResource(R.dimen.file_list_toolbar_padding_start)))
            Title(
                title = fileListTitle(state.pickOptions),
                subtitle = subtitleText(state.subtitle),
                modifier = Modifier.weight(1f),
            )
            BarIcon(
                iconRes = R.drawable.search_icon_control_normal_24dp,
                descriptionRes = R.string.search,
                onClick = onSearchExpand,
            )
            ViewSortMenu(state, onAction)
        }
        OverflowMenu(state, onAction)
    }
}

// Панель выделения: крестик, «Выбрано: N», кнопки и меню ⋮. Непрозрачная, касания до основной не пропускает
@Composable
private fun SelectionBar(
    title: String,
    actions: List<FileListSelectionAction>,
    onClose: () -> Unit,
    onAction: (FileListSelectionAction) -> Unit,
) {
    val menuActions = actions.filter { it.iconRes == null }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .pointerInput(Unit) {}
            .padding(
                start = dimensionResource(R.dimen.file_list_toolbar_padding_start),
                end = dimensionResource(R.dimen.file_list_toolbar_padding_end_with_overflow),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarIcon(
            iconRes = R.drawable.close_icon_control_normal_24dp,
            descriptionRes = R.string.close,
            onClick = onClose,
        )
        Spacer(modifier = Modifier.width(TITLE_START - 48.dp - dimensionResource(R.dimen.file_list_toolbar_padding_start)))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge,
        )
        actions.forEach { action ->
            action.iconRes?.let { iconRes ->
                BarIcon(
                    iconRes = iconRes,
                    descriptionRes = action.titleRes,
                    onClick = { onAction(action) },
                )
            }
        }
        if (menuActions.isNotEmpty()) {
            SelectionOverflowMenu(menuActions, onAction)
        }
    }
}

@Composable
private fun SelectionOverflowMenu(
    actions: List<FileListSelectionAction>,
    onAction: (FileListSelectionAction) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }

    Box {
        BarIcon(
            iconRes = R.drawable.more_vertical_icon_white_24dp,
            descriptionRes = androidx.appcompat.R.string.abc_action_menu_overflow_description,
            onClick = { isExpanded = true },
        )
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            actions.forEach { action ->
                TextItem(action.titleRes) {
                    isExpanded = false
                    onAction(action)
                }
            }
        }
    }
}

@Composable
private fun Title(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge,
        )
        // Подзаголовок сменяется через затухание, как в CrossfadeSubtitleToolbar
        Crossfade(
            targetState = subtitle,
            animationSpec = tween(durationMillis = integerResource(android.R.integer.config_shortAnimTime)),
            label = "subtitle",
        ) { text ->
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = SUBTITLE_SIZE),
                )
            }
        }
    }
}

// Поле поиска вместо SearchView: подсказка, крестик очистки, Enter — поиск сразу
@Composable
private fun SearchField(
    initialQuery: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldState = rememberTextFieldState(initialQuery, TextRange(initialQuery.length))
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    val focusRequester = remember { FocusRequester() }
    val textStyle = MaterialTheme.typography.bodyLarge

    LaunchedEffect(Unit) { focusRequester.requestFocus() } // как SearchView при раскрытии
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }
            .drop(1) // начальный текст — не изменение
            .collect { currentOnQueryChange(it) }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            state = fieldState,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
                .focusRequester(focusRequester),
            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            lineLimits = TextFieldLineLimits.SingleLine,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onKeyboardAction = { onSubmit(fieldState.text.toString()) },
            decorator = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (fieldState.text.isEmpty()) {
                        Text(
                            text = stringResource(androidx.appcompat.R.string.abc_search_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            style = textStyle,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (fieldState.text.isNotEmpty()) {
            BarIcon(
                iconRes = R.drawable.close_icon_control_normal_24dp,
                descriptionRes = androidx.appcompat.R.string.abc_searchview_description_clear,
                onClick = { fieldState.clearText() },
            )
        }
    }
}

// «Вид и сортировка»: группы с одним выбором и флажки, между группами — разделитель
@Composable
private fun ViewSortMenu(
    state: FileListUiState.TopBar,
    onAction: (FileListTopBarAction) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val sortOptions = state.sortOptions

    fun select(action: FileListTopBarAction) {
        isExpanded = false
        onAction(action)
    }

    Box {
        BarIcon(
            iconRes = R.drawable.sort_icon_control_normal_24dp,
            descriptionRes = R.string.file_list_action_view_sort,
            onClick = { isExpanded = true },
        )
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            RadioItem(R.string.file_list_action_view_list, state.viewType == FileViewType.LIST) {
                select(FileListTopBarAction.VIEW_LIST)
            }
            RadioItem(R.string.file_list_action_view_grid, state.viewType == FileViewType.GRID) {
                select(FileListTopBarAction.VIEW_GRID)
            }
            HorizontalDivider()
            RadioItem(R.string.file_list_action_sort_by_name, sortOptions.by == By.NAME) {
                select(FileListTopBarAction.SORT_BY_NAME)
            }
            RadioItem(R.string.file_list_action_sort_by_type, sortOptions.by == By.TYPE) {
                select(FileListTopBarAction.SORT_BY_TYPE)
            }
            RadioItem(R.string.file_list_action_sort_by_size, sortOptions.by == By.SIZE) {
                select(FileListTopBarAction.SORT_BY_SIZE)
            }
            RadioItem(R.string.file_list_action_sort_by_last_modified, sortOptions.by == By.LAST_MODIFIED) {
                select(FileListTopBarAction.SORT_BY_LAST_MODIFIED)
            }
            CheckItem(R.string.file_list_action_sort_order_ascending, sortOptions.order == Order.ASCENDING) {
                select(FileListTopBarAction.SORT_ORDER_ASCENDING)
            }
            CheckItem(R.string.file_list_action_sort_directories_first, sortOptions.isDirectoriesFirst) {
                select(FileListTopBarAction.SORT_DIRECTORIES_FIRST)
            }
            HorizontalDivider()
            CheckItem(R.string.file_list_action_view_sort_path_specific, state.isViewSortPathSpecific) {
                select(FileListTopBarAction.VIEW_SORT_PATH_SPECIFIC)
            }
        }
    }
}

// Меню ⋮: пункты и порядок — как в menu/file_list.xml
@Composable
private fun OverflowMenu(
    state: FileListUiState.TopBar,
    onAction: (FileListTopBarAction) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }

    fun select(action: FileListTopBarAction) {
        isExpanded = false
        onAction(action)
    }

    Box {
        // Описание — как у ⋮ AppCompat: по нему кнопку находят UI-тесты
        BarIcon(
            iconRes = R.drawable.more_vertical_icon_white_24dp,
            descriptionRes = androidx.appcompat.R.string.abc_action_menu_overflow_description,
            onClick = { isExpanded = true },
        )
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            TextItem(R.string.file_list_action_new_task) { select(FileListTopBarAction.NEW_TASK) }
            TextItem(R.string.file_list_action_navigate_up) { select(FileListTopBarAction.NAVIGATE_UP) }
            TextItem(R.string.file_list_action_navigate_to) { select(FileListTopBarAction.NAVIGATE_TO) }
            TextItem(R.string.refresh) { select(FileListTopBarAction.REFRESH) }
            if (state.isSelectAllVisible) {
                TextItem(R.string.select_all) { select(FileListTopBarAction.SELECT_ALL) }
            }
            CheckItem(R.string.file_list_action_show_hidden_files, state.showHiddenFiles) {
                select(FileListTopBarAction.SHOW_HIDDEN_FILES)
            }
            TextItem(R.string.share) { select(FileListTopBarAction.SHARE) }
            TextItem(R.string.file_list_action_copy_path) { select(FileListTopBarAction.COPY_PATH) }
            TextItem(R.string.file_list_action_open_in_terminal) { select(FileListTopBarAction.OPEN_IN_TERMINAL) }
            TextItem(R.string.file_list_action_add_bookmark) { select(FileListTopBarAction.ADD_BOOKMARK) }
            TextItem(R.string.file_list_action_create_shortcut) { select(FileListTopBarAction.CREATE_SHORTCUT) }
        }
    }
}

@Composable
private fun TextItem(
    @StringRes textRes: Int,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(textRes)) },
        onClick = onClick,
    )
}

@Composable
private fun RadioItem(
    @StringRes textRes: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(textRes)) },
        onClick = onClick,
        trailingIcon = { RadioButton(selected = isSelected, onClick = null) },
    )
}

@Composable
private fun CheckItem(
    @StringRes textRes: Int,
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(textRes)) },
        onClick = onClick,
        trailingIcon = { Checkbox(checked = isChecked, onCheckedChange = null) },
    )
}

// Иконка панели: цвет — ?colorControlNormal темы панели (textColorPrimary, то есть onSurface); недоступная —
// с прозрачностью 0,38, как неактивный пункт меню. Нужна и нижней панели. Описание — на самой кнопке:
// у неактивной описание иконки не сливается в узел кнопки, и тест видит иконку активной
@Composable
internal fun BarIcon(
    @DrawableRes iconRes: Int,
    @StringRes descriptionRes: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = MaterialTheme.colorScheme.onSurface
    val description = stringResource(descriptionRes)

    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description },
        enabled = enabled,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (enabled) color else color.copy(alpha = 0.38f),
        )
    }
}
