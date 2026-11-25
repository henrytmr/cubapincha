# CubaPincha 2.11.5 — registro de pruebas

## Comprobaciones estáticas
- [x] `versionName 2.11.5` y `versionCode 30`.
- [x] Servidor v25 no modificado.
- [x] Ojito persistente y siempre visible en Inicio, Buscar, Panel empresa y Buscar trabajadores.
- [x] Botón Descartar retirado visualmente; descarte por gesto conservado.
- [x] Invitación, retiro con oferta de respaldo y WhatsApp con mensaje prellenado revisados.
- [x] Aviso de vencimiento en menos de 24 horas incorporado en ofertas de trabajador y empresa.
- [x] `git diff --check` y parseo XML sin errores.
- [x] No se incluyen APK, keystore ni `local.properties`.

## Pendiente de build y dispositivo
- [ ] Crear commit final y publicar el mismo SHA en `origin` y `codemagic`.
- [ ] Iniciar un único build firmado seleccionando rama, workflow y SHA exactos.
- [ ] Descargar y validar `app-release.apk` con `file`, `unzip -tq`, SHA-256 y Androguard.
- [ ] Prueba en teléfono Android físico, ambos roles y claro/oscuro.
