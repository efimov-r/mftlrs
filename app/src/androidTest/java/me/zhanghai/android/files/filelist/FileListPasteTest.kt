/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import me.zhanghai.android.files.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Копирование, перемещение и нижняя панель вставки. Номера — пункты docs/ai/file-list-checklist.md. */
@RunWith(AndroidJUnit4::class)
class FileListPasteTest {
    @get:Rule
    val screen = FileListScreen()

    private val sandbox get() = screen.sandbox

    /** 36, 37, 38: копирование через панель выделения, вставка в другой папке. */
    @Test
    fun copy_thenPasteInOtherDirectory_copiesFile() {
        sandbox.file("doc.txt", "hello")
        sandbox.dir("Target")
        val copyingTitle = screen.string(R.string.file_list_paste_copy_title_format, 1)

        screen.launch().use {
            selectAndClick("doc.txt", R.string.copy)
            screen.assertShown(copyingTitle)
            screen.assertGone(screen.selectedTitle(1)) // выделение снято

            screen.clickText("Target")
            screen.assertShown(copyingTitle) // панель осталась в другой папке
            screen.clickDescription(screen.string(R.string.paste))
            screen.assertShown("doc.txt")
            screen.assertGone(copyingTitle)

            screen.waitUntil("Нет копии Target/doc.txt") { sandbox.exists("Target/doc.txt") }
            assertTrue("Пропал оригинал doc.txt", sandbox.exists("doc.txt"))
        }
    }

    /** 37, 38: «Вырезать» и вставка перемещают файл. */
    @Test
    fun cut_thenPaste_movesFile() {
        sandbox.file("doc.txt", "hello")
        sandbox.dir("Target")
        val movingTitle = screen.string(R.string.file_list_paste_move_title_format, 1)

        screen.launch().use {
            selectAndClick("doc.txt", R.string.cut)
            screen.assertShown(movingTitle)

            screen.clickText("Target")
            screen.clickDescription(screen.string(R.string.paste))
            screen.assertShown("doc.txt")
            screen.assertGone(movingTitle)

            screen.waitUntil("Файл не перемещён") { sandbox.exists("Target/doc.txt") && !sandbox.exists("doc.txt") }
        }
    }

    /** 39: в папке только для чтения (внутри архива) «Вставить» неактивна. */
    @Test
    fun pasteIntoArchive_isDisabled() {
        sandbox.file("doc.txt")
        sandbox.zip("pack.zip", mapOf("inside.txt" to "x"))

        screen.launch().use {
            selectAndClick("doc.txt", R.string.copy)
            screen.clickText("pack.zip")
            screen.assertShown("inside.txt")

            val paste = screen.string(R.string.paste)
            assertFalse("«$paste» активна в архиве", screen.waitFor(By.desc(paste), paste).isEnabled)
        }
    }

    /** 40: крестик на нижней панели убирает её. */
    @Test
    fun closePasteBar_hidesIt() {
        sandbox.file("doc.txt")
        val copyingTitle = screen.string(R.string.file_list_paste_copy_title_format, 1)

        screen.launch().use {
            selectAndClick("doc.txt", R.string.copy)
            screen.assertShown(copyingTitle)

            screen.clickDescription(screen.string(R.string.close))
            screen.assertGone(copyingTitle)
        }
    }

    private fun selectAndClick(name: String, actionTitle: Int) {
        screen.selectFiles(name)
        screen.clickDescription(screen.string(actionTitle))
    }
}
