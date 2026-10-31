---
title: Install and run
description: Gradle commands, the 460×660 setup window, and what the distribution contains.
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

## Setup window

`LauncherFork_Setup_<version>.exe` installs the launcher for the current user and does not ask for administrator rights. Apps & features shows the name **EXBO Launcher**, the publisher **EXBO**, and the game **STALZONE**.

The window a person sees is `SetupWindow` (`dev.launcherfork.install.SetupWindowKt`). It uses the same pre-main frame as login: `StageStyle.TRANSPARENT`, not resizable, width 460, scene **460×660**, clip 10 px. `fittedWindowHeight()` does not size this window. The center is the vector EXBO mark and the line “Launcher installation”.

1. A folder field and **Change folder**. The default is `%LOCALAPPDATA%\LauncherFork`. The final path always ends with `\LauncherFork`: `D:\Projects` becomes `D:\Projects\LauncherFork`. A silent `/S /D=...` follows the same rule.
2. Two checkboxes start checked: a desktop shortcut and a startup shortcut. The Start menu shortcut is always created. It has no checkbox.
3. **Next** runs the same exe silently (`/S`, `/DESKTOP=`, `/STARTUP=`, `/D=`). The window shows the phases: checking files, copying, checking the installed copy, shortcuts. Under the phase is the current file name.
4. After exit code 0 the button becomes **Launch** and starts `LauncherFork.exe`. The game is downloaded from the launcher after you sign in.

A silent `/S` does not open this window. Without `/DESKTOP=` and `/STARTUP=` both shortcuts are created. A folder that already has `runtime` or `app` and no `.launcherfork-install` marker is rejected with exit code 2, and **Next** stays disabled on that path.

Before it replaces files, and again after the copy, the silent step checks the SHA-256 of every shipped file (`packaging/verify-payload.ps1`). A mismatch, a missing file, or PowerShell being unavailable stops setup with exit code 3. The marker is not written in that case. File size is not enough for this check.

A shortcut named EXBO Launcher is replaced or deleted only when it points at this `LauncherFork.exe`.

Uninstall is the NSIS confirm page, not the `SetupWindow`. It leaves the game directory, `%APPDATA%\EXBO`, `%APPDATA%\Exbo_East`, `HKCU\SOFTWARE\EXBO` (`EXBO_SESSION`), and `HKCU\SOFTWARE\Exbo_East` in place. Setup does not write or delete them. Its own install key is `HKCU\Software\LauncherFork`.

:::caution[An unsigned name matches the official launcher]
Apps & features lists the publisher as EXBO and the product as EXBO Launcher. This repository has no Authenticode certificate, so the files are not signed. This is an unofficial fork, and without a signature it is easy to mistake for the official ExboLauncher.
:::

On Windows, build the installer with `packaging/build-installer.ps1`. On Linux, `packaging/build-dist.sh` only compiles the exe. The setup window does not open there. Payload SHA-256 at install time is PowerShell on Windows. NSIS packs the payload and performs the silent copy. The install no longer has MUI wizard pages.

## Distributions

`packaging/build-dist.sh` fills `dist/` (gitignored):

| File | What it is |
|---|---|
| `LauncherFork_Setup_<version>.exe` | JavaFX setup window, 460×660, no administrator rights. NSIS packs the files and copies them after Next. Default `%LOCALAPPDATA%\LauncherFork`. Apps & features name: EXBO Launcher |
| `LauncherFork_portable_win64.zip` | The same image; run `LauncherFork.exe` |
| `LauncherFork_linux_x64.tar.gz` | jlink image; run `bin/launcherfork` |
| `SHA256SUMS.txt` | Checksums |

`LauncherFork.exe` forwards its arguments to the JVM, for example `-Dlauncher.lang=en`, and starts `javaw` with `--enable-native-access=ALL-UNNAMED`. With that flag the installed copy reads the CPU, the GPU, the cores, L3, memory speed, and large pages for the JVM presets. The setup window and `LauncherFork.cmd` use the same flag. The version in the setup file name comes from `version` in `build.gradle.kts`. That is `0.1.0` now: `LauncherFork_Setup_0.1.0.exe`.

On Windows, `packaging/build-installer.ps1` builds the same installer (JDK 21 with `jlink`; the script downloads OpenJFX and NSIS). On Linux, `packaging/build-dist.sh` only compiles the exe. The window steps and what uninstall leaves in place are in the section above.

:::caution[EXBO assets are in git history]
PNGs from the original launcher and the `Europe` font belong to EXBO. The README forbids public builds until those files are replaced. Deleting them from the work tree does not rewrite history.
:::

## What next

- [Demo login](../mock-login/) — how to pass pre-main without an EXBO account;
- [Themes](../themes/) — `-Dlauncher.theme`.
