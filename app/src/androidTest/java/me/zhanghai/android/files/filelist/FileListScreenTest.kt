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

/** Открытие папок, состояния списка, выделение. Номера — пункты docs/ai/file-list-checklist.md. */
@RunWith(AndroidJUnit4::class)
class FileListScreenTest {
    @get:Rule
    val screen = FileListScreen()

    private val sandbox get() = screen.sandbox

    /** 2: файлы папки и подзаголовок «N папок, M файлов». */
    @Test
    fun directory_showsFilesAndCounts() {
        sandbox.dir("Alpha")
        sandbox.dir("Beta")
        sandbox.file("gamma.txt")
        sandbox.file("delta.txt")
        sandbox.file("epsilon.txt")
        val subtitle = screen.quantityString(R.plurals.file_list_subtitle_directory_count_format, 2) +
            screen.string(R.string.file_list_subtitle_separator) +
            screen.quantityString(R.plurals.file_list_subtitle_file_count_format, 3)

        screen.launch().use {
            listOf("Alpha", "Beta", "gamma.txt", "delta.txt", "epsilon.txt").forEach { screen.assertShown(it) }
            screen.assertShown(subtitle)
        }
    }

    /** 11: пустая папка — пустое состояние и подзаголовок «Пусто». */
    @Test
    fun emptyDirectory_showsEmptyState() {
        screen.launch().use {
            screen.assertShown(screen.string(R.string.file_list_empty))
            screen.assertShown(screen.string(R.string.empty))
        }
    }

    /** 2, 3: вход в папку и «Назад» на уровень выше. */
    @Test
    fun openDirectory_thenBack_returnsToParent() {
        sandbox.file("Alpha/inner.txt")
        sandbox.file("outer.txt")

        screen.launch().use {
            screen.clickText("Alpha")
            screen.assertShown("inner.txt")
            screen.assertGone("outer.txt")

            screen.pressBack()
            screen.assertShown("outer.txt")
            screen.assertGone("inner.txt")
        }
    }

    /** 9: ZIP открывается как папка, «Назад» выходит из архива. */
    @Test
    fun openArchive_thenBack_leavesArchive() {
        sandbox.zip("pack.zip", mapOf("inside.txt" to "x"))
        sandbox.file("outer.txt")

        screen.launch().use {
            screen.clickText("pack.zip")
            screen.assertShown("inside.txt")
            screen.assertGone("outer.txt")

            screen.pressBack()
            screen.assertShown("outer.txt")
            screen.assertShown("pack.zip")
        }
    }

    /** 29, 34: долгое нажатие выделяет файл, «Назад» снимает выделение. */
    @Test
    fun longClick_selectsFile_backClearsSelection() {
        sandbox.file("alpha.txt")
        sandbox.file("beta.txt")

        screen.launch().use {
            screen.waitForText("beta.txt").longClick()
            screen.assertShown(screen.selectedTitle(1))

            screen.pressBack()
            screen.assertGone(screen.selectedTitle(1))
            screen.assertShown("alpha.txt") // осталась та же папка
        }
    }
}
