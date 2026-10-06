# Launcher icon (zone palette)

Project assets (own work for this fork, not EXBO assets): `icon.svg` is the master, `gen.py` (cairosvg + Pillow, run in
this folder) renders the pixel-snapped `icon-16..256.png`, the 54 px `icon.png` and `icon.ico`; `preview.png` compares
old and new. The runtime copies live in `src/main/resources/dev/launcherfork/ui/style/image/` (`icon.png`, `icon-<size>.png`).
