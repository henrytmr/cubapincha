package com.cubapincha.job.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Sincronización inteligente en segundo plano: envía la cola offline y
 *  descarga los cambios (ofertas, candidaturas, notificaciones) con el
 *  cursor incremental del servidor, y los guarda en la caché local. Así
 *  las pantallas pintan al instante desde el teléfono y la red solo
 *  actualiza lo que cambió. No hace nada en modo demo, sin sesión o con
 *  el ahorro de datos activado (ahí la sincronización es solo manual). */
public class SyncWorker extends Worker {
    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SessionManager session = SessionManager.get(ctx);
        if (session.isDemoMode() || !session.isLoggedIn() || session.isDataSaver()) {
            return Result.success();
        }
        ApiResult<String> r = Repository.get(ctx).syncNowSync();
        if (r.ok) return Result.success();
        if ("NO_CONNECTION".equals(r.errorCode)) return Result.retry();
        // Un fallo del servidor no debe reintentar en bucle: el próximo
        // turno periódico lo intentará de nuevo.
        return Result.success();
    }
}
