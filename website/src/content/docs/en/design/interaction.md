---
title: Path
description: Branches from launch to exit, from INTERACTION.md and the current menu.
---

Two windows. Pre-main on `main` is 460×700. The main window is 1024×640. The source diagram is `docs/design/interaction.mmd`. The same branches are written out here: this site has no diagram plugin.

## Happy login

Update → login → an email code or an authenticator code when needed → main window → **PLAY**.

`-Dlauncher.mock2fa=none` skips the second factor. On live, the EXBO client response decides the second factor, not this property.

## Registration

Login → **Create** → age of at least 18 → form → login again with the nickname and the “created” notice → password.

A form error keeps you on registration. **← Back** returns to the age gate with the same date. Under 18 shows a refusal and stays on the gate.

## Login errors

An empty login, the password `wrong`, and the authenticator code `000000` show a field or a toast and do not open the main window. The email code `000000000` also stays in pre-main.

## Play

**PLAY** or the **⋯** menu moves the client through ready, downloading, paused, running, and verifying.

The **⋯** menu on `main`: verify files, open the game folder, game settings, reinstall, delete. Reinstall, delete, and verifying a running game ask for confirmation first. While the operation runs, **PLAY** is inactive. On mock the operations are `MockUpdateService` timers.

The folder opens only inside `gameRoot`. A path above the root is rejected. If the root is missing too, nothing opens and stderr gets a WARNING.

The mock online count drifts a little every 10 s by default. `-Dlauncher.onlineTickMs=0` keeps 1284. The tour always uses 0.

## Profile and exit

| Action | Where it goes |
|---|---|
| Communities | Discord, YouTube, Reddit, Telegram through `LinkPolicy` |
| Invite, account, top up | An external link, the same allowlist |
| Accounts | The settings page; the main window stays open |
| Log out | A modal. Cancel stays. Confirm returns to the login panel |
| Close or Alt+F4 | `Platform.exit()` with no account prompt |

`profile.fxml` has no separate “Switch account” item. Older README and `WINDOWS.md` paragraphs still mention one.

## What next

- [Navigation](../../architecture/navigation/) — which methods this calls;
- [Demo login](../../getting-started/mock-login/) — the strings that catch the errors.
