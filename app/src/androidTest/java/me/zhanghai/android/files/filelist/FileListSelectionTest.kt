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

/** Выделение и панель выделения — пункты 30–36 docs/ai/file-list-checklist.md. */
@RunWith(AndroidJUnit4::class)
class FileListSelectionTest {
    @get:Rule
    val screen = FileListScreen()

    private val sandbox get() = screen.sandbox

    /** 30: в режиме выделения тап выделяет и снимает, долгое нажатие открывает. */
    @Test
    fun selectionMode_clickToggles_longClickOpens() {
        sandbox.file("a.txt")
        sandbox.file("b.txt")
        sandbox.file("Dir/inner.txt")

        screen.launch().use {
            screen.waitForText("a.txt").longClick()
            screen.assertShown(screen.selectedTitle(1))
            screen.clickText("b.txt")
            screen.assertShown(screen.selectedTitle(2))
            screen.clickText("a.txt")
            screen.assertShown(screen.selectedTitle(1))

            screen.waitForText("Dir").longClick()
            screen.assertShown("inner.txt")
        }
    }

    /** 31: тап по иконке выделяет файл вне режима выделения. */
    @Test
    fun clickIcon_selectsFile() {
        sandbox.file("a.txt")

        screen.launch().use {
            screen.clickFileIcon("a.txt")
            screen.assertShown(screen.selectedTitle(1))
        }
    }

    /** 32: «Выбрать все» в меню экрана. */
    @Test
    fun selectAll_fromScreenMenu() {
        sandbox.file("a.txt")
        sandbox.file("b.txt")
        sandbox.file("c.txt")

        screen.launch().use {
            screen.assertShown("c.txt")
            screen.openOverflowMenu()
            screen.clickText(screen.string(R.string.select_all))
            screen.assertShown(screen.selectedTitle(3))
        }
    }

    /** 32: «Выбрать все» на панели выделения. */
    @Test
    fun selectAll_fromSelectionBar() {
        sandbox.file("a.txt")
        sandbox.file("b.txt")
        sandbox.file("c.txt")

        screen.launch().use {
            screen.waitForText("a.txt").longClick()
            screen.openOverflowMenu(near = screen.selectedTitle(1))
            screen.clickText(screen.string(R.string.select_all))
            screen.assertShown(screen.selectedTitle(3))
        }
    }

    /** 33: все выделенные — архивы: есть «Извлечь» и «Архивировать». */
    @Test
    fun selectionBar_allArchives_hasExtract() {
        sandbox.zip("one.zip", mapOf("x.txt" to "x"))
        sandbox.zip("two.zip", mapOf("y.txt" to "y"))

        screen.launch().use {
            screen.waitForText("one.zip").longClick()
            screen.clickText("two.zip")
            screen.assertShown(screen.selectedTitle(2))
            assertBarActions(shown = listOf(R.string.cut, R.string.copy, R.string.delete))

            screen.openOverflowMenu(near = screen.selectedTitle(2))
            screen.assertShown(screen.string(R.string.file_item_action_extract))
            screen.assertShown(screen.string(R.string.file_item_action_archive))
            screen.assertShown(screen.string(R.string.share))
            screen.pressBack()
        }
    }

    /** 33: архив и обычный файл — «Извлечь» нет. */
    @Test
    fun selectionBar_mixed_hasNoExtract() {
        sandbox.zip("one.zip", mapOf("x.txt" to "x"))
        sandbox.file("plain.txt")

        screen.launch().use {
            screen.waitForText("one.zip").longClick()
            screen.clickText("plain.txt")
            screen.assertShown(screen.selectedTitle(2))

            screen.openOverflowMenu(near = screen.selectedTitle(2))
            screen.assertShown(screen.string(R.string.file_item_action_archive))
            screen.assertAbsent(screen.string(R.string.file_item_action_extract))
            screen.pressBack()
        }
    }

    /** 33: внутри архива нет «Вырезать», «Удалить», «Архивировать»; «Копировать» → «Извлечь». */
    @Test
    fun selectionBar_insideArchive_isReadOnly() {
        sandbox.zip("pack.zip", mapOf("inside.txt" to "x"))

        screen.launch().use {
            screen.clickText("pack.zip")
            screen.waitForText("inside.txt").longClick()
            screen.assertShown(screen.selectedTitle(1))
            assertBarActions(
                shown = listOf(R.string.file_item_action_extract),
                absent = listOf(R.string.cut, R.string.copy, R.string.delete),
            )

            screen.openOverflowMenu(near = screen.selectedTitle(1))
            screen.assertShown(screen.string(R.string.share))
            screen.assertAbsent(screen.string(R.string.file_item_action_archive))
            screen.pressBack()
        }
    }

    /** 35, 64: пересоздание экрана (как при повороте) сохраняет выделение. */
    @Test
    fun recreate_keepsSelection() {
        sandbox.file("a.txt")
        sandbox.file("b.txt")

        screen.launch().use { scenario ->
            screen.waitForText("a.txt").longClick()
            screen.clickText("b.txt")
            screen.assertShown(screen.selectedTitle(2))

            screen.recreate(scenario)
            screen.assertShown(screen.selectedTitle(2))
        }
    }

    /** 36: действие над несколькими выделенными снимает выделение. */
    @Test
    fun copyMultiple_clearsSelection() {
        sandbox.file("a.txt")
        sandbox.file("b.txt")

        screen.launch().use {
            screen.waitForText("a.txt").longClick()
            screen.clickText("b.txt")
            screen.assertShown(screen.selectedTitle(2))

            screen.clickDescription(screen.string(R.string.copy))
            screen.assertShown(screen.string(R.string.file_list_paste_copy_title_format, 2))
            screen.assertGone(screen.selectedTitle(2))
        }
    }

    private fun assertBarActions(shown: List<Int>, absent: List<Int> = emptyList()) {
        shown.forEach { screen.assertShown(By.desc(screen.string(it)), screen.string(it)) }
        absent.forEach { screen.assertAbsent(By.desc(screen.string(it)), screen.string(it)) }
    }
}
