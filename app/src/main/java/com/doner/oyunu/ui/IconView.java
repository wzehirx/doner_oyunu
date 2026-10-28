package com.doner.oyunu.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Dekoratif ikon görünümü (erişilebilirlik ağacında yok). */
public class IconView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Icons.Kind kind;
    private float phase;
    private final int defaultSize;

    public IconView(Context c, Icons.Kind kind, float sizeDp) {
        super(c);
        this.kind = kind;
        this.defaultSize = Ui.dp(c, sizeDp);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setKind(Icons.Kind k) { kind = k; invalidate(); }

    public void setPhase(float p) { phase = p; invalidate(); }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = resolveSize(defaultSize, wSpec);
        int h = resolveSize(defaultSize, hSpec);
        setMeasuredDimension(w, h);
    }

    @Override protected void onDraw(Canvas c) {
        float s = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(), getHeight() - getPaddingTop() - getPaddingBottom());
        if (s <= 0) return;
        float x = (getWidth() - s) / 2f, y = (getHeight() - s) / 2f;
        Icons.draw(c, kind, x, y, s, paint, phase);
    }
}
