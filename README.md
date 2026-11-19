# mftlrs

Файловый менеджер с открытым кодом для Android 8.0+, основанный на [Material Files](https://github.com/zhanghai/MaterialFiles) автора Hai Zhang.

> mftlrs — независимый форк. Он не связан с проектом Material Files и его автором и не одобрен ими. О проблемах в mftlrs пишите в [issues этого репозитория](../../issues), а не в оригинальный проект.

## Состояние

Что уже изменено по сравнению с Material Files 1.7.5:

- удалены Firebase Analytics и Crashlytics — приложение не собирает и не отправляет данные;
- своё название и идентификатор приложения — ставится рядом с Material Files, не заменяя его;
- экран «О программе» ведёт в этот репозиторий, оригинал указан в пункте «Основано на»;
- убрана поддержка Android TV;
- минимальная версия — Android 8.0 (у Material Files — 6.0);
- иконка приложения и ярлыков — бирюзово-зелёная вместо синей;
- две фиксированные темы, светлая и тёмная, со своей палитрой: бирюзово-зелёный акцент на сине-серых поверхностях. Убраны Material Design 2, выбор цвета темы, цвета из обоев и чёрный фон тёмной темы;
- убрана анимация появления файлов в списке и настройка «Анимация списка файлов»;
- верхняя панель списка файлов не скрывается при прокрутке и всегда выделена цветом, отличным от фона списка;
- «Назад» в корне главного экрана не закрывает приложение сразу, а спрашивает: закрыть, свернуть или остаться;
- список файлов переписан на Jetpack Compose: полоса быстрой прокрутки видна всегда и без подсказки с буквой, обновление свайпом — индикатор Material 3, выделение без анимации;
- убраны переводы на баскский, галисийский, исландский, литовский, каталанский, норвежский, датский, финский, арабский, персидский и иврит, а с ними — зеркальная раскладка для языков с письмом справа налево; новые строки mftlrs переведены на все остальные 22 языка.

## Возможности

Унаследованы от Material Files:

- Хлебные крошки: удобная навигация по файловой системе.
- Root: просмотр и управление файлами с правами root или через Shizuku.
- Архивы: просмотр, распаковка и создание распространённых архивов.
- Сетевые хранилища: файлы на серверах FTP, SFTP, SMB и WebDAV.
- Темы: настраиваемые цвета интерфейса, ночной режим с чисто чёрным фоном.
- Знает Linux: символические ссылки, права доступа и контекст SELinux.
- Надёжность: работает через системные вызовы Linux, а не разбирает вывод `ls`.

## Сборка

```
./gradlew assembleDebug
```

APK появится в `app/build/outputs/apk/debug/`.

## Благодарности

mftlrs основан на [Material Files](https://github.com/zhanghai/MaterialFiles), Copyright (C) 2018 Hai Zhang, лицензия GNU General Public License v3.0.

- Копирайты оригинала в исходных файлах сохранены.
- В заголовке файлов upstream, изменённых для mftlrs, есть строка `Modified for mftlrs`, а сами правки помечены комментариями `mftlrs:`.
- Автор Material Files не отвечает за ошибки в mftlrs.

## Лицензия

Программа распространяется по лицензии GNU GPL версии 3 или (по вашему выбору) любой более поздней версии. Полный текст — в файле [LICENSE](LICENSE). Официальный текст уведомления:

    Copyright (C) 2018 Hai Zhang
    Copyright (C) 2026 mftlrs contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
