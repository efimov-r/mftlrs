/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import me.zhanghai.android.files.R
import me.zhanghai.android.files.theme.MftlrsTheme
import me.zhanghai.android.files.ui.compose.MftlrsBottomSheet
import me.zhanghai.android.files.util.addOnBackPressedCallback

// «Назад» спрашивает: закрыть приложение, свернуть или остаться.
// Вызывать раньше остальных addOnBackPressedCallback: последний добавленный срабатывает первым
fun Fragment.addExitConfirmation(): OnBackPressedCallback {
    val visible = mutableStateOf(false)
    val activity = requireActivity()
    val container = activity.findViewById<ViewGroup>(android.R.id.content)

    val composeView = ComposeView(activity).apply {
        setViewTreeLifecycleOwner(viewLifecycleOwner)
        setViewTreeSavedStateRegistryOwner(requireView().findViewTreeSavedStateRegistryOwner()) // активити без setContentView: владельцев нет на decor view
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MftlrsTheme {
                ExitSheet(
                    visible = visible.value,
                    onClose = { activity.finishAndRemoveTask() }, // и убрать из недавних
                    onMinimize = {
                        visible.value = false
                        activity.moveTaskToBack(true)
                    },
                    onDismissRequest = { visible.value = false },
                )
            }
        }
    }
    container.addView(composeView, ViewGroup.LayoutParams(0, 0)) // сам ничего не рисует, панель — в своём окне

    viewLifecycleOwner.lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                container.removeView(composeView)
            }
        }
    )

    val callback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            visible.value = true
        }
    }
    addOnBackPressedCallback(callback)

    return callback
}

@Composable
private fun ExitSheet(
    visible: Boolean,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    MftlrsBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.file_list_exit_title, stringResource(R.string.app_name)),
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )

            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.close))
            }

            FilledTonalButton(
                onClick = onMinimize,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.file_list_exit_minimize))
            }

            OutlinedButton(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    }
}