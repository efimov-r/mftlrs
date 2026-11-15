# Задачи

Что в работе и что дальше. Выполненное удаляется — история есть в git.

## В работе

- [ ] Выбрать, какие функции убрать и какие добавить — кандидаты: [features.md](features.md) (колонка «Убрать?») и [upstream.md](upstream.md) («Выводы», «Чего нет у MaterialFiles»)

## Дальше

- [ ] Проверить сборку: `.\gradlew.bat assembleDebug` и запуск на устройстве
- [ ] Добавить удалённый `upstream`: `git remote add upstream https://github.com/zhanghai/MaterialFiles.git`
- [ ] Подключить ACRA с диалогом отправки — [decisions.md](decisions.md); Firebase уже вырезан, места под ACRA помечены `TODO(mftlrs)` — [architecture.md](architecture.md), «Отчёты о падениях»
- [ ] Ребрендинг: иконка и баннер с надписью (`drawable-xhdpi/banner.png`, `art/banner.svg`, картинки fastlane), экран «О программе», README, описания и changelogs `fastlane/metadata`, `PRIVACY.md`. Firebase, `applicationId` и название `app_name` — сделано
- [ ] Решить, коммитить ли `gradle/gradle-daemon-jvm.properties`
- [ ] Обсудить хранение настроек: сложные значения (хранилища, закладки, сортировка, пути) лежат в SharedPreferences как Parcel → Base64, любое изменение этих классов без миграции молча сбрасывает настройки — [gotchas.md](gotchas.md), «Настройки хранятся как Parcel»
- [ ] Редизайн без боковой панели — что затронет: [architecture.md](architecture.md), «Боковая панель»
- [ ] Свои цвета темы; тему MD2 убрать
- [ ] TODO: какие ещё изменения интерфейса — от владельца
- [ ] TODO: заголовок лицензии для новых файлов mftlrs и отметка изменений в файлах upstream (требование GPLv3 при распространении)
