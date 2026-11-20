/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.name
import me.zhanghai.android.files.filelist.data.PasteState
import me.zhanghai.android.files.filelist.data.PickOptions
import me.zhanghai.android.files.filelist.data.fileItemSetOf
import me.zhanghai.android.files.filelist.ui.FileListBottomBarAction
import me.zhanghai.android.files.filelist.ui.FileListSelectionAction.ARCHIVE
import me.zhanghai.android.files.filelist.ui.FileListSelectionAction.COPY
import me.zhanghai.android.files.filelist.ui.FileListSelectionAction.COPY_EXTRACT
import me.zhanghai.android.files.filelist.ui.FileListSelectionAction.CREATE
import me.zhanghai.android.files.filelist.ui.FileListSelectionAction.CUT
import me.zhanghai.android.files.filelist.ui.FileListSelectionAction.DELETE
import me.zhanghai.android.files.filelist.ui.FileListSelectionAction.EXTRACT
import me.zhanghai.android.files.filelist.ui.FileListSelectionAction.OPEN
import me.zhanghai.android.files.filelist.ui.FileListSelectionAction.SELECT_ALL
import me.zhanghai.android.files.filelist.ui.FileListSelectionAction.SHARE
import me.zhanghai.android.files.settings.FileSortOptions
import me.zhanghai.android.files.settings.FileSortOptions.By
import me.zhanghai.android.files.settings.FileSortOptions.Order
import me.zhanghai.android.files.settings.FileViewType
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Success
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FileListUiLogicTest {
    private val dir = linuxPath("/d")
    private val archiveDir = archivePath("/d/b.zip")
    private val files = listOf(directory("/d/sub"), file("/d/a"), file("/d/b"))
    private val error = IOException("boom")

    // Подзаголовок

    @Test
    fun subtitle_countsDirectoriesAndFiles() {
        assertEquals(FileListUiState.Subtitle.Counts(1, 2), fileListSubtitle(Success(files), false))
    }

    @Test
    fun subtitle_loadingAndError() {
        assertEquals(FileListUiState.Subtitle.Loading, fileListSubtitle(Loading(files), false))
        assertEquals(FileListUiState.Subtitle.Error, fileListSubtitle(Failure(files, error), false))
    }

    @Test
    fun subtitle_whileSearching_countsFoundSoFar() {
        assertEquals(FileListUiState.Subtitle.Counts(1, 2), fileListSubtitle(Loading(files), true))
        assertEquals(FileListUiState.Subtitle.Counts(0, 0), fileListSubtitle(Loading(null), true))
    }

    // Статусы списка

    @Test
    fun status_loadingWithoutFiles_showsLoading() {
        val content = FileListUiState.Content().withStatus(Loading(null), false)

        assertTrue(content.isLoading)
        assertFalse(content.isRefreshing)
    }

    @Test
    fun status_loadingWithFilesOrSearching_showsRefresh() {
        val reloading = FileListUiState.Content().withStatus(Loading(files), false)
        val searching = FileListUiState.Content().withStatus(Loading(null), true)

        assertTrue(reloading.isRefreshing && !reloading.isLoading)
        assertTrue(searching.isRefreshing && !searching.isLoading)
    }

    @Test
    fun status_successWithoutFiles_isEmpty() {
        assertTrue(FileListUiState.Content().withStatus(Success(emptyList()), false).isEmpty)
        assertFalse(FileListUiState.Content().withStatus(Success(files), false).isEmpty)
    }

    @Test
    fun status_error_onScreenOnlyWithoutFiles() {
        val withoutFiles = FileListUiState.Content().withStatus(Failure(null, error), false)
        val withFiles = FileListUiState.Content().withStatus(Failure(files, error), false)

        assertEquals(error.toString(), withoutFiles.errorText)
        assertNull(withFiles.errorText)
    }

    @Test
    fun status_keepsOtherFields() {
        val content = FileListUiState.Content(viewType = FileViewType.GRID, isLoading = true)

        val updated = content.withStatus(Success(files), false)

        assertEquals(FileViewType.GRID, updated.viewType)
        assertFalse(updated.isLoading)
    }

    // Фильтр и сортировка

    @Test
    fun filterAndSort_hidesHiddenAndSorts() {
        val all = listOf(file("/d/b"), file("/d/.h", isHidden = true), directory("/d/z"), file("/d/a"))
        val sortOptions = FileSortOptions(By.NAME, Order.ASCENDING, true)

        val hidden = filterAndSortFiles(all, false, sortOptions, false)
        val shown = filterAndSortFiles(all, true, sortOptions, false)

        assertEquals(listOf("z", "a", "b"), hidden.map { it.name })
        assertEquals(listOf("z", "a", "b", ".h"), shown.map { it.name })
    }

    @Test
    fun filterAndSort_searchKeepsFoundOrder() {
        val found = listOf(file("/d/b"), file("/d/x/.h", isHidden = true), file("/d/a"))
        val sortOptions = FileSortOptions(By.NAME, Order.ASCENDING, true)

        val result = filterAndSortFiles(found, false, sortOptions, true)

        assertEquals(listOf("b", "a"), result.map { it.name })
    }

    // Панель выделения

    @Test
    fun selectionBar_files() {
        val actions = selectionBarActions(listOf(file("/d/a"), directory("/d/sub")), null, dir)

        assertEquals(listOf(CUT, COPY, DELETE, ARCHIVE, SHARE, SELECT_ALL), actions)
    }

    @Test
    fun selectionBar_archiveFiles_canExtract() {
        val zips = listOf(file("/d/a.zip", mimeType = ZIP), file("/d/b.zip", mimeType = ZIP))
        val mixed = listOf(file("/d/a.zip", mimeType = ZIP), file("/d/a"))

        assertEquals(listOf(CUT, COPY, DELETE, EXTRACT, ARCHIVE, SHARE, SELECT_ALL), selectionBarActions(zips, null, dir))
        assertFalse(EXTRACT in selectionBarActions(mixed, null, dir))
    }

    @Test
    fun selectionBar_insideArchive_readOnly() {
        val entries = listOf(fileItem(archivePath("/d/b.zip", "x")), fileItem(archivePath("/d/b.zip", "y")))

        val actions = selectionBarActions(entries, null, archiveDir)

        assertEquals(listOf(COPY_EXTRACT, SHARE, SELECT_ALL), actions)
    }

    @Test
    fun selectionBar_pickModes() {
        val selected = listOf(file("/d/a"))

        assertEquals(listOf(OPEN), selectionBarActions(selected, pickOptions(PickOptions.Mode.OPEN_FILE), dir))
        assertEquals(
            listOf(OPEN, SELECT_ALL),
            selectionBarActions(selected, pickOptions(PickOptions.Mode.OPEN_FILE, allowMultiple = true), dir),
        )
        assertEquals(listOf(CREATE), selectionBarActions(selected, pickOptions(PickOptions.Mode.CREATE_FILE), dir))
    }

    // Нижняя панель

    private fun pasteState(copy: Boolean, vararg files: FileItem): PasteState =
        PasteState(copy, fileItemSetOf(*files))

    @Test
    fun bottomBar_nothingToPaste_hidden() {
        assertNull(fileListBottomBar(null, PasteState(), dir))
    }

    @Test
    fun bottomBar_copy() {
        val bar = fileListBottomBar(null, pasteState(true, file("/e/a"), file("/e/b")), dir)!!

        assertEquals(FileListUiState.BottomBarTitle.Paste(R.string.file_list_paste_copy_title_format, 2), bar.title)
        assertEquals(FileListBottomBarAction.PASTE, bar.action)
        assertTrue(bar.isVisible && bar.isActionEnabled)
    }

    @Test
    fun bottomBar_cut() {
        val bar = fileListBottomBar(null, pasteState(false, file("/e/a")), dir)!!

        assertEquals(FileListUiState.BottomBarTitle.Paste(R.string.file_list_paste_move_title_format, 1), bar.title)
        assertEquals(FileListBottomBarAction.PASTE, bar.action)
    }

    @Test
    fun bottomBar_copyFromArchive_extracts() {
        val bar = fileListBottomBar(null, pasteState(true, fileItem(archivePath("/e/b.zip", "x"))), dir)!!

        assertEquals(FileListUiState.BottomBarTitle.Paste(R.string.file_list_paste_extract_title_format, 1), bar.title)
        assertEquals(FileListBottomBarAction.EXTRACT_HERE, bar.action)
    }

    @Test
    fun bottomBar_inArchive_pasteDisabled() {
        val bar = fileListBottomBar(null, pasteState(true, file("/e/a")), archiveDir)!!

        assertFalse(bar.isActionEnabled)
    }

    @Test
    fun bottomBar_pickModes() {
        val paste = pasteState(true, file("/e/a"))

        val create = fileListBottomBar(pickOptions(PickOptions.Mode.CREATE_FILE), paste, dir)!!
        val openDirectory = fileListBottomBar(pickOptions(PickOptions.Mode.OPEN_DIRECTORY), paste, dir)!!

        assertEquals(FileListUiState.BottomBarTitle.FileName, create.title)
        assertEquals(FileListBottomBarAction.CREATE, create.action)
        assertEquals(FileListUiState.BottomBarTitle.OpenDirectory(dir), openDirectory.title)
        assertEquals(FileListBottomBarAction.OPEN, openDirectory.action)
        assertNull(fileListBottomBar(pickOptions(PickOptions.Mode.OPEN_FILE), paste, dir))
    }
}
