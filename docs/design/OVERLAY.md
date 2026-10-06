# OVERLAY: окно одно, авторизация и обновление — панели поверх главного экрана

## Как устроено

```
window.fxml (WindowController)                    ← Scene одного окна (UNDECORATED, ресайз, 1024×640, мин. 900×580)
└─ StackPane#windowRoot
   └─ BorderPane
      ├─ top:    HBox#titleBar  [dragArea | hideButton | maximizeButton | closeButton]
      └─ center: StackPane
                 ├─ StackPane#contentHost   ← сюда грузится main.fxml (MainController)
                 └─ StackPane#overlayLayer  ← сюда кладётся панель оверлея (по центру), фон -scrim
```

- Полоса заголовка не входит в центральный стек, поэтому при открытом оверлее работает как обычно: окно можно
  перетащить, свернуть, развернуть и закрыть.
- Пока показан оверлей, `contentHost` получает `Theme.overlayBackdropEffect()` (GaussianBlur 16 поверх
  ColorAdjust: затемнение −0.3, насыщенность −0.3) и `isMouseTransparent = true`. Ввод уходит только в панель.
- Перетаскивание окна — за свободную часть полосы заголовка (`dragArea`), двойной клик разворачивает окно.
  Ресайз — за края (зона 6 px, `WindowFrame.installResize`). Разворачивание сделано вручную по
  `Screen.visualBounds`, поэтому работает и без оконного менеджера. Логика в `ui/components/WindowChrome.kt`
  (`WindowFrame`, `ResizeEdge`).

## Поток запуска

1. `LauncherApp` создаёт `StageNavigator`, тот загружает `window.fxml` и вызывает
   `showLauncherUpdate(afterUpdate = showAuth)`.
2. `showLauncherUpdate` сначала строит главный экран в `contentHost`, затем показывает панель обновления в
   `overlayLayer` и вызывает у её контроллера `UpdatePanel.start(done)`.
3. По `done` вызывается `showAuth()`: панель авторизации заменяет панель обновления, главный экран остаётся под ней.
4. Если нужен код из письма, вызывается `showMailAuth()`: панель кода заменяет панель авторизации.
5. При успешном входе вызывается `navigator.showMain()`. Оверлей скрывается, у контроллера панели вызывается
   `dispose()`, главный экран пересобирается, чтобы подхватить новую сессию (ник в карточке аккаунта).
6. «Сменить аккаунт» → подтверждение → `auth.logout()` + `clientStates.shutdown()` → `showAuth()`: снова оверлей
   поверх главного экрана.

## Какие файлы ищет навигатор (для автора панелей)

| Метод навигатора | Новый файл (если есть) | Запасной (legacy) |
|---|---|---|
| `showLauncherUpdate` | `ui/update_panel.fxml` | `ui/update.fxml` |
| `showAuth` | `ui/auth_panel.fxml` | `ui/initial_auth.fxml` |
| `showMailAuth` | `ui/mail_auth_panel.fxml` | `ui/mail_auth.fxml` |

`StageNavigator.showOverlay()` проверяет, лежит ли новый файл в classpath (`Theme.UI_ROOT + имя`). Если лежит,
грузится он, иначе legacy-файл. Поэтому, **добавив новые панели, ни `StageNavigator`, ни `Theme.kt` менять не
нужно**. У legacy-панелей (это были отдельные окна 379×490) навигатор прячет собственные `#close`, `#hide` и
`#header`.

### Контракт панели

- Корень панели: любой `Parent` фиксированного размера (рекомендуется ~500×540). Он будет отцентрован в
  `overlayLayer`. Растягиваться не нужно: задайте `maxWidth/maxHeight="-Infinity"` (USE_PREF) или явные размеры.
- Контроллер получает `UiContext` через конструктор `(UiContext)`, как и все остальные (`ui/Fxml.kt`).
- **Панель обновления** реализует `dev.launcherfork.ui.UpdatePanel` (`fun start(done: () -> Unit)`): запустить
  (фейковое) обновление и вызвать `done` на FX-потоке. Если контроллер `UpdatePanel` не реализует, навигатор
  сразу вызывает `afterUpdate`.
- **Панели авторизации** по результату вызывают `ctx.navigator.showMain()` (успех), `showMailAuth()` (нужен код)
  или показывают ошибку у себя. Готовая логика есть в `AuthScreenBase.handle(Result<AuthResult>)`.
- Если контроллер держит таймеры или слушатели, он реализует `dev.launcherfork.ui.Disposable`. `dispose()`
  вызывается, когда панель заменяют или скрывают.
- Стили: `layout.css` подключён к корню окна и действует внутри оверлея. Цвета берите только из токенов, радиусы —
  классами `.zone-radius-sm/.zone-radius-md` (STYLE-ZONE.md). Если у узла есть `fx:id`, совпадающий с legacy
  CSS-id (`enterButton`, `register`, `restore`, `enterSteam`, `folder`, `close`, `hide`, `error`, `input`…), задайте
  ему явный `id`, иначе к нему применятся старые PNG-правила из `lang_*.css`/`auth.css`. Это проверяет тест
  `FxmlLoadingTest.newFxmlDoNotReuseLegacyCssIds` (legacy-файлы из проверки исключены).

## Навигатор

```kotlin
interface Navigator {
    fun showLauncherUpdate(afterUpdate: () -> Unit = ::showAuth)
    fun showAuth()
    fun showMailAuth()
    fun showMain()            // скрыть оверлей + пересобрать главный экран
    fun switchLanguage(lang: String) // пересобрать главный экран, страница настроек остаётся открытой
    fun minimize()
    fun toggleMaximize()
    fun close()
    val window: Window?
}
```

`StageNavigator` дополнительно отдаёт `mainController`, `overlayController`, `isOverlayShown` и `hideOverlay()`
(ими пользуются автотур скриншотов и тесты, см. `OverlayNavigatorTest`).
