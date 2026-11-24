# Registro de pruebas — CubaPincha 2.11.3

## Validaciones ejecutadas

- Rama creada desde la etiqueta segura `cubapincha-2.11.2-segura`, commit base `355ceea5e42da86af92276d670496cd9c87e5dda`.
- `git diff --check`: correcto.
- Parseo de todos los XML de `app/src/main/res`: correcto.
- Balance de llaves Java en los archivos modificados: correcto.
- Verificación de referencias nuevas: `input_min_match`, ojitos de Buscar/Inicio/empresa, `btn_clear_cache` y deep link de trabajador presentes en código y layouts.
- El contrato v25 de `invited`, `invitedOfferId`, `uninviteWorker` y `publicWorker` quedó implementado solo en el cliente Android.

## Compilación

- `./gradlew :app:assembleRelease` local: no ejecutable en este sandbox porque no existe Android SDK; el error exacto fue `SDK location not found`.
- La validación de compilación release firmada queda asignada a Codemagic workflow `android-release`, después de publicar la rama y el commit exactos.

## No ejecutado

- [ ] Build firmado en Codemagic y descarga de `app-release.apk`.
- [ ] Instalación y prueba física en teléfono.
- [ ] Pruebas runtime con cuenta real: invitación/deshacer, filtro empresarial, compartir perfil y deep link, conexión WhatsApp, guardado offline, caché offline, borrado de caché, edición al 100%, estados de candidatura y avisos de vencimiento.

La prueba física final corresponde al teléfono del usuario; esta bitácora no la presenta como realizada.
