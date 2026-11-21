/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist.ui

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.Insets
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.navigation.NavigationItem
import me.zhanghai.android.files.ui.compose.MftlrsBottomSheet
import me.zhanghai.android.files.util.getDimensionPixelSizeByAttr

// Места главного экрана (хранилища, папки, закладки, пункты): в узком окне — нижняя панель на всю высоту
// экрана, её открывает FAB; в широком — панель слева, видна всегда. Размеры строк сняты со старой боковой панели
// (Widget.MaterialFiles.Material3.NavigationView)

private const val PERSISTENT_MIN_WIDTH_DP = 600

private val VERTICAL_PADDING = 8.dp // design_navigation_padding_bottom
private val ITEM_HEIGHT = 48.dp // ?listPreferredItemHeightSmall
private val ITEM_INSET = 12.dp // отступ «таблетки» от краёв панели; иконка и текст — у её краёв
private val ICON_SIZE = 24.dp
private val ICON_PADDING = 12.dp // от иконки до текста
private val DIVIDER_INSET = 28.dp // dividerInsetStart/End
private val DIVIDER_VERTICAL_PADDING = 8.dp // design_navigation_separator_vertical_padding
private val SUBTITLE_TEXT_SIZE = 10.sp // itemSubtitleTextSize
private val SUBTITLE_LINE_HEIGHT = 14.sp // у TextView высота строки шла от шрифта 10sp, а не от bodyMedium

// Панель слева вместо нижней: телефон в ландшафте, планшет, раскладной
internal val Configuration.isPersistentNavigation: Boolean
    get() = screenWidthDp >= PERSISTENT_MIN_WIDTH_DP

@Composable
internal fun FileListNavigation(
    isOpen: Boolean, // нижняя панель; хранит FileListViewModel
    windowInsets: Insets, // px, как у FileListScreen
    items: List<NavigationItem?>,
    currentPath: Path?, // от него зависит подсветка пунктов
    listener: NavigationItem.Listener,
    onOpenChange: (Boolean) -> Unit,
    content: @Composable (windowInsets: Insets) -> Unit,
) {
    val checked = remember(items, currentPath) { items.map { it?.isChecked(listener) == true } }
    val list = @Composable { modifier: Modifier, contentPadding: PaddingValues ->
        NavigationList(
            items = items,
            checked = checked,
            contentPadding = contentPadding,
            listener = listener,
            modifier = modifier,
        )
    }

    if (LocalConfiguration.current.isPersistentNavigation) {
        PersistentNavigation(
            windowInsets = windowInsets,
            list = list,
            content = content,
        )
    } else {
        content(windowInsets)
        MftlrsBottomSheet(
            visible = isOpen,
            onDismissRequest = { onOpenChange(false) },
            windowInsets = windowInsets,
            isFullHeight = true,
        ) {
            list(Modifier.weight(1f), PaddingValues(bottom = VERTICAL_PADDING))
        }
    }
}

// Панель слева во всю высоту, забирает себе левый отступ и отступы сверху и снизу, как PersistentDrawerLayout.
// От экрана отделена вертикальной чертой
@Composable
private fun PersistentNavigation(
    windowInsets: Insets,
    list: @Composable (modifier: Modifier, contentPadding: PaddingValues) -> Unit,
    content: @Composable (windowInsets: Insets) -> Unit,
) {
    val density = LocalDensity.current
    val (left, top, bottom) = with(density) {
        listOf(windowInsets.left.toDp(), windowInsets.top.toDp(), windowInsets.bottom.toDp())
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = sheetWidth(maxWidth, left)
        Row(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .width(width)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                list(
                    Modifier,
                    PaddingValues(start = left, top = VERTICAL_PADDING + top, bottom = VERTICAL_PADDING + bottom),
                )
            }
            VerticalDivider() // 1 dp, outlineVariant
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                content(Insets.of(0, windowInsets.top, windowInsets.right, windowInsets.bottom))
            }
        }
    }
}

// NavigationRecyclerView.onMeasure: ширина экрана минус ?actionBarSize, но не больше navigation_max_width
@Composable
private fun sheetWidth(maxWidth: Dp, insetLeft: Dp): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    val actionBarSize = remember(context, density) {
        with(density) { context.getDimensionPixelSizeByAttr(androidx.appcompat.R.attr.actionBarSize).toDp() }
    }
    return (maxWidth - actionBarSize).coerceIn(0.dp, insetLeft + dimensionResource(R.dimen.navigation_max_width))
}

// Список пунктов; разделители — null в списке
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NavigationList(
    items: List<NavigationItem?>,
    checked: List<Boolean>,
    contentPadding: PaddingValues,
    listener: NavigationItem.Listener,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .semantics { testTagsAsResourceId = true } // нижняя панель — в своём окне, вне FileListScreen
            .testTag("navigation"), // контейнер навигации для UI-тестов
        contentPadding = contentPadding,
    ) {
        itemsIndexed(items) { index, item ->
            if (item != null) {
                NavigationRow(item, checked[index], listener)
            } else {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = DIVIDER_INSET, vertical = DIVIDER_VERTICAL_PADDING),
                )
            }
        }
    }
}

// Пункт: иконка и заголовок с подзаголовком в «таблетке», выбранный — на secondaryContainer
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NavigationRow(
    item: NavigationItem,
    isChecked: Boolean,
    listener: NavigationItem.Listener,
) {
    val context = LocalContext.current
    val title = remember(item) { item.getTitle(context) }
    val subtitle = remember(item) { item.getSubtitle(context) } // свободное место хранилища — считается здесь
    val colorScheme = MaterialTheme.colorScheme
    val contentColor = if (isChecked) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ITEM_HEIGHT)
            .padding(horizontal = ITEM_INSET)
            .clip(CircleShape)
            .background(if (isChecked) colorScheme.secondaryContainer else Color.Transparent)
            .semantics { selected = isChecked }
            .combinedClickable(
                // Без своего долгого нажатия View выполнял обычное нажатие
                onLongClick = { if (!item.onLongClick(listener)) item.onClick(listener) },
                onClick = { item.onClick(listener) },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(item.iconRes!!),
            contentDescription = null,
            modifier = Modifier.size(ICON_SIZE),
            tint = contentColor,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = ICON_PADDING),
        ) {
            Text(
                text = title,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = if (isChecked) colorScheme.onSecondaryContainer else colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = SUBTITLE_TEXT_SIZE,
                        lineHeight = SUBTITLE_LINE_HEIGHT,
                    ),
                )
            }
        }
    }
}
