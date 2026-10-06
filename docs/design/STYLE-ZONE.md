# STYLE-ZONE: тема `zone` (стиль сайта STALZONE)

Включается так: `-Dlauncher.theme=zone`. Файл с токенами: `src/main/resources/dev/launcherfork/ui/style/theme-zone.css`.
Он подключается к Scene сразу после `theme.css` (`Theme.apply`) и переопределяет значения токенов, семейства
шрифтов и группы радиусов. Разметка и CSS компонентов ссылаются только на токены, литеральных цветов в них нет.

Источники:
- **Визуальный стиль** взят с сайта [stalzone.com/start](https://stalzone.com/start): CSS-переменные сайта и
  мобильные скриншоты `start.jpg` и `menu.jpg`. Отсюда палитра, рамки, радиусы, CAPS-подписи и плитки.
- **Концепция и раскладка** взяты с эталонных скриншотов лаунчера STALCRAFT: колонка слева, hero, новости,
  разделы настроек, авторизация в оверлее.

## Палитра (токены)

Имена токенов те же, что в `theme.css`, поэтому компонент на токенах работает во всех трёх темах
(`exbo`, `shadcn`, `zone`). Дополнительные токены этой ветки есть во всех трёх файлах тем, это проверяет
`ThemeTokensTest`.

| Токен | zone | Источник на сайте | Где используется |
|---|---|---|---|
| `-background` | `#0a0a0a` | `--black-bg-color` | фон окна и контента |
| `-foreground`, `-text-strong`, `-text-label` | `#ece7e1` | тёплый off-white | заголовки, основной текст |
| `-muted-foreground`, `-text-link`, `-icon` | `rgba(202,185,166,.64)` | `--accent-500` | подписи, ссылки, стрелки ↗, глифы |
| `-placeholder`, `-text-dim`, `-text-status` | `rgba(202,185,166,.46)` | `--accent-400` | подсказки, статус, единицы |
| `-accent`, `-fill-subtle` | `rgba(202,185,166,.04)` | слабая заливка | hover, водяные знаки |
| `-fill-strong`, `-divider-light` | `rgba(202,185,166,.12)` | заливка .12 | разделители внутри панелей |
| `-secondary`, `-muted`, `-input`, `-tile` | `#121212` | `--black-btn-color` | плитки, вторичные кнопки, поля ввода |
| `-tile-hover` | `#1a1a1a` | | hover плитки |
| `-card` | `#181818` | `--card` (stalzone.com/login) | карточка входа, карточки новостей, группы настроек, модалки, выпадающее меню профиля |
| `-card-description` | `#0f0f0f` | `--card-description` | нижняя часть карточки входа, строки выпадающего меню |
| `-input-border` | `#2e2d2d` | рамка `.form__wrapper__input` | рамка 1px полей ввода (фокус: `-foreground`) |
| `-error` | `#e63030` | `--error-color` | текст ошибки и рамка поля с ошибкой |
| `-card-alt`, `-sidebar` | `#0f0f0f` | карточки `#0f0f0f` | колонка слева, вложенные карточки |
| `-popover` | `rgba(15,15,15,.97)` | | меню инструментов, тултипы |
| `-border`, `-divider`, `-segment-off` | `#2e2d2d` | рамка 1px | рамки плиток/полей/карточек, кольцо радио, линии у подписи раздела |
| `-border-hover` | `#525252` | hover-рамка (на сайте `rgba(82,82,82,.3)`) | рамка при наведении (сделана заметнее, чем на сайте) |
| `-primary`, `-ring`, `-destructive` | `#ff3b52` | красный акцент сайта | CTA, активный сегмент, точка радио, индикатор, live-точка, рамка фокуса |
| `-primary-hover` | `#ff5a6d` | | hover CTA |
| `-track` | `#1f1e1e` | | дорожка прогресса |
| `-titlebar` | `#0a0a0a` | шапка сайта | полоса заголовка окна (снизу рамка `-border`) |
| `-window-close` / `-window-close-hover` | `#ff3b52` / `#ff6276` | | крестик окна |
| `-heading-rule` | `#ece7e1` | | подчёркивание заголовка раздела, градиент в прозрачность |
| `-scrim` | `rgba(0,0,0,.55)` | | затемнение под оверлеем |

**Выбор акцента.** Взят `#ff3b52` с сайта, а не `#e93046` со скриншотов лаунчера. Рядом с тёплым off-white
и рамками `#2e2d2d` он выглядит естественно, а с эталоном лаунчера расходится лишь чуть по яркости.

Остальные токены из `theme.css` (`-overlay*`, `-shadow*`, `-text-*`) тоже переопределены, чтобы legacy-экраны
не выпадали из палитры.

## Радиусы

В JavaFX looked-up значения работают только для цветов, поэтому радиус не может быть токеном. Вместо этого:

1. Общая группа контролов (`.button, .text-field, .progress-bar …, .check-box > .box, .context-button`) в `zone`
   по умолчанию получает **5 px**. В `theme.css` у той же группы 0.
2. После неё идут утилитарные классы. Специфичность у них та же, поэтому выигрывает тот, что объявлен позже:
   - `.zone-radius-sm`: 5 px (кнопки, поля, плитки);
   - `.zone-radius-md`: 10 px (карточки, панели, модалки).

   Оба класса задают и `-fx-background-radius`, и `-fx-border-radius`.
3. В FXML ставим эти классы. Числовых радиусов и цветов в остальных CSS-файлах нет. Исключение — круги
   радио/тумблеров (кольцо и точка).

## Типографика

| Роль | Семейство (имя для JavaFX) | Кегль | Где |
|---|---|---|---|
| Вордмарк EXBO (текстом, сейчас логотип векторный) | `Bebas Neue Regular` (`.font-display-strong`) | 64 | — |
| Hero, заголовки разделов, заголовки модалок | `Bebas Neue Regular` (`.font-display`), UPPERCASE | 30–53 | «ДОБРО ПОЖАЛОВАТЬ» 53, «НОВОСТИ» 51, «ИГРА» 48 |
| Большие кнопки | `Bebas Neue Regular` | 24 | «ИГРАТЬ» (`.play-button`), «ВОЙТИ» (`.zone-primary`) |
| Заголовок окна входа | `Bebas Neue Regular` (`.zone-title`) | 35 | «ЗАХОДИ СКОРЕЕ, МЫ ЗАЖДАЛИСЬ» |
| CAPS-подписи и пункты меню | `Roboto Medium` (`.zone-caps`), UPPERCASE | 11–15 | пункты колонки, ссылки ↗, «— РАЗДЕЛ —», кнопки-плитки |
| Подпись настройки, вариант | `Roboto` | 15 | «Оперативная память», «При старте игры» |
| Описание | `Roboto` | 13 | описание игры |
| Статус, единицы | `Roboto` | 11–13, `-placeholder` | «Готово к запуску», «KB/s» |

Откуда что взято (CSS сайта `app-BADgqdUN.css`, копия в `site-ref/sz.css`):

- `:root { --font-body: "Roboto"; --beb: "Bebas Neue"; --bebcyrill: "Bebas Neue Cyrillic"; --roboto-cond: "Roboto Condensed" }`.
- `html,body { font-family: var(--font-body); letter-spacing: .05em }`. Пункты мобильного меню
  (`.dropdown-nav__menu__link`: `font-weight: 500; text-transform: uppercase`) наследуют Roboto, то есть
  **Roboto Medium** обычной ширины (wdth 100), с разрядкой .05em. Roboto Condensed сайт не подгружает
  (`@font-face` для него нет) и использует только в `.media-cabinet .b16-caps`.
- `h1,h2,h3,h4,h5 { font-family: var(--beb), sans-serif; letter-spacing: .1em }`, кнопки меню
  `.login` («ВОЙТИ», `var(--beb)`, 24px, `letter-spacing: .1em`) и `.play_free` («ИГРАТЬ БЕСПЛАТНО», `var(--beb)`,
  24px) набраны **Bebas Neue** (файл `BebasNeueRegular-*.woff2`, weight 400; кириллица в нём есть).
  `--bebcyrill` (Bebas Neue Cyrillic, более жирный рисунок 2010 года) сайт берёт только для промо-страниц
  (`.korabli__*`, `.blitz__*`) и заголовка cookie-окна.

Поэтому в лаунчере: заголовки, hero и большие кнопки — `Bebas Neue Regular` (так JavaFX называет семейство,
name ID 1), текст — Roboto, CAPS-пункты — Roboto Medium. Bebas ниже по высоте прописных (0.70 em против 0.81 em у
Oswald), поэтому кегли заголовков в `theme-zone.css` увеличены примерно в 1.15 раза. Шрифты лежат в
`ui/style/font/`, регистрирует их `Theme.zoneFonts`; Oswald удалён.

**Лицензии шрифтов (важно).**

- `BebasNeue-Regular.ttf` — Bebas Neue Regular 001.003 (Ryoichi Tsunekawa / Dharma Type), **взят с
  stalzone.com** (WOFF2 из ассетов сайта, распакован в TTF без изменения глифов). В метаданных шрифта (name ID 13)
  указано «licensed under the SIL Open Font License, Version 1.1», текст лицензии — `OFL-BebasNeue.txt`. Сверки с
  официальным дистрибутивом не было, поэтому **лицензия не подтверждена**: репозиторий должен оставаться
  приватным, а перед любым публичным релизом шрифт нужно заменить или подтвердить его лицензию (например, взять
  Bebas Neue из официального источника под OFL).
- **Bebas Neue Cyrillic** (`BebasNeueCyrillic-*.woff2` с stalzone.com, «Cyrillic by AA») **в репозиторий не
  положен**: его встроенная лицензия Dharma Type разрешает использование, но прямо запрещает распространение
  («You may not redistribute this font without permission») и изменение, в том числе конвертацию. Если всё же
  понадобится его более жирный рисунок, это допустимо только локально, в приватной сборке, и шрифт должен быть
  убран или очищен по лицензии до любого публичного релиза.
- Roboto (`Roboto-*.ttf`) — SIL OFL 1.1 (`OFL-Roboto.txt`); ширины глифов совпадают с вариативным Roboto сайта
  при wght 500 / wdth 100.

**Разрядка (letter-spacing .05–.1em).** В JavaFX CSS свойства letter-spacing нет. Пока что узлы, где нужна
разрядка, — это обычные Label/Text с UPPERCASE-текстом и классом `.zone-caps`. После слияния с веткой
`redesign/auth` их заменят на `SpacedLabel` (`ui/controls`, делает Front.end.master). Собственную реализацию
здесь не заводим.

## Компоненты (правила сайта → лаунчер)

- **Плитка / вторичная кнопка** (`.tile` + `.zone-radius-sm`): заливка `-tile`, рамка 1 px `-border`. Внутри
  иконка и CAPS-подпись. При наведении заливка `-tile-hover`, рамка `-border-hover`. Пример на сайте — кнопки
  LAUNCHER / STORE / STEAM; в лаунчере — кнопка инструментов, «Настройки», кнопки модалок.
- **Основная кнопка (CTA)** (`.btn-primary` + `.zone-radius-sm`): заливка `-primary`, текст
  `-primary-foreground` CAPS, hover `-primary-hover`. Пример — «ИГРАТЬ».
- **Подпись раздела между линиями** (`.section-label`): «— ВЫБЕРИТЕ ПЛАТФОРМУ —». Это CAPS `-placeholder`
  по центру, слева и справа линии 1 px `-divider`.
- **Пункт меню** (`.menu-item`): CAPS `-foreground` с разрядкой, справа стрелка ↗ (`-icon`) или красная
  live-точка (`-primary`). Так сделаны пункты колонки и ссылки Профиль / Форум / Поддержка.
- **Карточка** (`.card` + `.zone-radius-md`): заливка `-card`, рамка 1 px `-border`, может быть слабый водяной
  знак (`-fill-subtle`). Пример на сайте — «БАЛАНС: 0»; в лаунчере — карточки новостей, группы настроек,
  модалки.
- **Поле ввода** (`.text-field` / `.zone-input` + `.zone-radius-sm`): заливка `-input`, рамка 1 px `-border`,
  при фокусе `-ring` (красная), текст `-input-foreground`, подсказка `-placeholder`.
- **Радио / тумблер**: кольцо Ø22, обводка 1.5 px `-border`, заливка `-input`. Выбранное — красная точка Ø12.
- **Сегментная полоса RAM** (`SegmentedBar`): 9 сегментов с `.zone-radius-sm`, активные `-primary`,
  неактивные `-segment-off`, значение мелким кеглем в последнем активном сегменте.
- **Заголовок раздела**: Bebas Neue UPPERCASE, под ним линия 1 px с градиентом от `-heading-rule` в прозрачность.
- **Полоса заголовка окна**: высота 28, `-titlebar`, снизу рамка `-border`. Справа «свернуть», «развернуть»
  и «закрыть»; крестик красный.
- **Окно входа**: фон `-background`, рамка 1 px `-border`, углы 10 px (как `.zone-radius-md`), полоса 32 px с
  логотипом; крестик краснеет при наведении (docs/design/WINDOWS.md).
- **Оверлей** (диалоги главного окна): `-scrim` и размытие контента, контент под ним отключён.
- **Профиль**: ник CAPS + ▾ в плитке `.zone-radius-sm`, меню — карточка `-card` `.zone-radius-md`, пункты
  с hover `-card-description`, «Выйти» цветом `-destructive`.
- **Логотип**: `LogoMark`, 4 `SVGPath` (even-odd), заливка `.logo-path { -fx-fill: -foreground; }`.
