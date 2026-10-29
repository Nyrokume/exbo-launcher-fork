---
title: Mock classpath
description: Mock reads the tunnel catalog from the classpath. Live still fetches HTTPS.
---

`SettingsController` sets `TunnelServices.mockCatalog = !ctx.liveBackend`.

<span class="zone-badge mock">mock</span> `defaultFetch` calls `TunnelCatalogs.bundled`. Mock does not fetch that list from the network.

| Region | Snapshot |
|---|---|
| RU | classpath `servers-ru.json` |
| EU, NA, SEA | classpath `servers.json`, pools whose `region` field matches |
| NEA | an in-code fixture, not a live host: pool `NEA1`, addresses `203.0.113.10` and `203.0.113.11`, port 29450 |

A missing file or an empty region makes `bundled` return null, and the page shows a catalog error. The `region` field is dropped before parse. `CLOUD-*` pools, including `CLOUD-RU-7`, stay out of the list, measure, and find-best.

<span class="zone-badge live">live</span> `mockCatalog` is false. `TunnelCatalogs.fetch` talks only to the region's existing HTTPS addresses (`TunnelCatalogs.sources`). This branch adds no hosts. Redirects are not followed. A non-200, a drop, or a body over 1 MiB tries the next source, then fails.

The UDP probe (`TunnelProbe`) and the `overrides.json` write are not replaced by this catalog choice. Measure does not write the file. Find best writes one node.

## Next

- [Loading/error/empty](../states/) — cards while there is no snapshot;
- [Tunnel layout](../../settings/tunnel/) — what lands in `jvm_wrapper`;
- [AppContext](../../architecture/app-context/) — when `liveBackend` is true.
