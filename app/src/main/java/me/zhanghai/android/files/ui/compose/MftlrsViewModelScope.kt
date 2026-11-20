/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

// Свои ViewModel на время показа диалога: viewModel() внутри content кладёт их в отдельное хранилище.
// Оно лежит во ViewModel родителя и переживает поворот; очищается, когда content ушёл из композиции
// после вызова finish (диалог закрыли). Так у двух диалогов подряд не смешиваются данные
@Composable
fun MftlrsViewModelScope(key: String, content: @Composable (finish: () -> Unit) -> Unit) {
    val scope = viewModel(key = "MftlrsViewModelScope:$key") { ScopeViewModel() }

    DisposableEffect(scope) {
        scope.isFinished = false
        onDispose {
            if (scope.isFinished) scope.viewModelStore.clear()
        }
    }

    CompositionLocalProvider(LocalViewModelStoreOwner provides scope) {
        content { scope.isFinished = true }
    }
}

private class ScopeViewModel : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    var isFinished = false

    override fun onCleared() {
        viewModelStore.clear()
    }
}
