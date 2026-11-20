/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileItemSetTest {
    @Test
    fun samePath_isOneItem() {
        val set = fileItemSetOf(file("/d/a", size = 1))

        val added = set.add(file("/d/a", size = 2))

        assertFalse(added)
        assertEquals(1, set.size)
        assertTrue(file("/d/a", size = 3) in set)
    }

    @Test
    fun keepsInsertionOrder() {
        val set = fileItemSetOf(file("/d/c"), file("/d/a"), file("/d/b"))

        assertEquals(listOf("c", "a", "b"), set.map { it.name })
    }

    @Test
    fun select_addsAndRemoves() {
        val set = fileItemSetOf()

        assertTrue(set.select(listOf(file("/d/a"), file("/d/b")), true))
        assertFalse(set.select(listOf(file("/d/a")), true))
        assertTrue(set.select(listOf(file("/d/a")), false))

        assertEquals(listOf("b"), set.map { it.name })
    }

    @Test
    fun select_itself_deselectClears() {
        val set = fileItemSetOf(file("/d/a"), file("/d/b"))

        assertFalse(set.select(set, true))
        assertEquals(2, set.size)
        assertTrue(set.select(set, false))
        assertTrue(set.isEmpty())
        assertFalse(set.select(set, false))
    }
}
