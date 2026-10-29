---
title: Accounts
description: The profile menu and the settings header open the same session page.
---

**Аккаунты** in the profile menu and the item in the settings header both call `settingsController.showSection("accounts")` and leave the main window open. This is not logout and not the login panel.

The page lists saved sessions: name, «Логин: …» when the snapshot has `EXBO_LOGIN`, a **Текущая** badge, a key, **Применить**, **Удалить**. An empty list says «Нет сохранённых сессий».

**Применить** writes `EXBO_AUTO_AUTH` (forced true), `EXBO_LOGIN`, and `EXBO_SESSION`. Other snapshot keys do not go out with that button. **Удалить** removes the row from `sessions.json`.

<span class="zone-badge live">live</span> The file is `%LOCALAPPDATA%\stalcraft-nyrokume\sessions.json`. On Windows `EXBO_SESSION` in that file is sealed with DPAPI (`dpapi1:`). `HKCU\SOFTWARE\EXBO` still holds the raw session string: the installed launcher reads it.

<span class="zone-badge mock">mock</span> The desk is a temp directory and `MemoryRegistry`. The any-password demo login does not write this file.

Leaving the account is a separate profile dialog («Выйти»). `profile.fxml` has no «switch account» item.

The card, key, and seal contract is on [Accounts in settings](../../settings/accounts/).

## Next

- [Home](../home/) — the profile menu next to communities;
- [Limitations](../../reference/limitations/) — DPAPI does not seal HKCU.
