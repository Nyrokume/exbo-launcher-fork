---
title: Themes
description: The launcher.theme property, main-window wallpapers, and zone-light.
---

Colors come from tokens. Kotlin and FXML do not set literal colors. `Theme.apply` attaches the theme file to the Scene right after `theme.css`.

## Which theme the property selects

`-Dlauncher.theme` (unset means `zone`):

| Value | What is attached |
|---|---|
| `zone` | `theme-zone.css`. STALZONE red, Bebas Neue, and Roboto. The default |
| `exbo` | The original EXBO colors on the same layout |
| `shadcn` | A neutral look in the spirit of shadcn/ui |
| `frost` | An icy prototype, `theme-frost.css`, its own wallpaper pack |
| `zone-light` | Accepted and treated as `zone` (`Theme.kt`, `StageNavigator`) |

Changelog entry #12 describes a settings switch “Theme: Dark / White”. Current `settings.fxml` has no such row. The `appearance` key in `settings.properties` is still documented as `dark | light`, but the launch property `zone-light` does not lighten the theme.

The showcase does not show a white theme. The tour uses zone, exbo, and shadcn only.

## Main-window wallpapers

Six shots live in `ui/wallpapers/`, with the manifest `wallpapers.txt`. Each launch picks a random wallpaper, but not the one used last time (`wallpaper.last` in `~/.launcher-fork/settings.properties`).

`-Dlauncher.wallpaper=1..6` pins the number. That choice is not written to `wallpaper.last`. The tour without the property uses `03`.

The image covers the window (cover, with a focus point) on resize and maximize. Only the chosen file is loaded, in the background. A missing or broken file leaves a flat background.

Text stays readable because of translucent scrims from the theme tokens. `WallpaperContrastTest` compares the text with the bright pixels under it.

## Launcher fonts and this site

On the `zone` theme, headings and large buttons are Bebas Neue Regular, body text is Roboto, and CAPS labels are Roboto Medium. `Theme.zoneFonts` registers them.

This site does not embed the launcher fonts. Body text here is Manrope, code is IBM Plex Mono, and Bebas Neue is only the hero title and H1.

## What next

- [Zone style](../../design/style-zone/) — the token table;
- [Launch flags](../../reference/gradle-flags/) — the full property list.
