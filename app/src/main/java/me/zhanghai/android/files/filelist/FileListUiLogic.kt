/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.Success

// Логика главного экрана без LiveData и настроек: её вызывает FileListViewModel, проверяют
// unit-тесты (src/test).

internal fun fileListSubtitle(
    stateful: Stateful<List<FileItem>>,
    isSearching: Boolean,
): FileListUiState.Subtitle = when {
    stateful is Failure -> FileListUiState.Subtitle.Error
    stateful is Loading && !isSearching -> FileListUiState.Subtitle.Loading
    else -> {
        val files = stateful.value.orEmpty()
        val directoryCount = files.count { it.attributes.isDirectory }
        FileListUiState.Subtitle.Counts(directoryCount, files.size - directoryCount)
    }
}

// Ошибка на месте списка — только если файлов нет; иначе её показывает тост
internal fun FileListUiState.Content.withStatus(
    stateful: Stateful<List<FileItem>>,
    isSearching: Boolean,
): FileListUiState.Content {
    val hasFiles = !stateful.value.isNullOrEmpty()
    val throwable = (stateful as? Failure)?.throwable
    return copy(
        isRefreshing = stateful is Loading && (hasFiles || isSearching),
        isLoading = stateful is Loading && !(hasFiles || isSearching),
        errorText = if (hasFiles) null else throwable?.toString(),
        isEmpty = stateful is Success && !hasFiles,
    )
}

// Результаты поиска не сортируются: остаются в порядке нахождения
internal fun filterAndSortFiles(
    files: List<FileItem>,
    showHiddenFiles: Boolean,
    sortOptions: FileSortOptions,
    isSearching: Boolean,
): List<FileItem> {
    var result = files
    if (!showHiddenFiles) {
        result = result.filterNot { it.isHidden }
    }
    if (!isSearching) {
        result = result.sortedWith(sortOptions.createComparator())
    }
    return result
}

// Пункты панели выделения: были menu/file_list_select.xml и file_list_pick.xml
internal fun selectionBarActions(
    files: Collection<FileItem>,
    pickOptions: PickOptions?,
    currentPath: Path,
): List<FileListSelectionAction> {
    if (pickOptions != null) {
        val isOpen = when (pickOptions.mode) {
            PickOptions.Mode.OPEN_FILE, PickOptions.Mode.OPEN_DIRECTORY -> true
            PickOptions.Mode.CREATE_FILE -> false
        }
        return buildList {
            add(if (isOpen) FileListSelectionAction.OPEN else FileListSelectionAction.CREATE)
            if (pickOptions.allowMultiple) {
                add(FileListSelectionAction.SELECT_ALL)
            }
        }
    }
    val isAnyFileReadOnly = files.any { it.path.fileSystem.isReadOnly }
    val areAllFilesArchivePaths = files.all { it.path.isArchivePath }
    val areAllFilesArchiveFiles = files.all { it.isArchiveFile }
    return buildList {
        if (!isAnyFileReadOnly) {
            add(FileListSelectionAction.CUT)
        }
        add(if (areAllFilesArchivePaths) FileListSelectionAction.COPY_EXTRACT else FileListSelectionAction.COPY)
        if (!isAnyFileReadOnly) {
            add(FileListSelectionAction.DELETE)
        }
        if (areAllFilesArchiveFiles) {
            add(FileListSelectionAction.EXTRACT)
        }
        if (!currentPath.fileSystem.isReadOnly) {
            add(FileListSelectionAction.ARCHIVE)
        }
        add(FileListSelectionAction.SHARE)
        add(FileListSelectionAction.SELECT_ALL)
    }
}

// Нижняя панель: были menu/file_list_paste.xml и file_list_pick_bottom.xml; null — панель скрыта
internal fun fileListBottomBar(
    pickOptions: PickOptions?,
    pasteState: PasteState,
    currentPath: Path,
): FileListUiState.BottomBar? {
    if (pickOptions != null) {
        return when (pickOptions.mode) {
            PickOptions.Mode.CREATE_FILE ->
                FileListUiState.BottomBar(
                    isVisible = true,
                    title = FileListUiState.BottomBarTitle.FileName,
                    action = FileListBottomBarAction.CREATE,
                    isActionEnabled = true,
                )
            PickOptions.Mode.OPEN_DIRECTORY ->
                FileListUiState.BottomBar(
                    isVisible = true,
                    title = FileListUiState.BottomBarTitle.OpenDirectory(currentPath),
                    action = FileListBottomBarAction.OPEN,
                    isActionEnabled = true,
                )
            PickOptions.Mode.OPEN_FILE -> null
        }
    }
    val files = pasteState.files
    if (files.isEmpty()) return null
    val areAllFilesArchivePaths = files.all { it.path.isArchivePath }
    val titleRes = when {
        !pasteState.copy -> R.string.file_list_paste_move_title_format
        areAllFilesArchivePaths -> R.string.file_list_paste_extract_title_format
        else -> R.string.file_list_paste_copy_title_format
    }
    return FileListUiState.BottomBar(
        isVisible = true,
        title = FileListUiState.BottomBarTitle.Paste(titleRes, files.size),
        action = if (areAllFilesArchivePaths) FileListBottomBarAction.EXTRACT_HERE else FileListBottomBarAction.PASTE,
        isActionEnabled = !currentPath.fileSystem.isReadOnly,
    )
}

// Выделить или снять files; true — выделение изменилось. files может быть этим же набором
internal fun FileItemSet.select(files: Collection<FileItem>, selected: Boolean): Boolean {
    if (files === this) {
        if (selected || isEmpty()) return false
        clear()
        return true
    }
    var changed = false
    for (file in files) {
        changed = changed or if (selected) add(file) else remove(file)
    }
    return changed
}

// Вырезать и копировать не смешиваются: смена режима сбрасывает прежние файлы
internal fun PasteState.add(copy: Boolean, files: Collection<FileItem>): Boolean {
    var changed = false
    if (this.copy != copy) {
        changed = this.files.isNotEmpty()
        this.files.clear()
        this.copy = copy
    }
    return this.files.addAll(files) or changed
}
