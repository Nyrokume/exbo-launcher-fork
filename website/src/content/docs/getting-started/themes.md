---
title: Темы
description: Свойство launcher.theme, обои главного окна и судьба zone-light.
---

Цвета берутся из токенов. В Kotlin и FXML литеральных цветов нет. Файл темы подключается к Scene в `Theme.apply` сразу после `theme.css`.

## Какую тему включает свойство

`-Dlauncher.theme` (без свойства — `zone`):

| Значение | Что подключается |
|---|---|
| `zone` | `theme-zone.css`. Красный STALZONE, Bebas Neue и Roboto. По умолчанию |
| `exbo` | Исходные цвета EXBO на той же раскладке |
| `shadcn` | Neutral в духе shadcn/ui |
| `frost` | Морозный прототип, `theme-frost.css`, свой набор обоев |
| `zone-light` | Принимается и трактуется как `zone` (`Theme.kt`, `StageNavigator`) |

Переключатель в настройках «Тема: Тёмная / Белая» описан в changelog (#12). В текущем `settings.fxml` такой строки нет. Ключ `appearance` в `settings.properties` по-прежнему описан как `dark | light`, но свойство запуска `zone-light` тему не осветляет.

Витрина белую тему не показывает: в туре только zone, exbo и shadcn.

## Обои главного окна

Шесть снимков в `ui/wallpapers/`, манифест `wallpapers.txt`. При каждом запуске выбираются случайные обои, но не те же, что в прошлый раз (`wallpaper.last` в `~/.launcher-fork/settings.properties`).

`-Dlauncher.wallpaper=1..6` фиксирует номер. Такой выбор в `wallpaper.last` не пишется. Автотур без свойства берёт `03`.

Картинка заполняет окно (cover с точкой фокуса) при ресайзе и разворачивании. Грузится только выбранный файл, в фоне. Если файла нет или он битый, остаётся однотонный фон.

Текст читается за счёт полупрозрачных скримов из токенов. `WallpaperContrastTest` сравнивает текст с яркими пикселями под ним.

## Шрифты лаунчера и этого сайта

В теме `zone` заголовки и крупные кнопки — Bebas Neue Regular, текст — Roboto, CAPS — Roboto Medium. Их регистрирует `Theme.zoneFonts`.

Этот сайт шрифты лаунчера не встраивает. Здесь основной текст — Manrope, код — IBM Plex Mono, Bebas Neue только у заголовка hero и H1.

## Что дальше

- [Стиль zone](../../design/style-zone/) — таблица токенов;
- [Флаги запуска](../../reference/gradle-flags/) — полный список свойств.
