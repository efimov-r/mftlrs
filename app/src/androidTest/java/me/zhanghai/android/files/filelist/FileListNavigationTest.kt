/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.zhanghai.android.files.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Навигация (сейчас — боковая панель): куда из неё можно попасть. Как она открывается, знает только
 * [FileListScreen.openNavigation]. Номера — пункты docs/ai/file-list-checklist.md; 72 (планшет) — руками.
 */
@RunWith(AndroidJUnit4::class)
class FileListNavigationTest {
    @get:Rule
    val screen = FileListScreen()

    private val sandbox get() = screen.sandbox

    private val exitTitle get() = screen.string(R.string.file_list_exit_title, screen.string(R.string.app_name))

    /** 71: ☰ открывает навигацию, «Назад» закрывает её и остаётся в той же папке. */
    @Test
    fun menuButton_opensNavigation_backClosesIt() {
        sandbox.file("Sub/inner.txt")
        sandbox.file("outer.txt")

        screen.launch().use {
            screen.clickText("Sub")
            screen.assertShown("inner.txt")

            screen.openNavigation()
            screen.pressBack()
            screen.assertNavigationClosed()
            screen.assertShown("inner.txt")
            screen.assertAbsent("outer.txt") // «Назад» не ушло на уровень выше
        }
    }

    /** 71: свайп вправо из середины экрана открывает навигацию. */
    @Test
    fun swipeRight_opensNavigation() {
        sandbox.file("alpha.txt")

        screen.launch().use {
            screen.assertShown("alpha.txt")

            screen.swipeOpenNavigation()
            screen.navigationItem(screen.string(R.string.navigation_settings))
        }
    }

    /** 66: хранилище «Корень» — переход в корень, история сброшена: «Назад» сразу показывает панель выхода. */
    @Test
    fun storage_navigatesToRoot_resetsHistory() {
        sandbox.file("Sub/inner.txt")

        screen.launch().use {
            screen.clickText("Sub")
            screen.assertShown("inner.txt")

            screen.openNavigation()
            screen.navigationItem(screen.string(R.string.storage_file_system_root_title)).click()
            screen.assertNavigationClosed()
            screen.assertGone("inner.txt")

            screen.pressBack()
            screen.assertShown(exitTitle)
        }
    }

    /** 66: «Добавить хранилище…» — выбор типа хранилища. */
    @Test
    fun addStorage_opensStorageTypes() {
        screen.launch().use {
            screen.openNavigation()
            screen.navigationItem(screen.string(R.string.navigation_add_storage)).click()

            screen.assertShown(screen.string(R.string.storage_add_storage_title))
            screen.assertShown(screen.string(R.string.storage_add_storage_smb_server))
            screen.pressBack()
            screen.assertGone(screen.string(R.string.storage_add_storage_title))
        }
    }

    /** 67: стандартная папка «Загрузки» — переход; в крошках появляется её имя. */
    @Test
    fun standardDirectory_navigatesToIt() {
        sandbox.file("alpha.txt")

        screen.launch().use {
            screen.assertShown("alpha.txt")
            screen.assertAbsent(Environment.DIRECTORY_DOWNLOADS)

            screen.openNavigation()
            screen.navigationItem(screen.string(R.string.navigation_standard_directory_downloads)).click()
            screen.assertNavigationClosed()
            screen.assertShown(Environment.DIRECTORY_DOWNLOADS)
            screen.assertGone("alpha.txt")
        }
    }

    /** 67, 53: папка, добавленная в закладки из меню экрана, появляется в навигации; тап — переход. */
    @Test
    fun bookmark_navigatesToIt() {
        sandbox.file("Marked/inner.txt")
        sandbox.file("outer.txt")

        screen.launch().use {
            addBookmark("Marked")
            screen.assertShown("outer.txt")

            screen.openNavigation()
            screen.navigationItem("Marked").click()
            screen.assertNavigationClosed()
            screen.assertShown("inner.txt")
            screen.assertGone("outer.txt")
        }
    }

    /** 68: FTP-сервер. */
    @Test
    fun ftpServer_opens() {
        assertMenuItemOpens(R.string.navigation_ftp_server, R.string.ftp_server_state_title)
    }

    /** 68: Настройки. */
    @Test
    fun settings_opens() {
        assertMenuItemOpens(R.string.navigation_settings, R.string.settings_interface_title)
    }

    /** 68: О программе. */
    @Test
    fun about_opens() {
        assertMenuItemOpens(R.string.navigation_about, R.string.about_version_title)
    }

    /** 69: текущая папка подсвечена, другие — нет; после перехода подсветка снимается. */
    @Test
    fun currentDirectory_isChecked() {
        sandbox.file("Marked/inner.txt")
        val root = screen.string(R.string.storage_file_system_root_title)

        screen.launch().use {
            addBookmark("Marked")
            screen.clickText("Marked")
            screen.assertShown("inner.txt")

            screen.openNavigation()
            assertTrue("«Marked» не подсвечена", screen.isNavigationItemChecked("Marked"))
            assertFalse("«$root» подсвечен", screen.isNavigationItemChecked(root))

            screen.pressBack()
            screen.assertNavigationClosed()
            screen.pressBack()
            screen.assertGone("inner.txt")
            screen.openNavigation()
            screen.waitUntil("«Marked» подсвечена вне неё") { !screen.isNavigationItemChecked("Marked") }
        }
    }

    /** 73: долгое нажатие на закладку — диалог правки закладки. */
    @Test
    fun bookmark_longClick_opensEditDialog() {
        sandbox.dir("Marked")

        screen.launch().use {
            addBookmark("Marked", inside = screen.string(R.string.file_list_empty))

            screen.openNavigation()
            screen.navigationItem("Marked").longClick()
            screen.assertShown(screen.string(R.string.navigation_edit_bookmark_directory_title))
            screen.clickText(screen.string(android.R.string.cancel)) // «Назад» сначала прячет клавиатуру поля имени
            screen.assertGone(screen.string(R.string.navigation_edit_bookmark_directory_title))
        }
    }

    /** 73: долгое нажатие на стандартную папку — экран «Стандартные папки». */
    @Test
    fun standardDirectory_longClick_opensSettings() {
        val title = screen.string(R.string.settings_standard_directory_list_title)

        screen.launch().use {
            screen.openNavigation()
            screen.navigationItem(screen.string(R.string.navigation_standard_directory_downloads)).longClick()
            screen.assertShown(title)
            screen.pressBack()
            screen.assertGone(title)
        }
    }

    // Зайти в папку [name] песочницы (в ней виден текст [inside]), добавить её в закладки из меню экрана и вернуться
    private fun addBookmark(name: String, inside: String = "inner.txt") {
        screen.clickText(name)
        screen.assertShown(inside)
        screen.openOverflowMenu()
        val addBookmark = screen.string(R.string.file_list_action_add_bookmark)
        screen.clickText(addBookmark)
        screen.assertGone(addBookmark) // иначе «Назад» достанется закрывающемуся меню
        screen.pressBack()
        screen.assertGone(inside) // fileRow(name) не годится: имя папки есть и в крошках
    }

    // Пункт меню навигации открывает свой экран: на нём есть [contentRes]; «Назад» возвращает к списку
    private fun assertMenuItemOpens(titleRes: Int, contentRes: Int) {
        sandbox.file("alpha.txt")

        screen.launch().use {
            screen.openNavigation()
            screen.navigationItem(screen.string(titleRes)).click()
            screen.assertShown(screen.string(contentRes))

            screen.pressBackToFileList()
            screen.assertShown("alpha.txt")
            screen.assertNavigationClosed()
        }
    }
}
