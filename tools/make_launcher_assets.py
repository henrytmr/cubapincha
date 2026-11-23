#!/usr/bin/env python3
"""Genera los recursos del icono de launcher v2 y del isotipo
transparente a partir de marca/ del paquete CubaPincha.

- Legacy: ic_launcher.png e ic_launcher_round.png en las 5 densidades.
- Adaptativo: foreground 432x432 (xxxhdpi) con el icono al 88% sobre el
  color de fondo muestreado del borde, para que el simbolo quede dentro
  de la zona segura de 66dp en mascaras circulares.
- Isotipo: recortado a su contenido visible (alfa > 12) y reducido a
  512px de ancho en drawable-nodpi (splash y bienvenida).
- Previews en /tmp/cp_launcher_preview/ para verificar la mascara.

Uso: python3 tools/make_launcher_assets.py [ruta_paquete]
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

PKG = sys.argv[1] if len(sys.argv) > 1 else "/tmp/cp_icons/CubaPincha_Iconos_Completos"
RES = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                    "..", "app", "src", "main", "res"))
PREVIEW = "/tmp/cp_launcher_preview"

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def edge_color(im):
    """Color medio del borde (1px) del icono: fondo del adaptativo."""
    w, h = im.size
    px = []
    for x in range(0, w, 16):
        px.append(im.getpixel((x, 0)))
        px.append(im.getpixel((x, h - 1)))
    for y in range(0, h, 16):
        px.append(im.getpixel((0, y)))
        px.append(im.getpixel((w - 1, y)))
    n = len(px)
    r = sum(p[0] for p in px) // n
    g = sum(p[1] for p in px) // n
    b = sum(p[2] for p in px) // n
    return (r, g, b)


def circle_crop(im):
    size = im.size[0]
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size, size), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(im.convert("RGBA"), (0, 0), mask)
    return out


def squircle_mask(size, radius_frac=0.28):
    """Aproximacion de mascara squircle (rectangulo redondeado)."""
    mask = Image.new("L", (size, size), 0)
    r = int(size * radius_frac)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size, size), radius=r, fill=255)
    return mask


def main():
    app = Image.open(os.path.join(PKG, "marca", "cubapincha_app_icon_v2.png")).convert("RGB")
    assert app.size == (2048, 2048), app.size
    bg = edge_color(app)
    print(f"color de fondo muestreado: #{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}")

    for dpi, size in DENSITIES.items():
        d = os.path.join(RES, f"mipmap-{dpi}")
        os.makedirs(d, exist_ok=True)
        icon = app.resize((size, size), Image.LANCZOS)
        icon.save(os.path.join(d, "ic_launcher.png"), optimize=True)
        circle_crop(icon).save(os.path.join(d, "ic_launcher_round.png"), optimize=True)
    print("legacy + round generados en 5 densidades")

    # Foreground adaptativo: icono al 88% centrado sobre el fondo.
    fg_size = 432
    fg = Image.new("RGB", (fg_size, fg_size), bg)
    inner = int(fg_size * 0.88)
    scaled = app.resize((inner, inner), Image.LANCZOS)
    off = (fg_size - inner) // 2
    fg.paste(scaled, (off, off))
    fg.save(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_foreground.png"), optimize=True)
    print("foreground adaptativo 432px (icono al 88%)")

    # Isotipo transparente: recorte al contenido visible + 512px.
    logo = Image.open(os.path.join(PKG, "marca",
                                   "cubapincha_logo_transparent_v2.png")).convert("RGBA")
    alpha = logo.split()[3]
    import numpy as np
    arr = np.array(alpha)
    ys, xs = np.where(arr > 12)
    pad = 24
    box = (max(0, xs.min() - pad), max(0, ys.min() - pad),
           min(logo.width, xs.max() + pad), min(logo.height, ys.max() + pad))
    trimmed = logo.crop(box)
    w512 = trimmed.resize((512, int(512 * trimmed.height / trimmed.width)), Image.LANCZOS)
    nodpi = os.path.join(RES, "drawable-nodpi")
    os.makedirs(nodpi, exist_ok=True)
    w512.save(os.path.join(nodpi, "cubapincha_logo_transparent.png"), optimize=True)
    print(f"isotipo recortado {trimmed.size} -> 512x{w512.height} en drawable-nodpi")

    # Previews: circulo y squircle del icono adaptativo final.
    os.makedirs(PREVIEW, exist_ok=True)
    prev = fg.resize((432, 432), Image.LANCZOS)
    circle_crop(prev).save(os.path.join(PREVIEW, "adaptive_circle.png"))
    sq = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    sq.paste(prev.convert("RGBA"), (0, 0), squircle_mask(432))
    sq.save(os.path.join(PREVIEW, "adaptive_squircle.png"))
    fg.save(os.path.join(PREVIEW, "adaptive_full.png"))
    w512.save(os.path.join(PREVIEW, "logo_trimmed.png"))
    print(f"previews en {PREVIEW}/")


if __name__ == "__main__":
    main()
