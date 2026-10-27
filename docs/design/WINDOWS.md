# WINDOWS: два окна, как в оригинальном лаунчере

Запуск → окно обновления → **отдельное окно входа** (и окно кода из письма) → при успешном входе оно закрывается
и открывается главное окно 1024×640. «Сменить аккаунт» и «Выйти из аккаунта» закрывают главное окно и снова открывают окно
входа. Оверлей внутри главного окна остался только для внутренних диалогов.

## Окна

| | Окно до входа (pre-main) | Главное окно |
|---|---|---|
| Stage | primary stage из `LauncherApp`, `StageStyle.TRANSPARENT` (без рамки ОС, скруглённые углы) | `StageNavigator.mainStage`, `StageStyle.UNDECORATED` |
| Размер | **ширина 460**, без ресайза. Вход **460×506**; код из письма **460×438** (с ошибкой **460×526**), Google Authenticator **460×444** (с ошибкой **460×532**), обновление лаунчера **460×316**; регистрация и возраст **460×700**. Высота по содержимому, в пределах 280…**700** | 1024×640, ресайз за края, минимум 900×580 |
| Рамка | `auth_window.fxml` (`AuthWindowController`) | `window.fxml` (`WindowController`) |
| Что показывает | панели update / auth / mail code (по одной) | `main.fxml` в `contentHost`, диалоги в `overlayLayer` |

```
auth_window.fxml (AuthWindowController)            ← ширина 460, высота по панели (вход 506, почта 438/526, TOTP 444/532, обновление 316, потолок 700), clip 10 px (= .zone-radius-md)
└─ StackPane#authWindowRoot .auth-window (фон -background)
   ├─ BorderPane
   │  ├─ top:    HBox#authTitleBar 32 px [authLogo (вектор EXBO; скрыт, если у панели свой .zone-logo) | authDragArea | authHideButton | authCloseButton]
   │  └─ center: PanelHost#panelHost           ← корень панели, размер как у Scene root: вход 460×474, почта 460×406/494, TOTP 460×412/500, обновление 460×284, полное окно 460×668
   └─ Region .auth-window-frame               ← рамка 1 px -border ПОВЕРХ панели (mouseTransparent)
```

- **Окно панели входа — 460×506.** Ширина прежняя (`Theme.AUTH_WINDOW_WIDTH` = 460). Высота — полоса заголовка
  32 px плюс содержимое `auth_panel.fxml`: у корня нет `prefHeight`, блок логотипа с фиксированными отступами,
  `fittedWindowHeight()` даёт **506**, `panelHost` на входе — **460×474**. Потолок того же окна —
  `Theme.AUTH_WINDOW_HEIGHT` = **700** (полоса 32 px и область панели 460×668), пол — `AUTH_WINDOW_MIN_HEIGHT` = 280.
  Константы: `AUTH_TITLEBAR_HEIGHT`, `AUTH_PANEL_WIDTH/HEIGHT`.
- **Код из письма и Google Authenticator тоже по содержимому.** У `mail_auth_panel.fxml` и
  `totp_auth_panel.fxml` нет `prefHeight`: верхняя карточка держит только свои отступы, форма снизу плотная.
  `fittedWindowHeight()` в покое даёт код из письма **460×438** (`panelHost` **460×406**) и TOTP **460×444**
  (`panelHost` **460×412**). Тост ошибки входит в раскладку и поднимает окно до **460×526** (почта, хост
  **460×494**) и **460×532** (TOTP, хост **460×500**), в пределах 280…700.
- **Окно обновления лаунчера тоже по содержимому.** У `update_panel.fxml` нет `minHeight`/`prefHeight`, у блока
  логотипа снят `VBox.vgrow`: карточка держит только отступы (логотип, заголовок, описание), снизу плотно фаза и
  тонкая полоса. Пустые строки ошибки не занимают место; текст ошибки растит окно. `fittedWindowHeight()` даёт
  **460×316** (`panelHost` **460×284**) и в ru, и в en.
- **Полное окно 460×700** остаётся у регистрации и возраста: у них `prefHeight="668"`, и
  `fittedWindowHeight()` возвращает `Theme.AUTH_WINDOW_HEIGHT`. Окно одно: смена панели пересчитывает высоту
  (`AuthWindowController.fittedWindowHeight()`), ширина и высота меняются вместе, пересчёт через `runLater` только
  когда панель запросила раскладку (`AuthWindowRefitTest`). Строка ошибки на входе тоже подгоняет окно, в пределах
  280…700. `PanelFitTest`: вход, код из письма, TOTP и обновление компактные; регистрация и возраст заполняют
  полное окно, ru и en.
- **Логотип на панели входа.** Вместо заголовка по центру векторный логотип EXBO (`#zoneLoginLogo`, 150 px) на
  `-card-description`, том же чёрном, что и форма. Место под логотип сжато: `.zone-login-top` задаёт отступы
  `34 28 26 28`, `VBox.vgrow` снят, блок держит эту высоту. Если в панели есть `.zone-logo`, маленький `authLogo`
  в полосе заголовка прячется, чтобы логотип не дублировался.
- **Steam и Epic на панели входа скрыты.** Строка «Или войти через» (`.zone-social-row`, Steam и Epic Games)
  стоит с `visible="false"` и `managed="false"`: на экране её нет, и в высоту окна она не входит. Узлы кнопок
  остаются у контроллера.
- Полоса заголовка: логотип, перетаскивание (без разворачивания), «свернуть», «закрыть». Крестик в покое цвета
  `-titlebar-foreground`, при наведении фон `-window-close` (красный) и белый глиф.
- Окно не скруглено средствами CSS: панель заполняет его от края до края, поэтому углы режет `clip`
  (`Theme.AUTH_WINDOW_RADIUS` = 10, как `.zone-radius-md`). На рабочем столе без композитора прозрачные углы
  станут чёрными, на тёмном фоне это незаметно.
- Размытия больше нет: за окном входа ничего не построено, главный экран создаётся только после входа.

## Поток

1. `LauncherApp` создаёт `StageNavigator(stage, …)`: тот грузит обе рамки и вызывает
   `showLauncherUpdate(afterUpdate = showAuth)`, затем `stage.show()` (по центру экрана).
2. Панель обновления кладётся в `panelHost`, у её контроллера вызывается `UpdatePanel.start(done)`.
3. По `done` вызывается `showAuth()`: панель входа заменяет панель обновления в том же окне.
4. Нужен код из письма → `showMailAuth()`, код приложения-аутентификатора → `showTotpAuth()` (6 ячеек,
   «Вернуться ко входу» / Esc → `showAuth()`): панель кода в том же окне.
   Регистрация: «Создать» на панели входа → `showAgeGate()` → «ПРОДОЛЖИТЬ» (возраст ≥ 18) → `showRegister(дата)`
   → «← Назад» / Esc → `showAgeGate(дата)` (дата сохраняется), успех → `showRegistered(логин)`: панель входа с
   заполненным логином и уведомлением «Аккаунт … создан».
5. Успешный вход → `showMain()`: у панели вызывается `dispose()`, строится `main.fxml`, **сначала** показывается
   главное окно (по центру того экрана, где было окно входа), **потом** окно входа прячется. Порядок важен: у
   приложения ни в какой момент не бывает ноль окон, иначе JavaFX завершился бы сам (implicit exit).
6. «Сменить аккаунт» (меню профиля) → `auth.logout()` + `clientStates.shutdown()` → `showAuth()`.
   «Выйти из аккаунта» → подтверждение «Выход из аккаунта» (`logout_pane`; единственный путь к нему:
   `ProfileController.logout` → `onLogout` → `logoutPaneController.show()` в `MainController`; Enter — выйти,
   Esc — отмена, фокус на «Отмена») → то же самое. `StageNavigator` вызывает `MainController.dispose()`
   **до** показа окна входа (снимает слушатели со сцены и стора), затем окно входа появляется по центру экрана,
   где было главное окно, а главное окно прячется.
7. Крестик любого окна (и Alt+F4) → `close()` **без подтверждения выхода из аккаунта**: dispose, обе Stage прячутся, `Platform.exit()`. Скрытых окон не
   остаётся.

## Какие файлы ищет навигатор

| Метод навигатора | Новый файл (если есть) | Запасной (legacy) |
|---|---|---|
| `showLauncherUpdate` | `ui/update_panel.fxml` | `ui/update.fxml` |
| `showAuth` | `ui/auth_panel.fxml` | `ui/initial_auth.fxml` |
| `showMailAuth` | `ui/mail_auth_panel.fxml` | `ui/mail_auth.fxml` |
| `showTotpAuth` | `ui/totp_auth_panel.fxml` | — |
| `showAgeGate(date?)` | `ui/age_gate_panel.fxml` | — |
| `showRegister(date)` | `ui/register_panel.fxml` | — |
| `showRegistered(login)` | `showAuth()` + `InitialAuthController.showRegistered(login)` | |

Навигатор проверяет, лежит ли новый файл в classpath (`Theme.UI_ROOT + имя`), иначе берёт legacy-файл. **Добавив
новые панели, ни `StageNavigator`, ни `Theme.kt` менять не нужно.** У legacy-панелей (бывшие окна 379×490)
навигатор прячет собственные `#close`, `#hide`, `#header`, а сами панели центрируются в `panelHost`.

### Контракт панели (как подключается панель Front.end.master)

- Тот же FXML грузится как есть и кладётся в `panelHost` (`AuthWindowController.setPanel`). `panelHost` — это
  `PanelHost` (ui/components): он задаёт размер корню панели так же, как Scene своему root. Панель, которую можно
  растянуть (max не ниже хоста; у регистрации и возраста — min 420×560, pref 460×668), на полном
  окне получает **ровно** 460×668, даже если содержимое просит минимум чуть больше: она не залезает под полосу
  заголовка. Панель входа без `prefHeight` оставляет окно **460×506**, и хост под неё — **460×474**. Код из письма
  и TOTP тоже без `prefHeight`: хост **460×406** и **460×412** в покое, с тостом ошибки **460×494** и **460×500**.
  Обновление без `prefHeight`: хост **460×284**.
  Панель фиксированного размера (legacy, max = pref) встаёт по центру. Хост обрезает содержимое по
  своим границам и не увеличивает окно.
  Проверено слиянием `redesign/auth-zone` (ef53456) поверх этой ветки во временном worktree: слияние без
  конфликтов, 186 тестов зелёные, панель заполняет окно.
- Скругление окна, рамку 1 px и полосу заголовка с логотипом, «свернуть», «закрыть» даёт окно. Свои кнопки окна
  у панели можно оставить скрытыми (`visible="false"`), fx:id `closeButton`, `hideButton`, `header` сохраняются
  для контроллера.
- Контроллер получает `UiContext` через конструктор `(UiContext)` (`ui/Fxml.kt`).
- **Панель обновления** реализует `dev.launcherfork.ui.UpdatePanel` (`fun start(done: () -> Unit)`). Если не
  реализует, навигатор сразу вызывает `afterUpdate`.
- **Панели входа** по результату вызывают `ctx.navigator.showMain()` (успех), `showMailAuth()` (код из письма),
  `showTotpAuth()` (код приложения) или показывают ошибку у себя. Готовая логика:
  `AuthScreenBase.handle(Result<AuthResult>)`. Панель TOTP (`TotpAuthController`) самостоятельна: ячейки
  `CodeInput` (число ячеек — параметр), стили в `zone-totp.css`; экран кода из письма собран по тому же
  компактному контракту. Новые методы `Navigator` (`showTotpAuth`, `showAgeGate`,
  `showRegister`, `showRegistered`) имеют реализации по умолчанию, тестовым навигаторам их писать не нужно.
- Таймеры и слушатели → `dev.launcherfork.ui.Disposable`: `dispose()` вызывается при замене панели, входе и закрытии.
- Фокус при показе уходит в первый фокусируемый узел панели (обычно поле логина).
- Стили: `layout.css` подключён к корню окна. Цвета берите только из токенов (`-card`, `-card-description`,
  `-input-border`, `-error` и др., STYLE-ZONE.md), радиусы — классами `.zone-radius-sm/.zone-radius-md`. Узлу с
  `fx:id`, совпадающим с legacy CSS-id (`enterButton`, `register`, `restore`, `close`, `hide`, `error`…), задайте
  явный `id` (`FxmlLoadingTest.newFxmlDoNotReuseLegacyCssIds`).

## Диалоги внутри главного окна

`StageNavigator.showOverlay(node, controller)` / `hideOverlay()` кладут узел в `overlayLayer` над главным
экраном. Модальные слои `main.fxml` (установка, удаление, выход и т. д.) показываются внутри `main.fxml`, но
блокируются так же. Пока виден диалог:

- контент под ним **отключён** (`InputBlock.set`: `isDisable = true`), поэтому туда не попадает ни мышь, ни
  клавиатура: Tab и Enter не дотягиваются до кнопок под диалогом, а `requestFocus()` на них ничего не даёт;
- класс `.input-blocked` отменяет modena-прозрачность 0.4 у отключённых контролов, внешний вид не меняется;
- контент кэшируется (`isCache = true`, `CacheHint.SPEED`), чтобы размытие не пересчитывалось каждый кадр;
- фокус переходит в первый фокусируемый узел диалога.

Ресайз главного окна за края (6 px, scene-фильтр) не перехватывает клики по кнопкам «свернуть», «развернуть»,
«закрыть»: их границы вырезаются из зоны ресайза (`ResizeEdge.at(..., exclusions)`).

## Навигатор

```kotlin
interface Navigator {
    fun showLauncherUpdate(afterUpdate: () -> Unit = ::showAuth) // панель в окне до входа
    fun showAuth()            // окно входа; главное окно (если было) закрывается, MainController.dispose()
    fun showMailAuth()
    fun showMain()            // окно входа закрывается, главное окно открывается с новым главным экраном
    fun switchLanguage(lang: String) // пересобрать главный экран, страница настроек остаётся открытой
    fun minimize()            // сворачивает активное окно
    fun toggleMaximize()      // только главное окно
    fun close()               // выход из приложения
    val window: Window?       // активное окно
}
```

`StageNavigator` дополнительно отдаёт `authStage`, `mainStage`, `activeWindow`, `panelController`,
`mainController`, `overlayController`, `showOverlay`/`hideOverlay`, `exit` (тесты подменяют `Platform.exit`).
Тесты: `AuthWindowTest` (поток окон, dispose до показа входа, центрирование, выход по крестику, заполнение
`panelHost`) и `OverlayNavigatorTest` (блокировка клавиатуры под диалогами, логотип).
