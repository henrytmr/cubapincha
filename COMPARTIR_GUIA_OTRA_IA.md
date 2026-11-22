# Compartir ofertas por enlace — guía para la app 2.10.0

Objetivo: al compartir una oferta, el enlace abre la app si está instalada
y, si no, la página web de la oferta.

## El enlace

Formato único que construye la app al pulsar Compartir en el detalle de
una oferta:

```
https://henrytmr.github.io/cubapincha/oferta.html?id=<jobId>
```

`<jobId>` es el UUID de la oferta (campo `jobId` de la respuesta de
`publicJob`). La página `oferta.html` ya existe en el repo
`henrytmr/cubapincha` y carga la oferta sola desde el endpoint público
(`POST /functions/v1/api`, acción `publicJob`, sin clave ni JWT).

## 1. Intent filter (AndroidManifest.xml)

En la actividad que muestra el detalle de la oferta, añadir dentro de
`<activity>`:

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="https" android:host="henrytmr.github.io"
        android:pathPrefix="/cubapincha/oferta.html" />
</intent-filter>
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="cubapincha" android:host="oferta" />
</intent-filter>
```

`android:autoVerify="true"` es lo que convierte el enlace https en App Link
(abre la app sin preguntar). Para eso el sitio ya sirve
`/.well-known/assetlinks.json` con la huella de producción y la de debug.

## 2. Abrir la oferta al llegar el enlace

En la actividad, en `onCreate` y en `onNewIntent`, leer el enlace:

- Enlace web: `getIntent().getData()` →
  `https://henrytmr.github.io/cubapincha/oferta.html?id=<jobId>`.
  El id sale de `Uri.getQueryParameter("id")`.
- Enlace de la app: `cubapincha://oferta/<jobId>`.
  El id es el último segmento de la ruta (`Uri.getLastPathSegment()`).

Con ese id, cargar el detalle como siempre (acción `publicJob`).
Si la oferta ya no existe, mostrar "Esta oferta ya no está disponible"
y no cerrar la app de golpe.

## 3. Botón Compartir

En el detalle de la oferta, el botón Compartir construye el enlace:

```
https://henrytmr.github.io/cubapincha/oferta.html?id=<jobId>
```

y lo comparte con un `Intent.ACTION_SEND` de texto. Texto sugerido:

```
<titulo de la oferta> — <empresa>
<enlace>
```

## 4. Verificación

1. Instalar la versión debug en el teléfono y tocar un enlace compartido:
   debe abrir la app en el detalle de la oferta.
2. Con la app desinstalada, el mismo enlace debe abrir `oferta.html`
   en el navegador y mostrar la oferta.
3. El botón "Abrir en la app" de la página web debe abrir la app
   instalada (`cubapincha://oferta/<jobId>`).

Nota: el enlace web solo funciona con la huella registrada en
`assetlinks.json`. En la versión release hay que usar la huella de
producción (ya incluida); en debug, la de debug (también incluida).
