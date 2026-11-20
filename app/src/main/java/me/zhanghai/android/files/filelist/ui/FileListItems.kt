/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist.ui

import android.text.TextUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import me.zhanghai.android.files.R
import me.zhanghai.android.files.coil.AppIconPackageName
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.appDirectoryPackageName
import me.zhanghai.android.files.file.fileSize
import me.zhanghai.android.files.file.formatShort
import me.zhanghai.android.files.file.iconRes
import me.zhanghai.android.files.file.isApk
import me.zhanghai.android.files.file.isArchiveFile
import me.zhanghai.android.files.file.name
import me.zhanghai.android.files.file.supportsThumbnail
import me.zhanghai.android.files.filelist.data.PickOptions
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.common.isEncrypted
import me.zhanghai.android.files.ui.theme.MftlrsTheme

// Элементы списка и сетки главного экрана — перенос удалённых file_item_list.xml, file_item_grid.xml и
// FileListAdapter; ripple, выделение и приглушение упрощены (docs/ai/compose-migration.md, «Отклонения»)

private const val DISABLED_ALPHA = 0.38f // android:disabledAlpha темы Material 3
private const val CHECKED_ALPHA = 0.12f // CheckableItemBackground: colorPrimary с alpha 0,12
private const val GRID_THUMBNAIL_ASPECT_RATIO = 1.78f
private const val OUTLINE_ALPHA = 0.12f // material_on_surface_stroke

@Composable
internal fun FileListItem(
    file: FileItem,
    isChecked: Boolean,
    hasSelection: Boolean,
    pickOptions: PickOptions?,
    nameEllipsize: TextUtils.TruncateAt,
    listener: FileItemListener,
    modifier: Modifier = Modifier,
) {
    val ui = rememberFileItemUi(file)
    val actions = rememberFileItemActions(file, isChecked, hasSelection, pickOptions, listener)

    FileListItemContent(
        ui = ui,
        isChecked = isChecked,
        isEnabled = actions.isEnabled,
        nameEllipsize = nameEllipsize,
        onClick = actions.onClick,
        onLongClick = actions.onLongClick,
        onIconClick = actions.onIconClick,
        menuItems = actions.menuItems,
        modifier = modifier,
    )
}

@Composable
internal fun FileGridItem(
    file: FileItem,
    isChecked: Boolean,
    hasSelection: Boolean,
    pickOptions: PickOptions?,
    listener: FileItemListener,
    modifier: Modifier = Modifier,
) {
    val ui = rememberFileItemUi(file)
    val actions = rememberFileItemActions(file, isChecked, hasSelection, pickOptions, listener)

    FileGridItemContent(
        ui = ui,
        isChecked = isChecked,
        isEnabled = actions.isEnabled,
        onClick = actions.onClick,
        onLongClick = actions.onLongClick,
        onIconClick = actions.onIconClick,
        menuItems = actions.menuItems,
        modifier = modifier,
    )
}

// Можно ли выбрать файл в режиме выбора (из удалённого FileListAdapter)
internal fun isFileSelectable(file: FileItem, pickOptions: PickOptions?): Boolean {
    pickOptions ?: return true
    return when (pickOptions.mode) {
        PickOptions.Mode.OPEN_FILE, PickOptions.Mode.CREATE_FILE ->
            !file.attributes.isDirectory && pickOptions.mimeTypes.any { it.match(file.mimeType) }
        PickOptions.Mode.OPEN_DIRECTORY -> file.attributes.isDirectory
    }
}

// Переключить выделение; при !allowMultiple сначала снять прежнее (из удалённого FileListAdapter)
private fun selectFile(
    file: FileItem,
    isChecked: Boolean,
    pickOptions: PickOptions?,
    listener: FileItemListener,
) {
    if (!isFileSelectable(file, pickOptions)) return
    if (!isChecked && pickOptions != null && !pickOptions.allowMultiple) {
        listener.clearSelectedFiles()
    }
    listener.selectFile(file, !isChecked)
}

// Что показать; без Path и атрибутов, чтобы превью строились без файловой системы
private data class FileItemUi(
    val name: String,
    val description: String?, // у папки нет
    val isDirectory: Boolean,
    @DrawableRes val iconRes: Int,
    val thumbnail: Any?, // модель Coil: Pair<Path, BasicFileAttributes>
    val isApk: Boolean, // в сетке миниатюра APK — значок приложения по центру, не на всё превью
    val appIconPackageName: AppIconPackageName?,
    @DrawableRes val badgeRes: Int?,
)

private class FileItemMenuItem(
    @StringRes val titleRes: Int,
    val onClick: () -> Unit,
)

private class FileItemActions(
    val isEnabled: Boolean,
    val onClick: () -> Unit,
    val onLongClick: () -> Unit,
    val onIconClick: () -> Unit,
    val menuItems: List<FileItemMenuItem>,
)

@Composable
private fun rememberFileItemUi(file: FileItem): FileItemUi {
    val context = LocalContext.current
    val separator = stringResource(R.string.file_item_description_separator) // не через context: lint, смена конфигурации
    return remember(file, separator) {
        val attributes = file.attributes
        val isDirectory = attributes.isDirectory
        val description = if (isDirectory) {
            null
        } else {
            val lastModificationTime = attributes.lastModifiedTime().toInstant().formatShort(context)
            val size = attributes.fileSize.formatHumanReadable(context)
            listOf(lastModificationTime, size).joinToString(separator)
        }
        val badgeRes = if (file.attributesNoFollowLinks.isSymbolicLink) {
            if (file.isSymbolicLinkBroken) {
                R.drawable.error_badge_icon_18dp
            } else {
                R.drawable.symbolic_link_badge_icon_18dp
            }
        } else if (file.attributesNoFollowLinks.isEncrypted()) {
            R.drawable.encrypted_badge_icon_18dp
        } else {
            null
        }

        FileItemUi(
            name = file.name,
            description = description,
            isDirectory = isDirectory,
            iconRes = file.mimeType.iconRes,
            thumbnail = if (file.supportsThumbnail) file.path to attributes else null,
            isApk = file.mimeType.isApk,
            appIconPackageName = file.appDirectoryPackageName?.let { AppIconPackageName(it) },
            badgeRes = badgeRes,
        )
    }
}

@Composable
private fun rememberFileItemActions(
    file: FileItem,
    isChecked: Boolean,
    hasSelection: Boolean,
    pickOptions: PickOptions?,
    listener: FileItemListener,
): FileItemActions = remember(file, isChecked, hasSelection, pickOptions, listener) {
    val select = { selectFile(file, isChecked, pickOptions, listener) }
    val open = { listener.openFile(file) }

    FileItemActions(
        isEnabled = isFileSelectable(file, pickOptions) || file.attributes.isDirectory,
        onClick = if (hasSelection) select else open,
        onLongClick = if (hasSelection) open else select,
        onIconClick = select,
        menuItems = fileItemMenuItems(file, pickOptions != null, listener),
    )
}

// Пункты меню ⋮ и условия их видимости (из удалённых menu/file_item.xml и FileListAdapter)
private fun fileItemMenuItems(
    file: FileItem,
    hasPickOptions: Boolean,
    listener: FileItemListener,
): List<FileItemMenuItem> {
    val path = file.path
    val isReadOnly = path.fileSystem.isReadOnly
    val isArchivePath = path.isArchivePath

    return buildList {
        add(FileItemMenuItem(R.string.file_item_action_open_with) { listener.openFileWith(file) })
        if (!hasPickOptions && !isReadOnly) {
            add(FileItemMenuItem(R.string.cut) { listener.cutFile(file) })
        }
        if (!hasPickOptions) {
            val titleRes = if (isArchivePath) R.string.file_item_action_extract else R.string.copy
            add(FileItemMenuItem(titleRes) { listener.copyFile(file) })
        }
        if (!isReadOnly) {
            add(FileItemMenuItem(R.string.delete) { listener.confirmDeleteFile(file) })
            add(FileItemMenuItem(R.string.rename) { listener.showRenameFileDialog(file) })
        }
        if (file.isArchiveFile) {
            add(FileItemMenuItem(R.string.file_item_action_extract) { listener.extractFile(file) })
        }
        if (!isArchivePath) {
            add(FileItemMenuItem(R.string.file_item_action_archive) { listener.showCreateArchiveDialog(file) })
        }
        add(FileItemMenuItem(R.string.share) { listener.shareFile(file) })
        add(FileItemMenuItem(R.string.file_item_action_copy_path) { listener.copyPath(file) })
        if (file.attributes.isDirectory) {
            add(FileItemMenuItem(R.string.file_item_action_add_bookmark) { listener.addBookmark(file) })
        }
        add(FileItemMenuItem(R.string.file_item_action_create_shortcut) { listener.createShortcut(file) })
        add(FileItemMenuItem(R.string.file_item_action_properties) { listener.showPropertiesDialog(file) })
    }
}

// Строка списка
@Composable
private fun FileListItemContent(
    ui: FileItemUi,
    isChecked: Boolean,
    isEnabled: Boolean,
    nameEllipsize: TextUtils.TruncateAt,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onIconClick: () -> Unit,
    menuItems: List<FileItemMenuItem>,
    modifier: Modifier = Modifier,
) {
    var isThumbnailLoaded by remember(ui.thumbnail) { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fileItemClickable(isChecked, isEnabled, onClick, onLongClick, MaterialTheme.colorScheme.primary)
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.two_line_list_item_height)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileItemIcon(
            ui = ui,
            isChecked = isChecked,
            isEnabled = isEnabled,
            onClick = onIconClick,
            modifier = Modifier.padding(
                start = dimensionResource(R.dimen.screen_edge_margin_minus_4dp),
                end = dimensionResource(R.dimen.content_start_from_screen_edge_margin_minus_44dp),
            ),
            isIconVisible = !isThumbnailLoaded, // миниатюра загрузилась — значок типа скрыт
        ) {
            if (ui.thumbnail != null) {
                AsyncImage(
                    model = ui.thumbnail,
                    contentDescription = null,
                    imageLoader = LocalContext.current.imageLoader,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    onSuccess = { isThumbnailLoaded = true },
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            FileItemName(
                name = ui.name,
                ellipsize = nameEllipsize,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (ui.description != null) {
                Text(
                    text = ui.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        FileItemMenuButton(
            isEnabled = isEnabled,
            menuItems = menuItems,
            modifier = Modifier.padding(
                start = dimensionResource(
                    R.dimen.content_start_from_screen_edge_margin_minus_36dp_considering_at_least_8dp
                ),
                end = dimensionResource(R.dimen.screen_edge_margin_minus_12dp_at_least_8dp),
            ),
        )
    }
}

// Плитка сетки
@Composable
private fun FileGridItemContent(
    ui: FileItemUi,
    isChecked: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onIconClick: () -> Unit,
    menuItems: List<FileItemMenuItem>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fileItemClickable(isChecked, isEnabled, onClick, onLongClick, MaterialTheme.colorScheme.primary)
            .fillMaxWidth(),
    ) {
        val screenEdgeMargin = dimensionResource(R.dimen.screen_edge_margin)
        FileGridThumbnail(
            ui = ui,
            modifier = Modifier
                .padding(start = screenEdgeMargin, top = screenEdgeMargin, end = screenEdgeMargin)
                .fillMaxWidth()
                .aspectRatio(GRID_THUMBNAIL_ASPECT_RATIO),
        )

        Row(
            modifier = Modifier
                .padding(bottom = dimensionResource(R.dimen.screen_edge_margin_minus_8dp))
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.single_line_list_item_height)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val edgeMargin = dimensionResource(R.dimen.screen_edge_margin_minus_12dp)
            FileItemIcon(
                ui = ui,
                isChecked = isChecked,
                isEnabled = isEnabled,
                onClick = onIconClick,
                modifier = Modifier.padding(start = edgeMargin),
            )

            Text(
                text = ui.name,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )

            FileItemMenuButton(
                isEnabled = isEnabled,
                menuItems = menuItems,
                modifier = Modifier.padding(end = edgeMargin),
            )
        }
    }
}

// Клик и долгое нажатие по всему элементу, фон выделения; недоступный элемент приглушён целиком
private fun Modifier.fileItemClickable(
    isChecked: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    primaryColor: Color,
): Modifier =
    this
        .alpha(if (isEnabled) 1f else DISABLED_ALPHA)
        .background(if (isChecked) primaryColor.copy(alpha = CHECKED_ALPHA) else Color.Transparent)
        .combinedClickable(
            enabled = isEnabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )

// Превью 1,78 : 1 — значок папки, миниатюра с контуром или значок типа по центру
@Composable
private fun FileGridThumbnail(
    ui: FileItemUi,
    modifier: Modifier = Modifier,
) {
    var isThumbnailLoaded by remember(ui.thumbnail) { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp) // shapeAppearanceSmallComponent Material 3

    if (ui.isDirectory) {
        Image(
            painter = painterResource(R.drawable.file_directory_thumbnail),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
        return
    }

    Box(modifier = modifier) {
        val largeIconSize = dimensionResource(R.dimen.large_icon_size)
        val iconModifier = Modifier
            .align(Alignment.Center)
            .size(largeIconSize)
        if (ui.isApk && ui.thumbnail != null) {
            val iconPainter = painterResource(ui.iconRes)
            AsyncImage(
                model = ui.thumbnail,
                contentDescription = null,
                imageLoader = LocalContext.current.imageLoader,
                modifier = iconModifier,
                placeholder = iconPainter,
                error = iconPainter,
                contentScale = ContentScale.Crop,
            )
        } else {
            if (!isThumbnailLoaded) {
                Image(
                    painter = painterResource(ui.iconRes),
                    contentDescription = null,
                    modifier = iconModifier,
                    contentScale = ContentScale.Crop,
                )
            }
            if (ui.thumbnail != null) {
                AsyncImage(
                    model = ui.thumbnail,
                    contentDescription = null,
                    imageLoader = LocalContext.current.imageLoader,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape),
                    contentScale = ContentScale.Crop,
                    onSuccess = { isThumbnailLoaded = true },
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = OUTLINE_ALPHA),
                    shape = shape,
                )
        )
    }
}

// Иконка 48 dp: значок типа, миниатюра (extra), значок приложения, бейдж ссылки или шифрования,
// галочка выделения. Тап — переключить выделение, без ripple (как iconLayout)
@Composable
private fun FileItemIcon(
    ui: FileItemUi,
    isChecked: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isIconVisible: Boolean = true,
    thumbnail: @Composable BoxScope.() -> Unit = {},
) {
    val badgeSize = dimensionResource(R.dimen.badge_size)

    Box(
        modifier = modifier
            .testTag("iconLayout") // контракт UI-тестов: тап по иконке
            .size(dimensionResource(R.dimen.touch_target_size))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = isEnabled,
                onClick = onClick,
            )
            .padding(dimensionResource(R.dimen.touch_target_large_icon_padding)),
    ) {
        if (isIconVisible) {
            Image(
                painter = painterResource(ui.iconRes),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(dimensionResource(R.dimen.icon_size)),
                contentScale = ContentScale.Crop,
            )
        }
        thumbnail()

        if (ui.appIconPackageName != null) {
            AsyncImage(
                model = ui.appIconPackageName,
                contentDescription = null,
                imageLoader = LocalContext.current.imageLoader,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 0.5.dp, y = 0.5.dp)
                    .size(dimensionResource(R.dimen.badge_size_plus_1dp)),
            )
        }
        if (ui.badgeRes != null) {
            Image(
                painter = painterResource(ui.badgeRes),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(badgeSize)
                    .background(MaterialTheme.colorScheme.surface, CircleShape), // badge_background_18dp
            )
        }
        if (isChecked) {
            Image(
                painter = painterResource(R.drawable.checkable_badge_icon_18dp),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(badgeSize),
            )
        }
    }
}

// Имя в одну строку с обрезкой по настройке FILE_NAME_ELLIPSIZE; MARQUEE — бегущая строка
@Composable
private fun FileItemName(
    name: String,
    ellipsize: TextUtils.TruncateAt,
    style: TextStyle,
) {
    val isMarquee = ellipsize == TextUtils.TruncateAt.MARQUEE
    val overflow = when (ellipsize) {
        TextUtils.TruncateAt.START -> TextOverflow.StartEllipsis
        TextUtils.TruncateAt.MIDDLE -> TextOverflow.MiddleEllipsis
        TextUtils.TruncateAt.END -> TextOverflow.Ellipsis
        else -> TextOverflow.Clip
    }

    Text(
        text = name,
        modifier = if (isMarquee) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
        overflow = overflow,
        softWrap = false,
        maxLines = 1,
        style = style,
    )
}

// Кнопка ⋮ с выпадающим меню; testTag — контракт UI-тестов (docs/ai/testing.md)
@Composable
private fun FileItemMenuButton(
    isEnabled: Boolean,
    menuItems: List<FileItemMenuItem>,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        val color = MaterialTheme.colorScheme.onSurfaceVariant // ?colorControlNormal
        IconButton(
            onClick = { isExpanded = true },
            modifier = Modifier.testTag("menuButton"),
            enabled = isEnabled,
            colors = IconButtonDefaults.iconButtonColors( // недоступный элемент уже приглушён целиком
                contentColor = color,
                disabledContentColor = color,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.more_vertical_icon_white_24dp),
                contentDescription = null,
            )
        }

        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            menuItems.forEach { item ->
                DropdownMenuItem(
                    text = { Text(stringResource(item.titleRes)) },
                    onClick = {
                        isExpanded = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

private val previewFile = FileItemUi(
    name = "Отчёт за ноябрь с длинным названием файла.pdf",
    description = "18 нояб. · 2,4 МБ",
    isDirectory = false,
    iconRes = R.drawable.file_pdf_icon,
    thumbnail = null,
    isApk = false,
    appIconPackageName = null,
    badgeRes = null,
)

private val previewDirectory = FileItemUi(
    name = "Download",
    description = null,
    isDirectory = true,
    iconRes = R.drawable.file_directory_icon,
    thumbnail = null,
    isApk = false,
    appIconPackageName = null,
    badgeRes = R.drawable.symbolic_link_badge_icon_18dp,
)

private val previewMenuItems = listOf(
    FileItemMenuItem(R.string.file_item_action_open_with) {},
    FileItemMenuItem(R.string.copy) {},
)

@Composable
private fun PreviewListItems() {
    Surface {
        Column {
            FileListItemContent(
                ui = previewDirectory,
                isChecked = false,
                isEnabled = true,
                nameEllipsize = TextUtils.TruncateAt.END,
                onClick = {},
                onLongClick = {},
                onIconClick = {},
                menuItems = previewMenuItems,
            )
            FileListItemContent(
                ui = previewFile,
                isChecked = true,
                isEnabled = true,
                nameEllipsize = TextUtils.TruncateAt.MIDDLE,
                onClick = {},
                onLongClick = {},
                onIconClick = {},
                menuItems = previewMenuItems,
            )
            FileListItemContent(
                ui = previewFile,
                isChecked = false,
                isEnabled = false,
                nameEllipsize = TextUtils.TruncateAt.END,
                onClick = {},
                onLongClick = {},
                onIconClick = {},
                menuItems = previewMenuItems,
            )
        }
    }
}

@Composable
private fun PreviewGridItems() {
    Surface {
        Row {
            FileGridItemContent(
                ui = previewDirectory,
                isChecked = false,
                isEnabled = true,
                onClick = {},
                onLongClick = {},
                onIconClick = {},
                menuItems = previewMenuItems,
                modifier = Modifier.width(180.dp),
            )
            FileGridItemContent(
                ui = previewFile,
                isChecked = true,
                isEnabled = true,
                onClick = {},
                onLongClick = {},
                onIconClick = {},
                menuItems = previewMenuItems,
                modifier = Modifier.width(180.dp),
            )
        }
    }
}

@Preview(name = "Список, светлая")
@Composable
private fun FileListItemLightPreview() {
    MftlrsTheme(darkTheme = false) { PreviewListItems() }
}

@Preview(name = "Список, тёмная")
@Composable
private fun FileListItemDarkPreview() {
    MftlrsTheme(darkTheme = true) { PreviewListItems() }
}

@Preview(name = "Сетка, светлая")
@Composable
private fun FileGridItemLightPreview() {
    MftlrsTheme(darkTheme = false) { PreviewGridItems() }
}

@Preview(name = "Сетка, тёмная")
@Composable
private fun FileGridItemDarkPreview() {
    MftlrsTheme(darkTheme = true) { PreviewGridItems() }
}
