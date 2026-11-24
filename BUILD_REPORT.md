# CubaPincha — informe de construcción Android 2.11.3

**Base:** `cubapincha-2.11.2-segura` / `355ceea5e42da86af92276d670496cd9c87e5dda`.
**Versión:** `versionName 2.11.3`, `versionCode 28`.
**Servidor:** v25 existente; no se modificó.

## Cambios de aplicación

- Invitaciones de empresa persistentes mediante `invited`/`invitedOfferId`, con `uninviteWorker` y estado visual de check.
- Filtro empresarial reutilizando el bottom sheet común, catálogo de provincia/municipio y mínimo de coincidencia.
- Compartir perfil de trabajador mediante página web y deep links `cubapincha://trabajador/<id>`.
- Ojito persistente junto a Guardar búsqueda en Buscar, en Inicio y en búsqueda empresarial.
- Caché de fotos en memoria y disco para miniaturas offline, con limpieza explícita desde Perfil.
- Guardado de trabajadores usando el objeto completo cacheado, evitando tarjetas vacías y conservando reversión en error.
- Editar perfil incluye disponibilidad, modalidades y salario mínimo.
- La versión y esta documentación quedan alineadas con la entrega 2.11.3.

## Validación

La validación estática local pasó parseo de los XML de recursos, balance de llaves Java en los archivos modificados y `git diff --check`. La compilación local no pudo comenzar porque este sandbox no tiene Android SDK (`SDK location not found`); la compilación release firmada debe ejecutarse en Codemagic mediante `android-release` usando exactamente la rama/commit publicados.

No se afirma prueba física en teléfono: corresponde al usuario.

## Seguridad

El árbol de fuente no incluye `local.properties`, contraseñas, claves privadas ni keystore. No se modificó ni desplegó servidor.
