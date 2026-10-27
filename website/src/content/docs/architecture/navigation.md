---
title: Навигация
description: Методы Navigator, оверлеи и меню профиля на main.
---

`StageNavigator` грузит обе рамки и начинает с `showLauncherUpdate(afterUpdate = showAuth)`, затем показывает primary stage по центру экрана.

## Поток до главного окна

1. Панель обновления попадает в `panelHost`. У контроллера вызывается `UpdatePanel.start(done)`.
2. `done` вызывает `showAuth()`. Панель входа заменяет обновление в том же окне.
3. Код из почты — `showMailAuth()`. Код из приложения — `showTotpAuth()`. Назад и Esc возвращают `showAuth()`.
4. **Создать** открывает `showAgeGate()`. Возраст не меньше 18 ведёт в `showRegister`. Назад возвращает на гейт с той же датой. Успех — `showRegistered(логин)`: снова вход с заполненным именем.
5. Успешный вход — `showMain()`. У панели вызывается `dispose()`. Сначала показывается главное окно на том экране, где был вход, потом вход прячется.

Крестик любого окна и Alt+F4 вызывают `close()` без вопроса про аккаунт: dispose, обе stage прячутся, `Platform.exit()`.

## Методы Navigator

```kotlin
interface Navigator {
    fun showLauncherUpdate(afterUpdate: () -> Unit = ::showAuth)
    fun showAuth()
    fun showMailAuth()
    fun showMain()
    fun switchLanguage(lang: String)
    fun minimize()
    fun toggleMaximize()
    fun close()
    val window: Window?
}
```

`switchLanguage` пересобирает главный экран и оставляет открытой страницу настроек. `toggleMaximize` действует только на главное окно. `minimize` сворачивает активное окно.

Новые методы (`showTotpAuth`, `showAgeGate`, `showRegister`, `showRegistered`) имеют реализации по умолчанию. Тестовому навигатору их писать не нужно.

## Диалоги

`showOverlay` / `hideOverlay` кладут узел в `overlayLayer`. Модальные слои `main.fxml` (установка, удаление, выход) блокируются так же:

- контент под диалогом отключён (`InputBlock.set`), мышь и клавиатура до кнопок под ним не доходят;
- класс `.input-blocked` убирает modena-прозрачность 0.4, вид не меняется;
- контент кэшируется (`CacheHint.SPEED`);
- фокус уходит в первый фокусируемый узел диалога.

Ресайз за края (6 px) не забирает клики у «свернуть», «развернуть» и «закрыть»: их границы вырезаны из зоны ресайза.

## Меню профиля

`profile.fxml` на `main` содержит: карточку пополнения, **Пригласить друга**, **Личный кабинет**, **Аккаунты**, **Выйти**.

**Аккаунты** открывает страницу настроек, а не окно входа. **Выйти** открывает подтверждение `logout_pane`. Enter подтверждает, Esc отменяет, фокус на «Отмена». После подтверждения `MainController.dispose()` вызывается до показа входа, затем вход центрируется на экране главного окна.

:::note[«Сменить аккаунт» в старых абзацах]
`README.md` и `docs/design/WINDOWS.md` ещё описывают пункт «Сменить аккаунт», который выходит сразу. В `profile.fxml` такой кнопки нет. `INTERACTION.md` ближе к коду: назад во вход ведёт подтверждённый «Выйти».
:::

Внешние ссылки меню идут через `LinkOpener` и allowlist `LinkPolicy`: только `https` без userinfo, хост из списка (`discord.com`, `discord.gg`, `youtube.com`, `reddit.com`, `t.me`, `telegram.org`, `stalzone.com`, `stalzone.net`, `exbo.net`, `exbo.games`, `exbo.ru`, `vk.com`, `vk.ru`) или их поддомен. Папку игры открывает отдельный `FolderOpener`, не `LinkOpener`.

## Что дальше

- [Маршрут](../../design/interaction/) — те же ветки таблицей;
- [Окна](../windows/) — размеры и FXML.
