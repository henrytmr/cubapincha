package com.cubapincha.job.util;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.data.SessionManager;

/** Push FCM inicializado desde los recursos generados por Google Services. */
public final class PushManager {
    private PushManager() {}

    /** Asegura que exista la app Firebase por defecto, sin valores manuales de Perfil. */
    public static boolean initIfNeeded(Context ctx) {
        if (ctx == null) return false;
        Context app = ctx.getApplicationContext();
        try {
            try {
                FirebaseApp.getInstance();
                return true;
            } catch (IllegalStateException notInitialized) {
                // El plugin Google Services genera los recursos que lee esta llamada.
                return FirebaseApp.initializeApp(app) != null;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Estado real de la configuración incluida en la app, no de preferencias legacy. */
    public static boolean isConfigured(Context ctx) {
        return initIfNeeded(ctx);
    }

    /** Pide el token FCM actual y lo registra en el backend (modo conectado).
     *  Best-effort: si falla por red o servicios Google, se reintenta al abrir
     *  la app o al entrar. */
    public static void refreshToken(Context ctx) {
        if (!initIfNeeded(ctx)) return;
        Context app = ctx.getApplicationContext();
        try {
            FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(token -> {
                        if (token == null || token.isEmpty()) return;
                        SessionManager.get(app).setFcmToken(token);
                        Repository repo = Repository.get(app);
                        if (!repo.isDemoMode() && SessionManager.get(app).isLoggedIn()) {
                            repo.registerPushToken(token, r -> { });
                        }
                    })
                    .addOnFailureListener(e -> { /* reintento al abrir o al iniciar sesión */ });
        } catch (Exception ignored) { }
    }
}
