package com.cubapincha.job.util;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.cubapincha.job.R;

/** Anillo animado de % de coincidencia: se dibuja con la curva única de la app. */
public class MatchRingView extends View {
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();
    private int percent = 0;
    private float animatedPercent = 0f;
    private ValueAnimator animator;

    public MatchRingView(Context context) {
        super(context);
        init();
    }

    public MatchRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setStrokeCap(Paint.Cap.ROUND);
        // El fondo del anillo sigue el tema (claro y nocturno), no un
        // color fijo: en noche usa el borde nocturno.
        bgPaint.setColor(resolveAttr(com.google.android.material.R.attr.colorSurfaceVariant));

        fgPaint.setStyle(Paint.Style.STROKE);
        fgPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    /** Resuelve un atributo del tema actual a su color. */
    private int resolveAttr(int attr) {
        TypedValue tv = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, tv, true)) {
            return tv.data;
        }
        return ContextCompat.getColor(getContext(), R.color.border_light);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Trazo y texto proporcionales al tamaño: el anillo se ve refinado
        // a 48dp en la tarjeta, 56dp en la ficha y 64dp en el detalle.
        float size = Math.min(w, h);
        if (size <= 0) return;
        float stroke = Math.max(4f, size * 0.11f);
        bgPaint.setStrokeWidth(stroke);
        fgPaint.setStrokeWidth(stroke);
        textPaint.setTextSize(size * 0.30f);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
    }

    /** Fija el porcentaje con animación (o sin ella si se pidió reducir movimiento). */
    public void setPercent(int percent, boolean animate) {
        this.percent = Math.max(0, Math.min(100, percent));
        setContentDescription(getContext().getString(R.string.match_percent_desc, this.percent));
        // El trazo usa el color gráfico (menta, violeta, sol); el número
        // usa la variante oscura con contraste AA sobre fondo claro.
        int ringColor;
        int textColor;
        if (this.percent >= 90) {
            ringColor = ContextCompat.getColor(getContext(), R.color.mint_400);
            textColor = ContextCompat.getColor(getContext(), R.color.success_text);
        } else if (this.percent >= 50) {
            ringColor = ContextCompat.getColor(getContext(), R.color.violet_500);
            textColor = ringColor;
        } else {
            ringColor = ContextCompat.getColor(getContext(), R.color.sun_300);
            textColor = ContextCompat.getColor(getContext(), R.color.warning_text);
        }
        fgPaint.setColor(ringColor);
        textPaint.setColor(textColor);

        if (animator != null) animator.cancel();
        if (!animate || AnimUtils.reduceMotion(getContext())) {
            animatedPercent = this.percent;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0f, this.percent);
        animator.setDuration(AnimUtils.DURATION_LONG * 3);
        animator.setInterpolator(AnimUtils.EASE_EMPHASIZED);
        animator.addUpdateListener(a -> {
            animatedPercent = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void setPercent(int percent) {
        setPercent(percent, true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) return;
        float stroke = bgPaint.getStrokeWidth();
        float pad = stroke / 2f + 1f;
        arcRect.set(pad, pad, size - pad, size - pad);
        canvas.drawArc(arcRect, -90, 360, false, bgPaint);
        canvas.drawArc(arcRect, -90, 360 * animatedPercent / 100f, false, fgPaint);
        float y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText(Math.round(animatedPercent) + "%", size / 2f, y, textPaint);
    }
}
