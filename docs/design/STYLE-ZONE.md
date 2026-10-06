# STYLE-ZONE: тема `zone` (стиль сайта STALZONE)

Включается так: `-Dlauncher.theme=zone`. Файл с токенами: `src/main/resources/dev/launcherfork/ui/style/theme-zone.css`.
Он подключается к Scene сразу после `theme.css` (`Theme.apply`) и переопределяет значения токенов, семейства
шрифтов и группы радиусов. Разметка и CSS компонентов ссылаются только на токены, литеральных цветов в них нет.

Источники:
- **Визуальный стиль** взят с сайта [stalzone.com/start](https://stalzone.com/start): CSS-переменные сайта и
  мобильные скриншоты `start.jpg` и `menu.jpg`. Отсюда палитра, рамки, радиусы, CAPS-подписи и плитки.
- **Концепция и раскладка** взяты с эталонных скриншотов лаунчера STALCRAFT: колонка слева, разделы настроек,
  авторизация в оверлее. Hero («ДОБРО ПОЖАЛОВАТЬ» / «В ЧЕРНОБЫЛЬ» / описание) и колонка новостей из центра
  главного окна убраны: центр — пустой слот `centerPane` (`main.fxml`) под новый дизайн.

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
| `-card` | `#181818` | `--card` (stalzone.com/login) | карточка входа, группы настроек, модалки, выпадающее меню профиля |
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
| Заголовки разделов, заголовки модалок | `Bebas Neue Regular` (`.font-display`), UPPERCASE | 22–40 | группы настроек 18, заголовки модалок 30 |
| Большие кнопки | `Bebas Neue Regular` | 24 | «ИГРАТЬ» (`.play-button`), «ВОЙТИ» (`.zone-primary`) |
| Заголовок окна входа | `Bebas Neue Regular` (`.zone-title`) | 35 | «ЗАХОДИ СКОРЕЕ, МЫ ЗАЖДАЛИСЬ» |
| CAPS-подписи и пункты меню | `Roboto Medium` (`.zone-caps`), UPPERCASE | 11–15 | пункты колонки, ссылки ↗, «— РАЗДЕЛ —», кнопки-плитки |
| Подпись настройки, вариант | `Roboto` | 13 | «Оперативная память», «При старте игры» |
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

Поэтому в лаунчере: заголовки и большие кнопки — `Bebas Neue Regular` (так JavaFX называет семейство,
name ID 1), текст — Roboto, CAPS-пункты — Roboto Medium. Bebas ниже по высоте прописных (0.70 em против 0.81 em у
Oswald), поэтому кегли заголовков в `theme-zone.css` увеличены примерно в 1.15 раза. Шрифты лежат в
`ui/style/font/`, регистрирует их `Theme.zoneFonts`; Oswald удалён.

**Источники шрифтов.**

- `BebasNeue-Regular.ttf` (семейство `Bebas Neue Regular`) — шрифт сайта, ассет проекта:
  https://stalzone.com/front_stalzone/build/assets/BebasNeueRegular-D00apsXq.woff2 (на сайте `@font-face`
  `Bebas Neue`, weight 400; это `--beb`). Скачан 2026-10-06, WOFF2 распакован в TTF fontTools (`flavor=None`), глифы и
  имена не менялись. Bebas Neue Regular 001.003 (Ryoichi Tsunekawa / Dharma Type); в таблице name указана
  SIL OFL 1.1, её текст — `OFL-BebasNeue.txt`.
- Bebas Neue Cyrillic (https://stalzone.com/front_stalzone/build/assets/BebasNeueCyrillic-B2yc5-l0.woff2, `--bebcyrill`)
  в лаунчер не включён: на референсе (заголовки h1–h5, «ВОЙТИ», «ИГРАТЬ БЕСПЛАТНО», пункты меню) он не участвует,
  сайт берёт его только для промо-страниц, а его рисунок заметно жирнее тонкого Bebas Neue с референса.
- `Roboto-Light/Regular/Medium.ttf` — Roboto 3.015 (roboto-classic, https://github.com/googlefonts/roboto-classic),
  SIL OFL 1.1, `OFL-Roboto.txt`. Это тот же Roboto, что и вариативный шрифт сайта
  (https://stalzone.com/front_stalzone/build/assets/Roboto-VariableFont_wdth_wght-746kZxMU.woff2): при wght 500 /
  wdth 100 ширины глифов совпадают с `Roboto-Medium.ttf`, поэтому статические файлы оставлены.

`ZoneFontsTest` проверяет, что каждое семейство из `theme-zone.css` / `zone-auth.css` грузится из TTF в
`ui/style/font/`, внутреннее имя семейства совпадает с CSS (иначе JavaFX молча подставил бы системный шрифт) и
кириллица U+0410–U+044F, Ё, ё есть.

**Ассеты проекта с stalzone.com.** Это ассеты проекта (STALZONE / EXBO), взятые с сайта:

- Иконка игры STALZONE в колонке — `src/main/resources/dev/launcherfork/mock/servers/game-stalzone.png`: фавикон
  https://stalzone.com/stalzone/favicon/favicon-master.svg (на https://stalzone.com/start это
  `<link rel="icon" type="image/svg+xml">`), скачан 2026-10-06, отрисован ImageMagick в PNG 48×48 (2× от 24 px
  иконки), рисунок не менялся.

**Иконки соцсетей (меню «Сообщества»).** Монохромные бренд-глифы Discord, YouTube, Reddit, Telegram —
**Simple Icons 16.34.0** (https://simpleicons.org, https://github.com/simple-icons/simple-icons), лицензия
**CC0 1.0** (public domain). Path-данные скачаны curl 2026-10-06 с
`https://cdn.jsdelivr.net/npm/simple-icons@16.34.0/icons/{discord,youtube,reddit,telegram}.svg` (viewBox 24×24) и
лежат строками в `ui/components/BrandGlyphs.kt`. Пути не менялись, кроме Telegram: оставлен только подконтур
бумажного самолётика (полная иконка — самолётик, вырезанный из круга; в футере stalzone.com, который взят за
референс, самолётик без круга). Сами знаки — товарные знаки их владельцев; CC0 касается только векторных путей.

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
  знак (`-fill-subtle`). Пример на сайте — «БАЛАНС: 0»; в лаунчере — группы настроек, модалки.
- **Поле ввода** (`.text-field` / `.zone-input` + `.zone-radius-sm`): заливка `-input`, рамка 1 px `-border`,
  при фокусе `-ring` (красная), текст `-input-foreground`, подсказка `-placeholder`.
- **Радио / тумблер**: кольцо Ø16, обводка 1.5 px `-border`, заливка `-input`. Выбранное — красная точка Ø8.
- **Строка навигации** (`topBar` в `main.fxml`): СООБЩЕСТВА / ФОРУМ / ПОДДЕРЖКА и кнопка профиля, одна и та же на
  всех экранах. На её уровне ничего больше нет: ни заголовков страниц, ни контента. Содержимое страниц начинается ниже
  (`centerPane` с 70 px, настройки с 64 px).
- **Настройки** (`settings.fxml`): один экран без прокрутки при 900×580. Заголовка страницы и разделительных линий
  нет. Группы разделены отступом 14 px. У каждой группы подпись Bebas (21 px в zone, 17 px в остальных темах,
  `.settings-group-label`, почти белый `-settings-heading`) и тёмная карточка `.card` (`zone-radius-md`) под ней:
  - ОПЕРАТИВНАЯ ПАМЯТЬ: «Выделено: N ГБ из M ГБ», под этой подписью шкала, под шкалой подписи min / max (`RamScale`).
  - ПОВЕДЕНИЕ: список в одну колонку из 7 строк по 32 px (`.settings-row`; было 6 по 36, седьмая — «Тема», и всё
    по-прежнему на одном экране при 900×580). В каждой строке подпись 14 px
    `-settings-label` слева, контрол справа, между строками тонкая линия `-settings-divider`. Закрывать лаунчер и
    Peer-to-peer и «Тема» (Тёмная / Белая) — компактные сегменты `.segmented` (`-input`, рамка `-border`, текст 13 px `-settings-muted`). Выбранная
    часть залита `-segment-selected` (в zone чуть глубже красного `-primary`: белый текст 4.9:1), текст на ней
    `-primary-foreground`, полная формулировка в тултипе. Четыре переключателя 30×16 стоят в одной
    колонке у правого края; включённый — дорожка `-switch-on`,
    выключенный — светлый кружок `-switch-off-knob` в кольце `-switch-off-border`.
  - Внизу в один ряд ОБНОВЛЕНИЕ (строка «Ограничение скорости» и два поля 28 px с ↓/↑) и ЯЗЫК (сегменты
    Русский | English), строки в том же стиле.

  Подписи не переносятся, длинный текст уходит в тултипы. Весь текст страницы — WCAG AA (≥ 4.5:1, заголовки ≥ 3:1)
  во всех темах; токены `-settings-*`, `-ram-*`, `-switch-off-*`, `-segment-selected` проверяет `SettingsContrastTest`.
- **Шкала RAM** (`RamBar`, логика `domain/RamOptions`) — как в оригинальном лаунчере: полоса 16 px из равных
  сегментов с зазором 2 px (сегмент = 1 ГБ памяти компьютера), без бегунка и делений. Три состояния: выбранные
  `-ram-filled`, доступные серые `-ram-free` (≥ 3:1 к карточке), резерв системы (выше «память − 2 ГБ») — только контур
  `-ram-reserve`. Над полосой «Выделено: 6 ГБ из 16 ГБ» 14 px `-settings-label`, под ней «2 ГБ» и «14 ГБ» 12 px
  `-settings-muted` точно под крайними доступными сегментами (`RamScale`; если min = max, одна подпись); рекомендуемое значение — в тултипе. Фокус с клавиатуры — рамка `-border-hover`.
  Хранится `LauncherSettings.ramMb`; старое `ramFraction` (0..1 → 512 + 3584·f МБ) читается, округляется до целых ГБ и
  ограничивается теми же границами. Токен `-segment-off` не используется.
- **Строка статуса** (`main/status_pane.fxml`): плашка `.status-pill` той же высоты (44) и с тем же центром, что «⋯» и
  ИГРАТЬ. Заливка `-input`, рамка `-border`. Текст статуса внутри слева, процент справа (при загрузке и на паузе).
  Прогресс заполняет плашку: заливка `-primary-tint` (новый токен во всех темах), по низу кромка 2 px `-primary`. На
  паузе заливка `-tile-hover`, кромка `-muted-foreground`.
- **«НАСТРОЙКИ» в колонке слева** (`.sidebar-settings`) стоит на линии строки статуса: высота 44 px, тот же центр и
  тот же нижний отступ 20 px (нижний padding `.sidebar` = `bottomAnchor` строки статуса), шестерёнка и подпись по
  центру. Проверяет `SidebarSettingsAlignTest` (все темы, оба языка, 900×580 / 1024×640 / 1366×800).
- **Действия с игрой** (`main/game_actions.fxml`): кнопка-призрак 44×44 слева от ИГРАТЬ (прозрачная, рамка 1 px `-border`)
  со значком «•••» (`SVGPath`: три точки Ø2.5 через 3 px по центру, `-muted-foreground`, при наведении / открытом меню `-foreground`), меню — карточка `.profile-menu` (плавающая, `managed=false`) над кнопкой, правые края совпадают.
  Подпись игры CAPS 11 px `-placeholder`, строки с линейными иконками 16 px (Lucide 0.544.0, ISC, обводка
  `-icon`), «Переустановить» / «Удалить игру» под линией цветом `-destructive-text`, наведение `-destructive-soft`.
  Неактивные строки — прозрачность 0.38.
- **Шаги окна входа** (`zone-steps.css`, поверх `zone-auth.css`): возраст (`age_gate_panel.fxml`, три
  `ComboBox` `.zone-combo` 54 px с белым треугольником, список `-card-description`), регистрация
  (`register_panel.fxml`, строки чекбоксов с подчёркнутыми ссылками `.zone-inline-link`),
  тост ошибки сайта (`.zone-toast`: карточка, заголовок Bebas 30, текст, полоса с ✕ `-border`, снизу 2 px
  `-primary`), светлая кнопка «ОТПРАВИТЬ» (`.zone-light`: `-foreground` / текст `-background`), уведомление об
  успешной регистрации на панели входа (`.zone-notice`: `-card`, полоска 2 px `-foreground` слева).
- **Код приложения-аутентификатора** (`zone-totp.css`, отдельный файл, подключён только в `totp_auth_panel.fxml`):
  `CodeInput` — 6 ячеек `.code-cell` 54×58 через 12 px, `:filled` / `:focused` / `:error`; тост ошибки сайта.
- **Подтверждение (модалка главного окна)** (`.modal-box` в `layout.css`, `main/modals/logout_pane.fxml`):
  карточка по содержимому, отступы 22/28/28, заголовок Bebas, линия, текст, ряд кнопок справа внизу:
  `.modal-action` Bebas 20, высота 44, основная `.btn-primary` (красная), вторая `.btn-outline` (прозрачная,
  рамка `-border-hover`). Без абсолютных координат — кнопки не вылезают при любом шрифте и языке.
- **Заголовок раздела**: Bebas Neue UPPERCASE, под ним линия 1 px с градиентом от `-heading-rule` в прозрачность.
- **Полоса заголовка окна**: высота 28, `-titlebar`, снизу рамка `-border`. Справа «свернуть», «развернуть»
  и «закрыть»; крестик красный.
- **Окно входа**: фон `-background`, рамка 1 px `-border`, углы 10 px (как `.zone-radius-md`), полоса 32 px с
  логотипом; крестик краснеет при наведении (docs/design/WINDOWS.md).
- **Оверлей** (диалоги главного окна): `-scrim` и размытие контента, контент под ним отключён.
- **Профиль**: ник CAPS + ▾ в плитке `.zone-radius-sm`, меню — карточка `-card` `.zone-radius-md`, пункты
  с hover `-card-description`, «Выйти» цветом `-destructive`.
- **Сообщества** (`main/header.fxml`, `HeaderController`): в ряду шапки первым пунктом «СООБЩЕСТВА ▾» — `Button`,
  оформленный как соседние ссылки (`.header-link`, без фона и отступов), вместо ↗ треугольник `.header-caret`,
  который переворачивается при открытом меню (`.open`); фокус с клавиатуры — подчёркивание (`:focus-visible`).
  Меню — та же карточка, что у профиля (`.profile-menu`, ширина 164), строки `.profile-menu-item` с тем же hover:
  плитка 28×28 `.community-icon-tile` (`-tile`, рамка 1 px `-border`, `.zone-radius-sm`, при hover/фокусе
  `-border-hover`), в ней глиф 14 px `-foreground` по центру (как плитки в футере stalzone.com), затем название и ↗.
  Карточка плавающая (unmanaged): левый край по левому краю «СООБЩЕСТВА», верх на уровне меню профиля, не выходит
  за окно. Ссылки — только в `domain/CommunityLinks`, открываются через `LinkOpener` (в приложении
  `HostServices.showDocument`). Открыто не больше одного меню; закрывается выбором, Esc и кликом мимо.
- **Логотип**: `LogoMark`, 4 `SVGPath` (even-odd), заливка `.logo-path { -fx-fill: -foreground; }`.

## Белая тема (`theme-zone-light.css`)

Светлый вариант того же стиля STALZONE. Включается в настройках («ПОВЕДЕНИЕ → Тема: Тёмная / Белая») или запуском
с `-Dlauncher.theme=zone-light`. Выбор применяется сразу к обоим окнам (главное и окно входа), так же как смена
языка: `StageNavigator.switchAppearance` → `Theme.apply` на обе Scene, экраны не пересобираются. Выбор хранится в
`~/.launcher-fork/settings.properties` (`AppearanceFileStore`, `-Dlauncher.settingsFile`) и переживает перезапуск.
При старте `-Dlauncher.theme=zone-light` важнее сохранённого выбора. Белая схема есть только у `zone`: в темах
`exbo` / `shadcn` строка «Тема» неактивна (тултип), и они всегда тёмные.

Стек стилей: `theme.css` → `theme-zone.css` → `theme-zone-light.css` → `lang_*.css`. Файл переопределяет **только
токены** (все, `ThemeTokensTest`), поэтому шрифты, радиусы и раскладка те же, а панели входа, регистрации и кодов
(`zone-auth.css`, `zone-steps.css`, `zone-totp.css`) перекрашиваются сами, их правила не менялись. Единственное
правило не про токены — `.entry-icon` (белые глифы игр в колонке слева перекрашиваются в `-foreground` через
`innershadow`, иначе на белом их не видно).

| Токен | Белая | Где |
|---|---|---|
| `-background`, `-card-description`, `-input`, `-titlebar` | `#ffffff` | окно, форма входа, поля и сегменты |
| `-card`, `-tile`, `-muted` | `#f4f4f5` | карточки настроек, меню, модалки |
| `-sidebar`, `-card-alt` | `#fafafa` | колонка слева |
| `-foreground`, `-settings-heading` | `#0a0a0a` | текст, заголовки, логотип (19.8:1 на белом) |
| `-muted-foreground`, `-settings-muted` | `#52525b` | подписи, неактивные сегменты (7.7:1 на белом, 7.0:1 на карточке) |
| `-border`, `-divider`, `-settings-divider` | `#e4e4e7` | рамки и разделители |
| `-primary`, `-segment-selected`, `-ram-filled`, `-switch-on` | `#d62037` | красный акцент: `#dc2239`, чуть темнее, чтобы красный текст был ≥ 4.5:1 и на карточке `#f4f4f5` (5.1:1 на белом, 4.6:1 на карточке; белый текст на нём 5.1:1) |
| `-ram-free`, `-switch-off-border` | `#85858d` | доступные сегменты RAM — серые и тише красного (3.3:1 против 4.6:1) |
| `-ram-reserve` | `#d4d4d8` | резерв, только контур |
| `-switch-off-knob` | `#71717a` | кружок выключенного переключателя (4.8:1) |
| `-accent`, `-fill-*`, `-divider-light`, `-scrim`, `-overlay`, `-shadow` | чёрный с альфой | hover и затемнения (в тёмной теме это белый с альфой, на белом он бы пропал) |

Включённый переключатель: красная дорожка и белый кружок (5.1:1), белого кружка на светлой дорожке нет.
`SettingsContrastTest` проверяет белую схему вместе с остальными: тексты настроек, главного окна и окна входа
≥ 4.5:1, нетекстовые элементы ≥ 3:1, выбранные сегменты RAM контрастнее доступных; `AppearanceTest` — сохранение
между запусками и живое переключение обоих окон. Скриншоты: `docs/screenshots/zone-light/`.
