/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.file.isImage
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.util.create
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.createSendStreamIntent
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.extraPathList
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.withChooser
import me.zhanghai.android.files.viewer.image.ImageViewerActivity

// Интенты главного экрана — перенос из FileListFragment. Собирает их FileListActivity, выполняя события FileListViewModel

private const val IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX = 1000 // больше — TransactionTooLargeException

// Результат режима выбора для вызвавшего приложения: один путь — data, несколько — ClipData
internal fun Activity.finishWithPickResult(paths: LinkedHashSet<Path>, pickOptions: PickOptions) {
    val intent = Intent().apply {
        if (paths.size == 1) {
            val path = paths.single()
            data = path.fileProviderUri
            extraPath = path
        } else {
            val mimeTypes = pickOptions.mimeTypes.map { it.value }
            val items = paths.map { ClipData.Item(it.fileProviderUri) }
            clipData = ClipData::class.create(null, mimeTypes, items)
            extraPathList = paths.toList()
        }
        var flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        if (!pickOptions.readOnly) {
            flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }
        if (pickOptions.mode == PickOptions.Mode.OPEN_DIRECTORY) {
            flags = flags or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        }
        addFlags(flags)
    }
    setResult(Activity.RESULT_OK, intent)
    finish()
}

internal fun createOpenInNewTaskIntent(path: Path): Intent =
    FileListActivity.createViewIntent(path)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

// Открыть файл не из архива другим приложением; картинке — соседние картинки списка для просмотрщика
internal fun createViewFileIntent(file: FileItem, withChooser: Boolean, files: List<FileItem>): Intent {
    val path = file.path
    val mimeType = file.mimeType
    val intent = path.fileProviderUri.createViewIntent(mimeType)
        .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        .apply {
            extraPath = path
            putImageViewerExtras(this, path, mimeType, files)
        }
    if (!withChooser) return intent

    return intent.withChooser(
        EditFileActivity::class.createIntent()
            .putArgs(EditFileActivity.Args(path, mimeType)),
        OpenFileAsDialogActivity::class.createIntent()
            .putArgs(OpenFileAsDialogFragment.Args(path)),
    )
}

// Порядок картинок — как в списке на экране, а не в FileListLiveData
private fun putImageViewerExtras(intent: Intent, path: Path, mimeType: MimeType, files: List<FileItem>) {
    if (!mimeType.isImage) return

    var paths = files
        .filter { it.mimeType.isImage || it.path == path }
        .map { it.path }
    var position = paths.indexOf(path)
    if (position == -1) return

    if (paths.size > IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX) {
        val start = (position - IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX / 2)
            .coerceIn(0, paths.size - IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX)
        paths = paths.subList(start, start + IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX)
        position -= start
    }
    ImageViewerActivity.putExtras(intent, paths, position)
}

// Uri для установщика пакетов; null — ставить через FileJobService (файл в архиве, до N — не локальный файл)
internal fun Path.installPackageUriOrNull(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        if (!isArchivePath) fileProviderUri else null
    } else {
        if (isLinuxPath) Uri.fromFile(toFile()) else null // до N установщик понимает только file://
    }

internal fun createShareIntent(paths: List<Path>, mimeTypes: List<MimeType>): Intent =
    paths.map { it.fileProviderUri }
        .createSendStreamIntent(mimeTypes)
        .withChooser()

// Ярлык на рабочем столе: папка открывается в новой задаче, файл — через OpenFileActivity
internal fun createShortcutInfo(context: Context, path: Path, mimeType: MimeType): ShortcutInfoCompat {
    val isDirectory = mimeType == MimeType.DIRECTORY
    val intent = if (isDirectory) {
        FileListActivity.createViewIntent(path)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    } else {
        OpenFileActivity.createIntent(path, mimeType)
    }
    val iconRes = if (isDirectory) R.mipmap.directory_shortcut_icon else R.mipmap.file_shortcut_icon

    return ShortcutInfoCompat.Builder(context, path.toString())
        .setShortLabel(path.name)
        .setIntent(intent)
        .setIcon(IconCompat.createWithResource(context, iconRes))
        .build()
}
