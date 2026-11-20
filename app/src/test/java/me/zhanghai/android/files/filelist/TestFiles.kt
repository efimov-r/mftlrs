/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import java8.nio.file.attribute.FileTime
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.asMimeType
import me.zhanghai.android.files.provider.archive.createArchiveRootPath
import me.zhanghai.android.files.provider.linux.LinuxFileSystemProvider
import java.text.Collator
import java.util.Locale

// Файлы для unit-тестов: пути настоящие (LinuxPath, ArchivePath), но диск не читается.
// Провайдер не устанавливается в FileSystemProvider — пути создаются напрямую.

internal val ZIP: MimeType = "application/zip".asMimeType()

internal fun linuxPath(path: String): Path = LinuxFileSystemProvider.fileSystem.getPath(path)

// Путь внутри архива; entry — от корня архива, "" — сам корень
internal fun archivePath(archive: String, entry: String = ""): Path {
    val root = linuxPath(archive).createArchiveRootPath()
    return if (entry.isEmpty()) root else root.resolve(entry)
}

internal fun file(
    path: String,
    size: Long = 0,
    lastModified: Long = 0,
    isHidden: Boolean = false,
    mimeType: MimeType = MimeType.GENERIC,
): FileItem = fileItem(linuxPath(path), false, size, lastModified, isHidden, mimeType)

internal fun directory(path: String, isHidden: Boolean = false): FileItem =
    fileItem(linuxPath(path), true, 0, 0, isHidden, MimeType.DIRECTORY)

internal fun fileItem(
    path: Path,
    isDirectory: Boolean = false,
    size: Long = 0,
    lastModified: Long = 0,
    isHidden: Boolean = false,
    mimeType: MimeType = if (isDirectory) MimeType.DIRECTORY else MimeType.GENERIC,
): FileItem {
    val nameCollationKey = Collator.getInstance(Locale.ROOT).getCollationKeyForFileName(path.name)
    val attributes = TestAttributes(isDirectory, size, lastModified)
    return FileItem(path, nameCollationKey, attributes, null, null, isHidden, mimeType)
}

internal fun pickOptions(mode: PickOptions.Mode, allowMultiple: Boolean = false): PickOptions =
    PickOptions(mode, null, false, listOf(MimeType.ANY), false, allowMultiple)

private class TestAttributes(
    private val isDirectory: Boolean,
    private val size: Long,
    lastModified: Long,
) : BasicFileAttributes {
    private val time = FileTime.fromMillis(lastModified)

    override fun lastModifiedTime(): FileTime = time

    override fun lastAccessTime(): FileTime = time

    override fun creationTime(): FileTime = time

    override fun isRegularFile(): Boolean = !isDirectory

    override fun isDirectory(): Boolean = isDirectory

    override fun isSymbolicLink(): Boolean = false

    override fun isOther(): Boolean = false

    override fun size(): Long = size

    override fun fileKey(): Any? = null
}
