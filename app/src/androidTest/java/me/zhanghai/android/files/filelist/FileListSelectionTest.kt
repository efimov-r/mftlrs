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

/** Режим выбора и панель выделения — пункты 29–36 docs/ai/file-list-checklist.md. */
@RunWith(AndroidJUnit4::class)
class FileListSelectionTest {
    @get:Rule
    val screen = FileListScreen()

    private val sandbox get() = screen.sandbox

    /** 30: в режиме выбора тап отмечает и снимает, папку не открывает; без отметок режим остаётся. */
    @Test
    fun selectionMode_clickToggles() {
        sandbox.file("a.txt")
        sandbox.file("b.txt")
        sandbox.file("Dir/inner.txt")

        screen.launch().use {
            screen.assertShown("a.txt")
            screen.startSelectionMode()
            screen.clickFile("a.txt")
            screen.assertShown(screen.selectedTitle(1))
            screen.assertChecked("a.txt", true)
            screen.clickFile("b.txt")
            screen.assertShown(screen.selectedTitle(2))
            screen.clickFile("a.txt")
            screen.assertShown(screen.selectedTitle(1))
            screen.assertChecked("a.txt", false)
            screen.clickFile("b.txt")
            screen.assertShown(screen.selectedTitle(0))

            screen.clickFile("Dir")
            screen.assertShown(screen.selectedTitle(1))
            screen.assertChecked("Dir", true)
            screen.assertAbsent("inner.txt")
        }
    }

    /** 29: в сетке флажки тоже есть и отмечаются. */
    @Test
    fun grid_selectionMode() {
        sandbox.file("a.txt")
        sandbox.file("b.txt")

        screen.launch().use {
            screen.openViewSortMenu()
            screen.clickText(screen.string(R.string.file_list_action_view_grid))
            screen.assertSameRow("a.txt", "b.txt")

            screen.selectFiles("b.txt")
            screen.assertChecked("b.txt", true)
            screen.assertChecked("a.txt", false)
        }
    }

    /** 31: «Выбрать» в контекстном меню включает режим выбора и отмечает элемент. */
    @Test
    fun contextMenuSelect_startsSelectionMode() {
        sandbox.file("a.txt")
        sandbox.file("b.txt")

        screen.launch().use {
            screen.openFileMenu("a.txt")
            screen.clickText(screen.string(R.string.select))
            screen.assertShown(screen.selectedTitle(1))
            screen.assertChecked("a.txt", true)
            screen.assertChecked("b.txt", false)
        }
    }

    /**
     * 31: в режиме выбора долгое нажатие на единственный отмеченный — его обычное меню без «Выбрать»; на
     * неотмеченный — его меню, «Выбрать» отмечает; на один из нескольких отмеченных — меню для всех выделенных
     * (пока только неактивный заголовок «Выбрано: N»).
     */
    @Test
    fun selectionMode_longClick() {
        sandbox.file("a.txt")
        sandbox.file("b.txt")
        val openWith = screen.string(R.string.file_item_action_open_with)

        screen.launch().use {
            screen.selectFiles("a.txt")
            screen.openFileMenu("a.txt")
            screen.assertShown(openWith)
            screen.assertAbsent(screen.string(R.string.select))
            screen.pressBack()
            screen.assertGone(openWith)
            screen.assertShown(screen.selectedTitle(1)) // «Назад» закрыло меню, а не режим выбора

            screen.openFileMenu("b.txt")
            screen.clickText(screen.string(R.string.select))
            screen.assertShown(screen.selectedTitle(2))
            screen.assertGone(openWith) // меню закрылось

            screen.openFileMenu("a.txt")
            screen.assertTextCount(screen.selectedTitle(2), 2) // заголовок панели и пункт меню
            screen.assertAbsent(openWith)
            screen.pressBack()
            screen.assertTextCount(screen.selectedTitle(2), 1)
        }
    }

    /** 32: «Выбрать все» в меню экрана — режим выбора, отмечены все. */
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
            screen.assertChecked("c.txt", true)
        }
    }

    /** 32, 33: «Выбрать все» на панели выделения, в том числе когда ничего не отмечено. */
    @Test
    fun selectAll_fromSelectionBar() {
        sandbox.file("a.txt")
        sandbox.file("b.txt")
        sandbox.file("c.txt")

        screen.launch().use {
            screen.assertShown("c.txt")
            screen.startSelectionMode()
            screen.openOverflowMenu(near = screen.selectedTitle(0))
            screen.clickText(screen.string(R.string.select_all))
            screen.assertShown(screen.selectedTitle(3))
        }
    }

    /** 33: ничего не отмечено — на панели нет действий над файлами. */
    @Test
    fun selectionBar_nothingSelected_hasNoFileActions() {
        sandbox.file("a.txt")

        screen.launch().use {
            screen.assertShown("a.txt")
            screen.startSelectionMode()
            assertBarActions(shown = emptyList(), absent = listOf(R.string.cut, R.string.copy, R.string.delete))
        }
    }

    /** 33: все выделенные — архивы: есть «Извлечь» и «Архивировать». */
    @Test
    fun selectionBar_allArchives_hasExtract() {
        sandbox.zip("one.zip", mapOf("x.txt" to "x"))
        sandbox.zip("two.zip", mapOf("y.txt" to "y"))

        screen.launch().use {
            screen.selectFiles("one.zip", "two.zip")
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
            screen.selectFiles("one.zip", "plain.txt")

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
            screen.assertShown("inside.txt")
            screen.selectFiles("inside.txt")
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

    /** 35, 64: пересоздание экрана (как при повороте) сохраняет режим выбора и отметки. */
    @Test
    fun recreate_keepsSelection() {
        sandbox.file("a.txt")
        sandbox.file("b.txt")

        screen.launch().use { scenario ->
            screen.selectFiles("a.txt", "b.txt")

            screen.recreate(scenario)
            screen.assertShown(screen.selectedTitle(2))
            screen.assertChecked("a.txt", true)
        }
    }

    /** 36: действие над несколькими выделенными снимает выделение и выключает режим выбора. */
    @Test
    fun copyMultiple_clearsSelection() {
        sandbox.file("a.txt")
        sandbox.file("b.txt")

        screen.launch().use {
            screen.selectFiles("a.txt", "b.txt")

            screen.clickDescription(screen.string(R.string.copy))
            screen.assertShown(screen.string(R.string.file_list_paste_copy_title_format, 2))
            screen.assertGone(screen.selectedTitle(2))
            screen.assertGone(screen.selectedTitle(0))
            screen.assertNotCheckable("a.txt")
        }
    }

    private fun assertBarActions(shown: List<Int>, absent: List<Int> = emptyList()) {
        shown.forEach { screen.assertShown(By.desc(screen.string(it)), screen.string(it)) }
        absent.forEach { screen.assertAbsent(By.desc(screen.string(it)), screen.string(it)) }
    }
}
