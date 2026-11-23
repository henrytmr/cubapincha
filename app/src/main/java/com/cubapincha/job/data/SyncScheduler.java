package com.cubapincha.job.data;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Programa la sincronización en segundo plano (WorkManager): un turno
 *  periódico cada 6 horas y un turno inmediato al abrir la app o al
 *  entrar. Solo con conexión y batería suficiente; Android decide el
 *  momento exacto dentro de la ventana para cuidar la batería. */
public final class SyncScheduler {
    private static final String PERIODIC_NAME = "cubapincha-sync";
    private static final String SOON_NAME = "cubapincha-sync-soon";

    private SyncScheduler() { }

    /** Turno periódico: cada 6 horas (ventana flexible de 1 hora). */
    public static void schedulePeriodic(Context ctx) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SyncWorker.class, 6, TimeUnit.HOURS, 1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /** Un turno lo antes posible (al abrir la app o al iniciar sesión). */
    public static void syncSoon(Context ctx) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(ctx).enqueueUniqueWork(
                SOON_NAME, ExistingWorkPolicy.REPLACE, request);
    }
}
