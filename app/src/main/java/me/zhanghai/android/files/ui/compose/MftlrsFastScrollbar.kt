/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.zhanghai.android.files.theme.MftlrsTheme
import kotlin.math.abs
import kotlin.math.roundToInt

private val THUMB_WIDTH = 8.dp
private val THUMB_HEIGHT = 52.dp
private val TOUCH_WIDTH = 48.dp // полоса касания у правого края
private const val TRACK_ALPHA = 0.2f

/**
 * Обёртка над [content] (ленивой сеткой на [state]) с полосой быстрой прокрутки справа: всегда видна,
 * пока список длиннее экрана. Ручку тянут; провести по полосе — ручка прыгает под палец.
 * Считает, что у всех строк одна высота. [bottomPadding] — отступ полосы снизу (системная панель).
 */
@Composable
fun MftlrsFastScrollbar(
    state: LazyGridState,
    columnCount: Int,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val maxScroll by remember(state, columnCount) { derivedStateOf { state.maxScroll(columnCount) } }
    val progress by remember(state, columnCount) {
        derivedStateOf {
            val max = maxScroll
            if (max > 0) (state.scroll(columnCount).toFloat() / max).coerceIn(0f, 1f) else 0f
        }
    }

    Box(
        modifier = modifier.pointerInput(state, columnCount, bottomPadding) {
            // Initial: касание видно раньше списка; забираем его, только когда тянем полосу,
            // иначе тапы по ⋮ под полосой не дошли бы до элементов
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (maxScroll <= 0 || down.position.x < size.width - TOUCH_WIDTH.toPx()) return@awaitEachGesture

                val thumbHeight = THUMB_HEIGHT.toPx()
                val trackLength = size.height - bottomPadding.toPx() - thumbHeight
                val thumbTop = progress * trackLength
                val isOnThumb = down.position.y in thumbTop..thumbTop + thumbHeight
                val grabOffset = if (isOnThumb) down.position.y - thumbTop else thumbHeight / 2 // за что держим ручку
                var isDragging = isOnThumb
                if (isDragging) down.consume()

                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break

                    val y = change.position.y
                    if (!isDragging && abs(y - down.position.y) > viewConfiguration.touchSlop) isDragging = true
                    if (!isDragging) continue

                    change.consume()
                    val fraction = ((y - grabOffset) / trackLength).coerceIn(0f, 1f)
                    scope.launch { state.scrollToFraction(fraction, columnCount) }
                }
            }
        },
    ) {
        content()

        if (maxScroll > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(bottom = bottomPadding)
                    .fillMaxHeight()
                    .width(THUMB_WIDTH)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TRACK_ALPHA),
                        RoundedCornerShape(THUMB_WIDTH),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .layout { measurable, constraints -> // ручка ходит по высоте полосы
                            val thumb = measurable.measure(
                                Constraints.fixed(THUMB_WIDTH.roundToPx(), THUMB_HEIGHT.roundToPx())
                            )
                            layout(thumb.width, constraints.maxHeight) {
                                thumb.place(0, (progress * (constraints.maxHeight - thumb.height)).roundToInt())
                            }
                        }
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(THUMB_WIDTH)),
                )
            }
        }
    }
}

// Прокрутка в пикселях по номеру строки и смещению
private fun LazyGridState.scroll(columnCount: Int): Int =
    firstVisibleItemIndex / columnCount * rowHeight() + firstVisibleItemScrollOffset

private fun LazyGridState.maxScroll(columnCount: Int): Int {
    val rowHeight = rowHeight()
    val itemCount = layoutInfo.totalItemsCount
    if (rowHeight == 0 || itemCount == 0) return 0
    val rowCount = (itemCount - 1) / columnCount + 1
    return layoutInfo.beforeContentPadding + rowCount * rowHeight + layoutInfo.afterContentPadding -
        layoutInfo.viewportSize.height
}

private suspend fun LazyGridState.scrollToFraction(fraction: Float, columnCount: Int) {
    val rowHeight = rowHeight()
    if (rowHeight == 0) return
    val target = (fraction * maxScroll(columnCount)).roundToInt()
    val row = target / rowHeight
    scrollToItem(index = row * columnCount, scrollOffset = target - row * rowHeight)
}

private fun LazyGridState.rowHeight(): Int = layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 0

@Preview(name = "Полоса", heightDp = 400)
@Composable
private fun FastScrollbarPreview() {
    val state = rememberLazyGridState()
    MftlrsTheme {
        Surface {
            MftlrsFastScrollbar(state = state, columnCount = 1) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(100) { index ->
                        Text(text = "Элемент $index", modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}
