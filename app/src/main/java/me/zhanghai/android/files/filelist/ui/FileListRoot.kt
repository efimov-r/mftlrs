/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.core.graphics.Insets
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java8.nio.file.Path
import me.zhanghai.android.files.filelist.FileListPermissionDialog
import me.zhanghai.android.files.filelist.FileListViewModel
import me.zhanghai.android.files.navigation.NavigationItem
import me.zhanghai.android.files.navigation.NavigationItemListLiveData
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.valueCompat

// Главный экран целиком: «Назад», боковая панель, FileListScreen, диалоги. Состояния элементов
// Compose (прокрутка, поле имени) и отступы под системные панели хранит FileListActivity
@Composable
internal fun FileListRoot(
    viewModel: FileListViewModel,
    windowInsets: Insets, // px
    gridState: LazyGridState,
    createFileName: TextFieldState,
    isCreateFileNameFocusRequested: Boolean,
    onCreateFileNameFocused: () -> Unit,
    navigationListener: NavigationItem.Listener,
    onTitleChange: (String) -> Unit,
    onNavigationClick: () -> Unit,
    onNavigationOpenChange: (Boolean) -> Unit,
    onCopyPath: (Path) -> Unit,
    onOpenInNewTask: (Path) -> Unit,
    onPermissionDialogResult: (FileListPermissionDialog, shouldRequest: Boolean) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle() // только пока экран виден: иначе папка перечитывается в фоне
    val breadcrumbData = viewModel.breadcrumbLiveData.observeAsState().value

    val title = fileListTitle(state.topBar.pickOptions)
    LaunchedEffect(title) { onTitleChange(title) }

    // Порядок как у колбэков фрагмента: добавленный позже срабатывает первым. Условия — только
    // через enabled: BackHandler, появившийся в композиции позже, встал бы в начало очереди
    FileListExitConfirmation(enabled = state.topBar.pickOptions == null)
    BackHandler(enabled = breadcrumbData != null && breadcrumbData.selectedIndex > 0) { viewModel.navigateUp() }
    BackHandler(enabled = state.topBar.isSearchExpanded) { viewModel.collapseSearch() }
    BackHandler(enabled = state.selectionBar.isVisible) { viewModel.stopSelectionMode() }
    BackHandler(enabled = state.isFabExpanded) { viewModel.setFabExpanded(false) }
    BackHandler(enabled = state.isNavigationOpen) { onNavigationOpenChange(false) }

    FileListNavigation(
        isOpen = state.isNavigationOpen,
        isPersistentOpen = Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN
            .observeAsState(Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN.valueCompat).value,
        windowInsets = windowInsets,
        items = NavigationItemListLiveData.observeAsState(NavigationItemListLiveData.valueCompat).value,
        currentPath = viewModel.currentPathLiveData.observeAsState().value,
        listener = navigationListener,
        onOpenChange = onNavigationOpenChange,
    ) { screenInsets ->
        FileListScreen(
            windowInsets = screenInsets,
            isBottomBarVisible = state.bottomBar.isVisible,
            topBar = {
                FileListTopBar(
                    state = state.topBar,
                    selectionBar = state.selectionBar,
                    onNavigationClick = onNavigationClick,
                    onSearchExpand = { viewModel.expandSearch() },
                    onSearchCollapse = { viewModel.collapseSearch() },
                    onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                    onSearchSubmit = { viewModel.onSearchSubmit(it) },
                    onAction = { viewModel.onTopBarAction(it) },
                    onSelectionClose = { viewModel.stopSelectionMode() },
                    onSelectionAction = { viewModel.onSelectionAction(it) },
                )
            },
            breadcrumbs = {
                FileListBreadcrumbs(
                    data = breadcrumbData,
                    onNavigate = { viewModel.navigateTo(it) },
                    onCopyPath = onCopyPath,
                    onOpenInNewTask = onOpenInNewTask,
                )
            },
            content = { bottomInset ->
                FileListContent(
                    state = state.content,
                    gridState = gridState,
                    listener = viewModel,
                    bottomInset = bottomInset,
                    onRefresh = { viewModel.reload() },
                )
            },
            bottomBar = {
                FileListBottomBar(
                    state = state.bottomBar,
                    fileName = createFileName,
                    isFileNameFocusRequested = isCreateFileNameFocusRequested,
                    onFileNameFocused = onCreateFileNameFocused,
                    onClose = { viewModel.onBottomBarClose() },
                    onAction = { viewModel.onBottomBarAction(it, createFileName.text.toString()) },
                )
            },
            fab = { bottomPadding, endPadding ->
                FileListFab(
                    isExpanded = state.isFabExpanded,
                    bottomPadding = bottomPadding,
                    endPadding = endPadding,
                    onExpandedChange = { viewModel.setFabExpanded(it) },
                    onAction = { viewModel.onFabAction(it) },
                )
            },
        )
    }

    // Каждый в своём окне
    state.dialog?.let { FileListDialogHost(it, viewModel) }
    state.permissionDialog?.let { dialog ->
        FileListPermissionDialogHost(dialog) { onPermissionDialogResult(dialog, it) }
    }
}
