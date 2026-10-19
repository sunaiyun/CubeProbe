package com.cubeprobe.mc.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * A two-option, hand-drawn segmented control. Java uses mint, Bedrock uses cyan.
 */
public class ModeSwitch extends View {

    public static final int MODE_JAVA = 0;
    public static final int MODE_BEDROCK = 1;

    public interface OnModeChangeListener {
        void onModeChanged(int mode);
    }

    private static final int JAVA_ACCENT = Color.rgb(98, 242, 162);
    private static final int BEDROCK_ACCENT = Color.rgb(78, 200, 255);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pill = new RectF();

    private int selected = MODE_JAVA;
    private float indicatorX;
    private float touchDownX;
    private boolean moved;
    private OnModeChangeListener listener;
    private float density;
    private float textSize;

    private float animatedX;
    private int animatedAccent = JAVA_ACCENT;

    public ModeSwitch(Context context) {
        this(context, null);
    }

    public ModeSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        textSize = 13.5f * getResources().getDisplayMetrics().scaledDensity;
        textPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        textPaint.setTextSize(textSize);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1f));
        setClickable(true);
        setFocusable(true);
        updateAccessibilityDescription();
    }

    private float dp(float value) {
        return value * density;
    }

    public void setOnModeChangeListener(OnModeChangeListener listener) {
        this.listener = listener;
    }

    public int getMode() {
        return selected;
    }

    public void setMode(int mode, boolean animate) {
        if (mode != MODE_JAVA && mode != MODE_BEDROCK) {
            return;
        }
        if (mode == selected) {
            return;
        }
        selected = mode;
        updateAccessibilityDescription();
        float target = targetX(selected);
        if (animate && getWidth() > 0) {
            android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(animatedX, target);
            animator.setDuration(230);
            animator.setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f));
            animator.addUpdateListener(animation -> {
                animatedX = (float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            animatedX = target;
            indicatorX = target;
            invalidate();
        }
    }

    private float targetX(int mode) {
        float inner = getWidth() - dp(8f);
        float slot = inner / 2f;
        return dp(4f) + (mode == MODE_BEDROCK ? slot : 0f);
    }

    private int accentFor(int mode) {
        return mode == MODE_BEDROCK ? BEDROCK_ACCENT : JAVA_ACCENT;
    }

    private int contentColor(int mode) {
        return mode == selected ? Color.rgb(242, 248, 245) : Color.rgb(128, 151, 169);
    }

    private void updateAccessibilityDescription() {
        setContentDescription("服务器版本：" + (selected == MODE_JAVA ? "Java 版" : "基岩版"));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        animatedX = targetX(selected);
        indicatorX = animatedX;
        animatedAccent = accentFor(selected);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float radius = dp(16f);

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF081725);
        canvas.drawRoundRect(new RectF(0, 0, w, h), radius, radius, paint);

        strokePaint.setColor(0xFF213A4C);
        canvas.drawRoundRect(new RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), radius, radius, strokePaint);

        float slot = (w - dp(8f)) / 2f;
        int targetAccent = accentFor(selected);
        if (animatedAccent != targetAccent) {
            animatedAccent = targetAccent;
        }
        pill.set(animatedX, dp(4f), animatedX + slot, h - dp(4f));

        paint.setColor(blend(0xFF0B1722, animatedAccent, 0.15f));
        canvas.drawRoundRect(pill, dp(12.5f), dp(12.5f), paint);
        strokePaint.setColor(withAlpha(animatedAccent, 0.42f));
        canvas.drawRoundRect(pill, dp(12.5f), dp(12.5f), strokePaint);

        drawItem(canvas, 0, "Java 版", dp(4f) + slot / 2f);
        drawItem(canvas, 1, "基岩版", dp(4f) + slot * 1.5f);
    }

    private void drawItem(Canvas canvas, int mode, String label, float centerX) {
        int color = contentColor(mode);
        float labelWidth = textPaint.measureText(label);
        float dot = dp(5f);
        float gap = dp(7f);
        float contentWidth = dot + gap + labelWidth;
        float left = centerX - contentWidth / 2f;
        float cy = getHeight() / 2f - (textPaint.ascent() + textPaint.descent()) / 2f;

        paint.setColor(mode == selected ? withAlpha(accentFor(mode), 1f) : 0xFF3D5365);
        canvas.drawCircle(left + dot / 2f, getHeight() / 2f, dot / 2f, paint);

        textPaint.setColor(color);
        canvas.drawText(label, left + dot + gap, cy, textPaint);
    }

    private static int withAlpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.round(255 * Math.min(1f, alpha)) << 24);
    }

    private static int blend(int from, int to, float amount) {
        float inv = 1f - amount;
        return Color.argb(
                Math.round(Color.alpha(from) * inv + Color.alpha(to) * amount),
                Math.round(Color.red(from) * inv + Color.red(to) * amount),
                Math.round(Color.green(from) * inv + Color.green(to) * amount),
                Math.round(Color.blue(from) * inv + Color.blue(to) * amount));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = event.getX();
                moved = false;
                setPressed(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - touchDownX) > dp(14f)) {
                    moved = true;
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                return true;
            case MotionEvent.ACTION_UP:
                setPressed(false);
                if (!moved) {
                    int mode = event.getX() < getWidth() / 2f ? MODE_JAVA : MODE_BEDROCK;
                    if (mode != selected) {
                        setMode(mode, true);
                        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                        if (listener != null) {
                            listener.onModeChanged(mode);
                        }
                    }
                    performClick();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Button");
        info.setContentDescription("切换服务器版本，当前" + (selected == MODE_JAVA ? "Java 版" : "基岩版"));
    }
}
