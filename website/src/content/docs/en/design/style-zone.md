---
title: Zone style
description: A short summary of the STALZONE tokens from STYLE-ZONE.md.
---

The default theme. Tokens live in `src/main/resources/dev/launcherfork/ui/style/theme-zone.css`. The file is attached right after `theme.css` and overrides the values. Token names are shared by `exbo`, `shadcn`, and `zone`, so a component that uses tokens works in all three. `ThemeTokensTest` checks that.

The full write-up of sources, fonts, and components is `docs/design/STYLE-ZONE.md`. Below is only what you need to check the UI against.

## Palette

| Token | zone | Where |
|---|---|---|
| `-background` | `#0a0a0a` | Window background |
| `-foreground` | `#ece7e1` | Headings and body text |
| `-card` | `#181818` | Settings groups, modals, menus |
| `-card-description` | `#0f0f0f` | Bottom of the login card, menu rows |
| `-secondary`, `-input`, `-tile` | `#121212` | Tiles and fields |
| `-border` | `#2e2d2d` | 1 px borders |
| `-primary`, `-switch-on`, `-ram-filled` | `#e91932` | PLAY, LOG IN, segment, RAM |
| `-primary-hover`, `-destructive-text` | `#ff3b52` | CTA hover and red text |
| `-error` | `#e63030` | Border of a field in error |
| `-window-close` | `#e91932` / hover `#ff3b52` | Close glyph |

White text on `#e91932` is 4.52:1. Red text `#e91932` on `-card` misses AA, so red labels use `-destructive-text` (`#ff3b52`). The empty-field hint is `-placeholder` at alpha 0.68. `SettingsContrastTest` checks the ratios.

This documentation site paints the same roles with its own Starlight variables: background `#0a0a0a`, card `#181818`, link accent `#e91932`, hover `#ff3b52`, muted text `#bdb2a6`. That is not `theme-zone.css`.

## Radii

JavaFX looked-up values work for colors only, so a radius is not a token.

- The shared control group in `zone` is 5 px. The same group in `theme.css` is 0.
- `.zone-radius-sm` is 5 px (buttons, fields, tiles).
- `.zone-radius-md` is 10 px (cards, panels, modals, the login-window clip).

Both classes set the background radius and the border radius. Other CSS files do not set numeric radii. Circles on radios and switches are the exception.

## Launcher typography

| Role | Family | Size |
|---|---|---|
| Section and modal headings | Bebas Neue Regular, UPPERCASE | 22–40 |
| PLAY, LOG IN | Bebas Neue Regular | 24 |
| CAPS items | Roboto Medium | 11–15 |
| A setting label | Roboto | 13 |

JavaFX CSS has no letter-spacing. CAPS nodes are ordinary labels with `.zone-caps`.

Bebas Neue Regular (OFL 1.1) and Roboto (OFL 1.1) live in `ui/style/font/`. Bebas Neue Cyrillic is not in the launcher. The STALZONE site uses it only on promo pages.

The `exbo` and `shadcn` themes still use Manrope and Europe. Europe is the original commercial font and is not for public builds.

## Components worth knowing

- **CTA** (`.btn-primary` + `.zone-radius-sm`): fill `-primary`, text `-primary-foreground`, hover `-primary-hover`.
- **Tile** (`.tile`): fill `-tile`, border `-border`, hover `-tile-hover`.
- **Field** (`.zone-input`): fill `-input`, border `-border`, focus `-ring` (red).
- **Card** (`.card` + `.zone-radius-md`): fill `-card`, border `-border`.

## What next

- [Themes](../../getting-started/themes/) — how to turn `zone` and the others on;
- [Path](../interaction/) — which screens this theme covers.
