/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R

// FAB главного экрана — перенос ThemedSpeedDialView с menu/file_list_speed_dial.xml: плюс, при раскрытии —
// поворот на 135°, затемнение экрана и пункты «Файл», «Папка» с подписями

private const val OVERLAY_ALPHA = 0.87f // ThemedSpeedDialView: colorSurface с прозрачностью 0,87
private const val EXPANDED_ROTATION = 135f // sdMainFabAnimationRotateAngle

internal enum class FileListFabAction(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
) {
    CREATE_FILE(R.string.file_list_action_create_file, R.drawable.file_icon_white_24dp),
    CREATE_DIRECTORY(R.string.file_list_action_create_directory, R.drawable.directory_icon_white_24dp),
}

@Composable
internal fun FileListFab(
    isExpanded: Boolean,
    bottomPadding: Dp, // над нижней панелью или панелью навигации
    endPadding: Dp, // панель навигации справа (ландшафт)
    onExpandedChange: (Boolean) -> Unit,
    onAction: (FileListFabAction) -> Unit,
) {
    val duration = integerResource(android.R.integer.config_shortAnimTime)
    val margin = dimensionResource(R.dimen.screen_edge_margin)

    // Пустое место не забирает касания: экран под слоем FAB работает, пока FAB закрыт
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(duration)),
            exit = fadeOut(tween(duration)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = OVERLAY_ALPHA))
                    .clickable(interactionSource = null, indication = null) { onExpandedChange(false) },
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = margin + endPadding, bottom = margin + bottomPadding),
            horizontalAlignment = Alignment.End,
        ) {
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(duration)),
                exit = fadeOut(tween(duration)),
            ) {
                // Первый пункт меню — ближе к кнопке, как в SpeedDialView
                Column(
                    modifier = Modifier.padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ActionItem(FileListFabAction.CREATE_FILE, onAction)
                    ActionItem(FileListFabAction.CREATE_DIRECTORY, onAction)
                }
            }
            MainButton(
                isExpanded = isExpanded,
                duration = duration,
                onClick = { onExpandedChange(!isExpanded) },
            )
        }
    }
}

// Цвета закрытой и раскрытой кнопки — как в ThemedSpeedDialView
@Composable
private fun MainButton(
    isExpanded: Boolean,
    duration: Int,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) EXPANDED_ROTATION else 0f,
        animationSpec = tween(duration, easing = FastOutSlowInEasing),
        label = "fabRotation",
    )
    val containerColor by animateColorAsState(
        targetValue = if (isExpanded) colorScheme.primary else colorScheme.secondaryContainer,
        animationSpec = tween(duration, easing = FastOutSlowInEasing),
        label = "fabContainerColor",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isExpanded) colorScheme.onPrimary else colorScheme.onSecondaryContainer,
        animationSpec = tween(duration, easing = FastOutSlowInEasing),
        label = "fabContentColor",
    )

    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.testTag("sd_main_fab"), // id главной кнопки speed-dial: по нему FAB находят UI-тесты
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        Icon(
            painter = painterResource(R.drawable.add_icon_white_24dp),
            contentDescription = null,
            modifier = Modifier.rotate(rotation),
        )
    }
}

// Подпись и маленькая кнопка; подпись тоже нажимается, без ripple
@Composable
private fun ActionItem(
    action: FileListFabAction,
    onAction: (FileListFabAction) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(action.titleRes),
            modifier = Modifier.clickable(interactionSource = null, indication = null) { onAction(action) },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        SmallFloatingActionButton(
            onClick = { onAction(action) },
            modifier = Modifier.padding(horizontal = 8.dp), // по центру над кнопкой 56 dp
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(
                painter = painterResource(action.iconRes),
                contentDescription = null,
            )
        }
    }
}
