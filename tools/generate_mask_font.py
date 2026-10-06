#!/usr/bin/env python3
"""Generates LauncherMask.ttf: the password-mask font of BulletPasswordFieldSkin (no third-party glyphs).

One real glyph, U+2022 BULLET: a round dot 0.36 em wide, centred 0.27 em above the baseline (inside the x-height
band of Roboto / Manrope), advance 0.625 em, so every dot gets ~0.27 em of air (at the zone field's 16 px: 5.8 px
dots, 4.2 px gaps, whole-pixel 10 px pitch). Line metrics are Roboto's (hhea 1900/-500 per 2048 em), so the caret of
a masked field has the same height as in the plain-text twin.

Run (needs fontTools): python3 tools/generate_mask_font.py src/main/resources/dev/launcherfork/ui/style/font
"""
import os
import sys

from fontTools.fontBuilder import FontBuilder
from fontTools.misc.timeTools import timestampFromString
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.ttGlyphPen import TTGlyphPen

UPM = 1000
DOT = 360        # diameter
CENTER_Y = 270   # dot centre above the baseline
ADVANCE = 625    # 10 px at the zone field size 16 px: whole-pixel pitch, evenly spaced dots
FAMILY = "Launcher Mask"


def circle(cx, cy, r):
    pen = TTGlyphPen(None)
    cu = Cu2QuPen(pen, max_err=1.0, reverse_direction=False)
    k = 0.5522847498 * r
    # clockwise (TrueType outer contour): left -> top -> right -> bottom
    cu.moveTo((cx - r, cy))
    cu.curveTo((cx - r, cy + k), (cx - k, cy + r), (cx, cy + r))
    cu.curveTo((cx + k, cy + r), (cx + r, cy + k), (cx + r, cy))
    cu.curveTo((cx + r, cy - k), (cx + k, cy - r), (cx, cy - r))
    cu.curveTo((cx - k, cy - r), (cx - r, cy - k), (cx - r, cy))
    cu.closePath()
    return pen.glyph()


def main(out_dir):
    fb = FontBuilder(UPM, isTTF=True)
    order = [".notdef", "space", "bullet"]
    fb.setupGlyphOrder(order)
    fb.setupCharacterMap({0x20: "space", 0x2022: "bullet"})
    empty = TTGlyphPen(None).glyph()
    fb.setupGlyf({".notdef": empty, "space": empty, "bullet": circle(ADVANCE // 2, CENTER_Y, DOT // 2)})
    side = (ADVANCE - DOT) // 2
    fb.setupHorizontalMetrics({".notdef": (ADVANCE, 0), "space": (ADVANCE, 0), "bullet": (ADVANCE, side)})
    asc, desc = round(1900 * UPM / 2048), round(-500 * UPM / 2048)
    fb.setupHorizontalHeader(ascent=asc, descent=desc, lineGap=0)
    fb.setupNameTable({"familyName": FAMILY, "styleName": "Regular", "uniqueFontIdentifier": "LauncherMask-Regular",
                       "fullName": FAMILY + " Regular", "psName": "LauncherMask-Regular", "version": "Version 1.000"})
    fb.setupOS2(sTypoAscender=asc, sTypoDescender=desc, sTypoLineGap=0, usWinAscent=asc, usWinDescent=-desc,
                sxHeight=round(1082 * UPM / 2048), sCapHeight=round(1456 * UPM / 2048), achVendID="LFRK")
    fb.setupPost()
    stamp = timestampFromString("Tue Oct 06 00:00:00 2026")  # fixed: reproducible output
    fb.setupHead(unitsPerEm=UPM, created=stamp, modified=stamp)
    path = os.path.join(out_dir, "LauncherMask.ttf")
    fb.save(path)
    print(path)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/dev/launcherfork/ui/style/font")
