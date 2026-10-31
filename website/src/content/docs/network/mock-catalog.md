---
title: Mock classpath
description: Мок читает каталог туннеля из classpath. Live по-прежнему ходит на HTTPS.
---

`SettingsController` ставит `TunnelServices.mockCatalog = !ctx.liveBackend`.

На mock `defaultFetch` вызывает `TunnelCatalogs.bundled`. В сеть за списком мок не ходит.

| Регион | Откуда снимок |
|---|---|
| RU | classpath `servers-ru.json` |
| EU, NA, SEA, NEA | classpath `servers.json`, пулы с полем `region` |

NEA в `servers.json` — пулы `TYO` и `KOR`. Отдельной фикстуры `NEA1` страница не читает.

Нет файла или пустой регион — `bundled` возвращает null, страница показывает ошибку каталога. Поле `region` из `servers.json` перед разбором отбрасывается. Пулы `CLOUD-*`, включая `CLOUD-RU-7`, и адреса документации (`192.0.2.0/24`, `198.51.100.0/24`, `203.0.113.0/24`) в список, замер и «Найти лучший» не попадают. Узел, который остался в снимке и не ответил на пробу, тоже не рисуется.

На live `mockCatalog` ложен. `TunnelCatalogs.fetch` ходит только на уже заданные HTTPS-адреса региона (`TunnelCatalogs.sources`). Новых хостов эта ветка не добавляет. Редирект не следует. Ответ не 200, обрыв или превышение 1 МиБ — следующий источник, затем ошибка.

Замер UDP (`TunnelProbe`) и запись `overrides.json` этим выбором каталога не подменяются. «Измерить» файл не пишет. «Найти лучший сервер» пишет один узел.

## Что дальше

- [Loading/error/empty](../states/) — карточки, пока снимка нет;
- [Туннель layout](../../settings/tunnel/) — что пишется в `jvm_wrapper`;
- [AppContext](../../architecture/app-context/) — когда `liveBackend` вообще истинен.
