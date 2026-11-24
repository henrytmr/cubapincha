package com.cubapincha.job.util;

import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

/** Revela una foto pública al completar un gesto horizontal hacia la izquierda. */
public final class PhotoSwipeBinder {
    private PhotoSwipeBinder() { }

    public static void bind(View card, ImageView photo, ImageButton eye, String thumbUrl) {
        if (card == null || photo == null || eye == null) return;
        photo.setVisibility(View.GONE);
        eye.setVisibility(View.GONE);
        eye.setOnClickListener(v -> reveal(card, photo, eye, thumbUrl));
        final float[] down = new float[2];
        final boolean[] moved = new boolean[1];
        card.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getX();
                    down[1] = event.getY();
                    moved[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - down[0];
                    if (dx < -12 && Math.abs(dx) > Math.abs(event.getY() - down[1])) {
                        moved[0] = true;
                        v.setTranslationX(Math.max(dx, -v.getWidth() * .38f));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    float totalDx = event.getX() - down[0];
                    if (totalDx < -v.getWidth() * .22f) {
                        reveal(v, photo, eye, thumbUrl);
                    } else if (!moved[0]) {
                        v.performClick();
                    } else {
                        v.animate().translationX(0f).setDuration(180).start();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.animate().translationX(0f).setDuration(180).start();
                    return true;
                default:
                    return true;
            }
        });
    }

    private static void reveal(View card, ImageView photo, ImageButton eye, String url) {
        if (url == null || url.trim().isEmpty()) return;
        eye.setVisibility(View.VISIBLE);
        photo.setVisibility(View.VISIBLE);
        NetworkImageLoader.load(photo, url);
        card.animate().translationX(0f).setDuration(180).start();
    }
}
