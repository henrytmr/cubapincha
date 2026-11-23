# Registro de pruebas — CubaPincha DEV

## Continuación Android 2.11.0 — 2026-11-22

### Cambios revisados estáticamente

- FCM usa `FirebaseApp.initializeApp(context)` con la configuración generada por Google Services; se eliminó el gate de cuatro preferencias manuales.
- El JSON Firebase de DEV se validó por paquete/proyecto contra el adjunto privado sin imprimir claves o identificadores.
- `ApiClient`, `SupabaseApiClient`, `DemoApiClient` y `Repository` incluyen `getRatings` y `rateApplication`.
- `getRatings` construye una lectura pública sin JWT; `rateApplication` usa la autorización normal del cliente. La valoración no se manda a `syncActions` ni a la cola offline.
- CTA de valoración limitada al estado `hired`; formulario limita comentarios a 1000 caracteres y consulta valoraciones paginadas antes de mostrarlo.
- Perfil propio, candidato y empresa disponen de rutas para consultar estrellas/media/lista de valoraciones.
- `gradlew` confirmado con permiso `755`.
- Versión de fuente: 2.11.0 / versionCode 26.

### Validaciones estáticas ejecutadas

- Parseo correcto del manifiesto y los 156 XML de recursos (157 XML en total).
- Sin IDs duplicados en `fragment_profile.xml`, `item_application.xml` y `item_candidate.xml`; `btn_rate_work` existe en ambos layouts de candidaturas.
- `google-services.json` es JSON válido e incluye el paquete Android esperado; no se imprimieron valores de configuración.
- `ShareUtils` usa la URL web canónica de GitHub Pages; `gradlew` tiene modo `755` y no se ejecutó.
- `local.properties` no está en el árbol fuente; no se detectó patrón de JWT en Java/XML/Gradle.
- El bloque antiguo de configuración manual de conexión/FCM quedó visualmente oculto en Perfil.

### No ejecutado (por instrucción del usuario)

- [ ] Gradle `assembleDebug` / cualquier compilación.
- [ ] Android Lint o pruebas instrumentadas.
- [ ] Instalación limpia y recepción real de FCM.
- [ ] Llamada real a `rateApplication`, escritura de datos o prueba con cuenta personal.
- [ ] Prueba de integración contra Edge v9; el backend no se llamó ni se modificó en esta sesión.
- [ ] Validación en el Motorola y confirmación de JDK 21 en Muse.
- [ ] Verificación del APK actualmente instalado; la fuente DEV de compartir ya usa el enlace web canónico.

**Conclusión:** fuente Android modificada y revisada de forma estática; no es evidencia de APK compilada ni de funcionamiento runtime. Muse debe compilar, instalar y ejecutar las pruebas pendientes antes de distribuir.
