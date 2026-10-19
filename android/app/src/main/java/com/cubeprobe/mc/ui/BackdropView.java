package com.cubeprobe.mc.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * A quiet, atmospheric backdrop: deep navy, soft coloured light and a faint pixel grid.
 */
public class BackdropView extends View {

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backgroundPaint = new Paint();
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint();

    private float width;
    private float height;
    private float density;

    public BackdropView(Context context) {
        this(context, null);
    }

    public BackdropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        backgroundPaint.setShader(new LinearGradient(
                0f, 0f, 0f, 1f,
                new int[]{Color.rgb(7, 18, 30), Color.rgb(4, 11, 20)},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP));
        dotPaint.setColor(Color.rgb(29, 53, 70));
        dotPaint.setAlpha(95);
        linePaint.setColor(Color.rgb(27, 53, 68));
        linePaint.setAlpha(45);
        linePaint.setStrokeWidth(Math.max(1f, density * 0.55f));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
        backgroundPaint.setShader(new LinearGradient(
                0f, 0f, 0f, h,
                new int[]{Color.rgb(7, 18, 30), Color.rgb(4, 11, 20)},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, width, height, backgroundPaint);

        drawGlow(canvas, width * 0.92f, height * 0.04f, width * 0.78f,
                new int[]{0x4A1C6A4A, 0x16134A38, 0x0006101C});
        drawGlow(canvas, width * 0.02f, height * 0.46f, width * 0.66f,
                new int[]{0x2E0D4655, 0x100B2C38, 0x0006101C});
        drawGlow(canvas, width * 0.70f, height * 0.82f, width * 0.60f,
                new int[]{0x22144736, 0x080A1E19, 0x0006101C});

        float spacing = density * 23f;
        float radius = Math.max(1f, density * 0.8f);
        for (float y = density * 12f; y < height; y += spacing) {
            for (float x = density * 10f; x < width; x += spacing) {
                canvas.drawCircle(x, y, radius, dotPaint);
            }
        }

        canvas.drawLine(0f, height * 0.24f, width, height * 0.24f, linePaint);
        canvas.drawLine(0f, height * 0.70f, width, height * 0.70f, linePaint);
    }

    private void drawGlow(Canvas canvas, float cx, float cy, float radius, int[] colors) {
        glowPaint.setShader(new RadialGradient(
                cx, cy, radius,
                colors,
                new float[]{0f, 0.52f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, glowPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec));
    }
}
