/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.fileproperties.basic

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java8.nio.file.FileVisitResult
import java8.nio.file.FileVisitor
import java8.nio.file.Files
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.asFileSize
import me.zhanghai.android.files.file.fileSize
import me.zhanghai.android.files.file.formatLong
import me.zhanghai.android.files.file.getMimeTypeName
import me.zhanghai.android.files.file.name
import me.zhanghai.android.files.file.toUserFriendlyString
import me.zhanghai.android.files.fileproperties.FilePropertiesFileViewModel
import me.zhanghai.android.files.fileproperties.FilePropertiesTab
import me.zhanghai.android.files.fileproperties.FilePropertyItem
import me.zhanghai.android.files.provider.archive.ArchiveFileAttributes
import me.zhanghai.android.files.provider.archive.archiveFile
import me.zhanghai.android.files.provider.archive.isArchivePath
import java.io.IOException

// Вкладка «Основное», была FilePropertiesBasicTabFragment. У папки — число файлов и размер,
// обновляются по ходу обхода
@Composable
internal fun FilePropertiesBasicTab(viewModel: FilePropertiesFileViewModel) {
    val stateful by viewModel.fileLiveData.observeAsState()
    val context = LocalContext.current

    FilePropertiesTab(stateful, onRefresh = viewModel::reload) { file ->
        FilePropertyItem(stringResource(R.string.file_properties_basic_name), file.name)
        val path = file.path
        if (path.isArchivePath) {
            FilePropertyItem(
                stringResource(R.string.file_properties_basic_archive_file),
                path.archiveFile.toUserFriendlyString(),
            )
            val attributes = file.attributes as ArchiveFileAttributes
            FilePropertyItem(stringResource(R.string.file_properties_basic_archive_entry), attributes.entryName())
        } else {
            path.parent?.let {
                FilePropertyItem(stringResource(R.string.file_properties_basic_parent_directory), it.toString())
            }
        }
        FilePropertyItem(stringResource(R.string.file_properties_basic_type), typeText(file))
        file.symbolicLinkTarget?.let {
            FilePropertyItem(stringResource(R.string.file_properties_basic_symbolic_link_target), it)
        }
        if (file.attributes.isDirectory) {
            val contents by produceState(DirectoryContents(0, 0), file) {
                directoryContents(path).collect { value = it }
            }
            FilePropertyItem(stringResource(R.string.file_properties_basic_contents), directoryContentsText(contents))
        } else {
            FilePropertyItem(stringResource(R.string.file_properties_basic_size), sizeText(file, context))
        }
        FilePropertyItem(
            stringResource(R.string.file_properties_basic_last_modification_time),
            file.attributes.lastModifiedTime().toInstant().formatLong(),
        )
    }
}

@Composable
private fun typeText(file: FileItem): String {
    val formatRes = if (file.attributesNoFollowLinks.isSymbolicLink && !file.isSymbolicLinkBroken) {
        R.string.file_properties_basic_type_symbolic_link_format
    } else {
        R.string.file_properties_basic_type_format
    }
    return stringResource(formatRes, file.getMimeTypeName(LocalContext.current), file.mimeType.value)
}

@Composable
private fun directoryContentsText(contents: DirectoryContents): String {
    if (contents.count == 0) return stringResource(R.string.empty)
    val context = LocalContext.current
    val size = contents.size.asFileSize()
    val sizeText = if (size.isHumanReadableInBytes) size.formatInBytes(context) else size.formatHumanReadable(context)
    return pluralStringResource(R.plurals.file_properties_basic_contents_format, contents.count, contents.count, sizeText)
}

@Composable
private fun sizeText(file: FileItem, context: Context): String {
    val size = file.attributes.fileSize
    val sizeInBytes = size.formatInBytes(context)
    if (size.isHumanReadableInBytes) return sizeInBytes
    return stringResource(
        R.string.file_properties_basic_size_with_human_readable_format,
        size.formatHumanReadable(context),
        sizeInBytes,
    )
}

private data class DirectoryContents(val count: Int, val size: Long)

// Обход папки в фоне; промежуточный итог — не чаще раза в 200 мс, в конце — окончательный
private fun directoryContents(directory: Path): Flow<DirectoryContents> =
    channelFlow {
        var count = 0
        var size = 0L
        var lastTimeMillis = System.currentTimeMillis()
        Files.walkFileTree(directory, object : FileVisitor<Path> {
            override fun preVisitDirectory(directory: Path, attributes: BasicFileAttributes): FileVisitResult =
                visit(directory, attributes, null)

            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult =
                visit(file, attributes, null)

            override fun visitFileFailed(file: Path, exception: IOException): FileVisitResult =
                visit(file, null, exception)

            override fun postVisitDirectory(directory: Path, exception: IOException?): FileVisitResult =
                visit(null, null, exception)

            private fun visit(path: Path?, attributes: BasicFileAttributes?, exception: IOException?): FileVisitResult {
                if (!isActive) return FileVisitResult.TERMINATE
                if (path == directory) return FileVisitResult.CONTINUE
                path?.let { ++count }
                attributes?.let { size += it.size() }
                exception?.printStackTrace()
                val currentTimeMillis = System.currentTimeMillis()
                if (currentTimeMillis >= lastTimeMillis + INTERVAL_MILLIS) {
                    trySend(DirectoryContents(count, size))
                    lastTimeMillis = currentTimeMillis
                }
                return FileVisitResult.CONTINUE
            }
        })
        send(DirectoryContents(count, size))
    }
        .buffer(Channel.CONFLATED)
        .catch { it.printStackTrace() }
        .flowOn(Dispatchers.IO)

private const val INTERVAL_MILLIS = 200L
