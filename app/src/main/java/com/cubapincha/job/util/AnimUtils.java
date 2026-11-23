package com.cubapincha.job.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;

import com.cubapincha.job.R;

import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

/**
 * UN SOLO sistema de movimiento para toda la app (sincronía y armonía).
 * Tokens del prompt maestro 2.1 (§9): microinteracción 160 ms, acción
 * estándar 220 ms, stagger 40–60 ms entre elementos, transición de
 * pantalla 280–320 ms (300 ms), énfasis 320 ms (hero, ruta y estados
 * de éxito), instant 0 ms con "Reducir movimiento".
 *
 * Curvas: EASE (FastOutSlowIn) para entradas y transiciones estándar;
 * EASE_EMPHASIZED (cubic-bezier 0.2, 0, 0, 1) para hero, énfasis y
 * gestos de marca. El movimiento representa conexión y progreso:
 * sin rebotes, giros ni decoración sin significado.
 */
public final class AnimUtils {
    private AnimUtils() {}

    public static final long DURATION_FAST = 160L;
    public static final long DURATION_SHORT = 180L;
    public static final long DURATION_MEDIUM = 220L;
    public static final long DURATION_LONG = 240L;
    public static final long DURATION_EMPHASIS = 320L;
    /** Nombres del sistema §9 (alias de los valores de arriba). */
    public static final long DURATION_MICRO = DURATION_FAST;
    public static final long DURATION_ACTION = DURATION_MEDIUM;
    public static final long DURATION_SCREEN = 300L;
    public static final long DURATION_STAGGER_STEP = 60L;

    /** Curva única: salida rápida, llegada suave (Material emphasized). */
    public static final FastOutSlowInInterpolator EASE = new FastOutSlowInInterpolator();
    public static final PathInterpolator EASE_EMPHASIZED =
            new PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f);

    /** ¿El usuario pidió reducir el movimiento? */
    public static boolean reduceMotion(Context ctx) {
        try {
            float scale = Settings.Global.getFloat(
                    ctx.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            if (scale == 0f) return true;
        } catch (Exception ignored) { }
        return false;
    }

    /** Entrada suave: fundido + subida ligera, la misma en toda la app.
     *  Cancela antes cualquier animador en curso sobre la vista: sin esto,
     *  dos animaciones solapadas pueden dejar la vista en alpha 0. */
    public static void fadeSlideIn(View view, long delayMs) {
        if (view == null) return;
        Context ctx = view.getContext();
        view.animate().cancel();
        if (reduceMotion(ctx)) {
            view.setAlpha(1f);
            view.setTranslationY(0f);
            view.setVisibility(View.VISIBLE);
            return;
        }
        view.setAlpha(0f);
        view.setTranslationY(16f);
        view.setVisibility(View.VISIBLE);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(DURATION_MEDIUM)
                .setStartDelay(delayMs)
                .setInterpolator(EASE_EMPHASIZED)
                .start();
    }

    /** Entrada suave hacia un alpha final concreto (p. ej. notificaciones leídas). */
    public static void fadeSlideIn(View view, long delayMs, float targetAlpha) {
        if (view == null) return;
        Context ctx = view.getContext();
        view.animate().cancel();
        if (reduceMotion(ctx)) {
            view.setAlpha(targetAlpha);
            view.setTranslationY(0f);
            view.setVisibility(View.VISIBLE);
            return;
        }
        view.setAlpha(0f);
        view.setTranslationY(16f);
        view.setVisibility(View.VISIBLE);
        view.animate()
                .alpha(targetAlpha)
                .translationY(0f)
                .setDuration(DURATION_MEDIUM)
                .setStartDelay(delayMs)
                .setInterpolator(EASE_EMPHASIZED)
                .start();
    }

    /**
     * Restablece el estado visual de un ítem de lista a un estado conocido y
     * visible. DEBE llamarse al inicio de cada bind() y en onViewRecycled():
     * una vista reciclada puede llegar con alpha 0 o desplazada si su
     * animación de entrada fue interrumpida (p. ej. al navegar fuera de la
     * lista a mitad de la animación). Sin este restablecimiento, la tarjeta
     * reaparece invisible: "la tarjeta desaparece".
     */
    public static void resetItemState(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.setAlpha(1f);
        view.setTranslationY(0f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setVisibility(View.VISIBLE);
    }

    /**
     * Fundido elegante y tranquilo para el splash: aparece despacio con una
     * escala casi imperceptible (0.97 → 1.0). Sin rebotes ni giros.
     */
    public static void elegantReveal(View view, long delayMs, long durationMs) {
        if (view == null) return;
        Context ctx = view.getContext();
        view.animate().cancel();
        if (reduceMotion(ctx)) {
            view.setAlpha(1f);
            view.setScaleX(1f);
            view.setScaleY(1f);
            view.setVisibility(View.VISIBLE);
            return;
        }
        view.setAlpha(0f);
        view.setScaleX(0.97f);
        view.setScaleY(0.97f);
        view.setVisibility(View.VISIBLE);
        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(durationMs)
                .setStartDelay(delayMs)
                .setInterpolator(EASE_EMPHASIZED)
                .start();
    }

    /** Fundido lento y simple (para el tagline del splash). */
    public static void elegantFade(View view, long delayMs, long durationMs) {
        if (view == null) return;
        Context ctx = view.getContext();
        view.animate().cancel();
        if (reduceMotion(ctx)) {
            view.setAlpha(1f);
            view.setVisibility(View.VISIBLE);
            return;
        }
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate()
                .alpha(1f)
                .setDuration(durationMs)
                .setStartDelay(delayMs)
                .setInterpolator(EASE_EMPHASIZED)
                .start();
    }

    public static void fadeSlideIn(View view) {
        fadeSlideIn(view, 0);
    }

    /** Entrada escalonada de una lista de vistas (misma curva, mismo ritmo). */
    public static void staggerIn(View... views) {
        long delay = 0;
        for (View v : views) {
            if (v == null) continue;
            fadeSlideIn(v, delay);
            delay += 60;
        }
    }

    /** Entrada con muelle suave (Material 3 Expressive): la vista sube 18dp
     *  con un leve sobreimpulso (0.94 → 1.0) y aparece. Una sola vez, sin
     *  rebotes constantes. Con Reducir movimiento, aparece de inmediato. */
    public static void springIn(View view, long delayMs) {
        if (view == null) return;
        if (reduceMotion(view.getContext())) {
            view.setAlpha(1f);
            view.setScaleX(1f);
            view.setScaleY(1f);
            view.setTranslationY(0f);
            return;
        }
        view.animate().cancel();
        view.setAlpha(0f);
        view.setScaleX(0.94f);
        view.setScaleY(0.94f);
        view.setTranslationY(18f * view.getResources().getDisplayMetrics().density);
        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setStartDelay(delayMs)
                .setDuration(DURATION_EMPHASIS)
                .setInterpolator(new android.view.animation.OvershootInterpolator(0.7f))
                .start();
    }

    /** Un conector vertical "se dibuja" de arriba abajo (escala Y desde el
     *  borde superior) hasta su opacidad destino. */
    public static void drawDown(View view, long delayMs, float targetAlpha) {
        if (view == null) return;
        if (reduceMotion(view.getContext())) {
            view.setAlpha(targetAlpha);
            view.setScaleY(1f);
            return;
        }
        view.animate().cancel();
        view.setPivotY(0f);
        view.setScaleY(0f);
        view.setAlpha(0f);
        view.animate()
                .scaleY(1f)
                .alpha(targetAlpha)
                .setStartDelay(delayMs)
                .setDuration(DURATION_SCREEN)
                .setInterpolator(EASE_EMPHASIZED)
                .start();
    }

    /** Chip de filtro aplicado (§9): aparece con escala 0.96 → 1.0. */
    public static void chipPop(View view) {
        if (view == null || reduceMotion(view.getContext())) return;
        view.animate().cancel();
        view.setScaleX(0.96f);
        view.setScaleY(0.96f);
        view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(DURATION_ACTION)
                .setInterpolator(EASE_EMPHASIZED)
                .start();
    }

    /** Microanimación de "pop" al pulsar (guardar, me gusta, seleccionar). */
    public static void pop(View view) {
        if (view == null || reduceMotion(view.getContext())) return;
        view.animate().cancel();
        view.setScaleX(0.85f);
        view.setScaleY(0.85f);
        view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(DURATION_MEDIUM)
                .setInterpolator(EASE_EMPHASIZED)
                .start();
    }

    /**
     * Pulsación elegante para tarjetas y botones principales: la vista
     * respira apenas (0.97, un leve fundido) y vuelve con calma; la acción
     * se ejecuta al final del gesto, sin rebotes ni saltos. Si el sistema
     * pidió reducir el movimiento, la acción corre de inmediato.
     */
    /** Guarda antidoble toque compartida (350 ms, como en UiUtils). */
    private static boolean isDoublePress(View view) {
        Object last = view.getTag(R.id.last_tap_tag);
        long now = System.currentTimeMillis();
        if (last instanceof Long && now - (Long) last < 350) return true;
        view.setTag(R.id.last_tap_tag, now);
        return false;
    }

    public static void elegantPress(View view, Runnable onPressed) {
        if (view == null) {
            if (onPressed != null) onPressed.run();
            return;
        }
        if (isDoublePress(view)) return;
        if (reduceMotion(view.getContext())) {
            if (onPressed != null) onPressed.run();
            return;
        }
        view.animate().cancel();
        view.animate()
                .scaleX(0.97f)
                .scaleY(0.97f)
                .alpha(0.88f)
                .setDuration(120L)
                .setInterpolator(EASE_EMPHASIZED)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .alpha(1f)
                                .setDuration(DURATION_SHORT)
                                .setInterpolator(EASE_EMPHASIZED)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator a) {
                                        if (onPressed != null) onPressed.run();
                                    }
                                })
                                .start();
                    }
                })
                .start();
    }

    /** Pulso suave y continuo para skeletons de carga. */
    public static ObjectAnimator pulse(View view) {
        if (view == null) return null;
        stopPulse(view);
        if (reduceMotion(view.getContext())) return null;
        ObjectAnimator anim = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.45f, 1f);
        anim.setDuration(1200L);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(EASE);
        view.setTag(R.id.pulse_animator_tag, anim);
        anim.start();
        return anim;
    }

    /** Detiene el pulso de un skeleton (evita animadores infinitos). */
    public static void stopPulse(View view) {
        if (view == null) return;
        Object tag = view.getTag(R.id.pulse_animator_tag);
        if (tag instanceof ObjectAnimator) {
            ((ObjectAnimator) tag).cancel();
            view.setTag(R.id.pulse_animator_tag, null);
        }
        view.setAlpha(1f);
    }

    /** Reproduce un AnimatedVectorDrawable (icono animado) si lo es. */
    public static void playAnimated(ImageView view) {
        if (view == null) return;
        Drawable d = view.getDrawable();
        if (d instanceof Animatable) {
            ((Animatable) d).start();
        }
    }

    /** Vibración háptica suave (10 ms) al confirmar una acción. */
    public static void hapticTick(View view) {
        if (view == null) return;
        try {
            Context ctx = view.getContext();
            Vibrator vibrator = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(12);
            }
        } catch (Exception ignored) { }
    }

    /** Desvanecer y ocultar con la curva única. */
    public static void fadeOut(View view, Runnable onEnd) {
        if (view == null) return;
        view.animate().cancel();
        if (reduceMotion(view.getContext())) {
            view.setVisibility(View.GONE);
            if (onEnd != null) onEnd.run();
            return;
        }
        view.animate()
                .alpha(0f)
                .setDuration(DURATION_SHORT)
                .setInterpolator(EASE)
                .setListener(new AnimatorListenerAdapter() {
                    private boolean done;
                    private void finish() {
                        if (done) return;
                        done = true;
                        view.setVisibility(View.GONE);
                        view.setAlpha(1f);
                        if (onEnd != null) onEnd.run();
                    }
                    @Override
                    public void onAnimationEnd(Animator animation) { finish(); }
                    @Override
                    public void onAnimationCancel(Animator animation) { finish(); }
                })
                .start();
    }
}
