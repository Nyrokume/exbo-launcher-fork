---
title: Localization
description: Search item and weapon names and write them into the client language file.
---

**LOCALIZATION** in the settings header replaces the column. The profile menu has no such item. The list scrolls inside the page, the same way the tunnel and sensitivity do.

## What is on screen

The top row, left to right: **Weapons**, **Items**, **Changed**, a “Search” field, and **Write**. The button tooltip is “Write the names into the game language file”. The button stays disabled while the file is missing.

A list row shows the stock name on the left and a field on the right. The field placeholder is “New name”. An empty search that matches nothing reads “Nothing found”. **Changed** keeps rows that already have a saved name or an unsaved edit.

A note sits under the list. While the file is read it says “Reading names…”. Once it is read it says “Weapons: N. Items: M.”. After a write it says “Saved: N”, where N is the number of stored renames. A failed write says “Could not write the names”. A missing file says “The game language file was not found”.

## Where the names come from

The page reads `modassets/assets/stalker/lang/<language>.lang` next to the client `options.json` under `%APPDATA%\EXBO\runtime\stalcraft`. The language comes from `"language"` in `options.json`. Accepted values are `ru`, `en`, `es`, `fr`, and `ko`. Anything else, and a missing options file, uses `ru`.

The list keeps `item.*.name` lines. Weapons are `item.wpn.*` and `item.melee.*`. The other keys of that shape are items. A name is cut at 120 characters, and line breaks are dropped.

## Where it is written

**Write** changes only the keys you touched. A blank field or the stock name drops the rename and, when the file still holds the custom text, puts the previous name back. The other lines of the file, including their line breaks, stay as they were.

The copy lives in `%LOCALAPPDATA%\stalcraft-nyrokume\item-names.json` (or `AppData\Local` under the user profile when the variable is missing). After a client download, verify, or reinstall, `restoreAfterUpdate` reads the fresh lang file and puts the saved names back. A key the new file no longer has drops out of the copy. A new stock name becomes the baseline of the edit.

On mock there is no installed-client language file, so the note stays “The game language file was not found”.

## What next

- [Sensitivity](../sensitivity/) — the neighboring write into `options.json`;
- [JVM](../jvm/) — the wrapper profile in the same `%APPDATA%\EXBO` tree;
- [Limitations](../../reference/limitations/) — what the button does to the client file.
