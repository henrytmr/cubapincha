# CubaPincha — Contrato API Edge/Supabase 2.0.0

Este es el contrato vigente de la aplicación Android 2.11.0. La app autentica con Supabase Auth y envía el JWT en `Authorization: Bearer <jwt>` a `POST https://<proyecto>.supabase.co/functions/v1/api`. La respuesta tiene siempre `{ ok, data, error, requestId }`. `health`, `publicJob`, `listCatalogs` y `getRatings` son públicas; el resto exige JWT.

## Identidad y perfiles

`usuarios.id` es el UID de Supabase Auth. `perfiles_trabajador.id` y `perfiles_empresa.id` son IDs de perfil distintos. Las respuestas de `applications` usan `workerId` y `companyId` como IDs de perfil; el cliente no debe compararlos con `usuarios.id`.

## Acciones principales

Lecturas: `health`, `me`, `jobs`, `job`, `applications`, `notifications`, `sync`, `contactOptions`, `listFilters`, `publicJob`, `listCatalogs`, `getRatings`.

Escrituras: `registerProfile`, `updateProfile`, `createJob`, `publishJob`, `updateJob`, `changeJobStatus`, `archiveJob`, `deleteJob`, `expressInterest`, `sendReminder`, `changeApplicationStatus`, `saveJob`, `saveCandidate`, `saveFilter`, `deleteFilter`, `syncActions`, `contactEvent`, `registerDevice`, `unregisterDevice`, `markNotificationRead`, `report`, `rateApplication` y `logout`.

Las candidaturas admiten `sent`, `reviewing`, `interview`, `hired`, `rejected` y `withdrawn`. Solo una candidatura `hired` puede valorarse.

## Valoraciones

`getRatings` es pública y recibe `{ action: "getRatings", userId, limit?, cursor? }`, donde `userId` es el ID del perfil destinatario (`perfiles_trabajador.id` o `perfiles_empresa.id`). Devuelve `{ userId, average, total, ratings, hasMore, nextCursor }`; cada elemento contiene `ratingId`, `applicationId`, `authorId` (UID de `usuarios`), `authorName`, `jobTitle`, `stars`, `comment` y `createdAt`.

`rateApplication` requiere JWT y recibe `{ action: "rateApplication", applicationId, stars, comment? }`. El servidor verifica que la candidatura pertenece al trabajador o empresa autenticados y está en `hired`; deduce los perfiles autor/destinatario y no acepta IDs de perfil enviados por el cliente. `stars` es entero de 1 a 5, `comment` admite como máximo 1000 caracteres y existe una restricción única por candidatura y autor. Responde `{ ratingId, applicationId, average, total }`.

Errores relevantes: `AUTH_REQUIRED`, `FORBIDDEN_ROLE`, `VALIDATION_ERROR`, `NOT_FOUND`, `CONFLICT`, `RATE_LIMITED` e `INTERNAL_ERROR`. Un duplicado de valoración responde `CONFLICT`.

## FCM y modo offline

FCM usa `google-services.json`; el token real se registra mediante `registerDevice`. Las acciones sincronizables viajan dentro de `syncActions` con `clientActionId`. Las valoraciones no se encolan offline: se envían únicamente con conexión y sesión válida.

## Base de datos

Aplicar `backend/sql/001_esquema.sql` a `005_permisos_service_role.sql` y después `006_valoraciones.sql`. La migración 006 crea `valoraciones`, índices, la unicidad anti-duplicados y RLS; las escrituras de la Edge Function usan `service_role` y la autorización de negocio se ejecuta en `rateApplication`.
