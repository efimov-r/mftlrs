/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import me.zhanghai.android.files.R
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Поиск — пункты 48–52 docs/ai/file-list-checklist.md. */
@RunWith(AndroidJUnit4::class)
class FileListSearchTest {
    @get:Rule
    val screen = FileListScreen()

    private val sandbox get() = screen.sandbox

    private val viewSort get() = screen.string(R.string.file_list_action_view_sort)

    @Before
    fun createFiles() {
        sandbox.file("needle_top.txt")
        sandbox.file("Sub/needle_deep.txt")
        sandbox.file("hay.txt")
    }

    /** 48, 49: результаты из подпапок, лишнее пропадает; «Вид и сортировка» скрыт. */
    @Test
    fun search_showsMatchesFromSubdirectories() {
        screen.launch().use {
            search("needle")
            screen.assertShown("needle_deep.txt")
            screen.assertShown("needle_top.txt")
            screen.assertGone("hay.txt")
            screen.assertAbsent(By.desc(viewSort), viewSort)
        }
    }

    /** 50: «Назад» закрывает поиск — снова обычный список папки. */
    @Test
    fun back_closesSearch() {
        screen.launch().use {
            search("needle")
            screen.assertShown("needle_deep.txt")

            screen.pressBack()
            screen.assertShown("hay.txt")
            screen.assertGone("needle_deep.txt")
            screen.assertShown(By.desc(viewSort), viewSort)
        }
    }

    /** 51: тап по папке в результатах — переход в неё, поиск закрыт. */
    @Test
    fun clickDirectoryInResults_opensItAndClosesSearch() {
        screen.launch().use {
            search("Sub")
            screen.clickFile("Sub")
            screen.assertShown("needle_deep.txt")
            screen.assertGone(FileListScreen.editText(), "поле поиска")
            screen.assertShown(By.desc(viewSort), viewSort)
        }
    }

    /** 52, 64: пересоздание экрана (как при повороте) сохраняет поле и запрос. */
    @Test
    fun recreate_keepsSearch() {
        screen.launch().use { scenario ->
            search("needle")
            screen.assertGone("hay.txt")

            screen.recreate(scenario)
            screen.assertShown(FileListScreen.editText().text("needle"), "поле поиска с «needle»")
            screen.assertShown("needle_deep.txt")
            screen.assertAbsent("hay.txt")
        }
    }

    private fun search(query: String) {
        screen.clickDescription(screen.string(R.string.search))
        screen.waitForEditText().text = query
    }
}
