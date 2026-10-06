# EXBO Launcher — визуальный форк (только UI)

Форк **только визуального слоя** лаунчера EXBO (STALZONE / STALCRAFT). Внутренностей и серверной части нет:
все данные берутся из локальных заглушек (`data/mock`). Цель: перенести основной визуал оригинала как можно
ближе к исходнику, чтобы потом спокойно его менять и делать редизайн.

Стек: **Kotlin 2.0 + OpenJFX 21** (`org.openjfx.javafxplugin`), **JDK 21**, Gradle wrapper 8.10.2.
Исходник оригинала: JavaFX 9.0.3, прозрачное окно без рамки, главное окно 886×550, окна авторизации и
обновления 379×490. Разбор оригинала лежит в `/workspace/stalzone/REPORT.md`.

**Раскладка (ветка `redesign/zone`).** Два окна, как в оригинале (`docs/design/WINDOWS.md`). Обновление, вход
и код из письма показываются в отдельном окне без рамки 460×620. После входа оно закрывается и открывается
главное окно: без рамки, с ресайзом, 1024×640 (минимум 900×580). Слева колонка фиксированной ширины с векторным
логотипом EXBO, профиль (ник ▾ и меню) в правом верхнем углу, контент тянется вместе с окном. Эта адаптивная раскладка
**заменила старую 886×550 для всех тем**. Темы `exbo` и `shadcn` работают на ней через общие токены, пусть и
скромнее. Тема по умолчанию осталась `exbo`. Стиль `zone` (сайт STALZONE + раскладка лаунчера STALCRAFT)
включается так: `-Dlauncher.theme=zone`, описание в `docs/design/STYLE-ZONE.md`.

## Запуск

```bash
./gradlew run                       # обычный запуск (ru)
./gradlew run -Dlauncher.lang=en    # английский интерфейс
./gradlew build                     # компиляция + тесты
./gradlew test                      # только тесты (headless, X-сервер не нужен)
```

Дополнительные свойства:

| Свойство | Что делает |
|---|---|
| `-Dlauncher.lang=ru\|en` | язык интерфейса (по умолчанию `ru`) |
| `-Dlauncher.mockSpeed=4` | ускоряет фейковые прогрессы во столько раз |
| `-Dlauncher.theme=exbo\|shadcn\|zone` | тема: исходные цвета EXBO (по умолчанию), тёмная neutral-тема shadcn/ui (`theme-shadcn.css`) или STALZONE (`theme-zone.css`, Bebas Neue + Roboto) |
| `-Dlauncher.screenshots=docs/screenshots` | автотур по экранам с сохранением PNG (dev-инструмент) |
| `-Dlauncher.onlineTickMs=10000` | как часто фейковый онлайн в списке игр немного меняется (по умолчанию 10 с, `0` — всегда 1284; в автотуре всегда 0) |

На машине без дисплея: `xvfb-run -a ./gradlew run -Dlauncher.theme=zone -Dlauncher.screenshots=$PWD/docs/screenshots/zone`.

**Демо-логин** (всё фейковое): подходит любой логин и пароль. Логин `2fa` открывает экран кода из e-mail
(как раньше: любой 6-значный код; экран кода из письма ведёт ветка redesign/panels-zone), логин `totp` — экран кода
из приложения-аутентификатора
(6 ячеек, подходит любой 6-значный код, кроме `000000` — он показывает тост «ОШИБКА ПРИ ПОДТВЕРЖДЕНИИ»). Пароль
`wrong` показывает ошибку «неверный логин или пароль».

**Выход из аккаунта**: подтверждение открывает только пункт меню профиля «Выйти из аккаунта» (Enter — выйти,
Esc — отмена, фокус на «Отмена»); «Сменить аккаунт» выходит сразу, крестик окна и Alt+F4 закрывают лаунчер без
вопросов.

**Регистрация** (ссылка «Создать» на панели входа): возраст (день / месяц / год, нужно ≥ 18 лет) → форма
(почта, имя 3–16 символов `A-Z a-z 0-9 _`, пароль ≥ 8, обязательное согласие с лицензией) → панель входа с
заполненным именем. Имя `taken` → «Имя занято», почта `taken@…` → «Эта почта уже зарегистрирована».

Сценарий: окно фейкового самообновления → в том же окне вход → окно входа закрывается, открывается главное окно
(Настройки / Скачать → прогресс → Играть → «Игра запущена»). Меню профиля в правом верхнем углу: «Профиль ↗»
(заглушка), «Сменить аккаунт» (сразу назад в окно входа), «Выйти» (подтверждение, затем окно входа). Главное
окно перетаскивается за полосу заголовка, двойной клик разворачивает его, края окна тянутся. Окно входа только
перетаскивается и сворачивается, крестик любого окна завершает приложение.

Dev-хоткеи главного окна для модальных слоёв, которые форк сам не показывает. Они работают **только с
`-Dlauncher.dev=true`** (`./gradlew run -Dlauncher.dev=true`): **F1** обновление лаунчера,
**F2** лицензия, **F3** офлайн, **F4** Защитник Windows, **F5** «уже идёт загрузка», **F6** ошибка,
**F7** спиннер загрузки. **Esc** (закрыть все модальные слои) работает всегда.

## Структура

```
src/main/kotlin/dev/launcherfork/
├─ app/                 точка входа и сборка приложения
│  ├─ Main.kt             main() -> Application.launch
│  ├─ LauncherApp.kt      primary stage = окно до входа (TRANSPARENT, 460×620, без ресайза), иконка
│  ├─ AppContext.kt       ЕДИНСТВЕННОЕ место, где выбираются реализации сервисов (ручной DI)
│  ├─ StageNavigator.kt   два окна: auth_window.fxml (update/auth/mail) и window.fxml + main.fxml (WINDOWS.md)
│  └─ ScreenshotTour.kt   автотур для скриншотов (dev)
├─ domain/              модели и интерфейсы, от которых зависит UI
│  ├─ Models.kt           Server, UserSession, AuthResult/AuthError, UpdatePhase, Progress,
│  │                      LauncherSettings (+ enum P2pMode, ExitMode; gameRoot: java.nio.file.Path)
│  └─ Services.kt         AuthService, ServerRepository, UpdateService, GameLauncher,
│                         SettingsStore, Services
├─ data/mock/           фейковые реализации (без сети, файлов и реестра)
│  ├─ MockData.kt         одна игра: STALZONE
│  ├─ MockAuthService.kt, MockRepositories.kt (серверы, InMemorySettingsStore)
│  ├─ MockUpdateService.kt  таймерный прогресс самообновления и «скачивания клиента»
│  ├─ MockGameLauncher.kt   фейковый запуск игры: «запущена» через 1.2 с, «завершилась» через 5 с
│  └─ MockScheduler.kt      общий таймер фейков (безопасная отмена/завершение)
├─ i18n/Strings.kt      ResourceBundle из локальных .properties (ru + en) вместо серверного LangManager
├─ theme/Theme.kt       то, что нельзя выразить в CSS: пути, геометрия окна, эффекты (оверлей, арт игры), шрифты
└─ ui/
   ├─ UiContext.kt, Navigator.kt, Fxml.kt   контекст контроллеров, интерфейс навигации (+ UpdatePanel), загрузка FXML
   ├─ Messages.kt       AuthError / UpdatePhase -> ключи строк (домен ключей не знает)
   ├─ Disposable.kt     контроллер экрана освобождает таймеры/слушатели при уходе с экрана
   ├─ state/            ClientStateStore: состояния клиента Download/Pause/Continue/Play/Exit (на сессию)
   ├─ components/       WindowChrome (WindowFrame: drag/maximize/resize/центрирование, ResizeEdge), RamBar (шкала RAM), ActionGlyphs (иконки меню действий с игрой), CodeInput (ячейки кода TOTP),
   │                    LogoMark (вектор EXBO из exbo-logo.svg), InputBlock (блок мыши/клавиатуры под диалогом),
   │                    Effects, ServerEntryController (пункт игры в колонке)
   └─ screens/
      ├─ window/        WindowController (window.fxml: полоса заголовка, contentHost, overlayLayer),
      │                 AuthWindowController (auth_window.fxml: полоса с логотипом, panelHost)
      ├─ update/        LauncherUpdateController     (update.fxml)
      ├─ auth/          InitialAuthController, MailAuthController (initial_auth.fxml, mail_auth.fxml)
      ├─ main/          MainController (main.fxml) + по контроллеру на каждую fx:include-область:
      │                 ServersList, Profile, Header, StatusPane, GameActions,
      │                 modals/ (10 модальных слоёв)
      └─ settings/      SettingsController (статичный settings.fxml, привязан к SettingsStore)

src/main/resources/dev/launcherfork/
├─ ui/*.fxml, ui/main/*.fxml, ui/main/modals/*.fxml   разметка (спецификация)
├─ ui/style/theme.css, theme-shadcn.css, theme-zone.css   токены цветов + шрифты + радиусы (литералы только тут)
├─ ui/style/layout.css   стили новой раскладки (окно, главный экран, настройки, модалки; только токены)
├─ ui/style/{common,auth,lang_ru,lang_en}.css   стили legacy-панелей авторизации/обновления
├─ ui/style/image/, ui/style/font/   PNG и Europe из оригинала; Manrope, Roboto (OFL), Bebas Neue (шрифт stalzone.com) (см. «Права на ассеты»)
├─ ui/i18n/messages*.properties      строки ru/en
└─ mock/                             сгенерированные картинки-заглушки (tools/GenerateZoneArt.java)
```

## Архитектура и правила для дальнейшей работы

1. **Данные только через интерфейсы.** `AuthService`, `ServerRepository`, `UpdateService`,
   `GameLauncher`, `SettingsStore` лежат в `domain/`. Фейки лежат в `data/mock/`. Связываются они один раз, в `app/AppContext.kt`.
   UI получает `UiContext` (интерфейсы + навигатор + строки) через controller factory в `ui/Fxml.kt` и про моки
   ничего не знает. Чтобы подключить другой источник данных, достаточно собрать `Services` с другими реализациями.
   Домен не знает про UI: ошибки и фазы передаются enum-ами (`AuthError`, `UpdatePhase`), а `ui/Messages.kt`
   превращает их в строки. Картинки описаны как `java.net.URI` (у моков это URI ресурсов из classpath).
   **Состояние на сессию** (`ClientStateStore`: идущие загрузки, запущенная игра) принадлежит `AppContext`,
   а не контроллеру. Поэтому пересборка сцены (например, смена языка в настройках) загрузку не останавливает.
   Каждая операция в сторе получает номер поколения, и поздние колбэки старой операции (например, `onFinished`
   после паузы и повторного старта) игнорируются. Исключения из блокирующих вызовов (`background()` в
   `ui/components/Fx.kt`) приходят в UI как `Result.failure`: форма разблокируется и показывает ошибку сервера.
2. **Один контроллер на один FXML.** Гигантский `main.fxml` оригинала (132 узла, `MainController` на 2000 строк
   и ~100 полей) разбит на `fx:include`: `main/status_pane.fxml`, `main/game_actions.fxml`, `servers_list.fxml`,
   `header.fxml`, `profile.fxml` и `main/modals/*.fxml`. У каждого файла свой
   контроллер. `MainController` только координирует области (хром окна теперь в `window.fxml`).
   Корни include по-прежнему носят исходные `fx:id` (`defaultStatusPane`, `logoutPane`…).
   В редизайне перевёрстаны колонка, шапка-ссылки, строка статуса и настройки. Hero, колонка новостей и
   окно статьи из центра главного окна убраны: на их месте пустой `StackPane` `centerPane` (между шапкой и
   строкой статуса) под новый дизайн центра. Узлам, у которых `fx:id` совпадает со старым CSS-id, задан явный `id` (иначе к ним применились бы
   PNG-правила из `lang_*.css`).
3. **В коде нет `setStyle`/`setFont`/цветов.** Весь визуал задаётся CSS-классами. Состояния переключаются
   классами (`.selected`, `.active`, `.state-play`, `.state-pause`…). В коде остались только вещи, которых нет
   в JavaFX CSS: `ColorAdjust`/`GaussianBlur` (константы в `Theme`: размытие под оверлеем, обесцвечивание арта)
   и картинки из данных (иконка и фон игры).
4. **`theme.css`: токены в стиле shadcn/ui** со значениями из оригинала:
   `-background #242428`, `-foreground #ffffff`, `-card #191919`, `-card-foreground #c7c7c7`,
   `-primary #1c4a61`, `-primary-foreground`, `-secondary #2b2b2b`, `-muted`, `-muted-foreground #b1b1b1`,
   `-accent rgba(255,255,255,.15)`, `-border #2b2b2b`, `-ring #1c4a61`, `-destructive #ae171c`,
   `-popover`, `-input-foreground`, `-placeholder`, `-overlay` и дополнительные оттенки текста оригинала
   (`-text-strong`, `-text-label`, `-text-dim`…). Остальные CSS-файлы ссылаются только на токены.
   Токены объявлены на `.root`, поэтому `theme.css` подключается к Scene (`Theme.apply`).
   **Ограничение JavaFX:** looked-up значения работают только для цветов. Радиусы и размеры переменными
   сделать нельзя, поэтому радиус (в оригинале везде 0) задан один раз группой селекторов в `theme.css`.
5. **Шрифты объявлены один раз в `theme.css`:** основной `Manrope Light` на `.root` (наследуется всеми узлами),
   акцидентный `Europe` через класс `.font-display`, жирный через `.font-bold` (`Manrope` bold). Manrope
   (SIL OFL 1.1, есть кириллица, лицензия в `ui/style/font/OFL-Manrope.txt`) заменил коммерческий Circe из оригинала.
   Чтобы сменить шрифт ещё раз, достаточно положить файлы в `ui/style/font/`,
   поправить список в `Theme.loadFonts()` и имена семейств в `theme.css`.
   В теме `zone` шрифты другие, как на stalzone.com: заголовки и большие кнопки Bebas Neue Regular (шрифт сайта,
   источник в docs/design/STYLE-ZONE.md, `OFL-BebasNeue.txt`), текст Roboto (SIL OFL 1.1, `OFL-Roboto.txt`). Их регистрирует `Theme.zoneFonts`, а семейства
   задаёт `theme-zone.css`. Разрядки (letter-spacing) в JavaFX CSS нет. CAPS-узлы с классом `.zone-caps` после
   слияния с `redesign/auth` перейдут на `SpacedLabel`.
   Маска пароля: `.password-field { -fx-skin }` в `theme.css` ставит всем `PasswordField` (все темы)
   `BulletPasswordFieldSkin`: вместо огромного U+25CF (его нет в наших шрифтах, рисовался запасной системный)
   по одному U+2022 на символ шрифтом `ui/style/font/LauncherMask.ttf` (одна круглая точка 0.36 em с шагом 0.625 em,
   метрики строки как у Roboto; собирается `python3 tools/generate_mask_font.py`, нужен fontTools).
6. **Тесты** (`./gradlew test`, JUnit 5 + headless Monocle):
   - `FxmlLoadingTest` загружает **каждый** FXML с настоящим контроллером на ru и en. Он падает на ошибках
     FXMLLoader (класс, свойство, include, обработчик) и на **любом неинжектированном `@FXML`-поле**
     (переименованный или удалённый `fx:id`), включая контроллеры из `fx:include`. Затем применяет CSS и layout.
   - Проверка, что ~65 ключевых `fx:id` новой раскладки достижимы через `main.fxml` и его include, у
     `window.fxml` и `auth_window.fxml` есть рамка, аккаунт живёт только в меню профиля, новые FXML не используют
     legacy CSS-id.
   - Совпадение ключей ru/en, нет слов со смесью латиницы и кириллицы, фейковые сервисы, края ресайза (кнопки окна
     вырезаны из зоны ресайза) и минимальный размер окна, токены всех тем.
   - `AuthWindowTest`: update → вход (→ код) в отдельном окне, вход закрывает его и открывает главное окно;
     «Сменить аккаунт» и «Выйти» вызывают `MainController.dispose()` до показа окна входа; окно входа центрируется
     на экране главного окна; крестик любого окна завершает приложение без скрытых окон; панель без
     фиксированного размера заполняет `panelHost` 460×588.
   - `OverlayNavigatorTest`: под диалогом (overlay и модальные слои) ничто не получает фокус, контент отключён и
     закэширован, фокус в диалоге; логотип — 4 `SVGPath` с even-odd, равномерный масштаб, заливка из токена.
   - `SettingsScreenTest`: настройки помещаются без прокрутки при 900×580 и при размере по умолчанию во всех темах
     и языках (`localToScene`), заливка шкалы RAM = N/M и совпадает с подписью, клавиатура и мышь меняют значение
     с шагом 1 ГБ в границах и сохраняют его. `GameActionsMenuTest`: меню плавает и не сдвигает ИГРАТЬ, пункты по
     состоянию клиента, подтверждение (отмена = ничего не происходит), папка через `FolderOpener`.
   - `ClientStateStore` (скачивание, пауза, продолжение, готово, запуск, «выйти», поздние колбэки после паузы
     и рестарта, shutdown) на ручных фейках, без таймеров.
   - `background()`: исключение доходит до UI как ошибка; окна авторизации разблокируются и показывают
     `auth.error.server_error`, если `login()`/`confirmCode()` бросили исключение. Поле кода принимает не больше 6 цифр.
   - Главное окно: загрузка переживает пересборку сцены; F1–F7 выключены без `-Dlauncher.dev=true`.
   - `MainZoneLayoutTest` (тема zone): нет арта игры, ссылки Форум / Поддержка в ряду профиля, STALZONE —
     единственная игра (иконка — фавикон сайта), центр `centerPane` пустой и стоит между шапкой и строкой статуса,
     hero / новостей / статьи в окне нет.
   - `OnlineCountTest`: строка онлайна под названием игры («1 284 онлайн» / «1,284 online», серая точка и
     «не в сети» / «offline» при неизвестном онлайне), обновление по свойству и из `ServerRepository.watchOnline`,
     раскладка во всех темах при минимальном размере окна (иконка по центру двух строк, ничего не обрезано).

## Что перенесено

- Окна (в редизайне): окно до входа 460×620 (без ресайза) и главное окно 1024×640 (`UNDECORATED`, ресайз за края,
  разворачивание и перетаскивание за полосу заголовка). Размеры оригинала (379×490 / 886×550) и многоугольники
  перетаскивания убраны.
- Экраны: самообновление (`update.fxml`), авторизация (`initial_auth.fxml`), код из e-mail (`mail_auth.fxml`),
  главное окно (список серверов, шапка со ссылками, статус и кнопка
  Скачать/Пауза/Продолжить/Играть/Выйти, меню клиента; центр пустой под редизайн), настройки (категории, линии и отступы как в оригинале),
  10 модальных слоёв.
- Эффекты: hover `ColorAdjust(brightness +0.2)` (+0.1 у карточек серверов), фон сервера
  `GaussianBlur(3)` поверх `ColorAdjust(-0.2)`, обесцвечивание полосы загрузки `(p-1)*1.3` и правый колпачок 82 px,
  hover-оверлей соцсетей, кнопки с текстом в PNG через `lang_ru.css` и `lang_en.css`.
- Строки: исходные ru/en тексты перенесены в `messages_*.properties`, плюс несколько ключей `fork.*`.

## Что удалено (намеренно)

Сеть и backend (`launcher-global.stalcraft.net`), авторизация, токены и сессии, bootstrap и самообновление с
проверкой SHA-1, загрузчик и торренты (jlibtorrent), запуск игры и процессы, ExboOptimizer, телеметрия
(Sentry, MetricsManager), нативные dll (jmt, registry, acc_check), реестр и автозапуск Windows, HWID, кэш
картинок с сервера, single-instance (JUnique), серверный `LangManager`, трей (AWT) и `JOptionPane`.
Внешние ссылки (сайт, форум, соцсети) никуда не ведут. Действия, которых в форке нет, показывают слой ошибки
«Недоступно в форке».

## Что заглушка

- `MockData`: одна игра, STALZONE (установлена, иконка — фавикон stalzone.com). Тёмный процедурный арт
  (силуэт, лес, вышка; фон игры в темах exbo и shadcn) сгенерирован скриптом
  `java tools/GenerateZoneArt.java src/main/resources/dev/launcherfork` и к EXBO не относятся: из эталонных
  скриншотов ничего не скопировано. Логотип EXBO — вектор из `ui/style/image/exbo-logo.svg` (логотип владельца
  форка), рисуется `SVGPath`-ами (`LogoMark`), цвет из токена `-foreground`.
- Авторизация, коды, «скачивание», «запуск игры», самообновление: таймеры в `MockAuthService`,
  `MockUpdateService` и `MockGameLauncher`.
- Папка игр по умолчанию `~/Games/EXBO` (в оригинале был путь Windows). Путь установки строится через `Path.resolve`.
- Настройки хранятся только в памяти (`InMemorySettingsStore`). В редизайне со страницы настроек убраны выбор
  корневой папки и кнопка «Открыть папку логов» (на эталоне их нет, а в форке они были заглушками). Поле
  `gameRoot` осталось в модели, путь установки виден в диалоге установки. Порядок пунктов взят с эталона:
  «Запускать игру по окончании загрузки» перенесён в раздел ОБНОВЛЕНИЕ.
- Трей не реализован. Вместо диалогов ошибок используется встроенный слой `exceptionPane`.

## Что перенеслось неидеально

- **Шрифты в коде → CSS.** Оригинал задавал кегли через `Font.loadFont` и `setFont` (41 вызов). Здесь
  кегли и цвета перенесены в CSS по значениям из кода. Сглаживание LCD/gray местами может отличаться:
  `prism.lcdtext=false`, как в оригинале.
- **`-fx-effect: dropshadow(...), innershadow(...)`** на корне `main.fxml` в JavaFX 21 не парсится (два эффекта
  через запятую). Оставлен только `dropshadow`. Внутри прозрачного окна 886×550 тень всё равно обрезается.
- **`NumberTextField`** (свой класс оригинала в `mail_auth.fxml`) заменён на `TextField` + `TextFormatter`.
- **RAM**: заполняемая шкала `RamBar` вместо слайдера оригинала. Шкала = вся память компьютера, заливка =
  выделенное («Выделено: 6 ГБ из 16 ГБ»), шаг ровно 1 ГБ, границы от 2 ГБ до «память − 2 ГБ» (резерв системы
  заштрихован), рекомендуемое отмечено (`domain/RamOptions`). Мышь (клик / перетаскивание) и клавиши
  ←/→/↑/↓, PgUp/PgDn, Home/End.
- **Страница настроек** собрана заново и помещается на один экран при минимальном окне 900×580 во всех темах и
  обоих языках: заголовок НАСТРОЙКИ в строке профиля, карточка ИГРА на всю ширину, ОБНОВЛЕНИЕ | ПРОЧЕЕ в две колонки.
- **Действия с игрой**: квадратная кнопка «⋯» слева от ИГРАТЬ открывает плавающее меню (Проверить файлы, Открыть
  папку игры, Настройки игры, Переустановить, Удалить игру). Переустановка и удаление (и проверка запущенной игры)
  сначала спрашивают подтверждение, пока операция идёт, ИГРАТЬ неактивна. Операции — моки `MockUpdateService`.
  Папка открывается через отдельный сервис `FolderOpener` (не через `LinkOpener`) и только папка игры из настроек
  (`domain/GameFolders`).
- Переключатель страниц «главная/новости» (`pagesPane`), соцсети и описание сервера в отдельной панели убраны.
  Hero и новости редизайна тоже убраны (вместе с `NewsRepository`/`NewsItem` и моками новостей): центр
  главного окна будет спроектирован заново.
- Экраны `google_auth`, `mail_confirmation_*`, `password_changing_auth`, `distributor_pending`,
  `bootstrap_outdated`, `uninstaller` и инсталлятор не переносились.

## Скриншоты

Снимки Scene активного окна, сделанные автотуром под Xvfb (`ScreenshotTour`), одинаковые для всех тем:
`01_update_window`, `02_auth_window`, `03_auth_error` (окно до входа, 460×620), `04_main`, `05_profile_menu`,
`06_settings` (настройки на один экран), `07_settings_min_size` (900×580), `08_game_actions` (меню действий с
игрой), `08b_game_actions_delete_confirm`, `09_running`, `10_verifying` (проверка файлов, ИГРАТЬ неактивна),
`11_resized_1366x800`, `12_settings_en`, `13_auth_after_switch_account`, затем снова окно входа (ru):
`14_age_gate`, `15_age_gate_too_young`, `16_register`, `17_register_error`, `18_registered_login`,
`19_totp_error`. После `05_profile_menu` есть `05b_logout_confirm` (подтверждение «Выход из аккаунта»). После него `05c_community_menu` (выпадающее меню «Сообщества» со ссылками на соцсети). Единственная игра STALZONE установлена,
поэтому диалога установки и прогресса скачивания в туре нет.

- `docs/screenshots/zone/` — тема `zone` (основная цель редизайна);
- `docs/screenshots/` — тема по умолчанию `exbo` на новой раскладке;
- `docs/screenshots/shadcn/` — тема shadcn/ui.

## Дистрибутивы: Windows-инсталлятор, portable, Linux

Всё собирается на Linux одним скриптом (`packaging/build-dist.sh`) в `dist/` (каталог в `.gitignore`):

| Файл | Что это |
|---|---|
| `LauncherFork_Setup_<версия>.exe` | NSIS-инсталлятор (x64, без прав администратора): ставит в `%LOCALAPPDATA%\LauncherFork`, ярлыки в меню «Пуск» и на рабочем столе, деинсталлятор и запись в «Приложения и возможности» |
| `LauncherFork_portable_win64.zip` | тот же образ: распаковать и запустить `LauncherFork.exe` |
| `LauncherFork_linux_x64.tar.gz` | jlink-образ для Linux x64, запуск `bin/launcherfork` |
| `exbo-launcher-fork-src.zip` | `git archive` текущего HEAD |
| `SHA256SUMS.txt` | контрольные суммы |

Внутри Windows-образа: `runtime/` (jlink из jmods Temurin JDK 21 windows-x64 и OpenJFX 21 windows-x64, модули
`java.base, java.desktop, javafx.controls, javafx.fxml` и их зависимости), `app/` (jar приложения и Kotlin stdlib),
`LauncherFork.exe` (маленький GUI-запускатель на NSIS, запускает `runtime\bin\javaw.exe`, консоли нет) и запасной
`LauncherFork.cmd`. Аргументы запускателя передаются JVM: `LauncherFork.exe -Dlauncher.lang=en -Dlauncher.theme=shadcn`.

```bash
sudo apt install nsis zip
# скачать и распаковать: Temurin JDK 21 windows-x64 (zip), openjfx-21.x windows-x64 и linux-x64 jmods (gluonhq)
WIN_JDK=.../jdk-21.0.x WIN_JFX=.../javafx-jmods-win LINUX_JFX=.../javafx-jmods-linux packaging/build-dist.sh
```

Правила безопасности инсталлятора: итоговая папка всегда заканчивается на `\LauncherFork` (если выбрать
`D:\Projects`, установка пойдёт в `D:\Projects\LauncherFork`, в том числе при тихой установке `/S /D=...`).
Папки `runtime\` и `app\` удаляются целиком только при обновлении, то есть когда есть маркер
`.launcherfork-install`, который пишет наш инсталлятор. Чужую папку с `runtime\`/`app\` без маркера инсталлятор
отклоняет (код выхода 2). Деинсталлятор удаляет ровно те файлы, которые поставил: их список генерируется при сборке.
Папки, включая саму папку установки, он удаляет только если они пустые. Без маркера деинсталлятор ничего не делает.

Feature-версия JDK (21) у jmods для Windows должна совпадать с `jlink` хоста. Иконка `.ico` берётся готовой из
`docs/design/icon/icon.ico` (размеры 16..256). Иконка окна и панели задач: `ui/style/image/icon-16..256.png` (все размеры в
`Stage.icons`, `Theme.APP_ICON_SIZES`); исходники (SVG, ICO, генератор) и пометка о правах в `docs/design/icon/`.

**Только для внутреннего использования.** Инсталлятор, portable-архив и Linux-образ содержат PNG EXBO и
коммерческий шрифт `Europe` (см. ниже). Распространять их наружу нельзя, пока эти ассеты не заменены.

## Права на ассеты — важно

PNG-изображения в `src/main/resources/dev/launcherfork/ui/style/image/` (логотипы EXBO и STALCRAFT, кнопки,
рамки, иконки) и шрифт `ui/style/font/europe.ttf` скопированы из оригинального лаунчера и **принадлежат EXBO**.
Акцидентный шрифт `Europe` **не свободный**. Circe из оригинала уже заменён на свободный Manrope (OFL), но
файлы Circe остались в истории git (коммит `14149a8`). Строки интерфейса взяты из оригинала.
Это допустимо только для внутренней работы над редизайном. **Перед любым публичным распространением
(релиз, публичный репозиторий, сборки) все эти ассеты и шрифты нужно заменить** на собственные или свободно
лицензированные. Глифы соцсетей в меню «Сообщества» (`ui/components/BrandGlyphs.kt`) — Simple Icons, CC0 1.0
(источник в docs/design/STYLE-ZONE.md). Картинки в `mock/` сгенерированы скриптом и свободны, кроме `mock/servers/game-stalzone.png` — это фавикон
stalzone.com (ассет проекта, источник в docs/design/STYLE-ZONE.md).

### Ассеты EXBO уже есть в истории git

Ассеты и шрифты EXBO закоммичены в первом коммите (`14149a8`) и остаются во всех последующих. `.gitignore` и
удаление файлов **не убирают их из истории**: любой, кто склонирует репозиторий, сможет их достать. Поэтому
перед публикацией (GitHub и т.п.) нужно одно из двух:

1. **Чистый первый коммит:** заменить ассеты на свои, затем создать новый репозиторий без истории
   (например, `git checkout --orphan public && git add -A && git commit`) и публиковать только его.
2. **Переписать историю:** `git filter-repo --invert-paths --path src/main/resources/dev/launcherfork/ui/style/image/ ...`
   (по списку ниже) и force-push. Все существующие клоны и ветки (включая `theme`) придётся пересоздать.

Сейчас история **не переписана**. Готовый блок для `.gitignore` на случай, если ассеты будут лежать рядом
локально, но не должны попасть в публичный репозиторий (уже отслеживаемые файлы сначала снять с учёта:
`git rm -r --cached <путь>`):

```gitignore
# --- EXBO-owned assets: never publish ---
src/main/resources/dev/launcherfork/ui/style/image/
src/main/resources/dev/launcherfork/ui/style/font/europe.ttf
# screenshots show EXBO logos/buttons
docs/screenshots/
```

Строки из `messages_*.properties` тоже взяты из оригинала. Их стоит переписать до публикации, но в `.gitignore`
они не попадают, потому что без них приложение не соберётся.
