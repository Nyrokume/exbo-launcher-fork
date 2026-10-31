---
title: Demo login
description: Mock auth and registration rules. Live does not create an account from this form.
---

While `LauncherApp` built `AppContext.mock`, any login and password are accepted. That path does not use the network. `-Dlauncher.mock2fa` picks the second factor.

## Password and login

| Input | What the mock does |
|---|---|
| Any login and password | Login continues; the second factor decides the next panel |
| Password `wrong` | “Wrong login or password. Check them and try again.” You stay on the login panel |
| Login `2fa` | Always the email code, in every `mock2fa` mode |
| Login `totp` | Always the authenticator code (TOTP / Google Authenticator), in every `mock2fa` mode |

## Second factor

`-Dlauncher.mock2fa`:

| Value | Behavior |
|---|---|
| `random` | Default outside a tour: each login picks an email code or an authenticator code at random |
| `email` | Always the email code |
| `totp` | Always the authenticator code |
| `none` | Straight to the main window |

The screenshot tour and the showcase use `none` when the property is unset.

The email code has 9 cells. Any 9-digit code works except `000000000`, which shows “Wrong email code. Enter it again.”

The authenticator code has 6 cells. Any 6-digit code works except `000000`, which shows “Wrong app code. Enter it again.” **Back to login** and Esc return to the login panel.

## Registration

**Create** on the login panel:

1. Age: day, month, year. You must be at least 18. Younger shows a refusal and keeps the date.
2. The form: email, a name of 3–16 characters `A-Z a-z 0-9 _`, a password of at least 8 characters, and the license consent.
3. Success returns to login with the name filled in and the notice “Account … created”. The notice hides when you start typing the password.

The name `taken` shows “Name taken”. The email `taken@…` shows “This email is already registered”. **← Back** and Esc from the form return to the age gate and keep the date.

On live, **Create**, the age gate, and the field checks are the same, and no account is created. `ExboAuthService.register` returns `RegisterError.NOT_SUPPORTED`. The form shows “This launcher cannot create an account. Create it on the STALZONE site.” (`fork.reg.error.unavailable`). This repository has no registration API. The mock rules above are unchanged.

## RU and EU

On live, **RU** and **EU** sit above the login fields. Mock and the showcase have no row: `ctx.realms` is set only in `AppContext.live`.

| Button | Install | Session registry |
|---|---|---|
| RU | `%APPDATA%\EXBO` | `HKCU\SOFTWARE\EXBO` |
| EU | `%APPDATA%\Exbo_East` | `HKCU\SOFTWARE\Exbo_East` |

EU in code is `ExboRealm.GLOBAL`. The choice is written to the fork settings file under `exbo.realm` (`ru` or `global`). The next launch reads `-Dlauncher.realm`, then that key. The requested install is used when it is present. Otherwise RU, when that install exists, otherwise EU.

A missing EU folder shows “The EU launcher is not installed. Its files belong in the Exbo_East folder. The EXBO folder is left as it is.” The current install stays. A successful switch drops the in-memory session and fills the login from the new install’s registry. When auto-login is accepted, the main window opens.

Auth and the server list talk to the selected install’s hosts. EU is `https://launcher-global.stalcraft.net:443`. The JVM catalog, `options.json`, and the [localization](../../settings/localization/) page read the `%APPDATA%\EXBO` tree. The [accounts](../../settings/accounts/) page still writes the snapshot to `HKCU\SOFTWARE\EXBO`.

## Main-window keys

With `-Dlauncher.dev=true`: **F1** launcher update, **F2** license, **F3** offline, **F4** Windows Defender, **F5** “a download is already running”, **F6** error, **F7** spinner. Without the property those keys are off.

**Esc** closes modal layers always.

:::tip
Login `totp` and code `000000` is the shortest way to open the error toast without depending on `random`.
:::

## What next

- [Windows](../../architecture/windows/) — where these panels are placed;
- [Accounts](../../settings/accounts/) — session snapshots, a different screen.
