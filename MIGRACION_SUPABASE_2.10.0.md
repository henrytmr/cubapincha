# Documento histórico — CubaPincha Android 2.10.0

Esta guía corresponde a una versión anterior y queda **supersedida** por la fuente Android DEV 2.11.0. No sigas sus pasos de configuración en pantalla ni sus instrucciones antiguas de FCM, JDK, pruebas o backend.

La fuente 2.11.0 configura Supabase/OAuth desde `local.properties` local al preparar la build y FCM desde `google-services.json`/recursos de Google Services; ya no se piden valores FCM en Perfil. La app contiene la integración cliente de ratings para el contrato Edge v9. El usuario confirma que SQL 006 y Edge v9 ya están aplicados y verificados: **no repetir migración, no redesplegar la Edge Function y no modificar el servidor**.

El código Android está actualizado, pero no se compiló ni se probó en teléfono. No hay APK/AAB nuevo. La compilación final, instalación limpia y validación de JDK 21 corresponden a Muse en el PC del usuario. No se probó una valoración real ni se escribieron datos.

Para el estado vigente, consulta [`BUILD_SETUP.md`](BUILD_SETUP.md), [`BUILD_REPORT.md`](BUILD_REPORT.md), [`TEST_LOG.md`](TEST_LOG.md) y el documento de custodia [`../CREDENCIALES.md`](../CREDENCIALES.md). No guardar ni copiar aquí secretos, `service_role`, claves privadas o contraseñas.
