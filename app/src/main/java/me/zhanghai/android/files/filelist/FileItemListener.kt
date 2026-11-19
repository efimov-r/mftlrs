/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 * Modified for mftlrs, 2026.
 */

package me.zhanghai.android.files.filelist

import me.zhanghai.android.files.file.FileItem

// mftlrs: was FileListAdapter.Listener; the adapter is replaced by FileListContent in Compose.
interface FileItemListener {
    fun clearSelectedFiles()
    fun selectFile(file: FileItem, selected: Boolean)
    fun selectFiles(files: FileItemSet, selected: Boolean)
    fun openFile(file: FileItem)
    fun openFileWith(file: FileItem)
    fun cutFile(file: FileItem)
    fun copyFile(file: FileItem)
    fun confirmDeleteFile(file: FileItem)
    fun showRenameFileDialog(file: FileItem)
    fun extractFile(file: FileItem)
    fun showCreateArchiveDialog(file: FileItem)
    fun shareFile(file: FileItem)
    fun copyPath(file: FileItem)
    fun addBookmark(file: FileItem)
    fun createShortcut(file: FileItem)
    fun showPropertiesDialog(file: FileItem)
}
