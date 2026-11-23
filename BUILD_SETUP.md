# Preparación de build — CubaPincha Android DEV 2.11.0

## Estado

La fuente está actualizada a `2.11.0` / `versionCode 26`, pero esta entrega **no está compilada** y no incluye un APK/AAB nuevo. La build final corresponde a Muse en el PC del usuario. No se ejecutó Gradle ni se modificó/desplegó el backend.

## Entorno esperado

Usa Android Studio con JDK 21 seleccionado como Gradle JDK, Android SDK Platform 34 y Build Tools 34.0.0. El proyecto usa Gradle Wrapper 8.9, Android Gradle Plugin 8.5.2 y Google Services plugin 4.4.2. El código compila con compatibilidad de lenguaje Java 17; eso no impide usar JDK 21 como runtime de Gradle. La disponibilidad/configuración actual de JDK 21 en el PC de Muse aún debe confirmarse.

## Configuración local

En la raíz de `codigo-fuente/`, copia `local.properties.example` como `local.properties` y completa localmente `sdk.dir`, la URL/clave publicable de Supabase y el OAuth Web Client ID. No añadas una `service_role`, contraseñas, tokens de usuario o secretos OAuth. `local.properties` y cualquier material de firma deben permanecer fuera del control de versiones.

El `app/google-services.json` incluido ya coincide con la app Android objetivo `com.cubapincha.job` y la referencia Firebase DEV revisada; no se sustituyó. No copies aquí una cuenta de servicio Firebase. Para release, obtén el keystore y sus contraseñas desde el gestor seguro del usuario; nunca los pegues en el código o en mensajes. Gradle bloquea una release cuando falta un keystore válido.

## Secuencia para Muse

Primero confirma el JDK que usa Android Studio (21) y sincroniza Gradle. Luego ejecuta `./gradlew :app:assembleDebug` en el PC y conserva el resultado/log para diagnosticar si falla. Instala el APK **desde cero** en un dispositivo y valida el alta del token FCM, permiso de notificaciones en Android 13+, llegada de una notificación y persistencia del registro tras reiniciar.

Revisa después el perfil propio, los ratings públicos y el formulario de “Valora este trabajo” con candidaturas de prueba en estado `hired`. El usuario no autorizó una escritura real de valoración: no envíes una contra sus datos sin permiso explícito. No repitas SQL 006 ni redespliegues Edge v9; el usuario confirma que eso ya está aplicado.

Una vez superadas las pruebas, compila la variante de release con el keystore protegido, verifica firma/paquete/versión y entrega el APK. No distribuyas el artefacto debug como release.

## Documentos de estado

Consulta `BUILD_REPORT.md` para el resumen de cambios y `TEST_LOG.md` para separar revisiones estáticas de pruebas aún pendientes. La guía segura de secretos está en `../CREDENCIALES.md`.
