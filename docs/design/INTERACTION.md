# Диаграмма взаимодействий

Внешние ссылки (меню сообществ, «Личный кабинет», «Пригласить друга», PDF лицензии/политики) идут через
`LinkOpener` и allowlist `LinkPolicy` (`exbo.net`, `exbo.games`, `stalzone.com`, …). «Сменить аккаунт» убран:
назад во вход — только подтверждённый «Выйти». Обои выбирает `Wallpapers.choose` один раз за запуск и рисует
`WallpaperLayer`.

Исходник Mermaid: [`interaction.mmd`](interaction.mmd).

```mermaid
flowchart TB
  U([Пользователь])

  subgraph Header["Шапка главного окна"]
    H[СООБЩЕСТВА / ФОРУМ / ПОДДЕРЖКА]
    CM[Меню сообществ<br/>Discord · YouTube · Reddit · Telegram]
  end

  subgraph Profile["Меню профиля"]
    PA[Личный кабинет ↗]
    INV[Пригласить друга ↗]
    LO[Выйти]
  end

  subgraph Modals["Модалки"]
    LM[Модалка лицензии]
    LC[Подтверждение выхода]
  end

  subgraph Settings["Настройки"]
    ST[Тема / язык / ОЗУ / …]
  end

  WP[Выбор обоев<br/>Wallpapers.choose]
  WL[WallpaperLayer]
  LO_OP[LinkOpener<br/>+ LinkPolicy]
  BR[(Системный браузер)]
  AUTH[Окно входа]
  PDF[PDF: LICENSE / PRIVACY<br/>exbo.games]

  U --> H
  U --> Profile
  U --> ST
  U --> LM

  H -->|▾| CM
  CM -->|CommunityLinks| LO_OP
  H -->|Форум / Поддержка| NA[«Недоступно»]

  PA -->|ExternalLinks.ACCOUNT| LO_OP
  INV -->|ExternalLinks.REFERRAL| LO_OP
  LO --> LC
  LC -->|подтвердить| AUTH

  LM -->|licenseRef / confidentialityRef| LO_OP
  LO_OP --> BR
  LO_OP -->|LICENSE / PRIVACY| PDF

  WP --> WL
  WL --> MainWin[Главное окно]
```
