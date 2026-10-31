---
title: Navigation
description: Navigator methods, overlays, and the profile menu on main.
---

`StageNavigator` loads both frames and starts with `showLauncherUpdate(afterUpdate = showAuth)`, then shows the primary stage centered on the screen.

## Flow before the main window

1. The update panel is placed in `panelHost`. The controller’s `UpdatePanel.start(done)` runs.
2. `done` calls `showAuth()`. The login panel replaces the update in the same window.
3. An email code calls `showMailAuth()`. An authenticator code calls `showTotpAuth()`. Back and Esc return to `showAuth()`.
4. **Create** opens `showAgeGate()`. Age of at least 18 leads to `showRegister`. Back returns to the gate with the same date. Success calls `showRegistered(login)`: login again with the name filled in.
5. A successful login calls `showMain()`. The panel’s `dispose()` runs. The main window is shown on the screen that held the login window, then the login window hides.

The close button of either window, and Alt+F4, call `close()` with no account prompt: dispose, both stages hide, `Platform.exit()`.

## Navigator methods

```kotlin
interface Navigator {
    fun showLauncherUpdate(afterUpdate: () -> Unit = ::showAuth)
    fun showAuth()
    fun showMailAuth()
    fun showMain()
    fun switchLanguage(lang: String)
    fun minimize()
    fun toggleMaximize()
    fun close()
    val window: Window?
}
```

`switchLanguage` rebuilds the main screen and leaves the settings page open. `toggleMaximize` affects only the main window. `minimize` minimizes the active window.

On the settings screen the header hides the community links and shows `settingsNav`: **JVM**, **Tunnel**, **Sensitivity**, **Accounts**, **Localization**. RAM, behavior, the speed limit, and language stay one column and have no item in that row.

The newer methods (`showTotpAuth`, `showAgeGate`, `showRegister`, `showRegistered`) have default implementations. A test navigator does not have to write them.

## Dialogs

`showOverlay` / `hideOverlay` place a node in `overlayLayer`. Modal layers inside `main.fxml` (install, delete, logout) are blocked the same way:

- content under the dialog is disabled (`InputBlock.set`), so mouse and keyboard do not reach buttons under it;
- `.input-blocked` cancels Modena’s 0.4 opacity, so the look does not change;
- the content is cached (`CacheHint.SPEED`);
- focus moves to the first focusable node in the dialog.

Edge resize (6 px) does not steal clicks from minimize, maximize, and close: those bounds are cut out of the resize zone.

## Profile menu

`profile.fxml` on `main` contains the top-up card, **Invite a friend**, **Account**, **Accounts**, and **Log out**.

**Accounts** opens the settings page. It does not open the login window. **Log out** opens the `logout_pane` confirmation. Enter confirms, Esc cancels, and focus starts on Cancel. After confirmation, `MainController.dispose()` runs before the login window is shown. The login window is then centered on the screen that held the main window.

:::note[“Switch account” in older paragraphs]
`README.md` and `docs/design/WINDOWS.md` still describe a “Switch account” item that logs out immediately. `profile.fxml` has no such button. `INTERACTION.md` is closer to the code: the way back to login is a confirmed **Log out**.
:::

External links from the menus go through `LinkOpener` and the `LinkPolicy` allowlist: `https` only, no userinfo, and a host from the list (`discord.com`, `discord.gg`, `youtube.com`, `reddit.com`, `t.me`, `telegram.org`, `stalzone.com`, `stalzone.net`, `exbo.net`, `exbo.games`, `exbo.ru`, `vk.com`, `vk.ru`) or a subdomain of one of them. The game folder is opened by a separate `FolderOpener`, not by `LinkOpener`.

## What next

- [Path](../../design/interaction/) — the same branches as a table;
- [Windows](../windows/) — sizes and FXML.
