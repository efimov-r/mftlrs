/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.R

// FAB главного экрана. Пока без действия: создание файла и папки переехало в «+» верхней панели,
// у кнопки будет другая функция. Касания забирает, но не нажимается
@Composable
internal fun FileListFab(
    bottomPadding: Dp, // над нижней панелью или панелью навигации
    endPadding: Dp, // панель навигации справа (ландшафт)
) {
    val margin = dimensionResource(R.dimen.screen_edge_margin)

    // Пустое место не забирает касания: список под слоем FAB работает
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = margin + endPadding, bottom = margin + bottomPadding)
                .size(56.dp),
            shape = FloatingActionButtonDefaults.shape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shadowElevation = 6.dp, // FloatingActionButtonDefaults.elevation()
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.add_icon_white_24dp),
                    contentDescription = null,
                )
            }
        }
    }
}
