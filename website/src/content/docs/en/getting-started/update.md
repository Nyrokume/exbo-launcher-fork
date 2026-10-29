---
title: Update fail-closed
description: Any launcher update offer is rejected until a release public key is pinned. The window is 460×316 before it is shown.
---

<span class="zone-badge closed">fail-closed</span>

Launcher self-update does not write a file. `decideLauncherUpdate` and `stageLauncherUpdate` in `LauncherUpdateGate.kt` reject every offer as `unsigned` until a release public key is pinned in this repo. A matching SHA-256, or a file that is only large enough, is not a signature. `stageLauncherUpdate` returns false and does not create the destination.

The window shows `auth.update.rejected`: «Обновление лаунчера отклонено: нет проверенной подписи.» When there is no offer (`offer == null`), the decision is `Current`: the check finishes and login opens.

There is no key and no manifest in the repo. The comment in `LauncherUpdateGate.kt` leaves that as a TODO. Installer Authenticode is a different TODO and is not this check.

## Window size

`update_panel.fxml` has no `minHeight` and no `prefHeight`. The top card does not grow. `fittedWindowHeight()` is **460×316** (host **460×284**) in ru and en.

Cold start applies that height before `stage.show()`. `StageNavigator.fitAuthHeight` sizes the window while it is still hidden, so the scene does not open at the registration ceiling of 700 and then snap down. An empty error line takes no space. Error text grows the window inside 280…700.

`docs/screenshots/` has no separate frame shot after the 460×316 fit. The tour writes `01_update_window.png`. This page does not treat that file as a 460×316 measurement.

## Mock

On mock the update panel runs its timer and calls `afterUpdate` (login). The signature rejection is the live branch, where an offer is considered at all.

## Next

- [Windows and sizes](../../architecture/windows/) — the other pre-main panels;
- [Limitations](../../reference/limitations/) — registration and seeds next to this check;
- [Install](../install-run/) — NSIS Authenticode.
