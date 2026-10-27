---
title: Установка и запуск
description: Команды Gradle, скриншоты и состав дистрибутива.
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

## Дистрибутивы

`packaging/build-dist.sh` собирает каталог `dist/` (он в `.gitignore`):

| Файл | Что это |
|---|---|
| `LauncherFork_Setup_<версия>.exe` | Мастер NSIS, x64, без прав администратора. Папка, «Далее», установка. По умолчанию `%LOCALAPPDATA%\LauncherFork`. В системе имя EXBO Launcher |
| `LauncherFork_portable_win64.zip` | Тот же образ, запуск `LauncherFork.exe` |
| `LauncherFork_linux_x64.tar.gz` | jlink, запуск `bin/launcherfork` |
| `SHA256SUMS.txt` | Контрольные суммы |

`LauncherFork.exe` передаёт свои аргументы в JVM, например `-Dlauncher.lang=en`.

На Windows тот же установщик собирает `packaging/build-installer.ps1` (JDK 21 с `jlink`; OpenJFX и NSIS скрипт скачивает сам). С Linux `packaging/build-dist.sh` только компилирует exe: мастер запускается на Windows, SHA-256 файлов там проверяет PowerShell. Подписи кода в репозитории нет. Удаление лаунчера не стирает игру и не трогает `EXBO_SESSION`.

:::caution[Ассеты EXBO в истории git]
PNG из оригинального лаунчера и шрифт `Europe` принадлежат EXBO. README запрещает публичные сборки, пока эти файлы не заменены. Удаление из рабочего дерева историю не чистит.
:::

## Что дальше

- [Демо-вход](../mock-login/) — как пройти pre-main без аккаунта EXBO;
- [Темы](../themes/) — `-Dlauncher.theme`.
