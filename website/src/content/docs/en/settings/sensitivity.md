---
title: Sensitivity
description: A settings-header page. 1x base, a magnification table, and a write into options.json.
---

**ЧУВСТВИТЕЛЬНОСТЬ** in the settings header replaces the column. The profile menu does not have this item.

The page has a «База 1x» field, «Посчитать», and «Записать в options.json». «Посчитать» builds `ScopeSens.snapshot`. There are 14 magnifications: 1, 1.25, 1.5, 1.6, 1.8, 2, 2.4, 2.8, 3, 3.4, 3.5, 4, 6, 8. Each row is «Чувствительность в прицелах кратностью %s», with less / more.

The formula in `ScopeSens` is the one cited from stalcraft.wiki: `8 * (u/200/mag*0.6+0.2)^3 / 0.044 * mag`. The 1x base is solved back to an internal `u`, then the other magnifications are filled. Zero or an empty field shows «Введите базу 1x больше нуля». A finished calculation shows «Таблица посчитана».

«Записать в options.json» calls `StalzoneDesk.writeSensitivity`. The file must already exist. If it does not, the note shows `options.json is missing`. The write replaces `mouse_sensitivity` and `sensitivity_zoom_<magnification>` through `OptionsSens.patch`. Success is «Значения записаны в options.json».

On live the path is `%APPDATA%\EXBO\runtime\stalcraft\config\options.json`.

On mock the desk is the `memoryDesk()` temp directory. Until that directory contains `options.json`, the write refuses. Mock does not open the machine's game file.

## Next

- [Behavior](../behavior/) — RAM and language on the other column;
- [Accounts](../accounts/) — the same desk, a different file;
- [Limitations](../../reference/limitations/) — what settings do not have.
