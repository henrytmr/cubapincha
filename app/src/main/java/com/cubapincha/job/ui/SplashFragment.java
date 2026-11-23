package com.cubapincha.job.ui;

import android.animation.AnimatorSet;
import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.cubapincha.job.R;
import com.cubapincha.job.data.SessionManager;
import com.cubapincha.job.util.AnimUtils;

/**
 * Entrada de marca de CubaPincha (v8, la definitiva) — el apretón de
 * manos. 3,1 s en cuatro tiempos, sobre noche índigo, con la paleta de
 * la app. La versión definitiva: el apretón es la marca.
 *
 * 1. El encuentro (200–800 ms): la mano izquierda entra desde la
 *    izquierda y la derecha desde la derecha, y se encuentran en el
 *    centro. Dos manos, un trato: el trabajo y la persona se encuentran.
 * 2. El estrechón (800–1600 ms): las manos unidas se estrechan dos
 *    veces (dos apretones de 400 ms: baja rápido, sube despacio). El
 *    apretón es el momento del acuerdo: el trabajo se cierra.
 * 3. El nombre (1600–2600 ms): «CubaPincha» se eleva desde abajo, desde
 *    su máscara (cubic-bezier(.2, .7, .1, 1), como en el HTML de Henry),
 *    y se posa exacto, en dos tonos — «Cuba» en marfil, «Pincha» en cobre.
 * 4. La frase (2200–3100 ms): «Conecta con tu próximo pincha», en
 *    cursiva, se asoma hasta 0.7, como en el HTML.
 * Salida (3100–3400 ms): zoom suave + fundido a la pantalla de roles.
 *
 * El sonido es una campana (tres notas con parciales inarmónicos y cola
 * larga): su nota final cae justo cuando el nombre se posa. Respeta
 * Reducir movimiento (el apretón completo al instante, un solo fundido
 * de 180 ms, 420 ms, sin estrechón ni zoom), no consulta la red y
 * cancela animación y sonido al salir.
 */
public class SplashFragment extends Fragment {
    /** La ceremonia termina a los 3100 ms; la salida (zoom + fundido)
     *  dura 300 ms más y la navegación ocurre a los 3400 ms. */
    private static final long NAVIGATE_MS = 3100L;
    private static final long EXIT_MS = 300L;
    private static final long NAVIGATE_REDUCED_MS = 420L;
    /** Las manos entran desde los lados a los 200 ms y se encuentran
     *  a los 800 ms (600 ms, con desaceleración). */
    private static final long HANDS_START_MS = 200L;
    private static final long HANDS_DURATION_MS = 600L;
    /** El estrechón: dos apretones de 400 ms (800–1600 ms). Cada apretón
     *  baja rápido (150 ms) y sube despacio (250 ms), como un apretón
     *  de verdad. */
    private static final long SHAKE_START_MS = 800L;
    private static final long SHAKE_DURATION_MS = 800L;
    /** La amplitud del apretón: 6 dp hacia abajo. */
    private static final float SHAKE_AMPLITUDE_DP = 6f;
    /** El nombre se eleva desde su máscara a los 1600 ms (1000 ms). */
    private static final long NAME_START_MS = 1600L;
    /** La frase aparece a los 2200 ms (900 ms). */
    private static final long TAGLINE_START_MS = 2200L;
    /** La nota final de la campana suena a los 360 ms del archivo, así
     *  que la campana arranca a los 2240 ms y culmina justo cuando el
     *  nombre se posa (2600 ms). */
    private static final long CHIME_DELAY_MS = 2240L;
    /** La frase se asoma hasta 0.7, como en el HTML. */
    private static final float TAGLINE_ALPHA = 0.7f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AnimatorSet entrance;
    private MediaPlayer chimePlayer;
    private Runnable startChime;
    private final Runnable navigate = () -> {
        if (!isAdded() || getActivity() == null) return;
        MainActivity activity = (MainActivity) getActivity();
        if (SessionManager.get(requireContext()).isLoggedIn()) {
            activity.enterMain();
        } else {
            activity.showFragment(new WelcomeFragment(), false);
        }
    };
    private final Runnable exit = () -> {
        View v = getView();
        if (v == null || !isAdded()) {
            navigate.run();
            return;
        }
        v.findViewById(R.id.splash_root).animate()
                .scaleX(1.05f).scaleY(1.05f).alpha(0f).setDuration(EXIT_MS)
                .setInterpolator(AnimUtils.EASE).start();
        handler.postDelayed(navigate, EXIT_MS);
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_splash, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View root = view.findViewById(R.id.splash_root);
        View markStage = view.findViewById(R.id.mark_stage);
        ImageView handLeft = view.findViewById(R.id.hand_left);
        ImageView handRight = view.findViewById(R.id.hand_right);
        TextView name = view.findViewById(R.id.app_name);
        TextView tagline = view.findViewById(R.id.tagline);
        // El nombre en dos tonos: «Cuba» en marfil, «Pincha» en cobre.
        SpannableString brand = new SpannableString(getString(R.string.app_name));
        brand.setSpan(new ForegroundColorSpan(
                ContextCompat.getColor(requireContext(), R.color.cpCream)),
                0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        brand.setSpan(new ForegroundColorSpan(
                ContextCompat.getColor(requireContext(), R.color.cpCoral)),
                4, brand.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        name.setText(brand);

        if (AnimUtils.reduceMotion(requireContext())) {
            // Movimiento reducido: el apretón completo al instante (la
            // mano izquierda lleva la marca entera), un solo fundido,
            // 420 ms. Sin encuentro, sin estrechón, sin zoom.
            handLeft.setImageResource(R.drawable.ic_cp_handshake_light);
            handRight.setVisibility(View.GONE);
            name.setAlpha(1f);
            name.setTranslationY(0f);
            tagline.setAlpha(TAGLINE_ALPHA);
            root.setAlpha(0f);
            root.animate().alpha(1f).setDuration(180L)
                    .setInterpolator(AnimUtils.EASE).start();
            if (savedInstanceState == null) playIntroChime(0L);
            handler.postDelayed(navigate, NAVIGATE_REDUCED_MS);
            return;
        }

        // Estados iniciales: las manos fuera de la pantalla (cada una
        // en su lado), el nombre escondido bajo su máscara (se coloca
        // tras el layout) y la frase invisible.
        float offScreen = getResources().getDisplayMetrics().widthPixels * 0.6f;
        handLeft.setTranslationX(-offScreen);
        handRight.setTranslationX(offScreen);
        name.setAlpha(1f); // La máscara lo oculta; no necesita fundido.
        tagline.setAlpha(0f);

        // El nombre se esconde bajo su máscara (105% de su altura, como
        // en el HTML) y la coreografía arranca tras el layout.
        name.post(() -> {
            if (getView() == null) return;
            name.setTranslationY(name.getHeight() * 1.05f);
            startEntrance(markStage, handLeft, handRight, name, tagline);
        });
        if (savedInstanceState == null) playIntroChime(CHIME_DELAY_MS);
        handler.postDelayed(exit, NAVIGATE_MS);
    }

    /** Coreografía 0–3100 ms: las manos se encuentran 200–800, el
     *  estrechón 800–1600 (dos apretones), el nombre 1600–2600 (se
     *  eleva desde la máscara y se posa exacto), la frase 2200–3100
     *  (se asoma hasta 0.7). Cada animador lleva su propio
     *  interpolador: el del conjunto los anularía a todos. */
    private void startEntrance(View markStage, View handLeft, View handRight,
                               View name, TextView tagline) {
        // El encuentro: cada mano desliza hasta el centro con
        // desaceleración (llega con decisión y se posa sin rebote).
        ObjectAnimator leftIn = ObjectAnimator.ofFloat(handLeft, View.TRANSLATION_X,
                handLeft.getTranslationX(), 0f);
        ObjectAnimator rightIn = ObjectAnimator.ofFloat(handRight, View.TRANSLATION_X,
                handRight.getTranslationX(), 0f);
        for (ObjectAnimator animator : new ObjectAnimator[]{leftIn, rightIn}) {
            animator.setStartDelay(HANDS_START_MS);
            animator.setDuration(HANDS_DURATION_MS);
            animator.setInterpolator(AnimUtils.EASE);
        }
        // El estrechón: dos apretones sobre el escenario completo (las
        // dos manos juntas). Cada apretón baja rápido (150 ms) y sube
        // despacio (250 ms): 0 → 1 en 0.1875, 1 → 0 en 0.5, y otra vez.
        Keyframe k0 = Keyframe.ofFloat(0f, 0f);
        Keyframe k1 = Keyframe.ofFloat(0.1875f, 1f);
        Keyframe k2 = Keyframe.ofFloat(0.5f, 0f);
        Keyframe k3 = Keyframe.ofFloat(0.6875f, 1f);
        Keyframe k4 = Keyframe.ofFloat(1f, 0f);
        PropertyValuesHolder pump = PropertyValuesHolder.ofKeyframe(
                "shake", k0, k1, k2, k3, k4);
        ValueAnimator shake = ValueAnimator.ofPropertyValuesHolder(pump);
        shake.setStartDelay(SHAKE_START_MS);
        shake.setDuration(SHAKE_DURATION_MS);
        float amplitudePx = SHAKE_AMPLITUDE_DP
                * getResources().getDisplayMetrics().density;
        shake.addUpdateListener(a -> markStage.setTranslationY(
                (float) a.getAnimatedValue("shake") * amplitudePx));
        // El nombre se eleva desde la máscara con la curva del HTML
        // (cubic-bezier(.2, .7, .1, 1)): sube con decisión y se posa.
        ObjectAnimator nameUp = ObjectAnimator.ofFloat(name, View.TRANSLATION_Y,
                name.getTranslationY(), 0f);
        nameUp.setStartDelay(NAME_START_MS);
        nameUp.setDuration(1000L);
        nameUp.setInterpolator(new PathInterpolator(0.2f, 0.7f, 0.1f, 1f));
        ObjectAnimator taglineIn = ObjectAnimator.ofFloat(tagline, View.ALPHA, 0f, TAGLINE_ALPHA);
        taglineIn.setStartDelay(TAGLINE_START_MS);
        taglineIn.setDuration(900L);
        taglineIn.setInterpolator(AnimUtils.EASE);

        entrance = new AnimatorSet();
        entrance.playTogether(leftIn, rightIn, shake, nameUp, taglineIn);
        entrance.start();
    }

    /** El sonido de marca permanece opcional y respeta silencio/vibración.
     *  En movimiento completo arranca con retraso para que su nota final
     *  coincida con el nombre posándose; en movimiento reducido suena de
     *  inmediato. */
    private void playIntroChime(long delayMs) {
        startChime = () -> {
            try {
                Context ctx = requireContext();
                if (!SessionManager.get(ctx).isIntroSoundEnabled()) return;
                AudioManager audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                if (audio != null && audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) return;
                chimePlayer = MediaPlayer.create(ctx, R.raw.cp_chime);
                if (chimePlayer == null) return;
                chimePlayer.setOnCompletionListener(mp -> {
                    mp.release();
                    if (chimePlayer == mp) chimePlayer = null;
                });
                chimePlayer.start();
            } catch (Exception ignored) {
                if (chimePlayer != null) {
                    try { chimePlayer.release(); } catch (Exception ignored2) { /* nada */ }
                    chimePlayer = null;
                }
            }
        };
        if (delayMs <= 0) {
            startChime.run();
        } else {
            handler.postDelayed(startChime, delayMs);
        }
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(navigate);
        handler.removeCallbacks(exit);
        if (startChime != null) handler.removeCallbacks(startChime);
        if (entrance != null) entrance.cancel();
        View v = getView();
        if (v != null) {
            v.findViewById(R.id.splash_root).animate().cancel();
        }
        if (chimePlayer != null) {
            try {
                if (chimePlayer.isPlaying()) chimePlayer.stop();
                chimePlayer.release();
            } catch (Exception ignored) { /* nada */ }
            chimePlayer = null;
        }
        super.onDestroyView();
    }
}
