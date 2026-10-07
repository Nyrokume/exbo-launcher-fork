import cairosvg, io, re, subprocess
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

# All paths are relative to this script, so it can be run from anywhere inside the repo checkout.
HERE = Path(__file__).resolve().parent                 # docs/design/icon: icon.svg, icon.ico, preview.png
ROOT = HERE.parents[2]                                 # repo root
RES_REL = "src/main/resources/dev/launcherfork/ui/style/image"
RES = ROOT / RES_REL                                   # runtime copies: icon.png, icon-<size>.png
FONTS = ROOT / "src/main/resources/dev/launcherfork/ui/style/font"
SCRATCH = ROOT / "build/icon"                          # git-ignored extras (zoom-small.png)
RUNTIME_SIZES = (16, 32, 48, 64, 128, 256)             # = Theme.APP_ICON_SIZES; 24 only goes into the .ico
# The old (pre-zone) 54px icon.png for the preview, taken from git history: the commit before the new icon.
OLD_REV = "ef84a4fc79a0df5910291f5ccc4d82f575fea9da"

# palette (theme-zone.css / exbo-logo.svg) — app icons (icon-*.png).
BG_TOP, BG_BOT = "#1c1c1c", "#101010"   # around -card #181818 / -sidebar #0f0f0f
BORDER = "#2e2d2d"                       # -border
CREAM, CREAM_D = "#ff3b52", "#b4263d"    # -foreground (logo) / shaded warm beige
RED, RED_D = "#ece7e1", "#a99d90"        # -primary / shaded

# geometry on the 256 grid. Each chevron is vertically symmetric about y=128;
# the right chevron is the left one rotated 180deg around the centre.
BASE = dict(X0=44, TOP=44, T=50, TIP=88, GAP=8)

def geom(size=256):
    """Snap the base geometry to the device-pixel grid of the target size (crisp vertical edges)."""
    if size == 256:
        return dict(BASE)
    u = 256 / size
    snap = lambda v: round(v / u) * u
    g = {k: snap(BASE[k]) for k in ("X0", "TOP", "T", "TIP")}
    gap_px = 0 if size <= 32 else (2 if size <= 96 else 4)
    g["GAP"] = gap_px * u
    return g

def paths(g):
    X0, TOP, T, TIP = g["X0"], g["TOP"], g["T"], g["TIP"]
    XT = 128 - g["GAP"] / 2
    f = lambda v: f"{v:g}" if abs(v - round(v)) < 1e-6 else f"{v:.2f}".rstrip("0")
    up = f"M{f(X0)} {f(TOP)}L{f(XT)} {f(TIP)}V{f(TIP+T)}L{f(X0)} {f(TOP+T)}Z"                  # front (light)
    lo = f"M{f(X0)} {f(256-TOP)}L{f(XT)} {f(256-TIP)}V{f(256-TIP-T)}L{f(X0)} {f(256-TOP-T)}Z"  # back (dark)
    return up, lo

def rot(path):
    toks = re.findall(r"[MLVZ]|-?\d+(?:\.\d+)?", path)
    res=[]; i=0; cmd=None
    while i < len(toks):
        t=toks[i]
        if t in "MLVZ":
            cmd=t; res.append(t); i+=1; continue
        if cmd in "ML":
            res.append(f"{256-float(toks[i]):g} {256-float(toks[i+1]):g}"); i+=2
        else:
            res.append(f"{256-float(t):g}"); i+=1
    return "".join(res)

def svg(border_w=2.0, rx=40, size=256):
    up, lo = paths(geom(size))
    rfront, rback = rot(up), rot(lo)
    h = border_w/2
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 256 256">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="{BG_TOP}"/>
      <stop offset="1" stop-color="{BG_BOT}"/>
    </linearGradient>
  </defs>
  <!-- dark launcher surface with a 1px card border -->
  <rect x="{h:g}" y="{h:g}" width="{256-border_w:g}" height="{256-border_w:g}" rx="{rx-h:g}" fill="url(#bg)" stroke="{BORDER}" stroke-width="{border_w:g}"/>
  <!-- left chevron (logo cream): back arm, then front arm -->
  <path fill="{CREAM_D}" d="{lo}"/>
  <path fill="{CREAM}" d="{up}"/>
  <!-- right chevron (accent red): back arm, then front arm -->
  <path fill="{RED_D}" d="{rback}"/>
  <path fill="{RED}" d="{rfront}"/>
</svg>
'''

(HERE / "icon.svg").write_text(svg())

def render(size):
    bw = 256/size * (2 if size >= 128 else 1)   # exactly 1 (2) device px border
    png = cairosvg.svg2png(bytestring=svg(bw, size=size).encode(), output_width=size, output_height=size)
    return Image.open(io.BytesIO(png)).convert("RGBA")

sizes = [16,24,32,48,64,128,256]
imgs = {}
for s in sizes:
    imgs[s] = render(s)
    if s in RUNTIME_SIZES: imgs[s].save(RES / f"icon-{s}.png")
old_png = subprocess.run(["git", "-C", str(ROOT), "show", f"{OLD_REV}:{RES_REL}/icon.png"],
                         check=True, capture_output=True).stdout
old = Image.open(io.BytesIO(old_png)).convert("RGBA")
render(old.width).save(RES / "icon.png")
imgs[256].save(HERE / "icon.ico", sizes=[(s,s) for s in sizes], append_images=[imgs[s] for s in sizes[:-1]])

W, H = 1180, 440
pv = Image.new("RGBA", (W,H), "#d9d9d9")
d = ImageDraw.Draw(pv)
f = ImageFont.truetype(str(FONTS / "Roboto-Medium.ttf"), 18); fs = ImageFont.truetype(str(FONTS / "Roboto-Regular.ttf"), 14)
d.rectangle([0,H-70,W,H], fill="#c6c6c6")
pv.alpha_composite(old.resize((256,256), Image.NEAREST), (40,50))
d.text((40,20),"OLD icon.png (54px, upscaled)",fill="#222",font=f)
d.line([330,20,330,H-90], fill="#999", width=2)
d.text((360,20),"NEW (zone palette)",fill="#222",font=f)
x=360
for s in (48,64,128,256):
    pv.alpha_composite(imgs[s], (x, 50 + 256 - s)); d.text((x, 50+256+6), f"{s}px", fill="#333", font=fs); x += s + 30
d.text((20,H-45),"taskbar 1:1",fill="#222",font=fs)
pv.alpha_composite(old.resize((48,48), Image.LANCZOS), (120,H-59)); d.text((172,H-45),"old",fill="#444",font=fs)
xx=240
for s in (16,24,32,48):
    pv.alpha_composite(imgs[s], (xx, H-35-s//2)); xx += s+20
d.text((xx,H-45),"new 16/24/32/48",fill="#444",font=fs)
pv.save(HERE / "preview.png")

zs = Image.new("RGBA",(16*12+24*8+32*6+48*4+5*20, 212),"#d9d9d9"); x=10
for s,k in ((16,12),(24,8),(32,6),(48,4)):
    zs.alpha_composite(imgs[s].resize((s*k,s*k),Image.NEAREST),(x,10)); x+=s*k+20
SCRATCH.mkdir(parents=True, exist_ok=True); zs.save(SCRATCH / "zoom-small.png")
print("done")
