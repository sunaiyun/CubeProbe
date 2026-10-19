package com.cubeprobe.mc.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

/** ImageView that keeps Minecraft server icons crisp instead of blurring them. */
public class FaviconView extends ImageView {

    public FaviconView(Context context) {
        super(context);
    }

    public FaviconView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FaviconView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        if (drawable != null) {
            drawable.setFilterBitmap(false);
        }
        super.setImageDrawable(drawable);
    }
}
