#!/usr/bin/env python3
"""Generador del icono v6 de CubaPincha — «La pieza que completa la ruta».

La C-ruta violeta sobre noche índigo, con la pieza coral
(la persona, en la abertura de la C) y el pin cobre (el destino, en
la terminal superior). Sin check: el sello se reserva para el splash.

Genera: legacy + round en las 5 densidades, foreground adaptativo
432x432 transparente (composición al 66%, zona segura), monocromo
para iconos temáticos (Android 13+) y el PNG 1024 del paquete de marca.
El fondo del icono es @color/jm_ink (res/values/colors.xml); la
variante nocturna usa @color/jm_dawn en mipmap-night-anydpi-v26.
"""
import math
import os
from PIL import Image, ImageDraw

INK = (23, 21, 43)        # jm_ink #17152B
VIOLET = (123, 75, 226)   # jm_violet #7B4BE2
CORAL = (242, 154, 131)   # jm_coral #F29A83
COPPER = (200, 117, 77)   # cpCoral #C8754D
IVORY = (247, 245, 240)   # cpCream #F7F5F0

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res")
MARCA = os.path.join(os.path.dirname(ROOT), "cubapincha-marca")
OUT = "/tmp/cp_launcher_v6"
os.makedirs(OUT, exist_ok=True)


def draw_c(draw, s, color, width=11):
    """La C canónica: centro (43,48), r29, de -58° a +35° por la izquierda."""
    pts = []
    for k in range(0, 268):
        a = math.radians(34.9 + k * 267.2 / 267.0)
        pts.append(((43 + 29 * math.cos(a)) * s, (48 + 29 * math.sin(a)) * s))
    draw.line(pts, fill=color, width=int(width * s), joint="curve")
    # Terminales planos: recortar las puntas redondeadas que PIL añade.
    for (x, y), ang in (((58.4, 23.4), -58), ((66.8, 64.6), 35)):
        # Pequeño rectángulo del color de fondo perpendicular al trazo.
        pass  # Los terminales quedan redondeados en el render; el vector real los tiene planos.


def draw_pin(draw, s):
    """El pin pequeño de la ruta: punta (58.4,23.6), cabeza r7 en (58.4,12.4)."""
    cx, cy = 58.4 * s, 12.4 * s
    draw.ellipse([cx - 7 * s, cy - 7 * s, cx + 7 * s, cy + 7 * s], fill=COPPER)
    draw.polygon([(52.8 * s, 16.6 * s), (64 * s, 16.6 * s), (58.4 * s, 23.6 * s)],
                 fill=COPPER)
    draw.ellipse([cx - 2.8 * s, cy - 2.8 * s, cx + 2.8 * s, cy + 2.8 * s], fill=IVORY)


def draw_piece(draw, s):
    """La pieza: coral r5 en la abertura de la C (76,48)."""
    cx, cy = 76 * s, 48 * s
    draw.ellipse([cx - 5 * s, cy - 5 * s, cx + 5 * s, cy + 5 * s], fill=CORAL)


def draw_mono(draw, s):
    """Silueta monocromo: la C + el pin + el punto, todo en blanco."""
    draw_c(draw, s, (255, 255))
    cx, cy = 58.4 * s, 12.4 * s
    draw.ellipse([cx - 7 * s, cy - 7 * s, cx + 7 * s, cy + 7 * s], fill=(255, 255))
    draw.polygon([(52.8 * s, 16.6 * s), (64 * s, 16.6 * s), (58.4 * s, 23.6 * s)],
                 fill=(255, 255))
    cx, cy = 76 * s, 48 * s
    draw.ellipse([cx - 5 * s, cy - 5 * s, cx + 5 * s, cy + 5 * s], fill=(255, 255))


def compose(size, scale, bg=None):
    """Composición en un lienzo cuadrado: la caja 96 centrada al `scale`."""
    img = Image.new("RGBA", (size, size), bg if bg else (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size * scale / 96.0
    off = (size - 96 * s) / 2
    d2 = ImageDraw.Draw(img)
    # Dibujar con desplazamiento: se usa una capa temporal.
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    # Redibujar las primitivas desplazadas: se hace con transformaciones
    # manuales (las funciones dibujan en coordenadas 96 escaladas por s).
    def T(x, y):
        return (off + x * s, off + y * s)
    # C
    pts = []
    for k in range(0, 268):
        a = math.radians(34.9 + k * 267.2 / 267.0)
        pts.append(T(43 + 29 * math.cos(a), 48 + 29 * math.sin(a)))
    ld.line(pts, fill=VIOLET + (255,), width=int(11 * s), joint="curve")
    # Pin
    cx, cy = T(58.4, 12.4)
    ld.ellipse([cx - 7 * s, cy - 7 * s, cx + 7 * s, cy + 7 * s], fill=COPPER + (255,))
    ld.polygon([T(52.8, 16.6), T(64, 16.6), T(58.4, 23.6)], fill=COPPER + (255,))
    ld.ellipse([cx - 2.8 * s, cy - 2.8 * s, cx + 2.8 * s, cy + 2.8 * s], fill=IVORY + (255,))
    # Pieza
    cx, cy = T(76, 48)
    ld.ellipse([cx - 5 * s, cy - 5 * s, cx + 5 * s, cy + 5 * s], fill=CORAL + (255,))
    img.alpha_composite(layer)
    return img


def mono_compose(size, scale):
    img = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(img)
    s = size * scale / 96.0
    off = (size - 96 * s) / 2

    def T(x, y):
        return (off + x * s, off + y * s)
    pts = []
    for k in range(0, 268):
        a = math.radians(34.9 + k * 267.2 / 267.0)
        pts.append(T(43 + 29 * math.cos(a), 48 + 29 * math.sin(a)))
    d.line(pts, fill=255, width=int(11 * s), joint="curve")
    cx, cy = T(58.4, 12.4)
    d.ellipse([cx - 7 * s, cy - 7 * s, cx + 7 * s, cy + 7 * s], fill=255)
    d.polygon([T(52.8, 16.6), T(64, 16.6), T(58.4, 23.6)], fill=255)
    cx, cy = T(76, 48)
    d.ellipse([cx - 5 * s, cy - 5 * s, cx + 5 * s, cy + 5 * s], fill=255)
    # Perforar el punto del pin (transparente) para que se lea en monocromo.
    cx, cy = T(58.4, 12.4)
    d.ellipse([cx - 2.8 * s, cy - 2.8 * s, cx + 2.8 * s, cy + 2.8 * s], fill=0)
    return img


DENS = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

for name, px in DENS.items():
    full = compose(px, 0.78, INK + (255,))
    full.convert("RGB").save(os.path.join(RES, f"mipmap-{name}/ic_launcher.png"))
    # Round: recorte circular.
    mask = Image.new("L", (px, px), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, px, px], fill=255)
    rnd = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    rnd.paste(full, (0, 0), mask)
    rnd.save(os.path.join(RES, f"mipmap-{name}/ic_launcher_round.png"))

fg = compose(432, 0.66)
fg.save(os.path.join(RES, "mipmap-xxxhdpi/ic_launcher_foreground.png"))
mono = mono_compose(432, 0.66)
mono.save(os.path.join(RES, "mipmap-xxxhdpi/ic_launcher_monochrome.png"))

# PNG 1024 del paquete de marca.
icon1024 = compose(1024, 0.78, INK + (255,))
icon1024.convert("RGB").save(os.path.join(MARCA, "cp_icon_v6_1024.png"))

# Vista previa: cuadrado, círculo, squircle, monocromo.
prev = Image.new("RGB", (880, 240), (10, 8, 20))
d = ImageDraw.Draw(prev)
sq = compose(200, 0.78, INK + (255,)).convert("RGB")
prev.paste(sq, (20, 20))
circ = compose(200, 0.78, INK + (255,))
mask = Image.new("L", (200, 200), 0)
ImageDraw.Draw(mask).ellipse([0, 0, 200, 200], fill=255)
c = Image.new("RGB", (200, 200), (10, 8, 20))
c.paste(circ.convert("RGB"), (0, 0), mask)
prev.paste(c, (240, 20))
# Squircle aproximado con rectángulo redondeado.
sq2 = compose(200, 0.78, INK + (255,))
mask2 = Image.new("L", (200, 200), 0)
ImageDraw.Draw(mask2).rounded_rectangle([0, 0, 200, 200], radius=50, fill=255)
s = Image.new("RGB", (200, 200), (10, 8, 20))
s.paste(sq2.convert("RGB"), (0, 0), mask2)
prev.paste(s, (460, 20))
m = mono_compose(200, 0.66).convert("RGB")
prev.paste(m, (680, 20))
prev.save(os.path.join(OUT, "sheet.jpg"), quality=90)
print("launcher v6 generado:", len(DENS) * 2 + 2, "PNG + cp_icon_v6_1024.png")
