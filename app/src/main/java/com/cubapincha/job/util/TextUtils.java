package com.cubapincha.job.util;

import java.text.Normalizer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Utilidades de texto: normalización sin acentos, fechas relativas. */
public final class TextUtils {
    private TextUtils() {}

    /** Normaliza texto para búsqueda: minúsculas, sin acentos ni signos. */
    public static String normalize(String input) {
        if (input == null) return "";
        String n = Normalizer.normalize(input, Normalizer.Form.NFD);
        n = n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }

    public static boolean matchesQuery(String haystack, String query) {
        String q = normalize(query);
        if (q.isEmpty()) return true;
        return normalize(haystack).contains(q);
    }

    /** Formato ISO reutilizado por hilo (SimpleDateFormat no es seguro entre hilos). */
    private static final ThreadLocal<SimpleDateFormat> ISO_FORMAT = ThreadLocal.withInitial(() -> {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f;
    });

    /** Limpia un ISO para parsearlo: quita Z, milisegundos y el offset (+00:00). */
    private static String cleanIso(String iso) {
        String clean = iso.replace("Z", "").split("\\.")[0];
        // Offset final tipo +00:00 o -05:30 (después de la hora).
        int t = clean.indexOf('T');
        int plus = clean.indexOf('+', t > 0 ? t : 0);
        int minus = clean.indexOf('-', t > 0 ? t + 1 : 0);
        int cut = plus > 0 ? plus : minus;
        if (cut > 0 && cut > t) clean = clean.substring(0, cut);
        return clean;
    }

    private static Date parseIso(String iso) {
        try {
            return ISO_FORMAT.get().parse(cleanIso(iso));
        } catch (ParseException e) {
            return null;
        }
    }

    /** "Hace 2 días", "Ayer", "Hoy" a partir de ISO 8601. */
    public static String timeAgo(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        Date date = parseIso(iso);
        if (date == null) return "";
        long diff = System.currentTimeMillis() - date.getTime();
        if (diff < 0) diff = 0; // fecha futura: se trata como hoy
        long days = diff / (24L * 60 * 60 * 1000);
        if (days <= 0) return "Hoy";
        if (days == 1) return "Ayer";
        if (days < 7) return "Hace " + days + " días";
        long weeks = days / 7;
        if (weeks == 1) return "Hace 1 semana";
        return "Hace " + weeks + " semanas";
    }

    /** Días completos desde una fecha ISO (-1 si no se puede interpretar). */
    public static long daysSince(String iso) {
        if (iso == null || iso.isEmpty()) return -1;
        Date date = parseIso(iso);
        if (date == null) return -1;
        return (System.currentTimeMillis() - date.getTime()) / (24L * 60 * 60 * 1000);
    }

    public static String nowIso() {
        // Con milisegundos: dos acciones encoladas en el mismo segundo
        // empataban en createdAt y el orden de la cola no estaba garantizado.
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    /** ISO de hace N días (para datos semilla). */
    public static String daysAgoIso(int days) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000));
    }

    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "CP";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toUpperCase(parts[0].charAt(0)));
        if (parts.length > 1) sb.append(Character.toUpperCase(parts[parts.length - 1].charAt(0)));
        return sb.toString();
    }

    public static String joinComma(java.util.List<String> items) {
        if (items == null || items.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    public static java.util.List<String> splitComma(String text) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null) return out;
        for (String part : text.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
