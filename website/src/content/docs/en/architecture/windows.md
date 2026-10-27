---
title: Windows
description: The borderless pre-main window and the 1024×640 main window.
---

Launch opens the update window, then login, a code, or registration in the same window. After a successful login that window hides and the main window opens. Zero windows would quit JavaFX, so the main window is shown before the login window hides.

A rejected update stays in this window and shows “The launcher update was rejected: it has no trusted signature.” (`auth.update.rejected`): the file is not downloaded and not written.

## Sizes on main

The pre-main window is 460 px wide and does not resize. `fittedWindowHeight()` is the 32 px title strip plus the panel content, clamped to `AUTH_WINDOW_MIN_HEIGHT` **280** … `AUTH_WINDOW_HEIGHT` **700**. The stage style is `TRANSPARENT`. A 10 px clip rounds the corners (`Theme.AUTH_WINDOW_RADIUS`, the same as `.zone-radius-md`).

Panels with no `prefHeight` fit their content: login **460×506**, email code **460×438** (with an error **460×526**), authenticator code **460×444** (with an error **460×532**), launcher update **460×316** (panel area **460×284**). Registration and age set `prefHeight="668"` and keep the full **460×700** window.

The main window is `StageStyle.UNDECORATED`, **1024×640**, resizable from the edges, minimum **900×580**.

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
