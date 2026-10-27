---
title: Accounts
description: Session cards, the key icon, and the registry write.
---

**Accounts** is both a settings heading and a profile-menu item. Both open the same page and do not close the main window. This is not logout and not the demo login.

## What a card shows

With no saved sessions the page reads “No saved sessions”.

Otherwise each session is a card:

- on the left, the name and, when the snapshot has a non-empty `EXBO_LOGIN`, the line “Login: …”;
- a **Current** badge when the snapshot matches the desk’s live values;
- on the right, a key icon, **Apply**, and **Delete**.

The key is not the tunnel lock. It opens the snapshot’s stored values. An empty snapshot reads “No keys in the snapshot”. The card does not collapse after save: an open key stays open while you remain on the page.

Under the list are the “Session name” field and **Save**. Save captures the desk’s current values (`captureCurrent`). An empty name becomes `session N`.

**Apply** writes the session plan to the desk and shows “Session ‘…’ written to the registry”. **Delete** removes the row from `sessions.json` and shows “Session removed”.

## What apply writes

`ExboSessions.applyPlan` returns only `EXBO_AUTO_AUTH`, `EXBO_LOGIN`, and `EXBO_SESSION`. Apply forces auto-auth to `true`. Other snapshot values are not written to the registry by this button.

On live the file is `%LOCALAPPDATA%\stalcraft-nyrokume\sessions.json`. On Windows the `EXBO_SESSION` value in that file is sealed with DPAPI (`dpapi1:` and base64, current user). The token is not stored in the clear. The card and **Apply** see the opened token. `HKCU\SOFTWARE\EXBO` still holds the raw session string: the installed launcher and `/auth?session=` read it. That query is the EXBO protocol and is not moved into a body. On a machine without DPAPI a new token is not written to the file. On mock the desk is a temp directory and `MemoryRegistry`: the page does not open the Windows registry.

:::caution[Do not confuse this with the demo login]
The any-password mock login lives in `MockAuthService` and does not write the registry. Account cards write the `AppContext` desk. On the showcase and in tests that desk is temporary.
:::

## What next

- [Demo login](../../getting-started/mock-login/) — the panel before the main window;
- [Navigation](../../architecture/navigation/) — how **Log out** differs from this page.
