#!/usr/bin/env python3
"""Generador del icono v8 de CubaPincha — el apretón de manos, definitivo.

El apretón trazado del diseño 1 de Henry (las mangas y la palma son
los contornos de su imagen; los dedos son 4 barras limpias dibujadas
a mano). Geometría canónica en cubapincha-marca/handshake.py.

El icono (el diseño 2 de Henry): el apretón sólido en azul tinta
sobre marfil. El fondo es @color/cpCream en los dos temas (la
variante nocturna se eliminó: el icono es el mismo de día y de noche).
Monocromo para iconos temáticos (Android 13+).

RENDER LIMPIO: los polígonos se dibujan como primitivas y todo se
renderiza a 4x y se reduce con LANCZOS (la lección de v7: nunca
segmentos unidos sin supersampling).

Genera: legacy + round en las 5 densidades, foreground adaptativo
432x432 transparente (composición al 66%, zona segura), monocromo,
el PNG 1024 del paquete de marca (sólido), el apretón en tres colores
transparente y la variante oscura de contorno (el diseño 3 de Henry,
recurso para después).
"""
import os
import sys
from PIL import Image, ImageDraw

NAVY = (23, 38, 60)       # cpInk #17263C
BROWN = (95, 86, 71)      # cpTaupe #5F5647
TAN = (170, 122, 73)      # cpTan #AA7A49
CREAM = (247, 245, 240)   # cpCream #F7F5F0
CORAL = (200, 117, 77)    # cpCoral #C8754D
SUN = (215, 168, 94)      # cpSun #D7A85E

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res")
MARCA = os.path.join(os.path.dirname(ROOT), "cubapincha-marca")
sys.path.insert(0, MARCA)
from handshake import MANGA_IZQ, PALMA, MANGA_DER, DEDOS, DEDO_ANCHO

OUT = "/tmp/cp_launcher_v8"
os.makedirs(OUT, exist_ok=True)

SS = 4  # Supersampling: se renderiza a 4x y se reduce con LANCZOS.


def draw_handshake(d, size, scale, colors, outline_w=0):
    """El apretón en un lienzo `size` con la caja 96 al `scale`.
    colors = (manga_izq+dedos, palma, manga_der). Si outline_w > 0,
    los polígonos se dibujan solo como contorno (el diseño 3)."""
    s = size * scale / 96.0
    off = (size - 96 * s) / 2
    navy, brown, tan = colors

    def pts(poly):
        return [(off + x * s, off + y * s) for x, y in poly]

    def poly(poly, color):
        p = pts(poly)
        if outline_w:
            w = max(1, int(round(outline_w * s)))
            d.line(p + [p[0]], fill=color, width=w, joint="curve")
            for x, y in p:
                r = w / 2
                d.ellipse([x - r, y - r, x + r, y + r], fill=color)
        else:
            d.polygon(p, fill=color)

    def finger(p1, p2, color):
        w = (outline_w if outline_w else DEDO_ANCHO) * s
        a = (off + p1[0] * s, off + p1[1] * s)
        b = (off + p2[0] * s, off + p2[1] * s)
        d.line([a, b], fill=color, width=max(1, int(round(w))), joint="curve")
        for x, y in (a, b):
            r = w / 2
            d.ellipse([x - r, y - r, x + r, y + r], fill=color)

    poly(PALMA, brown)
    poly(MANGA_DER, tan)
    poly(MANGA_IZQ, navy)
    for p1, p2 in DEDOS:
        finger(p1, p2, navy)


def compose(size, scale, colors, bg=None, outline_w=0):
    big = size * SS
    img = Image.new("RGBA", (big, big), bg if bg else (0, 0, 0, 0))
    draw_handshake(ImageDraw.Draw(img), big, scale,
                   tuple(c + (255,) for c in colors), outline_w)
    return img.resize((size, size), Image.LANCZOS)


def mono_compose(size, scale):
    big = size * SS
    img = Image.new("L", (big, big), 0)
    draw_handshake(ImageDraw.Draw(img), big, scale, (255, 255, 255))
    return img.resize((size, size), Image.LANCZOS)


SOLID = (NAVY, NAVY, NAVY)
COLOR = (NAVY, BROWN, TAN)

DENS = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

for name, px in DENS.items():
    full = compose(px, 0.78, SOLID, CREAM + (255,))
    full.convert("RGB").save(os.path.join(RES, f"mipmap-{name}/ic_launcher.png"))
    mask = Image.new("L", (px, px), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, px, px], fill=255)
    rnd = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    rnd.paste(full, (0, 0), mask)
    rnd.save(os.path.join(RES, f"mipmap-{name}/ic_launcher_round.png"))

fg = compose(432, 0.66, SOLID)
fg.save(os.path.join(RES, "mipmap-xxxhdpi/ic_launcher_foreground.png"))
mono = mono_compose(432, 0.66)
mono.save(os.path.join(RES, "mipmap-xxxhdpi/ic_launcher_monochrome.png"))

# Recursos de marca.
icon1024 = compose(1024, 0.78, SOLID, CREAM + (255,))
icon1024.convert("RGB").save(os.path.join(MARCA, "cp_icon_v8_1024.png"))
color1024 = compose(1024, 0.78, COLOR)
color1024.save(os.path.join(MARCA, "cp_handshake_1024.png"))
# El diseño 3 (contorno claro sobre tinta): contorno 4 en la caja 96.
dark1024 = compose(1024, 0.78, (CREAM, CREAM, CREAM), NAVY + (255,),
                   outline_w=4)
dark1024.convert("RGB").save(os.path.join(MARCA, "cp_handshake_outline_1024.png"))

# Vista previa: cuadrado, círculo, squircle, monocromo.
prev = Image.new("RGB", (880, 240), (30, 28, 40))
sq = compose(200, 0.78, SOLID, CREAM + (255,)).convert("RGB")
prev.paste(sq, (20, 20))
circ = compose(200, 0.78, SOLID, CREAM + (255,))
mask = Image.new("L", (200, 200), 0)
ImageDraw.Draw(mask).ellipse([0, 0, 200, 200], fill=255)
c = Image.new("RGB", (200, 200), (30, 28, 40))
c.paste(circ.convert("RGB"), (0, 0), mask)
prev.paste(c, (240, 20))
sq2 = compose(200, 0.78, SOLID, CREAM + (255,))
mask2 = Image.new("L", (200, 200), 0)
ImageDraw.Draw(mask2).rounded_rectangle([0, 0, 200, 200], radius=50, fill=255)
s = Image.new("RGB", (200, 200), (30, 28, 40))
s.paste(sq2.convert("RGB"), (0, 0), mask2)
prev.paste(s, (460, 20))
m = mono_compose(200, 0.66).convert("RGB")
prev.paste(m, (680, 20))
prev.save(os.path.join(OUT, "sheet.jpg"), quality=90)

# Ampliación de los dedos a 4x para comprobar que las barras son limpias.
zoom = icon1024.crop((480, 480, 860, 860)).resize((760, 760), Image.NEAREST)
zoom.save(os.path.join(OUT, "zoom-dedos.png"))
print("launcher v8 generado:", len(DENS) * 2 + 2,
      "PNG + cp_icon_v8_1024.png + cp_handshake_1024.png + cp_handshake_outline_1024.png")
