---
title: Install and run
description: Gradle commands, screenshots, and what the distribution contains.
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

## Distributions

`packaging/build-dist.sh` fills `dist/` (gitignored):

| File | What it is |
|---|---|
| `LauncherFork_Setup_<version>.exe` | NSIS, x64, no administrator rights, `%LOCALAPPDATA%\LauncherFork` |
| `LauncherFork_portable_win64.zip` | The same image; run `LauncherFork.exe` |
| `LauncherFork_linux_x64.tar.gz` | jlink image; run `bin/launcherfork` |
| `SHA256SUMS.txt` | Checksums |

`LauncherFork.exe` forwards its arguments to the JVM, for example `-Dlauncher.lang=en`.

:::caution[EXBO assets are in git history]
PNGs from the original launcher and the `Europe` font belong to EXBO. The README forbids public builds until those files are replaced. Deleting them from the work tree does not rewrite history.
:::

## What next

- [Demo login](../mock-login/) — how to pass pre-main without an EXBO account;
- [Themes](../themes/) — `-Dlauncher.theme`.
