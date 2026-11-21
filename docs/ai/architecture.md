# Архитектура

Как устроен код: где что лежит и как работает. Основа — upstream 1.7.5, с 2026-11-21 код развивается независимо; недоделанное помечено `TODO(mftlrs)`. Пути — от `app/src/main/java/me/zhanghai/android/files/`, если не указано иное. Строки кода и библиотеки по функциям — [features.md](features.md).

## Обзор

```
Activity / Fragment (filelist, viewer, storage, settings…)
        │ LiveData, ViewModel
        ▼
java8.nio.file.Files / Path  ← единый API для всех источников
        │
FileSystemProviders ──► linux ─► Linux syscalls (JNI)          ┐
                    ├─► root  ─► AIDL ─► процесс root/Shizuku  │ локальные
                    ├─► archive ─► libarchive (JNI)            ┘
                    ├─► document, content ─► SAF (ContentResolver)
                    └─► ftp/ftps/ftpes, sftp, smb, dav/davs ─► сетевые клиенты
        ▲
FileJobService (копирование, удаление, архивы…) и FtpServerService работают через тот же API
```

Главная идея upstream: UI не знает, откуда файл. Любой источник — это `Path` своего `FileSystemProvider`; копирование между источниками, свойства, миниатюры и FTP-сервер работают с любым `Path` без правок UI.

## Запуск приложения

- **Класса `Application` нет.** Инициализация — в `app/AppProvider.kt`: ContentProvider создаётся раньше любой Activity, сохраняет глобальный `application` и вызывает по порядку `appInitializers` из `app/AppInitializers.kt`:
  1. `disableHiddenApiChecks` — JNI `hiddenapi.c`;
  2. отладка WebView в debug;
  3. Coil (`coil/CoilInitializer.kt`);
  4. установка провайдеров ФС (`provider/FileSystemProviders.kt`), фоновая инициализация jcifs (NetBIOS, SMB1), назначение authenticator'ов сетевым клиентам;
  5. миграции настроек по `versionCode` (`app/AppUpgrader.kt`; сейчас миграций нет);
  6. принудительная инициализация LiveData на главном потоке;
  7. цвет темы и ночной режим;
  8. каналы уведомлений.
- Системные сервисы — глобальные `val` в `app/SystemServices.kt` (`packageManager`, `notificationManager`, `defaultSharedPreferences`, общий `okHttpClient`…). DI-фреймворка нет.
- Все Activity наследуют `app/AppActivity.kt`: применяет цвет темы и ночной режим до `onCreate`.

## Точки входа (AndroidManifest)

| Компонент | Зачем |
|---|---|
| `filelist.FileListActivity` | лаунчер; `VIEW` папок и архивов (список MIME в манифесте); системный выбор файла: `GET_CONTENT`, `OPEN_DOCUMENT`, `CREATE_DOCUMENT`; свои действия `VIEW_DOWNLOADS` (ярлык) |
| `fileaction.OpenFileActivity` | действие `…intent.action.OPEN_FILE` — открыть файл по пути (из ярлыков на рабочем столе; в ярлыке — полное имя класса, после переноса класса старые ярлыки не открываются) |
| `fileaction.EditFileActivity`, `OpenFileAsDialogActivity` | пункты «Изменить» и «Открыть как» в системном chooser |
| `viewer.text.TextEditorActivity` | `VIEW` для `text/*`, JSON, XML, YAML, скриптов |
| `viewer.image.ImageViewerActivity` | `VIEW` для `image/*` |
| `viewer.saveas.SaveAsActivity` | `VIEW` с `content://` и `SEND` — сохранить присланный файл |
| `storage.*Activity` | добавление и правка хранилищ и серверов (прозрачные диалоги и полноэкранные формы) |
| `ftpserver.FtpServerActivity` | экран FTP-сервера; `…MANAGE_FTP_SERVER`; настройки плитки |
| `settings.SettingsActivity` | `APPLICATION_PREFERENCES` |
| `filejob.FileJobService` | foreground service `specialUse`: все операции с файлами |
| `ftpserver.FtpServerService`, `FtpServerTileService` | FTP-сервер и плитка в шторке |
| `app.AppProvider` | инициализация (см. выше) |
| `file.FileProvider` | `content://` для отдачи файлов другим приложениям (`<applicationId>.file_provider`) |
| `rikka.shizuku.ShizukuProvider` | связь с Shizuku |

Разрешения: `MANAGE_EXTERNAL_STORAGE` (доступ ко всем файлам), `WRITE_EXTERNAL_STORAGE` до API 32, `POST_NOTIFICATIONS`, `QUERY_ALL_PACKAGES`, `REQUEST_INSTALL_PACKAGES`, `INTERNET`, `WAKE_LOCK`, `FOREGROUND_SERVICE_SPECIAL_USE`, `INSTALL_SHORTCUT`. `allowBackup="true"`, `usesCleartextTraffic="true"` (FTP, HTTP-WebDAV), `requestLegacyExternalStorage`.

## Файловый бэкенд — `provider/`

- **API:** NIO2 из бэкпорта `retrofile`: импорты `java8.nio.file.*` и `java8.nio.channels.*`, не `java.nio.*`.
- **Регистрация:** `FileSystemProviders.install()` — провайдер по умолчанию `LinuxFileSystemProvider` (схема `file`), плюс `archive`; если процесс не root — ещё `content`, `document`, `ftp`, `ftps`, `ftpes`, `sftp`, `smb`, `dav`, `davs`. Тип файла по умолчанию определяет `common/AndroidFileTypeDetector`.
- **`common/`** — общая база всех провайдеров:
  - `ByteString`, `ByteStringListPath` — путь хранится байтами, а не `String`: имена в битой кодировке не ломают операции;
  - POSIX-атрибуты: `PosixFileAttributes`, `PosixFileAttributeView`, `PosixFileMode`, `PosixUser`, `PosixGroup`;
  - копирование: `CopyOptions`, `ProgressCopyOption` (прогресс для уведомлений), `ForeignCopyMove` — копирование между разными провайдерами потоками;
  - слежение за изменениями: `PathObservable`, `LocalWatchService` (inotify), `PollingWatchService` (опрос для сети);
  - поиск: `Searchable`, `WalkFileTreeSearchable`;
  - `UserActionRequiredException` — операции нужен ответ пользователя (пароль архива, доступ SAF).
- **Типовой пакет провайдера** (`ftp`, `sftp`, `smb`, `webdav`, `archive`, `document`): `XxxFileSystemProvider` (object), `XxxFileSystem`, `XxxPath`, `XxxFileAttributes` / `…AttributeView`, `XxxCopyMove`, расширения `OpenOptionsXxx…`, `PathXxx…`. У сетевых — `client/`: `Authority` (хост, порт, логин), `Authenticator` (берёт пароль из сохранённого хранилища), `Client` (соединения и операции), `FileByteChannel` (`SeekableByteChannel` поверх сети).
- **`linux/`** — локальная ФС без `java.io.File` и разбора `ls`: `syscall/Syscall.kt` → JNI `app/src/main/jni/syscall.c` (`stat`, `open`, `readdir`, `inotify`, `getmntent`, xattr/SELinux через libselinux…). `LocalLinuxWatchService` — inotify; `media/MediaScanner.kt` — сообщает MediaStore об изменениях.
- **`root/` + `remote/` + `aidl/`** — доступ с правами root или shell:
  - `LinuxFileSystemProvider` — `RootableFileSystemProvider`: каждый вызов идёт через `callRootable` (`root/RootablePath.kt`), по настройке `ROOT_STRATEGY`: `NEVER`, `AUTOMATIC` (root, только если путь этого требует), `ALWAYS`;
  - root-провайдер передаёт вызов по AIDL (`IRemoteFileSystemProvider` и др.) в отдельный процесс; запускает его `ShizukuFileServiceLauncher`, если Shizuku доступен, иначе `LibSuFileServiceLauncher` (`su` через libsu). Таймаут — 15 с;
  - в том процессе `RootFileService.main()` ставит только `linux` и `archive` (`isRunningAsRoot`) и грузит код приложения через `createPackageContext(BuildConfig.APPLICATION_ID)`;
  - `remote/` — Parcelable-обёртки для передачи потоков, атрибутов, исключений через Binder.
- **`archive/`** — архивы через libarchive (JNI): чтение `archiver/ArchiveReader`, `ReadArchive`; запись `ArchiveWriter`, `WriteArchive` (используется `ArchiveFileJob`). Путь внутри архива — `createArchiveRootPath()`; пароль — `ArchivePasswordRequiredException` → `fileaction/ArchivePasswordDialogActivity`. Кодировка имён — настройка `ARCHIVE_FILE_NAME_ENCODING`.
- **`document/`** — деревья Storage Access Framework (SD-карты, USB, сторонние DocumentsProvider) по URI из `OPEN_DOCUMENT_TREE`; `resolver/ExternalStorageProviderHacks.kt` — обходы для системного провайдера. **`content/`** — отдельные `content://`-файлы из других приложений.
- **Сетевые клиенты:**
  - FTP, FTPS, FTPES — commons-net (`ftp/client/Protocol.kt`), активный и пассивный режим (`Mode.kt`);
  - SFTP — sshj + BouncyCastle (`sftp/client/SecurityProviderHelper.kt` подменяет урезанный BC Android'а); `net/schmizz/sshj/sftp/RemoteFileAccessor.java` — доступ к внутренностям sshj;
  - SMB — smbj 0.11.5; список общих папок — dcerpc (`ServerService`); имена NetBIOS — jcifs-ng; `SmbWatchService` — уведомления об изменениях; `com/hierynomus/smbj/share/*Accessor.java` — доступ к внутренностям smbj;
  - WebDAV — dav4jvm + OkHttp, cookies — `webdav/client/MemoryCookieJar.kt`; `at/bitfire/dav4jvm/DavResourceAccessor.java`.

## Хранилища — `storage/`

- `Storage.kt` — абстрактный Parcelable: `id`, `customName`, `path`, `linuxPath`, `iconRes`, `isVisible`, интенты открытия и правки. Наследники: `DeviceStorage` (`FileSystemRoot` — `/`, `PrimaryStorageVolume` — внутренняя память), `DocumentTree` (SAF), `ExternalStorageShortcut`, `FtpServer`, `SftpServer`, `SmbServer`, `WebDavServer` — серверы вместе с паролями.
- Список хранится в `Settings.STORAGES`; правки — `Storages.kt` (add, replace, move, remove).
- Экраны: `StorageListActivity` (порядок перетаскиванием, advrecyclerview), `AddStorageDialogActivity` (выбор типа) → `Edit<Протокол>ServerActivity`/`Fragment`/`ViewModel`.
- `*ServerAuthenticator.kt` — отдают сетевым клиентам пароль по `Authority` из сохранённых хранилищ.
- Поиск SMB в сети — `LanSmbServerListLiveData`: служба Computer Browser и перебор подсети в 60 потоков, результаты сливаются через Flow.
- Тома системы (SD, USB) — `StorageVolumeListLiveData` по `StorageManager`: показываются только тома, о которых сообщила система.

## Главный экран — `filelist/`

Три уровня:

- `filelist/` — `FileListActivity`, `FileListViewModel` (с `FileListUiState`, `FileListEvent`, `FileListDialog`), `FileListUiLogic.kt` (чистые расчёты), `FileListIntents.kt`, `FileListPermissions.kt`;
- `filelist/ui/` — всё на Compose: `FileListRoot`, `FileListScreen`, `FileListContent`, `FileListItems`, `FileListTopBar`, `FileListBreadcrumbs`, `FileListBottomBar`, `FileListFab`, `FileListNavigation`, `FileListDialogs`, `ExitConfirmation`, `FileItemListener`;
- `filelist/data/` — данные экрана: `FileListLiveData`, `SearchFileListLiveData`, `TrailData`/`TrailLiveData`, `BreadcrumbData`/`BreadcrumbLiveData`, `FileItemSet`, `PasteState`, `PickOptions`, `SearchState`, `CreateArchiveType`, LiveData вида и сортировки.

Снаружи `filelist/` используют только `FileListActivity` — как экран выбора папки или файла (`settings/`, `storage/`, `viewer/saveas/`). Общее, что раньше лежало в `filelist/`: расширения `Path` и `FileItem` (`name`, `toUserFriendlyString`, `isRemotePath`…), `PathObserver` — в `file/`; `FileSortOptions`, `FileViewType`, `OpenApkDefaultAction` — в `settings/`; `OpenFileActivity`, `EditFileActivity`, «Открыть как» — в `fileaction/`.

- `FileListActivity` — без фрагментов, View и разметки, MVVM; плюс контракты выбора файла и папки внутри приложения (`OpenFileContract`, `CreateFileContract`, `OpenDirectoryContract`):
  - `setContent { FileListRoot(...) }` (`filelist/ui/FileListRoot.kt`): боковая панель `FileListNavigation`, внутри неё `FileListScreen` — раскладка из слотов; отступы под системные панели — `windowInsets` из слушателя insets на `android.R.id.content` (там же флаги `layoutInStatusBar`, `layoutInNavigation`);
  - `FileListRoot` собирает `FileListViewModel.uiState` через `collectAsStateWithLifecycle`, каждая часть (`Content`, `TopBar`, `SelectionBar`, `BottomBar`) — в свой composable; действия Compose вызывают методы ViewModel напрямую, `FileItemListener` списка реализует ViewModel;
  - «Назад» — 6 `BackHandler` в `FileListRoot` в порядке очереди (срабатывает первым последний): подтверждение выхода (`FileListExitConfirmation`, выключено при выборе файла для другого приложения), вверх по крошкам, поиск, режим выбора нескольких файлов, FAB, боковая панель;
  - режим выбора нескольких файлов — флаг `FileListViewModel.isSelectionMode`, отдельно от выделения (`FileItemSet`): включается кнопкой на панели (`FileListTopBarAction.SELECT`), любым выделением (`selectFiles(…, true)`) или «Выбрать все» и держится без отметок; выключает `stopSelectionMode` (крестик, «Назад») или действие, снявшее последние отметки (`deselectAfterAction`). Флаг — в `FileListUiState.Content.isSelectionMode` (флажки у элементов) и видимость `SelectionBar`;
  - элемент списка (`FileListItems.kt`): тап — открыть, в режиме выбора — отметить; долгое нажатие и правая кнопка мыши (`contextMenuPointer`) — `DropdownMenu` у места нажатия: «Выбрать» (кроме отмеченного) и действия с файлом, а на отмеченном, когда отмечено несколько, — меню для всех выделенных (`selectionMenuItems`, пока только неактивный «Выбрано: N», `TODO(mftlrs)`). Пункты запоминаются при открытии (`shownMenuItems`): иначе после «Выбрать» закрывающееся меню меняло бы размер и съезжало;
  - в Activity — только состояния элементов Compose: `LazyGridState` списка (позицию прокрутки отдаёт ViewModel через `snapshotFlow` → `scrollPosition`, она уходит в `TrailData` при переходе), `TextFieldState` поля имени файла и флаг его фокуса; при повороте и выгрузке процесса — `onSaveInstanceState` (с ними — флаги FAB и боковой панели);
  - диалоги — на Compose в `FileListRoot` (`FileListDialogs.kt`): какой открыт — `FileListUiState.dialog`, результат — в методы ViewModel; объяснения перед запросом разрешений — `permissionDialog`, их результат получает `FileListPermissions`; «Свойства» — `FileListDialog.Properties` → `FilePropertiesDialog` (ниже, «Свойства файла»);
  - в `repeatOnLifecycle(STARTED)` Activity собирает события `FileListEvent` и выполняет их: тосты, интенты из `FileListIntents.kt` (открыть файл, поделиться, установить APK, новое окно, результат выбора), буфер обмена, терминал, ярлык;
  - `NavigationItem.Listener` — Activity передаёт действия боковой панели во ViewModel;
  - разрешения — `FileListPermissions.kt`: лаунчеры `registerForActivityResult`, проверка в `onResume` — доступ ко всем файлам, хранилище, уведомления; сочетания клавиш — `onKeyShortcut` → `FileListViewModel.onKeyShortcut`.
- `FileListViewModel` — состояние экрана `uiState` (`StateFlow<FileListUiState>`; LiveData ниже наблюдаются через `asFlow`, пока есть подписчик, `WhileSubscribed(5 с)` — иначе `FileListLiveData` перечитывал бы папку в фоне; файлы фильтрует и сортирует `updateFiles`, пункты панелей — `updateSelectionBar`, `updateBottomBar`; сами расчёты — чистые функции в `FileListUiLogic.kt`: подзаголовок, статусы списка, фильтр и сортировка, пункты панели выделения и нижней панели, изменение выделения и `PasteState` — их проверяют unit-тесты, [testing.md](testing.md)) и действия экрана: `start(intent)` — путь и режим выбора из интента, поиск с задержкой 1 с (`delay` в `viewModelScope`), `onTopBarAction`, `onSelectionAction`, `onBottomBarAction`, `onFabAction`, `openFile` (режим выбора, APK по настройке, папка или архив, иначе интент), результаты диалогов, сочетания клавиш. Задачи `FileJobService` (копирование, удаление, архив и др.) запускает сама через `application`-контекст. Источники: история (`TrailLiveData`), текущий путь, список (`FileListLiveData`) или поиск (`SearchFileListLiveData`), выделение (`FileItemSet`), буфер вырезать/копировать (`PasteState`), режим выбора (`PickOptions`), вид и сортировка — общие или для конкретной папки (`FileViewSortPathSpecificLiveData`, `settings/PathSettings.kt`).
- `FileListLiveData` читает папку в `AsyncTask.THREAD_POOL_EXECUTOR` (`loadFileItem` на каждый файл) и перечитывает её по сигналу `PathObserver`.
- Режим выбора (`PickOptions.Mode`: `OPEN_FILE`, `CREATE_FILE`, `OPEN_DIRECTORY`) — приложение работает как системный выбор файла для других приложений.

## Боковая панель — `navigation/`

- Состав — `NavigationItems.kt`, `navigationItems`: видимые хранилища → тома SD и USB (API 30+) → «Добавить хранилище» → стандартные папки (Alarms, DCIM, Documents, Download, Movies, Music, Notifications, Pictures, Podcasts, Ringtones; QQ, TIM, WeChat — только до Android 11) → закладки → меню (FTP-сервер, Настройки, О программе).
- Показывает список `FileListNavigation` (Compose, `filelist/ui/FileListNavigation.kt`) из `NavigationItemListLiveData`; действия идут в `NavigationItem.Listener` (реализует `FileListActivity`): `navigateTo`, `navigateToRoot`, `launchIntent`, `closeNavigationDrawer`. `NavigationRoot` — пункты, которые сбрасывают историю. Иконка пункта — `NavigationItem.iconRes` (vector, `painterResource`).
- Телефон — `ModalNavigationDrawer`: свайп с любого места экрана, «Назад» — `BackHandler` в `FileListRoot` (последний в очереди, срабатывает первым). Планшет в ландшафте (`Configuration.isPersistentNavigation`: `sw600dp`, ландшафт) — панель рядом с экраном, видимость — настройка `FILE_LIST_PERSISTENT_DRAWER_OPEN`, ☰ её переключает. Размеры панели — константы по замеру старой панели на телефоне; отступы для планшета не проверялись.
- **Для редизайна без drawer:** данные (`navigationItems`, `BookmarkDirectories`, `StandardDirectoriesLiveData`, `NavigationRootMapLiveData`) от drawer'а не зависят и годятся для нового экрана; привязка к drawer'у — `FileListNavigation`, `FileListUiState.isNavigationOpen`, `setNavigationOpen()` и `closeNavigationDrawer()` в `FileListActivity`.

## Операции с файлами — `filejob/`

- `FileJob.kt` — базовый класс; реализации — в `FileJobs.kt` (~2300 строк): `ArchiveFileJob`, `CopyFileJob`, `CreateFileJob`, `DeleteFileJob`, `MoveFileJob`, `InstallApkJob`, `OpenFileJob`, `RenameFileJob`, `RestoreFileSeLinuxContextJob`, `SaveFileJob`, `SetFileGroupJob`, `SetFileModeJob`, `SetFileOwnerJob`, `SetFileSeLinuxContextJob`, `WriteFileJob`.
- `FileJobService` — foreground service `specialUse` (у `dataSync` лимит 6 часов), `Executors.newCachedThreadPool()`, wake lock и Wi-Fi lock на время работы (`util/WakeWifiLock.kt`). Прогресс — только в уведомлении (`FileJobNotificationTemplate`, `util/ForegroundNotificationManager.kt`); отдельного экрана операций нет.
- Конфликты и ошибки — диалоги в прозрачных Activity (`FileJobConflictDialogActivity`, `FileJobErrorDialogActivity`), т. к. операция может идти в фоне. Если приложение в фоне — `app/BackgroundActivityStarter.kt` показывает уведомление вместо запуска Activity.
- Открыть удалённый файл или файл из архива — `OpenFileJob`: копия в кэш `open_cache`, затем `VIEW` через `FileProvider`.
- Корзины нет: `DeleteFileJob` удаляет сразу.

## Свойства файла — `fileproperties/`

`FilePropertiesDialog` (Compose, `FilePropertiesDialog.kt`; открывается с главного экрана через `FileListUiState.dialog`) с вкладками: `basic` (размер, даты, тип; у папки — обход с подсчётом), `permission` (владелец, группа, режим, SELinux-контекст — с рекурсивной сменой через `filejob`; диалоги — `FilePropertiesPermissionDialogs.kt`), `checksum` (CRC32, MD5, SHA-1, SHA-256, SHA-512, сравнение), `image` (EXIF, размеры; SVG через androidsvg), `audio`, `video` (`MediaMetadataRetriever`), `apk` (подписи, разрешения — `PermissionListDialog`).

- Набор вкладок — по файлу (`isXxxTabAvailable` в файле вкладки). Данные грузят прежние ViewModel на LiveData (`FilePropertiesFileViewModel` — общий файл для «Основного» и «Прав», у остальных — свои `…TabViewModel`), Compose — через `observeAsState`; вкладка создаёт ViewModel, когда её впервые открыли (контрольные суммы не считаются зря).
- ViewModel диалогов живут в своём хранилище на время показа (`MftlrsViewModelScope`): переживают поворот, очищаются при закрытии — у двух диалогов подряд данные не смешиваются.
- Общий вид вкладки (загрузка, ошибка, свайп для обновления, строки «подпись — значение») — `FilePropertiesTab.kt`; расчёты без ресурсов (сравнение суммы, камера, выдержка, названия битов) — `FilePropertiesLogic.kt`, их проверяют unit-тесты.

## Просмотрщики — `viewer/`

- `image/` — `ImageViewerActivity`: ViewPager по картинкам папки, PhotoView для обычных и SubsamplingScaleImageView для больших картинок, полноэкранный режим (systemuihelper, insetter), удаление.
- `text/` — `TextEditorActivity` + `TextEditorViewModel`: открыть, править, сохранить (`WriteFileJob`), подтверждение при выходе и перезагрузке.
- `saveas/` — `SaveAsActivity`: приём `content://` из других приложений и сохранение через выбор папки.

## FTP-сервер — `ftpserver/`

Apache FtpServer (`FtpServer.kt`) с файловой системой `ProviderFileSystemFactory` → `ProviderFileSystemView` → `ProviderFtpFile`: раздаёт любой `Path`, в том числе сетевой. `FtpServerService` — foreground `specialUse`, держит wake lock и Wi-Fi lock. Настройки — `res/xml/ftp_server.xml` (анонимный вход, логин, пароль, порт, домашняя папка, запись). Плитка в шторке — `FtpServerTileService`.

## Настройки — `settings/`

- `Settings.kt` — все настройки как `SettingLiveData` (`SettingLiveDatas.kt`: Boolean, String, Integer, Enum, ResourceId, ParcelValue). Ключи и значения по умолчанию — `res/values/donottranslate_prefs.xml`.
- Сложные значения (хранилища, закладки, сортировка, пути) — `ParcelValueSettingLiveData`: объект → Parcel → Base64 → строка в SharedPreferences.
- Настройки отдельных папок — `PathSettings.kt`, отдельный файл SharedPreferences.
- Экран — `SettingsPreferenceFragment` по `res/xml/settings.xml`; свои Preference — в `settings/`. Там же значения настроек `FileSortOptions`, `FileViewType`, `OpenApkDefaultAction` и редактор закладки `EditBookmarkDirectoryDialogActivity`/`Fragment` (открывается из списка закладок и боковой панели).
- Миграции при обновлении версии — `app/AppUpgrader.kt`, по `versionCode`; сейчас их нет.

## Темы — `ui/theme/`

- **Две фиксированные темы — светлая и тёмная** ([decisions.md](decisions.md)). Палитра — `values/colors_mftlrs.xml` (роли Material 3, `mftlrs_light_*`, `mftlrs_dark_*`), темы — `values/themes_mftlrs.xml`: `Theme.Mftlrs.Material3.Light`, `.Dark`, `.DayNight` (в `values-night/themes_mftlrs.xml` указывает на `.Dark`). Upstream-тема `Theme.MaterialFiles.Material3` наследует `.DayNight`, просмотрщик картинок (`…Material3.Immersive`) — `.Dark`. Compose — `MftlrsTheme` с той же палитрой ([compose.md](compose.md)).
- Манифест ссылается прямо на `Theme.MaterialFiles.Material3`, `.Material3.Translucent`, `.Material3.Immersive`; подмены темы во время работы нет.
- Светлая или тёмная — по настройке «Ночной режим» (`NightModeHelper`): как в системе, выкл., вкл., по времени, при энергосбережении.
- `color_primary_light` / `_dark` (`values/colors.xml`) — тоже палитра mftlrs: ими красятся уведомления. Вторичный текст — `color/m3_*default_color_secondary_text.xml`, переведены на `on_surface_variant` палитры. `colors_google_material3.xml` (нейтральная палитра `m3_ref_palette_*` для библиотеки) оставлен.

## Миниатюры — `coil/`

Coil 2 со своими загрузчиками: `PathAttributesFetcher` (через NIO2, сетевые файлы — по настройке `READ_REMOTE_FILES_FOR_THUMBNAIL`), `VideoFrameFetcher`, `PdfPageFetcher` (`PdfRenderer`; до API 28 — по настройке), `AppIconFetcher` (иконки APK, appiconloader). Модель — пара `Path` и `BasicFileAttributes` (`path to attributes`), не один `Path`: загрузчики ключатся по ней.

## Отчёты о падениях

- Сейчас отчётов нет: Firebase upstream вырезан. Замена — ACRA ([decisions.md](decisions.md)). Места под неё помечены `TODO(mftlrs)`:
  - `app/build.gradle` — зависимости;
  - `app/AppInitializers.kt` — инициализация. ACRA нужно запускать как можно раньше, обычно в `Application.attachBaseContext()`, а класса `Application` нет — решить при подключении;
  - `about/AboutFragment.kt` — пункт «Политика конфиденциальности» закомментирован; `privacyPolicyLayout` в разметке скрыт по умолчанию. Включить со ссылкой на свой `PRIVACY.md`.

## Нативный код и hidden API

- `app/CMakeLists.txt` собирает `libhiddenapi.so` и `libsyscall.so` с `-Wall -Werror`.
- `hiddenapi/HiddenApi.kt` отключает проверки hidden API при старте; аннотация `@RestrictedHiddenApi` помечает рефлексию к скрытым методам. Обёртки над API разных версий Android — `compat/`.

## Хранение данных

- БД нет. SharedPreferences по умолчанию (`Settings`) и отдельный файл для настроек папок (`PathSettings`).
- Пароли серверов — открытым текстом и попадают в резервные копии Android — [bugs.md](bugs.md).

## Ресурсы, переводы, метаданные

- 22 перевода в `res/values-*` (есть `values-ru`); RTL нет: `supportsRtl="false"`, раскладка всегда слева направо ([decisions.md](decisions.md), «Переводы»). Список языков в настройках (`LocalePreference`) строится из папок `values-*` через `generateLocaleConfig`. Название `app_name` = `mftlrs` — только в `values/strings.xml`, `translatable="false"`, в локалях его нет.
- Иконка — только адаптивная, `mipmap-anydpi-v26/launcher_icon.xml`: фон — цвет `launcher_icon_color`, белая папка — `mipmap-*/launcher_icon_foreground.png`, монохромный слой — вектор. Иконки ярлыков — тоже адаптивные: серый фон и векторный значок цвета `launcher_icon_color`. `launcher_icon_color` (`values/colors.xml`) — отдельный от темы цвет `#12876A` (бирюзово-зелёный); иконки не зависят от `color_primary_light`. Тот же цвет — в `art/*.svg` и `art/generate-png.sh`. Запечённых PNG нет (minSdk 26). Исходники — `art/*.svg`, выгрузка PNG переднего слоя и иконки для магазина — `art/generate-png.sh` + `deploy-png.sh` (Inkscape).
- `README.md`, `PRIVACY.md`, `SECURITY.md`, `fastlane/metadata/android/en-US/*.txt` — свои, про mftlrs, с указанием «основано на Material Files». Скриншоты и картинки fastlane — пока upstream.
- Экран «О программе» (`about/`): ссылки — на форк; пункт «Основано на» — на upstream; в «Лицензиях» (`raw/licenses.xml`) первой — запись mftlrs. Правила атрибуции — [decisions.md](decisions.md), заголовки файлов — [style.md](style.md).

## CI

`.github/workflows/android.yml`: на каждый push — `./gradlew assembleDebug lintVitalRelease` на ubuntu с JDK 21, артефакт `app-debug.apk` на 10 дней.
