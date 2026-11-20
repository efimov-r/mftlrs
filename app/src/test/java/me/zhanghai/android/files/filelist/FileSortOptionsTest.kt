/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.filelist.FileSortOptions.By
import me.zhanghai.android.files.filelist.FileSortOptions.Order
import org.junit.Assert.assertEquals
import org.junit.Test

class FileSortOptionsTest {
    private fun List<FileItem>.sortedNames(by: By, order: Order, isDirectoriesFirst: Boolean): List<String> =
        sortedWith(FileSortOptions(by, order, isDirectoriesFirst).createComparator()).map { it.name }

    @Test
    fun byName_numbersNaturally_dotFilesLast() {
        val files = listOf(file("/d/file10"), file("/d/.hidden"), file("/d/file2"), file("/d/a"))

        val names = files.sortedNames(By.NAME, Order.ASCENDING, false)

        assertEquals(listOf("a", "file2", "file10", ".hidden"), names)
    }

    @Test
    fun byName_hashFilesLast() {
        val files = listOf(file("/d/#tmp"), file("/d/z"))

        assertEquals(listOf("z", "#tmp"), files.sortedNames(By.NAME, Order.ASCENDING, false))
    }

    @Test
    fun byName_directoriesFirst() {
        val files = listOf(file("/d/a"), directory("/d/z"), file("/d/b"), directory("/d/y"))

        assertEquals(listOf("y", "z", "a", "b"), files.sortedNames(By.NAME, Order.ASCENDING, true))
        assertEquals(listOf("a", "b", "y", "z"), files.sortedNames(By.NAME, Order.ASCENDING, false))
    }

    @Test
    fun descending_keepsDirectoriesFirst() {
        val files = listOf(file("/d/a"), directory("/d/y"), file("/d/b"), directory("/d/z"))

        val names = files.sortedNames(By.NAME, Order.DESCENDING, true)

        assertEquals(listOf("z", "y", "b", "a"), names)
    }

    @Test
    fun bySize_thenByName() {
        val files = listOf(file("/d/big", size = 300), file("/d/b", size = 10), file("/d/a", size = 10))

        assertEquals(listOf("a", "b", "big"), files.sortedNames(By.SIZE, Order.ASCENDING, false))
        assertEquals(listOf("big", "b", "a"), files.sortedNames(By.SIZE, Order.DESCENDING, false))
    }

    @Test
    fun byType_extensionIgnoresCase() {
        val files = listOf(file("/d/b.TXT"), file("/d/c.apk"), file("/d/a.txt"), file("/d/noext"))

        val names = files.sortedNames(By.TYPE, Order.ASCENDING, false)

        assertEquals(listOf("noext", "c.apk", "a.txt", "b.TXT"), names)
    }

    @Test
    fun byLastModified() {
        val files = listOf(file("/d/new", lastModified = 3000), file("/d/old", lastModified = 1000))

        assertEquals(listOf("old", "new"), files.sortedNames(By.LAST_MODIFIED, Order.ASCENDING, false))
    }
}
