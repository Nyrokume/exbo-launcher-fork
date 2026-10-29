---
title: 2FA (TOTP/mail)
description: Код из письма 9 ячеек и код из приложения 6 ячеек. Размеры окна из fittedWindowHeight.
---

<span class="zone-badge mock">mock</span> <span class="zone-badge live">live</span>

Второй фактор живёт в том же pre-main окне, что вход и обновление. Ширина 460, окно не ресайзится. Высоту считает `AuthWindowController.fittedWindowHeight()`: полоса 32 px плюс содержимое, в пределах 280…700. У `mail_auth_panel.fxml` и `totp_auth_panel.fxml` нет `prefHeight`, поэтому окно садится по карточке.

<div class="size-table">
<table>
<thead>
<tr><th>Панель</th><th>Покой</th><th>С тостом ошибки</th><th>Хост панели</th></tr>
</thead>
<tbody>
<tr><td>Код из письма</td><td>460×438</td><td>460×526</td><td>460×406 / 460×494</td></tr>
<tr><td>Код из приложения (TOTP)</td><td>460×444</td><td>460×532</td><td>460×412 / 460×500</td></tr>
</tbody>
</table>
</div>

Цифры одинаковы в ru и en. Пустая строка ошибки места не занимает. Текст ошибки входит в раскладку и поднимает окно. Пересчёт идёт, когда панель запросила раскладку, а не на каждом кадре (`AuthWindowRefitTest`).

## Что на панелях

Код из письма — 9 ячеек `CodeInput`. Код из приложения — 6 ячеек. «Вернуться ко входу» и Esc вызывают `showAuth()`.

На mock подходит любой код нужной длины, кроме нулей: `000000000` для почты и `000000` для приложения. Тексты: «Неверный код из почты. Введите его ещё раз.» и «Неверный код из приложения. Введите его ещё раз.».

На live второй фактор решает ответ клиента EXBO. Свойство `-Dlauncher.mock2fa` на live не переключает ветку. Какой код показать, выбирает `AuthScreenBase.handle`: `showMailAuth()` или `showTotpAuth()`.

## Как mock выбирает ветку

| Условие | Панель |
|---|---|
| Логин `2fa` | Всегда почта |
| Логин `totp` | Всегда приложение |
| `-Dlauncher.mock2fa=email` | Почта |
| `-Dlauncher.mock2fa=totp` | Приложение |
| `-Dlauncher.mock2fa=none` | Сразу главное окно |
| `-Dlauncher.mock2fa=random` | Почта или приложение, случайно |
| Автотур и витрина без свойства | `none` |

## Что дальше

- [Mock/live вход](../mock-login/) — пароль и регистрация;
- [Окна и размеры](../../architecture/windows/) — потолок 700 и clip 10 px;
- [Скриншоты](../../reference/screenshots/) — кадр `19_totp_error.png`.
