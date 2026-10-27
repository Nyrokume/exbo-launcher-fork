---
title: Флаги запуска
description: Свойства -Dlauncher, которые читает LauncherApp.
---

Их передают в `./gradlew run` или в `LauncherFork.exe`. Это свойства процесса лаунчера, не JSON пресета JVM. Пресеты игры описаны на странице [JVM](../../settings/jvm/).

## Интерфейс и данные

| Свойство | Что делает |
|---|---|
| `launcher.lang=ru\|en` | Язык. По умолчанию `ru` |
| `launcher.theme=zone\|exbo\|shadcn\|frost` | Тема. По умолчанию `zone`. `zone-light` трактуется как `zone` |
| `launcher.wallpaper=1..6` | Фиксированные обои. Без свойства — случайные, но не прошлые. В `wallpaper.last` номер из свойства не пишется |
| `launcher.settingsFile=<путь>` | Файл `settings.properties`. По умолчанию `~/.launcher-fork/settings.properties`. Витрина файл не пишет |
| `launcher.mockSpeed=<число>` | Во сколько раз быстрее фейковые прогрессы. По умолчанию 1 |
| `launcher.mock2fa=random\|email\|totp\|none` | Второй фактор mock. Вне тура по умолчанию `random`. Тур без свойства берёт `none` |
| `launcher.onlineTickMs=<мс>` | Как часто mock-онлайн чуть меняется. По умолчанию 10000. `0` — всегда 1284. В туре всегда 0 |
| `launcher.demo` | Любое заданное значение оставляет mock, даже если клиент EXBO установлен |
| `launcher.dev=true` | Клавиши F1–F7 на главном окне. Esc работает и без свойства |

## Витрина и снимки

| Свойство | Что делает |
|---|---|
| `launcher.showcase=true` | Круг экранов на mock, анимации включены, ничего не сохраняется. Перекрывает автотур скриншотов |
| `launcher.showcase.size=1366x800` | Размер главного окна витрины. Ниже 900×580 поднимается до минимума окна |
| `launcher.showcase.captions=true\|false` | Подпись шага в полосе заголовка. По умолчанию включена |
| `launcher.showcase.stills=<каталог>` | PNG шагов первого круга, без подписи |
| `launcher.showcase.loops=N` | Выйти после N кругов. `0` — бесконечно |
| `launcher.screenshots=<каталог>` | Автотур PNG. Анимации выключены, пока не задано иное |
| `launcher.animations=on\|off` | Перебивает ключ настроек `animations`. Тур скриншотов форсит `off`, витрина форсит `on` |

Витрина и каталог скриншотов не собирают live: `LauncherApp` считает их туром.

```bash
./gradlew run -Dlauncher.lang=en -Dlauncher.theme=shadcn
./gradlew run "-Dlauncher.showcase=true"
./gradlew run "-Dlauncher.screenshots=docs/screenshots/zone" "-Dlauncher.animations=off"
```

:::tip
Кавычки вокруг всего `-D…=…` нужны оболочке, если в значении есть слэши или звёздочки, которые она хочет раскрыть. Самому Gradle кавычки не принадлежат.
:::

## Что дальше

- [Установка и запуск](../../getting-started/install-run/) — wrapper и дистрибутив;
- [AppContext](../../architecture/app-context/) — какие из этих флагов оставляют mock.
