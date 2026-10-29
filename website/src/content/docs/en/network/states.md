---
title: Loading/error/empty
description: Tunnel loading, error, and an empty catalog are separate cards. The error card has Retry.
---

While a region's list is missing, «Загружаю группы…» does not sit forever above a blank pane. Catalog states are their own cards in the group column.

| Card | Title | Also |
|---|---|---|
| Loading | «Загружаю группы…» | «Список этого региона ещё в пути». Only while the list is still on the way |
| Error | «Не удалось загрузить группы» | «Повторите загрузку. Для этого региона нет сохранённой копии.» Button «Повторить» |
| Empty | «Группы ещё не загружены» | «Нажмите «Загрузить», чтобы открыть список.» Button «Загрузить» |
| List arrived, no working nodes | «Нет рабочих серверов» | This is an empty parse of a catalog, not the waiting card |

«Повторить» and «Загрузить» fetch the region again. Those cards have no other buttons.

<span class="zone-badge mock">mock</span> When `TunnelServices.mockCatalog` is true (`!liveBackend`), the open region shows the classpath snapshot at once. The loading card does not stay up for that snapshot.

<span class="zone-badge live">live</span> `mockCatalog` is false. The list is `TunnelCatalogs.fetch`: the region's HTTPS hosts, redirects are not followed, a 10 s budget, a 3 s source timeout. Until the reply arrives, the loading card is shown. A network failure is the card with «Повторить».

`docs/screenshots/` has no frame of the tunnel after these cards. None is invented here.

The heading, the measure / find-best / refresh / mode bar, and the group cards are separate blocks. «До вас» and «Пинг» and the block toggles stay inside the group card. Labels fit in full at 900×580 and 1280×760.

## Next

- [Tunnel layout](../../settings/tunnel/) — what happens once the list is there;
- [Mock classpath](../mock-catalog/) — where the snapshot comes from;
- [Shell scale](../../architecture/shell/) — how wide the settings column is.
