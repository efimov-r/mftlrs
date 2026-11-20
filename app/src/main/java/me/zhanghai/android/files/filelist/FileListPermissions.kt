/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.compat.checkSelfPermissionCompat
import me.zhanghai.android.files.util.createManageAppAllFilesAccessPermissionIntent
import me.zhanghai.android.files.util.supportsExternalStorageManager

// Запросы доступа к файлам и уведомлениям главного экрана — перенос из FileListFragment.
// Создавать вместе с Activity: лаунчеры регистрируются до onStart
internal class FileListPermissions(
    private val activity: ComponentActivity,
    private val viewModelProducer: () -> FileListViewModel,
) {
    private val viewModel: FileListViewModel
        get() = viewModelProducer()

    private val requestAllFilesAccessLauncher = activity.registerForActivityResult(
        RequestAllFilesAccessContract(), ::onRequestAllFilesAccessResult
    )
    private val requestStoragePermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(), ::onRequestStoragePermissionResult
    )
    private val requestStoragePermissionInSettingsLauncher = activity.registerForActivityResult(
        RequestPermissionInSettingsContract(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
        ::onRequestStoragePermissionInSettingsResult
    )
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestNotificationPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(), ::onRequestNotificationPermissionResult
    )
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestNotificationPermissionInSettingsLauncher = activity.registerForActivityResult(
        RequestPermissionInSettingsContract(android.Manifest.permission.POST_NOTIFICATIONS),
        ::onRequestNotificationPermissionInSettingsResult
    )

    fun onResume() {
        if (!viewModel.isNotificationPermissionRequested) {
            ensureStorageAccess()
        }
        if (!viewModel.isStorageAccessRequested) {
            ensureNotificationPermission()
        }
    }

    // Результат FileListPermissionDialogHost
    fun onPermissionDialogResult(dialog: FileListPermissionDialog, shouldRequest: Boolean) {
        viewModel.dismissPermissionDialog()
        when (dialog) {
            FileListPermissionDialog.ALL_FILES_ACCESS ->
                onShowRequestAllFilesAccessRationaleResult(shouldRequest)
            FileListPermissionDialog.STORAGE ->
                onShowRequestStoragePermissionRationaleResult(shouldRequest)
            FileListPermissionDialog.STORAGE_IN_SETTINGS ->
                onShowRequestStoragePermissionInSettingsRationaleResult(shouldRequest)
            FileListPermissionDialog.NOTIFICATION ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    onShowRequestNotificationPermissionRationaleResult(shouldRequest)
                }
            FileListPermissionDialog.NOTIFICATION_IN_SETTINGS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    onShowRequestNotificationPermissionInSettingsRationaleResult(shouldRequest)
                }
        }
    }

    private fun ensureStorageAccess() {
        if (viewModel.isStorageAccessRequested) return

        if (Environment::class.supportsExternalStorageManager()) {
            if (!Environment.isExternalStorageManager()) {
                viewModel.showPermissionDialog(FileListPermissionDialog.ALL_FILES_ACCESS)
                viewModel.isStorageAccessRequested = true
            }
        } else if (activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            if (activity.shouldShowRequestPermissionRationale(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                viewModel.showPermissionDialog(FileListPermissionDialog.STORAGE)
            } else {
                requestStoragePermission()
            }
            viewModel.isStorageAccessRequested = true
        }
    }

    private fun onShowRequestAllFilesAccessRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestAllFilesAccessLauncher.launch(Unit)
        } else {
            viewModel.isStorageAccessRequested = false
            ensureNotificationPermission() // не результат лаунчера: onResume не будет, спросить вручную
        }
    }

    private fun onRequestAllFilesAccessResult(isGranted: Boolean) {
        viewModel.isStorageAccessRequested = false
        if (isGranted) {
            viewModel.reload()
        }
    }

    private fun onShowRequestStoragePermissionRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestStoragePermission()
        } else {
            viewModel.isStorageAccessRequested = false
        }
    }

    private fun requestStoragePermission() {
        requestStoragePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun onRequestStoragePermissionResult(isGranted: Boolean) {
        if (isGranted) {
            viewModel.isStorageAccessRequested = false
            viewModel.reload()
        } else if (activity.shouldShowRequestPermissionRationale(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            viewModel.showPermissionDialog(FileListPermissionDialog.STORAGE)
        } else {
            viewModel.showPermissionDialog(FileListPermissionDialog.STORAGE_IN_SETTINGS)
        }
    }

    private fun onShowRequestStoragePermissionInSettingsRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestStoragePermissionInSettingsLauncher.launch(Unit)
        } else {
            viewModel.isStorageAccessRequested = false
        }
    }

    private fun onRequestStoragePermissionInSettingsResult(isGranted: Boolean) {
        viewModel.isStorageAccessRequested = false
        if (isGranted) {
            viewModel.reload()
        }
    }

    private fun ensureNotificationPermission() {
        if (viewModel.isNotificationPermissionRequested) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        if (activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            if (activity.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                viewModel.showPermissionDialog(FileListPermissionDialog.NOTIFICATION)
            } else {
                requestNotificationPermission()
            }
            viewModel.isNotificationPermissionRequested = true
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onShowRequestNotificationPermissionRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestNotificationPermission()
        } else {
            viewModel.isNotificationPermissionRequested = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermission() {
        requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onRequestNotificationPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            viewModel.isNotificationPermissionRequested = false
        } else if (activity.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
            viewModel.showPermissionDialog(FileListPermissionDialog.NOTIFICATION)
        } else {
            viewModel.showPermissionDialog(FileListPermissionDialog.NOTIFICATION_IN_SETTINGS)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onShowRequestNotificationPermissionInSettingsRationaleResult(shouldRequest: Boolean) {
        if (shouldRequest) {
            requestNotificationPermissionInSettingsLauncher.launch(Unit)
        } else {
            viewModel.isNotificationPermissionRequested = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun onRequestNotificationPermissionInSettingsResult(isGranted: Boolean) {
        if (isGranted) {
            viewModel.isNotificationPermissionRequested = false
        }
    }

    private class RequestAllFilesAccessContract : ActivityResultContract<Unit, Boolean>() {
        @RequiresApi(Build.VERSION_CODES.R)
        override fun createIntent(context: Context, input: Unit): Intent =
            Environment::class.createManageAppAllFilesAccessPermissionIntent(context.packageName)

        @RequiresApi(Build.VERSION_CODES.R)
        override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
            Environment.isExternalStorageManager()
    }

    private class RequestPermissionInSettingsContract(
        private val permissionName: String,
    ) : ActivityResultContract<Unit, Boolean>() {
        override fun createIntent(context: Context, input: Unit): Intent =
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
            application.checkSelfPermissionCompat(permissionName) == PackageManager.PERMISSION_GRANTED
    }
}
