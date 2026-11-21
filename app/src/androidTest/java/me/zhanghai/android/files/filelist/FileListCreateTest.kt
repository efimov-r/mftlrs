/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import me.zhanghai.android.files.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Создание файлов и папок через «+» на верхней панели — пункты 45–47 docs/ai/file-list-checklist.md. */
@RunWith(AndroidJUnit4::class)
class FileListCreateTest {
    @get:Rule
    val screen = FileListScreen()

    private val sandbox get() = screen.sandbox

    @Test
    fun createMenu_createsDirectory() {
        sandbox.file("existing.txt")

        screen.launch().use {
            createWithMenu(R.string.file_list_action_create_directory, "NewFolder")
            screen.assertShown("NewFolder")
            screen.waitUntil("Нет папки NewFolder") { sandbox.root.resolve("NewFolder").isDirectory }
        }
    }

    @Test
    fun createMenu_createsFile() {
        sandbox.file("existing.txt")

        screen.launch().use {
            createWithMenu(R.string.file_list_action_create_file, "new.txt")
            screen.assertShown("new.txt")
            screen.waitUntil("Нет файла new.txt") { sandbox.root.resolve("new.txt").isFile }
        }
    }

    /** 46: имя существующего файла — ошибка в поле, диалог остаётся. */
    @Test
    fun existingName_showsError() {
        sandbox.dir("Existing")
        val title = screen.string(R.string.file_create_directory_title)

        screen.launch().use {
            createWithMenu(R.string.file_list_action_create_directory, "Existing")
            screen.assertShown(screen.string(R.string.file_name_error_already_exists))
            screen.assertShown(title)

            screen.clickText(screen.string(android.R.string.cancel))
            screen.assertGone(title)
        }
    }

    /** 47: «Назад» при открытом меню «+» закрывает его и не уходит из папки. */
    @Test
    fun back_closesCreateMenu() {
        sandbox.file("here.txt")
        val folder = screen.string(R.string.file_list_action_create_directory)

        screen.launch().use {
            screen.assertShown("here.txt")
            screen.openCreateMenu()
            screen.assertShown(folder)

            screen.pressBack()
            screen.assertGone(folder)
            screen.assertShown("here.txt")
        }
    }

    private fun createWithMenu(actionTitle: Int, name: String) {
        screen.openCreateMenu()
        screen.clickText(screen.string(actionTitle))
        screen.waitFor(By.clazz("android.widget.EditText"), "поле имени").text = name
        screen.clickText(screen.string(android.R.string.ok))
    }
}
