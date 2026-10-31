---
title: Limitations
description: What the UI saves or shows without reaching the network, the torrent, or a new account.
---

## Live registration

`ExboAuthService.register` returns `RegisterError.NOT_SUPPORTED`. The form shows «Этот лаунчер не создаёт аккаунт. Создайте его на сайте STALZONE.» There is no registration request in this repo. The account is created on the STALZONE site. Mock is unchanged: a name other than `taken`, an email that is not taken, and age 18 or older still create an account on mock.

## Self-update

Every offer is rejected as `unsigned` until a release public key is pinned. The file is not written. The window says «Обновление лаунчера отклонено: нет проверенной подписи.» With no offer, login opens. The update window is 460×316 before it is shown. This repo has no Authenticode certificate, so the installer is not signed.

## Tunnel catalog

Mock (`mockCatalog`, that is, not live) reads the classpath: `servers-ru.json` and `servers.json`, including NEA (`TYO`, `KOR`). It does not fetch that list over HTTPS. `CLOUD-*` pools and documentation addresses never become rows. The probe then keeps only the nodes that answered. Live calls `TunnelCatalogs.fetch` on the region's HTTPS hosts. Redirects are not followed. The page does not add catalog addresses.

## P2P and the speed limit

The P2P positions and the KB/s fields are written to `settings.properties`. `SessionManager` does not receive them. They do not affect the torrent.

## Client seeds

`httpsWebSeeds` keeps HTTPS with no userinfo. `http://` drops out of the seed list. An empty seed list during file verify fails with `no seeds` and the line «Нет адресов загрузки, чтобы восстановить файлы.» Credential hosts in `ExboHosts.credential` are three HTTPS URLs. `ExboHosts.cleartext` sits beside them with `http://`. It is not sent: `exboBaseIsTrustedHttps` accepts only the credential list.

## Sessions

On Windows `sessions.json` stores `EXBO_SESSION` as `dpapi1:` plus base64, current user. Without DPAPI a new token is not written to the file. `HKCU\SOFTWARE\EXBO` stays the raw string: sealing it would send ciphertext to the installed launcher's session query. The setup window and the silent NSIS step do not write or delete that key.

## What is not on screen

There is no graphics page and no sound page. The tray switch and the start-with-the-computer switch are saved. Closing the window calls `Platform.exit()` and does not open a tray. On live, autostart and Defender are locked on the form.

## Two installs

On live, RU and EU sit above the login fields. With no `%APPDATA%\Exbo_East` folder you stay on the current install, and the form says “The EU launcher is not installed…”. Mock has no such row. The JVM catalog and the item-name language file are read from `%APPDATA%\EXBO` (or `AppData\Roaming\EXBO` in the user profile when the variable is missing). Choosing EU does not move the wrapper folder. **Apply** on the accounts page writes `HKCU\SOFTWARE\EXBO`. Setup and uninstall do not write or delete `%APPDATA%\EXBO`, `%APPDATA%\Exbo_East`, `HKCU\SOFTWARE\EXBO`, or `HKCU\SOFTWARE\Exbo_East`.

## Item names

**Write** on the localization page changes `item.*.name` lines in the RU client language file and stores a copy in `%LOCALAPPDATA%\stalcraft-nyrokume\item-names.json`. With no file the button is disabled and the line is “The game language file was not found”.

## Next

- [Mock/live login](../../getting-started/mock-login/) — the registration form and the RU / EU buttons;
- [Localization](../../settings/localization/) — what is written into the lang file;
- [Update fail-closed](../../getting-started/update/) — the window and the gate;
- [P2P later](../../network/p2p/) — the same fields in more detail.
