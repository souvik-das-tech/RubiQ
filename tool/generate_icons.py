"""Draws the RubiQ logo and launcher icons: an isometric 3x3 cube (white top, green and red
sides, the app's sticker colours) on the app's indigo-to-cyan accent gradient.

Run from the project root:  python tool/generate_icons.py
Writes the adaptive-icon layers and legacy icons into app/src/main/res, and the logos into branding/.
"""
import math
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
BRANDING = os.path.join(ROOT, "branding")

INDIGO = (0x63, 0x66, 0xF1)
CYAN = (0x06, 0xB6, 0xD4)
BODY = (0x0B, 0x11, 0x20)
WHITE = (0xF8, 0xFA, 0xFC)
GREEN = (0x22, 0xC5, 0x5E)
RED = (0xEF, 0x44, 0x44)
SS = 4  # supersampling factor for smooth edges

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def shade(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c)


def gradient(size):
    """Diagonal indigo (top-left) to cyan (bottom-right) gradient."""
    img = Image.new("RGB", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * (size - 1))
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(INDIGO, CYAN))
    return img


def rounded(poly, t, steps=8):
    """Polygon with each corner cut `t` of the way along its edges and replaced by a curve."""
    out = []
    n = len(poly)
    for k in range(n):
        p, prev, nxt = poly[k], poly[k - 1], poly[(k + 1) % n]
        a = (p[0] + (prev[0] - p[0]) * t, p[1] + (prev[1] - p[1]) * t)
        b = (p[0] + (nxt[0] - p[0]) * t, p[1] + (nxt[1] - p[1]) * t)
        for i in range(steps + 1):
            s = i / steps
            out.append((
                (1 - s) ** 2 * a[0] + 2 * (1 - s) * s * p[0] + s ** 2 * b[0],
                (1 - s) ** 2 * a[1] + 2 * (1 - s) * s * p[1] + s ** 2 * b[1],
            ))
    return out


def cube_faces(cx, cy, r):
    """The three visible faces of an isometric cube as (origin, u, v, colour)."""
    c30 = math.cos(math.radians(30))
    top = (cx, cy - r)
    ur = (cx + r * c30, cy - r / 2)
    ul = (cx - r * c30, cy - r / 2)
    centre = (cx, cy)
    sub = lambda a, b: (a[0] - b[0], a[1] - b[1])
    return [
        (ul, sub(top, ul), sub(centre, ul), shade(WHITE, 1.0)),  # top
        (ul, sub(centre, ul), (0, r), shade(GREEN, 0.92)),  # left
        (centre, sub(ur, centre), (0, r), shade(RED, 0.82)),  # right
    ]


def draw_cube(draw, cx, cy, r, mono=False):
    c30 = math.cos(math.radians(30))
    hexagon = [(cx, cy - r), (cx + r * c30, cy - r / 2), (cx + r * c30, cy + r / 2),
               (cx, cy + r), (cx - r * c30, cy + r / 2), (cx - r * c30, cy - r / 2)]
    if not mono:
        draw.polygon(rounded(hexagon, 0.06), fill=BODY)
    gap = 0.09
    for origin, u, v, colour in cube_faces(cx, cy, r * 0.94):
        for i in range(3):
            for j in range(3):
                a0, a1 = (i + gap) / 3, (i + 1 - gap) / 3
                b0, b1 = (j + gap) / 3, (j + 1 - gap) / 3
                pt = lambda a, b: (origin[0] + u[0] * a + v[0] * b, origin[1] + u[1] * a + v[1] * b)
                quad = [pt(a0, b0), pt(a1, b0), pt(a1, b1), pt(a0, b1)]
                draw.polygon(rounded(quad, 0.22), fill=(255, 255, 255, 255) if mono else colour)


def render(size, background, cube_scale, mono=False, corner=None):
    big = size * SS
    if background:
        img = gradient(big).convert("RGBA")
    else:
        img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw_cube(ImageDraw.Draw(img), big / 2, big / 2, big * cube_scale, mono)
    if corner is not None:
        mask = Image.new("L", (big, big), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, big - 1, big - 1], radius=big * corner, fill=255)
        img.putalpha(mask)
    return img.resize((size, size), Image.LANCZOS)


def save(img, *path):
    full = os.path.join(*path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full, optimize=True)


def wordmark(height):
    """Icon plus the "RubiQ Offline" name, for README / store listing use."""
    icon = render(height, True, 0.30, corner=0.22)
    font_path = "C:/Windows/Fonts/segoeuib.ttf"
    sub_path = "C:/Windows/Fonts/segoeui.ttf"
    title = ImageFont.truetype(font_path, int(height * 0.42)) if os.path.exists(font_path) else ImageFont.load_default()
    sub = ImageFont.truetype(sub_path, int(height * 0.17)) if os.path.exists(sub_path) else ImageFont.load_default()
    pad = int(height * 0.25)
    tw = max(title.getbbox("RubiQ")[2], sub.getbbox("OFFLINE CUBE SOLVER")[2])
    img = Image.new("RGBA", (height + pad + tw + pad // 2, height), (0x07, 0x0B, 0x14, 255))
    img.alpha_composite(icon, (0, 0))
    d = ImageDraw.Draw(img)
    x = height + pad
    d.text((x, height * 0.12), "RubiQ", font=title, fill=(0xF1, 0xF5, 0xF9))
    d.text((x, height * 0.64), "OFFLINE CUBE SOLVER", font=sub, fill=CYAN)
    return img


def main():
    for name, f in DENSITIES.items():
        folder = os.path.join(RES, f"mipmap-{name}")
        fg = round(108 * f)
        # Adaptive layers: the cube stays well inside the 66dp safe zone.
        save(render(fg, False, 0.25), folder, "ic_launcher_foreground.png")
        save(render(fg, False, 0.25, mono=True), folder, "ic_launcher_monochrome.png")
        # Legacy icon (also used by some launchers and the settings screen).
        save(render(round(48 * f), True, 0.33, corner=0.22), folder, "ic_launcher.png")
    save(render(1024, True, 0.33), BRANDING, "RubiQ_logo_1024.png")
    save(render(512, True, 0.33), BRANDING, "RubiQ_playstore_512.png")
    save(wordmark(256), BRANDING, "RubiQ_wordmark.png")


if __name__ == "__main__":
    main()
