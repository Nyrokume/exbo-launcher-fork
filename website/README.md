# Веб-документация

Сайт на Astro 7 и Starlight 0.41. Русский — корневая локаль, английский — те же пути под `/en/`. Исходные заметки в `docs/` репозитория этот каталог не заменяет.

```bash
cd website
npm install
npm run dev
```

Проверка типов и сборка: `npm run verify` (`astro check` и `astro build`). Превью собранного сайта: `npm run preview`.

`site` — `https://nyrokume.github.io`, `base` — `/exbo-launcher-fork`. Публикация — workflow [`.github/workflows/docs-pages.yml`](../.github/workflows/docs-pages.yml) в корне репозитория: он ставит зависимости в `website/`, запускает `npm run verify` и отдаёт `website/dist` в GitHub Pages. В настройках репозитория источник Pages — **GitHub Actions**. У приватного репозитория страница может не открываться снаружи. Workflow всё равно лежит в репозитории.

Сборка копирует `docs/screenshots/zone/*.png` в `dist/__zone-shots/zone`. В `website/` эти PNG не лежат. Кадры других тем на Pages не копируются.

Шрифты (Manrope 400/500/600, Bebas Neue, IBM Plex Mono 400/500) подключаются в `astro.config.mjs` с Google Fonts. Палитра — `src/styles/custom.css`.
