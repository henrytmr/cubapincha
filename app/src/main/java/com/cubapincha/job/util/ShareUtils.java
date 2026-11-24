package com.cubapincha.job.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.cubapincha.job.R;
import com.cubapincha.job.model.Job;
import com.cubapincha.job.model.RecommendedWorker;

/**
 * Compartir ofertas con enlace canónico estable.
 * URL pública: https://henrytmr.github.io/cubapincha/oferta.html?id={jobId} — abre la app si está
 * instalada (deep link) y la ficha web pública si no. El enlace nunca
 * incluye teléfonos privados, CV ni notas internas.
 */
public final class ShareUtils {
    private ShareUtils() {}

    public static final String WEB_BASE = "https://henrytmr.github.io/cubapincha";
    public static final String DEEP_LINK_SCHEME = "cubapincha://oferta/";

    public static String jobUrl(String jobId) {
        return WEB_BASE + "/oferta.html?id=" + jobId;
    }

    public static String jobDeepLink(String jobId) {
        return DEEP_LINK_SCHEME + jobId;
    }
    public static String workerUrl(String workerId) { return WEB_BASE + "/trabajador.html?id=" + workerId; }
    public static String workerDeepLink(String workerId) { return "cubapincha://trabajador/" + workerId; }
    public static String shareText(RecommendedWorker worker) {
        return worker.workerName + " · " + (worker.headline == null ? "Perfil profesional" : worker.headline)
                + " (" + worker.locationText() + ")\n" + workerUrl(worker.workerId) + "\nVía CubaPincha";
    }
    public static void shareWorkerSystem(Context ctx, RecommendedWorker worker) {
        Intent share = new Intent(Intent.ACTION_SEND); share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, shareText(worker));
        ctx.startActivity(Intent.createChooser(share, ctx.getString(R.string.share)));
    }
    public static void shareWorkerWhatsApp(Context ctx, RecommendedWorker worker) {
        openExternal(ctx, "https://wa.me/?text=" + Uri.encode(shareText(worker)));
    }

    /** Texto corto para compartir: título, empresa, municipio, salario, enlace. */
    public static String shareText(Job job) {
        return job.title + " en " + job.companyName
                + " (" + job.locationText() + ") · " + job.salaryText()
                + "\n" + jobUrl(job.jobId)
                + "\nVía CubaPincha";
    }

    public static void shareSystem(Context ctx, Job job) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, shareText(job));
        ctx.startActivity(Intent.createChooser(share, ctx.getString(R.string.share)));
    }

    /** Copia el enlace canónico al portapapeles. */
    public static void copyLink(Context ctx, Job job) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("CubaPincha", jobUrl(job.jobId)));
        }
    }

    /** WhatsApp sin número fijo: abre el selector de contactos con el texto listo. */
    public static void shareWhatsApp(Context ctx, Job job) {
        openExternal(ctx, "https://wa.me/?text=" + Uri.encode(shareText(job)));
    }

    public static void shareTelegram(Context ctx, Job job) {
        openExternal(ctx, "https://t.me/share/url?url=" + Uri.encode(jobUrl(job.jobId))
                + "&text=" + Uri.encode(job.title + " en " + job.companyName));
    }

    public static void shareEmail(Context ctx, Job job) {
        Intent mail = new Intent(Intent.ACTION_SENDTO);
        mail.setData(Uri.parse("mailto:"));
        mail.putExtra(Intent.EXTRA_SUBJECT, "Oferta en CubaPincha: " + job.title);
        mail.putExtra(Intent.EXTRA_TEXT, shareText(job));
        try {
            ctx.startActivity(Intent.createChooser(mail, ctx.getString(R.string.share_via_email)));
        } catch (Exception ignored) { }
    }

    private static void openExternal(Context ctx, String uri) {
        try {
            ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
        } catch (Exception ignored) { }
    }
}
