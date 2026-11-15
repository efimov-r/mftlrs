# Архитектура

Как устроен код: где что лежит и как работает. Описано состояние upstream 1.7.5 с правками форка; правки в коде помечены комментарием `mftlrs:`, недоделанное — `TODO(mftlrs)`. Пути — от `app/src/main/java/me/zhanghai/android/files/`, если не указано иное. Строки кода и библиотеки по функциям — [features.md](features.md).

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
  5. апгрейд настроек со старых версий (`app/AppUpgrader.kt`, `AppUpgraders.kt`);
  6. принудительная инициализация LiveData на главном потоке;
  7. цвет темы и ночной режим;
  8. каналы уведомлений.
- Системные сервисы — глобальные `val` в `app/SystemServices.kt` (`packageManager`, `notificationManager`, `defaultSharedPreferences`, общий `okHttpClient`…). DI-фреймворка нет.
- Все Activity наследуют `app/AppActivity.kt`: применяет цвет темы и ночной режим до `onCreate`.

## Точки входа (AndroidManifest)

| Компонент | Зачем |
|---|---|
| `filelist.FileListActivity` | лаунчер (поддержка Android TV убрана); `VIEW` папок и архивов (список MIME в манифесте); системный выбор файла: `GET_CONTENT`, `OPEN_DOCUMENT`, `CREATE_DOCUMENT`; свои действия `VIEW_DOWNLOADS` (ярлык) |
| `filelist.OpenFileActivity` | действие `…intent.action.OPEN_FILE` — открыть файл по пути (из ярлыков на рабочем столе) |
| `filelist.EditFileActivity`, `OpenFileAsDialogActivity` | пункты «Изменить» и «Открыть как» в системном chooser |
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

- `FileListActivity` — только хост для `FileListFragment` в `android.R.id.content`; плюс контракты выбора файла и папки внутри приложения (`OpenFileContract`, `CreateFileContract`, `OpenDirectoryContract`).
- `FileListFragment` (~1750 строк) — почти весь UI экрана:
  - toolbar с поиском, `BreadcrumbLayout` (хлебные крошки);
  - `RecyclerView` + `FileListAdapter`, список или сетка через `GridLayoutManager` и число колонок;
  - режимы панелей: `OverlayToolbarActionMode` — выделение, `PersistentBarLayoutToolbarActionMode` — нижняя панель вставки и выбора;
  - FAB speed-dial: новый файл и новая папка;
  - drawer: дочерний `NavigationFragment`, фрагмент реализует его `Listener`;
  - запрос разрешений в `onResume`: доступ ко всем файлам, хранилище, уведомления;
  - открытие файлов: интент с chooser, экстры для просмотрщика картинок (до 1000 путей соседних картинок), APK; ярлыки, закладки, терминал.
- `FileListViewModel` — состояние: история (`TrailLiveData`), текущий путь, список (`FileListLiveData`) или поиск (`SearchFileListLiveData`), выделение (`FileItemSet`), буфер вырезать/копировать (`PasteState`), режим выбора (`PickOptions`), вид и сортировка — общие или для конкретной папки (`FileViewSortPathSpecificLiveData`, `settings/PathSettings.kt`).
- `FileListLiveData` читает папку в `AsyncTask.THREAD_POOL_EXECUTOR` (`loadFileItem` на каждый файл) и перечитывает её по сигналу `PathObserver`.
- Разметка: `res/layout/file_list_fragment.xml` + `_include`. `file_list_fragment_include.xml` — `DrawerLayout` (телефон); `layout-sw600dp-land/` — `PersistentDrawerLayout`, панель всегда видна. Остальные части: `_app_bar_include`, `_content_include`, `_bottom_bar_include`, `_speed_dial_include`. Класс `Binding` во фрагменте собирает их в одно; `drawerLayout` и `persistentDrawerLayout` в нём nullable.
- Режим выбора (`PickOptions.Mode`: `OPEN_FILE`, `CREATE_FILE`, `OPEN_DIRECTORY`) — приложение работает как системный выбор файла для других приложений.

## Боковая панель — `navigation/`

- Состав — `NavigationItems.kt`, `navigationItems`: видимые хранилища → тома SD и USB (API 30+) → «Добавить хранилище» → стандартные папки (Alarms, DCIM, Documents, Download, Movies, Music, Notifications, Pictures, Podcasts, Ringtones; QQ, TIM, WeChat — только до Android 11) → закладки → меню (FTP-сервер, Настройки, О программе).
- `NavigationFragment` + `NavigationListAdapter` показывают список; действия идут в `NavigationFragment.Listener` (реализует `FileListFragment`): `navigateTo`, `navigateToRoot`, `launchIntent`, `closeNavigationDrawer`. `NavigationRoot` — пункты, которые сбрасывают историю.
- Свайп drawer'а с любого места — `ui/IgnoreFitsSystemWindowsFullyDraggableDrawerContentLayout.kt` (`com.drakeet.drawer`); на планшете — `ui/PersistentDrawerLayout.kt` и настройка `FILE_LIST_PERSISTENT_DRAWER_OPEN`; «назад» — `ui/DrawerLayoutOnBackPressedCallback.kt`.
- **Для редизайна без drawer:** данные (`navigationItems`, `BookmarkDirectories`, `StandardDirectoriesLiveData`, `NavigationRootMapLiveData`) от drawer'а не зависят и годятся для нового экрана; привязка к drawer'у — `NavigationFragment`, `NavigationFrameLayout`, два `file_list_fragment_include.xml`, `closeNavigationDrawer()` и `binding.drawerLayout` в `FileListFragment`.

## Операции с файлами — `filejob/`

- `FileJob.kt` — базовый класс; реализации — в `FileJobs.kt` (~2300 строк): `ArchiveFileJob`, `CopyFileJob`, `CreateFileJob`, `DeleteFileJob`, `MoveFileJob`, `InstallApkJob`, `OpenFileJob`, `RenameFileJob`, `RestoreFileSeLinuxContextJob`, `SaveFileJob`, `SetFileGroupJob`, `SetFileModeJob`, `SetFileOwnerJob`, `SetFileSeLinuxContextJob`, `WriteFileJob`.
- `FileJobService` — foreground service `specialUse` (у `dataSync` лимит 6 часов), `Executors.newCachedThreadPool()`, wake lock и Wi-Fi lock на время работы (`util/WakeWifiLock.kt`). Прогресс — только в уведомлении (`FileJobNotificationTemplate`, `util/ForegroundNotificationManager.kt`); отдельного экрана операций нет.
- Конфликты и ошибки — диалоги в прозрачных Activity (`FileJobConflictDialogActivity`, `FileJobErrorDialogActivity`), т. к. операция может идти в фоне. Если приложение в фоне — `app/BackgroundActivityStarter.kt` показывает уведомление вместо запуска Activity.
- Открыть удалённый файл или файл из архива — `OpenFileJob`: копия в кэш `open_cache`, затем `VIEW` через `FileProvider`.
- Корзины нет: `DeleteFileJob` удаляет сразу.

## Свойства файла — `fileproperties/`

`FilePropertiesDialogFragment` с вкладками: `basic` (размер, даты, тип), `permission` (владелец, группа, режим, SELinux-контекст — с рекурсивной сменой через `filejob`), `checksum` (CRC32, MD5, SHA-1, SHA-256, SHA-512, сравнение), `image` (EXIF, размеры; SVG через androidsvg), `audio`, `video` (`MediaMetadataRetriever`), `apk` (подписи, разрешения, иконка).

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
- Экран — `SettingsPreferenceFragment` по `res/xml/settings.xml`; свои Preference — в `settings/` и `theme/custom/ThemeColorPreference.kt`.
- Миграции при обновлении версии — `app/AppUpgrader.kt` и `AppUpgraders.kt`, по `versionCode`.

## Темы — `theme/`, `colorpicker/`

- `ThemeColor` — enum цветов; `CustomThemeHelper` применяет цвет и переключатель Material Design 3 (`Settings.MATERIAL_DESIGN_3`) и пересоздаёт Activity при смене; `NightModeHelper` — ночной режим и «чисто чёрный».
- Ресурсы: `res/values/themes.xml`, `themes_material3.xml`, `themes_custom.xml`, `styles*.xml`, `colors_*.xml`, `color-v31/` (динамические цвета Android 12).

## Миниатюры — `coil/`

Coil 2 со своими загрузчиками: `PathAttributesFetcher` (через NIO2, сетевые файлы — по настройке `READ_REMOTE_FILES_FOR_THUMBNAIL`), `VideoFrameFetcher`, `PdfPageFetcher` (`PdfRenderer`; до API 28 — по настройке), `AppIconFetcher` (иконки APK, appiconloader).

## Отчёты о падениях

- Firebase Analytics и Crashlytics upstream вырезаны (2026-11-15): плагины и зависимости в `app/build.gradle`, `app/google-services.json`, `nonfree/FirebaseInitializer.kt`, вызов в `app/AppInitializers.kt`, `FirebaseInitProvider` в манифесте. Сейчас отчётов нет.
- Замена — ACRA ([decisions.md](decisions.md)). Места под неё помечены `TODO(mftlrs)`:
  - `app/build.gradle` — зависимости;
  - `app/AppInitializers.kt` — инициализация. ACRA нужно запускать как можно раньше, обычно в `Application.attachBaseContext()`, а класса `Application` нет — решить при подключении;
  - `about/AboutFragment.kt` — пункт «Политика конфиденциальности» закомментирован; `privacyPolicyLayout` в разметке скрыт по умолчанию. Включить со ссылкой на свой `PRIVACY.md`.
- `PRIVACY.md` пока upstream и упоминает Firebase — переписать при ребрендинге.

## Нативный код и hidden API

- `app/CMakeLists.txt` собирает `libhiddenapi.so` и `libsyscall.so` с `-Wall -Werror`.
- `hiddenapi/HiddenApi.kt` отключает проверки hidden API при старте; аннотация `@RestrictedHiddenApi` помечает рефлексию к скрытым методам. Обёртки над API разных версий Android — `compat/`.

## Хранение данных

- БД нет. SharedPreferences по умолчанию (`Settings`) и отдельный файл для настроек папок (`PathSettings`).
- Пароли серверов лежат в `Settings.STORAGES` открытым текстом (Parcel → Base64), а `allowBackup="true"` — они попадают в резервные копии Android.

## Ресурсы, переводы, метаданные

- 33 перевода в `res/values-*` (есть `values-ru`); upstream переводит через Transifex. Название `app_name` = `mftlrs` — только в `values/strings.xml`, `translatable="false"`, в локалях его нет.
- Иконка — `mipmap-*/launcher_icon` (адаптивная: фон — цвет `color_primary_light`, белая папка — PNG, монохромный слой — вектор; для Android 6–7.1 — PNG), исходники — `art/*.svg`. Баннера Android TV нет.
- `fastlane/metadata/android/` — тексты и скриншоты для Google Play и F-Droid (оригинальные).
- `README.md`, `PRIVACY.md`, `SECURITY.md`, `fastlane/metadata/android/en-US/*.txt` — свои, про mftlrs, с указанием «основано на Material Files». Скриншоты и картинки fastlane — пока upstream.
- Экран «О программе» (`about/`): ссылки — на форк; пункт «Основано на» — на upstream; в «Лицензиях» (`raw/licenses.xml`) первой — запись mftlrs. Правила атрибуции — [decisions.md](decisions.md), заголовки файлов — [style.md](style.md).

## CI

`.github/workflows/android.yml`: на каждый push — `./gradlew assembleDebug lintVitalRelease` на ubuntu с JDK 21, артефакт `app-debug.apk` на 10 дней.
