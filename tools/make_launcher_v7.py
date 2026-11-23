#!/usr/bin/env python3
"""Generador del icono v7 de CubaPincha — el del HTML de Henry, tal cual.

El arco violeta del HTML (centro (48,48), r28.125, trazo 7.5, de la
terminal superior a la inferior por la izquierda) y la pieza verde
menta en su abertura (76.13,48) r4.875. Sin pin, sin check, sin nada
más: solo la paleta de la app sobre noche índigo.

RENDER LIMPIO: el arco se dibuja con una sola primitiva (draw.arc),
no con segmentos unidos — los segmentos eran lo que hacía que el icono
se viera rayado, como dibujado con crayola. Además todo se renderiza a
4x y se reduce con LANCZOS, así que los bordes quedan suaves y limpios.

Genera: legacy + round en las 5 densidades, foreground adaptativo
432x432 transparente (composición al 66%, zona segura), monocromo
para iconos temáticos (Android 13+) y el PNG 1024 del paquete de marca.
El fondo del icono es @color/jm_ink (res/values/colors.xml); la
variante nocturna usa @color/jm_dawn en mipmap-night-anydpi-v26.
"""
import os
from PIL import Image, ImageDraw

INK = (23, 21, 43)        # jm_ink #17152B
VIOLET = (123, 75, 226)   # jm_violet #7B4BE2
MINT = (92, 212, 176)     # jm_mint #5CD4B0

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res")
MARCA = os.path.join(os.path.dirname(ROOT), "cubapincha-marca")
OUT = "/tmp/cp_launcher_v7"
os.makedirs(OUT, exist_ok=True)

SS = 4  # Supersampling: se renderiza a 4x y se reduce con LANCZOS.


def draw_mark(d, size, scale, color_arc, color_piece):
    """El arco y la pieza, en un lienzo `size` con la caja 96 al `scale`.
    El arco es UNA primitiva (draw.arc), de 40° a 320° — el camino largo
    por la izquierda —, con terminales planos. La pieza es una elipse."""
    s = size * scale / 96.0
    off = (size - 96 * s) / 2
    cx, cy = off + 48 * s, off + 48 * s
    r = 28.125 * s
    d.arc([cx - r, cy - r, cx + r, cy + r], start=40, end=320,
          fill=color_arc, width=max(1, int(round(7.5 * s))))
    px, py = off + 76.125 * s, off + 48 * s
    pr = 4.875 * s
    d.ellipse([px - pr, py - pr, px + pr, py + pr], fill=color_piece)


def compose(size, scale, bg=None):
    """Composición en un lienzo cuadrado, renderizada a 4x y reducida."""
    big = size * SS
    img = Image.new("RGBA", (big, big), bg if bg else (0, 0, 0, 0))
    draw_mark(ImageDraw.Draw(img), big, scale, VIOLET + (255,), MINT + (255,))
    return img.resize((size, size), Image.LANCZOS)


def mono_compose(size, scale):
    big = size * SS
    img = Image.new("L", (big, big), 0)
    draw_mark(ImageDraw.Draw(img), big, scale, 255, 255)
    return img.resize((size, size), Image.LANCZOS)


DENS = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

for name, px in DENS.items():
    full = compose(px, 0.78, INK + (255,))
    full.convert("RGB").save(os.path.join(RES, f"mipmap-{name}/ic_launcher.png"))
    mask = Image.new("L", (px, px), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, px, px], fill=255)
    rnd = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    rnd.paste(full, (0, 0), mask)
    rnd.save(os.path.join(RES, f"mipmap-{name}/ic_launcher_round.png"))

fg = compose(432, 0.66)
fg.save(os.path.join(RES, "mipmap-xxxhdpi/ic_launcher_foreground.png"))
mono = mono_compose(432, 0.66)
mono.save(os.path.join(RES, "mipmap-xxxhdpi/ic_launcher_monochrome.png"))

icon1024 = compose(1024, 0.78, INK + (255,))
icon1024.convert("RGB").save(os.path.join(MARCA, "cp_icon_v7_1024.png"))

# Vista previa: cuadrado, círculo, squircle, monocromo.
prev = Image.new("RGB", (880, 240), (10, 8, 20))
sq = compose(200, 0.78, INK + (255,)).convert("RGB")
prev.paste(sq, (20, 20))
circ = compose(200, 0.78, INK + (255,))
mask = Image.new("L", (200, 200), 0)
ImageDraw.Draw(mask).ellipse([0, 0, 200, 200], fill=255)
c = Image.new("RGB", (200, 200), (10, 8, 20))
c.paste(circ.convert("RGB"), (0, 0), mask)
prev.paste(c, (240, 20))
sq2 = compose(200, 0.78, INK + (255,))
mask2 = Image.new("L", (200, 200), 0)
ImageDraw.Draw(mask2).rounded_rectangle([0, 0, 200, 200], radius=50, fill=255)
s = Image.new("RGB", (200, 200), (10, 8, 20))
s.paste(sq2.convert("RGB"), (0, 0), mask2)
prev.paste(s, (460, 20))
m = mono_compose(200, 0.66).convert("RGB")
prev.paste(m, (680, 20))
prev.save(os.path.join(OUT, "sheet.jpg"), quality=90)

# Ampliación del arco a 4x para comprobar que el trazo es limpio.
zoom = icon1024.crop((180, 180, 560, 560)).resize((760, 760), Image.NEAREST)
zoom.save(os.path.join(OUT, "zoom-arco.png"))
print("launcher v7 generado:", len(DENS) * 2 + 2, "PNG + cp_icon_v7_1024.png")
