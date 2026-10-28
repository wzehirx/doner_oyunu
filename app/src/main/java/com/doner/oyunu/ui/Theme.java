package com.doner.oyunu.ui;

import android.graphics.Color;

/** Yüksek kontrastlı koyu tema: krem/altın yazı, turuncu-kırmızı vurgu. */
public final class Theme {
    private Theme() {}

    public static final int BG_TOP = 0xFF1A0E08;
    public static final int BG_MID = 0xFF2A170D;
    public static final int BG_BOTTOM = 0xFF45200F;

    public static final int CARD = 0xFF3A2315;
    public static final int CARD_HI = 0xFF54331E;
    public static final int STROKE = 0xFF9A6A3E;

    public static final int CREAM = 0xFFFFF0D2;
    public static final int MUTED = 0xFFDCC3A3;
    public static final int GOLD = 0xFFFFC83D;
    public static final int ORANGE = 0xFFFF8A2B;
    public static final int ORANGE_DEEP = 0xFFFF5F1F;
    public static final int RED = 0xFFFF5A3C;
    public static final int GREEN = 0xFF9BE08F;
    public static final int DARK = 0xFF1A0E08;

    public static int darken(int c, float f) {
        return Color.argb(Color.alpha(c), (int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }

    public static int lighten(int c, float f) {
        int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
        return Color.argb(Color.alpha(c), (int) (r + (255 - r) * f), (int) (g + (255 - g) * f), (int) (b + (255 - b) * f));
    }
}
