# UI-тесты

Автоматическая проверка главного экрана на устройстве. Нужны прежде всего для переноса на Compose ([compose-migration.md](compose-migration.md)): одни и те же тесты гоняются до и после каждого этапа. Решение — [decisions.md](decisions.md), «Главный экран на Compose».

## Как устроено

- **Где:** `app/src/androidTest/java/me/zhanghai/android/files/filelist/`:
  - `FileListScreen.kt` — JUnit-правило: перед тестом сбрасывает настройки и песочницу, выдаёт разрешение на уведомления; запуск экрана, поиск элементов, ожидания, открытие меню;
  - `TestSandbox.kt` — тестовые файлы, папки, ZIP;
  - тесты по темам: `FileListScreenTest` (папки, состояния, архив, выделение), `FileListViewSortTest`, `FileListFileMenuTest`, `FileListPasteTest`, `FileListCreateTest`, `FileListSelectionTest`, `FileListSearchTest`, `FileListExitTest`.
- **Режимы выбора файла (54–60) не автоматизированы** — проверяются руками. Запуск экрана с ожиданием результата (`ActivityScenario.launchActivityForResult`) на устройстве 2026-11-18 зависал: на экране висела пустая служебная активити AndroidX (белый экран), экран выбора не появлялся, каждый тест ждал таймаута. Владелец решил такие тесты не делать.
- **Пересоздание экрана** (пункт 64) проверяется `scenario.recreate()` — как при повороте, но без поворота телефона и без смены его настроек.
- **Чёрный ящик:** элементы ищутся UI Automator через accessibility, без Espresso и ссылок на классы экрана, поэтому тестам всё равно, на Views экран или на Compose. Строки — из ресурсов приложения (`screen.string(R.string...)`), язык устройства не важен.
- **Отдельное приложение:** build type `uiTest` (`testBuildType 'uiTest'` в `app/build.gradle`) — `applicationId` с суффиксом `.uitest`, свои authority провайдеров. Debug-сборка на устройстве, её настройки и данные не затрагиваются; Gradle после прогона удаляет только `.uitest`.
- **Песочница:** все тестовые файлы — в `Android/data/<applicationId>.uitest/files/uitest`. `TestSandbox` отказывается создавать и удалять что-либо вне неё и не разворачивает ссылки при удалении. Доступ ко всем файлам (`MANAGE_EXTERNAL_STORAGE`) тестовому приложению не выдаётся — Android сам не пустит его за пределы своих папок.
- **Запуск экрана:** `ActivityScenario.launch(FileListActivity.createViewIntent(path))`. Экран при каждом запуске спрашивает доступ ко всем файлам — правило закрывает диалог «Назад».
- **Сброс настроек:** ключи `<пакет>_preferences` и `<пакет>_preferences_path` удаляются по одному на главном потоке. `clear()` не годится: `SettingLiveData` не получает ключ и держит старое значение.

## Контракт для Compose

Как тесты находят элементы. При переносе на Compose элемент должен находиться так же, иначе тест упадёт — это ошибка переноса, а не теста.

| Элемент | Как ищется |
|---|---|
| имена файлов, заголовки, подзаголовок, пункты меню, подписи FAB, тексты диалогов | `By.text` — точный текст |
| иконки на панелях: поиск, «Вид и сортировка», «Вырезать», «Копировать», «Удалить», «Вставить», крестик | `By.desc` — описание = заголовок пункта меню (`contentDescription`) |
| кнопка ⋮ экрана | `By.desc` — системная строка Android `action_menu_overflow_description` («Другие варианты» на устройстве 2026-11-18), запасной вариант — `abc_action_menu_overflow_description` AppCompat |
| иконка файла (тап — выделить) | id `iconLayout` (`FileListScreen.clickFileIcon`). В Compose — `testTag("iconLayout")` и `testTagsAsResourceId = true` |
| кнопка ⋮ у файла | id `menuButton` внутри самого маленького контейнера, где есть и имя файла, и эта кнопка. В Compose — `Modifier.testTag("menuButton")` и `testTagsAsResourceId = true` |
| главная кнопка FAB | id `sd_main_fab` (из библиотеки speed-dial). В Compose — `testTag("sd_main_fab")` |
| поле имени в диалоге, поле поиска | класс `android.widget.EditText` или `android.widget.AutoCompleteTextView` (так называется поле SearchView) — `FileListScreen.editText()` |
| элемент списка, когда то же имя есть в другом месте (поле поиска) | `clickFile` — контейнер с именем и кнопкой ⋮, как у меню файла |
| сетка или список | по координатам: в сетке два файла в одном ряду, в списке — друг под другом |
| порядок сортировки | по координатам сверху вниз |

У кнопки ⋮ файла и у FAB сейчас нет `contentDescription`, поэтому они ищутся по id. Если появится описание (для TalkBack), тесты можно перевести на `By.desc`.

## Запуск

```
.\gradlew.bat connectedUiTestAndroidTest
.\gradlew.bat connectedUiTestAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=me.zhanghai.android.files.filelist.FileListPasteTest"
.\gradlew.bat connectedUiTestAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=me.zhanghai.android.files.filelist.FileListPasteTest#cut_thenPaste_movesFile"
```

- Нужен подключённый по USB телефон с отладкой и **разблокированным экраном**. Эмулятор не используется.
- Отчёт: `app/build/reports/androidTests/connected/uiTest/index.html`, XML — `app/build/outputs/androidTest-results/connected/uiTest/`. При «Нет на экране» в сообщении — список всего кликабельного на экране (класс, описание, id, текст).
- Только собрать, без устройства: `.\gradlew.bat assembleUiTest assembleUiTestAndroidTest`.
- Несколько классов через запятую в `class=` 2026-11-18 запустили только первый класс — запускать по одному классу или весь набор.

## Что покрыто

Номера — пункты [file-list-checklist.md](file-list-checklist.md).

| Класс | Тесты | Пункты |
|---|---|---|
| `FileListScreenTest` | файлы и подзаголовок; пустая папка; вход в папку и «Назад»; ZIP как папка; долгое нажатие и «Назад» | 2, 3 (без панели выхода), 9, 11, 29, 34 |
| `FileListViewSortTest` | сетка и список; по размеру и по убыванию; «Сначала папки»; скрытые файлы | 17, 18 (без галочек в меню), 20 |
| `FileListFileMenuTest` | меню файла, папки, архива, файла внутри архива | 27 |
| `FileListPasteTest` | копирование и вставка; перемещение; «Вставить» неактивна в архиве; крестик панели | 36 (частично), 37, 38, 39, 40 |
| `FileListCreateTest` | FAB → папка; FAB → файл; существующее имя; «Назад» закрывает FAB | 45, 46, 47 |
| `FileListSelectionTest` | тап и долгое нажатие в режиме выделения; тап по иконке; «Выбрать все» из меню и с панели; пункты панели для архивов, смешанного выделения и внутри архива; пересоздание; копирование нескольких | 30–33, 35, 36, 64 |
| `FileListSearchTest` | результаты из подпапок; «Назад» закрывает поиск; переход в папку из результатов; пересоздание | 48–52, 64 |
| `FileListExitTest` | панель «Закрыть mftlrs?» на корне `/`: «Отмена», «Закрыть», «Свернуть» | 3 |

Прогон 2026-11-18 на текущей сборке (Views): 37 из 37, ~4,5 мин.

Замечено по ходу (поведение приложения, не теста): если в доступе ко всем файлам отказали, экран спрашивает его снова при каждом пересоздании (повороте) — `FileListScreen.recreate()` закрывает этот диалог. Что тесты ловят поломку, проверено: тест с испорченным ожиданием упал с понятным сообщением.

## Как писать новые

- Один тест — один пункт или пара связанных пунктов чек-листа; номер — в KDoc теста или класса.
- Файлы — только через `sandbox.dir()` / `file()` / `zip()`; даты фиксированы (`FIXED_TIME`), чтобы список не зависел от дня прогона.
- Ожидания — `assertShown`, `assertGone`, `waitFor`, `waitUntil` с таймаутом, без `Thread.sleep`. `assertAbsent` — без ожидания: только после того, как экран уже показан.
- Новый способ найти элемент — сразу в таблицу «Контракт для Compose».
- Не уходить «Назад» выше папки песочницы на корне истории — там панель «Закрыть mftlrs?». Тест панели открывает корень `/` и только читает его.
- Не использовать `ActivityScenario.launchActivityForResult` — см. «Как устроено», режимы выбора.
