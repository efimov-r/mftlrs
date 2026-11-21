/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import androidx.test.ext.junit.runners.AndroidJUnit4
import me.zhanghai.android.files.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Контекстное меню элемента списка (долгое нажатие) — пункт 27 docs/ai/file-list-checklist.md. */
@RunWith(AndroidJUnit4::class)
class FileListFileMenuTest {
    @get:Rule
    val screen = FileListScreen()

    private val sandbox get() = screen.sandbox

    private val alwaysShown
        get() = listOf(
            R.string.select,
            R.string.file_item_action_open_with,
            R.string.share,
            R.string.file_item_action_copy_path,
            R.string.file_item_action_create_shortcut,
            R.string.file_item_action_properties,
        )

    @Test
    fun file_menuHasFileActions() {
        sandbox.file("note.txt")

        screen.launch().use {
            screen.openFileMenu("note.txt")
            assertMenu(
                shown = alwaysShown + listOf(
                    R.string.cut,
                    R.string.copy,
                    R.string.delete,
                    R.string.rename,
                    R.string.file_item_action_archive,
                ),
                absent = listOf(R.string.file_item_action_extract, R.string.file_item_action_add_bookmark),
            )
        }
    }

    @Test
    fun directory_menuHasBookmark() {
        sandbox.dir("Folder")

        screen.launch().use {
            screen.openFileMenu("Folder")
            assertMenu(
                shown = alwaysShown + listOf(R.string.file_item_action_add_bookmark, R.string.file_item_action_archive),
                absent = listOf(R.string.file_item_action_extract),
            )
        }
    }

    @Test
    fun archive_menuHasExtract() {
        sandbox.zip("pack.zip", mapOf("inside.txt" to "x"))

        screen.launch().use {
            screen.openFileMenu("pack.zip")
            assertMenu(
                shown = alwaysShown + listOf(R.string.file_item_action_extract, R.string.copy),
                absent = listOf(R.string.file_item_action_add_bookmark),
            )
        }
    }

    /** Внутри архива ФС только для чтения: нет «Вырезать», «Удалить», «Переименовать», «Архивировать»; «Копировать» → «Извлечь». */
    @Test
    fun insideArchive_menuIsReadOnly() {
        sandbox.zip("pack.zip", mapOf("inside.txt" to "x"))

        screen.launch().use {
            screen.clickText("pack.zip")
            screen.openFileMenu("inside.txt")
            assertMenu(
                shown = alwaysShown + listOf(R.string.file_item_action_extract),
                absent = listOf(
                    R.string.cut,
                    R.string.copy,
                    R.string.delete,
                    R.string.rename,
                    R.string.file_item_action_archive,
                    R.string.file_item_action_add_bookmark,
                ),
            )
        }
    }

    private fun assertMenu(shown: List<Int>, absent: List<Int>) {
        shown.forEach { screen.assertShown(screen.string(it)) }
        absent.forEach { screen.assertAbsent(screen.string(it)) }
        screen.pressBack()
    }
}
