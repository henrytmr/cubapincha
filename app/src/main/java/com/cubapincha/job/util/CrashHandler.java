package com.cubapincha.job.util;

import android.content.Context;
import android.os.Build;

import com.cubapincha.job.BuildConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Captura el error que cierra la app y lo guarda en un archivo, para
 * mostrarlo al volver a abrirla. Solo se usa para diagnosticar el cierre
 * al arrancar: no envía nada a ningún servidor.
 */
public final class CrashHandler {
    private CrashHandler() {}

    public static final String CRASH_FILE = "cubapincha-crash.txt";

    /** Instala el capturador. Debe ser lo primero en CubaPinchaApp.onCreate. */
    public static void install(Context ctx) {
        final Context appCtx = ctx.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                writeReport(appCtx, thread, error);
            } catch (Exception ignored) { }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    public static File crashFile(Context ctx) {
        return new File(ctx.getFilesDir(), CRASH_FILE);
    }

    public static boolean hasReport(Context ctx) {
        File f = crashFile(ctx);
        return f.exists() && f.length() > 0;
    }

    public static String readReport(Context ctx) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader(crashFile(ctx)))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        } catch (Exception ignored) { }
        return sb.toString();
    }

    public static void clearReport(Context ctx) {
        try {
            crashFile(ctx).delete();
            File ext = ctx.getExternalFilesDir(null);
            if (ext != null) new File(ext, CRASH_FILE).delete();
        } catch (Exception ignored) { }
    }

    private static void writeReport(Context ctx, Thread thread, Throwable error) {
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        StringBuilder report = new StringBuilder();
        report.append("CubaPincha ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        report.append("Android API ").append(Build.VERSION.SDK_INT)
                .append(" (").append(Build.VERSION.RELEASE).append(") ")
                .append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        report.append("Fecha: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date())).append("\n");
        report.append("Hilo: ").append(thread.getName()).append("\n");
        // Estado mínimo (sin valores sensibles): ayuda a localizar el camino.
        // Se pregunta a SessionManager, dueño de esas claves, en vez de
        // leer el archivo de preferencias con claves duplicadas aquí.
        try {
            report.append(com.cubapincha.job.data.SessionManager.get(ctx).crashStateSummary()).append("\n");
        } catch (Exception ignored) { }
        report.append("\n").append(sw.toString());
        String text = report.toString();
        writeTo(new File(ctx.getFilesDir(), CRASH_FILE), text);
        File ext = ctx.getExternalFilesDir(null);
        if (ext != null) writeTo(new File(ext, CRASH_FILE), text);
    }

    private static void writeTo(File file, String text) {
        try (FileWriter w = new FileWriter(file, false)) {
            w.write(text);
        } catch (Exception ignored) { }
    }
}
