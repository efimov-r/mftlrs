# Экраны и компоненты UI

Перечень Activity, Fragment, диалогов, настроек, адаптеров, своих View и ресурсов разметки, по группам. Пакет везде — `me.zhanghai.android.files.<подпакет>`. Как устроены главный экран и боковая панель — [architecture.md](architecture.md); элементы на Compose — [compose.md](compose.md). Подсчёт — на 2026-11-15, обновлён после переноса главного экрана и «Свойств» на Compose (2026-11-21).

## Сводка

| Группа | Кол-во |
|---|---|
| Activity | 28 + база `AppActivity` |
| Fragment полноэкранные | 16 (2 без UI) |
| PreferenceFragment | 3 + база |
| DialogFragment | 15 + 1 база |
| Свои Preference | 15 |
| Адаптеры | 4 + 5 баз в `ui/view/` |
| Свои View и помощники в `ui/view/` | 34 + 5 на Compose в `ui/compose/` |
| Разметка `res/` | layout 29, menu 2, XML настроек 2 |

Объём: Kotlin в пакете приложения — ~59 тыс. строк; пакеты с экранами (`filelist`, `storage`, `fileproperties`, `viewer`, `settings`, `navigation`, `ftpserver`, `about`) — ~17,5 тыс. вместе с ViewModel и моделями; `ui/` — ~4,3 тыс.; `layout`, `menu`, `xml` — ~4,6 тыс.

## Activity

Все наследуют `app/AppActivity` (`AppCompatActivity`). Объявлены в `AndroidManifest.xml`.

### Экраны (14)

Тема `Theme.MaterialFiles.Material3`, у просмотра картинок — `.Immersive`.

| Activity | Пакет | Fragment внутри | Откуда открывается |
|---|---|---|---|
| `FileListActivity` | `filelist` | нет: `setContent { FileListRoot }`, Compose целиком | лаунчер; открытие папок и архивов; выбор файла для других приложений (`GET_CONTENT`, `OPEN_DOCUMENT`, `CREATE_DOCUMENT`); ярлыки |
| `TextEditorActivity` | `viewer.text` | `TextEditorFragment` | `VIEW` для `text/*`, JSON, XML и др. |
| `ImageViewerActivity` | `viewer.image` | `ImageViewerFragment` | `VIEW` для `image/*` |
| `SettingsActivity` | `settings` | `SettingsFragment` | меню; `APPLICATION_PREFERENCES` |
| `StorageListActivity` | `storage` | `StorageListFragment` | настройки |
| `StandardDirectoryListActivity` | `settings` | `StandardDirectoryListFragment` | настройки |
| `BookmarkDirectoryListActivity` | `settings` | `BookmarkDirectoryListFragment` | настройки |
| `FtpServerActivity` | `ftpserver` | `FtpServerFragment` | меню; долгое нажатие на плитку в шторке |
| `AboutActivity` | `about` | `AboutFragment` | меню |
| `AddLanSmbServerActivity` | `storage` | `AddLanSmbServerFragment` | «Добавить хранилище» → SMB |
| `EditSmbServerActivity` | `storage` | `EditSmbServerFragment` | добавление и правка SMB |
| `EditFtpServerActivity` | `storage` | `EditFtpServerFragment` | добавление и правка FTP |
| `EditSftpServerActivity` | `storage` | `EditSftpServerFragment` | добавление и правка SFTP |
| `EditWebDavServerActivity` | `storage` | `EditWebDavServerFragment` | добавление и правка WebDAV |

### Хосты диалогов (9)

Прозрачная тема `.Translucent`: Activity только показывает один `DialogFragment`. Нужны, чтобы диалог можно было открыть из уведомления, из фонового сервиса или из другой Activity.

| Activity | Пакет | Диалог |
|---|---|---|
| `FileJobConflictDialogActivity` | `filejob` | `FileJobConflictDialogFragment` |
| `FileJobErrorDialogActivity` | `filejob` | `FileJobErrorDialogFragment` |
| `ArchivePasswordDialogActivity` | `fileaction` | `ArchivePasswordDialogFragment` |
| `OpenFileAsDialogActivity` | `fileaction` | `OpenFileAsDialogFragment` |
| `AddStorageDialogActivity` | `storage` | `AddStorageDialogFragment` |
| `EditDeviceStorageDialogActivity` | `storage` | `EditDeviceStorageDialogFragment` |
| `EditDocumentTreeDialogActivity` | `storage` | `EditDocumentTreeDialogFragment` |
| `EditExternalStorageShortcutDialogActivity` | `storage` | `EditExternalStorageShortcutDialogFragment` |
| `EditBookmarkDirectoryDialogActivity` | `settings` | `EditBookmarkDirectoryDialogFragment` |

### Без UI (5)

Тема `.Translucent`: сделать действие и сразу `finish()`.

| Activity | Пакет | Что делает |
|---|---|---|
| `OpenFileActivity` | `fileaction` | открывает файл из ярлыка (`intent.action.OPEN_FILE`) |
| `EditFileActivity` | `fileaction` | передаёт файл внешнему редактору (`createEditIntent`) |
| `SaveAsActivity` | `viewer.saveas` | «Сохранить как» для `VIEW`/`SEND` от других приложений: запускает выбор места через `FileListActivity.CreateFileContract` |
| `AddDocumentTreeActivity` | `storage` | через `AddDocumentTreeFragment` открывает системный выбор папки (SAF) |
| `AddExternalStorageShortcutActivity` | `storage` | через `AddExternalStorageShortcutFragment` добавляет ярлык внешнего хранилища |

## Fragment

### Полноэкранные (16)

Наследуют `androidx.fragment.app.Fragment`, разметка через ViewBinding.

| Fragment | Пакет | Layout | Примечание |
|---|---|---|---|
| `TextEditorFragment` | `viewer.text` | `text_editor_fragment` | |
| `ImageViewerFragment` | `viewer.image` | `image_viewer_fragment` | ViewPager2 + `ImageViewerAdapter` |
| `SettingsFragment` | `settings` | `settings_fragment` | обёртка над `SettingsPreferenceFragment` |
| `StorageListFragment` | `storage` | `storage_list_fragment` | перетаскивание (`advrecyclerview`) |
| `StandardDirectoryListFragment` | `settings` | `standard_directory_list_fragment` | обёртка над `StandardDirectoryListPreferenceFragment` |
| `BookmarkDirectoryListFragment` | `settings` | `bookmark_directory_list_fragment` | перетаскивание (`advrecyclerview`) |
| `FtpServerFragment` | `ftpserver` | `ftp_server_fragment` | обёртка над `FtpServerPreferenceFragment` |
| `AboutFragment` | `about` | `about_fragment` | |
| `AddLanSmbServerFragment` | `storage` | `add_lan_smb_server_fragment` | поиск SMB в локальной сети |
| `EditSmbServerFragment` | `storage` | `edit_smb_server_fragment` | |
| `EditFtpServerFragment` | `storage` | `edit_ftp_server_fragment` | |
| `EditSftpServerFragment` | `storage` | `edit_sftp_server_fragment` | |
| `EditWebDavServerFragment` | `storage` | `edit_webdav_server_fragment` | |
| `AddDocumentTreeFragment` | `storage` | — | без UI |
| `AddExternalStorageShortcutFragment` | `storage` | — | без UI |

### Настройки — PreferenceFragment (3)

Наследуют `ui/view/PreferenceFragmentCompat` (над `preferencex` от takisoft).

| Fragment | Пакет | XML |
|---|---|---|
| `SettingsPreferenceFragment` | `settings` | `xml/settings.xml` |
| `FtpServerPreferenceFragment` | `ftpserver` | `xml/ftp_server.xml` |
| `StandardDirectoryListPreferenceFragment` | `settings` | строится в коде |

## Диалоги — DialogFragment (15)

Наследуют `AppCompatDialogFragment`, внутри — `MaterialAlertDialogBuilder`. Диалоги главного экрана и «Свойства» с их диалогами — на Compose ([compose.md](compose.md)).

### Файлы — `fileaction` (1)

| Диалог | Что делает | Layout |
|---|---|---|
| `OpenFileAsDialogFragment` | открыть как: текст, картинка и т. д.; через свою Activity | — |

### Фоновые операции (3)

Вызываются из `FileJobService` через Activity-хосты.

| Диалог | Пакет | Layout |
|---|---|---|
| `FileJobConflictDialogFragment` | `filejob` | `file_job_conflict_dialog_view` |
| `FileJobErrorDialogFragment` | `filejob` | `file_job_error_dialog_view` |
| `ArchivePasswordDialogFragment` | `fileaction` | `archive_password_dialog` |

### Хранилища и закладки (5)

Вызываются через свои Activity-хосты.

| Диалог | Пакет | Layout |
|---|---|---|
| `AddStorageDialogFragment` | `storage` | — (список вариантов) |
| `EditDeviceStorageDialogFragment` | `storage` | `edit_device_storage_dialog` |
| `EditDocumentTreeDialogFragment` | `storage` | `edit_document_tree_dialog` |
| `EditExternalStorageShortcutDialogFragment` | `storage` | `edit_external_storage_shortcut_dialog` |
| `EditBookmarkDirectoryDialogFragment` | `settings` | `edit_bookmark_directory_dialog` |

### Просмотрщики (3)

| Диалог | Пакет | Вызывает |
|---|---|---|
| `ConfirmCloseDialogFragment` | `viewer.text` | `TextEditorFragment`: закрыть без сохранения |
| `ConfirmReloadDialogFragment` | `viewer.text` | `TextEditorFragment`: перечитать с потерей правок |
| `ConfirmDeleteDialogFragment` | `viewer.image` | `ImageViewerFragment`: удалить картинку |

### Настройки и прочее (3)

| Диалог | Пакет | Где |
|---|---|---|
| `MaterialListPreferenceDialogFragmentCompat` | `ui` | диалог выбора у `ListPreference` |
| `RootStrategyPreference.DialogFragment` | `settings` | выбор режима root (вложенный класс) |
| `LicensesDialogFragment` | `ui` | «Лицензии» в `AboutFragment` |

### Абстрактные базы (2)

| Класс | Пакет | Наследники |
|---|---|---|
| `MaterialPreferenceDialogFragmentCompat` | `ui` | `MaterialListPreferenceDialogFragmentCompat`, `RootStrategyPreference.DialogFragment` |
| `PreferenceFragmentCompat` | `ui` | 3 PreferenceFragment |

## Свои Preference (15)

Элементы экранов настроек, наследуют `androidx.preference.Preference` и его подклассы.

| Пакет | Классы |
|---|---|
| `settings` (11) | `PathPreference` (база), `DefaultDirectoryPreference`, `StoragesPreference`, `StandardDirectoriesPreference`, `BookmarkDirectoriesPreference`, `CharsetPreference`, `LocalePreference`, `RootStrategyPreference`, `PasswordPreference`, `NonNegativeIntegerPreference`, `DefaultIfEmptyEditTextPreference` |
| `ftpserver` (4) | `FtpServerStatePreference`, `FtpServerUrlPreference`, `FtpServerHomeDirectoryPreference`, `FtpServerAddTilePreference` |

Ещё в настройках используется `simplemenu-preference` (rikka) — выпадающие списки.

## Адаптеры

### Экранные (4)

Список файлов, боковая панель и списки «Свойств» — на Compose, адаптеров у них нет.

| Адаптер | Пакет | База | Где |
|---|---|---|---|
| `StorageListAdapter` | `storage` | `SimpleAdapter` + `DraggableItemAdapter` | список хранилищ |
| `BookmarkDirectoryListAdapter` | `settings` | `SimpleAdapter` + `DraggableItemAdapter` | список закладок |
| `LanSmbServerListAdapter` | `storage` | `SimpleAdapter` | найденные SMB-серверы |
| `ImageViewerAdapter` | `viewer.image` | `SimpleAdapter` | страницы просмотра картинок |

### Базы в `ui/view/` (5)

`SimpleAdapter`, `ListAdapter`, `StaticAdapter`, `UnfilteredArrayAdapter`, `ViewPagerAdapter`; к ним — `ListDiffer`.

## Свои View и помощники UI

Почти всё — в `ui/view/`; главный экран и «Свойства» своих View не используют.

| Назначение | Классы в `ui/view/` |
|---|---|
| Панели и прокрутка других экранов | `CoordinatorAppBarLayout`, `FitsSystemWindowsAppBarLayout`, `CoordinatorScrollingFrameLayout`, `CoordinatorScrollingLinearLayout`, `ScrollingViewOnApplyWindowInsetsListener` |
| Fast scroll | `ThemedFastScroller` (текстовый редактор) |
| Элементы списка | `CheckableFrameLayout`, `CheckableForegroundLinearLayout`, `AspectRatioImageView`, `DisabledAlphaImageView`, `AutoGoneTextView` |
| Поля ввода и поиск | `ReadOnlyTextInputLayout`, `ReadOnlyTextInputEditText` (диалоги хранилищ и закладок), `ScrollingChildEditText`, `FixLayoutSearchView`, `AllowSoftInputHackAlertDialogCustomView` |
| Картинки и страницы | `SaveStateSubsamplingScaleImageView`, `ViewPagerTransformers` |
| Лицензии | `LicensesDialogExtensions` |

Библиотеки View, на которых держится UI: Material Components, `fastscroll` (zhanghai), `advrecyclerview` (перетаскивание), `subsampling-scale-image-view` и PhotoView (картинки), `preferencex`, `simplemenu-preference`, `licensesdialog`, `insetter`, `swiperefreshlayout`.

## Прочий UI вне экранов

| Что | Где |
|---|---|
| Уведомления фоновых операций | `filejob/FileJobNotificationTemplate.kt`, `util/NotificationTemplate.kt`, `util/ForegroundNotificationManager.kt` |
| Уведомление FTP-сервера | `ftpserver/FtpServerNotification.kt` |
| Плитка в шторке | `ftpserver/FtpServerTileService.kt` |
| Ярлыки приложения | `res/xml/shortcuts.xml` |

## Ресурсы разметки

| Папка | Кол-во | Что |
|---|---|---|
| `res/layout/` | 28 | экраны, диалоги, элементы списков; у главного экрана и «Свойств» разметки нет |
| `res/menu/` | 2 | `text_editor`, `image_viewer`; меню главного экрана — на Compose |
| `res/xml/` | 2 настроек | `settings.xml`, `ftp_server.xml`; ещё `shortcuts.xml`, `netework_security_config.xml` |
