package com.cubapincha.job.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Comprime una imagen seleccionada en los dos tamaños exactos del contrato de fotos. */
public final class PhotoPayload {
    public final String detailBase64;
    public final String thumbBase64;

    private PhotoPayload(String detailBase64, String thumbBase64) {
        this.detailBase64 = detailBase64;
        this.thumbBase64 = thumbBase64;
    }

    public static PhotoPayload fromUri(Context context, Uri uri) throws Exception {
        Bitmap source;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            source = BitmapFactory.decodeStream(in);
        }
        if (source == null) throw new IllegalArgumentException("No se pudo leer la imagen.");
        try {
            byte[] detail = encodeWithin(source, 1200, 200 * 1024);
            byte[] thumb = encodeWithin(source, 400, 50 * 1024);
            return new PhotoPayload(Base64.encodeToString(detail, Base64.NO_WRAP),
                    Base64.encodeToString(thumb, Base64.NO_WRAP));
        } finally {
            source.recycle();
        }
    }

    private static byte[] encodeWithin(Bitmap source, int maxWidth, int maxBytes) throws Exception {
        float scale = Math.min(1f, maxWidth / (float) Math.max(1, source.getWidth()));
        int width = Math.max(1, Math.round(source.getWidth() * scale));
        int height = Math.max(1, Math.round(source.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(source, width, height, true);
        try {
            for (int quality = 88; quality >= 25; quality -= 7) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(maxBytes);
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out);
                byte[] bytes = out.toByteArray();
                if (bytes.length <= maxBytes) return bytes;
            }
            throw new IllegalArgumentException("La imagen no se pudo comprimir al tamaño permitido.");
        } finally {
            if (scaled != source) scaled.recycle();
        }
    }

    private PhotoPayload() { detailBase64 = ""; thumbBase64 = ""; }
}
