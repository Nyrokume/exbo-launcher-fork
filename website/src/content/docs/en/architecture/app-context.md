---
title: AppContext
description: The only place mock and live are chosen.
---

`AppContext` builds `Services` and holds session state (`ClientStateStore`). The UI receives `UiContext` from the controller factory in `Fxml.kt` and does not see the concrete data classes.

The domain does not know about the UI. Errors and phases are enums (`AuthError`, `UpdatePhase`). `ui/Messages.kt` turns them into strings.

## How LauncherApp chooses

```kotlin
val useExbo = !tour && demo == null && ExboApi().installed()
```

`tour` means the showcase (`-Dlauncher.showcase`) or the screenshot tour. `demo` is the `launcher.demo` property: if it is set, you stay on mock.

`ExboApi.installed()` is true when all three files exist under the EXBO client root:

- `bootstrap.jar`;
- `launcher/launcher.jar`;
- `java/bin/java.exe`.

The root is `ExboPaths.roamingRoot()`. stderr prints `backend: exbo server list` for live, and a 2FA-mode line for mock.

:::note
The README separates mock from live. Mock does not open a socket for the tunnel catalog. Live, when a client is installed, uses the HTTPS addresses that are already in the code. Live registration stays `NOT_SUPPORTED`, and self-update does not write a file.
:::

## mock

`AppContext.mock` installs `MockAuthService`, `MockServerRepository`, `MockUpdateService`, `MockGameLauncher`, `InMemorySettingsStore`, and `MockSystemInfo`.

The default `desk` is `memoryDesk()`: a temp directory and `MemoryRegistry`. That is not HKCU and not the installed EXBO files.

`liveBackend` stays `false`. “Start when the download finishes” does not launch the game by itself, and the exit mode is fixed at `ExitMode.NEVER`.

## live

`AppContext.live` installs `ExboAuthService`, `ExboServerRepository`, `LiveUpdateService`, `InstalledGameLauncher`, `ExboSettingsStore`, and `HostSystemInfo`.

The desk is a `StalzoneDesk` with `WindowsRegistry`, the sessions file, the JVM config directory, and `options.json`. `liveBackend = true`: start-after-download and the exit mode are read from settings.

Reinstall stays disabled on this path. A catalog client is fetched into the directory the official launcher already maps.

Rebuilding the scene (a language change) does not stop a download: `ClientStateStore` lives on `AppContext`, not on the controller. Each operation has a generation. A late callback from an old operation is ignored.

## What next

- [Navigation](../navigation/) — what the UI calls after the context is built;
- [Accounts](../../settings/accounts/) — where mock and live write sessions;
- [Launch flags](../../reference/gradle-flags/) — properties that force mock.
