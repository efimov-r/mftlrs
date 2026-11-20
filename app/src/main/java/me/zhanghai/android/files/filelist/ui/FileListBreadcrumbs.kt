/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java8.nio.file.Path
import kotlinx.coroutines.launch
import me.zhanghai.android.files.R
import me.zhanghai.android.files.filelist.data.BreadcrumbData

// Хлебные крошки главного экрана — перенос BreadcrumbLayout и menu/file_list_breadcrumb.xml

private val ITEM_HEIGHT = 40.dp // маска ripple из breadcrumb_item_foreground.xml
private val ITEM_SHAPE = RoundedCornerShape(20.dp)

@Composable
internal fun FileListBreadcrumbs(
    data: BreadcrumbData?,
    onNavigate: (Path) -> Unit,
    onCopyPath: (Path) -> Unit,
    onOpenInNewTask: (Path) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isFirstScroll by remember { mutableStateOf(true) }

    // Выбранная крошка — к левому краю; первый раз без анимации, дальше плавно, как в BreadcrumbLayout
    LaunchedEffect(data) {
        if (data == null) return@LaunchedEffect
        if (isFirstScroll) {
            listState.scrollToItem(data.selectedIndex)
        } else {
            listState.animateScrollToItem(data.selectedIndex)
        }
        isFirstScroll = false
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.tab_layout_height)),
        contentPadding = PaddingValues(
            start = dimensionResource(R.dimen.content_start_margin_minus_12dp),
            end = dimensionResource(R.dimen.screen_edge_margin_minus_12dp),
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (data == null) return@LazyRow
        itemsIndexed(data.paths) { index, path ->
            BreadcrumbItem(
                name = data.nameProducers[index](context),
                isSelected = index == data.selectedIndex,
                isLast = index == data.paths.lastIndex,
                onClick = {
                    if (index == data.selectedIndex) {
                        scope.launch { listState.animateScrollToItem(index) }
                    } else {
                        onNavigate(path)
                    }
                },
                onCopyPath = { onCopyPath(path) },
                onOpenInNewTask = { onOpenInNewTask(path) },
            )
        }
    }
}

// Крошка: имя и стрелка (кроме последней); долгое нажатие — меню
@Composable
private fun BreadcrumbItem(
    name: String,
    isSelected: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onCopyPath: () -> Unit,
    onOpenInNewTask: () -> Unit,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    val color = if (isSelected) { // textColorPrimary / textColorSecondary по state_activated
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box {
        Row(
            modifier = Modifier
                .height(ITEM_HEIGHT)
                .clip(ITEM_SHAPE)
                .combinedClickable(
                    onLongClick = { isMenuExpanded = true },
                    onClick = onClick,
                )
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                modifier = Modifier.padding(end = if (isLast) 8.dp else 4.dp), // у стрелки было marginStart -4dp
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall, // TextAppearance.Design.Tab: 14sp, medium
            )
            if (!isLast) {
                Icon(
                    painter = painterResource(R.drawable.arrow_end_icon_white_24dp),
                    contentDescription = null,
                    tint = color,
                )
            }
        }
        DropdownMenu(
            expanded = isMenuExpanded,
            onDismissRequest = { isMenuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.file_list_breadcrumb_action_copy_path)) },
                onClick = {
                    isMenuExpanded = false
                    onCopyPath()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.file_list_breadcrumb_action_open_in_new_task)) },
                onClick = {
                    isMenuExpanded = false
                    onOpenInNewTask()
                },
            )
        }
    }
}
