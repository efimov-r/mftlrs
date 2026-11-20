/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.fileproperties.apk

import android.content.pm.PermissionInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.compat.protectionCompat
import me.zhanghai.android.files.ui.compose.MftlrsDialog
import me.zhanghai.android.files.ui.compose.MftlrsViewModelScope
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.util.copyText
import java.util.Locale

// Разрешения из APK, был PermissionListDialogFragment: название (опасные — жирным), имя, описание;
// долгое нажатие копирует имя
@Composable
internal fun PermissionListDialog(permissionNames: List<String>, onDismissRequest: () -> Unit) {
    MftlrsViewModelScope("PermissionListDialog") { finish ->
        val dismiss = {
            finish()
            onDismissRequest()
        }
        val viewModel = viewModel { PermissionListViewModel(permissionNames.toTypedArray()) }
        val stateful by viewModel.permissionListLiveData.observeAsState()

        MftlrsDialog(
            title = pluralStringResource(
                R.plurals.file_properties_apk_requested_permissions_positive_format,
                permissionNames.size,
                permissionNames.size,
            ),
            onDismissRequest = dismiss,
            buttons = {
                TextButton(onClick = dismiss) { Text(stringResource(android.R.string.ok)) }
            },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (val stateful = stateful) {
                    is Success ->
                        if (stateful.value.isEmpty()) {
                            Text(stringResource(R.string.empty), style = MaterialTheme.typography.bodyLarge)
                        } else {
                            LazyColumn { items(stateful.value) { PermissionItemRow(it) } }
                        }
                    is Failure ->
                        Text(
                            text = stateful.throwable.toString(),
                            modifier = Modifier.padding(horizontal = 24.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    is Loading, null -> CircularProgressIndicator()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PermissionItemRow(permission: PermissionItem) {
    val context = LocalContext.current
    val label = permission.label

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .combinedClickable(
                onClick = {},
                onLongClick = { clipboardManager.copyText(permission.name, context) },
            )
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text(
            text = label?.replaceFirstChar { it.titlecase(Locale.getDefault()) } ?: permission.name,
            fontWeight = if (permission.permissionInfo?.protectionCompat == PermissionInfo.PROTECTION_DANGEROUS) {
                FontWeight.Bold
            } else {
                null
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
        if (label != null) {
            Text(permission.name, color = secondaryColor, style = MaterialTheme.typography.bodyMedium)
        }
        permission.description?.takeIf { it.isNotEmpty() }?.let {
            Text(it, color = secondaryColor, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
