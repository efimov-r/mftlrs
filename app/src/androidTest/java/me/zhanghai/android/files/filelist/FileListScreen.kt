/*
 * Copyright (c) 2026 mftlrs contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package me.zhanghai.android.files.filelist

import android.Manifest
import android.app.Instrumentation
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.SystemClock
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File
import java.util.regex.Pattern
import java8.nio.file.Paths
import me.zhanghai.android.files.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.rules.ExternalResource

/**
 * Главный экран для тестов: запуск, поиск элементов, ожидания. Элементы с текстом ищутся по тексту,
 * иконки — по `contentDescription`; элементы без того и другого — по имени id View (в Compose — такой же
 * `testTag` при `testTagsAsResourceId`). Перед каждым тестом сбрасывает настройки и песочницу.
 * Как тесты находят элементы — docs/ai/testing.md, «Контракт для Compose».
 */
class FileListScreen : ExternalResource() {
    val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    val context: Context = instrumentation.targetContext
    val device: UiDevice = UiDevice.getInstance(instrumentation)
    val sandbox = TestSandbox()

    override fun before() {
        resetSettings()
        sandbox.reset()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // иначе экран спросит про уведомления
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    override fun after() {
        sandbox.delete()
    }

    // Ключи удаляются по одному: clear() не уведомляет SettingLiveData, и в процессе остались бы старые значения
    private fun resetSettings() {
        val names = listOf("${context.packageName}_preferences", "${context.packageName}_preferences_path")
        instrumentation.runOnMainSync {
            names.forEach { name ->
                val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                preferences.edit()
                    .apply { preferences.all.keys.forEach { remove(it) } }
                    .apply()
            }
        }
    }

    // ---- Запуск ----

    fun launch(directory: File = sandbox.root): ActivityScenario<FileListActivity> =
        ActivityScenario.launch<FileListActivity>(FileListActivity.createViewIntent(Paths.get(directory.path)))
            .also { dismissAllFilesAccessRationale() }

    /**
     * Пересоздать экран, как при повороте. После отказа в доступе ко всем файлам экран при пересоздании
     * спрашивает снова — это поведение приложения, диалог закрываем.
     */
    fun recreate(scenario: ActivityScenario<FileListActivity>) {
        scenario.recreate()
        dismissAllFilesAccessRationale()
    }

    // Доступ ко всем файлам тестовому приложению не даём — экран при каждом запуске спрашивает, отказываемся
    private fun dismissAllFilesAccessRationale() {
        val message = By.text(string(R.string.all_files_access_rationale_message))
        if (device.wait(Until.hasObject(message), TIMEOUT) != true) return

        device.pressBack()
        device.wait(Until.gone(message), TIMEOUT)
    }

    // ---- Строки ----

    fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    fun quantityString(@PluralsRes id: Int, quantity: Int): String =
        context.resources.getQuantityString(id, quantity, quantity)

    fun selectedTitle(count: Int): String = string(R.string.file_list_select_title_format, count)

    // ---- Поиск и проверки ----

    fun waitFor(selector: BySelector, description: String): UiObject2 {
        var found: UiObject2? = null
        pollUntil { device.findObject(selector).also { found = it } != null }
        assertNotNull("Нет на экране: $description. Кликабельное на экране: ${describeClickable()}", found)
        return found!!
    }

    // Compose не шлёт accessibility-события, когда подключена только UiAutomation, и кэш узлов UiAutomator не
    // сбрасывается: экран на Compose выглядит для теста застывшим. Поэтому кэш сбрасывается перед каждой проверкой
    private fun pollUntil(timeout: Long = TIMEOUT, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) instrumentation.uiAutomation.clearCache()
            if (runCatching(condition).getOrDefault(false)) return true
            if (SystemClock.uptimeMillis() >= deadline) return false
            SystemClock.sleep(POLL_INTERVAL)
        }
    }

    // Для сообщений об ошибках: класс, описание, id и текст всего кликабельного
    private fun describeClickable(): String =
        runCatching {
            device.findObjects(By.clickable(true).pkg(context.packageName)).joinToString("; ") {
                "${it.className.substringAfterLast('.')} desc=${it.contentDescription} id=${it.resourceName} text=${it.text}"
            }
        }.getOrDefault("?")

    fun waitForText(text: String): UiObject2 = waitFor(By.text(text), text)

    fun assertShown(text: String) {
        assertShown(By.text(text), text)
    }

    fun assertShown(selector: BySelector, description: String) {
        assertTrue(
            "Нет на экране: $description. Кликабельное на экране: ${describeClickable()}",
            pollUntil { device.hasObject(selector) },
        )
    }

    fun assertGone(text: String) {
        assertGone(By.text(text), text)
    }

    fun assertGone(selector: BySelector, description: String) {
        assertTrue("Не пропало с экрана: $description", pollUntil { !device.hasObject(selector) })
    }

    /** Без ожидания: вызывать, когда экран уже показан (например, после [assertShown] соседнего элемента). */
    fun assertAbsent(text: String) {
        assertAbsent(By.text(text), text)
    }

    fun assertAbsent(selector: BySelector, description: String) {
        assertFalse("Лишнее на экране: $description", device.hasObject(selector))
    }

    // ---- Действия ----

    fun clickText(text: String) {
        waitForText(text).click()
    }

    fun clickDescription(description: String) {
        waitFor(By.desc(description), description).click()
    }

    fun pressBack() {
        device.pressBack()
    }

    /**
     * «Назад» при открытом поиске. Поле поиска при раскрытии получает фокус и открывает клавиатуру, и первое
     * нажатие может только спрятать её, как у SearchView; тогда нужно второе.
     */
    fun pressBackToCloseSearch() {
        device.pressBack()
        if (pollUntil(KEYBOARD_TIMEOUT) { !device.hasObject(editText()) }) return

        device.pressBack()
    }

    /**
     * Меню ⋮ на панели. Описание кнопки — системная строка Android, а если её нет — строка AppCompat
     * (на устройстве 2026-11-18 оказалась системная). [near] — текст на той же панели: нужен, когда
     * панелей с ⋮ две (панель выделения поверх основной).
     */
    fun openOverflowMenu(near: String? = null) {
        val descriptions = listOfNotNull(
            Resources.getSystem()
                .getIdentifier("action_menu_overflow_description", "string", "android")
                .takeIf { it != 0 }
                ?.let { Resources.getSystem().getString(it) },
            context.getString(androidx.appcompat.R.string.abc_action_menu_overflow_description),
        )
        val overflow = By.desc(Pattern.compile(descriptions.joinToString("|") { Pattern.quote(it) }))
        if (near == null) {
            waitFor(overflow, "⋮ $descriptions").click()
            return
        }

        waitForText(near)
        val bar = smallestContaining(near, overflow)
        assertNotNull("Нет ⋮ рядом с «$near»", bar)
        bar!!.findObject(overflow).click()
    }

    fun openViewSortMenu() {
        clickDescription(string(R.string.file_list_action_view_sort))
    }

    /** Элемент списка целиком: самый маленький контейнер, где есть и имя, и кнопка меню ⋮. */
    fun fileRow(name: String): UiObject2 {
        var row: UiObject2? = null
        waitUntil("Нет элемента списка $name") {
            row = smallestContaining(name, MENU_BUTTON)
            row != null
        }
        return row!!
    }

    /** Тап по элементу списка, а не по другому тексту с тем же именем (например, по полю поиска). */
    fun clickFile(name: String) {
        fileRow(name).click()
    }

    fun openFileMenu(name: String) {
        fileRow(name).findObject(MENU_BUTTON).click()
    }

    /** Тап по иконке файла — выделение. */
    fun clickFileIcon(name: String) {
        fileRow(name).findObject(ICON).click()
    }

    fun clickFab() {
        waitFor(FAB, "FAB").click()
    }

    /** Поле ввода: поиск, имя в диалоге. */
    fun waitForEditText(): UiObject2 = waitFor(editText(), "поле ввода")

    // Самый маленький объект, в котором есть и текст [text] (сам или в потомках), и потомок [inner]
    private fun smallestContaining(text: String, inner: BySelector): UiObject2? =
        device.findObjects(By.hasDescendant(inner))
            .filter { it.text == text || it.hasObject(By.text(text)) }
            .minByOrNull { it.visibleBounds.width() * it.visibleBounds.height() }

    // ---- Раскладка ----

    /** Элементы идут сверху вниз в этом порядке; ждёт, пока список перестроится. */
    fun assertVerticalOrder(vararg names: String) {
        waitUntil("Порядок сверху вниз не ${names.toList()}") {
            val tops = names.map { waitForText(it).visibleBounds.top }
            tops.zipWithNext().all { (a, b) -> a < b }
        }
    }

    /** Элементы стоят в одном ряду слева направо — сетка. */
    fun assertSameRow(left: String, right: String) {
        waitUntil("$left и $right не в одном ряду") {
            val a = waitForText(left).visibleBounds
            val b = waitForText(right).visibleBounds
            a.top == b.top && a.right <= b.left
        }
    }

    fun waitUntil(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT
        while (SystemClock.uptimeMillis() < deadline) {
            if (runCatching(condition).getOrDefault(false)) return // список мог перестроиться во время проверки
            SystemClock.sleep(POLL_INTERVAL)
        }
        assertTrue(message, runCatching(condition).getOrDefault(false))
    }

    companion object {
        const val TIMEOUT = 5_000L
        private const val POLL_INTERVAL = 200L
        private const val KEYBOARD_TIMEOUT = 1_000L // за это время поиск закрывается, если клавиатуры не было

        /** Поле ввода; у поля поиска в SearchView класс — AutoCompleteTextView. Новый объект: BySelector изменяемый. */
        fun editText(): BySelector = By.clazz(Pattern.compile("android\\.widget\\.(EditText|AutoCompleteTextView)"))
        private val MENU_BUTTON: BySelector = By.res(Pattern.compile("(.+:id/)?menuButton"))
        private val ICON: BySelector = By.res(Pattern.compile("(.+:id/)?iconLayout"))
        private val FAB: BySelector = By.res(Pattern.compile("(.+:id/)?sd_main_fab"))
    }
}
