package com.doner.oyunu.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Sıcak gradyanlı arka plan + iki yumuşak ışık lekesi. */
public final class GradientBackground extends Drawable {
    private final Paint base = new Paint();
    private final Paint glowA = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowB = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override protected void onBoundsChange(Rect b) {
        base.setShader(new LinearGradient(0, 0, 0, b.height(), new int[]{Theme.BG_TOP, Theme.BG_MID, Theme.BG_BOTTOM},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        float r1 = b.width() * 0.9f;
        glowA.setShader(new RadialGradient(b.width() * 0.85f, b.height() * 0.08f, r1,
                new int[]{0x55FF8A2B, 0x00FF8A2B}, null, Shader.TileMode.CLAMP));
        glowB.setShader(new RadialGradient(b.width() * 0.1f, b.height() * 0.95f, r1,
                new int[]{0x44FF5F1F, 0x00FF5F1F}, null, Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        c.drawRect(b, base);
        c.drawRect(b, glowA);
        c.drawRect(b, glowB);
    }

    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter f) { }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
