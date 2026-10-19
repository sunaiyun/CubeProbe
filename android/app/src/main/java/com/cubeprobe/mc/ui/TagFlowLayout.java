package com.cubeprobe.mc.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/** A lightweight wrapping layout for player and metadata pills. */
public class TagFlowLayout extends ViewGroup {

    private final int horizontalGap;
    private final int verticalGap;
    private final List<Row> rows = new ArrayList<>();

    public TagFlowLayout(Context context) {
        this(context, null);
    }

    public TagFlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        horizontalGap = Math.round(density * 7f);
        verticalGap = Math.round(density * 7f);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int available = widthMode == MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE
                : Math.max(0, widthSize - getPaddingLeft() - getPaddingRight());

        rows.clear();
        int lineWidth = 0;
        int lineHeight = 0;
        int maxLineWidth = 0;
        int totalHeight = getPaddingTop() + getPaddingBottom();
        Row current = new Row();
        int count = getChildCount();

        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            int needed = lineWidth == 0 ? childWidth : lineWidth + horizontalGap + childWidth;
            if (lineWidth > 0 && needed > available) {
                totalHeight += lineHeight + verticalGap;
                rows.add(current);
                current = new Row();
                lineWidth = 0;
                lineHeight = 0;
            }
            if (lineWidth > 0) {
                lineWidth += horizontalGap;
            }
            current.children.add(child);
            current.width = lineWidth + childWidth;
            current.height = Math.max(lineHeight, childHeight);
            lineWidth += childWidth;
            lineHeight = Math.max(lineHeight, childHeight);
            maxLineWidth = Math.max(maxLineWidth, lineWidth);
        }
        if (lineWidth > 0) {
            rows.add(current);
            totalHeight += lineHeight;
        }

        int desiredWidth = Math.max(maxLineWidth + getPaddingLeft() + getPaddingRight(),
                getSuggestedMinimumWidth());
        int desiredHeight = Math.max(totalHeight, getSuggestedMinimumHeight());
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int left = getPaddingLeft();
        int top = getPaddingTop();
        for (Row row : rows) {
            int x = left;
            for (View child : row.children) {
                int childHeight = child.getMeasuredHeight();
                int childTop = top + (row.height - childHeight) / 2;
                child.layout(x, childTop, x + child.getMeasuredWidth(), childTop + childHeight);
                x += child.getMeasuredWidth() + horizontalGap;
            }
            top += row.height + verticalGap;
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return p instanceof LayoutParams;
    }

    private static class Row {
        final List<View> children = new ArrayList<>();
        int width;
        int height;
    }
}
