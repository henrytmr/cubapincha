package com.cubapincha.job.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Carga imágenes públicas del bucket sin añadir una dependencia pesada. */
public final class NetworkImageLoader {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private NetworkImageLoader() { }

    public static void load(ImageView target, String url) {
        if (target == null) return;
        target.setTag(url);
        if (url == null || url.trim().isEmpty()) return;
        EXECUTOR.execute(() -> {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();
                if (connection.getResponseCode() >= 200 && connection.getResponseCode() < 300) {
                    try (InputStream input = connection.getInputStream()) {
                        bitmap = BitmapFactory.decodeStream(input);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
            Bitmap result = bitmap;
            MAIN.post(() -> {
                if (result != null && url.equals(target.getTag())) target.setImageBitmap(result);
            });
        });
    }
}
