# CubaPincha — informe de construcción (Android DEV 2.11.0)

**Estado:** fuente Android actualizada a 2.11.0 / versionCode 26. **No compilada** por instrucción del usuario; no hay APK nuevo en esta entrega.

## Cambios incluidos

- FCM ahora usa los recursos Firebase generados por Google Services y no exige cuatro valores manuales en preferencias para arrancar en instalación limpia.
- Se añadieron modelos y llamadas Android `rateApplication`/`getRatings` contra el contrato existente de Edge v9. El `getRatings` público no transmite JWT; la escritura sí usa la sesión del usuario y no entra en la cola offline.
- Se añadió valoración en candidaturas `hired`, con estrellas 1–5, comentario opcional de hasta 1000 caracteres, consulta paginada para no permitir duplicados en la UI y tratamiento de conflictos del servidor.
- Perfil propio muestra promedio, estrellas, total y valoraciones recibidas con paginación. Se añadieron puntos de entrada a las valoraciones públicas del candidato y de la empresa.
- El archivo Firebase incluido ya coincidía con la configuración de referencia para `com.cubapincha.job`; no se sustituyó ni se imprimieron sus valores.
- `gradlew` tiene permiso ejecutable `755`.
- Se verificó la fuente de compartir: ya usa la URL web canónica; no se cambió.

## Backend y operaciones remotas

El usuario confirmó que SQL 006 y la Edge Function v9 ya están aplicadas y verificadas. No se volvió a ejecutar migración, no se llamó a la Edge Function para escritura, no se desplegó ni se modificó backend.

## Estado de build / pruebas

No se ejecutó Gradle, Android Lint, instalación o prueba en dispositivo. No se creó APK/AAB y no se envió ninguna valoración real. La sincronización con JDK 21 en el PC de Muse sigue pendiente de confirmar. Las verificaciones realizadas en esta sesión son estáticas y se registran en `TEST_LOG.md`.

La revisión estática posterior confirmó 157 XML parseables, IDs no duplicados en los tres layouts afectados, JSON Firebase válido con el paquete esperado, `gradlew` en modo `755`, enlace web canónico y ausencia de `local.properties` en el árbol fuente. También se ocultaron los contenedores de conexión/FCM que aún dejaban etiquetas visibles en Perfil.

## Seguridad

No incluir `service_role`, contraseñas de firma ni tokens en la aplicación. La `service_role` compartida en el chat debe rotarse coordinadamente; esta sesión no la usó ni la rotó.
