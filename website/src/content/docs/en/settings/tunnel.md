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

Above the rows, the column captions are **Status**, **To you**, **Ping**. The column Find best server minimizes is marked. Stability still reads the ping column. Those columns and the block buttons sit in the group card. Labels fit in full at 900×580 and 1280×760.

The heading, the action panel, and the group cards are separate blocks, with a gap between them. The settings column fills the content area: the 820 px cap is gone. At a 1280 px window the page uses the full content width. Sidebar width is on [Shell scale](../../architecture/shell/).

A group is a card: name, count of visible nodes, and **Block** on the right (**Unblock** once every node is closed). The press writes the firewall immediately. There is no confirm bar. The row button does the same for one server. Clicking the group name measures only that group. Rows with no valid IPv4 stay hidden. `CLOUD-*` pools are filtered out before the list, measure, and find-best.

A node row shows the name, a status of **Open** or **Closed**, two figures, and a **Block** / **Unblock** button. A closed row is struck through and marked with a red bar on the left. Clicking an available row writes that region’s override. A closed, excluded, or at-limit node cannot be selected.

Empty figures are `—`. No reply puts a label in the ping column. Row tooltips use the scale up to 50 ms, up to 100, and above 100. Loss and jitter for the hour are in the tooltip, not a separate paragraph.

## Catalog

Mock (`mockCatalog = !liveBackend`) reads the region list from the classpath: `servers-ru.json` for RU, and `servers.json` for EU, NA, SEA, and NEA (pools whose `region` field matches). NEA in that file is the `TYO` and `KOR` pools. Mock does not fetch that list over HTTPS. Live calls `TunnelCatalogs.fetch` over HTTPS. The catalog hosts are unchanged.

Before rows, Measure, and Find best, `TunnelPresentation` drops `CLOUD-*` pools and addresses with no valid IPv4. The documentation ranges `192.0.2.0/24`, `198.51.100.0/24`, and `203.0.113.0/24` go with them.

An open region in a normal run (`probeOnLoad`) probes the nodes after the list arrives. The “Loading groups…” card stays up until only the nodes that answered remain. A miss is not drawn. A server that was not in the previous list stays hidden until it answers. Tests that set `probeOnLoad = false` paint the classpath snapshot at once, with no probe.

A missing catalog does not leave a permanent line over an empty list. The states are their own cards:

- loading — “Loading groups…”, while the list is on the way and while the probe runs;
- error — “Could not load groups” and a **Retry** button;
- empty catalog — “No working servers”. If there is no list yet and no error, the card is “Groups are not loaded yet” with **Load**.

The cards are also on [Loading/error/empty](../../network/states/) and [Mock classpath](../../network/mock-catalog/).

## Where it is written

The game reads one flag per region: `-Droxy_address_override.<region>=host:port`.

The choice and the search mode live in `jvm_wrapper/overrides.json`. The last 20 samples per node live in `jvm_wrapper/cache/tunnel_stats.json` and are dropped after 24 hours. On live the directory is `%APPDATA%\EXBO\jvm_wrapper`. On mock the desk is temporary, and the page does not touch the Windows profile.

Launch only checks the saved address. It does not fetch a catalog and does not probe.

## What next

- [JVM](../jvm/) — the neighboring client page;
- [P2P later](../../network/p2p/) — speed limits are not written into this file;
- [AppContext](../../architecture/app-context/) — why mock does not write the Windows profile.
