/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import me.zhanghai.android.files.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileListDialogLogicTest {
    private val existing = setOf("a.txt")

    private fun check(name: String, initialName: String? = null): NameInput =
        checkFileName(name, initialName) { it in existing }

    // Имя файла

    @Test
    fun fileName_valid() {
        assertEquals(NameInput.Valid("b.txt"), check("b.txt"))
    }

    @Test
    fun fileName_errors() {
        assertEquals(NameInput.Invalid(R.string.file_name_error_empty), check(""))
        assertEquals(NameInput.Invalid(R.string.file_name_error_invalid), check("a/b"))
        assertEquals(NameInput.Invalid(R.string.file_name_error_already_exists), check("a.txt"))
    }

    @Test
    fun fileName_sameAsInitial_unchanged() {
        assertEquals(NameInput.Unchanged, check("a.txt", initialName = "a.txt"))
    }

    // Путь: верный путь не проверяется — Paths.get без установленного провайдера

    @Test
    fun path_emptyAndUnchanged() {
        assertEquals(NameInput.Invalid(R.string.file_list_path_error_empty), checkPathName("", "/d"))
        assertEquals(NameInput.Unchanged, checkPathName("/d", "/d"))
    }

    // Имя архива

    @Test
    fun archiveName_oneFile_itsName() {
        assertEquals("a.txt", defaultArchiveName(listOf(file("/d/a.txt"))))
    }

    @Test
    fun archiveName_sameParent_parentName() {
        assertEquals("d", defaultArchiveName(listOf(file("/d/a"), file("/d/b"))))
    }

    @Test
    fun archiveName_differentParentsOrRoot_none() {
        assertNull(defaultArchiveName(listOf(file("/d/a"), file("/e/b"))))
        assertNull(defaultArchiveName(listOf(file("/a"), file("/b"))))
    }

    @Test
    fun archiveType_ofSetting() {
        assertEquals(CreateArchiveType.TAR_XZ, CreateArchiveType.ofSettingId(R.id.tarXzRadio))
        assertEquals(CreateArchiveType.ZIP, CreateArchiveType.ofSettingId(0))
    }

    // Подтверждение удаления

    @Test
    fun deleteMessage_one() {
        assertEquals(
            DeleteMessage.One(R.string.file_delete_message_file_format, "a"),
            deleteMessage(listOf(file("/d/a"))),
        )
        assertEquals(
            DeleteMessage.One(R.string.file_delete_message_directory_format, "sub"),
            deleteMessage(listOf(directory("/d/sub"))),
        )
    }

    @Test
    fun deleteMessage_many() {
        assertEquals(
            DeleteMessage.Many(R.plurals.file_delete_message_multiple_files_format, 2),
            deleteMessage(listOf(file("/d/a"), file("/d/b"))),
        )
        assertEquals(
            DeleteMessage.Many(R.plurals.file_delete_message_multiple_directories_format, 2),
            deleteMessage(listOf(directory("/d/x"), directory("/d/y"))),
        )
        assertEquals(
            DeleteMessage.Many(R.plurals.file_delete_message_multiple_mixed_format, 2),
            deleteMessage(listOf(file("/d/a"), directory("/d/y"))),
        )
    }
}
