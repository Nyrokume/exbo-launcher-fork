# UI Showcase — подготовка презентации

Краткая карта экранов и как прогнать живой тур / снять PNG для HTML-слайдов (frontend-slides / talk deck).

## Живой showcase (с анимациями)

```bash
./gradlew run "-Dlauncher.showcase=true"
```

Опции: `launcher.showcase.size=1366x800`, `launcher.showcase.captions=true|false`,
`launcher.showcase.stills=<dir>` (PNG шагов первого круга), `launcher.showcase.loops=N` (0 = бесконечно).
Esc — выход. Белой темы в туре нет (только zone / exbo / shadcn).

Порядок одного круга: **регистрация** (возраст → форма) → **авторизация** (логин → вход) →
**лаунчер** (обои, темы, баннер, настройки, ЛК, сообщества, меню «⋯», загрузка / пауза / готово).

## Снять скриншоты автотуром

```bash
./gradlew run "-Dlauncher.screenshots=docs/screenshots/zone"
```

PNG появятся в `docs/screenshots/zone/`. Тема по умолчанию — тёмная `zone`.

## Слайды (порядок)

| # | Файл | Экран |
|---|---|---|
| 01 | `01_update_window.png` | Обновление лаунчера |
| 02 | `02_auth_window.png` | Вход (логотип EXBO, форма, Steam / Epic) |
| 03 | `03_auth_error.png` | Ошибка пустого логина |
| 04 | `04_main.png` | Главный экран + обои + баннер STALZONE |
| 05 | `05_profile_menu.png` | ЛК: баланс, пополнить, пригласить, кабинет, выход |
| 05b | `05b_logout_confirm.png` | Подтверждение выхода |
| 05c | `05c_community_menu.png` | Меню «Сообщества» |
| 06–07 | `06_settings.png`, `07_settings_min_size.png` | Настройки (полный / min) |
| 08 | `08_game_actions.png` | Меню «⋯»: файлы, папка, **логи**, настройки, переустановка, удаление |
| 08b | `08b_game_actions_delete_confirm.png` | Подтверждение удаления |
| 09–10c | running / verifying / downloading / paused | Состояния клиента |
| 11 | `11_resized_1366x800.png` | Адаптив |
| 12 | `12_settings_en.png` | EN |
| 13 | `13_auth_after_switch_account.png` | Снова вход после выхода |
| 14–17 | age gate / register / errors | Регистрация |
| 18 | `18_registered_login.png` | Уведомление «Аккаунт создан» (скрывается при вводе пароля) |
| 19 | `19_totp_error.png` | TOTP + тост ошибки |

## Тон презентации

- Тёмная zone-палитра, красный `#e91932`, Bebas Neue + Roboto
- Акцент на: два окна (auth 460×620 / main 1024×640), обои, ЛК как на сайте, меню действий с логами
- Не включать удалённую белую тему

## Артефакт для показа

Набор PNG всех экранов/состояний: `docs/screenshots/zone/` (Scene snapshot, без записи монитора).

Переснять:

```bash
./gradlew run "-Dlauncher.screenshots=docs/screenshots/zone" "-Dlauncher.animations=off"
```

Опционально позже: HTML deck по `frontend-slides` из этих PNG.
