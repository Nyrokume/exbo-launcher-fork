---
title: Overview
description: A visual fork of the EXBO launcher on Kotlin and JavaFX.
---

The fork ports the visual layer of the EXBO launcher (STALZONE / STALCRAFT). The stack is Kotlin 2.0, OpenJFX 21, JDK 21, and Gradle wrapper 8.10.2.

The default theme is `zone`. The main window is adaptive: 1024×640, minimum 900×580. At 1024 the left column is 253 px and then tracks 220–340 px. That layout replaced the old 886×550 for every theme.

## Where to go

| Section | Why |
|---|---|
| [Install](../install-run/) | Gradle and the 460×660 setup window under `%LOCALAPPDATA%\LauncherFork` |
| [Mock/live login](../mock-login/) | The demo password, live registration `NOT_SUPPORTED`, and RU / EU on live |
| [Localization](../../settings/localization/) | Item and weapon names in the client language file |
| [2FA](../two-factor/) | Mail 460×438, app 460×444 |
| [Update fail-closed](../update/) | The signature is not accepted. The window is 460×316 before show |
| [Home](../../game/home/) | The banner and the **⋯** menu |
| [Tunnel](../../settings/tunnel/) | Groups, ping, classpath on mock |
| [Limitations](../../reference/limitations/) | P2P, HTTPS seeds, DPAPI |

## What to read in the repository

- `README.md` — how to run, the directory map, and the layer rules;
- `docs/design/WINDOWS.md` — the two windows and the panel contract;
- `docs/design/STYLE-ZONE.md` — `zone` tokens (this site keeps a short summary);
- `docs/design/INTERACTION.md` — the player path;
- `docs/CHANGELOG.md` — history by pull request.

:::note
This site does not replace those files. When the code and an older paragraph disagree, the page names both sources.
:::

## Two data modes

`AppContext` is the only place service implementations are chosen. The UI sees the `domain/` interfaces and does not know whether the backend is mock or live.

The showcase, the screenshot tour, and the tests stay on mock. A normal launch on Windows switches to live when a RU client (`%APPDATA%\EXBO`) or an EU client (`%APPDATA%\Exbo_East`) is installed. Details are on the [AppContext](../../architecture/app-context/) page.

The README section that lists removed network pieces describes the mock layer. It does not cancel the live path.

## What next

- [Install and run](../install-run/) — Gradle commands;
- [Demo login](../mock-login/) — passwords and codes on mocks;
- [Windows](../../architecture/windows/) — stage sizes.
