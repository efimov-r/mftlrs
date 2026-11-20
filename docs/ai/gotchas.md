# Ловушки

На чём легко ошибиться и как правильно. Новые записи — сверху. Пока записи — по чтению кода (2026-11-15), не по реальным ошибкам.

## Настройка типа архива хранит имя id из разметки
- **Что случится:** после удаления `zipRadio`, `tarXzRadio`, `sevenZRadio` выбранный тип архива молча сбрасывается на ZIP или не собирается код
- **Причина:** `Settings.CREATE_ARCHIVE_TYPE` — `ResourceIdSettingLiveData`: хранит имя ресурса (`"tarXzRadio"`) и читает через `getIdentifier`. Это были id `RadioButton` удалённой `create_archive_dialog.xml` (2026-11-20, этап 11)
- **Как правильно:** id объявлены в `res/values/ids_mftlrs.xml`, `CreateArchiveType` сопоставляет их с типами. Не удалять и не переименовывать, пока настройку не переведут на свои значения (после первого релиза — с миграцией в `app/AppUpgrader.kt`)

## Нет задачи `testDebugUnitTest`
- **Что случится:** `gradlew testDebugUnitTest` — `Task 'testDebugUnitTest' not found`
- **Причина:** AGP 9 создаёт unit-тесты только для `testBuildType`, а он у нас `uiTest` (ради UI-тестов) (обнаружено 2026-11-20, этап 10c)
- **Как правильно:** запускать `.\gradlew.bat :app:testUiTestUnitTest`; `testBuildType` не менять — на нём держатся UI-тесты

## `testTagsAsResourceId` действует только на потомков
- **Что случится:** UI-тест не находит элемент по id (`By.res("…navigation")`), хотя элемент на экране и `testTag` у него есть; в сообщении о падении кликабельные узлы без id
- **Причина:** `semantics { testTagsAsResourceId = true }` превращает `testTag` в id только внутри своего поддерева. Боковая панель `FileListNavigation` лежит снаружи `FileListScreen`, у которого флаг стоял на корне (обнаружено 2026-11-20, этап 9: все 12 тестов навигации упали)
- **Как правильно:** флаг — на самом внешнем composable в `setContent` (сейчас `FileListNavigation`); новый слой над ним — перенести флаг туда

## Размеры старой боковой панели не совпадают с `dimen` и стилем
- **Что случится:** панель на Compose по `navigation_item_*_material3` и `m3_navigation_item_*` получается сдвинутой: текст на 16 dp правее, чем было
- **Причина:** не выяснена: `NavigationListAdapter` читал атрибуты стиля `NavigationView`, и на телефоне реально было иконка в 12 dp от края, текст — 12 dp после иконки (замер 2026-11-20 на устройстве: плотность 1,75, пункт 84 px, иконка с x = 21, текст с x = 84)
- **Как правильно:** размеры `FileListNavigation` — константы по замеру на устройстве, `dimens_material3.xml` удалены. Планшетные значения (`values-sw600dp`: 32 и 24 dp) не проверены — на планшете отступы могли быть другими

## Неактивная `IconButton` в Compose: тест находит иконку, а не кнопку
- **Что случится:** `waitFor(By.desc(…)).isEnabled` у неактивной кнопки панели возвращает `true` — тест «Вставить» в архиве (`pasteIntoArchive_isDisabled`) падает, хотя кнопка серая
- **Причина:** у `IconButton(enabled = false)` описание `Icon(contentDescription = …)` не сливается в узел кнопки: в дереве accessibility остаётся отдельный некликабельный узел иконки 24 dp с этим описанием и `enabled = true` (обнаружено 2026-11-19, этап 5)
- **Как правильно:** описание — на самой кнопке (`Modifier.semantics { contentDescription = … }`), у `Icon` — `null`; так сделано в `BarIcon` (`FileListTopBar.kt`)

## UI-тест не видит изменений на Compose — устаревший кэш UiAutomator
- **Что случится:** тест ждёт текст 5 с и падает, хотя на экране он уже есть: например, результаты поиска после ввода в поле на Compose. В сообщении — старое состояние экрана. Иногда тест проходит, если в это время что-то поменялось на Views или появился тост
- **Причина:** UiAutomator кэширует узлы accessibility и сбрасывает кэш по событиям изменения окна. Compose шлёт эти события, только если включена служба accessibility (TalkBack и т. п.); одной UiAutomation ему мало, поэтому изменения Compose кэш не сбрасывают. Пока рядом были View (тулбар, подзаголовок), их события сбрасывали кэш (обнаружено 2026-11-19, этап 4a)
- **Как правильно:** в ожиданиях `FileListScreen` сбрасывать кэш перед каждой проверкой — `uiAutomation.clearCache()` (API 34+). Отлаживать такие падения через лог нельзя: на тестовом телефоне логи приложения в logcat не пишутся. Рабочий способ — временно выводить отладочную строку в `contentDescription` кликабельного элемента: её покажет сообщение о падении. mobile-mcp во время UI-тестов не трогать: его процесс на телефоне (`app_process`) держит UiAutomation, тесты падают с `UiAutomationService … already registered`; остановить — `adb shell kill <pid>`, pid — из `adb shell pgrep -l -f app_process` (`com.mobilenext.mobilecli.DeviceServer`). Если такого процесса в `adb shell ps -A` уже нет — просто перезапустить тесты (2026-11-19: все 37 упали так за 23 с, повтор зелёный)

## Compose в debug-сборке лагает — плавность проверять со сборкой R8
- **Что случится:** прокрутка списка в debug-сборке заметно дёргается, кажется регрессией после перевода на Compose
- **Причина:** Compose в debuggable-сборке без R8 в разы медленнее; RecyclerView этого почти не замечает. Замер 2026-11-19, папка из 400 файлов, 20 свайпов, `dumpsys gfxinfo`, телефон 60 Гц: Views debug — 0% рваных кадров, 90-й перцентиль 10 мс; Compose debug — 7%, 44 мс; Compose с R8 и без debuggable — 0,4%, 17 мс. Убрать `nestedScroll`-interop — не влияет
- **Как правильно:** плавность и производительность судить только по release-сборке: `installRelease` ставит её поверх debug с сохранением данных. Для замера на отдельной установке (другой пакет) выдать доступ: `adb shell appops set <пакет> MANAGE_EXTERNAL_STORAGE allow`, `adb shell pm grant <пакет> android.permission.POST_NOTIFICATIONS`

## Отступы под системные панели на главном экране
- **Что случится:** последний файл закрыт навигационной панелью, или отступ двойной, или список не уходит под панель (чек-лист 63); панель заезжает под строку состояния
- **Причина:** `WindowInsets.*` Compose здесь не подходили: родители (`PersistentDrawerLayout`, раньше `CoordinatorScrollingFrameLayout`) пересобирали insets только как `systemWindowInsets`, без типов (обнаружено 2026-11-19; с этапа 9 этих родителей нет, переход на `WindowInsets` Compose — отдельная задача, не проверялся). Флаг `layoutInStatusBar` (экран под строкой состояния) раньше ставили `DrawerLayout` и `PersistentDrawerLayout`, теперь — `FileListActivity` на `android.R.id.content` (с этапа 12; до него — фрагмент на своём `screenLayout`). Отступы берутся из `systemWindowInsets` через `OnApplyWindowInsetsListener` на `android.R.id.content` — `FrameLayout` AppCompat вокруг `ComposeView` из `setContent` → параметр `windowInsets` у `FileListRoot` и `FileListScreen`, который раздаёт их частям экрана. Слушатель на самом `ComposeView` не срабатывает, когда в его композиции есть что-то, читающее insets (вероятно, Compose ставит свой слушатель на этот же View): после сборки всего экрана в один `ComposeView` отступы стали нулевыми, панель заехала под строку состояния, тесты не попадали по её кнопкам (обнаружено 2026-11-19, этап 6). Флаг `layoutInNavigation` (список под панелью навигации) ставит `FileListActivity` там же — раньше его ставил `CoordinatorScrollingFrameLayout`
- **Как правильно:** слушатель insets — на обычном View вокруг `ComposeView`, не на нём самом; в `FileListScreen` и его частях не использовать `WindowInsets.*` Compose; отступы — только из `windowInsets` и параметров, которые раздаёт `FileListScreen` (`bottomInset` списка, отступы FAB)

## `fileListStateful` бросает, пока нет значения
- **Что случится:** `NullPointerException: <get-valueCompat>(...) must not be null` при открытии экрана
- **Причина:** `sortOptionsLiveData` присылает значение раньше `fileListLiveData`; `FileListViewModel.fileListStateful` — `valueCompat`, бросает на `null` (обнаружено UI-тестом 2026-11-19)
- **Как правильно:** в обработчиках, которые могут прийти раньше списка, читать `viewModel.fileListLiveData.value?.value`

## UI-тест с `launchActivityForResult` — белый экран и долгие таймауты
- **Что случится:** на телефоне висит белый экран (`InstrumentationActivityInvoker$EmptyActivity` пакета `.uitest.test`), экран выбора файла не появляется, каждый тест ждёт таймаута; прогон растягивается на десятки минут
- **Причина:** не выяснена (обнаружено 2026-11-18): `ActivityScenario.launchActivityForResult` запускает экран через пустую служебную активити, и на устройстве это не сработало
- **Как правильно:** режимы выбора файла (чек-лист 54–60) проверять руками. Если зависло — остановить Gradle, затем `adb shell am force-stop` и `adb uninstall` для `<applicationId>.uitest` и `<applicationId>.uitest.test`: после прерванного прогона Gradle их не удаляет

## Описание кнопки ⋮ панели — не строка AppCompat
- **Что случится:** UI-тест не находит меню экрана по `abc_action_menu_overflow_description` («Ещё»)
- **Причина:** на устройстве у кнопки описание «Другие варианты» — системная строка Android `action_menu_overflow_description`, а не строка AppCompat (обнаружено 2026-11-18)
- **Как правильно:** искать по обеим строкам — `FileListScreen.openOverflowMenu()`. У ⋮ на Compose (`FileListTopBar`) описание — строка AppCompat: системную можно достать только через `getIdentifier`, тест принимает обе ([testing.md](testing.md), «Контракт для Compose»)

## `connected*AndroidTest` удаляет приложение после прогона
- **Что случится:** после UI-тестов с устройства пропадает установленное приложение вместе с настройками, закладками и серверами
- **Причина:** Gradle после прогона удаляет APK, которые ставил для тестов. Если тестовый build type — `debug`, это та же debug-сборка, что стоит на устройстве
- **Как правильно:** тесты — только в build type `uiTest` с суффиксом `.uitest` (`testBuildType 'uiTest'` в `app/build.gradle`); не переключать `testBuildType` на `debug`. У `uiTest` свои `app_provider_authority` и `file_provider_authority` в `resValue` — без них установка рядом с debug падает с `INSTALL_FAILED_CONFLICTING_PROVIDER` ([testing.md](testing.md))

## Цвет верхней панели списка файлов не зависит от `colorAppBarSurface`
- **Что случится:** правка `colorAppBarSurface` в теме не меняет панель главного экрана
- **Причина:** главный экран на Compose (`FileListScreen`): фон панели и крошек — `colorScheme.surfaceContainer`, панель не прячется при прокрутке, панель выделения — свой фон `surfaceContainer` в `FileListTopBar` (2026-11-19, этапы 4–7)
- **Как правильно:** цвет панели менять в `filelist/ui/FileListScreen.kt` и `FileListTopBar.kt`

## `gradlew` без `JAVA_HOME`
- **Что случится:** `ERROR: JAVA_HOME is not set and no 'java' command could be found`
- **Причина:** JDK не в PATH
- **Как правильно:** подходит JDK из Android Studio: в PowerShell `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` в той же команде перед `.\gradlew.bat`

## Смена цвета иконки — в нескольких местах
- **Что случится:** иконка в приложении одного цвета, а иконка в магазине или исходники `art/` — другого
- **Причина:** цвет записан в `values/colors.xml` (`launcher_icon_color`), в `art/launcher_icon.svg` и 4 SVG ярлыков, в `art/generate-png.sh` (фон иконки для магазина); PNG для магазинов выгружаются отдельно
- **Как правильно:** поменять цвет во всех местах, затем в `art/` запустить `generate-png.sh` и `deploy-png.sh` (bash, Inkscape 1.4 в PATH; на Windows — `C:\Program Files\Inkscape\bin`). Скрипты upstream были под Inkscape 1.0 — команды `FileSave`, `EditDelete` в 1.4 не работают, скрипт переписан. Передний слой рисуется из `art/launcher_icon_foreground.svg` — при правке формы папки в `launcher_icon.svg` поправить и его. `featureGraphic.png` — сплошная заливка цветом (ImageMagick: `magick -size 1024x500 xc:<цвет>`)

## Смена `applicationId` задевает не только `build.gradle`
- **Что случится:** ярлыки в лаунчере не открываются
- **Причина:** в `res/xml/shortcuts.xml` `android:targetPackage` записан строкой. (Вторая причина — `google-services.json`, привязанный к пакету, — ушла вместе с Firebase 2026-11-15)
- **Как правильно:** менять `applicationId` вместе с `targetPackage` в `shortcuts.xml`. Authority провайдеров и `BuildConfig.APPLICATION_ID` подтянутся сами. `namespace` и Kotlin-пакет от `applicationId` не зависят — их переименование отдельный вопрос ([decisions.md](decisions.md)). Строки действий `me.zhanghai.android.files.intent.action.*` можно оставить — это просто имена

## Настройки хранятся как Parcel — классы моделей менять осторожно
- **Что случится:** после обновления у пользователя молча пропадают серверы с паролями, закладки, стандартные папки, сортировка (общая и по папкам), папка по умолчанию, домашняя папка FTP-сервера — подставляются значения по умолчанию. Ошибки нет, приложение работает
- **Причина:** `ParcelValueSettingLiveData` (`settings/SettingLiveDatas.kt`) пишет объект через `Parcel.writeValue` → байты → Base64 → строка в SharedPreferences, читает обратно `readValue(appClassLoader)`. В байтах лежат полное имя класса (`me.zhanghai.android.files.settings.FileSortOptions`) и поля подряд, без имён: порядок, типы и состав полей `Storage` и наследников, `BookmarkDirectory`, `StandardDirectorySettings`, `FileSortOptions`, `Path`-классов (у пути внутри ещё и имя класса его файловой системы) — это формат хранения. Переименовали класс, перенесли в другой пакет, добавили, убрали или переставили поле — старые байты не читаются; `getValue` ловит исключение и молча отдаёт значение по умолчанию, а при следующей записи старое значение затирается. Те же настройки отдельных папок — `settings/PathSettings.kt`, отдельный файл SharedPreferences. Формат `Parcel` ещё и не обещан стабильным между версиями Android (upstream обходил разницу в Android 13 — длину перед значением)
- **Как правильно:** до первого релиза пользователей нет — классы меняем свободно, у себя на телефоне настройки сбросятся (так было при переносе `FileSortOptions` в `settings/` 2026-11-21). После релиза — при любом таком изменении миграция в `app/AppUpgrader.kt` по `versionCode` (прочитать старые байты, записать новые) и поднять `versionCode`; примеры — удалённый `AppUpgraders.kt` в истории git. Надёжнее перейти на формат с именами полей (JSON, kotlinx.serialization) до релиза — задача в [todo.md](todo.md)

## Импорты NIO — из `java8.nio`, не из `java.nio`
- **Что случится:** код компилируется, но провайдеры не работают или типы не сходятся
- **Причина:** файловый бэкенд построен на бэкпорте `retrofile`; `java.nio.file.Path` и `java8.nio.file.Path` — разные типы
- **Как правильно:** `java8.nio.file.*`, `java8.nio.channels.*`, как в соседних файлах

## Зафиксированные версии библиотек
- **Что случится:** после «обновить всё» ломается анонимный вход SMB, FTP-сервер на старых API, тёмная тема диалога лицензий, полноэкранный просмотр
- **Причина:** часть версий зафиксирована намеренно, причины — в комментариях `app/build.gradle`
- **Как правильно:** таблица — [features.md](features.md), «Зафиксированные версии»; обновлять по одной и проверять

## Java-классы в пакетах библиотек
- **Что случится:** после обновления smbj, sshj, dav4jvm, AppCompat или Material сборка падает в `app/src/main/java/{androidx,at,com,net}/`
- **Причина:** эти классы лежат в чужих пакетах, чтобы достучаться до package-private API библиотек
- **Как правильно:** при обновлении такой библиотеки проверять её accessor-класс

## Release без ключа подписывается debug-ключом
- **Что случится:** release-APK собран и ставится, но подписан debug-ключом — такой нельзя публиковать: обновление поверх него с настоящим ключом не встанет
- **Причина:** `signing.gradle` берёт значения из `signing.properties`, затем из переменных `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, затем спрашивает в консоли (у Gradle daemon консоли нет — получается `null`). Если `storeFile` не нашёлся, `release` в `app/build.gradle` берёт `signingConfigs.debug` (mftlrs, 2026-11-19) — чтобы локальная release-сборка ставилась поверх debug: пакет и подпись те же, данные сохраняются
- **Как правильно:** для публикации — `signing.properties` и `signing.jks` в корне (они в `.gitignore`; в git и документацию не класть) и проверить подпись APK (`apksigner verify --print-certs`). Для проверки на телефоне — `installRelease` и `installDebug`; debug называется «mftlrs debug» (`app/src/debug/res/values/strings.xml`)

## `gradle/gradle-daemon-jvm.properties` не в git
- **Что случится:** у разных людей и в CI Gradle daemon работает на разных JDK
- **Причина:** файл создан задачей `updateDaemonJvm` (toolchain JDK 25, скачивание через foojay) и не закоммичен; CI ставит JDK 21
- **Как правильно:** решить, коммитить ли файл; если коммитить — проверить, что CI собирается

## Вызовы локальной ФС могут уйти в root-процесс
- **Что случится:** операция неожиданно идёт через root-процесс или падает по таймауту 15 с
- **Причина:** `LinuxFileSystemProvider` — `RootableFileSystemProvider`: при `ROOT_STRATEGY = AUTOMATIC` вызов уходит в root-процесс, если путь «требует root». Процесс поднимается через Shizuku, а без него — через `su`
- **Как правильно:** отлаживая доступ к файлам, сначала смотреть настройку стратегии root и `provider/root/RootablePath.kt`
