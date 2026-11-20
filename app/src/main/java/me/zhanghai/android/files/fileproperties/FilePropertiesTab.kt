/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.fileproperties

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.showToast

// Вкладка «Свойств», была FilePropertiesTabFragment: загрузка и ошибка по центру, пока данных нет;
// с данными — строки content, свайп вниз — onRefresh, ошибка — тостом
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> FilePropertiesTab(
    stateful: Stateful<T>?,
    onRefresh: () -> Unit,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    val value = stateful?.value
    val context = LocalContext.current

    LaunchedEffect(stateful) {
        if (stateful !is Failure) return@LaunchedEffect
        stateful.throwable.printStackTrace()
        if (value != null) context.showToast(stateful.throwable.toString())
    }

    when {
        value != null ->
            PullToRefreshBox(
                isRefreshing = stateful is Loading,
                onRefresh = onRefresh,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    content(value)
                }
            }
        stateful is Failure ->
            TabMessage { Text(stateful.throwable.toString(), textAlign = TextAlign.Center) }
        else -> TabMessage { CircularProgressIndicator() }
    }
}

@Composable
private fun TabMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// Строка «подпись — значение», была ReadOnlyTextInputLayout: текст выделяется; с onClick — нажимается
// целиком, справа стрелка
@Composable
internal fun FilePropertyItem(label: String, text: String, onClick: (() -> Unit)? = null) {
    Box {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            label = { Text(label) },
            trailingIcon = onClick?.let {
                { Icon(painterResource(com.google.android.material.R.drawable.mtrl_ic_arrow_drop_down), null) }
            },
            textStyle = MaterialTheme.typography.bodyLarge,
        )
        if (onClick != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(top = 8.dp) // над рамкой — место подписи
                    .clip(OutlinedTextFieldDefaults.shape)
                    .clickable(role = Role.Button, onClick = onClick),
            )
        }
    }
}
