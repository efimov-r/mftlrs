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
- [ ] Редизайн без боковой панели — что затронет: [architecture.md](architecture.md), «Боковая панель»
- [ ] Свои цвета темы; тему MD2 убрать
- [ ] TODO: какие ещё изменения интерфейса — от владельца
