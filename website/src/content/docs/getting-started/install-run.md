---
title: Установка и запуск
description: Команды Gradle, окно установки 460×660 и состав дистрибутива.
---

Нужен JDK 21. Wrapper уже лежит в корне репозитория, отдельно ставить Gradle не нужно.

## 1. Запустите лаунчер

```bash title="корень репозитория"
./gradlew run
./gradlew run -Dlauncher.lang=en
./gradlew build
./gradlew test
```

`test` идёт headless, X-сервер не нужен. На машине без дисплея живой запуск:

```bash
xvfb-run -a ./gradlew run -Dlauncher.screenshots=$PWD/docs/screenshots/zone
```

Аргументы `-Dlauncher.*` перечислены в [флагах запуска](../../reference/gradle-flags/).

## 2. Снимите экраны

```bash
./gradlew run "-Dlauncher.screenshots=docs/screenshots/zone" "-Dlauncher.animations=off"
```

PNG пишет автотур `ScreenshotTour` в указанный каталог. Тема по умолчанию — `zone`. Живой круг с анимациями: `-Dlauncher.showcase=true` (`docs/SHOWCASE.md`). Esc выходит из витрины.

В результате:

- в каталоге появляются `01_update_window.png` … `19_totp_error.png`;
- обои тура фиксированы (`03`), снимки повторяются.

Если окно не открылось, последовательно проверьте:

- установлен ли JDK 21 и совпадает ли он с `JAVA_HOME`;
- не занят ли дисплей; без него используйте `xvfb-run`.

:::tip
Для спора про вёрстку оставляйте mock. Live подключается сам, только когда клиент EXBO уже стоит на машине.
:::

## Окно установки

`LauncherFork_Setup_<версия>.exe` ставит лаунчер для текущего пользователя и не запрашивает права администратора. В «Приложениях и возможностях» имя **EXBO Launcher**, издатель **EXBO**, игра **STALZONE**.

Окно, которое видит человек, — `SetupWindow` (`dev.launcherfork.install.SetupWindowKt`). Это тот же pre-main кадр, что у входа: `StageStyle.TRANSPARENT`, без ресайза, ширина 460, сцена **460×660**, clip 10 px. Высоту здесь не считает `fittedWindowHeight()`. По центру вектор EXBO и подпись «Установка лаунчера».

1. Поле папки и кнопка «Изменить папку». По умолчанию `%LOCALAPPDATA%\LauncherFork`. Итоговый путь всегда заканчивается на `\LauncherFork`: `D:\Projects` становится `D:\Projects\LauncherFork`. Тихий `/S /D=...` следует тому же правилу.
2. Две галочки включены: ярлык на рабочем столе и ярлык в автозагрузке. Ярлык в меню «Пуск» создаётся всегда, отдельной галочки у него нет.
3. «Далее» запускает тот же exe тихо (`/S`, `/DESKTOP=`, `/STARTUP=`, `/D=`). Окно показывает фазы: проверка файлов, копирование, проверка копии, ярлыки. Под фазой — имя текущего файла.
4. После кода 0 кнопка становится «Запустить» и стартует `LauncherFork.exe`. Игра скачивается уже из лаунчера, после входа.

Тихий `/S` это окно не открывает. Без `/DESKTOP=` и `/STARTUP=` оба ярлыка создаются. Папка с `runtime` или `app` без маркера `.launcherfork-install` отклоняется с кодом 2, и «Далее» на таком пути не нажимается.

Перед заменой файлов и ещё раз после копирования тихий шаг сверяет SHA-256 каждого поставляемого файла (`packaging/verify-payload.ps1`). Несовпадение, отсутствующий файл или недоступный PowerShell останавливают установку с кодом 3. Маркер в этом случае не пишется. Длины файла для проверки недостаточно.

Ярлык с именем «EXBO Launcher» заменяется или удаляется, только если он указывает на этот `LauncherFork.exe`.

Удаление — страница подтверждения NSIS, не окно `SetupWindow`. Оно оставляет каталог игры, `%APPDATA%\EXBO` и `HKCU\SOFTWARE\EXBO` (`EXBO_SESSION`). Установщик эти места не пишет и не стирает. Свой ключ установки — `HKCU\Software\LauncherFork`.

:::caution[Имя без подписи совпадает с официальным лаунчером]
В «Приложениях и возможностях» издатель — EXBO, продукт — EXBO Launcher. Сертификата Authenticode в репозитории нет, файлы не подписываются. Это неофициальный форк, и без подписи его легко принять за официальный ExboLauncher.
:::

Сборка на Windows — `packaging/build-installer.ps1`. На Linux `packaging/build-dist.sh` только компилирует exe: окно установки там не открывается, SHA-256 при установке считает PowerShell на Windows. NSIS упаковывает payload и выполняет тихое копирование. Страниц мастера MUI у установки нет.

## Дистрибутивы

`packaging/build-dist.sh` собирает каталог `dist/` (он в `.gitignore`):

| Файл | Что это |
|---|---|
| `LauncherFork_Setup_<версия>.exe` | Окно установки JavaFX, 460×660, без прав администратора. NSIS упаковывает файлы и копирует их после «Далее». По умолчанию `%LOCALAPPDATA%\LauncherFork`. В системе имя EXBO Launcher |
| `LauncherFork_portable_win64.zip` | Тот же образ, запуск `LauncherFork.exe` |
| `LauncherFork_linux_x64.tar.gz` | jlink, запуск `bin/launcherfork` |
| `SHA256SUMS.txt` | Контрольные суммы |

`LauncherFork.exe` передаёт свои аргументы в JVM, например `-Dlauncher.lang=en`.

На Windows тот же установщик собирает `packaging/build-installer.ps1` (JDK 21 с `jlink`; OpenJFX и NSIS скрипт скачивает сам). С Linux `packaging/build-dist.sh` только компилирует exe. Шаги окна и границы удаления — в разделе выше.

:::caution[Ассеты EXBO в истории git]
PNG из оригинального лаунчера и шрифт `Europe` принадлежат EXBO. README запрещает публичные сборки, пока эти файлы не заменены. Удаление из рабочего дерева историю не чистит.
:::

## Что дальше

- [Демо-вход](../mock-login/) — как пройти pre-main без аккаунта EXBO;
- [Темы](../themes/) — `-Dlauncher.theme`.
