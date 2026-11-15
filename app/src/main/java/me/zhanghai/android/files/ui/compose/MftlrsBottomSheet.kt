/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

private const val ENTER_MS = 300
private const val EXIT_MS = 120 // уход заметно быстрее появления
private const val WARMUP_FRAMES = 3 // кадры на создание окна и первую сборку содержимого, до начала выезда
private const val SCRIM_ALPHA = 0.4f
private const val DISMISS_VELOCITY = 1500f // px/с
private val DISMISS_DRAG = 80.dp

// Панель, выезжающая снизу (как в Seryve). visible = false запускает анимацию ухода.
// Содержимое собирается за экраном до начала выезда: тяжёлая форма иначе рвёт анимацию
@Composable
fun MftlrsBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    fillHeight: Boolean = false, // на всю высоту (для форм с клавиатурой): панель не прыгает и не отрывается от низа
    content: @Composable ColumnScope.() -> Unit,
) {
    val progress = remember { Animatable(0f) } // 0 — спрятана, 1 — показана
    var windowShown by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            windowShown = true
            repeat(WARMUP_FRAMES) { withFrameNanos { } }
            progress.animateTo(1f, tween(ENTER_MS, easing = FastOutSlowInEasing))
        } else if (windowShown) {
            progress.animateTo(0f, tween(EXIT_MS, easing = FastOutLinearInEasing))
            windowShown = false
        }
    }

    if (!windowShown) return

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnClickOutside = false, // тап мимо обрабатывает свой фон
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        DisableWindowDim()

        SheetLayout(
            progress = { progress.value },
            onDismissRequest = onDismissRequest,
            fillHeight = fillHeight,
            content = content,
        )
    }
}

@Composable
private fun SheetLayout(
    progress: () -> Float,
    onDismissRequest: () -> Unit,
    fillHeight: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    var dragPx by remember { mutableFloatStateOf(0f) }
    val heightModifier = if (fillHeight) Modifier.fillMaxHeight() else Modifier
    val density = LocalDensity.current
    val currentDismiss by rememberUpdatedState(onDismissRequest)

    // Отпустили: закрыть, если утянули далеко или резко, иначе вернуть на место
    suspend fun settle(velocity: Float) {
        if (dragPx > with(density) { DISMISS_DRAG.toPx() } || velocity > DISMISS_VELOCITY) {
            currentDismiss()
        } else {
            animate(dragPx, 0f) { value, _ -> dragPx = value }
        }
    }

    // Содержимое прокручивается само; когда оно уже в начале, остаток жеста вниз двигает панель
    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f || dragPx <= 0f) return Offset.Zero

                val consumed = maxOf(available.y, -dragPx) // палец вверх: сначала вернуть панель
                dragPx += consumed

                return Offset(0f, consumed)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero

                dragPx += available.y

                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragPx <= 0f) return Velocity.Zero

                settle(available.y)

                return available
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress() }
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp)
                .imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(heightModifier)
                    .graphicsLayer { translationY = (1f - progress()) * size.height + dragPx } // только отрисовка
                    .pointerInput(Unit) {} // тап по панели не должен доходить до фона
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta -> dragPx = (dragPx + delta).coerceAtLeast(0f) },
                        onDragStopped = { velocity -> settle(velocity) },
                    ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier
                        .then(heightModifier)
                        .nestedScroll(nestedScroll)
                        .navigationBarsPadding(),
                ) {
                    DragHandle()

                    content()
                }
            }
        }
    }
}

// Только подсказка: тянуть можно за любое место панели
@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
    }
}

// Системное затемнение окна диалога выключаем: фон рисуем сами, чтобы управлять скоростью
@Composable
private fun DisableWindowDim() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window

    SideEffect { window?.setDimAmount(0f) }
}