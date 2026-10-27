# Маршрут пользователя

Два окна: **pre-main** (обновление 460×316 → вход / регистрация / 2FA, высота по панели, потолок 460×700) и **главное** (игра, настройки, меню).  
Внешние ссылки (сообщества, ЛК, пригласить) — через `LinkOpener` + allowlist `LinkPolicy`.  
Назад во вход только через подтверждённый «Выйти» (отдельного «Сменить аккаунт» нет).

Исходник Mermaid: [`interaction.mmd`](interaction.mmd).

## Диаграмма

```mermaid
flowchart TB
  Start([Запуск лаунчера]) --> Update[Окно обновления<br/>update_panel]

  Update --> Auth[Панель входа<br/>auth_panel]

  Auth -->|Создать| Age[Возрастной гейт<br/>age_gate]
  Age -->|младше 18| AgeErr[Отказ / слишком молод]
  AgeErr --> Age
  Age -->|≥ 18 ПРОДОЛЖИТЬ| Reg[Регистрация<br/>register_panel]
  Reg -->|← Назад| Age
  Reg -->|ошибка формы| Reg
  Reg -->|аккаунт создан| AuthOk[Вход с ником<br/>+ notice «создан»]
  AuthOk --> Auth

  Auth -->|логин / пароль| AuthCheck{Второй фактор?}
  Auth -->|пустой / wrong| AuthErr[Ошибка на форме]
  AuthErr --> Auth

  AuthCheck -->|нет mock2fa=none| Main
  AuthCheck -->|email / 2fa| Mail[Код из письма<br/>mail_auth]
  AuthCheck -->|totp| Totp[Google Authenticator<br/>totp_auth]
  Mail -->|назад / Esc| Auth
  Mail -->|верный код| Main[Главное окно<br/>main 1024×640]
  Totp -->|назад / Esc| Auth
  Totp -->|000000| TotpErr[Тост ошибки]
  TotpErr --> Totp
  Totp -->|верный код| Main

  subgraph MainWin["Главное окно"]
    direction TB
    Home[Баннер STALZONE<br/>+ обои]
    Home --> Play[ИГРАТЬ / скачать]
    Play --> States[Состояния клиента<br/>ready · downloading · paused · running · verifying]
    Home --> Dots[Меню ⋯]
    Dots --> Actions[Проверить · папки · логи<br/>переустановить · удалить]
    Actions --> Confirm{Нужно подтверждение?}
    Confirm -->|да| ModalAsk[Модалка подтверждения]
    Confirm -->|нет| States
    ModalAsk -->|да| States
    ModalAsk -->|отмена| Home

    Home --> Settings[Настройки<br/>RAM · поведение · лимиты · язык]
    Home --> Communities[СООБЩЕСТВА ▾]
    Communities --> ExtSoc[Discord · YouTube<br/>Reddit · Telegram]
    Home --> Profile[Профиль ▾]
    Profile --> ExtAcc[ЛК · пригласить друга]
    Profile --> Logout[Выйти]
  end

  Main --> MainWin
  ExtSoc --> Browser[(Браузер)]
  ExtAcc --> Browser

  Logout --> LogoutModal[Подтверждение выхода]
  LogoutModal -->|Отмена| Home
  LogoutModal -->|Выйти| Auth

  Home -->|крестик / Alt+F4| Exit([Выход из приложения])
  Auth -->|крестик| Exit
```

## Кратко по веткам

| Ветка | Шаги |
|---|---|
| Счастливый вход | обновление → вход → (опц. 2FA) → главное → ИГРАТЬ |
| Регистрация | вход → Создать → возраст ≥ 18 → форма → notice на входе → пароль |
| Ошибки auth | пустой логин / `wrong` / TOTP `000000` → тост или поле с ошибкой, остаёмся в pre-main |
| Игра | ⋯ или ИГРАТЬ → прогресс / пауза / running / verify / delete с модалками |
| Выход из аккаунта | профиль → Выйти → подтверждение → снова панель входа |
| Выход из приложения | крестик любого окна / Alt+F4 → `Platform.exit()` без вопроса про аккаунт |

На live регистрация аккаунт не создаёт: после формы «Этот лаунчер не создаёт аккаунт. Создайте его на сайте STALZONE.» Предложенное самообновление файл не пишет и показывает «Обновление лаунчера отклонено: нет проверенной подписи.» Mock-ветки таблицы те же.
