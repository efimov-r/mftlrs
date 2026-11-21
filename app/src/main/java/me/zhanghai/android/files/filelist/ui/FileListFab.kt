/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import me.zhanghai.android.files.R

// FAB главного экрана: открывает нижнюю панель мест. Пустое место слоя не забирает касания: список под ним работает
@Composable
internal fun FileListFab(
    visible: Boolean,
    bottomPadding: Dp, // над нижней панелью или панелью навигации
    endPadding: Dp, // панель навигации справа (ландшафт)
    onClick: () -> Unit,
) {
    val margin = dimensionResource(R.dimen.screen_edge_margin)

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = margin + endPadding, bottom = margin + bottomPadding),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            FloatingActionButton(
                onClick = onClick,
                modifier = Modifier.testTag("navigationFab"),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(
                    painter = painterResource(R.drawable.menu_icon_control_normal_24dp),
                    contentDescription = stringResource(R.string.open_navigation_drawer),
                )
            }
        }
    }
}
