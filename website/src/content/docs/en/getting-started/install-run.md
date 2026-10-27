---
title: Install and run
description: Gradle commands, the setup wizard, and what the distribution contains.
---

You need JDK 21. The wrapper is already in the repository root. You do not install Gradle separately.

## 1. Run the launcher

```bash title="repository root"
./gradlew run
./gradlew run -Dlauncher.lang=en
./gradlew build
./gradlew test
```

`test` is headless and does not need an X server. On a machine without a display:

```bash
xvfb-run -a ./gradlew run -Dlauncher.screenshots=$PWD/docs/screenshots/zone
```

`-Dlauncher.*` properties are listed under [launch flags](../../reference/gradle-flags/).

## 2. Capture screens

```bash
./gradlew run "-Dlauncher.screenshots=docs/screenshots/zone" "-Dlauncher.animations=off"
```

`ScreenshotTour` writes PNGs into that directory. The default theme is `zone`. A looping pass with motion is `-Dlauncher.showcase=true` (`docs/SHOWCASE.md`). Esc leaves the showcase.

You should see:

- `01_update_window.png` through `19_totp_error.png` in the directory;
- the tour wallpaper fixed at `03`, so the shots repeat.

If the window does not open, check in order:

- JDK 21 is installed and matches `JAVA_HOME`;
- a display is available; otherwise use `xvfb-run`.

:::tip
Stay on mock when you are arguing about layout. Live connects on its own only when an EXBO client is already installed.
:::

## Setup wizard

`LauncherFork_Setup_<version>.exe` installs the launcher for the current user and does not ask for administrator rights. Apps & features shows the name **EXBO Launcher**, the publisher **EXBO**, and the game **STALZONE**.

The screens follow the public EXBO install article:

1. Choose the launcher folder. The browse button is labeled Change Folder. The default is `%LOCALAPPDATA%\LauncherFork`.
2. Click Next. The final path always ends with `\LauncherFork`. `D:\Projects` becomes `D:\Projects\LauncherFork`. A silent install `/S /D=...` follows the same rule.
3. A Start Menu shortcut is always created. A desktop shortcut is optional: the checkbox starts checked, and a silent install creates it too.
4. Wait until copying finishes.
5. The last screen can launch the launcher. The game is downloaded from the launcher after you sign in.

Before it replaces files, and again after the copy, Setup checks the SHA-256 of every shipped file. A mismatch, a missing file, or PowerShell being unavailable stops Setup with exit code 3. The marker `.launcherfork-install` is not written in that case. File size is not enough for this check. A folder that already has `runtime` or `app` but not this marker is rejected with exit code 2.

A shortcut named EXBO Launcher is replaced or deleted only when it points at this `LauncherFork.exe`.

Uninstall leaves the game directory, `%APPDATA%\EXBO`, and `HKCU\SOFTWARE\EXBO` (`EXBO_SESSION`) in place. Setup does not write or delete them.

:::caution[An unsigned name matches the official launcher]
Apps & features lists the publisher as EXBO and the product as EXBO Launcher. This repository has no Authenticode certificate, so the files are not signed (TODO). This is an unofficial fork, and without a signature it is easy to mistake for the official ExboLauncher.
:::

The public help article does not include the original wizard artwork, a license page, or the official default directory, so this Setup does not copy them. The sidebar image is built from the fork icon.

On Windows, build the installer with `packaging/build-installer.ps1`. On Linux, `packaging/build-dist.sh` only compiles the exe. The wizard does not open there. Payload SHA-256 at install time is PowerShell on Windows.

## Distributions

`packaging/build-dist.sh` fills `dist/` (gitignored):

| File | What it is |
|---|---|
| `LauncherFork_Setup_<version>.exe` | NSIS wizard, x64, no administrator rights. Folder, Next, install. Default `%LOCALAPPDATA%\LauncherFork`. Apps & features name: EXBO Launcher |
| `LauncherFork_portable_win64.zip` | The same image; run `LauncherFork.exe` |
| `LauncherFork_linux_x64.tar.gz` | jlink image; run `bin/launcherfork` |
| `SHA256SUMS.txt` | Checksums |

`LauncherFork.exe` forwards its arguments to the JVM, for example `-Dlauncher.lang=en`.

On Windows, `packaging/build-installer.ps1` builds the same installer (JDK 21 with `jlink`; the script downloads OpenJFX and NSIS). On Linux, `packaging/build-dist.sh` only compiles the exe. The wizard steps and what uninstall leaves in place are in the section above.

:::caution[EXBO assets are in git history]
PNGs from the original launcher and the `Europe` font belong to EXBO. The README forbids public builds until those files are replaced. Deleting them from the work tree does not rewrite history.
:::

## What next

- [Demo login](../mock-login/) — how to pass pre-main without an EXBO account;
- [Themes](../themes/) — `-Dlauncher.theme`.
