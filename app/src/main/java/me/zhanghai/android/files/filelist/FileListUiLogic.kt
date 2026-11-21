/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import android.net.Uri
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.isArchiveFile
import me.zhanghai.android.files.file.name
import me.zhanghai.android.files.filelist.data.FileItemSet
import me.zhanghai.android.files.filelist.data.PasteState
import me.zhanghai.android.files.filelist.data.PickOptions
import me.zhanghai.android.files.filelist.ui.FileListBottomBarAction
import me.zhanghai.android.files.filelist.ui.FileListSelectionAction
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.settings.FileSortOptions
import me.zhanghai.android.files.storage.createOrLog
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.util.asFileNameOrNull
import me.zhanghai.android.files.util.asPathNameOrNull
import java.net.URI

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

// Пункты панели выделения: были menu/file_list_select.xml и file_list_pick.xml. Режим выбора без
// выделенных — только «Выбрать все»
internal fun selectionBarActions(
    files: Collection<FileItem>,
    pickOptions: PickOptions?,
    currentPath: Path,
): List<FileListSelectionAction> {
    if (files.isEmpty()) {
        val canSelectMultiple = pickOptions == null || pickOptions.allowMultiple
        return if (canSelectMultiple) listOf(FileListSelectionAction.SELECT_ALL) else emptyList()
    }
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

// Ввод в диалоге имени или пути
internal sealed interface NameInput {
    data object Unchanged : NameInput // как было — закрыть диалог без действия

    data class Invalid(@StringRes val errorRes: Int) : NameInput

    data class Valid(val name: String) : NameInput
}

// Было FileNameDialogFragment.isNameValid(); name — уже без пробелов по краям
internal fun checkFileName(name: String, initialName: String?, hasFileWithName: (String) -> Boolean): NameInput =
    when {
        name == initialName -> NameInput.Unchanged
        name.isEmpty() -> NameInput.Invalid(R.string.file_name_error_empty)
        name.asFileNameOrNull() == null -> NameInput.Invalid(R.string.file_name_error_invalid)
        hasFileWithName(name) -> NameInput.Invalid(R.string.file_name_error_already_exists)
        else -> NameInput.Valid(name)
    }

// Было PathDialogFragment.isNameValid()
internal fun checkPathName(name: String, initialName: String?): NameInput =
    when {
        name == initialName -> NameInput.Unchanged
        name.isEmpty() -> NameInput.Invalid(R.string.file_list_path_error_empty)
        name.asPathNameOrNull() == null || name.toPathOrNull() == null ->
            NameInput.Invalid(R.string.file_list_path_error_invalid)
        else -> NameInput.Valid(name)
    }

// Путь или URI из поля «Перейти к…»; было в PathDialogFragment
internal fun String.toPathOrNull(): Path? {
    val uri = URI::class.createOrLog(this)
        // Also try to accept decoded path.
        ?: Uri.parse(this).let {
            URI::class.createOrLog(it.scheme, it.userInfo, it.host, it.port, it.path, it.query, it.fragment)
        }
    if (uri != null) {
        try {
            return Paths.get(uri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    if (startsWith('/')) {
        return Paths.get(this)
    }
    return null
}

// Имя архива по умолчанию: имя файла или общей папки; было в CreateArchiveDialogFragment
internal fun defaultArchiveName(files: Collection<FileItem>): String? {
    if (files.size == 1) {
        return files.single().path.fileName.toString()
    }
    val parent = files.mapTo(mutableSetOf()) { it.path.parent }.singleOrNull()
    return if (parent != null && parent.nameCount > 0) parent.fileName.toString() else null
}

// Текст подтверждения удаления; было в ConfirmDeleteFilesDialogFragment
internal sealed interface DeleteMessage {
    data class One(@StringRes val formatRes: Int, val name: String) : DeleteMessage

    data class Many(@PluralsRes val formatRes: Int, val count: Int) : DeleteMessage
}

internal fun deleteMessage(files: Collection<FileItem>): DeleteMessage {
    if (files.size == 1) {
        val file = files.single()
        val formatRes = if (file.attributesNoFollowLinks.isDirectory) {
            R.string.file_delete_message_directory_format
        } else {
            R.string.file_delete_message_file_format
        }
        return DeleteMessage.One(formatRes, file.name)
    }
    val allDirectories = files.all { it.attributesNoFollowLinks.isDirectory }
    val allFiles = files.none { it.attributesNoFollowLinks.isDirectory }
    val formatRes = when {
        allDirectories -> R.plurals.file_delete_message_multiple_directories_format
        allFiles -> R.plurals.file_delete_message_multiple_files_format
        else -> R.plurals.file_delete_message_multiple_mixed_format
    }
    return DeleteMessage.Many(formatRes, files.size)
}
