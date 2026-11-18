/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import me.zhanghai.android.files.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Панель «Закрыть mftlrs?» — пункт 3 docs/ai/file-list-checklist.md. Экран открывается на корне `/`
 * (там история из одной папки, и «Назад» сразу показывает панель); корень только читается.
 */
@RunWith(AndroidJUnit4::class)
class FileListExitTest {
    @get:Rule
    val screen = FileListScreen()

    private val title get() = screen.string(R.string.file_list_exit_title, screen.string(R.string.app_name))

    @Test
    fun back_onRoot_showsPanel_cancelHidesIt() {
        screen.launch(File("/")).use { scenario ->
            screen.pressBack()
            screen.assertShown(title)

            screen.clickText(screen.string(android.R.string.cancel))
            screen.assertGone(title)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun close_finishesScreen() {
        screen.launch(File("/")).use { scenario ->
            screen.pressBack()
            screen.assertShown(title)

            screen.clickText(screen.string(R.string.close))
            screen.waitUntil("Экран не закрыт") { scenario.state == Lifecycle.State.DESTROYED }
        }
    }

    @Test
    fun minimize_movesScreenToBackground() {
        screen.launch(File("/")).use { scenario ->
            screen.pressBack()
            screen.assertShown(title)

            screen.clickText(screen.string(R.string.file_list_exit_minimize))
            screen.waitUntil("Экран не свёрнут") { scenario.state == Lifecycle.State.CREATED }
        }
    }
}
