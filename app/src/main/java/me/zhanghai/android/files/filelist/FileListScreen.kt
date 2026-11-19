/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import me.zhanghai.android.files.util.getDimensionPixelSizeByAttr

// Главный экран целиком в одном ComposeView — вместо CoordinatorLayout, AppBarLayout, PersistentBarLayout и
// BottomBarLayout: панель с крошками сверху, список, нижняя панель, FAB с затемнением поверх всего.
// Части рисуют свои composable, здесь — только раскладка и отступы под системные панели

private val BOTTOM_BAR_ELEVATION = 4.dp // design_appbar_elevation у BottomBarLayout

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FileListScreen(
    windowInsets: Insets, // px, systemWindowInsets от DrawerLayout: типов в них нет, WindowInsets Compose не подходят
    isBottomBarVisible: Boolean,
    topBar: @Composable () -> Unit,
    breadcrumbs: @Composable () -> Unit,
    content: @Composable (bottomInset: Dp) -> Unit,
    bottomBar: @Composable () -> Unit,
    fab: @Composable (bottomPadding: Dp, endPadding: Dp) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val barHeight = remember(context, density) {
        with(density) { context.getDimensionPixelSizeByAttr(androidx.appcompat.R.attr.actionBarSize).toDp() }
    }
    val (left, top, right, bottom) = with(density) {
        listOf(windowInsets.left.toDp(), windowInsets.top.toDp(), windowInsets.right.toDp(), windowInsets.bottom.toDp())
    }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) } // вместе с отступом под навигацию; меняется при выезде
    val bottomBarHeight = with(density) { bottomBarHeightPx.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }, // testTag виден UI-тестам как id
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Фон панели уходит под строку состояния, как у AppBarLayout с fitsSystemWindows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(start = left, top = top, end = right),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight),
                ) {
                    topBar()
                }
                breadcrumbs()
            }
            // Список уходит под панель навигации; нижняя панель забирает её отступ себе, как PersistentBarLayout
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = left, end = right),
            ) {
                content((bottom - bottomBarHeight).coerceAtLeast(0.dp))
            }
            AnimatedVisibility(
                visible = isBottomBarVisible,
                modifier = Modifier.onSizeChanged { bottomBarHeightPx = it.height },
                enter = expandVertically(expandFrom = Alignment.Top),
                exit = shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = BOTTOM_BAR_ELEVATION,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = left, end = right, bottom = bottom)
                            .height(barHeight),
                    ) {
                        bottomBar()
                    }
                }
            }
        }
        // Затемнение — на весь экран, кнопка — над нижней панелью или над панелью навигации
        fab(maxOf(bottom, bottomBarHeight), right)
    }
}
