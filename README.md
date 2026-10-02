# EXBO Launcher — визуальный форк (только UI)

Форк **только визуального слоя** лаунчера EXBO (STALZONE / STALCRAFT). Внутренностей и серверной части нет:
все данные берутся из локальных заглушек (`data/mock`). Цель: перенести основной визуал оригинала как можно
ближе к исходнику, чтобы потом спокойно его менять и делать редизайн.

Стек: **Kotlin 2.0 + OpenJFX 21** (`org.openjfx.javafxplugin`), **JDK 21**, Gradle wrapper 8.10.2.
Исходник оригинала: JavaFX 9.0.3, прозрачное окно без рамки, главное окно 886×550, окна авторизации и
обновления 379×490. Разбор оригинала лежит в `/workspace/stalzone/REPORT.md`.

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
| `-Dlauncher.theme=exbo\|shadcn` | тема: исходные цвета EXBO (по умолчанию) или тёмная neutral-тема shadcn/ui (`theme-shadcn.css`) |
| `-Dlauncher.screenshots=docs/screenshots` | автотур по экранам с сохранением PNG (dev-инструмент) |

На машине без дисплея: `xvfb-run -a ./gradlew run -Dlauncher.screenshots=$PWD/docs/screenshots`.

**Демо-логин** (всё фейковое): подходит любой логин и пароль. Логин `2fa` открывает экран ввода кода из e-mail
(подходит любой 6-значный код). Пароль `wrong` показывает ошибку «неверный логин или пароль».

Сценарий: фейковое самообновление (`update.fxml`) → авторизация → главное окно → (Настройки / Скачать → прогресс →
Играть → «Игра запущена»). Кнопка с ником справа сверху открывает «Смену пользователя» и возвращает на авторизацию.

Dev-хоткеи главного окна для модальных слоёв, которые форк сам не показывает. Они работают **только с
`-Dlauncher.dev=true`** (`./gradlew run -Dlauncher.dev=true`): **F1** обновление лаунчера,
**F2** лицензия, **F3** офлайн, **F4** Защитник Windows, **F5** «уже идёт загрузка», **F6** ошибка,
**F7** спиннер загрузки. **Esc** (закрыть все модальные слои) работает всегда.

## Структура

```
src/main/kotlin/dev/launcherfork/
├─ app/                 точка входа и сборка приложения
│  ├─ Main.kt             main() -> Application.launch
│  ├─ LauncherApp.kt      Stage: TRANSPARENT, без рамки, без ресайза, иконка
│  ├─ AppContext.kt       ЕДИНСТВЕННОЕ место, где выбираются реализации сервисов (ручной DI)
│  ├─ StageNavigator.kt   переключение экранов (смена Scene + размер окна 379×490 / 886×550)
│  └─ ScreenshotTour.kt   автотур для скриншотов (dev)
├─ domain/              модели и интерфейсы, от которых зависит UI
│  ├─ Models.kt           Server, NewsItem, UserSession, AuthResult/AuthError, UpdatePhase, Progress,
│  │                      LauncherSettings (+ enum P2pMode, ExitMode; gameRoot: java.nio.file.Path)
│  └─ Services.kt         AuthService, ServerRepository, NewsRepository, UpdateService, GameLauncher,
│                         SettingsStore, Services
├─ data/mock/           фейковые реализации (без сети, файлов и реестра)
│  ├─ MockData.kt         4 сервера и 4 новости-заглушки
│  ├─ MockAuthService.kt, MockRepositories.kt (серверы, новости, InMemorySettingsStore)
│  ├─ MockUpdateService.kt  таймерный прогресс самообновления и «скачивания клиента»
│  ├─ MockGameLauncher.kt   фейковый запуск игры: «запущена» через 1.2 с, «завершилась» через 5 с
│  └─ MockScheduler.kt      общий таймер фейков (безопасная отмена/завершение)
├─ i18n/Strings.kt      ResourceBundle из локальных .properties (ru + en) вместо серверного LangManager
├─ theme/Theme.kt       то, что нельзя выразить в CSS: пути, геометрия окон, полигон перетаскивания, эффекты
└─ ui/
   ├─ UiContext.kt, Navigator.kt, Fxml.kt   контекст контроллеров, интерфейс навигации, загрузка FXML
   ├─ Messages.kt       AuthError / UpdatePhase -> ключи строк (домен ключей не знает)
   ├─ Disposable.kt     контроллер экрана освобождает таймеры/слушатели при уходе с экрана
   ├─ state/            ClientStateStore: состояния клиента Download/Pause/Continue/Play/Exit (на сессию)
   ├─ components/       WindowChrome (drag/close/hide), Effects (hover-яркость), DownloadBar,
   │                    ServerEntryController (карточка сервера)
   └─ screens/
      ├─ update/        LauncherUpdateController     (update.fxml)
      ├─ auth/          InitialAuthController, MailAuthController (initial_auth.fxml, mail_auth.fxml)
      ├─ main/          MainController (main.fxml) + по контроллеру на каждую fx:include-область:
      │                 ServersList, StatusPane, News, Article, ClientSettings, Header,
      │                 modals/ (10 модальных слоёв)
      └─ settings/      SettingsController (settings.fxml) + entries/ (check box, numeric, progression, radio, path)

src/main/resources/dev/launcherfork/
├─ ui/*.fxml, ui/main/*.fxml, ui/main/modals/*.fxml, ui/settings/*.fxml   разметка (спецификация)
├─ ui/style/theme.css   токены цветов + шрифты (единственное место с литералами цветов)
├─ ui/style/{common,auth,main,settings,lang_ru,lang_en}.css               стили компонентов (только токены)
├─ ui/style/image/, ui/style/font/   PNG и Europe из оригинала, Manrope (OFL) (см. «Права на ассеты»)
├─ ui/i18n/messages*.properties      строки ru/en
└─ mock/                             сгенерированные картинки-заглушки (tools/GeneratePlaceholders.java)
```

## Архитектура и правила для дальнейшей работы

1. **Данные только через интерфейсы.** `AuthService`, `ServerRepository`, `NewsRepository`, `UpdateService`,
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
   и ~100 полей) разбит на `fx:include`: `main/status_pane.fxml`, `servers_list.fxml`, `news.fxml`,
   `article.fxml`, `client_settings.fxml`, `header.fxml` и `main/modals/*.fxml`. У каждого файла свой
   контроллер. `MainController` держит только хром окна и координирует области.
   **Исходные `fx:id` сохранены.** Корень каждого include носит тот `fx:id`, который был у узла в оригинале
   (`defaultStatusPane`, `advancedNewsPane`, `logoutPane`…). Порядок слоёв (z-order) тот же.
3. **В коде нет `setStyle`/`setFont`/цветов.** Весь визуал задаётся CSS-классами. Состояния переключаются
   классами (`.selected`, `.active`, `.state-play`, `.state-pause`…). В коде остались только вещи, которых нет
   в JavaFX CSS: `ColorAdjust`/`GaussianBlur` (константы в `Theme`), картинки из данных (иконка и фон
   сервера, новости) и ширинозависимый «колпачок» полосы загрузки (`DownloadBar`, порт `setProgress` оригинала).
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
6. **Тесты** (`./gradlew test`, JUnit 5 + headless Monocle):
   - `FxmlLoadingTest` загружает **каждый** FXML с настоящим контроллером на ru и en. Он падает на ошибках
     FXMLLoader (класс, свойство, include, обработчик) и на **любом неинжектированном `@FXML`-поле**
     (переименованный или удалённый `fx:id`), включая контроллеры из `fx:include`. Затем применяет CSS и layout.
   - Проверка, что ~55 `fx:id` из оригинального `main.fxml` достижимы через `main.fxml` и его include.
   - Совпадение ключей ru/en, фейковые сервисы, полигон перетаскивания.
   - `ClientStateStore` (скачивание, пауза, продолжение, готово, запуск, «выйти», поздние колбэки после паузы
     и рестарта, shutdown) на ручных фейках, без таймеров.
   - `background()`: исключение доходит до UI как ошибка; окна авторизации разблокируются и показывают
     `auth.error.server_error`, если `login()`/`confirmCode()` бросили исключение. Поле кода принимает не больше 6 цифр.
   - Главное окно: загрузка переживает пересборку сцены; F1–F7 выключены без `-Dlauncher.dev=true`.

## Что перенесено

- Окна: прозрачные `StageStyle.TRANSPARENT`, без рамки, без ресайза. Размеры 379×490 и 886×550. Кастомные
  «закрыть» и «свернуть». Перетаскивание за шапку по тем же многоугольникам, что в оригинале
  (`AuthController.headerArea`, `MainController.initializeStageMovement`).
- Экраны: самообновление (`update.fxml`), авторизация (`initial_auth.fxml`), код из e-mail (`mail_auth.fxml`),
  главное окно (новости, описание сервера, список серверов, шапка со ссылками, статус и кнопка
  Скачать/Пауза/Продолжить/Играть/Выйти, меню клиента), настройки (категории, линии и отступы как в оригинале),
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

- `MockData`: 4 сервера (Alpha/Bravo/Charlie/Delta) и 4 новости. Картинки сгенерированы скриптом
  `java tools/GeneratePlaceholders.java src/main/resources/dev/launcherfork/mock` и к EXBO не относятся.
- Авторизация, коды, «скачивание», «запуск игры», самообновление: таймеры в `MockAuthService`,
  `MockUpdateService` и `MockGameLauncher`.
- Папка игр по умолчанию `~/Games/EXBO` (в оригинале был путь Windows). Путь установки строится через `Path.resolve`.
- Настройки хранятся только в памяти (`InMemorySettingsStore`). Выбор папки и «Открыть логи» — заглушки.
- Трей не реализован. Вместо диалогов ошибок используется встроенный слой `exceptionPane`.

## Что перенеслось неидеально

- **Шрифты в коде → CSS.** Оригинал задавал кегли через `Font.loadFont` и `setFont` (41 вызов). Здесь
  кегли и цвета перенесены в CSS по значениям из кода. Сглаживание LCD/gray местами может отличаться:
  `prism.lcdtext=false`, как в оригинале.
- **`-fx-effect: dropshadow(...), innershadow(...)`** на корне `main.fxml` в JavaFX 21 не парсится (два эффекта
  через запятую). Оставлен только `dropshadow`. Внутри прозрачного окна 886×550 тень всё равно обрезается.
- **`NumberTextField`** (свой класс оригинала в `mail_auth.fxml`) заменён на `TextField` + `TextFormatter`.
- **RAM-слайдер** (`progression_entry.fxml`): оригинал рисовал трек через `BorderImage` в коде. Здесь это два
  региона с CSS-фоном, `fx:id="progress"` стал `Pane` с дочерним `bar`.
- **`firstNewsDescPane`** стал `VBox` (подкласс `Pane`) вместо `Pane`. Позиции подписей новостей примерные.
- Точную раскладку `pagesPane` (иконки «главная/новости») и меню клиента оригинал строил в коде. Здесь она
  восстановлена по координатам из декомпиляции, но вживую с оригиналом не сверялась.
- Экраны `google_auth`, `mail_confirmation_*`, `password_changing_auth`, `distributor_pending`,
  `bootstrap_outdated`, `uninstaller` и инсталлятор не переносились.

## Скриншоты

`docs/screenshots/` (снимки Scene, сделанные автотуром под Xvfb):
`01_update_progress`, `02_auth`, `03_auth_error`, `04_main_news`, `05_main_server`, `06_settings`,
`07_install_dialog`, `08_fake_progress`, `09_fake_running`, `10_settings_en`.
Те же экраны в теме shadcn/ui (`-Dlauncher.theme=shadcn`) лежат в `docs/screenshots/shadcn/`.

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

Feature-версия JDK (21) у jmods для Windows должна совпадать с `jlink` хоста. Иконка `.ico` собирается из
`icon.png` скриптом `packaging/MakeIco.java`.

**Только для внутреннего использования.** Инсталлятор, portable-архив и Linux-образ содержат PNG EXBO и
коммерческий шрифт `Europe` (см. ниже). Распространять их наружу нельзя, пока эти ассеты не заменены.

## Права на ассеты — важно

PNG-изображения в `src/main/resources/dev/launcherfork/ui/style/image/` (логотипы EXBO и STALCRAFT, кнопки,
рамки, иконки) и шрифт `ui/style/font/europe.ttf` скопированы из оригинального лаунчера и **принадлежат EXBO**.
Акцидентный шрифт `Europe` **не свободный**. Circe из оригинала уже заменён на свободный Manrope (OFL), но
файлы Circe остались в истории git (коммит `14149a8`). Строки интерфейса взяты из оригинала.
Это допустимо только для внутренней работы над редизайном. **Перед любым публичным распространением
(релиз, публичный репозиторий, сборки) все эти ассеты и шрифты нужно заменить** на собственные или свободно
лицензированные. Картинки в `mock/` сгенерированы скриптом и свободны.

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
