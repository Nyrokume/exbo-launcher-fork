---
title: Servers
description: The game column on the left. Online is shown once, under the name.
---

The column is `servers_list.fxml`: logo, game list, settings button. Choosing a row calls `MainController.selectServer`: background, status, banner, and the banner lined up with that row. If settings had replaced home, a selection with `showHome` brings home back.

Online comes from the server model and is drawn on the card (`ServerEntryController`). The banner does not repeat it.

<span class="zone-badge mock">mock</span> On mock the count drifts slightly every 10 s by default. `-Dlauncher.onlineTickMs=0` holds 1284. The screenshot tour always passes 0.

<span class="zone-badge live">live</span> The list comes from the installed client (`ExboServerRepository`). A card carries id, caption, online, client size, description, run command, version, and seeds. This site does not invent list URLs. The reply is parsed by `parseServerCatalog` in `ExboProtocol.kt`.

At 1024×640 the column is 253 px and then tracks the window between 220 and 340 px. Details are on [Shell scale](../../architecture/shell/).

The settings button at the bottom of the column opens the settings column (`setSettingsVisible`). Pressing it again at the top of settings returns home.

## Next

- [Home](../home/) — the banner for the selected row;
- [Launch](../launch/) — the button under the status line;
- [Tunnel](../../settings/tunnel/) — a different page, Roxy regions, not the game list.
