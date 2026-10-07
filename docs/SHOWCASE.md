# Режим витрины (`-Dlauncher.showcase=true`)

Короткий режим для демонстрации редизайна: лаунчер сам проходит по экранам с анимациями, на **мок-данных**,
**ничего не сохраняя**. Подходит для записи промо-видео и для ручного просмотра.

## Запуск

```bash
./gradlew showcase
# или
./gradlew run -Dlauncher.showcase=true
# или из installDist:
java -Dlauncher.showcase=true -cp 'build/install/exbo-launcher-fork/lib/*' dev.launcherfork.app.MainKt
```

**Escape** — выход.

## Свойства

| Свойство | По умолчанию | Смысл |
|---|---|---|
| `launcher.showcase` | off | `true` / `on` / `yes` / `1` — включить витрину |
| `launcher.showcase.size` | `1366x800` | размер главного окна (`WxH`; меньше минимума 900×580 поднимается) |
| `launcher.showcase.captions` | `true` | подпись шага (чипы в полосе заголовка, **не** в nav-ряду) |
| `launcher.showcase.stills` | — | каталог: PNG каждого шага первого круга (без подписи) |
| `launcher.showcase.loops` | `0` | сколько полных кругов, потом выход; `0` = бесконечно |

Анимации в витрине **включены** (если нет `-Dlauncher.animations=off`). Медленный drift обоев включается сам,
если `-Dlauncher.wallpaper.drift` не задан. `-Dlauncher.settingsFile` в витрине **игнорируется**: настройки только
в памяти, `wallpaper.last` не пишется.

## Маршрут (цикл ~55 с после первого входа)

1. Главное окно + обои → другие обои → темы (белая / EXBO / shadcn / тёмная zone)
2. Баннер игры → настройки (RAM, «Поведение», переключатель «В трей»)
3. Меню профиля → «Сообщества» → загрузка / пауза / готовность
4. Окно входа → регистрация (возраст, анкета) → вход (`player` / `secret`) → снова

Шаги по 3–4 с. Навигация — через общий `TourActions` (тот же путь, что у screenshot-тура). Логин-панели
Front.end.master не правятся: только переходы `StageNavigator`, как в `ScreenshotTour`.

## Запись

Файлы **не** в git. Типичные артефакты на машине сборки:

- `showcase/showcase-1366x800.mp4` — H.264, yuv420p, 30 fps, полный круг
- `showcase/showcase-1920x1080.mp4` — то же в Full HD
- `showcase/showcase.gif` — palettegen/paletteuse, ≤ 8 МБ, ~960 px
- `showcase/stills/*.png` — 6–8 ключевых кадров

Пример записи с Xvfb (`ffmpeg` 7.1 и ImageMagick, без доп. пакетов). Запись останавливается, когда витрина
сама закрывается после `launcher.showcase.loops` кругов (в stderr строка `showcase finished`), а не по
фиксированной длительности: иначе хвост видео — пустой чёрный экран после закрытия окон.

```bash
Xvfb :19 -screen 0 1920x1080x24 -ac +render -noreset &
# фон за маленьким окном входа (только для видео, в самом окне входа обоев нет):
convert src/main/resources/dev/launcherfork/ui/wallpapers/03.jpg -resize 1920x1080^ -gravity center \
  -extent 1920x1080 -blur 0x18 -fill black -colorize 40% /tmp/backdrop.png
DISPLAY=:19 display -window root /tmp/backdrop.png
DISPLAY=:19 ./gradlew showcase -Dlauncher.showcase.loops=1 -Dlauncher.showcase.size=1920x1080 &
APP=$!
ffmpeg -f x11grab -draw_mouse 0 -framerate 30 -video_size 1920x1080 -i :19.0 \
  -c:v libx264 -pix_fmt yuv420p -r 30 showcase/showcase-1920x1080.mp4 &
FF=$!
wait $APP; kill -INT $FF; wait $FF
# проверка: вывод должен быть пустым
ffmpeg -i showcase/showcase-1920x1080.mp4 -vf blackdetect=d=0.3:pix_th=0.05 -an -f null - 2>&1 | grep black_start
```

После последнего круга главное окно держится на экране ещё 2.5 с, затем лаунчер закрывается.

## Что не делает витрина

- не пишет `~/.launcher-fork/settings.properties` и любой `-Dlauncher.settingsFile`
- не трогает `wallpaper.last`
- не использует реальные аккаунты / почты (ник `player`)
- не меняет панели входа / регистрации Front.end.master
