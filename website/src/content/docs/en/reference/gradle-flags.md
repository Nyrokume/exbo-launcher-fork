---
title: Launch flags
description: The -Dlauncher properties LauncherApp reads.
---

Pass them to `./gradlew run` or to `LauncherFork.exe`. These are properties of the launcher process, not the JVM preset JSON. Game presets are on the [JVM](../../settings/jvm/) page.

## Interface and data

| Property | What it does |
|---|---|
| `launcher.lang=ru\|en` | Language. Default `ru` |
| `launcher.theme=zone\|exbo\|shadcn\|frost` | Theme. Default `zone`. `zone-light` is treated as `zone` |
| `launcher.wallpaper=1..6` | Pins the wallpaper. Unset picks a random one that is not the previous. A number from the property is not stored in `wallpaper.last` |
| `launcher.settingsFile=<path>` | The `settings.properties` file. Default `~/.launcher-fork/settings.properties`. The showcase does not write a file |
| `launcher.mockSpeed=<number>` | How many times faster fake progress runs. Default 1 |
| `launcher.mock2fa=random\|email\|totp\|none` | Mock second factor. Default `random` outside a tour. A tour with the property unset uses `none` |
| `launcher.onlineTickMs=<ms>` | How often the mock online count drifts. Default 10000. `0` keeps 1284. A tour always uses 0 |
| `launcher.demo` | Any set value keeps mock, even when an EXBO client is installed |
| `launcher.dev=true` | F1–F7 on the main window. Esc works without the property |

## Showcase and stills

| Property | What it does |
|---|---|
| `launcher.showcase=true` | A screen loop on mock, animations on, nothing saved. It wins over the screenshot tour |
| `launcher.showcase.size=1366x800` | Showcase main-window size. Below 900×580 it is raised to the window minimum |
| `launcher.showcase.captions=true\|false` | Step caption in the title strip. On by default |
| `launcher.showcase.stills=<dir>` | PNGs of the first loop, without the caption |
| `launcher.showcase.loops=N` | Exit after N loops. `0` means forever |
| `launcher.screenshots=<dir>` | PNG tour. Animations stay off unless you set otherwise |
| `launcher.animations=on\|off` | Overrides the settings key `animations`. The screenshot tour forces `off`; the showcase forces `on` |

The showcase and the screenshot directory do not build live: `LauncherApp` treats both as a tour.

```bash
./gradlew run -Dlauncher.lang=en -Dlauncher.theme=shadcn
./gradlew run "-Dlauncher.showcase=true"
./gradlew run "-Dlauncher.screenshots=docs/screenshots/zone" "-Dlauncher.animations=off"
```

:::tip
Quote the whole `-D…=…` when the shell would expand slashes or stars in the value. The quotes do not belong to Gradle.
:::

## What next

- [Install and run](../../getting-started/install-run/) — the wrapper and the distribution;
- [AppContext](../../architecture/app-context/) — which of these flags keep mock.
