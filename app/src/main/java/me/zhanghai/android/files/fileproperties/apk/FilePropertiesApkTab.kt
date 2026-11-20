/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.fileproperties.apk

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compat.longVersionCodeCompat
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.isApk
import me.zhanghai.android.files.fileproperties.FilePropertiesTab
import me.zhanghai.android.files.fileproperties.FilePropertyItem
import me.zhanghai.android.files.util.isGetPackageArchiveInfoCompatible

fun isApkTabAvailable(file: FileItem): Boolean = file.mimeType.isApk && file.path.isGetPackageArchiveInfoCompatible

// Вкладка «APK», была FilePropertiesApkTabFragment; нажатие на «Разрешения» — их список
@Composable
internal fun FilePropertiesApkTab(file: FileItem) {
    val viewModel = viewModel { FilePropertiesApkTabViewModel(file.path) }
    val stateful by viewModel.apkInfoLiveData.observeAsState()
    var isPermissionListShown by rememberSaveable { mutableStateOf(false) }

    FilePropertiesTab(stateful, onRefresh = viewModel::reload) { apkInfo ->
        val packageInfo = apkInfo.packageInfo
        FilePropertyItem(stringResource(R.string.file_properties_apk_label), apkInfo.label)
        FilePropertyItem(stringResource(R.string.file_properties_apk_package_name), packageInfo.packageName)
        FilePropertyItem(
            stringResource(R.string.file_properties_apk_version),
            stringResource(
                R.string.file_properties_apk_version_format,
                packageInfo.versionName.toString(),
                packageInfo.longVersionCodeCompat,
            ),
        )
        val applicationInfo = packageInfo.applicationInfo!!
        // minSdk 26: minSdkVersion есть всегда (было условие для Android до 7.0)
        FilePropertyItem(
            stringResource(R.string.file_properties_apk_min_sdk_version),
            sdkVersionText(applicationInfo.minSdkVersion),
        )
        FilePropertyItem(
            stringResource(R.string.file_properties_apk_target_sdk_version),
            sdkVersionText(applicationInfo.targetSdkVersion),
        )
        val permissionNames = packageInfo.requestedPermissions.orEmpty().toList()
        FilePropertyItem(
            stringResource(R.string.file_properties_apk_requested_permissions),
            if (permissionNames.isEmpty()) {
                stringResource(R.string.file_properties_apk_requested_permissions_zero)
            } else {
                pluralStringResource(
                    R.plurals.file_properties_apk_requested_permissions_positive_format,
                    permissionNames.size,
                    permissionNames.size,
                )
            },
            if (permissionNames.isEmpty()) null else ({ isPermissionListShown = true }),
        )
        FilePropertyItem(
            stringResource(R.string.file_properties_apk_signature_digests),
            if (apkInfo.signingCertificateDigests.isNotEmpty()) {
                apkInfo.signingCertificateDigests.joinToString("\n")
            } else {
                stringResource(R.string.file_properties_apk_signature_digests_empty)
            },
        )
        if (apkInfo.pastSigningCertificateDigests.isNotEmpty()) {
            FilePropertyItem(
                stringResource(R.string.file_properties_apk_past_signature_digests),
                apkInfo.pastSigningCertificateDigests.joinToString("\n"),
            )
        }

        if (isPermissionListShown && permissionNames.isNotEmpty()) {
            PermissionListDialog(permissionNames) { isPermissionListShown = false }
        }
    }
}

@Composable
private fun sdkVersionText(sdkVersion: Int): String {
    val names = stringArrayResource(R.array.file_properites_apk_sdk_version_names)
    val codeNames = stringArrayResource(R.array.file_properites_apk_sdk_version_codenames)
    return stringResource(
        R.string.file_properites_apk_sdk_version_format,
        names[sdkVersion.coerceIn(names.indices)],
        codeNames[sdkVersion.coerceIn(codeNames.indices)],
        sdkVersion,
    )
}
