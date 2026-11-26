#!/usr/bin/env python3
"""Genera el icono de MyMouse (docs/spec-v4.7-universo.md §3.3) para Windows y Android.

Uso (desde cualquier carpeta):  python3 tools/icon/make_icons.py [--preview RUTA.png]

Necesita Pillow. Escribe, sobre el repositorio:
  windows/src/MyMouse.Host/Assets/MyMouse.ico          multi-tamaño (16, 20, 24, 32, 40, 48, 64 y 256)
  android/app/src/main/res/mipmap-*dpi/ic_launcher[_round].png   PNG de respaldo (Android 7 y anteriores)
  android/app/src/main/res/mipmap-anydpi-v26/ic_launcher[_round].xml   icono adaptativo
  android/app/src/main/res/drawable/ic_launcher_{background,foreground}.xml   VectorDrawable del adaptativo
  android/app/src/main/res/drawable-nodpi/ic_app_logo.png   el mismo dibujo para la barra superior de la app

El dibujo se define una sola vez, en coordenadas 0–1 (ver DISEÑO), y de él salen los PNG (con Pillow) y los vectores (a mano).
A 24 px o menos se simplifica: sin estrellas y con el trazo más grueso.
"""
import argparse
import math
import pathlib

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = pathlib.Path(__file__).resolve().parents[2]

# ---- DISEÑO (coordenadas 0–1 sobre el cuadrado del icono) ----
VIOLET = (0x7C, 0x3A, 0xED)
BLUE = (0x25, 0x63, 0xEB)
MAGENTA = (0xDB, 0x27, 0x77)
LAVENDER = (0xA5, 0xB4, 0xFC)
CORNER_RADIUS = 0.22                        # radio del cuadrado redondeado
PAD = (0.20, 0.27, 0.80, 0.73)              # trackpad de cristal: izquierda, arriba, derecha, abajo
PAD_RADIUS = 0.085
STARS = [(0.14, 0.16, 0.013), (0.82, 0.12, 0.017), (0.89, 0.84, 0.012), (0.16, 0.86, 0.010)]
# Trazo: curva de Bézier cúbica que termina en el punto (el dedo o el cursor).
CURVE = [(0.29, 0.62), (0.40, 0.28), (0.53, 0.74), (0.69, 0.43)]
STROKE = 0.036
STROKE_SMALL = 0.075
DOT = 0.050

ICO_SIZES = [16, 20, 24, 32, 40, 48, 64, 256]
PNG_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
SMALL = 24  # hasta este tamaño (inclusive) se usa la versión simplificada


def bezier(t):
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = CURVE
    u = 1 - t
    return (
        u**3 * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t**3 * x3,
        u**3 * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t**3 * y3,
    )


def curve_points(n=64):
    return [bezier(i / n) for i in range(n + 1)]


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def radial_layer(size, cx, cy, radius, rgb, alpha):
    """Capa RGBA con un degradado radial de color rgb (alfa máximo `alpha` en el centro, 0 en el borde)."""
    layer = Image.new("RGBA", (size, size), rgb + (0,))
    px = layer.load()
    for y in range(size):
        for x in range(size):
            d = math.hypot(x / size - cx, y / size - cy) / radius
            if d < 1:
                px[x, y] = rgb + (round(alpha * 255 * (1 - d) ** 1.6),)
    return layer


def layer(big, painter):
    """Capa transparente con lo que pinte `painter(ImageDraw)`: se mezcla con alpha_composite (ImageDraw sobre RGBA no mezcla)."""
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    painter(ImageDraw.Draw(img))
    return img


def draw_art(size):
    """El dibujo a pantalla completa (sin recortar) a `size` px: lo recortan después la máscara redonda o cuadrada."""
    big = size * 4 if size < 256 else size * 2
    small = size <= SMALL
    # Fondo: degradado diagonal violeta → azul.
    grad = Image.new("RGB", (big, big))
    gp = grad.load()
    for y in range(big):
        for x in range(big):
            gp[x, y] = lerp(VIOLET, BLUE, min(1.0, (x + y) / (2 * big)))
    img = grad.convert("RGBA")
    if not small:
        # Una nebulosa magenta suave a la derecha, y estrellas.
        img = Image.alpha_composite(img, radial_layer(big, 0.78, 0.55, 0.45, MAGENTA, 0.30))
        img = Image.alpha_composite(img, layer(big, lambda d: [
            d.ellipse([(x - r) * big, (y - r) * big, (x + r) * big, (y + r) * big], fill=(255, 255, 255, 235)) for x, y, r in STARS
        ]))
    # Trackpad de cristal: blanco al 18 % con borde blanco al 60 %.
    box = [PAD[0] * big, PAD[1] * big, PAD[2] * big, PAD[3] * big]
    border = max(2, round((0.040 if small else 0.020) * big))
    img = Image.alpha_composite(img, layer(big, lambda d: d.rounded_rectangle(
        box, radius=PAD_RADIUS * big, fill=(255, 255, 255, 46), outline=(255, 255, 255, 153), width=border)))
    if not small:
        # Brillo superior del cristal.
        def shine(sd):
            top, bottom = box[1] + border, box[1] + (box[3] - box[1]) * 0.35
            for i in range(int(bottom - top)):
                a = round(36 * (1 - i / (bottom - top)))
                sd.line([(box[0] + PAD_RADIUS * big * 0.6, top + i), (box[2] - PAD_RADIUS * big * 0.6, top + i)], fill=(255, 255, 255, a))
        img = Image.alpha_composite(img, layer(big, shine))
    # Trazo luminoso con halo lavanda y el punto del final.
    pts = [(x * big, y * big) for x, y in curve_points()]
    width = round((STROKE_SMALL if small else STROKE) * big)
    ex, ey = pts[-1]

    def halo(gd):
        gd.line(pts, fill=LAVENDER + (210,), width=round(width * 2.4), joint="curve")
        gr = DOT * big * 2.4
        gd.ellipse([ex - gr, ey - gr, ex + gr, ey + gr], fill=LAVENDER + (230,))

    glow = layer(big, halo).filter(ImageFilter.GaussianBlur(big * (0.012 if small else 0.018)))
    img = Image.alpha_composite(img, glow)

    def trace(d):
        d.line(pts, fill=(255, 255, 255, 255), width=width, joint="curve")
        for x, y in (pts[0], pts[-1]):  # extremos redondeados
            d.ellipse([x - width / 2, y - width / 2, x + width / 2, y + width / 2], fill=(255, 255, 255, 255))
        dr = DOT * big * (1.15 if small else 1.0)
        d.ellipse([ex - dr, ey - dr, ex + dr, ey + dr], fill=(255, 255, 255, 255))

    img = Image.alpha_composite(img, layer(big, trace))
    return img.resize((size, size), Image.LANCZOS)


def rounded_mask(size, radius_frac=CORNER_RADIUS, circle=False):
    big = size * 4
    mask = Image.new("L", (big, big), 0)
    d = ImageDraw.Draw(mask)
    if circle:
        d.ellipse([0, 0, big - 1, big - 1], fill=255)
    else:
        d.rounded_rectangle([0, 0, big - 1, big - 1], radius=radius_frac * big, fill=255)
    return mask.resize((size, size), Image.LANCZOS)


def icon(size, circle=False):
    art = draw_art(size)
    mask = rounded_mask(size, circle=circle)
    art.putalpha(ImageChops.multiply(art.getchannel("A"), mask))
    return art


# ---- Android: VectorDrawable del icono adaptativo (lienzo 108 dp; el dibujo cabe en la zona segura de 66 dp) ----
VP = 108.0
SCALE = 80.0  # el dibujo 0–1 se escala a 80 dp y se centra


def vx(u):
    return round(VP / 2 + (u - 0.5) * SCALE, 2)


def hexc(rgb, alpha=255):
    return "#%02X%02X%02X%02X" % ((alpha,) + tuple(rgb))


def vector_background():
    stars = "\n".join(
        f'    <path android:fillColor="#EBFFFFFF" android:pathData="M{vx(x) - r * 60:.2f},{vx(y):.2f}a{r * 60:.2f},{r * 60:.2f} 0 1,0 {r * 120:.2f},0a{r * 60:.2f},{r * 60:.2f} 0 1,0 {-r * 120:.2f},0z" />'
        for x, y, r in [(0.10, 0.12, 0.016), (0.90, 0.08, 0.020), (0.95, 0.88, 0.015), (0.08, 0.90, 0.013), (0.50, 0.04, 0.012)]
    )
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generado por tools/icon/make_icons.py: no editar a mano. Fondo del icono adaptativo: universo en miniatura. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108">
                <item android:offset="0" android:color="{hexc(VIOLET)}" />
                <item android:offset="1" android:color="{hexc(BLUE)}" />
            </gradient>
        </aapt:attr>
    </path>
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="86"
                android:centerY="60"
                android:gradientRadius="52">
                <item android:offset="0" android:color="{hexc(MAGENTA, 0x55)}" />
                <item android:offset="1" android:color="{hexc(MAGENTA, 0x00)}" />
            </gradient>
        </aapt:attr>
    </path>
{stars}
</vector>
'''


def vector_foreground():
    p = [(vx(x), vx(y)) for x, y in CURVE]
    path = f"M{p[0][0]},{p[0][1]}C{p[1][0]},{p[1][1]} {p[2][0]},{p[2][1]} {p[3][0]},{p[3][1]}"
    left, top, right, bottom = vx(PAD[0]), vx(PAD[1]), vx(PAD[2]), vx(PAD[3])
    r = round(PAD_RADIUS * SCALE, 2)
    w, h = round(right - left - 2 * r, 2), round(bottom - top - 2 * r, 2)
    pad = f"M{left + r},{top}h{w}a{r},{r} 0 0,1 {r},{r}v{h}a{r},{r} 0 0,1 {-r},{r}h{-w}a{r},{r} 0 0,1 {-r},{-r}v{-h}a{r},{r} 0 0,1 {r},{-r}z"
    ex, ey = p[3]
    sw = round(STROKE * SCALE, 2)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generado por tools/icon/make_icons.py: no editar a mano. Primer plano del icono adaptativo: trackpad de cristal y trazo luminoso. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#2EFFFFFF"
        android:strokeColor="#99FFFFFF"
        android:strokeWidth="1.6"
        android:pathData="{pad}" />
    <path
        android:pathData="{path}"
        android:strokeColor="#55A5B4FC"
        android:strokeWidth="{round(sw * 3.2, 2)}"
        android:strokeLineCap="round" />
    <path
        android:pathData="{path}"
        android:strokeColor="#99A5B4FC"
        android:strokeWidth="{round(sw * 1.9, 2)}"
        android:strokeLineCap="round" />
    <path
        android:pathData="{path}"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="{sw}"
        android:strokeLineCap="round" />
    <path android:pathData="M{ex - DOT * SCALE * 2.6:.2f},{ey:.2f}a{DOT * SCALE * 2.6:.2f},{DOT * SCALE * 2.6:.2f} 0 1,0 {DOT * SCALE * 5.2:.2f},0a{DOT * SCALE * 2.6:.2f},{DOT * SCALE * 2.6:.2f} 0 1,0 {-DOT * SCALE * 5.2:.2f},0z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="{ex}"
                android:centerY="{ey}"
                android:gradientRadius="{DOT * SCALE * 2.6:.2f}">
                <item android:offset="0" android:color="#CCA5B4FC" />
                <item android:offset="1" android:color="#00A5B4FC" />
            </gradient>
        </aapt:attr>
    </path>
    <path android:fillColor="#FFFFFFFF" android:pathData="M{ex - DOT * SCALE:.2f},{ey:.2f}a{DOT * SCALE:.2f},{DOT * SCALE:.2f} 0 1,0 {DOT * SCALE * 2:.2f},0a{DOT * SCALE:.2f},{DOT * SCALE:.2f} 0 1,0 {-DOT * SCALE * 2:.2f},0z" />
</vector>
'''


ADAPTIVE = '''<?xml version="1.0" encoding="utf-8"?>
<!-- Generado por tools/icon/make_icons.py: no editar a mano. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'''


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--preview", help="además, guarda aquí una vista previa de 256 px")
    args = ap.parse_args()

    ico = ROOT / "windows/src/MyMouse.Host/Assets/MyMouse.ico"
    ico.parent.mkdir(parents=True, exist_ok=True)
    frames = {s: icon(s) for s in ICO_SIZES}
    biggest = frames[256]
    biggest.save(ico, format="ICO", sizes=[(s, s) for s in ICO_SIZES], append_images=[frames[s] for s in ICO_SIZES if s != 256])

    res = ROOT / "android/app/src/main/res"
    for density, px in PNG_SIZES.items():
        folder = res / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        icon(px).save(folder / "ic_launcher.png", optimize=True)
        icon(px, circle=True).save(folder / "ic_launcher_round.png", optimize=True)
    (res / "drawable-nodpi").mkdir(parents=True, exist_ok=True)
    icon(192).save(res / "drawable-nodpi/ic_app_logo.png", optimize=True)
    write(res / "mipmap-anydpi-v26/ic_launcher.xml", ADAPTIVE)
    write(res / "mipmap-anydpi-v26/ic_launcher_round.xml", ADAPTIVE)
    write(res / "drawable/ic_launcher_background.xml", vector_background())
    write(res / "drawable/ic_launcher_foreground.xml", vector_foreground())
    if args.preview:
        icon(256).save(args.preview)
    print(f"Icono generado: {ico.relative_to(ROOT)} y recursos de Android en {res.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
