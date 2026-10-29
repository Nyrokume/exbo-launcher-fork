---
title: Path
description: Branches from launch to exit, from INTERACTION.md and the current menu.
---

Two windows. Pre-main is 460 px wide and as tall as its panel (the launcher update is **460×316** before it is shown, ceiling 700). The main window is 1024×640: the side column is **253 px** there and then **220–340 px** as the window changes width. The source diagram is `docs/design/interaction.mmd`. The same branches are written out here: this site has no diagram plugin.

## Happy login

Update → login → an email code or an authenticator code when needed → main window → **PLAY**.

`-Dlauncher.mock2fa=none` skips the second factor. On live, the EXBO client response decides the second factor, not this property.

On live, an offered launcher update is rejected until a release public key is pinned: the file is not written, and the window shows “The launcher update was rejected: it has no trusted signature.” With no offer, the check finishes and login opens. The mock still runs its timer and continues to login.

## Registration

Login → **Create** → age of at least 18 → form → login again with the nickname and the “created” notice → password. That is the mock branch.

A form error keeps you on registration. **← Back** returns to the age gate with the same date. Under 18 shows a refusal and stays on the gate.

On live the same steps reach the form and stop: `RegisterError.NOT_SUPPORTED`, and the text “This launcher cannot create an account. Create it on the STALZONE site.” There is no registration request. The mock branch above is unchanged.

## Login errors

An empty login, the password `wrong`, and the authenticator code `000000` show a field or a toast and do not open the main window. The email code `000000000` also stays in pre-main.

## Play

**PLAY** or the **⋯** menu moves the client through ready, downloading, paused, running, and verifying.

The **⋯** menu on `main`: verify files, open the game folder, game settings, reinstall, delete. Reinstall, delete, and verifying a running game ask for confirmation first. While the operation runs, **PLAY** is inactive. On mock the operations are `MockUpdateService` timers.

The folder opens only inside `gameRoot`. A path above the root is rejected. If the root is missing too, nothing opens and stderr gets a WARNING.

The mock online count drifts a little every 10 s by default. `-Dlauncher.onlineTickMs=0` keeps 1284. The tour always uses 0.

## Shell and tunnel

The logo scales with the column, and the game subtitle stays inside the row. The settings column fills the content area; the 820 px cap is gone. At 1280 px the tunnel page uses the full content width.

The heading, the action panel, and the group cards are separate blocks. The **To you** / **Ping** columns and the block toggles stay in place. Labels fit in full at 900×580 and 1280×760.

Mock shows the classpath catalog (`servers-ru.json`, `servers.json`, the NEA fixture) and does not fetch that list. **Loading groups…**, **Could not load groups**, and an empty catalog are different cards. The error card offers **Retry**. **Cancel** on the group-block confirm does not touch the firewall.

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
