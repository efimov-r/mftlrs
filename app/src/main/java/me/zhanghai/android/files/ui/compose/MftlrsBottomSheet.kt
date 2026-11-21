/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.Insets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Панель снизу поверх экрана, в окне экрана, а не в своём (как ModalBottomSheet): затемнение растёт вместе с
// выездом панели, панель останавливается под строкой состояния, строка остаётся затемнённой. Свайп вниз
// (по ручке, по содержимому или по списку, прокрученному к началу), «Назад» и тап по фону вызывают
// onDismissRequest; visible = false убирает панель с анимацией. Поверх соседей — через zIndex

private const val SHOW_DURATION_MILLIS = 400
private const val HIDE_DURATION_MILLIS = 300
private const val SCRIM_ALPHA = 0.32f // BottomSheetDefaults.ScrimColor
private const val DISMISS_PROGRESS = 0.5f // отпустили ниже середины — панель закрывается
private const val Z_INDEX = 1f

private val SHEET_MAX_WIDTH = 640.dp // BottomSheetDefaults.SheetMaxWidth
private val CORNER_RADIUS = 28.dp // shapes.extraLarge сверху
private val FLING_VELOCITY = 125.dp // в секунду, как у SheetState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MftlrsBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    windowInsets: Insets, // px: сверху — строка состояния, снизу — панель навигации
    isFullHeight: Boolean = false, // иначе — по содержимому, но не выше строки состояния
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val motion = remember { SheetMotion(scope) }
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    var isShown by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            isShown = true
            motion.animateTo(1f, SHOW_DURATION_MILLIS)
        } else if (isShown) {
            motion.animateTo(0f, HIDE_DURATION_MILLIS) { isShown = false }
        }
    }

    if (!isShown) return

    // Появляется в композиции позже остальных — срабатывает первым
    BackHandler(enabled = visible) { currentOnDismissRequest() }

    val density = LocalDensity.current
    val flingVelocity = with(density) { FLING_VELOCITY.toPx() }
    // Палец отпустил панель: закрыть или вернуть. Уже закрытую (уходит) — только довести до конца
    val settle = { velocity: Float ->
        val isDismissed = velocity > flingVelocity ||
            velocity >= -flingVelocity && motion.progress < DISMISS_PROGRESS
        when {
            !visible -> motion.animateTo(0f, HIDE_DURATION_MILLIS) { isShown = false }
            isDismissed -> currentOnDismissRequest()
            else -> motion.animateTo(1f, SHOW_DURATION_MILLIS)
        }
    }
    val currentSettle by rememberUpdatedState(settle)
    val currentVisible by rememberUpdatedState(visible)
    // Уходящую панель не подхватить: иначе палец останавливает анимацию ухода
    val nestedScroll = remember(motion) {
        SheetNestedScroll(motion, isEnabled = { currentVisible }) { currentSettle(it) }
    }
    val (left, top, right, bottom) = with(density) {
        listOf(windowInsets.left.toDp(), windowInsets.top.toDp(), windowInsets.right.toDp(), windowInsets.bottom.toDp())
    }
    val scrimColor = MaterialTheme.colorScheme.scrim

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(Z_INDEX),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(scrimColor, alpha = SCRIM_ALPHA * motion.progress) }
                .pointerInput(Unit) { detectTapGestures { currentOnDismissRequest() } },
        )
        // Вне панели касания проходят к затемнению
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = left, top = top, end = right),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = SHEET_MAX_WIDTH)
                    .fillMaxWidth()
                    .then(if (isFullHeight) Modifier.fillMaxHeight() else Modifier)
                    .onSizeChanged { motion.height = it.height }
                    .graphicsLayer { translationY = (1f - motion.progress) * size.height }
                    .nestedScroll(nestedScroll)
                    .draggable(
                        state = rememberDraggableState { motion.dragBy(it) },
                        orientation = Orientation.Vertical,
                        enabled = visible,
                        onDragStopped = { currentSettle(it) },
                    ),
                shape = RoundedCornerShape(topStart = CORNER_RADIUS, topEnd = CORNER_RADIUS),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(modifier = Modifier.padding(bottom = bottom)) {
                    BottomSheetDefaults.DragHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
                    content()
                }
            }
        }
    }
}

// Положение панели: 0 — спрятана, 1 — открыта. Пальцем — сразу, иначе — анимацией
private class SheetMotion(private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(0f)
        private set
    var height = 0 // px
    private var job: Job? = null

    fun animateTo(target: Float, durationMillis: Int, onEnd: () -> Unit = {}) {
        job?.cancel()
        job = scope.launch {
            animate(
                initialValue = progress,
                targetValue = target,
                animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
            ) { value, _ -> progress = value }
            onEnd()
        }
    }

    // delta > 0 — палец вниз; возвращает сдвиг, который забрала панель
    fun dragBy(delta: Float): Float {
        if (height == 0) return 0f

        job?.cancel()
        val old = progress
        progress = (progress - delta / height).coerceIn(0f, 1f)
        return (old - progress) * height
    }
}

// Список внутри панели: прокрученный к началу тянет панель вниз, недотянутую панель сначала поднимает обратно
private class SheetNestedScroll(
    private val motion: SheetMotion,
    private val isEnabled: () -> Boolean,
    private val onSettle: (velocity: Float) -> Unit,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (isEnabled() && available.y < 0 && motion.progress < 1f) {
            Offset(0f, motion.dragBy(available.y))
        } else {
            Offset.Zero
        }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (isEnabled() && available.y > 0 && source == NestedScrollSource.UserInput) {
            Offset(0f, motion.dragBy(available.y))
        } else {
            Offset.Zero
        }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!isEnabled() || motion.progress >= 1f) return Velocity.Zero

        onSettle(available.y)
        return available
    }
}
