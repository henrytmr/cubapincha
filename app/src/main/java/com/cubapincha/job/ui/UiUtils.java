package com.cubapincha.job.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.cubapincha.job.R;

/** Pequeñas ayudas compartidas por los fragmentos. */
public final class UiUtils {
    private UiUtils() {}
    private static final String PHOTO_PREFS = "cubapincha_photo_visibility";
    public static boolean getPhotosVisible(Context ctx, String screen) {
        return ctx.getSharedPreferences(PHOTO_PREFS, Context.MODE_PRIVATE).getBoolean(screen, false);
    }
    public static void setPhotosVisible(Context ctx, String screen, boolean visible) {
        ctx.getSharedPreferences(PHOTO_PREFS, Context.MODE_PRIVATE).edit().putBoolean(screen, visible).apply();
    }

    public static void snackbar(View view, String message) {
        if (view != null) Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
    }

    public static void snackbar(View view, int resId) {
        if (view != null) Snackbar.make(view, resId, Snackbar.LENGTH_LONG).show();
    }

    /** Clic con guarda antidoble toque: un segundo toque antes de 350 ms se
     *  ignora. Evita apilar dos veces el mismo destino o duplicar acciones
     *  (duplicar una oferta, cambiar dos veces el estado de un candidato). */
    public static void setDebouncedClickListener(View view, View.OnClickListener listener) {
        view.setOnClickListener(v -> {
            Object last = view.getTag(R.id.last_tap_tag);
            long now = System.currentTimeMillis();
            if (last instanceof Long && now - (Long) last < 350) return;
            view.setTag(R.id.last_tap_tag, now);
            listener.onClick(v);
        });
    }

    public static void hideKeyboard(View view) {
        if (view == null) return;
        try {
            InputMethodManager imm = (InputMethodManager) view.getContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        } catch (Exception ignored) { }
    }

    public static void setupDropdown(AutoCompleteTextView view, String[] options) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(view.getContext(),
                android.R.layout.simple_dropdown_item_1line, options);
        view.setAdapter(adapter);
    }

    public static String text(android.widget.EditText edit) {
        return edit.getText() != null ? edit.getText().toString().trim() : "";
    }

    public static Integer parseIntOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Abre WhatsApp con un número y un mensaje prellenado. Devuelve false
     *  si no hay ninguna app que pueda abrir el enlace (sin WhatsApp ni
     *  navegador). El número se normaliza a solo dígitos para wa.me. */
    public static boolean openWhatsApp(Context ctx, String phone, String message) {
        if (phone == null || phone.replaceAll("[^0-9]", "").isEmpty()) return false;
        String url = "https://wa.me/" + phone.replaceAll("[^0-9]", "")
                + "?text=" + Uri.encode(message != null ? message : "");
        try {
            ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            return true;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(ctx, R.string.whatsapp_unavailable, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    public static boolean isValidEmail(String email) {
        return email != null && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    /** Resuelve un atributo de tema (?attr/...) a su color actual. */
    public static int resolveThemeColor(Context ctx, int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        if (ctx.getTheme().resolveAttribute(attr, tv, true) && tv.type >= 28 && tv.type <= 31) {
            return tv.data;
        }
        if (tv.resourceId != 0) {
            return ContextCompat.getColor(ctx, tv.resourceId);
        }
        return tv.data;
    }

    /**
     * Estado de éxito de un botón primario (§6): menta + check, SOLO después
     * de completar la operación. Devuelve un Runnable que restaura el botón
     * a su estado original (útil si la pantalla sigue visible).
     */
    public static Runnable buttonSuccess(MaterialButton button, String successText) {
        if (button == null) return () -> {};
        Context ctx = button.getContext();
        ColorStateList prevTint = button.getBackgroundTintList();
        android.graphics.drawable.Drawable prevIcon = button.getIcon();
        ColorStateList prevIconTint = button.getIconTint();
        CharSequence prevText = button.getText();
        ColorStateList prevTextColor = button.getTextColors();
        boolean prevEnabled = button.isEnabled();
        button.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(ctx, R.color.cpMint)));
        button.setIconResource(R.drawable.ic_check_circle);
        // Tinta oscura sobre menta: el blanco sobre menta era casi ilegible.
        int ink = ContextCompat.getColor(ctx, R.color.cpInk);
        button.setIconTint(ColorStateList.valueOf(ink));
        button.setTextColor(ink);
        button.setText(successText);
        button.setEnabled(false);
        return () -> {
            button.setBackgroundTintList(prevTint);
            button.setIcon(prevIcon);
            button.setIconTint(prevIconTint);
            button.setText(prevText);
            button.setTextColor(prevTextColor);
            button.setEnabled(prevEnabled);
        };
    }
}
