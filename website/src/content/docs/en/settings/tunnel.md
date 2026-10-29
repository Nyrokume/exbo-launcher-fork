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
- an icon-only circular refresh control (SVG) spins on press and reloads the list. A server that was not in the previous list stays hidden until it answers. A miss drops the node from the pool;
- the mode menu: **Ping**, **Client RTT**, **Server RTT**, **Stability**. It is an in-page list, not a native combo.

Above the rows, the column captions are **To you**, **Ping**. The column Find best server minimizes is marked. Stability still reads the ping column. Those columns and the block toggles sit in the group card. Labels fit in full at 900×580 and 1280×760.

The heading, the action panel, and the group cards are separate blocks, with a gap between them. The settings column fills the content area: the 820 px cap is gone. At a 1280 px window the page uses the full content width. Sidebar width is on [Shell scale](../../architecture/shell/).

A group is a card: name, count of visible nodes, and **Exclude group** on the right (after it is applied, **Group excluded**). Pressing it asks for confirmation (“Block N nodes in this group?” / “Return N nodes in this group?”). **Cancel** closes that bar: exclusions, the blocklist, and the firewall stay as they were. A node switch changes one id immediately, with no dialog. Clicking the group name measures only that group. Rows with no valid IPv4 stay hidden. `CLOUD-*` pools are filtered out before the list, measure, and find-best.

A node row shows a lock (open, closed with the group, or at limit), a dot for “Find best server would save this node”, the name, and two figures. Clicking an available row writes that region’s override. A closed, excluded, or at-limit node cannot be selected.

Empty figures are `—`. No reply puts a label in the ping column. Row tooltips use the scale up to 50 ms, up to 100, and above 100. Loss and jitter for the hour are in the tooltip, not a separate paragraph.

## Catalog

Mock (`mockCatalog = !liveBackend`) reads the region list from the classpath: `servers-ru.json` for RU, `servers.json` for the other regions (pools whose `region` field matches), and the NEA fixture. Mock does not fetch that list. Live calls `TunnelCatalogs.fetch` over HTTPS. The catalog hosts are unchanged.

A missing catalog does not stay on “Loading groups…” over an empty list. Three states are their own cards:

- loading — “Loading groups…”, while the region list is still on the way;
- error — “Could not load groups” and a **Retry** button;
- empty catalog — “No working servers”. If there is no list yet and no error, the card is “Groups are not loaded yet” with **Load**.

On mock, an open region shows the fixture at once. The cards are also on [Loading/error/empty](../../network/states/) and [Mock classpath](../../network/mock-catalog/).

## Where it is written

The game reads one flag per region: `-Droxy_address_override.<region>=host:port`.

The choice and the search mode live in `jvm_wrapper/overrides.json`. The last 20 samples per node live in `jvm_wrapper/cache/tunnel_stats.json` and are dropped after 24 hours. On live the directory is `%APPDATA%\EXBO\jvm_wrapper`. On mock the desk is temporary, and the page does not touch the Windows profile.

Launch only checks the saved address. It does not fetch a catalog and does not probe.

## What next

- [JVM](../jvm/) — the neighboring client page;
- [P2P later](../../network/p2p/) — speed limits are not written into this file;
- [AppContext](../../architecture/app-context/) — why mock does not write the Windows profile.
