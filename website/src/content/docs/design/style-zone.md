---
title: Стиль zone
description: Краткая сводка токенов STALZONE из STYLE-ZONE.md.
---

Тема по умолчанию. Файл токенов — `src/main/resources/dev/launcherfork/ui/style/theme-zone.css`. Он подключается сразу после `theme.css` и переопределяет значения. Имена токенов общие для `exbo`, `shadcn` и `zone`, поэтому компонент на токенах живёт во всех трёх. Это проверяет `ThemeTokensTest`.

Полный разбор источников, шрифтов и компонентов — `docs/design/STYLE-ZONE.md`. Ниже только то, без чего нельзя сверять UI.

## Палитра

| Токен | zone | Где |
|---|---|---|
| `-background` | `#0a0a0a` | Фон окна |
| `-foreground` | `#ece7e1` | Заголовки и основной текст |
| `-card` | `#181818` | Группы настроек, модалки, меню |
| `-card-description` | `#0f0f0f` | Низ карточки входа, строки меню |
| `-secondary`, `-input`, `-tile` | `#121212` | Плитки и поля |
| `-border` | `#2e2d2d` | Рамки 1 px |
| `-primary`, `-switch-on`, `-ram-filled` | `#e91932` | ИГРАТЬ, ВОЙТИ, сегмент, RAM |
| `-primary-hover`, `-destructive-text` | `#ff3b52` | Hover CTA и красный текст |
| `-error` | `#e63030` | Рамка поля с ошибкой |
| `-window-close` | `#e91932` / hover `#ff3b52` | Крестик |

Белый текст на `#e91932` даёт 4.52:1. Красный текст `#e91932` на `-card` не дотягивает до AA, поэтому красные надписи берут `-destructive-text` (`#ff3b52`). Подсказка пустого поля — `-placeholder` с альфой 0.68. Цифры проверяет `SettingsContrastTest`.

Сайт документации красит те же роли своими переменными Starlight: фон `#0a0a0a`, карточка `#181818`, акцент ссылок `#e91932`, hover `#ff3b52`, приглушённый текст `#bdb2a6`. Это не файл `theme-zone.css`.

## Радиусы

Looked-up значения JavaFX работают только для цветов, поэтому радиус не токен.

- Общая группа контролов в `zone` — 5 px. В `theme.css` у той же группы 0.
- `.zone-radius-sm` — 5 px (кнопки, поля, плитки).
- `.zone-radius-md` — 10 px (карточки, панели, модалки, clip окна входа).

Оба класса задают и фон, и рамку. Числовые радиусы в остальных CSS не ставят. Исключение — круги радио и тумблеров.

## Типографика лаунчера

| Роль | Семейство | Кегль |
|---|---|---|
| Заголовки разделов и модалок | Bebas Neue Regular, UPPERCASE | 22–40 |
| ИГРАТЬ, ВОЙТИ | Bebas Neue Regular | 24 |
| CAPS-пункты | Roboto Medium | 11–15 |
| Подпись настройки | Roboto | 13 |

Letter-spacing в JavaFX CSS нет. CAPS-узлы — обычные Label с `.zone-caps`.

Bebas Neue Regular (OFL 1.1) и Roboto (OFL 1.1) лежат в `ui/style/font/`. Bebas Neue Cyrillic в лаунчер не включён: сайт STALZONE берёт его только для промо-страниц.

Темы `exbo` и `shadcn` по-прежнему на Manrope и Europe. Europe — коммерческий шрифт оригинала, в публичные сборки его не кладут.

## Компоненты, которые стоит узнать

- **CTA** (`.btn-primary` + `.zone-radius-sm`): заливка `-primary`, текст `-primary-foreground`, hover `-primary-hover`.
- **Плитка** (`.tile`): заливка `-tile`, рамка `-border`, hover `-tile-hover`.
- **Поле** (`.zone-input`): заливка `-input`, рамка `-border`, фокус `-ring` (красный).
- **Карточка** (`.card` + `.zone-radius-md`): заливка `-card`, рамка `-border`.

## Что дальше

- [Темы](../../getting-started/themes/) — как включить `zone` и соседей;
- [Маршрут](../interaction/) — какие экраны этой темой покрыты.
