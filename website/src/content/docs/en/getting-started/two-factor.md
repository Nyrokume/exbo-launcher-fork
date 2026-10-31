---
title: 2FA (TOTP/mail)
description: Mail code is 9 cells, app code is 6. Window sizes come from fittedWindowHeight.
---

The second factor stays in the same pre-main window as login and the launcher update. Width is 460. The window does not resize from the edges. `AuthWindowController.fittedWindowHeight()` is the 32 px title bar plus the panel, clamped to 280…700. `mail_auth_panel.fxml` and `totp_auth_panel.fxml` have no `prefHeight`, so the window hugs the card.

<div class="size-table">
<table>
<thead>
<tr><th>Panel</th><th>Idle</th><th>With the error toast</th><th>Panel host</th></tr>
</thead>
<tbody>
<tr><td>Mail code</td><td>460×438</td><td>460×526</td><td>460×406 / 460×494</td></tr>
<tr><td>App code (TOTP)</td><td>460×444</td><td>460×532</td><td>460×412 / 460×500</td></tr>
</tbody>
</table>
</div>

The numbers are the same in ru and en. An empty error line takes no space. Error text is part of the layout and grows the window. Height is refit when the panel asks for layout, not on every frame (`AuthWindowRefitTest`).

## What is on the panels

Mail is 9 `CodeInput` cells. The app code is 6 cells. «Вернуться ко входу» and Esc call `showAuth()`.

On mock, any code of the right length works except zeros: `000000000` for mail and `000000` for the app. The lines are «Неверный код из почты. Введите его ещё раз.» and «Неверный код из приложения. Введите его ещё раз.».

On live, the EXBO client reply chooses the factor. `-Dlauncher.mock2fa` does not switch the live branch. `AuthScreenBase.handle` calls `showMailAuth()` or `showTotpAuth()`.

## How mock picks the branch

| Condition | Panel |
|---|---|
| Login `2fa` | Always mail |
| Login `totp` | Always the app |
| `-Dlauncher.mock2fa=email` | Mail |
| `-Dlauncher.mock2fa=totp` | App |
| `-Dlauncher.mock2fa=none` | Straight to the main window |
| `-Dlauncher.mock2fa=random` | Mail or app, at random |
| Showcase and screenshot tour with no property | `none` |

## Next

- [Mock/live login](../mock-login/) — password and registration;
- [Windows and sizes](../../architecture/windows/) — the 700 ceiling and the 10 px clip;
- [Screenshots](../../reference/screenshots/) — frame `19_totp_error.png`.
