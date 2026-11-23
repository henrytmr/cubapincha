#!/usr/bin/env python3
"""Verificación automática antes de cada entrega de CubaPincha.

Revisa las cosas que ya nos hicieron fallar en el teléfono y que el
compilador NO detecta (aapt compila sin quejarse, la app se cierra al
abrir la pantalla):
  1. Animadores: ningún <keyframe> fuera de <propertyValuesHolder>
     (causa "Unknown animator name: keyframe" al cargar un AVD).
  2. AVDs: cada android:animation existe y cada android:name (target)
     existe en el vector drawable de referencia.
  3. Drawables: la etiqueta raíz es válida (un <gradient> como raíz
     cierra la app al arrancar).
  4. Layouts: cada @drawable/@string/@color/@dimen/@layout/@menu
     referenciado existe.
  5. Java: cada R.id.* usado existe en algún layout.
  6. Manifest: permisos declarados y FileProvider presente.
  7. Marca CubaPincha: ningún "JobMatch" visible fuera del package.
  8. Catálogo Cuba: 16 provincias y 168 municipios exactos.
  9. Compartir: URL canónica cubapincha.cu y deep links en el manifest.
 10. Offline: tabla drafts en LocalDb y versión 2.8.0.
 11. Tokens: identidad corporativa azul tinta/cobre en colors.xml.
 12. Iconos: los 54 del paquete como ic_* + launcher v8 (el apretón de Henry, 5 densidades, adaptativo, monocromo, el mismo de día y de noche).
 13. Splash y bienvenida: ceremonia v8 (el apretón, 3,1 s) y §5.
 14. Iconos viejos: ningún nombre retirado se referencia en layouts/Java.
 15. Web PWA: tokens tinta/cobre, marca v3, manifest con PNG, sin emojis.
 16. Restauración de la barra inferior tras recrear la actividad.
 17. Insignia de verificado v2: disco sólido centrado, legible a 20dp.
 18. Sonido de introducción: chime con respeto a silencio y perfil.
 19. Espaciado de tarjetas: padding 18dp y ritmo 12dp.
 20. Push FCM: cliente manual (sin google-services) + envío real.
 21. Google Sign-In: flujo real con signInWithIdp, sin stub.
 22. Alertas de búsquedas guardadas: motor inmediato + digest diario.
 23. Coherencia contrato↔backend: node --check y versiones alineadas.
 24. Correcciones de la auditoría 2.2.0 (P0/P1 de datos, navegación y UI).
 25. Correcciones de la auditoría visual/lógica/seguridad v2.3.
 26. Marca v3: vectores con la geometría canónica, el apretón de Henry (trazado de su diseño, dos manos), sin restos de la marca de la auditoría, de la carpeta, del maletín, de la ruta, del punto ni de la marca del HTML retirados.
 27. Formularios: sin amarillo de autofill, sin pistas duplicadas y con aire.

Uso:  python3 verify_release.py
Salida: OK por cada comprobación, o FAIL con el archivo y el motivo.
Código de salida 0 = todo bien, 1 = hay fallos (NO entregar).
"""
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent
RES = ROOT / "app/src/main/res"
LAYOUT = RES / "layout"
JAVA = ROOT / "app/src/main/java"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"

VALID_DRAWABLE_ROOTS = {
    "shape", "vector", "animated-vector", "selector", "ripple",
    "layer-list", "bitmap", "nine-patch", "inset", "scale", "clip",
    "rotate", "level-list", "transition", "animation-list",
    "adaptive-icon",
}

failures = []
checks = 0


def ok(msg):
    global checks
    checks += 1
    print(f"  OK  {msg}")


def fail(msg):
    global checks
    checks += 1
    failures.append(msg)
    print(f"  FAIL {msg}")


def parse(path):
    try:
        return ET.parse(path).getroot()
    except ET.ParseError as e:
        fail(f"{path.relative_to(ROOT)}: XML inválido ({e})")
        return None


def check_animators():
    """Ningún <keyframe> fuera de <propertyValuesHolder>."""
    print("1. Animadores (keyframe solo dentro de propertyValuesHolder)")
    for f in sorted((RES / "animator").glob("*.xml")):
        root = parse(f)
        if root is None:
            continue
        bad = []
        for child in root:
            if child.tag == "keyframe":
                bad.append(child.tag)
            elif child.tag == "set":
                for g in child:
                    if g.tag == "keyframe":
                        bad.append("set/keyframe")
        if bad:
            fail(f"{f.name}: <keyframe> fuera de propertyValuesHolder ({', '.join(bad)})")
        else:
            ok(f"{f.name}")
        # Cada keyframe debe tener fraction y value.
        for kf in root.iter("keyframe"):
            attrs = kf.attrib
            if not any(k.endswith("fraction") for k in attrs) or \
               not any(k.endswith("value") for k in attrs):
                fail(f"{f.name}: keyframe sin fraction o value")


def check_avds():
    """AVD: animaciones y targets existen."""
    print("2. AnimatedVectorDrawable (animaciones y targets)")
    animator_names = {p.stem for p in (RES / "animator").glob("*.xml")}
    for f in sorted((RES / "drawable").glob("avd_*.xml")):
        root = parse(f)
        if root is None:
            continue
        drawable = root.attrib.get("{http://schemas.android.com/apk/res/android}drawable", "")
        vector_name = drawable.split("/")[-1] if drawable else ""
        vector = RES / "drawable" / f"{vector_name}.xml"
        names = set()
        if vector.exists():
            vroot = parse(vector)
            if vroot is not None:
                for el in vroot.iter():
                    n = el.attrib.get("{http://schemas.android.com/apk/res/android}name")
                    if n:
                        names.add(n)
        else:
            fail(f"{f.name}: vector {vector_name} no existe")
        good = True
        for t in root.iter("target"):
            a = t.attrib.get("{http://schemas.android.com/apk/res/android}animation", "")
            name = t.attrib.get("{http://schemas.android.com/apk/res/android}name", "")
            anim = a.split("/")[-1] if a else ""
            if anim and anim not in animator_names:
                fail(f"{f.name}: animador @{anim} no existe")
                good = False
            if name and name not in names:
                fail(f"{f.name}: target '{name}' no existe en {vector_name}")
                good = False
        if good:
            ok(f"{f.name}")


def check_drawable_roots():
    """La etiqueta raíz de cada drawable es válida."""
    print("3. Drawables (raíz válida)")
    bad = 0
    for f in sorted((RES / "drawable").glob("*.xml")):
        root = parse(f)
        if root is None:
            bad += 1
            continue
        if root.tag not in VALID_DRAWABLE_ROOTS:
            fail(f"{f.name}: raíz <{root.tag}> inválida como drawable")
            bad += 1
    if bad == 0:
        ok(f"{len(list((RES / 'drawable').glob('*.xml')))} drawables con raíz válida")


def collect_defined():
    defined = {"drawable": set(), "string": set(), "color": set(),
               "dimen": set(), "layout": set(), "menu": set(),
               "animator": set(), "xml": set(), "mipmap": set(), "id": set()}
    for kind in ("drawable", "layout", "menu", "animator", "xml"):
        for p in (RES / kind).glob("*.xml"):
            defined[kind].add(p.stem)
    # PNGs en drawable-nodpi (isotipo) también son @drawable/*.
    for d in RES.glob("drawable-*"):
        for p in d.iterdir():
            if p.is_file():
                defined["drawable"].add(p.stem)
    # Listas de colores (@color/...) también viven como archivos en res/color/.
    for p in (RES / "color").glob("*.xml"):
        defined["color"].add(p.stem)
    for d in RES.glob("mipmap-*"):
        for p in d.iterdir():
            if p.is_file():
                defined["mipmap"].add(p.stem)
    for p in (RES / "values").glob("*.xml"):
        root = parse(p)
        if root is None:
            continue
        for el in root:
            name = el.attrib.get("name")
            if not name:
                continue
            if el.tag == "string":
                defined["string"].add(name)
            elif el.tag == "color":
                defined["color"].add(name)
            elif el.tag == "dimen":
                defined["dimen"].add(name)
            elif el.tag == "item" and el.attrib.get("type") == "id":
                defined["id"].add(name)
    for p in (RES / "layout").glob("*.xml"):
        text = p.read_text(encoding="utf-8", errors="ignore")
        for m in re.finditer(r'android:id="@\+id/([A-Za-z0-9_]+)"', text):
            defined["id"].add(m.group(1))
    # Los ítems de menú (@+id/nav_*) también son R.id.* válidos.
    for p in (RES / "menu").glob("*.xml"):
        text = p.read_text(encoding="utf-8", errors="ignore")
        for m in re.finditer(r'android:id="@\+id/([A-Za-z0-9_]+)"', text):
            defined["id"].add(m.group(1))
    return defined


def check_layout_refs(defined):
    """Cada @tipo/nombre en layouts existe."""
    print("4. Referencias en layouts (@drawable, @string, @color, ...)")
    ref_re = re.compile(r"@([a-z]+)/([A-Za-z0-9_]+)")
    skip_types = {"android", "style", "attr"}
    bad = 0
    for f in sorted((RES / "layout").glob("*.xml")):
        text = f.read_text(encoding="utf-8", errors="ignore")
        for kind, name in set(ref_re.findall(text)):
            if kind in skip_types or kind == "id":
                continue
            if kind in defined and name not in defined[kind]:
                fail(f"{f.name}: @{kind}/{name} no existe")
                bad += 1
    if bad == 0:
        ok("todas las referencias de layout resuelven")


def check_java_ids(defined):
    """Cada R.id.* usado en Java existe en algún layout."""
    print("5. IDs usados en Java (R.id.*)")
    bad = 0
    for f in sorted(JAVA.rglob("*.java")):
        text = f.read_text(encoding="utf-8", errors="ignore")
        for name in set(re.findall(r"R\.id\.([A-Za-z0-9_]+)", text)):
            if name not in defined["id"]:
                fail(f"{f.name}: R.id.{name} no existe en ningún layout")
                bad += 1
    if bad == 0:
        ok("todos los R.id.* existen en layouts")


def check_manifest():
    """Permisos y FileProvider declarados."""
    print("6. Manifest (permisos y FileProvider)")
    root = parse(MANIFEST)
    if root is None:
        return
    ns = "{http://schemas.android.com/apk/res/android}"
    perms = {el.attrib.get(f"{ns}name", "")
             for el in root.iter("uses-permission")}
    expected = {
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.VIBRATE",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.CAMERA",
    }
    missing = expected - perms
    if missing:
        fail(f"permisos ausentes: {', '.join(sorted(missing))}")
    else:
        ok(f"{len(expected)} permisos declarados")
    providers = [el for el in root.iter("provider")]
    if not any("FileProvider" in (p.attrib.get(f"{ns}name", "")) for p in providers):
        fail("FileProvider no declarado (la cámara no puede guardar la foto)")
    else:
        ok("FileProvider declarado")
    if not (RES / "xml/file_paths.xml").exists():
        fail("res/xml/file_paths.xml no existe")
    else:
        ok("res/xml/file_paths.xml existe")


def check_branding():
    """Ningún 'JobMatch' visible fuera del nombre de paquete."""
    print("7. Marca CubaPincha (sin 'JobMatch' visible)")
    bad = 0
    for f in sorted(RES.rglob("*.xml")):
        text = f.read_text(encoding="utf-8", errors="ignore")
        if "JobMatch" in text or "jobmatch" in text.lower().replace("com.cubapincha.job", ""):
            if "JobMatch" in text:
                fail(f"{f.relative_to(ROOT)}: contiene 'JobMatch'")
                bad += 1
    for f in sorted(JAVA.rglob("*.java")):
        text = f.read_text(encoding="utf-8", errors="ignore")
        clean = text.replace("com.cubapincha.job", "").replace("cubapincha.db", "").replace("cubapincha_session", "")
        if "JobMatch" in clean:
            fail(f"{f.relative_to(ROOT)}: contiene 'JobMatch' fuera del package")
            bad += 1
    strings = (RES / "values/strings.xml").read_text(encoding="utf-8", errors="ignore")
    if "CubaPincha" not in strings:
        fail("strings.xml no menciona CubaPincha")
        bad += 1
    if bad == 0:
        ok("sin 'JobMatch' visible; marca CubaPincha presente")


def check_catalog():
    """Catálogo Cuba: 16 provincias y 168 municipios exactos."""
    print("8. Catálogo Cuba (16 provincias, 168 municipios)")
    catalog = JAVA / "com/cubapincha/job/data/CubaCatalog.java"
    if not catalog.exists():
        fail("CubaCatalog.java no existe")
        return
    text = catalog.read_text(encoding="utf-8", errors="ignore")
    adds = re.findall(r'add\("([A-Z]+)",\s*"([^"]+)",\s*(.*?)\);', text, re.S)
    provinces = len(adds)
    municipalities = sum(len(re.findall(r'"([^"]+)"', rest)) for _, _, rest in adds)
    if provinces != 16:
        fail(f"provincias: {provinces} (esperadas 16)")
    else:
        ok("16 provincias")
    if municipalities != 168:
        fail(f"municipios: {municipalities} (esperados 168)")
    else:
        ok("168 municipios")


def check_sharing():
    """URL canónica y deep links para enlaces compartidos."""
    print("9. Compartir (URL canónica y deep links)")
    share = JAVA / "com/cubapincha/job/util/ShareUtils.java"
    if not share.exists():
        fail("ShareUtils.java no existe")
    else:
        text = share.read_text(encoding="utf-8", errors="ignore")
        if "https://cubapincha.cu" not in text:
            fail("ShareUtils no usa https://cubapincha.cu")
        elif "/ofertas/" not in text:
            fail("ShareUtils no construye /ofertas/{id}")
        elif "cubapincha://oferta/" not in text:
            fail("ShareUtils no construye el deep link cubapincha://oferta/")
        else:
            ok("URL canónica https://cubapincha.cu/ofertas/{id} + deep link")
    manifest = MANIFEST.read_text(encoding="utf-8", errors="ignore")
    if 'android:scheme="cubapincha"' not in manifest:
        fail("manifest: falta el deep link cubapincha://")
    elif 'android:host="cubapincha.cu"' not in manifest:
        fail("manifest: falta el App Link cubapincha.cu/ofertas/")
    else:
        ok("manifest: deep links cubapincha:// y https://cubapincha.cu/ofertas/")
    if not (RES / "drawable/ic_cp_route.xml").exists():
        fail("ic_cp_route.xml no existe (símbolo del estado vacío de Inicio)")
    else:
        ok("símbolo de ruta en el estado vacío de Inicio")


def check_offline_and_version():
    """Tabla drafts en LocalDb y versión 2.9.2."""
    print("10. Offline (tabla drafts) y versión")
    db = JAVA / "com/cubapincha/job/data/LocalDb.java"
    if not db.exists():
        fail("LocalDb.java no existe")
    else:
        text = db.read_text(encoding="utf-8", errors="ignore")
        if "CREATE TABLE IF NOT EXISTS drafts" not in text and "drafts" not in text:
            fail("LocalDb no define la tabla drafts")
        elif "DB_VERSION = 4" not in text and "DB_VERSION=4" not in text:
            fail("LocalDb no está en DB_VERSION 4 (migración de cifrado)")
        else:
            ok("tabla drafts + DB_VERSION 4")
    gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8", errors="ignore")
    if "versionCode 24" not in gradle:
        fail("build.gradle: versionCode no es 24")
    elif 'versionName "2.9.2"' not in gradle:
        fail('build.gradle: versionName no es "2.9.2"')
    elif 'applicationId "com.cubapincha.job"' not in gradle:
        fail('build.gradle: applicationId no es "com.cubapincha.job"')
    elif "namespace 'com.cubapincha.job'" not in gradle:
        fail("build.gradle: namespace no es 'com.cubapincha.job'")
    else:
        ok("versión 2.9.2 (versionCode 24) · paquete com.cubapincha.job")
    backend = ROOT.parent / "jobmatch-backend" / "Code.gs"
    if backend.exists():
        code = backend.read_text(encoding="utf-8", errors="ignore")
        if "API_VERSION = '1.4.1'" not in code:
            fail("backend Code.gs: API_VERSION no es 1.4.1")
        else:
            ok("backend Code.gs 1.4.1")
        seeds = re.findall(r"\{ code: '([A-Z]+)', name: '([^']+)', municipalities: \[(.*?)\] \}", code, re.S)
        mun = sum(len(re.findall(r"'([^']+)'", rest)) for _, _, rest in seeds)
        if len(seeds) != 16 or mun != 168:
            fail(f"backend: catálogo semilla {len(seeds)} provincias / {mun} municipios (esperado 16/168)")
        else:
            ok("backend: catálogo semilla 16 provincias / 168 municipios")
    else:
        ok("backend Code.gs no está en el workspace (se omite su comprobación)")


def check_tokens():
    """Tokens de la identidad corporativa azul tinta/cobre."""
    print("11. Tokens corporativos CubaPincha")
    colors = (RES / "values/colors.xml").read_text(encoding="utf-8", errors="ignore")
    expected = {
        "cpInk": "#17263C", "cpNight": "#1C2C3E", "cpIndigo": "#203F5A",
        "cpViolet": "#2F5872", "cpCoral": "#C8754D", "cpMint": "#63A38B",
        "cpSun": "#D7A85E", "cpCream": "#F7F5F0", "cpWhite": "#FFFFFF",
        "cpOutline": "#DDE3E5",
    }
    bad = 0
    for name, value in expected.items():
        if f'<color name="{name}">{value}</color>' not in colors:
            fail(f"colors.xml: {name} no es {value}")
            bad += 1
    if bad == 0:
        ok("10 tokens azul tinta/cobre y superficies cálidas")


def check_icons_and_launcher():
    """Los 54 iconos del paquete + launcher v8 (el apretón)."""
    print("12. Iconos del paquete y launcher v8")
    pkg = ROOT.parent / "cubapincha-iconos"
    svgs = sorted(pkg.glob("iconos_actuales/*.svg")) + sorted(pkg.glob("iconos_futuros/*.svg"))
    if not svgs:
        fail("paquete cubapincha-iconos no encontrado en el workspace")
        return
    missing = []
    for svg in svgs:
        name = "ic_" + svg.stem.replace("-", "_") + ".xml"
        if not (RES / "drawable" / name).exists():
            missing.append(name)
    if missing:
        fail(f"iconos ausentes: {', '.join(missing[:5])}{'...' if len(missing) > 5 else ''}")
    else:
        ok(f"{len(svgs)} iconos del paquete como vectores Android")
    missing_extra = [e for e in ("ic_bookmark_filled.xml", "ic_bell_anim.xml",
                               "ic_bookmark_anim.xml", "ic_heart_anim.xml", "ic_check_anim.xml")
                     if not (RES / "drawable" / e).exists()]
    for e in missing_extra:
        fail(f"res/drawable/{e} no existe")
    if not missing_extra:
        ok("variantes animadas + relleno presentes")
    # Launcher v4: PNG en 5 densidades + foreground adaptativo +
    # monocromo para iconos temáticos + variante oscura.
    bad = 0
    for dpi in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            if not (RES / f"mipmap-{dpi}" / name).exists():
                fail(f"mipmap-{dpi}/{name} no existe")
                bad += 1
    if not (RES / "mipmap-xxxhdpi/ic_launcher_foreground.png").exists():
        fail("mipmap-xxxhdpi/ic_launcher_foreground.png no existe")
        bad += 1
    if not (RES / "mipmap-xxxhdpi/ic_launcher_monochrome.png").exists():
        fail("mipmap-xxxhdpi/ic_launcher_monochrome.png no existe (iconos temáticos)")
        bad += 1
    for f in ("ic_launcher.xml", "ic_launcher_round.xml"):
        text = (RES / "mipmap-anydpi-v26" / f).read_text(encoding="utf-8", errors="ignore")
        if "@mipmap/ic_launcher_foreground" not in text:
            fail(f"mipmap-anydpi-v26/{f}: no usa el foreground v8")
            bad += 1
        if "@mipmap/ic_launcher_monochrome" not in text:
            fail(f"mipmap-anydpi-v26/{f}: sin variante monocromo")
            bad += 1
    # El icono v8 es el mismo de día y de noche (marfil + apretón
    # azul tinta): no hay variante nocturna.
    if (RES / "mipmap-night-anydpi-v26").exists():
        fail("mipmap-night-anydpi-v26 sigue existiendo (el icono v8 es el mismo de día y de noche)")
        bad += 1
    colors = (RES / "values/colors.xml").read_text(encoding="utf-8", errors="ignore")
    if '<color name="ic_launcher_background">@color/cpCream</color>' not in colors:
        fail("ic_launcher_background no es el marfil del diseño 2 de Henry")
        bad += 1
    if not (ROOT / "tools/make_launcher_v8.py").exists():
        fail("tools/make_launcher_v8.py no existe (el generador del icono v8)")
        bad += 1
    if bad == 0:
        ok("launcher v8: 5 densidades + adaptativo + monocromo (el mismo de día y de noche)")
    # Pop del marcador según §9: 0.94 → 1.04 → 1.0.
    pop_bad = 0
    for axis in ("x", "y"):
        text = (RES / f"animator/pop_scale_{axis}.xml").read_text(encoding="utf-8", errors="ignore")
        if '"0.94"' not in text or '"1.04"' not in text:
            fail(f"pop_scale_{axis}.xml: no sigue 0.94 → 1.04 → 1.0")
            pop_bad += 1
    if pop_bad == 0:
        ok("pop del marcador 0.94 → 1.04 → 1.0")


def check_splash_and_welcome():
    """Splash corporativo con movimiento sutil y bienvenida §5."""
    print("13. Splash y selección de rol")
    before = len(failures)
    splash = (RES / "layout/fragment_splash.xml").read_text(encoding="utf-8", errors="ignore")
    for needle, what in (
            ("@+id/mark_stage", "escenario fijo de la marca"),
            ("@color/jm_ink", "fondo plano noche índigo del splash"),
            ("ic_cp_hand_left_light", "mano izquierda clara en el splash"),
            ("ic_cp_hand_right_light", "mano derecha clara en el splash"),
            ("@+id/hand_left", "capa de la mano izquierda en el splash"),
            ("@+id/hand_right", "capa de la mano derecha en el splash"),
            ("@+id/name_mask", "máscara del nombre en el splash"),
            ("@+id/app_name", "nombre de marca"),
            ("@string/tagline", "lema de la app en el splash"),
            ('android:textStyle="italic"', "frase en cursiva, como en el HTML")):
        if needle not in splash:
            fail(f"fragment_splash.xml: falta {what}")
    # El splash v8 es el apretón: sin arco, sin pieza, sin amanecer,
    # sin halo, sin check, sin regla, sin pin ni punto añadidos.
    for needle, what in (
            ("avd_cp_arc_draw", "AVD del arco"),
            ("ic_cp_arc", "arco"),
            ("ic_cp_piece", "pieza"),
            ("logo_piece", "capa de la pieza"),
            ("dawn_overlay", "amanecer"),
            ("bg_glow_violet", "halo"),
            ("logo_check", "check"),
            ("name_rule", "regla"),
            ("logo_pin", "pin"),
            ("logo_dot", "punto que viaja"),
            ("bg_splash_route", "fondo con degradado")):
        if needle in splash:
            fail(f"fragment_splash.xml: el splash v8 no lleva {what}")
    hand_left = (RES / "drawable/ic_cp_hand_left_light.xml").read_text(
        encoding="utf-8", errors="ignore")
    hand_right = (RES / "drawable/ic_cp_hand_right_light.xml").read_text(
        encoding="utf-8", errors="ignore")
    if ("M 27.0,71.4" not in hand_left or "@color/cpCream" not in hand_left
            or 'android:strokeWidth="7.5"' not in hand_left
            or "M 62,52 L 76,66" not in hand_left):
        fail("ic_cp_hand_left_light.xml: la mano izquierda clara debe llevar la manga y los 4 dedos en marfil")
    elif ("M 40.1,53.9" not in hand_right or "@color/cpCoral" not in hand_right
            or "M 77.9,56.9" not in hand_right or "@color/cpSun" not in hand_right):
        fail("ic_cp_hand_right_light.xml: la mano derecha clara debe llevar la palma en cobre y la manga en dorado")
    else:
        ok("las dos manos claras del splash (manga + dedos, palma + manga)")
    code = (JAVA / "com/cubapincha/job/ui/SplashFragment.java").read_text(encoding="utf-8", errors="ignore")
    if "NAVIGATE_MS = 3100L" not in code:
        fail("SplashFragment: la ceremonia debe durar 3,1 s")
    elif "EXIT_MS = 300L" not in code:
        fail("SplashFragment: falta la salida (zoom + fundido, 300 ms)")
    elif "NAVIGATE_REDUCED_MS = 420L" not in code:
        fail("SplashFragment: la navegación con movimiento reducido debe ser breve (420 ms)")
    elif ("AnimUtils.reduceMotion" not in code
          or "HANDS_START_MS = 200L" not in code
          or "SHAKE_START_MS = 800L" not in code
          or "SHAKE_DURATION_MS = 800L" not in code
          or "SHAKE_AMPLITUDE_DP = 6f" not in code
          or "NAME_START_MS = 1600L" not in code
          or "TAGLINE_START_MS = 2200L" not in code
          or "CHIME_DELAY_MS = 2240L" not in code
          or "TAGLINE_ALPHA = 0.7f" not in code
          or "hand_left" not in code
          or "hand_right" not in code
          or "Keyframe" not in code
          or '"shake"' not in code
          or "0.1875f" not in code
          or "PathInterpolator" not in code
          or "0.2f, 0.7f, 0.1f, 1f" not in code
          or "1.05f" not in code
          or "ObjectAnimator.ofFloat(handLeft" not in code
          or "ObjectAnimator.ofFloat(handRight" not in code
          or "ForegroundColorSpan" not in code
          or "scaleX(1.05f)" not in code):
        fail("SplashFragment: falta la ceremonia v8 (manos que se encuentran, estrechón de dos apretones, nombre que se eleva desde la máscara, frase en cursiva hasta 0.7, salida con zoom y campana sincronizada) o el respeto por movimiento reducido")
    elif ("logo_check" in code or "logo_pin" in code or "logo_dot" in code
            or "logo_piece" in code or "startAvd" in code):
        fail("SplashFragment: el splash v8 no lleva check, pin, punto, pieza ni AVD")
    else:
        ok("splash: ceremonia v8 (el apretón), 3,1 s (420 ms reducido)")
    welcome = (RES / "layout/fragment_welcome.xml").read_text(encoding="utf-8", errors="ignore")
    for needle, what in (
            ("ic_cp_handshake", "apretón en bienvenida"),
            ("bg_handshake_plate", "placa del apretón en bienvenida"),
            ("card_worker", "tarjeta de trabajador"),
            ("card_company", "tarjeta de empresa"),
            ("route_connector", "ruta entre tarjetas"),
            ("Widget.CubaPincha.Button.Secondary", "inicio de sesión secundario")):
        if needle not in welcome:
            fail(f"fragment_welcome.xml: falta {what}")
    strings = (RES / "values/strings.xml").read_text(encoding="utf-8", errors="ignore")
    for needle, what in (
            ("El trabajo que buscas, cerca de ti.", "título de bienvenida"),
            ("Encuentra oportunidades. Encuentra talento.", "subtítulo de bienvenida"),
            ("Crea tu perfil y encuentra oportunidades que conecten con tu talento.", "texto de trabajador"),
            ("Publica tus ofertas y encuentra personas para hacer crecer tu negocio.", "texto de empresa")):
        if needle not in strings:
            fail(f"strings.xml: falta {what}")
    if len(failures) == before:
        ok("bienvenida §5: isotipo, nombre, título, subtítulo, 2 tarjetas, ruta, login, cómo funciona")


def check_no_old_icons():
    """Ningún icono retirado se referencia en layouts, menús o Java."""
    print("14. Iconos retirados (sin referencias)")
    retired = {"ic_back", "ic_arrow_right", "ic_warning", "ic_profile", "ic_people",
               "ic_add", "ic_call", "ic_location", "ic_dashboard", "ic_close",
               "ic_dark_mode", "ic_eye", "ic_eye_off", "ic_star", "ic_wifi_off"}
    bad = 0
    for f in sorted(list((RES / "layout").glob("*.xml")) + list((RES / "menu").glob("*.xml"))
                    + list(JAVA.rglob("*.java"))):
        text = f.read_text(encoding="utf-8", errors="ignore")
        for name in retired:
            if re.search(r"\b" + re.escape(name) + r"\b", text):
                fail(f"{f.relative_to(ROOT)}: referencia a {name} (retirado)")
                bad += 1
    if bad == 0:
        ok("sin referencias a los 15 iconos retirados")


def check_web():
    """Web PWA: tokens, marca v3, manifest, sin emojis."""
    print("15. Web PWA")
    web = ROOT.parent / "cubapincha-web"
    if not web.exists():
        ok("cubapincha-web no está en el workspace (se omite)")
        return
    tokens = (web / "css/tokens.css").read_text(encoding="utf-8", errors="ignore")
    if "--night: #1C2C3E" not in tokens or "--coral: #C8754D" not in tokens:
        fail("web tokens.css: faltan los tokens azul tinta/cobre nuevos")
    else:
        ok("web: tokens azul tinta/cobre")
    for asset in ("assets/logo-transparent.png", "assets/app-icon-192.png",
                  "assets/app-icon-512.png", "favicon.png"):
        if not (web / asset).exists():
            fail(f"web: falta {asset}")
    import json
    manifest = json.loads((web / "manifest.json").read_text(encoding="utf-8"))
    icons = [i.get("src", "") for i in manifest.get("icons", [])]
    if not any("app-icon-512.png" in s for s in icons):
        fail("web manifest.json: sin icono v2 de 512px")
    else:
        ok("web: marca v3 + iconos en el manifest")
    sw = (web / "sw.js").read_text(encoding="utf-8", errors="ignore")
    if "cubapincha-v3" not in sw:
        fail("web sw.js: falta invalidación de caché de la identidad v3")
    else:
        ok("web: service worker en caché v3")
    emoji = ["🌙", "☀", "📍", "⚠", "✓"]
    bad = 0
    for f in sorted(list(web.glob("*.html")) + list((web / "js").glob("*.js"))):
        text = f.read_text(encoding="utf-8", errors="ignore")
        for e in emoji:
            if e in text:
                fail(f"web {f.name}: contiene '{e}' (usar iconos del paquete)")
                bad += 1
    if bad == 0:
        ok("web: sin emojis ni símbolos sueltos en HTML/JS")


def check_nav_restore():
    """Bug 2.1.1: la barra inferior debe restaurarse tras recrear la
    actividad (cambio de tema), con el menú del rol y la pestaña marcada."""
    print("16. Restauración del menú inferior tras recreación")
    main = (JAVA / "com/cubapincha/job/ui/MainActivity.java").read_text(
        encoding="utf-8", errors="ignore")
    for needle, label in [
        ("restoreBottomNav()", "restoreBottomNav() existe"),
        ("inflateBottomNavMenu()", "inflateBottomNavMenu() existe"),
        ("syncCheckedTab()", "syncCheckedTab() existe"),
        ("} else if (session.isLoggedIn()) {", "onCreate restaura al recrear con sesión"),
    ]:
        if needle not in main:
            fail(f"MainActivity: falta {label}")
        else:
            ok(label)
    if "restoreBottomNav();" not in main:
        fail("MainActivity: onCreate no llama a restoreBottomNav()")
    else:
        ok("onCreate llama a restoreBottomNav()")


def check_verified_badge():
    """Insignia de verificado 2.2.0: disco sólido centrado (ic_verified_badge),
    legible a 20dp, en tarjeta, detalle de empresa y ficha."""
    print("17. Insignia de verificado")
    badge = RES / "drawable/ic_verified_badge.xml"
    if not badge.exists():
        fail("ic_verified_badge.xml no existe (la insignia v2 es un disco sólido)")
    else:
        btext = badge.read_text(encoding="utf-8", errors="ignore")
        if "@color/verified_tint" not in btext or "@color/on_verified" not in btext:
            fail("ic_verified_badge: no usa verified_tint + on_verified")
        else:
            ok("ic_verified_badge: disco menta + check en on_verified")
    for layout in ("item_job_card.xml", "item_job_detail.xml", "fragment_job_detail.xml"):
        text = (LAYOUT / layout).read_text(encoding="utf-8", errors="ignore")
        m = re.search(r"<ImageView[^>]*?verified_icon[^>]*/>", text, re.S)
        block = m.group(0) if m else ""
        if "ic_verified_badge" not in block:
            fail(f"{layout}: verified_icon no usa ic_verified_badge")
        elif 'android:layout_width="20dp"' not in block:
            fail(f"{layout}: verified_icon no es 20dp")
        elif 'android:layout_gravity="center_vertical"' not in block:
            fail(f"{layout}: verified_icon sin centrado vertical")
        elif "app:tint" in block:
            fail(f"{layout}: verified_icon con tint (el disco lleva su propio color)")
        else:
            ok(f"{layout}: insignia v2 20dp centrada")
    light = (RES / "values/colors.xml").read_text(encoding="utf-8", errors="ignore")
    night = (RES / "values-night/colors.xml").read_text(encoding="utf-8", errors="ignore")
    for token in ("verified_tint", "on_verified"):
        if f'name="{token}"' not in light or f'name="{token}"' not in night:
            fail(f"{token} no definido en claro y noche")
        else:
            ok(f"{token} en claro y noche")
    if 'name="success_text"' not in night:
        fail("success_text sin variante nocturna (verde oscuro invisible de noche)")
    else:
        ok("success_text con variante nocturna")


def check_intro_sound():
    """Sonido de introducción 2.1.1: chime en res/raw, reproducción con
    respeto a silencio/vibración y al interruptor de Perfil."""
    print("18. Sonido de introducción")
    wav = RES / "raw/cp_chime.wav"
    if not wav.exists() or wav.stat().st_size < 1000:
        fail("res/raw/cp_chime.wav no existe o está vacío")
    else:
        ok(f"cp_chime.wav presente ({wav.stat().st_size} bytes)")
    splash = (JAVA / "com/cubapincha/job/ui/SplashFragment.java").read_text(
        encoding="utf-8", errors="ignore")
    for needle, label in [
        ("R.raw.cp_chime", "splash reproduce cp_chime"),
        ("RINGER_MODE_NORMAL", "splash respeta silencio/vibración"),
        ("isIntroSoundEnabled", "splash respeta el interruptor de Perfil"),
        ("chimePlayer.release()", "MediaPlayer se libera"),
    ]:
        if needle not in splash:
            fail(f"SplashFragment: falta {label}")
        else:
            ok(label)
    session = (JAVA / "com/cubapincha/job/data/SessionManager.java").read_text(
        encoding="utf-8", errors="ignore")
    if "isIntroSoundEnabled" not in session:
        fail("SessionManager: falta isIntroSoundEnabled")
    else:
        ok("SessionManager guarda la preferencia de sonido")
    profile = (LAYOUT / "fragment_profile.xml").read_text(encoding="utf-8", errors="ignore")
    if "switch_intro_sound" not in profile:
        fail("fragment_profile: falta switch_intro_sound")
    else:
        ok("Perfil tiene el interruptor de sonido")


def check_spacing():
    """Pulido 2.1.1: tarjetas con más aire (padding 18dp) y ritmo 12dp."""
    print("19. Espaciado de tarjetas")
    dimen = (RES / "values/dimens.xml").read_text(encoding="utf-8", errors="ignore")
    if '<dimen name="padding_card">18dp</dimen>' not in dimen:
        fail("padding_card no es 18dp")
    else:
        ok("padding_card 18dp")
    card = (LAYOUT / "item_job_card.xml").read_text(encoding="utf-8", errors="ignore")
    if card.count("@dimen/space_12") < 2:
        fail("item_job_card: las filas de datos no usan ritmo 12dp")
    else:
        ok("item_job_card con ritmo 12dp entre filas")


def _backend_text():
    """Lee Code.gs o devuelve None si no está en el workspace."""
    backend = ROOT.parent / "jobmatch-backend" / "Code.gs"
    if not backend.exists():
        return None
    return backend.read_text(encoding="utf-8", errors="ignore")


def check_push():
    """Push FCM 2.2.0: cliente con init manual (sin google-services) y
    envío real en el backend."""
    print("20. Push FCM")
    gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8", errors="ignore")
    if "firebase-messaging" not in gradle:
        fail("build.gradle: falta la dependencia firebase-messaging")
    else:
        ok("firebase-messaging en build.gradle")
    manifest = MANIFEST.read_text(encoding="utf-8", errors="ignore")
    if "CubaPinchaMessagingService" not in manifest:
        fail("AndroidManifest: falta CubaPinchaMessagingService")
    elif "default_notification_channel_id" not in manifest:
        fail("AndroidManifest: falta default_notification_channel_id")
    else:
        ok("servicio de mensajería + canal por defecto en el manifest")
    push = (JAVA / "com/cubapincha/job/util/PushManager.java").read_text(
        encoding="utf-8", errors="ignore")
    if "FirebaseOptions" not in push or "initIfNeeded" not in push:
        fail("PushManager: falta la init manual con FirebaseOptions")
    else:
        ok("PushManager con init manual (FirebaseOptions)")
    repo = (JAVA / "com/cubapincha/job/data/Repository.java").read_text(
        encoding="utf-8", errors="ignore")
    if "getFcmToken" not in repo:
        fail("Repository: registerDevice no usa el token FCM")
    elif "getDeviceId" in repo:
        fail("Repository: registerDevice sigue enviando el deviceId (no es un token FCM)")
    else:
        ok("registerDevice envía el token FCM real")
    code = _backend_text()
    if code is None:
        fail("backend Code.gs no está en el workspace (no se puede verificar el push)")
        return
    if "PUSH_STUB" in code:
        fail("Code.gs: trySendPush_ sigue siendo un stub")
    elif "fcm.googleapis.com" not in code or "messages:send" not in code:
        fail("Code.gs: trySendPush_ no llama a FCM HTTP v1")
    elif "UNREGISTERED" not in code:
        fail("Code.gs: trySendPush_ no desactiva tokens inválidos")
    else:
        ok("trySendPush_ real (FCM HTTP v1 + desactivación de tokens)")
    for fname, label in (("ApiClient.java", "ApiClient"),
                         ("AppsScriptApiClient.java", "AppsScriptApiClient")):
        text = (JAVA / "com/cubapincha/job/data" / fname).read_text(
            encoding="utf-8", errors="ignore")
        if "unregisterDevice" not in text:
            fail(f"{label}: falta unregisterDevice")
        else:
            ok(f"{label}: unregisterDevice presente")
    if "unregisterDevice" not in code:
        fail("Code.gs: falta la acción unregisterDevice")
    else:
        ok("Code.gs: unregisterDevice presente")


def check_google_signin():
    """Google Sign-In 2.2.0: flujo real con signInWithIdp, sin stub."""
    print("21. Google Sign-In")
    gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8", errors="ignore")
    if "play-services-auth" not in gradle:
        fail("build.gradle: falta play-services-auth")
    else:
        ok("play-services-auth en build.gradle")
    login = (JAVA / "com/cubapincha/job/ui/LoginFragment.java").read_text(
        encoding="utf-8", errors="ignore")
    if "google_soon" in login:
        fail("LoginFragment: btn_google sigue siendo un stub (google_soon)")
    elif "GoogleSignIn" not in login:
        fail("LoginFragment: btn_google no usa GoogleSignIn")
    else:
        ok("LoginFragment: flujo Google real")
    auth = (JAVA / "com/cubapincha/job/data/FirebaseAuthRest.java").read_text(
        encoding="utf-8", errors="ignore")
    if "signInWithIdp" not in auth:
        fail("FirebaseAuthRest: falta signInWithIdp")
    elif "providerId=google.com" not in auth:
        fail("FirebaseAuthRest: falta providerId=google.com")
    else:
        ok("FirebaseAuthRest: signInWithIdp con Google")


def check_saved_filter_alerts():
    """Alertas de búsquedas guardadas 2.2.0: el CRUD existía, el motor no."""
    print("22. Alertas de búsquedas guardadas")
    code = _backend_text()
    if code is None:
        fail("backend Code.gs no está en el workspace (no se puede verificar las alertas)")
    else:
        for needle, label in (
                ("matchSavedFiltersForJob_", "evaluación inmediata al publicar"),
                ("runSavedFiltersDaily_", "digest diario"),
                ("installSavedFiltersTrigger_", "trigger diario"),
                ("filterMatchesJob_", "evaluador de filtros"),
                ("saved_filter_match", "tipo de notificación saved_filter_match")):
            if needle not in code:
                fail(f"Code.gs: falta {label} ({needle})")
            else:
                ok(f"Code.gs: {label}")
    search = (JAVA / "com/cubapincha/job/ui/SearchFragment.java").read_text(
        encoding="utf-8", errors="ignore")
    if "saved_searches_row" not in search or "loadSavedSearchChips" not in search:
        fail("SearchFragment: falta la fila visible de búsquedas guardadas")
    else:
        ok("SearchFragment: chips de búsquedas guardadas")
    contract = (ROOT / "CONTRACT.md").read_text(encoding="utf-8", errors="ignore")
    if "Push y alertas de búsquedas guardadas (1.4.0)" not in contract:
        fail("CONTRACT.md: falta la sección de push y alertas 1.4.0")
    elif "saved_filter_match" not in contract:
        fail("CONTRACT.md: no documenta saved_filter_match")
    else:
        ok("CONTRACT.md documenta push y alertas 1.4.0")


def check_contract_coherence():
    """Coherencia contrato↔backend: sintaxis de Code.gs, versiones
    alineadas y acciones documentadas."""
    print("23. Coherencia contrato↔backend")
    backend = ROOT.parent / "jobmatch-backend" / "Code.gs"
    if not backend.exists():
        fail("backend Code.gs no está en el workspace")
        return
    try:
        with tempfile.NamedTemporaryFile("w", suffix=".js", delete=False,
                                         encoding="utf-8") as tmp:
            tmp.write(backend.read_text(encoding="utf-8", errors="ignore"))
            tmp_path = tmp.name
        result = subprocess.run(["node", "--check", tmp_path],
                                capture_output=True, text=True, timeout=60)
        Path(tmp_path).unlink(missing_ok=True)
        if result.returncode != 0:
            fail(f"node --check Code.gs: {result.stderr.strip()[:200]}")
        else:
            ok("node --check Code.gs OK")
    except FileNotFoundError:
        fail("node no disponible: no se puede verificar la sintaxis de Code.gs")
    except subprocess.TimeoutExpired:
        fail("node --check Code.gs: tiempo agotado")
    code = backend.read_text(encoding="utf-8", errors="ignore")
    contract = (ROOT / "CONTRACT.md").read_text(encoding="utf-8", errors="ignore")
    m_code = re.search(r"API_VERSION = '([0-9.]+)'", code)
    m_doc = re.search(r"Contrato API ([0-9.]+)", contract)
    if not m_code or not m_doc:
        fail("no se pudo leer la versión del backend o del contrato")
    elif m_code.group(1) != m_doc.group(1):
        fail(f"versión desalineada: Code.gs {m_code.group(1)} vs CONTRACT.md {m_doc.group(1)}")
    else:
        ok(f"versiones alineadas: API {m_code.group(1)}")
    actions = set()
    for var in ("READ_ACTIONS", "WRITE_ACTIONS"):
        m = re.search(r"var %s = \[(.*?)\];" % var, code, re.S)
        if m:
            actions.update(re.findall(r"'([^']+)'", m.group(1)))
    missing = sorted(a for a in actions if a not in contract)
    if missing:
        fail(f"acciones sin documentar en CONTRACT.md: {', '.join(missing)}")
    else:
        ok(f"{len(actions)} acciones de Code.gs documentadas en CONTRACT.md")


def check_audit_fixes():
    """Correcciones de la auditoría 2.2.0: P0/P1 de datos, navegación y UI."""
    print("24. Correcciones de la auditoría")
    data = JAVA / "com/cubapincha/job/data"
    ui = JAVA / "com/cubapincha/job/ui"
    apps = (data / "AppsScriptApiClient.java").read_text(encoding="utf-8", errors="ignore")
    if '"salaryCurrency"' in apps:
        fail("AppsScriptApiClient: jobBody sigue enviando salaryCurrency (el backend lo rechaza)")
    else:
        ok("jobBody sin salaryCurrency (P0-1)")
    db = (data / "LocalDb.java").read_text(encoding="utf-8", errors="ignore")
    m = re.search(r"void clearUserData\(\) \{(.*?)\n    \}", db, re.S)
    segment = m.group(1) if m else ""
    if "pending_actions" in segment or '"drafts"' in segment:
        fail("LocalDb.clearUserData sigue borrando pending_actions o drafts")
    elif "clearPendingAndDrafts" not in db:
        fail("LocalDb: falta clearPendingAndDrafts (solo para cuentas distintas)")
    else:
        ok("logout conserva cola offline y borradores (P0-3/P0-4)")
    session = (data / "SessionManager.java").read_text(encoding="utf-8", errors="ignore")
    if "EncryptedSharedPreferences" not in session or "cubapincha_secure" not in session:
        fail("SessionManager: los tokens no están en EncryptedSharedPreferences")
    else:
        ok("tokens en EncryptedSharedPreferences (P0-5)")
    main = (ui / "MainActivity.java").read_text(encoding="utf-8", errors="ignore")
    if "isStateSaved" not in main or "runWhenSafe" not in main:
        fail("MainActivity: falta la guardia de estado (isStateSaved/runWhenSafe)")
    else:
        ok("navegación con guardia de estado (P0-6)")
    picker = (ui / "LocationPicker.java").read_text(encoding="utf-8", errors="ignore")
    if "refreshMunicipalities" not in picker:
        fail("LocationPicker: falta refreshMunicipalities")
    else:
        ok("LocationPicker con refreshMunicipalities (P0-7)")
    no_restore = [f for f in ("RegisterWorkerFragment", "RegisterCompanyFragment",
                              "FilterSheetFragment", "CreateJobFragment",
                              "EditProfileFragment")
                  if "onViewStateRestored" not in (ui / f"{f}.java").read_text(
                      encoding="utf-8", errors="ignore")]
    if no_restore:
        fail(f"formularios sin onViewStateRestored: {', '.join(no_restore)}")
    else:
        ok("los 5 formularios restauran municipio tras rotar (P0-7)")
    for fname in ("ApiClient.java", "AppsScriptApiClient.java", "DemoApiClient.java"):
        text = (data / fname).read_text(encoding="utf-8", errors="ignore")
        if "sync(String since, int offset)" not in text:
            fail(f"{fname}: sync sin paginación por offset")
        else:
            ok(f"{fname}: sync con offset")
    demo = (data / "DemoApiClient.java").read_text(encoding="utf-8", errors="ignore")
    if "filterCachedJobs" not in demo:
        fail("DemoApiClient: falta filterCachedJobs (búsqueda offline)")
    else:
        ok("búsqueda offline filtra la caché")
    code = _backend_text()
    if code is None:
        fail("backend Code.gs no está en el workspace")
    else:
        m = re.search(r"var SYNCABLE = \{(.*?)\};", code, re.S)
        if not m or "saveCandidate" not in m.group(1):
            fail("Code.gs: saveCandidate no está en SYNCABLE")
        else:
            ok("Code.gs: saveCandidate en SYNCABLE")
        if "ctx.params.offset" not in code:
            fail("Code.gs: readSync_ sin paginación por offset")
        else:
            ok("Code.gs: readSync_ con offset")
        if "reactivated" not in code:
            fail("Code.gs: writeExpressInterest_ no reactiva candidaturas retiradas")
        else:
            ok("Code.gs: re-postulación tras retirar el interés")
    utils = (ui / "UiUtils.java").read_text(encoding="utf-8", errors="ignore")
    if "setDebouncedClickListener" not in utils:
        fail("UiUtils: falta setDebouncedClickListener")
    else:
        ok("UiUtils con guarda anti-doble-toque")
    night = (RES / "values-night/colors.xml").read_text(encoding="utf-8", errors="ignore")
    missing = [c for c in ("night_blue", "confirm_green", "amber_notice", "error_red")
               if f'name="{c}"' not in night]
    if missing:
        fail(f"colores sin variante nocturna: {', '.join(missing)}")
    else:
        ok("variantes nocturnas de colores de estado (P1-30)")
    if not (JAVA / "com/cubapincha/job/util/NoHighlightEditText.java").exists():
        fail("NoHighlightEditText.java no existe")
    else:
        ok("NoHighlightEditText presente")
    no_nh = [f for f in ("fragment_register_worker.xml", "fragment_register_company.xml",
                         "fragment_login.xml", "fragment_edit_profile.xml")
             if "NoHighlightEditText" not in (LAYOUT / f).read_text(
                 encoding="utf-8", errors="ignore")]
    if no_nh:
        fail(f"layouts sin NoHighlightEditText: {', '.join(no_nh)}")
    else:
        ok("autofill sin amarillo en registro, login y perfil")
    splash = (ui / "SplashFragment.java").read_text(encoding="utf-8", errors="ignore")
    if ("AnimUtils.reduceMotion" not in splash
          or "HANDS_START_MS = 200L" not in splash
          or "SHAKE_START_MS = 800L" not in splash
          or "NAME_START_MS = 1600L" not in splash
          or "TAGLINE_START_MS = 2200L" not in splash
          or "CHIME_DELAY_MS = 2240L" not in splash
          or "NAVIGATE_REDUCED_MS = 420L" not in splash):
        fail("SplashFragment: falta la ceremonia v8 o el movimiento reducido")
    else:
        ok("splash v8: el apretón (manos que se encuentran y se estrechan), con movimiento reducido")
    if ("Keyframe" not in splash
            or '"shake"' not in splash
            or "0.1875f" not in splash
            or "SHAKE_AMPLITUDE_DP = 6f" not in splash
            or "markStage.setTranslationY" not in splash):
        fail("SplashFragment: el estrechón no aprieta dos veces sobre el escenario completo")
    elif ("logo_check" in splash or "name_rule" in splash or "dawn" in splash.lower()
            or "logo_piece" in splash or "startAvd" in splash):
        fail("SplashFragment: el splash v8 no lleva check, regla, amanecer, pieza ni AVD")
    else:
        ok("splash v8: el estrechón aprieta dos veces (baja rápido, sube despacio) y el nombre se eleva desde su máscara")
    job = (JAVA / "com/cubapincha/job/model/Job.java").read_text(
        encoding="utf-8", errors="ignore")
    if "equalsIgnoreCase" not in job:
        fail("Job.locationText no elimina la localidad duplicada")
    else:
        ok("localidad sin duplicar (Isla de la Juventud)")


def check_audit_fixes_v23():
    """Regresiones añadidas por la auditoría visual/lógica/seguridad v2.3."""
    print("25. Correcciones de auditoría v2.3")
    data = JAVA / "com/cubapincha/job/data"
    ui = JAVA / "com/cubapincha/job/ui"
    db = (data / "LocalDb.java").read_text(encoding="utf-8", errors="ignore")
    repo = (data / "Repository.java").read_text(encoding="utf-8", errors="ignore")
    session = (data / "SessionManager.java").read_text(encoding="utf-8", errors="ignore")
    net = (data / "AppsScriptApiClient.java").read_text(encoding="utf-8", errors="ignore")

    if ("throw new IllegalStateException" not in session
            or "using preferencias normales" in session
            or "return ctx.getSharedPreferences(SECURE_PREFS" in session):
        fail("SessionManager: sigue existiendo fallback a almacenamiento en claro")
    else:
        ok("SessionManager falla cerrado; nunca degrada tokens a texto plano")
    if (all(x in net for x in ("isTrustedScriptUrl", "script.google.com",
                               "script.googleusercontent.com", "currentUrl = new URL(currentUrl, loc)"))):
        ok("Apps Script: HTTPS + allowlist al seguir redirecciones")
    else:
        fail("AppsScriptApiClient: falta allowlist estricta de redirecciones")
    if all(x in db for x in ("offline_owner_user_id", "clearAccountData()",
                             "getReviewCount()", "return getWritableDatabase().insertWithOnConflict")):
        ok("LocalDb: owner offline, limpieza por cuenta, revisión persistente y enqueue verificable")
    else:
        fail("LocalDb: falta aislamiento por propietario o comprobación de escritura")
    if ("publish:%" in db and "clearPendingAndDrafts" in db
            and "db.clearAccountData()" in repo):
        ok("cambio de cuenta limpia cachés; logout conserva trabajo del mismo usuario")
    else:
        fail("Repository/LocalDb: política de logout/cambio de propietario incompleta")
    if all(x in repo for x in ("batchSize = 50", "pending.subList(", "db.getReviewCount()")):
        ok("sync offline: lotes acotados y contador Revisar persistente")
    else:
        fail("Repository: sync no divide la cola o pierde Revisar")
    if all(x in repo for x in ("LOCAL_STORAGE_ERROR", "boolean wasSaved = db.isSaved(jobId)",
                               "db.setSaved(jobId, wasSaved)")):
        ok("mutaciones offline no informan éxito si SQLite falla; favoritos revierten")
    else:
        fail("Repository: falta comprobar persistencia/rollback optimista")

    home = (ui / "HomeFragment.java").read_text(encoding="utf-8", errors="ignore")
    if "updateSectionVisibility()" in home and "Conserva las últimas ofertas válidas" in home:
        ok("Home: oculta secciones sin datos y conserva caché válida ante error")
    else:
        fail("Home: carga, vacío/error y contenido previo no están separados")
    create = (ui / "CreateJobFragment.java").read_text(encoding="utf-8", errors="ignore")
    if "scrollToField(firstInvalid)" in create and "private void scrollToField" in create:
        ok("publicación: scroll/enfoque al primer campo inválido")
    else:
        fail("CreateJob: el primer error puede quedar fuera del viewport")
    login = (ui / "LoginFragment.java").read_text(encoding="utf-8", errors="ignore")
    if all(x in login for x in ("btnGoogle", "btnRegister", "btnForgot", "for (View action : actions)")):
        ok("Login: bloquea acciones concurrentes y recupera estado al cancelar Google")
    else:
        fail("Login: controles secundarios no se bloquean durante autenticación")
    profile = (ui / "EditProfileFragment.java").read_text(encoding="utf-8", errors="ignore")
    if "displayNameTouched" in profile and "currentUser.displayName" in profile:
        ok("Perfil: el nombre del servidor no se sobreescribe con sesión obsoleta")
    else:
        fail("EditProfile: nombre inicial no protege contra datos de sesión obsoletos")
    jobs = (ui / "MyJobsFragment.java").read_text(encoding="utf-8", errors="ignore")
    detail = (ui / "JobDetailFragment.java").read_text(encoding="utf-8", errors="ignore")
    if "if (result.ok)" in jobs and "R.string.job_deleted" in jobs:
        ok("Mis ofertas: éxito de borrado condicionado al resultado")
    else:
        fail("MyJobs: borrado local puede anunciar éxito sin confirmación")
    if "No se pudieron cargar las opciones de contacto" in detail:
        ok("Detalle: error de contacto no se confunde con ausencia de contacto")
    else:
        fail("JobDetail: falta estado explícito de error de contacto")


def check_brand_v3():
    """Marca v3: vectores con la geometría canónica y sin restos de la
    marca propuesta por la auditoría (excluida por Henry)."""
    print("26. Marca v3 \u00abLa C que se completa\u00bb")
    before = len(failures)
    c_path = ("M 58.4 23.4 A 29 29 0 0 0 14 48 "
              "A 29 29 0 0 0 66.8 64.6")
    check_path = "M 66 66 L 73 77 L 88 57"
    # El apretón de Henry, trazado de su diseño 1: la manga izquierda
    # (M 27.0,71.4), la palma (M 40.1,53.9), la manga derecha
    # (M 77.9,56.9) y el primer dedo (M 62,52 L 76,66, trazo 7.5).
    sleeve_left = "M 27.0,71.4"
    palm = "M 40.1,53.9"
    sleeve_right = "M 77.9,56.9"
    finger_1 = "M 62,52 L 76,66"
    mark = (RES / "drawable/ic_cp_mark.xml").read_text(
        encoding="utf-8", errors="ignore") if (RES / "drawable/ic_cp_mark.xml").exists() else ""
    hand_left = (RES / "drawable/ic_cp_hand_left.xml").read_text(
        encoding="utf-8", errors="ignore") if (RES / "drawable/ic_cp_hand_left.xml").exists() else ""
    hand_right = (RES / "drawable/ic_cp_hand_right.xml").read_text(
        encoding="utf-8", errors="ignore") if (RES / "drawable/ic_cp_hand_right.xml").exists() else ""
    handshake = (RES / "drawable/ic_cp_handshake.xml").read_text(
        encoding="utf-8", errors="ignore") if (RES / "drawable/ic_cp_handshake.xml").exists() else ""
    hand_left_light = (RES / "drawable/ic_cp_hand_left_light.xml").read_text(
        encoding="utf-8", errors="ignore") if (RES / "drawable/ic_cp_hand_left_light.xml").exists() else ""
    hand_right_light = (RES / "drawable/ic_cp_hand_right_light.xml").read_text(
        encoding="utf-8", errors="ignore") if (RES / "drawable/ic_cp_hand_right_light.xml").exists() else ""
    if not mark or not hand_left or not hand_right or not handshake:
        fail("faltan vectores de la marca v3 o del apretón")
    elif c_path not in mark or check_path not in mark:
        fail("ic_cp_mark.xml: la geometría no es la canónica (C + check)")
    elif "@color/cpInk" not in mark or "@color/cpCoral" not in mark:
        fail("ic_cp_mark.xml: la C debe ser cpInk y el check cpCoral")
    elif (sleeve_left not in hand_left or finger_1 not in hand_left
            or "@color/cpInk" not in hand_left
            or 'android:strokeWidth="7.5"' not in hand_left):
        fail("ic_cp_hand_left.xml: la mano izquierda debe llevar la manga trazada y los 4 dedos en azul tinta")
    elif (palm not in hand_right or sleeve_right not in hand_right
            or "@color/cpTaupe" not in hand_right or "@color/cpTan" not in hand_right):
        fail("ic_cp_hand_right.xml: la mano derecha debe llevar la palma en marrón y la manga en dorado")
    elif (sleeve_left not in handshake or palm not in handshake
            or sleeve_right not in handshake or finger_1 not in handshake):
        fail("ic_cp_handshake.xml: el apretón completo debe llevar las dos manos")
    elif ("@color/cpCream" not in hand_left_light
            or "@color/cpCoral" not in hand_right_light
            or "@color/cpSun" not in hand_right_light):
        fail("las manos claras del splash deben ser marfil (izquierda) y cobre/dorado (derecha)")
    else:
        ok("vectores de la marca v3 con la geometría canónica y el apretón de Henry (dos manos, tres colores + versión clara)")
    # La carpeta manila (v3.2–v3.3) se retiró en v4, el maletín (v4) en
    # v5, el punto que viajaba (v5) en v6, la ruta con su pin, su check
    # y su fondo (v6) en v7 y la marca del HTML (v7) en v8: ni archivos
    # ni referencias a ellos en el código ni en los layouts.
    retired = ("ic_cp_folder.xml", "ic_cp_mark_c_folder.xml",
               "ic_cp_mark_check_folder.xml", "ic_cp_mark_folder.xml",
               "avd_cp_mark_c_folder_draw.xml", "ic_cp_pin.xml",
               "ic_cp_briefcase.xml", "ic_cp_mark_c_case.xml",
               "ic_cp_mark_check_case.xml", "ic_cp_mark_case.xml",
               "ic_cp_pin_case.xml", "avd_cp_mark_c_case_draw.xml",
               "ic_cp_dot_route.xml", "bg_dot_person.xml",
               "ic_cp_route_c.xml", "ic_cp_piece_route.xml",
               "ic_cp_pin_route.xml", "ic_cp_mark_route.xml",
               "ic_cp_mark_check_route.xml", "avd_cp_route_c_draw.xml",
               "bg_splash_route.xml", "bg_glow_violet.xml",
               "ic_cp_arc.xml", "ic_cp_piece.xml", "ic_cp_mark_html.xml",
               "avd_cp_arc_draw.xml")
    for name in retired:
        if (RES / "drawable" / name).exists():
            fail(f"res/drawable/{name} sigue existiendo (escenario retirado)")
            bad_retired = True
            break
    else:
        bad_retired = False
    if not bad_retired and (RES / "animator/cp_route_draw.xml").exists():
        fail("res/animator/cp_route_draw.xml sigue existiendo (animador retirado)")
        bad_retired = True
    if not bad_retired and (RES / "animator/cp_arc_draw.xml").exists():
        fail("res/animator/cp_arc_draw.xml sigue existiendo (animador retirado)")
        bad_retired = True
    if not bad_retired and (RES / "interpolator/route_draw_ease.xml").exists():
        fail("res/interpolator/route_draw_ease.xml sigue existiendo (interpolador retirado)")
        bad_retired = True
    if not bad_retired:
        ok("la carpeta, el maletín, el punto, la ruta y la marca del HTML retirados: sin archivos huérfanos")
    pkg = ROOT.parent / "cubapincha-marca"
    geo = pkg / "geometry.py"
    if geo.exists():
        text = geo.read_text(encoding="utf-8", errors="ignore")
        # geometry.py parte el path en varias líneas entre comillas.
        flat = re.sub(r"\s+", " ", text.replace('"', " "))
        if c_path not in flat or check_path not in flat:
            fail("cubapincha-marca/geometry.py no coincide con los vectores")
        else:
            ok("geometry.py coincide con los vectores Android")
    else:
        ok("paquete cubapincha-marca no está en el workspace (se omite)")
    shake = pkg / "handshake.py"
    if shake.exists():
        text = shake.read_text(encoding="utf-8", errors="ignore")
        if ("(27.0, 71.4)" not in text or "(40.1, 53.9)" not in text
                or "(77.9, 56.9)" not in text or "((62, 52), (76, 66))" not in text):
            fail("cubapincha-marca/handshake.py no coincide con los vectores")
        else:
            ok("handshake.py coincide con los vectores Android")
    web = ROOT.parent / "cubapincha-web"
    if (web / "assets/cp_mark.png").exists():
        ok("web: cp_mark.png presente")
    else:
        fail("web assets/cp_mark.png no existe")
    # Sin restos de la marca de la auditoría en el proyecto ni en la web.
    bad = 0
    for base in (ROOT, web):
        if not base.exists():
            continue
        for f in list(base.rglob("*.java")) + list(base.rglob("*.xml")) \
                + list(base.rglob("*.html")) + list(base.rglob("*.js")) \
                + list(base.rglob("*.css")):
            if "build/" in str(f) or ".gradle" in str(f):
                continue
            try:
                text = f.read_text(encoding="utf-8", errors="ignore")
            except OSError:
                continue
            if "cubapincha_mark_professional" in text or "cubapincha_logo_transparent" in text:
                fail(f"{f.relative_to(ROOT.parent)}: resto de la marca anterior")
                bad += 1
                if bad > 3:
                    break
        if bad > 3:
            break
    if bad == 0:
        ok("sin referencias a la marca de la auditoría ni al logo v2")
    if len(failures) == before:
        pass


def check_forms():
    """Formularios: sin amarillo de autofill, sin pistas duplicadas y
    con aire entre campos (lo que Henry veía solapado y pegado)."""
    print("27. Formularios (autofill, pistas y espaciado)")
    layouts = sorted((RES / "layout").glob("*.xml"))
    texts = {f: f.read_text(encoding="utf-8", errors="ignore") for f in layouts}
    plain = [f.name for f, t in texts.items()
             if "com.google.android.material.textfield.TextInputEditText" in t]
    if plain:
        fail(f"campos planos sin NoHighlightEditText: {', '.join(plain)}")
    else:
        ok("todos los campos de texto son NoHighlightEditText")
    double = []
    for f, t in texts.items():
        for m in re.finditer(r"<(?:com\.cubapincha\.job\.util\.NoHighlightEditText"
                             r"|com\.google\.android\.material\.textfield\.MaterialAutoCompleteTextView"
                             r"|AutoCompleteTextView)\b[^>]*/>", t, re.S):
            if "android:hint=" in m.group(0):
                double.append(f.name)
                break
    if double:
        fail(f"pista duplicada en el hijo del campo: {', '.join(double)}")
    else:
        ok("sin pistas duplicadas: los ejemplos van en placeholderText")
    themed = 0
    for themes in (RES / "values/themes.xml", RES / "values-night/themes.xml"):
        if "android:autofilledHighlight" in themes.read_text(
                encoding="utf-8", errors="ignore"):
            themed += 1
    if themed < 2:
        fail("el tema debe teñir autofilledHighlight en claro y noche")
    else:
        ok("autofilledHighlight en menta (claro y noche)")
    profile = texts[RES / "layout/fragment_profile.xml"]
    tight = [m.group(0) for m in re.finditer(
        r"<com\.google\.android\.material\.textfield\.TextInputLayout\b.*?"
        r"</com\.google\.android\.material\.textfield\.TextInputLayout>",
        profile, re.S) if 'android:layout_marginTop="8dp"' in m.group(0)]
    if tight:
        fail(f"campos de Perfil pegados (8dp): {len(tight)}")
    else:
        ok("campos de Perfil con 16dp de aire")
    tight_rows = [f.name for f in
                  ("fragment_register_worker.xml", "fragment_create_job.xml",
                   "sheet_filters.xml", "sheet_contact.xml")
                  if 'android:layout_marginEnd="6dp"' in texts[RES / "layout" / f]
                  or 'android:layout_marginStart="6dp"' in texts[RES / "layout" / f]]
    if tight_rows:
        fail(f"filas con separación de 6dp: {', '.join(tight_rows)}")
    else:
        ok("filas de dos columnas con 16dp de separación")
    nh = (JAVA / "com/cubapincha/job/util/NoHighlightEditText.java").read_text(
        encoding="utf-8", errors="ignore")
    if "postDelayed(this::invokeSetAutofilledFalse, 100L" not in nh:
        fail("NoHighlightEditText: falta la segunda limpieza a los 100 ms")
    else:
        ok("NoHighlightEditText limpia el resaltado dos veces")


def check_api_redirect():
    """Cliente de la API: en 301/302 el POST ya se ejecutó en /exec y la
    respuesta se recoge con GET sin cuerpo (v2.8.0 reenviaba el POST y
    Google devolvía una página HTML: la app no podía conectar)."""
    print("28. Redirección de la API (POST a /exec, GET a la respuesta)")
    client = JAVA / "com/cubapincha/job/data/AppsScriptApiClient.java"
    if not client.exists():
        fail("AppsScriptApiClient.java no existe")
        return
    text = client.read_text(encoding="utf-8", errors="ignore")
    if 'method = "GET"' not in text or "body = null" not in text:
        fail("AppsScriptApiClient: en 301/302 debe pasar a GET sin cuerpo")
    else:
        ok("en 301/302 el cliente pasa a GET sin cuerpo")
    if "code == 307 || code == 308" not in text:
        fail("AppsScriptApiClient: 307/308 deben conservar método y cuerpo")
    else:
        ok("307/308 conservan el método y el cuerpo")


def check_google_pending_token():
    """Registro con Google: el token pendiente debe ser el ID token de
    Firebase (del intercambio signInWithIdp), no el token de Google
    (v2.8.1 guardaba el de Google y el backend lo rechazaba con
    «Sesión no válida o caducada»)."""
    print("29. Token pendiente de Google (ID token de Firebase)")
    repo = JAVA / "com/cubapincha/job/data/Repository.java"
    login = JAVA / "com/cubapincha/job/ui/LoginFragment.java"
    if not repo.exists() or not login.exists():
        fail("Repository.java o LoginFragment.java no existe")
        return
    text = repo.read_text(encoding="utf-8", errors="ignore")
    m = re.search(r"public void loginWithGoogle.*?\n    \}\n", text, re.S)
    if not m:
        fail("Repository.loginWithGoogle no encontrado")
    else:
        body = m.group(0)
        save = body.find("saveFirebaseTokens(fb.data.idToken")
        call = body.find("loginWithIdToken(fb.data.idToken)")
        if save < 0:
            fail("loginWithGoogle: no guarda los tokens de Firebase")
        elif call >= 0 and save > call:
            fail("loginWithGoogle: los tokens de Firebase deben guardarse ANTES del login en el backend")
        else:
            ok("loginWithGoogle guarda los tokens de Firebase antes del login")
    text = login.read_text(encoding="utf-8", errors="ignore")
    i = text.find("savePendingGoogle")
    if i < 0:
        fail("LoginFragment: no llama a savePendingGoogle")
    else:
        seg = text[i:i + 250]
        if "session.getFirebaseIdToken()" in seg:
            ok("LoginFragment guarda el ID token de Firebase como pendiente")
        else:
            fail("LoginFragment: el token pendiente debe ser session.getFirebaseIdToken()")
        if "account.getIdToken()" in seg:
            fail("LoginFragment: savePendingGoogle no debe recibir account.getIdToken()")
        else:
            ok("savePendingGoogle no recibe el token de Google")


def check_builtin_config():
    """Valores de conexión integrados y cifrados (v2.9): la app arranca
    conectada sin pegar nada, y ningún secreto queda en claro en el código
    ni en el APK (el blob es AES-256-GCM con clave PBKDF2)."""
    print("30. Configuración integrada cifrada (sin secretos en claro)")
    defaults = JAVA / "com/cubapincha/job/data/SecureDefaults.java"
    if not defaults.exists():
        fail("SecureDefaults.java no existe")
        return
    text = defaults.read_text(encoding="utf-8", errors="ignore")
    for needle, what in [
            ('"AES/GCM/NoPadding"', "AES-GCM"),
            ("PBKDF2WithHmacSHA256", "PBKDF2"),
            ("BLOB_B64", "blob cifrado")]:
        if needle not in text:
            fail(f"SecureDefaults: falta {what}")
        else:
            ok(f"SecureDefaults usa {what}")
    # Ningún secreto en claro en el código de la app (lo que va al APK).
    src = ROOT / "app/src/main"
    leaks = []
    for pat in ["AIzaSy", "script.google.com/macros/s/", "apps.googleusercontent.com",
                "AKfycbwcponkXEE4"]:
        for f in src.rglob("*"):
            if f.is_file() and f.suffix in (".java", ".xml", ".kt"):
                try:
                    if pat in f.read_text(encoding="utf-8", errors="ignore"):
                        leaks.append(f"{f.relative_to(ROOT)}: {pat}")
                except OSError:
                    pass
    if leaks:
        fail("secreto en claro en el código: " + "; ".join(leaks[:3]))
    else:
        ok("ningún secreto en claro en el código de la app")
    sm = JAVA / "com/cubapincha/job/data/SessionManager.java"
    text = sm.read_text(encoding="utf-8", errors="ignore")
    missing = [key for key in ["apiUrl", "firebaseApiKey", "fcmProjectId", "fcmAppId",
                               "fcmSenderId", "googleClientId"]
               if f'SecureDefaults.get("{key}")' not in text]
    for key in missing:
        fail(f"SessionManager: sin respaldo integrado para {key}")
    if not missing:
        ok("SessionManager usa los 6 valores integrados como respaldo")
    if "!SecureDefaults.hasBuiltInConfig()" not in text:
        fail("SessionManager: el modo por defecto debe ser conectado con valores integrados")
    else:
        ok("una instalación nueva arranca en modo conectado")


def check_local_encryption():
    """Cifrado de datos locales (v2.9): borradores, cola, candidaturas y
    notificaciones cifrados con AES-256-GCM y clave en el Keystore; los
    valores de conexión, en el almacén cifrado."""
    print("31. Cifrado local (Keystore + almacén cifrado)")
    crypto = JAVA / "com/cubapincha/job/data/CryptoHelper.java"
    if not crypto.exists():
        fail("CryptoHelper.java no existe")
        return
    text = crypto.read_text(encoding="utf-8", errors="ignore")
    for needle, what in [
            ("AndroidKeyStore", "clave en Android Keystore"),
            ('"AES/GCM/NoPadding"', "AES-GCM"),
            ('"v1:"', "prefijo de versión del cifrado")]:
        if needle not in text:
            fail(f"CryptoHelper: falta {what}")
        else:
            ok(f"CryptoHelper: {what}")
    db = JAVA / "com/cubapincha/job/data/LocalDb.java"
    text = db.read_text(encoding="utf-8", errors="ignore")
    for table, what in [
            ("cached_applications", "candidaturas"),
            ("cached_notifications", "notificaciones"),
            ("pending_actions", "cola offline"),
            ("drafts", "borradores")]:
        if table not in text:
            fail(f"LocalDb: falta la tabla {table}")
    if "oldVersion < 4" not in text or "encryptColumn" not in text:
        fail("LocalDb: falta la migración de cifrado a v4")
    else:
        ok("LocalDb migra a v4 cifrando las 4 tablas personales")
    writes = text.count("CryptoHelper.encrypt(")
    reads = text.count("CryptoHelper.decrypt(")
    if writes < 4:
        fail(f"LocalDb: solo {writes} escrituras cifradas (esperado 4)")
    else:
        ok(f"LocalDb cifra al escribir ({writes} puntos)")
    if reads < 6:
        fail(f"LocalDb: solo {reads} lecturas descifradas (esperado 6+)")
    else:
        ok(f"LocalDb descifra al leer ({reads} puntos)")
    sm = (JAVA / "com/cubapincha/job/data/SessionManager.java").read_text(
        encoding="utf-8", errors="ignore")
    missing = [key for key in ["KEY_API_URL", "KEY_FCM_PROJECT_ID", "KEY_FCM_APP_ID",
                               "KEY_FCM_SENDER_ID", "KEY_GOOGLE_CLIENT_ID"]
               if f"secure.edit().putString({key}" not in sm]
    for key in missing:
        fail(f"SessionManager: {key} no se guarda en el almacén cifrado")
    if not missing:
        ok("todos los valores de conexión se guardan cifrados")


def check_smart_sync():
    """Sincronización inteligente (v2.9): caché primero en las pantallas,
    refresco silencioso, trabajo periódico en segundo plano y vigilante
    del esqueleto (nunca se queda colgado)."""
    print("32. Sincronización inteligente + caché primero")
    gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8", errors="ignore")
    if "androidx.work:work-runtime" not in gradle:
        fail("build.gradle: falta la dependencia de WorkManager")
    else:
        ok("dependencia WorkManager presente")
    worker = JAVA / "com/cubapincha/job/data/SyncWorker.java"
    if not worker.exists():
        fail("SyncWorker.java no existe")
    else:
        text = worker.read_text(encoding="utf-8", errors="ignore")
        if "extends Worker" not in text or "syncNowSync()" not in text:
            fail("SyncWorker: debe extender Worker y llamar a syncNowSync()")
        else:
            ok("SyncWorker sincroniza en segundo plano")
        if "isDataSaver()" not in text:
            fail("SyncWorker: debe respetar el ahorro de datos")
        else:
            ok("SyncWorker respeta el ahorro de datos")
    sched = JAVA / "com/cubapincha/job/data/SyncScheduler.java"
    if not sched.exists():
        fail("SyncScheduler.java no existe")
    else:
        text = sched.read_text(encoding="utf-8", errors="ignore")
        if "PeriodicWorkRequest" not in text or "OneTimeWorkRequest" not in text:
            fail("SyncScheduler: falta el turno periódico o el inmediato")
        else:
            ok("SyncScheduler programa turno periódico e inmediato")
    app = (ROOT / "app/src/main/java/com/cubapincha/job/CubaPinchaApp.java").read_text(
        encoding="utf-8", errors="ignore")
    if "SyncScheduler.schedulePeriodic" not in app or "SyncScheduler.syncSoon" not in app:
        fail("CubaPinchaApp: no programa la sincronización al arrancar")
    else:
        ok("la app programa el sync al arrancar")
    main = (JAVA / "com/cubapincha/job/ui/MainActivity.java").read_text(
        encoding="utf-8", errors="ignore")
    if "SyncScheduler.syncSoon" not in main:
        fail("MainActivity: no sincroniza al entrar")
    else:
        ok("al entrar se sincroniza en segundo plano")
    repo = (JAVA / "com/cubapincha/job/data/Repository.java").read_text(
        encoding="utf-8", errors="ignore")
    missing = [m for m in ["getCachedJobsPage", "getCachedNotifications",
                           "getCachedApplications", "syncNowSync"]
               if m not in repo]
    for method in missing:
        fail(f"Repository: falta {method}")
    if not missing:
        ok("Repository expone caché y sync síncrono")
    ui = JAVA / "com/cubapincha/job/ui"
    for frag, cache_call in [
            ("HomeFragment.java", "getCachedJobsPage"),
            ("NotificationsFragment.java", "getCachedNotifications"),
            ("SearchFragment.java", "getCachedJobsPage"),
            ("InterestsFragment.java", "getCachedApplications")]:
        text = (ui / frag).read_text(encoding="utf-8", errors="ignore")
        if cache_call not in text:
            fail(f"{frag}: no pinta primero desde la caché")
        else:
            ok(f"{frag} pinta primero desde la caché")
        if "postDelayed(watchdog, 15000)" not in text:
            fail(f"{frag}: falta el vigilante del esqueleto (15 s)")
        else:
            ok(f"{frag} tiene vigilante del esqueleto")
    text = (ui / "MyJobsFragment.java").read_text(encoding="utf-8", errors="ignore")
    if "postDelayed(watchdog, 15000)" not in text:
        fail("MyJobsFragment: falta el vigilante del esqueleto (15 s)")
    else:
        ok("MyJobsFragment tiene vigilante del esqueleto")
    strings = (ROOT / "app/src/main/res/values/strings.xml").read_text(
        encoding="utf-8", errors="ignore")
    if "connection_slow" not in strings:
        fail("strings.xml: falta el texto connection_slow")
    else:
        ok("texto de conexión lenta presente")


def check_field_padding():
    """Campos de formulario (v2.9.1): el relleno es explícito en todos los
    campos. Material solo da 16dp por defecto a los hijos que resuelven
    editTextStyle; los desplegables (AutoCompleteTextView) no lo resuelven
    y TextInputLayout les quita el fondo, así que su texto quedaba pegado
    al borde (Municipio, Provincia...)."""
    print("33. Relleno de campos (texto con aire en los 87 campos)")
    themes = (RES / "values/themes.xml").read_text(encoding="utf-8", errors="ignore")
    m = re.search(r'<style name="Widget\.CubaPincha\.TextInputEditText".*?</style>',
                  themes, re.S)
    if not m:
        fail("themes.xml: falta el estilo Widget.CubaPincha.TextInputEditText")
        return
    style = m.group(0)
    for attr, val in [("paddingStart", "16dp"), ("paddingEnd", "16dp"),
                      ("paddingTop", "16dp"), ("paddingBottom", "16dp"),
                      ("textSize", "16sp"), ("minHeight", "56dp")]:
        if f'android:{attr}">{val}' not in style:
            fail(f"el estilo del campo no fija {attr}={val}")
            return
    ok("estilo del campo: 16dp de aire, texto 16sp, alto 56dp")
    layouts = sorted((RES / "layout").glob("*.xml"))
    fields = styled = plain_ac = stray = 0
    for f in layouts:
        t = f.read_text(encoding="utf-8", errors="ignore")
        plain_ac += len(re.findall(r"<AutoCompleteTextView\b", t))
        stray += len(re.findall(r"(?m)^[ \t]*android:$", t))
        for m in re.finditer(
                r"<(?:com\.cubapincha\.job\.util\.NoHighlightEditText"
                r"|com\.google\.android\.material\.textfield\.MaterialAutoCompleteTextView)"
                r"[^>]*(?:/>|>)", t, re.S):
            fields += 1
            if 'style="@style/Widget.CubaPincha.TextInputEditText"' in m.group(0):
                styled += 1
    if plain_ac:
        fail(f"quedan {plain_ac} AutoCompleteTextView planos (usar Material)")
    else:
        ok("sin AutoCompleteTextView planos: todos son Material")
    if stray:
        fail(f"quedan {stray} líneas 'android:' sueltas en layouts")
    else:
        ok("sin líneas sueltas en los layouts")
    if fields == 0:
        fail("no se encontró ningún campo en los layouts")
    elif styled != fields:
        fail(f"campos con el estilo: {styled} de {fields}")
    else:
        ok(f"los {fields} campos llevan el estilo con aire")


def check_package_rename():
    """Paquete com.cubapincha.job (v2.9.2) + firma de pruebas fijada."""
    print("34. Paquete com.cubapincha.job y firma fijada")
    leftovers = []
    for f in sorted((ROOT / "app").rglob("*")):
        if "app/build" in str(f.relative_to(ROOT)):
            continue  # generado: lo regenera la compilación limpia
        if f.is_file() and f.suffix in (".java", ".xml", ".gradle", ".kt"):
            text = f.read_text(encoding="utf-8", errors="ignore")
            if "henry.jobmatch" in text or "com.henry" in text:
                leftovers.append(str(f.relative_to(ROOT)))
    if leftovers:
        fail(f"quedan referencias al paquete viejo: {', '.join(leftovers[:5])}")
    else:
        ok("sin referencias a com.henry.jobmatch en app/")
    keystore = ROOT / "debug.keystore"
    if not keystore.exists():
        fail("debug.keystore no existe en la raíz del proyecto")
    else:
        ok("debug.keystore del proyecto presente")
    gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8", errors="ignore")
    if 'storeFile file("../debug.keystore")' not in gradle:
        fail("build.gradle: la firma debug no apunta al keystore del proyecto")
    else:
        ok("firma debug fijada al keystore del proyecto")


def main():
    print("CubaPincha — verificación de entrega\n")
    check_animators()
    check_avds()
    check_drawable_roots()
    defined = collect_defined()
    check_layout_refs(defined)
    check_java_ids(defined)
    check_manifest()
    check_branding()
    check_catalog()
    check_sharing()
    check_offline_and_version()
    check_tokens()
    check_icons_and_launcher()
    check_splash_and_welcome()
    check_no_old_icons()
    check_web()
    check_nav_restore()
    check_verified_badge()
    check_intro_sound()
    check_spacing()
    check_push()
    check_google_signin()
    check_saved_filter_alerts()
    check_contract_coherence()
    check_audit_fixes()
    check_audit_fixes_v23()
    check_brand_v3()
    check_forms()
    check_api_redirect()
    check_google_pending_token()
    check_builtin_config()
    check_local_encryption()
    check_smart_sync()
    check_field_padding()
    check_package_rename()
    print(f"\n{checks} comprobaciones, {len(failures)} fallos.")
    if failures:
        print("NO ENTREGAR: corrige los fallos y vuelve a verificar.")
        return 1
    print("Todo bien: la entrega puede continuar (falta la prueba en el teléfono).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
