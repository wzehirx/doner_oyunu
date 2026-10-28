package com.doner.oyunu.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** 1.0-5.0 arası ondalıklı yıldız göstergesi (dolu/boş biçim farkıyla, renge bağımlı değil). */
public class StarsView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private double value = 3.0;
    private final int h;

    public StarsView(Context c, float heightDp) {
        super(c);
        h = Ui.dp(c, heightDp);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setValue(double v) {
        if (Math.abs(v - value) < 1e-6) return;
        value = v;
        invalidate();
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(resolveSize(h * 5 + h / 4 * 4, wSpec), resolveSize(h, hSpec));
    }

    @Override protected void onDraw(Canvas c) {
        float cell = Math.min(getHeight(), getWidth() / 5.25f);
        float gap = (getWidth() - cell * 5) / 4f;
        float r = cell * 0.5f;
        for (int i = 0; i < 5; i++) {
            float left = i * (cell + gap);
            float cx = left + r, cy = getHeight() / 2f;
            // boş yıldız: kontur
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(cell * 0.08f);
            paint.setColor(Theme.MUTED);
            Icons.star(c, cx, cy, r * 0.88f, paint);
            double fill = Math.max(0, Math.min(1, value - i));
            if (fill > 0) {
                c.save();
                c.clipRect(left, 0, left + cell * (float) fill, getHeight());
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Theme.GOLD);
                Icons.star(c, cx, cy, r * 0.88f, paint);
                c.restore();
            }
        }
    }
}
