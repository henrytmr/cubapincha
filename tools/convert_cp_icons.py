#!/usr/bin/env python3
"""Convierte los SVG del paquete CubaPincha_Iconos_Completos a
VectorDrawable Android, conservando la geometria (viewBox 24) y el
nombre tecnico (ic_<nombre>, con guiones convertidos a guiones bajos).

Gramatica del paquete: trazo currentColor, grosor 2.2, extremos y
uniones redondeadas. En Android eso es strokeColor negro + tint del
tema (?attr/colorControlNormal), igual que los vectores que ya usa
la app: el color lo controla el ImageView o el tema, nunca el icono.

Uso: python3 tools/convert_cp_icons.py [ruta_paquete]
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

PKG = sys.argv[1] if len(sys.argv) > 1 else "/tmp/cp_icons/CubaPincha_Iconos_Completos"
DST = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "..", "app", "src", "main", "res", "drawable")
DST = os.path.normpath(DST)


def local(tag):
    return tag.split("}")[-1].split(":")[-1]


def fmt(n):
    if abs(n - round(n)) < 1e-9:
        return str(int(round(n)))
    s = f"{n:.2f}".rstrip("0").rstrip(".")
    return s if s not in ("-0", "") else "0"


def circle_to_path(cx, cy, r):
    return (f"M{fmt(cx - r)},{fmt(cy)} "
            f"a{fmt(r)},{fmt(r)} 0 1,0 {fmt(2 * r)},0 "
            f"a{fmt(r)},{fmt(r)} 0 1,0 {fmt(-2 * r)},0")


def ellipse_to_path(cx, cy, rx, ry):
    return (f"M{fmt(cx - rx)},{fmt(cy)} "
            f"a{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(2 * rx)},0 "
            f"a{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(-2 * rx)},0")


def rect_to_path(x, y, w, h, rx, ry):
    rx = min(rx or 0, w / 2)
    ry = min(ry if ry is not None else rx, h / 2)
    if rx == 0 and ry == 0:
        return f"M{fmt(x)},{fmt(y)} h{fmt(w)} v{fmt(h)} h{fmt(-w)} Z"
    return (f"M{fmt(x + rx)},{fmt(y)} "
            f"h{fmt(w - 2 * rx)} a{fmt(rx)},{fmt(ry)} 0 0 1 {fmt(rx)},{fmt(ry)} "
            f"v{fmt(h - 2 * ry)} a{fmt(rx)},{fmt(ry)} 0 0 1 {fmt(-rx)},{fmt(ry)} "
            f"h{fmt(-(w - 2 * rx))} a{fmt(rx)},{fmt(ry)} 0 0 1 {fmt(-rx)},{fmt(-ry)} "
            f"v{fmt(-(h - 2 * ry))} a{fmt(rx)},{fmt(ry)} 0 0 1 {fmt(rx)},{fmt(-ry)} Z")


def parse_svg(svg_path):
    """Devuelve [(pathData, filled)] en orden de documento."""
    tree = ET.parse(svg_path)
    paths = []
    for el in tree.getroot().iter():
        t = local(el.tag)
        if el.get("transform"):
            raise SystemExit(f"ERROR: transform no soportado en {svg_path}")
        cls = el.get("class", "")
        filled = "cp-fill" in cls
        if t == "path":
            d = re.sub(r"\s+", " ", el.get("d", "")).strip()
            if d:
                paths.append((d, filled))
        elif t == "circle":
            paths.append((circle_to_path(float(el.get("cx", 0)),
                                         float(el.get("cy", 0)),
                                         float(el.get("r", 0))), filled))
        elif t == "ellipse":
            paths.append((ellipse_to_path(float(el.get("cx", 0)),
                                          float(el.get("cy", 0)),
                                          float(el.get("rx", 0)),
                                          float(el.get("ry", 0))), filled))
        elif t == "rect":
            ry = el.get("ry")
            paths.append((rect_to_path(float(el.get("x", 0)),
                                       float(el.get("y", 0)),
                                       float(el.get("width", 0)),
                                       float(el.get("height", 0)),
                                       float(el.get("rx", 0) or 0),
                                       float(ry) if ry else None), filled))
        elif t == "line":
            x1, y1 = float(el.get("x1", 0)), float(el.get("y1", 0))
            x2, y2 = float(el.get("x2", 0)), float(el.get("y2", 0))
            paths.append((f"M{fmt(x1)},{fmt(y1)} L{fmt(x2)},{fmt(y2)}", filled))
        elif t == "polyline":
            nums = [float(p) for p in el.get("points", "").replace(",", " ").split()]
            d = "M" + " L".join(f"{fmt(nums[i])},{fmt(nums[i + 1])}"
                                for i in range(0, len(nums) - 1, 2))
            paths.append((d, filled))
    return paths


def path_xml(d, filled, name=None, extra=""):
    name_attr = f'\n        android:name="{name}"' if name else ""
    if filled:
        return (f'    <path{name_attr}\n'
                f'        android:fillColor="#FF000000"\n{extra}'
                f'        android:pathData="{d}" />')
    return (f'    <path{name_attr}\n'
            f'        android:fillColor="#00000000"\n{extra}'
            f'        android:pathData="{d}"\n'
            f'        android:strokeColor="#FF000000"\n'
            f'        android:strokeWidth="2.2"\n'
            f'        android:strokeLineCap="round"\n'
            f'        android:strokeLineJoin="round" />')


def vector_xml(paths, header_comment):
    body = "\n".join(path_xml(d, f) for d, f in paths)
    return (f'<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- {header_comment} -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="24dp"\n'
            f'    android:height="24dp"\n'
            f'    android:viewportWidth="24"\n'
            f'    android:viewportHeight="24"\n'
            f'    android:tint="?attr/colorControlNormal">\n'
            f'{body}\n'
            f'</vector>\n')


def write(name, content):
    with open(os.path.join(DST, name), "w", encoding="utf-8") as f:
        f.write(content)


def main():
    files = sorted(glob.glob(os.path.join(PKG, "iconos_actuales", "*.svg")) +
                   glob.glob(os.path.join(PKG, "iconos_futuros", "*.svg")))
    assert files, f"sin SVG en {PKG}"
    converted = {}
    for svg in files:
        base = os.path.splitext(os.path.basename(svg))[0]
        name = "ic_" + base.replace("-", "_")
        paths = parse_svg(svg)
        assert paths, f"sin trazados en {svg}"
        converted[base] = paths
        section = "actual" if "actuales" in svg else "futuro"
        write(name + ".xml",
              vector_xml(paths, f"Icono {base} del paquete CubaPincha ({section}). "
                                f"Trazo 2.2, extremos redondeados; color por tint."))
    print(f"convertidos: {len(files)} iconos")

    # Marcador relleno (estado guardado): misma geometria, con relleno.
    b = converted["bookmark"]
    assert len(b) == 1 and not b[0][1]
    d = b[0][0]
    write("ic_bookmark_filled.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n'
          '<!-- Marcador relleno (guardado): geometria del paquete, con relleno. -->\n'
          '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
          '    android:width="24dp"\n'
          '    android:height="24dp"\n'
          '    android:viewportWidth="24"\n'
          '    android:viewportHeight="24"\n'
          '    android:tint="?attr/colorControlNormal">\n'
          f'    <path\n'
          f'        android:fillColor="#FF000000"\n'
          f'        android:pathData="{d}"\n'
          f'        android:strokeColor="#FF000000"\n'
          f'        android:strokeWidth="2.2"\n'
          f'        android:strokeLineCap="round"\n'
          f'        android:strokeLineJoin="round" />\n'
          '</vector>\n')

    # Chispa de oportunidad (estrella de 4 puntas, splash): no viene en el
    # paquete como icono; se dibuja con la misma gramatica (relleno sun).
    write("ic_spark.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n'
          '<!-- Chispa de oportunidad del splash (estrella de 4 puntas). -->\n'
          '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
          '    android:width="24dp"\n'
          '    android:height="24dp"\n'
          '    android:viewportWidth="24"\n'
          '    android:viewportHeight="24"\n'
          '    android:tint="?attr/colorControlNormal">\n'
          '    <path\n'
          '        android:fillColor="#FF000000"\n'
          '        android:pathData="M12,2 L13.6,10.4 L22,12 L13.6,13.6 L12,22 L10.4,13.6 L2,12 L10.4,10.4 Z"\n'
          '        android:strokeColor="#FF000000"\n'
          '        android:strokeWidth="1"\n'
          '        android:strokeLineCap="round"\n'
          '        android:strokeLineJoin="round" />\n'
          '</vector>\n')

    # Bases animadas: misma geometria del paquete, con los grupos que
    # esperan los AnimatedVectorDrawable (nombres sin cambio).
    bell = converted["bell"]
    bell_xml = "\n".join(path_xml(d, f) for d, f in bell)
    write("ic_bell_anim.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n'
          '<!-- Base animada de avd_bell: geometria del paquete, grupo "bell". -->\n'
          '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
          '    android:width="24dp"\n'
          '    android:height="24dp"\n'
          '    android:viewportWidth="24"\n'
          '    android:viewportHeight="24"\n'
          '    android:tint="?attr/colorControlNormal">\n'
          '    <group\n'
          '        android:name="bell"\n'
          '        android:pivotX="12"\n'
          '        android:pivotY="3">\n'
          + bell_xml + '\n'
          '    </group>\n'
          '</vector>\n')

    write("ic_bookmark_anim.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n'
          '<!-- Base animada de avd_bookmark: contorno + relleno que aparece. -->\n'
          '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
          '    android:width="24dp"\n'
          '    android:height="24dp"\n'
          '    android:viewportWidth="24"\n'
          '    android:viewportHeight="24"\n'
          '    android:tint="?attr/colorControlNormal">\n'
          '    <group\n'
          '        android:name="pop"\n'
          '        android:pivotX="12"\n'
          '        android:pivotY="12">\n'
          + path_xml(d, False, name="outline") + '\n'
          + path_xml(d, True, name="fill",
                     extra='        android:fillAlpha="0"\n')
          + '\n'
          '    </group>\n'
          '</vector>\n')

    heart = converted["heart"]
    heart_xml = "\n".join(path_xml(dd, f) for dd, f in heart)
    write("ic_heart_anim.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n'
          '<!-- Base animada de avd_heart: geometria del paquete, grupo "pulse". -->\n'
          '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
          '    android:width="24dp"\n'
          '    android:height="24dp"\n'
          '    android:viewportWidth="24"\n'
          '    android:viewportHeight="24"\n'
          '    android:tint="?attr/colorControlNormal">\n'
          '    <group\n'
          '        android:name="pulse"\n'
          '        android:pivotX="12"\n'
          '        android:pivotY="12">\n'
          + heart_xml + '\n'
          '    </group>\n'
          '</vector>\n')

    cc = converted["check-circle"]
    assert len(cc) == 2, f"check-circle inesperado: {len(cc)} trazados"
    circle_d, check_d = cc[0][0], cc[1][0]
    write("ic_check_anim.xml",
          '<?xml version="1.0" encoding="utf-8"?>\n'
          '<!-- Base animada de avd_check: circulo + marca que se dibuja. -->\n'
          '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
          '    android:width="24dp"\n'
          '    android:height="24dp"\n'
          '    android:viewportWidth="24"\n'
          '    android:viewportHeight="24"\n'
          '    android:tint="?attr/colorControlNormal">\n'
          '    <group\n'
          '        android:name="pop"\n'
          '        android:pivotX="12"\n'
          '        android:pivotY="12">\n'
          + path_xml(circle_d, False, name="circle") + '\n'
          + path_xml(check_d, False, name="check",
                     extra='        android:trimPathEnd="0"\n')
          + '\n'
          '    </group>\n'
          '</vector>\n')
    print("bases animadas: bell, bookmark, heart, check + bookmark_filled + spark")


if __name__ == "__main__":
    main()
