package com.cubapincha.job.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/** Estrellas de valoración dibujadas; siempre representa exactamente cinco. */
public class RatingStarsView extends View {
    private static final int STAR_COUNT = 5;
    private static final int GOLD = Color.rgb(244, 185, 66);
    private static final int EMPTY = Color.rgb(145, 145, 145);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int rating;
    private boolean interactive;
    private OnRatingChangedListener listener;

    public interface OnRatingChangedListener {
        void onRatingChanged(int rating);
    }

    public RatingStarsView(Context context) {
        super(context);
        init();
    }

    public RatingStarsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RatingStarsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setContentDescription("Valoración de 0 de 5 estrellas");
        setClickable(false);
        paint.setStyle(Paint.Style.FILL);
    }

    public void setRating(int value) {
        int safe = Math.max(0, Math.min(STAR_COUNT, value));
        if (rating == safe) return;
        rating = safe;
        setContentDescription("Valoración de " + rating + " de 5 estrellas");
        invalidate();
    }

    public int getRating() { return rating; }

    public void setInteractive(boolean value) {
        interactive = value;
        setClickable(value);
        setFocusable(value);
    }

    public void setOnRatingChangedListener(OnRatingChangedListener value) {
        listener = value;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float slot = width / STAR_COUNT;
        float radius = Math.min(slot * 0.37f, height * 0.38f);
        float cy = height / 2f;
        for (int i = 0; i < STAR_COUNT; i++) {
            float cx = slot * (i + 0.5f);
            Path path = starPath(cx, cy, radius, radius * 0.42f);
            if (i < rating) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(GOLD);
                canvas.drawPath(path, paint);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, radius * 0.12f));
                paint.setColor(EMPTY);
                canvas.drawPath(path, paint);
            }
        }
    }

    private Path starPath(float cx, float cy, float outer, float inner) {
        Path path = new Path();
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            float radius = i % 2 == 0 ? outer : inner;
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        return path;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (!interactive) return super.onTouchEvent(event);
        if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
            int selected = Math.max(1, Math.min(STAR_COUNT,
                    (int) (event.getX() / Math.max(1f, getWidth() / STAR_COUNT)) + 1));
            setRating(selected);
            if (listener != null) listener.onRatingChanged(selected);
            performClick();
        }
        return true;
    }
}
