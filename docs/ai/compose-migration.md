# Перенос главного экрана на Compose

Подробный план переноса `FileListFragment` на Compose **один в один**: тот же внешний вид и поведение, без редизайна. Краткий список этапов и их статус — [todo.md](todo.md); решение — [decisions.md](decisions.md), «Главный экран на Compose»; готовые элементы — [compose.md](compose.md). Агенту: прочитать этот файл целиком до начала работы, после каждого этапа обновить статус и «Журнал».

## Принцип «как есть»

- Эталон — поведение и вид приложения на коммите `52dea100` (2026-11-18): чек-лист [file-list-checklist.md](file-list-checklist.md) (69 пунктов) и UI-тесты ([testing.md](testing.md)).
- Размеры, отступы, цвета, иконки, строки, анимации — брать из существующих XML и классов, а не «как красивее». Нашёл расхождение, которое не устранить, — записать в «Отклонения» ниже, не молчать.
- **Не делать в рамках переноса:** новую навигацию, новые пункты, переделку меню, переименования для красоты, перенос логики во ViewModel, unit-тесты, перевод диалогов, удаление drawer. Всё это — раздел «Отложено» в [todo.md](todo.md). Если очень хочется — записать туда и не трогать.
- После каждого этапа: приложение собирается (`.\gradlew.bat assembleDebug lintVitalRelease`), UI-тесты зелёные (`.\gradlew.bat connectedUiTestAndroidTest`, [testing.md](testing.md)), ручные пункты чек-листа этапа проходят, изменения в индексе (`git add`), документация обновлена. Коммитит владелец.
- Тесты при переносе не переписываются. Если тест упал — чинить экран, а не тест; менять тест можно, только если в нём ошибка, и это видно на текущей сборке Views.

## Стратегия

- **Хост** — по-прежнему `FileListFragment`. Диалоги (14 в `filelist`) отдают результат через `requireParentFragment() as Listener` — их не трогаем.
- **`FileListViewModel` не меняется** (LiveData). Состояние в Compose — через `observeAsState()` или `collectAsState` от LiveData; снимки изменяемых объектов — см. «Ловушки».
- **Логика остаётся во фрагменте.** Compose-элементы получают данные и лямбды. Контракт действий над файлом — интерфейс `FileItemListener` (`filelist/FileItemListener.kt`; был `FileListAdapter.Listener`, методы те же).
- **Drawer и `NavigationFragment` не трогаем** — остаются на Views до отдельной задачи «новая навигация». Кнопка «меню» в верхней панели по-прежнему открывает drawer (`binding.drawerLayout`), на планшете в ландшафте остаётся `PersistentDrawerLayout`.
- **Подключение `ComposeView`** — как в `addExitConfirmation()` (`filelist/ExitConfirmation.kt`): `FileListActivity` не вызывает `setContentView`, поэтому владельцев `ViewTree*` брать у view фрагмента; `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`.
- **Новые файлы** — заголовок `mftlrs contributors` + SPDX ([style.md](style.md)); элементы одного экрана — в `filelist/`, `private`, общие (например, полоса прокрутки) — в `ui/compose/` с префиксом `Mftlrs`; цвета только из `MaterialTheme.colorScheme`, строки из ресурсов. Каждый новый элемент — сразу в таблицу [compose.md](compose.md).
- **Правки старых файлов** — комментарий `mftlrs:` и строка `Modified for mftlrs, <год>.` в заголовке, если её нет.

## Что сейчас на Views и куда это переезжает

| Сейчас | Файлы | Станет |
|---|---|---|
| Список и сетка | `RecyclerView` + `GridLayoutManager` + `FileListAdapter`, `file_item_list.xml`, `file_item_grid.xml` | `LazyVerticalGrid` в `ComposeView`, элементы `FileListItem` / `FileGridItem` |
| Меню файла ⋮ | `PopupMenu` по `menu/file_item.xml` | `DropdownMenu` с теми же пунктами и условиями видимости (`FileListAdapter.onBindViewHolder`) |
| Состояния списка | `progress`, `errorText`, `emptyView` в `file_list_fragment_content_include.xml` | один composable: загрузка / ошибка / пусто по `Stateful` |
| Свайп вниз | `ThemedSwipeRefreshLayout` | `PullToRefreshBox` (Material 3) |
| Быстрая прокрутка | `ThemedFastScroller` (AndroidFastScroll) + `PopupTextProvider` в адаптере | `MftlrsFastScrollbar` |
| Верхняя панель | `CrossfadeSubtitleToolbar`, `menu/file_list.xml`, `FixQueryChangeSearchView`, `file_list_fragment_app_bar_include.xml` | `TopAppBar` с подзаголовком, меню ⋮, подменю «Вид и сортировка», поиск |
| Хлебные крошки | `BreadcrumbLayout`, `BreadcrumbData` | `LazyRow` |
| Режим выделения | `OverlayToolbar` + `OverlayToolbarActionMode`, `menu/file_list_select.xml`, `file_list_pick.xml` | контекстная верхняя панель поверх основной |
| Нижняя панель | `BottomBarLayout` + `PersistentBarLayoutToolbarActionMode`, `menu/file_list_paste.xml`, `file_list_pick_bottom.xml`, поле имени файла | нижняя панель Compose с полем |
| FAB | `ThemedSpeedDialView` (leinardi) + `SpeedDialOverlayLayout`, `menu/file_list_speed_dial.xml` | FAB с раскрытием «Файл» / «Папка» и затемнением |
| Сочетания клавиш | `onKeyShortcut` во фрагменте, `alphabeticShortcut` в меню (l, r, a, h) | то же поведение; не терять (чек-лист 65) |

## Этапы

Статусы — в [todo.md](todo.md). Каждый этап заканчивается рабочим приложением.

### Этап 0. Эталон

Код экрана не меняем, снимаем контрольные данные.
- UI-тесты по автоматизируемым пунктам чек-листа ([testing.md](testing.md), «Что покрыто»): все зелёные на текущей сборке — это эталон поведения. Сделано: 37 тестов.
- Пройти [file-list-checklist.md](file-list-checklist.md) на текущей сборке (`installDebug`), исправить пункты под реальное поведение. Пункты 65 и 66–69 помечены «уточнить» — зафиксировать как есть.
- Эталонных скриншотов не делаем (решение владельца); вид сверять с текущей сборкой на телефоне.

### Этап 1. Элементы списка и сетки (`FileListItem`, `FileGridItem`)

Только composable-функции и `@Preview`, в приложение пока не подключаются — работа приложения не меняется.
- Прочитать до начала: `FileListAdapter.kt` (весь), `layout/file_item_list.xml`, `layout/file_item_grid.xml`, `ui/DisabledAlphaImageView.kt`, `ui/CheckableItemBackground.kt`, `ui/CheckableForegroundLinearLayout.kt`, `ui/AutoGoneTextView.kt`, `drawable/file_item_grid_foreground_material3`, `file_item_grid_thumbnail_outline_material3`, `file_directory_thumbnail`, `badge_background_18dp`, `checkable_badge_18dp`, значения `dimen` (`two_line_list_item_height`, `touch_target_size`, `icon_size`, `large_icon_size`, `badge_size`, `screen_edge_margin*` и др.).
- Список: высота `two_line_list_item_height`; слева иконка в зоне `touch_target_size` (иконка по `file.mimeType.iconRes`; миниатюра поверх; значок приложения `AppIconPackageName` для папок `Android/data/<пакет>`; значок симлинка / битой ссылки / шифрования); имя в одну строку с обрезкой по настройке `FILE_NAME_ELLIPSIZE`; у файла (не у папки) вторая строка «дата · размер» (`formatShort`, `fileSize.formatHumanReadable`, разделитель `file_item_description_separator`); справа кнопка ⋮.
- Сетка: превью с отношением сторон 1,78 (у папки — `file_directory_thumbnail`, у файла — миниатюра с контуром и иконкой типа по центру, у APK иконка приложения), снизу строка высотой `single_line_list_item_height`: иконка, имя до двух строк по центру, ⋮; фон и скругления выбранного элемента — как `CheckableItemBackground.create(4f, 12f)` для сетки и `(0f, 0f)` для списка.
- Состояния: выбран (`isChecked`: фон и галочка `checkable_badge_18dp` на иконке); недоступен (`isEnabled = isFileSelectable || isDirectory` в режиме выбора — приглушён через `DisabledAlphaImageView`, меню и клик отключены); нажатие.
- Жесты — те же, что в адаптере: тап — открыть, если ничего не выбрано, иначе переключить выделение; долгое нажатие — наоборот; тап по иконке — переключить выделение (`combinedClickable`). `selectFile` учитывает `PickOptions` (один выбор, если `!allowMultiple`).
- Миниатюры: `AsyncImage` с моделью `path to attributes` (`Pair<Path, BasicFileAttributes>`) — загрузчики `coil/PathAttributesFetcher` и др. уже ключатся по такой паре; `AppIconPackageName` — отдельный fetcher. Проверить, что `coil-compose` берёт общий загрузчик из `CoilInitializer`.
- Меню ⋮ — `DropdownMenu`, пункты и условия из `onBindViewHolder` (строки 210–214, 320–327) и `menu/file_item.xml`; «Копировать» внутри архива называется «Извлечь» (`file_item_action_extract`).
- Проверка: превью в Android Studio против текущей сборки на телефоне; сборка проходит; приложение не изменилось.

### Этап 2. `MftlrsFastScrollbar`

Отдельный элемент в `ui/compose/`, превью, в приложение пока не подключён.
- Прочитать: `ui/ThemedFastScroller.kt`, исходники AndroidFastScroll (поведение: ручка, подсказка с текстом, автоскрытие, перетаскивание, тап по полосе), `FileListAdapter.getPopupText`.
- Принимает `LazyGridState` (или универсальный интерфейс по индексам) и `popupText: (index) -> String`. Подпись — по ключу сортировки: имя → первая буква в верхнем регистре, тип → расширение в верхнем регистре, размер и дата — форматированные (как `getPopupText`).
- Цвета и форма — как у `ThemedFastScroller`. **Самый рискованный элемент:** если точного совпадения не добиться, записать отклонение.

### Этап 3. Список подключается

`RecyclerView` + `ThemedSwipeRefreshLayout` + `progress` + `errorText` + `emptyView` в `file_list_fragment_content_include.xml` заменяются одним `ComposeView`. Тулбары, drawer, нижняя панель и FAB остаются на Views.
- Прочитать: `FileListFragment` строки 211–262, 585–673, 738–745, 1188–1202; `ui/CoordinatorScrollingFrameLayout.kt`, `ui/ScrollingViewOnApplyWindowInsetsListener.kt`, `ui/AppBarLayoutExpandHackListener.kt`, `ui/PersistentBarLayout.kt`; `TrailData.kt`, `TrailLiveData.kt`.
- `FileListContent.kt`: `LazyVerticalGrid`; список — одна колонка, сетка — `ширина / 180 dp`, минимум 2 (сейчас `configuration.screenWidthDp`, минус `navigation_max_width` при открытой постоянной панели; в Compose брать реальную ширину контента — результат тот же). Ключ элемента — `path`. Анимации изменения списка нет (убрана раньше) — `animateItem` не использовать.
- Сортировка: сейчас делает адаптер (`replaceListAndIsSearching`: при поиске список не сортируется, иначе `sortedWith(sortOptions.createComparator())`; смена сортировки пересортировывает). Перенести как есть в подготовку списка для Compose (`remember` по списку, сортировке, признаку поиска), не во ViewModel.
- Выделение, `pickOptions`, обрезка имени — в Compose из тех же LiveData (`selectedFilesLiveData`, `pickOptionsLiveData`, `Settings.FILE_NAME_ELLIPSIZE`).
- Состояния — как `onFileListChanged` (594–625): индикатор обновления (свайп) показывается при `Loading` и (есть файлы или идёт поиск); индикатор по центру — при `Loading` без файлов и вне поиска; ошибка по центру (`error_icon_240dp` + текст) при `Failure` без файлов; при `Failure` с файлами — тост; «Пусто» (`file_icon_240dp` + `file_list_empty`) при `Success` без файлов.
- Позиция прокрутки: `TrailData` хранит `Parcelable` от `GridLayoutManager`. Заменить на `@Parcelize data class ScrollPosition(index, offset)` (`Parcelable` — чтобы сигнатуры `TrailData`/`TrailLiveData`/`FileListViewModel` не менялись). Сохранять в `navigateTo(path)` из `firstVisibleItemIndex` и `firstVisibleItemScrollOffset`; восстанавливать `scrollToItem` после `Success` из `viewModel.pendingState` (оно забирает и обнуляет значение — сохранить эту семантику).
- Поля и отступы: `paddingBottom = list_bottom_padding_with_fab`, `clipToPadding = false`, обработка системных панелей (edge-to-edge), нижний отступ, который `contentLayout` сейчас добавляет по смещению `AppBarLayout`. Не потерять: чек-лист 63.
- Подключить `MftlrsFastScrollbar` (этап 2).
- Проверка: UI-тесты; чек-лист 11–28, 29–36, 41–44, 63–64.

### Этап 4. Верхняя панель

Заменяется `file_list_fragment_app_bar_include.xml`: основной тулбар, меню, поиск, крошки, панель выделения. Drawer остаётся.
- Прочитать: `FileListFragment` строки 389–583, 754–770, 849–963; `ui/CrossfadeSubtitleToolbar.kt`, `ui/OverlayToolbar.kt`, `ui/OverlayToolbarActionMode.kt`, `ui/ToolbarActionMode.kt`, `filelist/BreadcrumbLayout.kt`, `BreadcrumbData.kt`, `menu/file_list*.xml`, `integers.xml` (`file_list_toolbar_scroll_flags` = noScroll, панель не скрывается).
- 4a. Основная панель: значок меню слева (открывает drawer, `open_navigation_drawer`), заголовок (`file_list_title` или по `PickOptions`: `file_list_title_open_file`, `…_create_file`, `…_open_directory`), подзаголовок с кроссфейдом («Загрузка…», «Ошибка», «N папок, M файлов», «Пусто» — `getSubtitle`), поиск, «Вид и сортировка», меню ⋮ с пунктами из `menu/file_list.xml` в том же порядке и с теми же условиями видимости (`onPrepareOptionsMenu`, `updateViewSortMenuItems`, `updateSelectAllMenuItem`, `updateShowHiddenFilesMenuItem`).
- Поиск: раскрывающееся поле, результаты через 1 с после последней буквы (`DebouncedRunnable`), Enter — сразу; пока поиск открыт, «Вид и сортировка» скрыт; состояние поля и запроса уже в `FileListViewModel` (`searchViewExpandedLiveData`, `searchViewQuery`), поворот экрана его сохраняет.
- 4b. Крошки: `LazyRow` из `BreadcrumbData` (пути, выбранный индекс; выбранная крошка видна и прокручена в видимую область; крошки правее выбранной тоже показываются — чек-лист 4); тап → `navigateTo`; длинное нажатие/меню — как в `BreadcrumbLayout` и `menu/file_list_breadcrumb.xml` (прочитать, что умеет).
- 4c. Панель выделения: накладывается поверх основной, крестик слева («Назад» и крестик снимают выделение), заголовок «Выбрано: N» (`file_list_select_title_format`), пункты из `file_list_select.xml` / `file_list_pick.xml` с условиями из `updateOverlayToolbar` (854–918). Переключение — с кроссфейдом как сейчас.
- Убрать из фрагмента: `setHasOptionsMenu`, `onCreateOptionsMenu`, `onPrepareOptionsMenu`, `MenuBinding`, `setSupportActionBar`, `overlayActionMode`. Сочетания клавиш (`onKeyShortcut`) сохранить.
- Проверка: чек-лист 2–10, 15, 17–21, 29–36, 48–53, 64–65.

### Этап 5. Нижняя панель и FAB

- Прочитать: `FileListFragment` строки 1031–1186, `ui/BottomBarLayout.kt`, `ui/PersistentBarLayout.kt`, `ui/PersistentBarLayoutToolbarActionMode.kt`, `ui/ThemedSpeedDialView.kt`, `ui/SpeedDialViewOnBackPressedCallback.kt`, `menu/file_list_paste.xml`, `file_list_pick_bottom.xml`, `file_list_speed_dial.xml`, `layout/file_list_fragment_bottom_bar_include.xml`, `file_list_fragment_speed_dial_include.xml`.
- Панель вставки: крестик слева (`close`), заголовок («Перемещение: N» / «Копирование: N» / «Извлечение: N»), кнопка «Вставить» / «Извлечь сюда» (неактивна, если текущая папка только для чтения); выезжает снизу, список сдвигается (`PersistentBarLayout`).
- Режимы выбора: `CREATE_FILE` — поле имени без рамки (подсказка `file_list_create_file_name_hint`, предзаполнение из `PickOptions.fileName`, выделено без расширения, фокус, `isCreateFileNameEditInitialized`, Enter = «Создать») и кнопка «Создать»; `OPEN_DIRECTORY` — «Открыть «<имя>»» с кнопкой; крестик закрывает Activity.
- FAB: плюс, при раскрытии поворот на 135°, затемнение содержимого, два пункта «Файл» и «Папка» с подписями; закрытие по «Назад» и тапу по затемнению. Material 3 `FloatingActionButtonMenu` — если не совпадает по виду, свой элемент.
- «Назад»: приоритет должен остаться как сейчас (`FileListFragment.onActivityCreated`, строки 264–285; последний зарегистрированный срабатывает первым): закрыть drawer → закрыть FAB → снять выделение → вверх по крошкам → подтверждение выхода (регистрируется первым, срабатывает последним, выключено в режиме выбора файла). Проверить на устройстве, что порядок сохранён.
- Проверка: чек-лист 37–40, 45–47, 54–60, 63–64.

### Этап 6. Один экран

Все части уже на Compose — собрать в один `FileListScreen` в одном `ComposeView`, убрать промежуточные View-контейнеры.
- Убрать из `file_list_fragment_include.xml` (обе версии: обычная и `layout-sw600dp-land`) `CoordinatorLayout`, `PersistentBarLayout`, `AppBarLayout`, `speed dial`, `bottomBarLayout`: остаётся `DrawerLayout` (или `PersistentDrawerLayout`) с одним `ComposeView` и `NavigationFrameLayout` (drawer без изменений).
- Разобраться с edge-to-edge целиком: `fitsSystemWindows`, `IgnoreFitsSystemWindowsFullyDraggableDrawerContentLayout` (свайп drawer с любого места) — поведение сохранить.
- Проверка: UI-тесты; весь чек-лист, портрет и ландшафт, планшет, светлая и тёмная тема.

### Этап 7. Чистка и документация

- Удалить то, чем больше никто не пользуется (проверять поиском перед удалением): `file_list_fragment_{app_bar,content,bottom_bar,speed_dial}_include.xml`, `file_list*.xml` (меню экрана), `OverlayToolbar*`, `ToolbarActionMode`, `PersistentBarLayout*`, `BottomBarLayout`, `CoordinatorAppBarLayout`, `FitsSystemWindowsAppBarLayout`, `CoordinatorScrollingFrameLayout` и `…LinearLayout`, `ThemedSpeedDialView`, `SpeedDialViewOnBackPressedCallback`, класс `Binding` во фрагменте; библиотека `speed-dial` — если освободилась. Уже удалено после этапа 3: `FileListAdapter`, `file_item_list.xml`, `file_item_grid.xml`, `menu/file_item.xml`, `CheckableItemBackground`, `AspectRatioFrameLayout`, `AppBarLayoutExpandHackListener`; после этапа 4: `CrossfadeSubtitleToolbar`, `FixQueryChangeSearchView`, `menu/file_list.xml`, `BreadcrumbLayout`, `breadcrumb_item.xml`, `breadcrumb_item_foreground.xml`, `menu/file_list_breadcrumb.xml`. Остаются, потому что нужны другим экранам: `ThemedFastScroller` (текстовый редактор), `ScrollingViewOnApplyWindowInsetsListener` (закладки, хранилища), `ThemedSwipeRefreshLayout` (поиск SMB, свойства), `DisabledAlphaImageView` (диалог конфликта), `CheckableForegroundLinearLayout` и `AutoGoneTextView` (боковая панель, права), библиотеки `fastscroll` и `swiperefreshlayout`. `lintVitalRelease` и правила R8 (`proguard-rules.pro`) проверить.
- Обновить: [compose.md](compose.md) (таблица элементов), [architecture.md](architecture.md) («Главный экран»), [ui-inventory.md](ui-inventory.md) (адаптеры, свои View, разметка), [file-list-checklist.md](file-list-checklist.md), этот файл (статусы, «Журнал»), [decisions.md](decisions.md) (статус), README «Состояние» — только то, что заметно пользователю (если внешний вид изменился — отметить).

## Ловушки (известны заранее)

- **Изменяемые коллекции с тем же экземпляром.** `FileItemSet` (выделение) и `PasteState` меняются на месте и публикуются в LiveData тем же объектом. `observeAsState` / `State` сравнивают по `equals`, поэтому изменение может не вызвать перерисовку. Перед передачей в Compose делать снимок (`toSet()`, копия `PasteState`).
- **Обрезка имён.** `FILE_NAME_ELLIPSIZE` — `TextUtils.TruncateAt` (начало, середина, конец, бегущая строка). В Compose: `TextOverflow.StartEllipsis` / `MiddleEllipsis` (есть в Compose UI с 1.8, проверить на BOM 2026.09), бегущая — `Modifier.basicMarquee()` для выбранного режима. Lint в `file_item_list.xml` предупреждает о падении `ellipsize="middle"` + `maxLines="1"` у TextView — в Compose этой проблемы нет.
- **`ComposeView` без владельцев `ViewTree*`** падает при attach — см. «Стратегия».
- **Порядок «Назад».** Колбэки срабатывают в обратном порядке регистрации; `BackHandler` в Compose встаёт по порядку композиции — не сломать порядок из этапа 5.
- **Миниатюры.** Модель Coil — `Pair<Path, BasicFileAttributes>`; `FileItem.supportsThumbnail`; у APK иконка приложения вместо миниатюры; сетевые файлы — по настройке `READ_REMOTE_FILES_FOR_THUMBNAIL`. Не заменять на `Path`.
- **Edge-to-edge.** Отступы сейчас распределены между `fitsSystemWindows` на трёх уровнях, `ScrollingViewOnApplyWindowInsetsListener` и смещением `AppBarLayout`. При замене по частям следить, что последний элемент списка не закрыт навигационной панелью, FAB и нижней панелью (чек-лист 63).
- **Поворот и процесс.** Состояние поиска, выделения и режима выбора живёт в ViewModel; `rememberSaveable` для того, что уже там, не дублировать.
- **Ресурсы Views, нужные Compose.** Иконки — `drawable` (vector) через `painterResource`; `?colorControlNormal`, `?textAppearanceListItem` и др. атрибуты Views в Compose заменять значениями `MaterialTheme` — подобрать так, чтобы цвет и размер совпали, а не взять «ближайший».

## Отклонения от «один в один»

Заполняется по ходу. Формат: этап — что отличается — почему не удалось.

| Этап | Отличие | Причина |
|---|---|---|
| 1 | элементы списка и сетки: ripple и фон выделения на весь элемент (в сетке был отступ 4 dp и скругление 12 dp); выделение и галочка без анимации; недоступный элемент приглушён целиком (раньше — картинки и текст по отдельности); ⋮ — стандартный `IconButton` | решение владельца 2026-11-19: меньше кода; тесты вид не проверяют |
| 3 | свайп для обновления — индикатор `PullToRefreshBox` Material 3 вместо `SwipeRefreshLayout`; загрузка, ошибка и «Пусто» появляются без плавного затухания | стандартные компоненты Compose, упрощение по решению владельца 2026-11-19 |
| 2 | полоса быстрой прокрутки всегда видна, без подсказки с буквой и без анимаций появления | решение владельца 2026-11-19: точный перенос библиотеки — слишком много кода; тесты полосу не проверяют |
| 4a | меню панели — `DropdownMenu` Material 3 (переключатели и флажки справа, разделители между группами) вместо `PopupMenu` AppCompat; подзаголовок сменяется наложением (`Crossfade`), а не «погас — загорелся»; поле поиска — `BasicTextField` с текстом `bodyLarge`, без подсказок SearchView; описание ⋮ — «Ещё» (AppCompat), а не системное | стандартные компоненты Compose; системную строку ⋮ без `getIdentifier` не взять |
| 4b | меню крошки по долгому нажатию — `DropdownMenu` Material 3 вместо `PopupMenu`; зона нажатия крошки — 40 dp по высоте, как ripple (было 48 dp, ripple 40 dp); текст — `titleSmall` (14sp, medium, межбуквенный интервал 0,1sp) вместо `TextAppearance.Design.Tab` | стандартные компоненты Compose, меньше кода |

## Журнал

Одна строка на сделанное: дата, этап, что сделано, что осталось. Новые записи — снизу.

- 2026-11-18 — план составлен и записан; код не менялся. Следующий шаг — этап 0 (эталон).
- 2026-11-18 — этап 0: UI-тесты — 37 зелёных на телефоне (~4,5 мин), покрытие и контракт для Compose — [testing.md](testing.md). Режимы выбора (54–60) — только руками. Скриншотов не делаем. Осталось: ручной проход чек-листа.
- 2026-11-19 — этап 1: `filelist/FileListItems.kt` — `FileListItem`, `FileGridItem` и превью (список и сетка, светлая и тёмная); в приложение не подключены, `assembleDebug lintVitalRelease` проходит. Превью строятся из `FileItemUi`, без `FileItem`: тому нужен `Path`, а файловой системы в превью нет. Осталось: сверить превью в Android Studio с телефоном. Проверить при подключении (этап 3): цвет ripple (`ripple()` Compose против `?colorControlHighlight`), вертикальное положение текста (в Compose нет `includeFontPadding`), значок APK в сетке до загрузки миниатюры.
- 2026-11-19 — этап 2: `ui/compose/MftlrsFastScrollbar.kt`. Сначала был полный перенос AndroidFastScroll (подсказка, автоскрытие, анимации, ~470 строк) — по решению владельца заменён простой полосой (~150 строк), см. «Отклонения». Касания — на родителе в `PointerEventPass.Initial`, список получает касание, пока не началось перетаскивание. Сборка проходит; на устройстве — после подключения на этапе 3.
- 2026-11-19 — `FileListItems.kt`: иконке добавлен `testTag("iconLayout")` — тест «тап по иконке» ищет её по id; строка добавлена в контракт [testing.md](testing.md).
- 2026-11-19 — `MftlrsBottomSheet` переписан на `ModalBottomSheet` Material 3 (~240 → ~50 строк), `fillHeight` убран; `FileListExitTest` 3 из 3 на телефоне.
- 2026-11-19 — `FileListItems.kt` упрощён по решению владельца (835 → 755 строк): убраны своя обёртка ripple/выделения, анимации, приглушение по частям; ⋮ — `IconButton`. Модель `FileItemUi` и превью, миниатюры, особый случай APK, размеры из `dimen` оставлены. Сборка проходит.
- 2026-11-19 — этап 3: список подключён. `file_list_fragment_content_include.xml` — `CoordinatorScrollingFrameLayout` с одним `ComposeView`; `FileListContent.kt` — сетка, свайп (`PullToRefreshBox`), полоса прокрутки, загрузка, ошибка, «Пусто». Фрагмент вместо адаптера заполняет `FileListContentState`: сортировка перенесена в `updateAdapterFileList`, выделение — снимок путей, «Выбрать все» и список картинок для просмотрщика — из `contentState.files`. Позиция прокрутки — `ScrollPosition`, при повороте — через `onSaveInstanceState`. Убраны `updateSpanCount` (колонки считает Compose по реальной ширине) и `AppBarLayoutExpandHackListener` (панель `noScroll`). `CoordinatorScrollingFrameLayout` не ставит margin детям с `fitsSystemWindows` — [gotchas.md](gotchas.md). UI-тесты 37 из 37 (первый прогон поймал NPE — тоже в gotchas). `FileListAdapter`, `ThemedFastScroller`, `ScrollingViewOnApplyWindowInsetsListener`, `AppBarLayoutExpandHackListener` больше не используются экраном — удалить на этапе 7. Осталось: ручной чек-лист 11–28, 29–36, 41–44, 63–64 (владелец).
- 2026-11-19 — лаги прокрутки после этапа 3: замер `dumpsys gfxinfo` показал, что дело в debug-сборке (с R8 — на уровне Views), подробности — [gotchas.md](gotchas.md). `nestedScroll`-interop убран (не нужен: у панели `liftOnScroll="false"`, на лаги не влиял).
- 2026-11-19 — чистка после этапа 3 (по решению владельца — сейчас, не на этапе 7): удалены `FileListAdapter`, `file_item_list.xml`, `file_item_grid.xml`, `menu/file_item.xml`, `CheckableItemBackground`, `AspectRatioFrameLayout`, `AppBarLayoutExpandHackListener` и drawable `file_item_grid_foreground_material3`, `file_item_grid_thumbnail_outline_material3`, `checkable_badge_18dp`; `FileListAdapter.Listener` → `FileItemListener`. Сборка и `lintVitalRelease` проходят; UI-тесты не гонялись — по просьбе владельца позже.
- 2026-11-19 — этап 4a: основная панель на Compose — `filelist/FileListTopBar.kt` в `ComposeView` вместо `CrossfadeSubtitleToolbar` (значок drawer, заголовок, подзаголовок, поиск на `TextFieldState`, «Вид и сортировка», ⋮). Убраны `setHasOptionsMenu`, `setSupportActionBar`, `onCreateOptionsMenu`/`onPrepareOptionsMenu`/`onOptionsItemSelected`, `MenuBinding`; удалены `menu/file_list.xml`, `CrossfadeSubtitleToolbar`, `FixQueryChangeSearchView`, стиль `…Subtitle.Small`. «Назад» закрывает поиск отдельным колбэком (раньше — support action bar), сочетания Ctrl+L/R/A/H перенесены в `onKeyShortcut`. Панель выделения и крошки — пока Views. Сборка и `lintVitalRelease` проходят. UI-тесты: без сброса кэша UiAutomator падали тесты поиска ([gotchas.md](gotchas.md), «UI-тест не видит изменений на Compose») — в `FileListScreen` добавлен сброс кэша. `back_closesSearch` падал из-за клавиатуры: поле получает фокус, первое «Назад» прячет клавиатуру. По решению владельца поведение оставлено как у `SearchView`, а тест нажимает «Назад» второй раз, если поле не пропало (`pressBackToCloseSearch`) — исключение из правила «тесты не переписываются». Итог: 37 из 37 (весь набор — 36 до правки `back_closesSearch`, затем он отдельно — зелёный). Осталось: 4b — крошки, 4c — панель выделения; владельцу — чек-лист 2–10, 15, 17–21, 48–53, 65
- 2026-11-19 — UI-тесты после чистки: 36 из 37, `FileListSearchTest.clickDirectoryInResults_opensItAndClosesSearch` — нестабилен (`StaleObjectException`, при повторе зелёный), разбор — [testing.md](testing.md), «Что покрыто».
- 2026-11-19 — этап 4b: крошки на Compose — `filelist/FileListBreadcrumbs.kt` (`LazyRow`) в `ComposeView` вместо `BreadcrumbLayout`; данные — `breadcrumbLiveData.observeAsState()`. Выбранная крошка прокручивается к левому краю (первый раз без анимации), тап по выбранной — прокрутка к ней, по другой — `navigateTo`; долгое нажатие — «Копировать путь», «Открыть в новом окне». Удалены `BreadcrumbLayout`, `breadcrumb_item.xml`, `breadcrumb_item_foreground.xml`, `menu/file_list_breadcrumb.xml`. Сборка и `lintVitalRelease` проходят, UI-тесты 37 из 37 (крошки тестами не покрыты). Осталось: 4c — панель выделения; владельцу — чек-лист 2, 4, 9 (крошки)
