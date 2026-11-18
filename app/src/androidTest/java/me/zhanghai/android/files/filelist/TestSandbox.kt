/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Папка с тестовыми файлами внутри собственной папки тестового приложения
 * (`Android/data/<applicationId>/files/uitest`). Создаёт и удаляет только внутри неё.
 */
class TestSandbox {
    private val appDir: File = InstrumentationRegistry.getInstrumentation().targetContext
        .getExternalFilesDir(null)!!
        .canonicalFile

    val root = File(appDir, "uitest")

    fun reset() {
        delete()
        check(root.mkdirs()) { "Не создать $root" }
    }

    fun delete() {
        if (!root.exists()) return
        check(root.canonicalFile.parentFile == appDir) { "Песочница вне папки приложения: $root" }

        // Ссылки не разворачиваются: удаляется сама ссылка, а не то, на что она указывает
        Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    fun dir(relativePath: String): File =
        resolve(relativePath).apply { check(isDirectory || mkdirs()) { "Не создать $this" } }

    fun file(relativePath: String, content: String = ""): File =
        resolve(relativePath).apply {
            parentFile!!.mkdirs()
            writeText(content)
            setLastModified(FIXED_TIME)
        }

    fun zip(relativePath: String, entries: Map<String, String>): File =
        resolve(relativePath).apply {
            parentFile!!.mkdirs()
            ZipOutputStream(outputStream()).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name).apply { time = FIXED_TIME })
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
            }
            setLastModified(FIXED_TIME)
        }

    fun exists(relativePath: String): Boolean = resolve(relativePath).exists()

    private fun resolve(relativePath: String): File {
        val file = File(root, relativePath).canonicalFile
        check(file.path.startsWith(root.canonicalPath + File.separator)) { "Путь вне песочницы: $file" }
        return file
    }

    companion object {
        private const val FIXED_TIME = 1767268800000L // 2026-01-01 12:00 UTC — одинаковые даты в списке
    }
}
