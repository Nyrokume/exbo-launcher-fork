---
title: Changelog
description: A summary of docs/CHANGELOG.md and the main commits after that file.
---

`docs/CHANGELOG.md` numbers pull requests into `main`. Dates there are Yekaterinburg (UTC+5). This page is a short retelling, not a replacement.

## Not released, inside the history file

The login window from `redesign/login-socials`: height follows the panel, the EXBO logo replaces the title, links and errors use `-destructive-text`, and empty-field hints meet AA. Wallpapers behind the login window were tried and removed. Height is recomputed only when the panel changes layout (`AuthWindowRefitTest`).

The setup window is `SetupWindow`, scene **460×660**, not `fittedWindowHeight()`. Next copies files with the silent NSIS step. Uninstall stays the NSIS confirm page. Page: [install](../../getting-started/install-run/).

Shell and tunnel (#41): the column is **253 px** at 1024×640 and **220–340 px** as the window changes width, settings fill the content area, the mock catalog comes from the classpath, and loading, error, and an empty list are separate cards. The update window is **460×316** before it is shown. Live registration is `NOT_SUPPORTED`. Self-update is fail-closed until a release key is pinned. P2P and the speed limits are saved and do not affect the torrent.

The RAM scale never goes above 8 GB. The first JVM card is “No JVM”: it clears the wrapper’s active profile. The grid keeps 8 kHz, v1.1.2, v1.1.0, v1.0.8, v1.0.7, v1.0.4, and Aikar. The line under the summary names the CPU, the GPU, the cores, the threads, and the RAM.

Localization is its own header item. **Write** changes `item.*.name` in the RU client language file and the copy in `item-names.json`. On live, RU and EU sit above the login fields (`%APPDATA%\EXBO` and `%APPDATA%\Exbo_East`). A missing EU install leaves the current choice. The property is `launcher.realm`.

After the catalog, the tunnel probes nodes and draws only the ones that answered. `CLOUD-*` pools and documentation addresses never reach the list. NEA on mock is `TYO` and `KOR` from `servers.json`.

Pages: [windows](../../architecture/windows/), [shell](../../architecture/shell/), [tunnel](../../settings/tunnel/), [JVM](../../settings/jvm/), [localization](../../settings/localization/), [login](../../getting-started/mock-login/), [limitations](../../reference/limitations/).

## 2026-10-07

- STALZONE game description on the banner (#13): title, lead, subheading, body, and closing line, ru and en. Online is shown once, in the games column.
- White theme (#12): `zone-light` and a settings switch. The property `launcher.theme=zone-light` is now treated as `zone`. See [themes](../../getting-started/themes/).

## 2026-10-06

- “SETTINGS” aligned with the status row (#11).
- Settings contrast and sizes to WCAG AA (#10).
- The game folder cannot sit above `gameRoot`; percent for verify and reinstall (#9).
- Settings on one screen, the RAM bar, and the “⋯” menu (#8).
- Online count in the games list (#7), the Communities menu (#6), the profile menu no longer pushes header links (#5).
- Icon: relative generator paths, a multi-size `.ico` (#4).
- Zone style for the email-code window (9 digits) and the update window (#3).
- Zone redesign: STALZONE style, a separate login window, a vector logo, the profile menu (#2).
- Login panel in the stalzone.com/login style (#1).

## In git after the history file

`docs/CHANGELOG.md` does not list these commits yet. Dates are the `main` commit dates.

| Date | Commit | What changed in the code |
|---|---|---|
| 2026-10-24 | `5fcc81f` | Connect to the installed EXBO client: `AppContext.live` |
| 2026-10-24 | `ca46ea7` | Tunnel as a Roxy override (`overrides.json`, `-Droxy_address_override`). JVM card subtitles removed. The session key is an icon |
| 2026-10-24 | `5fb69fd` | Measure does not write `overrides.json`. Find best server writes one node for the region |
| 2026-10-27 | `4a7351e` | Columns are named by the path they measure. The node Find best server would save is marked |
| 2026-10-27 | `b12c51d` | Each server shows an open or closed lock instead of the metrics paragraph |
| 2026-10-29 | `3aebdf1` | JavaFX setup window instead of the wizard pages. NSIS copies files after Next |

Those commits are written up on [AppContext](../../architecture/app-context/), [tunnel](../../settings/tunnel/), [JVM](../../settings/jvm/), and [accounts](../../settings/accounts/).

## What next

- [Overview](../../getting-started/overview/) — where to look when the file and the code drift again.
