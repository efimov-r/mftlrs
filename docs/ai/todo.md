# Задачи

Что в работе и что дальше. Выполненное удаляется — история есть в git.

## В работе

- [ ] Выбрать, какие функции убрать и какие добавить — кандидаты: [features.md](features.md) (колонка «Убрать?») и [upstream.md](upstream.md) («Выводы», «Чего нет у MaterialFiles»)

## Дальше

- [ ] Добавить удалённый `upstream`: `git remote add upstream https://github.com/zhanghai/MaterialFiles.git`
- [ ] Подключить ACRA с диалогом отправки — [decisions.md](decisions.md); Firebase уже вырезан, места под ACRA помечены `TODO(mftlrs)` — [architecture.md](architecture.md), «Отчёты о падениях»
- [ ] Скриншоты в `fastlane/.../images/` — после редизайна (сейчас upstream). Firebase, `applicationId`, название и атрибуция — сделано
- [ ] Включить в настройках репозитория на GitHub Private vulnerability reporting (Settings → Security) — на него ссылается `SECURITY.md`
- [ ] Решить, коммитить ли `gradle/gradle-daemon-jvm.properties`
- [ ] Обсудить хранение настроек: сложные значения (хранилища, закладки, сортировка, пути) лежат в SharedPreferences как Parcel → Base64, любое изменение этих классов без миграции молча сбрасывает настройки — [gotchas.md](gotchas.md), «Настройки хранятся как Parcel»
- [ ] TODO: какие ещё изменения интерфейса — от владельца

## Главный экран на Compose

Переписывать по частям внутри `FileListFragment`: после каждого этапа приложение собирается и работает, проверка — [file-list-checklist.md](file-list-checklist.md). Фрагмент остаётся хостом: 14 диалогов `filelist` отдают результат через `requireParentFragment() as Listener`. ViewModel на LiveData не переписывать — Compose читает её через `observeAsState()`. Решение и цена — [decisions.md](decisions.md), «Главный экран на Compose».

- [ ] UI-тесты: 37 зелёных ([testing.md](testing.md), «Что покрыто»). Не покрыто: режимы выбора (54–60) — только руками; галочки в меню «Вид и сортировка». Скриншотов не делаем
- [ ] Пройти [file-list-checklist.md](file-list-checklist.md) на текущей сборке, поправить пункты под реальное поведение
- [ ] 2. Вынести логику из `FileListFragment` (~1750 строк) в ViewModel и отдельные классы: выделение, видимость пунктов меню, сборка интентов открытия и выбора, режим выбора. Наружу — одно состояние экрана. Unit-тесты на вынесенное и на `TrailData`, `FileItemSet`, `FileSortOptions`, `PasteState`. Позиция прокрутки в `TrailData` сейчас — `Parcelable` от `GridLayoutManager`; для Compose — индекс и смещение
- [ ] 3. Список: `RecyclerView` + `FileListAdapter` → `LazyColumn` / `LazyVerticalGrid` в `ComposeView`; миниатюры — `AsyncImage` со своими загрузчиками из `coil/`; fast scroller, свайп для обновления, пустое состояние, ошибка, загрузка
- [ ] 4. Верхняя панель: toolbar, поиск, `BreadcrumbLayout` → `TopAppBar` и `LazyRow`; режим выделения (`OverlayToolbarActionMode`) → контекстная верхняя панель
- [ ] 5. Нижняя панель вставки и выбора (`PersistentBarLayoutToolbarActionMode`, поле имени файла) и FAB speed-dial
- [ ] 6. Навигация без боковой панели — сразу новый вид, не копия drawer. Что затронет: [architecture.md](architecture.md), «Боковая панель»
- [ ] 7. Чистка: `file_list_fragment*.xml`, `FileListAdapter`, `BreadcrumbLayout`, `NavigationFragment`, класс `Binding`. Удаление фрагмента и перевод диалогов на Compose — отдельная задача
