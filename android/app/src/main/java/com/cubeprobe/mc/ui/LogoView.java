package com.cubeprobe.mc.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** The small isometric cube mark used in the header. */
public class LogoView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cube = new Path();
    private final RectF bounds = new RectF();
    private Shader shader;

    public LogoView(Context context) {
        this(context, null);
    }

    public LogoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(0x66041B10);
        stroke.setStrokeWidth(dp(1f));
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        shader = new LinearGradient(0, 0, w, h,
                new int[]{Color.rgb(133, 255, 184), Color.rgb(39, 214, 128)},
                null, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float pad = dp(1f);
        bounds.set(pad, pad, w - pad, h - pad);
        paint.setShader(shader);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(bounds, dp(14f), dp(14f), paint);
        paint.setShader(null);

        float cx = w / 2f;
        float cy = h / 2f + dp(1f);
        float s = w * 0.205f;

        Path top = new Path();
        top.moveTo(cx, cy - s * 1.35f);
        top.lineTo(cx + s * 1.72f, cy - s * 0.40f);
        top.lineTo(cx, cy + s * 0.52f);
        top.lineTo(cx - s * 1.72f, cy - s * 0.40f);
        top.close();

        Path left = new Path();
        left.moveTo(cx - s * 1.72f, cy - s * 0.40f);
        left.lineTo(cx, cy + s * 0.52f);
        left.lineTo(cx, cy + s * 1.75f);
        left.lineTo(cx - s * 1.72f, cy + s * 0.90f);
        left.close();

        Path right = new Path();
        right.moveTo(cx + s * 1.72f, cy - s * 0.40f);
        right.lineTo(cx + s * 1.72f, cy + s * 0.90f);
        right.lineTo(cx, cy + s * 1.75f);
        right.lineTo(cx, cy + s * 0.52f);
        right.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xD904331E);
        canvas.drawPath(top, paint);
        paint.setColor(0xE8022415);
        canvas.drawPath(left, paint);
        paint.setColor(0xF001180D);
        canvas.drawPath(right, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.1f));
        paint.setColor(0x8029B96E);
        canvas.drawPath(top, paint);
        canvas.drawPath(left, paint);
        canvas.drawPath(right, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xCCFFFFFF);
        canvas.drawCircle(w - dp(11f), dp(11f), dp(1.7f), paint);
    }
}
