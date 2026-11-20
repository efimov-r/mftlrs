/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import me.zhanghai.android.files.R
import me.zhanghai.android.libarchive.Archive

// Тип архива в диалоге «Архивировать»; был CreateArchiveTypeRadioGroup с RadioButton
internal enum class CreateArchiveType(
    @IdRes val settingId: Int, // значение настройки CREATE_ARCHIVE_TYPE — id бывших RadioButton (ids_mftlrs.xml)
    @StringRes val titleRes: Int,
    val extension: String,
    val format: Int,
    val filter: Int,
    val isPasswordSupported: Boolean,
) {
    ZIP(R.id.zipRadio, R.string.file_create_archive_type_zip, "zip", Archive.FORMAT_ZIP, Archive.FILTER_NONE, true),
    TAR_XZ(R.id.tarXzRadio, R.string.file_create_archive_type_tar_xz, "tar.xz", Archive.FORMAT_TAR, Archive.FILTER_XZ, false),
    SEVEN_Z(R.id.sevenZRadio, R.string.file_create_archive_type_7z, "7z", Archive.FORMAT_7ZIP, Archive.FILTER_NONE, false);

    companion object {
        fun ofSettingId(@IdRes settingId: Int): CreateArchiveType = entries.find { it.settingId == settingId } ?: ZIP
    }
}
