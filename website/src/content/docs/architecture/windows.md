---
title: Окна
description: Pre-main без рамки и главное окно 1024×640.
---

Запуск открывает окно обновления, затем в том же окне вход, код или регистрацию. После успешного входа это окно скрывается и открывается главное. Ноль окон сразу завершило бы JavaFX, поэтому главное показывается раньше, чем прячется вход.

## Размеры на main

Ширина pre-main — 460, окно не ресайзится. Высоту считает `fittedWindowHeight()`: полоса 32 px плюс содержимое панели, в пределах `AUTH_WINDOW_MIN_HEIGHT` **280** … `AUTH_WINDOW_HEIGHT` **700**. Стиль stage — `TRANSPARENT`. Скругление даёт clip 10 px (`Theme.AUTH_WINDOW_RADIUS`, как `.zone-radius-md`).

Панели без `prefHeight` садятся по содержимому: вход **460×506**, код из письма **460×438** (с ошибкой **460×526**), код из приложения **460×444** (с ошибкой **460×532**), обновление лаунчера **460×316** (область панели **460×284**). Регистрация и возраст задают `prefHeight="668"` и занимают полное окно **460×700**.

Главное окно: `StageStyle.UNDECORATED`, **1024×640**, ресайз за края, минимум **900×580**.

## Что лежит в каждом окне

| | Окно до входа | Главное окно |
|---|---|---|
| Stage | primary, `TRANSPARENT` | `StageNavigator.mainStage`, `UNDECORATED` |
| Рамка | `auth_window.fxml`, `AuthWindowController` | `window.fxml`, `WindowController` |
| Содержимое | update, вход, код из почты, код из приложения, возраст, регистрация | `main.fxml` в `contentHost`, диалоги в `overlayLayer` |

Полоса заголовка pre-main: логотип, перетаскивание без разворачивания, «свернуть», «закрыть». Если у панели есть `.zone-logo`, маленький `authLogo` прячется. На панели входа по центру вектор EXBO (`#zoneLoginLogo`, 150 px) на `-card-description`.

Крестик в покое цвета `-titlebar-foreground`. При наведении фон `-window-close` (`#e91932`) и белый глиф.

## Какие файлы ищет навигатор

| Метод | Новый файл | Запасной |
|---|---|---|
| `showLauncherUpdate` | `ui/update_panel.fxml` | `ui/update.fxml` |
| `showAuth` | `ui/auth_panel.fxml` | `ui/initial_auth.fxml` |
| `showMailAuth` | `ui/mail_auth_panel.fxml` | `ui/mail_auth.fxml` |
| `showTotpAuth` | `ui/totp_auth_panel.fxml` | — |
| `showAgeGate` | `ui/age_gate_panel.fxml` | — |
| `showRegister` | `ui/register_panel.fxml` | — |
| `showRegistered` | снова `showAuth()` и `InitialAuthController.showRegistered` | |

Навигатор смотрит, есть ли новый файл в classpath. Иначе берёт legacy. Свои кнопки окна у панели можно оставить скрытыми. `fx:id` `closeButton`, `hideButton`, `header` для контроллера сохраняются.

## Контракт панели

`PanelHost` задаёт корню панели размер, как Scene задаёт своему root. Панель, которую можно растянуть, получает ровно область под заголовком и не залезает под него. Legacy фиксированного размера встаёт по центру.

Контроллер получает `UiContext` конструктором `(UiContext)`. Панель обновления реализует `UpdatePanel.start(done)`. Если не реализует, навигатор сразу вызывает `afterUpdate`.

Панели входа по результату вызывают `showMain()`, `showMailAuth()`, `showTotpAuth()` или показывают ошибку у себя. Готовая развилка — `AuthScreenBase.handle`. Таймеры снимает `dispose()` при замене панели, входе и закрытии.

Цвета только из токенов. Узлу, чей `fx:id` совпадает со старым CSS-id, нужен явный `id`, иначе подхватятся PNG-правила `lang_*.css`.

## Что дальше

- [Навигация](../navigation/) — порядок вызовов и диалоги;
- [Демо-вход](../../getting-started/mock-login/) — чем заполнять панели.
