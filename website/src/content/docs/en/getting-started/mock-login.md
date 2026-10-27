---
title: Demo login
description: Mock auth rules, registration, and the dev keys.
---

While `LauncherApp` built `AppContext.mock`, any login and password are accepted. That path does not use the network. `-Dlauncher.mock2fa` picks the second factor.

## Password and login

| Input | What the mock does |
|---|---|
| Any login and password | Login continues; the second factor decides the next panel |
| Password `wrong` | “Wrong login or password”; you stay on the login panel |
| Login `2fa` | Always the mail code, in every `mock2fa` mode |
| Login `totp` | Always Google Authenticator, in every `mock2fa` mode |

## Second factor

`-Dlauncher.mock2fa`:

| Value | Behavior |
|---|---|
| `random` | Default outside a tour: each login picks mail or TOTP at random |
| `email` | Always the mail code |
| `totp` | Always the authenticator code |
| `none` | Straight to the main window |

The screenshot tour and the showcase use `none` when the property is unset.

The mail code has 9 cells. Any 9-digit code works except `000000000`, which shows “Invalid code!”.

Google Authenticator has 6 cells. Any 6-digit code works except `000000`, which shows the toast “CONFIRMATION ERROR”. **Back to login** and Esc return to the login panel.

## Registration

**Create** on the login panel:

1. Age: day, month, year. You must be at least 18. Younger shows a refusal and keeps the date.
2. The form: email, a name of 3–16 characters `A-Z a-z 0-9 _`, a password of at least 8 characters, and the license consent.
3. Success returns to login with the name filled in and the notice “Account … created”. The notice hides when you start typing the password.

The name `taken` shows “Name taken”. The email `taken@…` shows “This email is already registered”. **← Back** and Esc from the form return to the age gate and keep the date.

## Main-window keys

With `-Dlauncher.dev=true`: **F1** launcher update, **F2** license, **F3** offline, **F4** Windows Defender, **F5** “a download is already running”, **F6** error, **F7** spinner. Without the property those keys are off.

**Esc** closes modal layers always.

:::tip
Login `totp` and code `000000` is the shortest way to open the error toast without depending on `random`.
:::

## What next

- [Windows](../../architecture/windows/) — where these panels are placed;
- [Accounts](../../settings/accounts/) — session snapshots, a different screen.
