package com.cubapincha.job;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;
import com.cubapincha.job.data.CubaCatalog;
import com.cubapincha.job.data.SessionManager;
import com.cubapincha.job.data.SyncScheduler;
import com.cubapincha.job.util.CrashHandler;
import com.cubapincha.job.util.PushManager;

/** Application: activa Material You (color dinámico) cuando el sistema lo ofrece. */
public class CubaPinchaApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Primero el capturador de errores: si la app se cierra, el motivo
        // queda guardado para mostrarlo al reabrir.
        CrashHandler.install(this);
        DynamicColors.applyToActivitiesIfAvailable(this);
        // Correcciones del catálogo de municipios recibidas del backend.
        CubaCatalog.loadOverrides(this);
        // Modo de tema ANTES de que ninguna actividad infle su contenido:
        // aplicarlo después causa un parpadeo de tema y un reinflado.
        applyThemeMode(SessionManager.get(this).getThemeMode());
        // "Recordarme" sin marcar = sesión solo para esta ejecución: al abrir
        // la app de nuevo (nuevo proceso), la sesión se cierra.
        SessionManager session = SessionManager.get(this);
        if (session.isLoggedIn() && !session.isRemember()) {
            session.logout();
        }
        // Push reales (FCM): inicializar Firebase con la configuración de
        // Perfil y refrescar el token (no-op si no está configurado).
        PushManager.initIfNeeded(this);
        PushManager.refreshToken(this);
        // Sincronización en segundo plano: turno periódico cada 6 horas y
        // un turno inmediato al abrir (no-op en demo, sin sesión o con
        // ahorro de datos).
        SyncScheduler.schedulePeriodic(this);
        SyncScheduler.syncSoon(this);
    }

    /** Aplica el tema elegido: system (seguir el sistema), light o dark. */
    public static void applyThemeMode(String mode) {
        if ("dark".equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if ("light".equals(mode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}
