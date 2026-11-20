/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.Insets
import java8.nio.file.Path
import kotlin.math.roundToInt
import me.zhanghai.android.files.R
import me.zhanghai.android.files.navigation.NavigationItem
import me.zhanghai.android.files.util.getDimensionPixelSizeByAttr

// Боковая панель главного экрана — перенос NavigationFragment (NavigationRecyclerView, NavigationListAdapter):
// на телефоне выезжает поверх экрана, на планшете в ландшафте стоит рядом с ним (было PersistentDrawerLayout).
// Размеры сняты с Widget.MaterialFiles.Material3.NavigationView на устройстве

private val VERTICAL_PADDING = 8.dp // design_navigation_padding_bottom
private val ITEM_HEIGHT = 48.dp // ?listPreferredItemHeightSmall
private val ITEM_INSET = 12.dp // отступ «таблетки» от краёв панели; иконка и текст — у её краёв
private val ICON_SIZE = 24.dp
private val ICON_PADDING = 12.dp // от иконки до текста
private val DIVIDER_INSET = 28.dp // dividerInsetStart/End
private val DIVIDER_VERTICAL_PADDING = 8.dp // design_navigation_separator_vertical_padding
private val MODAL_ELEVATION = 10.dp // высота панели в DrawerLayout: оттенок поверхности, как у NavigationFrameLayout
private val SUBTITLE_TEXT_SIZE = 10.sp // itemSubtitleTextSize
private val SUBTITLE_LINE_HEIGHT = 14.sp // у TextView высота строки шла от шрифта 10sp, а не от bodyMedium
private val SCRIM_COLOR = Color(0x99000000) // DrawerLayout.DEFAULT_SCRIM_COLOR

// Панель рядом с экраном вместо выезжающей — как раньше по ресурсам layout-sw600dp-land
internal val Configuration.isPersistentNavigation: Boolean
    get() = smallestScreenWidthDp >= 600 && orientation == Configuration.ORIENTATION_LANDSCAPE

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FileListNavigation(
    isOpen: Boolean, // выезжающая панель; хранит FileListViewModel, жесты сообщают через onOpenChange
    isPersistentOpen: Boolean, // Settings.FILE_LIST_PERSISTENT_DRAWER_OPEN, только для панели рядом с экраном
    windowInsets: Insets, // px, как у FileListScreen
    items: List<NavigationItem?>,
    currentPath: Path?, // от него зависит подсветка пунктов
    listener: NavigationItem.Listener,
    onOpenChange: (Boolean) -> Unit,
    content: @Composable (windowInsets: Insets) -> Unit,
) {
    val isPersistent = LocalConfiguration.current.isPersistentNavigation
    val checked = remember(items, currentPath) { items.map { it?.isChecked(listener) == true } }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }, // testTag виден UI-тестам как id; панель — вне FileListScreen
    ) {
        val sheetWidth = sheetWidth(maxWidth, windowInsets.left)
        val sheet = @Composable { elevation: Dp, overshoot: () -> Float ->
            NavigationSheet(
                items = items,
                checked = checked,
                windowInsets = windowInsets,
                width = sheetWidth,
                elevation = elevation,
                overshoot = overshoot,
                listener = listener,
            )
        }
        if (isPersistent) {
            PersistentNavigation(
                isOpen = isPersistentOpen,
                windowInsets = windowInsets,
                sheet = { sheet(0.dp) { 0f } },
                content = content,
            )
        } else {
            ModalNavigation(
                isOpen = isOpen,
                onOpenChange = onOpenChange,
                sheet = { overshoot -> sheet(MODAL_ELEVATION, overshoot) },
                content = { content(windowInsets) },
            )
        }
    }
}

// NavigationRecyclerView.onMeasure: ширина экрана минус ?actionBarSize, но не больше navigation_max_width
@Composable
private fun sheetWidth(maxWidth: Dp, insetLeftPx: Int): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    val actionBarSize = remember(context, density) {
        with(density) { context.getDimensionPixelSizeByAttr(androidx.appcompat.R.attr.actionBarSize).toDp() }
    }
    val insetLeft = with(density) { insetLeftPx.toDp() }
    return (maxWidth - actionBarSize).coerceIn(0.dp, insetLeft + dimensionResource(R.dimen.navigation_max_width))
}

@Composable
private fun ModalNavigation(
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    sheet: @Composable (overshoot: () -> Float) -> Unit,
    content: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(if (isOpen) DrawerValue.Open else DrawerValue.Closed)
    val currentOnOpenChange by rememberUpdatedState(onOpenChange)

    // Фрагмент открывает и закрывает панель через isOpen, жесты — через drawerState; оба сводятся к isOpen
    LaunchedEffect(isOpen) {
        try {
            if (isOpen) drawerState.open() else drawerState.close()
        } finally {
            currentOnOpenChange(drawerState.currentValue == DrawerValue.Open) // анимацию мог прервать жест
        }
    }
    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.currentValue == DrawerValue.Open }
            .collect { currentOnOpenChange(it) }
    }

    // Свайп открывает панель с любого места экрана, как FullDraggableContainer; «Назад» — колбэк во фрагменте
    // После резкого свайпа пружина проносит панель дальше открытого положения: offset > 0
    ModalNavigationDrawer(
        drawerContent = { sheet { drawerState.currentOffset } },
        drawerState = drawerState,
        scrimColor = SCRIM_COLOR,
        content = content,
    )
}

@Composable
private fun PersistentNavigation(
    isOpen: Boolean,
    windowInsets: Insets,
    sheet: @Composable () -> Unit,
    content: @Composable (windowInsets: Insets) -> Unit,
) {
    var sheetWidthPx by remember { mutableIntStateOf(0) } // меняется, пока панель выезжает

    Row(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isOpen,
            modifier = Modifier.onSizeChanged { sheetWidthPx = it.width },
            enter = expandHorizontally(expandFrom = Alignment.End),
            exit = shrinkHorizontally(shrinkTowards = Alignment.End),
        ) {
            sheet()
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            // Отступ слева панель забирает себе, как PersistentDrawerLayout
            val left = (windowInsets.left - if (isOpen) sheetWidthPx else 0).coerceAtLeast(0)
            content(Insets.of(left, windowInsets.top, windowInsets.right, windowInsets.bottom))
        }
    }
}

// Список пунктов; разделители — null в списке. Прокручивается под строкой состояния, как NavigationRecyclerView
@Composable
private fun NavigationSheet(
    items: List<NavigationItem?>,
    checked: List<Boolean>,
    windowInsets: Insets,
    width: Dp,
    elevation: Dp,
    overshoot: () -> Float, // px, на сколько панель проскочила открытое положение; читается при отрисовке
    listener: NavigationItem.Listener,
) {
    val density = LocalDensity.current
    val (left, top, bottom) = with(density) {
        listOf(windowInsets.left.toDp(), windowInsets.top.toDp(), windowInsets.bottom.toDp())
    }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Surface(
        modifier = Modifier
            // Проскочившую панель держим у края: сдвигаем обратно на величину проскока. Целыми px, как сдвигает
            // ModalNavigationDrawer: от сдвига на долю пикселя меняются расстояния между буквами
            .graphicsLayer {
                val gap = overshoot()
                translationX = when {
                    gap <= 0f || gap.isNaN() -> 0f // NaN — пока DrawerState не знает ширину панели
                    isRtl -> gap.roundToInt().toFloat()
                    else -> -gap.roundToInt().toFloat()
                }
            }
            .width(width)
            .fillMaxHeight()
            .testTag("navigation"), // контейнер навигации для UI-тестов
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = elevation,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = left,
                top = VERTICAL_PADDING + top,
                bottom = VERTICAL_PADDING + bottom,
            ),
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
