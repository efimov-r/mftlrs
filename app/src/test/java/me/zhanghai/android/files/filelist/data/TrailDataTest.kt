/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist.data

import me.zhanghai.android.files.filelist.archivePath
import me.zhanghai.android.files.filelist.linuxPath
import me.zhanghai.android.files.filelist.ui.ScrollPosition
import me.zhanghai.android.files.provider.archive.isArchivePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailDataTest {
    private val scrolled = ScrollPosition(5, 10)
    private val other = ScrollPosition(1, 0)

    private fun TrailData.paths(): List<String> = trail.map { it.toString() }

    @Test
    fun of_buildsTrailFromRoot() {
        val trail = TrailData.of(linuxPath("/a/b"))

        assertEquals(listOf("/", "/a", "/a/b"), trail.paths())
        assertEquals(linuxPath("/a/b"), trail.currentPath)
    }

    @Test
    fun navigateUp_restoresScrollPositionOnce() {
        val trail = TrailData.of(linuxPath("/a"))
            .navigateTo(scrolled, linuxPath("/a/b"))
            .navigateUp()!!

        assertEquals(linuxPath("/a"), trail.currentPath)
        assertEquals(scrolled, trail.pendingState)
        assertNull(trail.pendingState)
    }

    @Test
    fun navigateToAncestor_keepsForwardTrail() {
        val trail = TrailData.of(linuxPath("/a/b/c")).navigateTo(scrolled, linuxPath("/a"))

        assertEquals(listOf("/", "/a", "/a/b", "/a/b/c"), trail.paths())
        assertEquals(linuxPath("/a"), trail.currentPath)

        val back = trail.navigateTo(other, linuxPath("/a/b/c"))

        assertEquals(scrolled, back.pendingState)
    }

    @Test
    fun navigateToSibling_dropsForwardTrail() {
        val trail = TrailData.of(linuxPath("/a/b/c"))
            .navigateTo(scrolled, linuxPath("/a"))
            .navigateTo(other, linuxPath("/a/x"))

        assertEquals(listOf("/", "/a", "/a/x"), trail.paths())
        assertNull(trail.pendingState)
    }

    @Test
    fun navigateUp_atRoot_returnsNull() {
        assertNull(TrailData.of(linuxPath("/")).navigateUp())
    }

    @Test
    fun archivePath_trailGoesThroughArchiveParent() {
        val path = archivePath("/a/b.zip", "dir")

        val trail = TrailData.of(path)

        assertEquals(listOf("/", "/a"), trail.trail.take(2).map { it.toString() })
        assertTrue(trail.trail.drop(2).all { it.isArchivePath })
        assertEquals(4, trail.trail.size)
        assertEquals(path, trail.currentPath)
    }
}
