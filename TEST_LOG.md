# CubaPincha 2.11.4 — registro de pruebas

## Comprobaciones estáticas

- [x] Rama derivada del punto de restauración 2.11.3.
- [x] `versionName 2.11.4` y `versionCode 29`.
- [x] Aviso sin conexión con amarillo más oscuro en claro y oscuro.
- [x] Inicio: ojito antes de Cambiar vista.
- [x] Ojitos de Inicio, Buscar, Panel empresa y tarjetas permanecen visibles y alternan abierto/cerrado.
- [x] Buscar conserva un solo ojito junto a Guardar búsqueda.
- [x] Reportar usa escudo `gpp_maybe` y colores del tema.
- [x] Crear oferta: calidad, modo, foto, extracción; Publicar en la cabecera; sin destello final duplicado.
- [x] Guardado de ofertas y perfiles: estado confirmado, bloqueo lógico/reversión ante error y cola offline preservada.
- [x] `git diff --check` y parseo XML sin errores.
- [x] No hay APK, keystore ni `local.properties` versionados.

## Build firmado

El primer build Codemagic (`6b061a81d17d55100e0ffeb3`, commit `cfd3bbf`) falló por un error de alcance de `wasSaved` en `Repository.java`. Se corrigió, se publicó el commit `161ae673cf3eda99fcfaf8d924ca1dfe82a15cb9` en ambos remotos y se inició un segundo build seleccionando explícitamente la rama `manus/cubapincha-2.11.4` y el workflow firmado.

El build final `6b061b54d17d55100e0fff00` terminó correctamente. El APK descargado pasó `unzip -tq`, fue identificado como firmado, y Androguard extrajo `com.cubapincha.job`, `2.11.4`, `29` y un certificado. SHA-256: `b28306dd9a7a89427829158bdab9a6fbb7757dec01eea01df96317b71832f9b4`.

## Validación en dispositivo

Pendiente de ejecutar en teléfono Android físico. Debe cubrir ambos roles, claro/oscuro, online/offline, guardado de publicación y perfil, doble toque, navegación de tarjetas, menú Reportar y formulario Publicar oferta.

La compilación local `./gradlew :app:assembleDebug` no pudo ejecutarse en este sandbox por ausencia de Android SDK (`SDK location not found`); no se creó `local.properties`.
