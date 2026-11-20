/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasteStateTest {
    @Test
    fun copyMore_addsToCopied() {
        val state = PasteState()

        assertTrue(state.add(true, listOf(file("/d/a"))))
        assertTrue(state.add(true, listOf(file("/d/b"))))

        assertTrue(state.copy)
        assertEquals(listOf("a", "b"), state.files.map { it.name })
    }

    @Test
    fun sameFilesAgain_noChange() {
        val state = PasteState()
        state.add(false, listOf(file("/d/a")))

        assertFalse(state.add(false, listOf(file("/d/a"))))
    }

    @Test
    fun cutAfterCopy_replacesFiles() {
        val state = PasteState()
        state.add(true, listOf(file("/d/a"), file("/d/b")))

        assertTrue(state.add(false, listOf(file("/d/a"))))

        assertFalse(state.copy)
        assertEquals(listOf("a"), state.files.map { it.name })
    }

    @Test
    fun switchModeWithNoFiles_noChange() {
        val state = PasteState()

        assertFalse(state.add(true, emptyList()))
        assertTrue(state.copy)
    }
}
