package com.cubapincha.job.util;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageButton;
import android.widget.ImageView;

/** Revela una foto pública con un deslizamiento horizontal hacia la izquierda. */
public final class PhotoSwipeBinder {
    private PhotoSwipeBinder() { }

    public static void bind(View card, ImageView photo, ImageButton eye, String thumbUrl) {
        if (card == null || photo == null || eye == null) return;
        photo.setVisibility(View.GONE);
        eye.setVisibility(View.GONE);
        eye.setOnClickListener(v -> reveal(card, photo, eye, thumbUrl));
        final float[] down = new float[2];
        final boolean[] horizontal = new boolean[1];
        final boolean[] tracking = new boolean[1];
        final int slop = ViewConfiguration.get(card.getContext()).getScaledTouchSlop();
        card.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getX();
                    down[1] = event.getY();
                    horizontal[0] = false;
                    tracking[0] = true;
                    requestParent(v, false);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!tracking[0]) return false;
                    float dx = event.getX() - down[0];
                    float dy = event.getY() - down[1];
                    if (!horizontal[0] && Math.max(Math.abs(dx), Math.abs(dy)) < slop) return true;
                    if (!horizontal[0]) {
                        if (Math.abs(dy) >= Math.abs(dx)) {
                            tracking[0] = false;
                            requestParent(v, false);
                            return false;
                        }
                        horizontal[0] = true;
                        requestParent(v, true);
                    }
                    if (dx < 0) v.setTranslationX(Math.max(dx, -v.getWidth() * .38f));
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!tracking[0]) return false;
                    float totalDx = event.getX() - down[0];
                    tracking[0] = false;
                    requestParent(v, false);
                    if (horizontal[0] && totalDx < -v.getWidth() * .22f) {
                        reveal(v, photo, eye, thumbUrl);
                    } else if (horizontal[0]) {
                        v.animate().translationX(0f).setDuration(180).start();
                    } else {
                        v.performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    tracking[0] = false;
                    requestParent(v, false);
                    v.animate().translationX(0f).setDuration(180).start();
                    return false;
                default:
                    return true;
            }
        });
    }

    private static void requestParent(View view, boolean disallow) {
        if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(disallow);
    }

    private static void reveal(View card, ImageView photo, ImageButton eye, String url) {
        if (url == null || url.trim().isEmpty()) {
            card.animate().translationX(0f).setDuration(180).start();
            return;
        }
        eye.setVisibility(View.VISIBLE);
        photo.setVisibility(View.VISIBLE);
        NetworkImageLoader.load(photo, url);
        card.animate().translationX(0f).setDuration(180).start();
    }
}
