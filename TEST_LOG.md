# CubaPincha 2.11.4 — registro de pruebas

## Comprobaciones estáticas

- [x] Rama de trabajo derivada del punto de restauración 2.11.3.
- [x] `versionName 2.11.4` y `versionCode 29`.
- [x] Aviso sin conexión con amarillo más oscuro en claro y oscuro.
- [x] Inicio: ojito antes de Cambiar vista.
- [x] Ojitos de Inicio, Buscar, Panel empresa y tarjetas permanecen visibles y alternan abierto/cerrado.
- [x] Buscar conserva un solo ojito junto a Guardar búsqueda.
- [x] Reportar usa escudo `gpp_maybe` y colores del tema.
- [x] Crear oferta: calidad, modo, foto, extracción; Publicar en la cabecera; sin destello final duplicado.
- [x] Guardado de ofertas y perfiles: estado confirmado, bloqueo lógico/reversión ante error y cola offline preservada.

## Validación local

La comprobación `./gradlew :app:assembleDebug` quedó bloqueada en este sandbox porque no existe Android SDK (`SDK location not found`); no se creó `local.properties`.

## Validación en dispositivo

Pendiente de ejecutar con la APK firmada de Codemagic en teléfono Android. Debe cubrir ambos roles, claro/oscuro, online/offline y doble toque.
