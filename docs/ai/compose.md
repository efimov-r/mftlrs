# Compose: готовые элементы

Что уже написано на Compose. Перед новым UI на Compose — проверить здесь, нет ли подходящего элемента: переиспользовать или расширить его, а не писать однотипный.

## Правила

- **Общие элементы** (нужны или могут понадобиться больше чем одному экрану) — в `ui/compose/`, пакет `me.zhanghai.android.files.ui.compose`, имя с префиксом `Mftlrs`. Тема — в `theme/`.
- **Элементы одного экрана** — в папке экрана (`filelist/` и т. п.), `private`, если не нужны снаружи. Понадобились второму экрану — вынести в `ui/compose/`, а не копировать.
- Каждый новый, вынесенный или удалённый элемент — сразу отметить в таблице ниже.
- Цвета — только из `MaterialTheme.colorScheme` (палитра `MftlrsTheme`), строки — из ресурсов.

## Элементы

| Элемент | Где | Что делает |
|---|---|---|
| `MftlrsTheme` | `theme/MftlrsTheme.kt` | тема Material 3 с палитрой `colors_mftlrs.xml`, светлая и тёмная по ночному режиму приложения. Оборачивать каждый `setContent` |
| `MftlrsBottomSheet` | `ui/compose/MftlrsBottomSheet.kt` | панель снизу в своём окне `Dialog`: выезд и уход с анимацией, затемнение фона, закрытие тапом по фону, свайпом вниз и «Назад»; `fillHeight` — на всю высоту для форм с клавиатурой. Содержимое — `ColumnScope`, ручка для перетаскивания уже есть |
| `ExitSheet` (private) | `filelist/ExitConfirmation.kt` | панель «Закрыть mftlrs?»: «Закрыть», «Свернуть», «Отмена» столбцом на `MftlrsBottomSheet` |
| `Fragment.addExitConfirmation()` | `filelist/ExitConfirmation.kt` | подтверждение выхода по «Назад»; возвращает `OnBackPressedCallback` |

## Compose внутри экрана на Views

Пока экраны на Views, Compose встраивается через `ComposeView` — образец в `addExitConfirmation()`:

- `FileListActivity` не вызывает `setContentView`, поэтому у decor view нет владельцев `ViewTree*`: `ComposeView` берёт их у view фрагмента (`setViewTreeLifecycleOwner(viewLifecycleOwner)`, `setViewTreeSavedStateRegistryOwner(...)`), иначе падение при attach;
- стратегия — `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed`; view убирается из контейнера в `onDestroy` жизненного цикла view фрагмента;
- для элементов в своём окне (`Dialog`, `MftlrsBottomSheet`) `ComposeView` может быть размера 0 в `android.R.id.content`.