---
title: AppContext
description: Единственное место, где выбираются mock и live.
---

`AppContext` собирает `Services` и хранит состояние на сессию (`ClientStateStore`). UI получает `UiContext` через фабрику контроллеров в `Fxml.kt` и про конкретные классы данных не знает.

Домен не знает про UI. Ошибки и фазы — enum (`AuthError`, `UpdatePhase`). Строки из них делает `ui/Messages.kt`.

## Как LauncherApp выбирает

```kotlin
val useExbo = !tour && demo == null && ExboApi().installed()
```

`tour` — это витрина (`-Dlauncher.showcase`) или автотур скриншотов. `demo` — свойство `launcher.demo`: если оно задано, остаётесь на mock.

`ExboApi.installed()` истинен, когда в корне клиента EXBO лежат все три файла:

- `bootstrap.jar`;
- `launcher/launcher.jar`;
- `java/bin/java.exe`.

Корень — `ExboPaths.roamingRoot()`. В stderr при live пишется `backend: exbo server list`, при mock — строка про режим 2FA.

:::note
README в разделе «Что удалено» перечисляет сеть, токены и запуск игры как вырезанные. Это описание mock-слоя и того, что не переносилось из оригинала. На Windows при установленном клиенте `LauncherApp` собирает live, и этот список live не отменяет.
:::

## mock

`AppContext.mock` ставит `MockAuthService`, `MockServerRepository`, `MockUpdateService`, `MockGameLauncher`, `InMemorySettingsStore`, `MockSystemInfo`.

Стол `desk` по умолчанию — `memoryDesk()`: временный каталог и `MemoryRegistry`. Это не HKCU и не файлы установленного EXBO.

`liveBackend` остаётся `false`. Тогда «запускать по окончании загрузки» не стартует игру само, а режим выхода зафиксирован как `ExitMode.NEVER`.

## live

`AppContext.live` ставит `ExboAuthService`, `ExboServerRepository`, `LiveUpdateService`, `InstalledGameLauncher`, `ExboSettingsStore`, `HostSystemInfo`.

Стол — `StalzoneDesk` с `WindowsRegistry`, файлом сессий, каталогом JVM-конфигов и `options.json`. `liveBackend = true`: автостарт после загрузки и режим выхода читаются из настроек.

Повторная установка клиента в этом пути отключена: каталог качается в каталог, который уже сопоставил официальный лаунчер.

Пересборка сцены (смена языка) загрузку не останавливает: `ClientStateStore` живёт в `AppContext`, не в контроллере. У операции есть номер поколения. Поздний колбэк старой операции игнорируется.

## Что дальше

- [Навигация](../navigation/) — что UI вызывает после сборки контекста;
- [Аккаунты](../../settings/accounts/) — куда mock и live пишут сессии;
- [Флаги запуска](../../reference/gradle-flags/) — свойства, которые форсят mock.
