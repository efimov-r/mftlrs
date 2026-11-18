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

/** Вид, сортировка, скрытые файлы. Номера — пункты docs/ai/file-list-checklist.md. */
@RunWith(AndroidJUnit4::class)
class FileListViewSortTest {
    @get:Rule
    val screen = FileListScreen()

    private val sandbox get() = screen.sandbox

    /** 17: «Сетка» ставит файлы в ряд, «Список» — друг под другом. */
    @Test
    fun grid_putsFilesInRow_listPutsThemInColumn() {
        sandbox.file("aa.txt")
        sandbox.file("bb.txt")

        screen.launch().use {
            screen.assertVerticalOrder("aa.txt", "bb.txt")

            screen.openViewSortMenu()
            screen.clickText(screen.string(R.string.file_list_action_view_grid))
            screen.assertSameRow("aa.txt", "bb.txt")

            screen.openViewSortMenu()
            screen.clickText(screen.string(R.string.file_list_action_view_list))
            screen.assertVerticalOrder("aa.txt", "bb.txt")
        }
    }

    /** 18: сортировка по размеру, затем по убыванию. */
    @Test
    fun sortBySize_thenDescending_reordersFiles() {
        sandbox.file("a.txt", "x".repeat(300))
        sandbox.file("b.txt", "x".repeat(100))
        sandbox.file("c.txt", "x".repeat(200))

        screen.launch().use {
            screen.assertVerticalOrder("a.txt", "b.txt", "c.txt") // по умолчанию — по имени

            screen.openViewSortMenu()
            screen.clickText(screen.string(R.string.file_list_action_sort_by_size))
            screen.assertVerticalOrder("b.txt", "c.txt", "a.txt")

            screen.openViewSortMenu()
            screen.clickText(screen.string(R.string.file_list_action_sort_order_ascending))
            screen.assertVerticalOrder("a.txt", "c.txt", "b.txt")
        }
    }

    /** 18: «Сначала папки» по умолчанию включено; выключение ставит папку по алфавиту. */
    @Test
    fun foldersFirst_off_sortsFoldersWithFiles() {
        sandbox.dir("zfolder")
        sandbox.file("afile.txt")

        screen.launch().use {
            screen.assertVerticalOrder("zfolder", "afile.txt")

            screen.openViewSortMenu()
            screen.clickText(screen.string(R.string.file_list_action_sort_directories_first))
            screen.assertVerticalOrder("afile.txt", "zfolder")
        }
    }

    /** 20: «Показывать скрытые» включает и выключает файлы с точкой без перезагрузки папки. */
    @Test
    fun showHiddenFiles_togglesDotFiles() {
        sandbox.file(".secret.txt")
        sandbox.file("plain.txt")
        val showHidden = screen.string(R.string.file_list_action_show_hidden_files)

        screen.launch().use {
            screen.assertShown("plain.txt")
            screen.assertAbsent(".secret.txt")

            screen.openOverflowMenu()
            screen.clickText(showHidden)
            screen.assertShown(".secret.txt")

            screen.openOverflowMenu()
            screen.clickText(showHidden)
            screen.assertGone(".secret.txt")
        }
    }
}
