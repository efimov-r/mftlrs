/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.fileproperties.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.fileproperties.FilePropertiesFileViewModel
import me.zhanghai.android.files.fileproperties.FilePropertiesTab
import me.zhanghai.android.files.fileproperties.FilePropertyItem
import me.zhanghai.android.files.provider.common.PosixFileAttributes
import me.zhanghai.android.files.provider.common.PosixPrincipal
import me.zhanghai.android.files.provider.common.toInt
import me.zhanghai.android.files.provider.common.toModeString

fun isPermissionTabAvailable(file: FileItem): Boolean {
    val attributes = file.attributes
    return attributes is PosixFileAttributes && (attributes.owner() != null || attributes.group() != null
        || attributes.mode() != null || attributes.seLinuxContext() != null)
}

// Вкладка «Права», была FilePropertiesPermissionTabFragment; нажатие на строку — диалог изменения
@Composable
internal fun FilePropertiesPermissionTab(viewModel: FilePropertiesFileViewModel) {
    val stateful by viewModel.fileLiveData.observeAsState()
    var dialog by rememberSaveable { mutableStateOf<PermissionDialog?>(null) }

    FilePropertiesTab(stateful, onRefresh = viewModel::reload) { file ->
        val attributes = file.attributes as PosixFileAttributes
        val owner = attributes.owner()
        FilePropertyItem(
            stringResource(R.string.file_properties_permission_owner),
            principalText(owner),
            owner?.let { { dialog = PermissionDialog.OWNER } },
        )
        val group = attributes.group()
        FilePropertyItem(
            stringResource(R.string.file_properties_permission_group),
            principalText(group),
            group?.let { { dialog = PermissionDialog.GROUP } },
        )
        val mode = attributes.mode()
        FilePropertyItem(
            stringResource(R.string.file_properties_permission_mode),
            if (mode != null) {
                stringResource(R.string.file_properties_permission_mode_format, mode.toModeString(), mode.toInt())
            } else {
                stringResource(R.string.unknown)
            },
            if (mode != null && !attributes.isSymbolicLink) {
                { dialog = PermissionDialog.MODE }
            } else {
                null
            },
        )
        attributes.seLinuxContext()?.let { context ->
            FilePropertyItem(
                stringResource(R.string.file_properties_permission_selinux_context),
                if (context.isNotEmpty()) context.toString() else stringResource(R.string.empty_placeholder),
            ) { dialog = PermissionDialog.SELINUX_CONTEXT }
        }

        // Диалог — по файлу, который сейчас на вкладке
        val dismiss = { dialog = null }
        when (dialog) {
            PermissionDialog.OWNER -> SetPrincipalDialog(file, PrincipalType.OWNER, dismiss)
            PermissionDialog.GROUP -> SetPrincipalDialog(file, PrincipalType.GROUP, dismiss)
            PermissionDialog.MODE -> SetModeDialog(file, dismiss)
            PermissionDialog.SELINUX_CONTEXT -> SetSeLinuxContextDialog(file, dismiss)
            null -> {}
        }
    }
}

private enum class PermissionDialog { OWNER, GROUP, MODE, SELINUX_CONTEXT }

@Composable
private fun principalText(principal: PosixPrincipal?): String {
    if (principal == null) return stringResource(R.string.unknown)
    val name = principal.name ?: return principal.id.toString()
    return stringResource(R.string.file_properties_permission_principal_format, name, principal.id)
}
