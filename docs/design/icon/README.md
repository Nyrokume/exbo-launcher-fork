# Launcher icon (zone palette)

Project assets (own work for this fork, not EXBO assets): `icon.svg` is the master, `gen.py` (cairosvg + Pillow; paths
are relative to the script, run it from anywhere in the checkout) renders the pixel-snapped `icon-16..256.png`, the
54 px `icon.png` and `icon.ico` (used as is by `packaging/build-dist.sh`); `preview.png` compares old and new (the
old icon is read from git history). The runtime copies live in `src/main/resources/dev/launcherfork/ui/style/image/`
(`icon.png`, `icon-<size>.png`).
