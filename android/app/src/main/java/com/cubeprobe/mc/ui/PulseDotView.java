package com.cubeprobe.mc.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/** Small status dot with a slow, breathing halo. */
public class PulseDotView extends View {

    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float phase;
    private boolean pulsing = true;
    private int color = 0xFF62F2A2;

    public PulseDotView(Context context) {
        this(context, null);
    }

    public PulseDotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        core.setColor(color);
        halo.setStyle(Paint.Style.STROKE);
        halo.setStrokeWidth(getResources().getDisplayMetrics().density * 1.1f);
        halo.setColor(color);
    }

    public void setColor(int color) {
        this.color = color;
        core.setColor(color);
        halo.setColor(color);
        invalidate();
    }

    public void setPulsing(boolean pulsing) {
        this.pulsing = pulsing;
        if (pulsing) {
            startPulse();
        } else {
            stopPulse();
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startPulse();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopPulse();
        super.onDetachedFromWindow();
    }

    private void startPulse() {
        if (!pulsing || animator != null) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1650);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stopPulse() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        phase = 0f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float min = Math.min(getWidth(), getHeight());
        float coreRadius = min * 0.29f;

        if (pulsing) {
            float ringRadius = min * (0.40f + phase * 0.55f);
            int alpha = Math.round(150 * (1f - phase));
            halo.setAlpha(alpha);
            canvas.drawCircle(cx, cy, ringRadius, halo);
        }
        core.setAlpha(255);
        canvas.drawCircle(cx, cy, coreRadius, core);
    }
}
