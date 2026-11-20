# Функции: код, библиотеки, убирать ли

Что умеет приложение (состояние upstream 1.7.5), сколько кода на каждую функцию, сколько библиотек она тянет и стоит ли её убирать. «Убрать?» — рекомендация агента; решает владелец, принятое — в [decisions.md](decisions.md). Как устроены части — [architecture.md](architecture.md).

**Как считали (2026-11-15):** непустые строки `.kt`, `.java`, `.c`, `.aidl` в `app/src/main/`, включая заголовки лицензий и импорты. Каждый файл отнесён к одной функции, без пересечений. Ресурсы (разметка, строки, темы) не входят. Всего — **54 477 строк в 718 файлах** (100 %); разметка — ещё 58 файлов, ≈ 3 600 строк. Доля — от всего кода. «Либ» — прямые зависимости из `app/build.gradle`, нужные функции; какие именно — список под таблицей.

## Таблица

Отсортировано по доле кода.

| Функция | Строк (доля) | Либ | Где в коде | Убрать? |
|---|---|---|---|---|
| Инфраструктура | 11320 (20,8%) | 15 | `app/`, `file/`, `util/`, `compat/`, `ui/` | **Оставить** |
| Ядро ФС (NIO2) | 4300 (7,9%) | 2 | `provider/common/`, `provider/FileSystemProviders.kt` | **Оставить** — на нём всё |
| Главный экран | 4270 (7,8%) | 0 | `filelist/` | **Оставить** — переделывается при редизайне |
| Локальные файлы | 4140 (7,6%) | 1 | `provider/linux/`, `jni/syscall.c` | **Оставить** — основа |
| SMB | 3260 (6,0%) | 7 | `provider/smb/`, `storage/*SmbServer*`, `java/com/hierynomus/` | **Оставить** — самая востребованная сеть; боль — скорость |
| Фоновые операции | 3110 (5,7%) | 0 | `filejob/` | **Оставить**; доработать: экран прогресса, корзина |
| SAF | 2750 (5,0%) | 0 | `provider/document/`, `provider/content/`, `storage/*DocumentTree*` | **Оставить** — SD и USB, где нет прямого доступа |
| Root и Shizuku | 2590 (4,8%) | 3 | `provider/root/`, `provider/remote/`, `aidl/` | **Оставить** — Shizuku даёт `Android/data` без root |
| FTP-клиент | 2520 (4,6%) | 1 | `provider/ftp/`, `storage/*FtpServer*` | **По желанию** — изолирован, убирается вместе с `commons-net` |
| WebDAV | 2210 (4,1%) | 1 | `provider/webdav/`, `storage/*WebDavServer*`, `java/at/bitfire/` | **Оставить** — Nextcloud и облака без закрытых SDK; минус — JitPack |
| Архивы | 1870 (3,4%) | 1 | `provider/archive/`, `fileaction/`, `filelist/data/CreateArchiveType.kt` | **Оставить** |
| Настройки | 1660 (3,1%) | 3 | `settings/` | **Оставить**; при редизайне проверить библиотеки настроек |
| Боковая панель | 1660 (3,0%) | 0 | `navigation/`, `filelist/ui/FileListNavigation.kt` | **Убрать** по цели проекта; пункты перенести в новый интерфейс |
| SFTP | 1580 (2,9%) | 3 | `provider/sftp/`, `storage/*SftpServer*`, `java/net/schmizz/` | **Оставить** — доступ к серверам по SSH |
| FTP-сервер | 930 (1,7%) | 3 | `ftpserver/`, `res/xml/ftp_server.xml` | **По желанию** — если не нужен: минус 3 либы, сервис `specialUse` и плитка |
| Права и SELinux | 910 (1,7%) | 0 | `fileproperties/permission/` | **Оставить** — ниша, но уникально |
| EXIF, аудио, видео | 850 (1,6%) | 2 | `fileproperties/image/`, `audio/`, `video/` | **Оставить** |
| Хранилища | 800 (1,5%) | 2 | остальное в `storage/` | **Оставить** — может переехать на главный экран |
| Миниатюры | 620 (1,1%) | 5 | `coil/` | **Оставить** |
| Свойства файла | 560 (1,0%) | 0 | `fileproperties/`, `fileproperties/basic/` | **Оставить** |
| Текстовый редактор | 520 (1,0%) | 0 | `viewer/text/` | **Оставить** |
| Темы | 490 (0,9%) | 1 | `ui/theme/` | **Переделано**: две фиксированные темы, палитра mftlrs |
| Просмотр картинок | 470 (0,9%) | 4 | `viewer/image/` | **Оставить** — есть проблемы жестов и поворота по EXIF |
| Свойства APK | 410 (0,8%) | 0 | `fileproperties/apk/` | **Оставить** |
| О программе | 270 (0,5%) | 1 | `about/`, `ui/view/Licenses*` | **Переделано**: ссылки на форк, пункт «Основано на» |
| Контрольные суммы | 210 (0,4%) | 0 | `fileproperties/checksum/` | **Оставить** |
| Hidden API | 80 (0,1%) | 0 | `hiddenapi/`, `jni/hiddenapi.c` | **Оставить** — нужен `compat/` |
| «Сохранить как» | 46 (0,1%) | 0 | `viewer/saveas/` | **Оставить** |
| Папки QQ, TIM, WeChat | ~40 (<0,1%) | 0 | `navigation/NavigationItems.kt`, входят в боковую панель | **Убрать** — на Android 11+ и так скрыты |
| Терминал | 25 (<0,1%) | 0 | `terminal/` | **Проверить** — зовёт заброшенный Android Terminal Emulator, с Termux, вероятно, не работает |
| Samsung DeX, оконный режим | — | 0 | манифест: `resizeableActivity`, `touchscreen required=false` | **Оставить** |
| Переводы (22) | — | 0 | `res/values-*` | **Оставить**; новые строки — сразу во все локали ([style.md](style.md), «Тексты») |

## Библиотеки по функциям

`bcprov`, `slf4j-android` и `material` нужны двум функциям и посчитаны в обеих. Compose (BOM, Material 3, `activity-compose`, `lifecycle-*-compose`, `coil-compose`) в подсчёт 2026-11-15 не входит.

- **Инфраструктура (15):** `kotlin-stdlib-jdk8`, `kotlinx-coroutines-android`, `kotlinx-coroutines-core`, `activity-ktx`, `appcompat`, `core-ktx`, `fragment-ktx`, `lifecycle-common-java8`, `lifecycle-livedata-ktx`, `lifecycle-process`, `lifecycle-runtime-ktx`, `lifecycle-viewmodel-ktx`, `recyclerview`, `material`, `foregroundcompat`.
- **Ядро ФС (2):** `retrofile` (бэкпорт `java.nio.file`), `desugar_jdk_libs`.
- **Другие экраны на Views (2):** `fastscroll` (текстовый редактор), `swiperefreshlayout` (поиск SMB).
- **Локальные файлы (1):** `libselinux`.
- **SMB (7):** `smbj` 0.11.5, `dcerpc`, `guava-android`, `listenablefuture` (заглушка от конфликта Guava с AndroidX), `jcifs-ng` (имена NetBIOS, поиск в сети; инициализируется при старте приложения), `slf4j-android`, BouncyCastle `bcprov`.
- **Root и Shizuku (3):** `libsu:service`, Shizuku `api`, Shizuku `provider`.
- **FTP-клиент (1):** `commons-net`.
- **WebDAV (1):** `dav4jvm` (коммит через JitPack); транзитивно — OkHttp, общий клиент в `app/SystemServices.kt`.
- **Архивы (1):** `libarchive` (нативная).
- **Настройки (3):** `preference-ktx`, `preferencex` (takisoft), `simplemenu-preference` (rikka).
- **SFTP (3):** `sshj`, BouncyCastle `bcprov`, `bcpkix`.
- **FTP-сервер (3):** `ftpserver-core`, `mina-core` 2.1.3, `slf4j-android`.
- **EXIF, аудио, видео (2):** `exifinterface`, `androidsvg`.
- **Хранилища (2):** `advrecyclerview`, `materialshadowninepatch`.
- **Миниатюры (5):** `coil`, `coil-gif`, `coil-svg`, `coil-video`, `appiconloader`.
- **Темы (1):** `material`.
- **Просмотр картинок (4):** `PhotoView`, `subsampling-scale-image-view`, `systemuihelper`, `insetter`.
- **О программе (1):** `licensesdialog`.

## Зафиксированные версии

Не обновлять без проверки — причины в комментариях `app/build.gradle`:

| Библиотека | Версия | Почему |
|---|---|---|
| `smbj` | 0.11.5 | в 0.12+ сломан анонимный вход |
| `mina-core` | 2.1.3 (`strictly`) | 2.1.4+ несовместима с API < 24 |
| `licensesdialog` | 2.1.0 | 2.2.0 тянет `androidx.webkit` и неправильно красит тёмную тему |
| `insetter-ktx` | 0.3.1 | 0.6.0 нестабильна в полноэкранном режиме |
| `dav4jvm` | коммит `02fe1a9…` с JitPack | нужен один коммит upstream без следующего |
| `dcerpc` | 0.12.13 с исключениями | иначе подтянет JRE-Guava и поднимет `smbj` до 0.12.2 |
