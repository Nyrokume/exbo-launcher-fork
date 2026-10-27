---
title: Windows
description: The borderless pre-main window and the 1024×640 main window.
---

Launch opens the update window, then login, a code, or registration in the same window. After a successful login that window hides and the main window opens. Zero windows would quit JavaFX, so the main window is shown before the login window hides.

## Sizes on main

`Theme.AUTH_WINDOW_HEIGHT` is 700. Zone panels in FXML use `prefHeight="668"` at width 460. The title strip is 32 px, so the window is **460×700**. It does not resize. The stage style is `TRANSPARENT`. A 10 px clip rounds the corners (`Theme.AUTH_WINDOW_RADIUS`, the same as `.zone-radius-md`).

The main window is `StageStyle.UNDECORATED`, **1024×640**, resizable from the edges, minimum **900×580**.

:::note[WINDOWS.md disagrees]
`docs/design/WINDOWS.md` still describes height 620 and `prefHeight` 588. FXML and `Theme.kt` on `main` use 668 and 700. The README, `INTERACTION.md`, and `SHOWCASE.md` say 460×700, which matches the code.
:::

:::note[Open PR #20]
Branch `ui/auth-compact` is not merged. There the login window is **460×506**. The email code is **460×438** without an error and **460×526** with one. The authenticator code is **460×444** and **460×532**. Registration, age, and update stay **460×700**. The Steam / Epic row is hidden (`visible=false`, `managed=false`): the nodes and handlers stay, and the row collapses. On `main` that row is still on the login panel.
:::

## What each window holds

| | Pre-main | Main window |
|---|---|---|
| Stage | primary, `TRANSPARENT` | `StageNavigator.mainStage`, `UNDECORATED` |
| Frame | `auth_window.fxml`, `AuthWindowController` | `window.fxml`, `WindowController` |
| Content | update, login, email code, authenticator code, age, registration | `main.fxml` in `contentHost`, dialogs in `overlayLayer` |

The pre-main title strip has the logo, drag without maximize, minimize, and close. If the panel has `.zone-logo`, the small `authLogo` hides. The login panel centers the EXBO vector (`#zoneLoginLogo`, 150 px) on `-card-description`.

The close glyph is `-titlebar-foreground` at rest. On hover the background is `-window-close` (`#e91932`) and the glyph is white.

## Files the navigator looks up

| Method | New file | Fallback |
|---|---|---|
| `showLauncherUpdate` | `ui/update_panel.fxml` | `ui/update.fxml` |
| `showAuth` | `ui/auth_panel.fxml` | `ui/initial_auth.fxml` |
| `showMailAuth` | `ui/mail_auth_panel.fxml` | `ui/mail_auth.fxml` |
| `showTotpAuth` | `ui/totp_auth_panel.fxml` | — |
| `showAgeGate` | `ui/age_gate_panel.fxml` | — |
| `showRegister` | `ui/register_panel.fxml` | — |
| `showRegistered` | `showAuth()` again, then `InitialAuthController.showRegistered` | |

The navigator checks the classpath for the new file. Otherwise it loads the legacy file. A panel may keep its own window buttons hidden. The `fx:id` values `closeButton`, `hideButton`, and `header` stay for the controller.

## Panel contract

`PanelHost` sizes the panel root the way a Scene sizes its root. A panel that can grow gets exactly the area under the title strip and does not slide under it. A fixed-size legacy panel is centered.

The controller receives `UiContext` from a `(UiContext)` constructor. The update panel implements `UpdatePanel.start(done)`. If it does not, the navigator calls `afterUpdate` immediately.

Login panels call `showMain()`, `showMailAuth()`, `showTotpAuth()`, or show an error themselves. The shared branch is `AuthScreenBase.handle`. `dispose()` drops timers when the panel is replaced, on login, and on close.

Colors come only from tokens. A node whose `fx:id` matches an old CSS id needs an explicit `id`, or the PNG rules in `lang_*.css` attach to it.

## What next

- [Navigation](../navigation/) — call order and dialogs;
- [Demo login](../../getting-started/mock-login/) — what to type into the panels.
