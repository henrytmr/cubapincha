#!/usr/bin/env python3
"""Genera el icono de launcher v4 de CubaPincha (el maletín con su C
grabada y el pin que lo encuentra) a partir de la geometría canónica
del prompt v4, sin depender de ningún paquete externo.

- Legacy: ic_launcher.png e ic_launcher_round.png en las 5 densidades,
  composición al 78% sobre fondo azul tinta.
- Adaptativo: foreground 432x432 transparente (composición al 66%,
  dentro de la zona segura de 66dp) + monocromo para iconos temáticos
  (Android 13+).
- Previews en /tmp/cp_launcher_v4/ para verificar las máscaras.
- Copia el icono 1024px a cubapincha-marca/ (cp_icon_v4_1024.png).

Uso: python3 tools/make_launcher_v4.py
"""
import math
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, "..", "app", "src", "main", "res"))
MARCA = os.path.normpath(os.path.join(HERE, "..", "..", "cubapincha-marca"))
PREVIEW = "/tmp/cp_launcher_v4"

INK = (23, 38, 60)
COPPER = (200, 117, 77)
CREAM = (247, 245, 240)
WHITE = (255, 255, 255)

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

# La composición se dibuja en el viewBox 96 a gran escala y se reduce
# con LANCZOS para que los bordes queden suaves.
S = 16
BOX = 96 * S


def case_transform(x, y):
    """La C y el check: escala 0.58 sobre (43,48), traslación (5,6)."""
    return (43 + (x - 43) * 0.58 + 5, 48 + (y - 48) * 0.58 + 6)


def draw_case(d, colors):
    """Maletín + C grabada + pin (sin check: el icono estático no lo
    lleva). colors = (marfil, cobre) o (blanco, blanco) en monocromo."""
    ivory, copper = colors
    # Cuerpo: rect redondeado 14–82 × 30–80, r6, trazo 4.
    d.rounded_rectangle([14 * S, 30 * S, 82 * S, 80 * S], radius=6 * S,
                        outline=ivory, width=4 * S)
    # Asa: arco de (38,30) a (58,30), r10, cima en y=20.
    d.arc([38 * S, 10 * S, 58 * S, 30 * S], start=180, end=360,
          fill=ivory, width=4 * S)
    # C grabada (cobre): la C canónica de la marca v3.
    cx, cy = case_transform(43, 48)
    pts = []
    for k in range(234):
        ang = math.radians(-58 - k)
        pts.append(((cx + 29 * 0.58 * math.cos(ang)) * S,
                    (cy + 29 * 0.58 * math.sin(ang)) * S))
    d.line(pts, fill=copper, width=int(11 * 0.58 * S), joint="curve")
    # Pin: lágrima trasladada (12,-10): punta (78,32), centro (78,16).
    d.ellipse([(78 - 10) * S, (16 - 10) * S, (78 + 10) * S, (16 + 10) * S],
              fill=copper)
    d.polygon([(78 * S, 32 * S), (70 * S, 22 * S), (86 * S, 22 * S)],
              fill=copper)
    # El punto del pin: en color es marfil; en monocromo se perfora
    # después sobre la máscara, así que aquí no se dibuja.
    if ivory != WHITE:
        d.ellipse([(78 - 4) * S, (16 - 4) * S, (78 + 4) * S, (16 + 4) * S],
                  fill=ivory)


def render_color(size):
    """Composición en color, centrada por su contenido, en un lienzo
    cuadrado transparente de `size` px (la composición ocupa el 100%
    del lienzo; el llamador la escala al porcentaje que quiera)."""
    big = Image.new("RGBA", (BOX, BOX), (0, 0, 0, 0))
    draw_case(ImageDraw.Draw(big), (CREAM, COPPER))
    bbox = big.split()[3].getbbox()
    content = big.crop(bbox)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(content.resize((size, size), Image.LANCZOS), (0, 0))
    return out


def render_mono(size):
    """Silueta blanca para iconos temáticos: todo blanco, con el punto
    del pin perforado (transparente) para que siga leyéndose."""
    big = Image.new("L", (BOX, BOX), 0)
    draw_case(ImageDraw.Draw(big), (255, 255))
    # Perfora el punto del pin.
    ImageDraw.Draw(big).ellipse(
        [(78 - 4) * S, (16 - 4) * S, (78 + 4) * S, (16 + 4) * S], fill=0)
    bbox = big.getbbox()
    content = big.crop(bbox).resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    out.putalpha(content)
    return out


def circle_crop(im):
    size = im.size[0]
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size, size), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(im.convert("RGBA"), (0, 0), mask)
    return out


def squircle_mask(size, radius_frac=0.28):
    mask = Image.new("L", (size, size), 0)
    r = int(size * radius_frac)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size, size), radius=r, fill=255)
    return mask


def main():
    os.makedirs(PREVIEW, exist_ok=True)

    # Legacy: composición al 78% sobre fondo tinta.
    for dpi, size in DENSITIES.items():
        d = os.path.join(RES, f"mipmap-{dpi}")
        os.makedirs(d, exist_ok=True)
        icon = Image.new("RGBA", (size, size), INK + (255,))
        inner = int(size * 0.78)
        comp = render_color(inner)
        off = (size - inner) // 2
        icon.alpha_composite(comp, (off, off))
        icon.convert("RGB").save(os.path.join(d, "ic_launcher.png"), optimize=True)
        circle_crop(icon).save(os.path.join(d, "ic_launcher_round.png"), optimize=True)
    print("legacy + round generados en 5 densidades (fondo tinta)")

    # Foreground adaptativo: composición al 66% sobre transparente.
    fg_size = 432
    fg = Image.new("RGBA", (fg_size, fg_size), (0, 0, 0, 0))
    inner = int(fg_size * 0.66)
    fg.alpha_composite(render_color(inner), ((fg_size - inner) // 2,) * 2)
    fg.save(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_foreground.png"),
            optimize=True)
    print("foreground adaptativo 432px (composición al 66%, transparente)")

    # Monocromo para iconos temáticos (Android 13+).
    mono = render_mono(fg_size)
    mono.save(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_monochrome.png"),
              optimize=True)
    print("monocromo 432px para iconos temáticos")

    # Icono 1024px para el paquete de marca.
    icon1024 = Image.new("RGBA", (1024, 1024), INK + (255,))
    inner = int(1024 * 0.78)
    icon1024.alpha_composite(render_color(inner), ((1024 - inner) // 2,) * 2)
    os.makedirs(MARCA, exist_ok=True)
    icon1024.save(os.path.join(MARCA, "cp_icon_v4_1024.png"), optimize=True)
    print("cp_icon_v4_1024.png en cubapincha-marca/")

    # Previews: el adaptativo final bajo máscaras de círculo y squircle.
    full = Image.new("RGBA", (432, 432), INK + (255,))
    full.alpha_composite(fg, (0, 0))
    circle_crop(full).save(os.path.join(PREVIEW, "adaptive_circle.png"))
    sq = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    sq.paste(full, (0, 0), squircle_mask(432))
    sq.save(os.path.join(PREVIEW, "adaptive_squircle.png"))
    full.save(os.path.join(PREVIEW, "adaptive_full.png"))
    print(f"previews en {PREVIEW}/")


if __name__ == "__main__":
    main()
