---
title: Tunnel
description: The regional Roxy-override page on main.
---

**Tunnel** in settings replaces the launcher column with the whole area. The behavior source is `TunnelPage.kt` and the comment in `TunnelOverrides.kt`. This page does not describe firewall rules. On `main` a chosen node is stored as a Roxy address, not as a firewall script.

## What is on screen

The first row is the region: **RU**, **EU**, **NA**, **SEA**, **NEA**.

The second row, left to right:

- **Measure** probes the open region. It does not change the selected server or `overrides.json`;
- **Find best server** measures first when there is no fresh successful sample. It then writes the single node with the best score for the mode. No reply and at-limit nodes are not selected. Excluded groups are not candidates;
- **Refresh** reloads the list. A server that was not in the previous list stays hidden until it answers. A miss drops the node from the pool;
- the mode menu: **Ping**, **Client RTT**, **Server RTT**, **Stability**. It is an in-page list, not a native combo.

Above the rows, the column captions are **To you**, **To the game**, **Ping**. The column Find best minimizes is marked. Stability still reads the ping column.

A group is a card: name, count of visible nodes, and an **Exclude** lock on the right. The lock writes the group exclusion to `overrides.json` immediately, with no extra confirm. Clicking the group name measures only that group.

A node row shows a lock (open, closed with the group, or at limit), a dot for “Find best would save this node”, the name, and three figures. Clicking an available row writes that region’s override. A closed, excluded, or at-limit node cannot be selected.

Empty figures are `—`. No reply puts a label in the ping column. Row tooltips use the scale up to 50 ms, up to 100, and above 100. Loss and jitter for the hour are in the tooltip, not a separate paragraph.

## Where it is written

The game reads one flag per region: `-Droxy_address_override.<region>=host:port`.

The choice and the search mode live in `jvm_wrapper/overrides.json`. The last 20 samples per node live in `jvm_wrapper/cache/tunnel_stats.json` and are dropped after 24 hours. On live the directory is `%APPDATA%\EXBO\jvm_wrapper`. On mock the desk is temporary, and the page does not touch the Windows profile.

Launch only checks the saved address. It does not fetch a catalog and does not probe.

:::note[Open PR #21]
Branch `cursor/tunnel-settings-ui-4562` is not merged. There the group control reads **Exclude group**, and after it is applied **Group excluded**. Pressing it opens a confirm (“Block N nodes in the group?” / “Restore N nodes in the group?”). Cancel writes nothing. A separate node switch (red track means blocked) toggles one id immediately, with no dialog.

Rows with no valid IPv4 stay hidden. `CLOUD-*` pools are filtered out before measure and find-best. On `main` that confirm and that filter are absent: excluding a group writes the file immediately, as described above.
:::

## What next

- [JVM](../jvm/) — the neighboring client page;
- [AppContext](../../architecture/app-context/) — why mock does not write the Windows profile.
