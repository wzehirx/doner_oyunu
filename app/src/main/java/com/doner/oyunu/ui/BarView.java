package com.doner.oyunu.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** İlerleme/sabır çubuğu (dekoratif; bilgi metin olarak da verilir). */
public class BarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float fraction;
    private int fill = Theme.GOLD;
    private final int heightPx;

    public BarView(Context c, float heightDp) {
        super(c);
        heightPx = Ui.dp(c, heightDp);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void set(float f, int color) {
        f = Math.max(0f, Math.min(1f, f));
        if (f == fraction && color == fill) return;
        fraction = f;
        fill = color;
        invalidate();
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(resolveSize(Ui.dp(getContext(), 60), wSpec), heightPx);
    }

    @Override protected void onDraw(Canvas c) {
        float r = getHeight() / 2f;
        paint.setColor(0xFF1A0E08);
        rect.set(0, 0, getWidth(), getHeight());
        c.drawRoundRect(rect, r, r, paint);
        if (fraction > 0) {
            paint.setColor(fill);
            rect.set(0, 0, Math.max(getHeight(), getWidth() * fraction), getHeight());
            c.drawRoundRect(rect, r, r, paint);
        }
    }
}
