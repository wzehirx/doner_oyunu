package com.doner.oyunu.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Programatik arayüz yardımcıları (boyut, düğme, erişilebilirlik işaretleri). */
public final class Ui {
    private Ui() {}

    public static final int PRIMARY = 0, SECONDARY = 1, DANGER = 2, GOLD = 3;
    public static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    public static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static boolean animationsOn(Context c) {
        try {
            return Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (Exception e) {
            return true;
        }
    }

    // ------------------------------------------------------------------ yerleşim
    public static LinearLayout.LayoutParams lp(Context c, int w, int h, float weight, float l, float t, float r, float b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    public static LinearLayout.LayoutParams lp(Context c, int w, int h, float l, float t, float r, float b) {
        return lp(c, w, h, 0f, l, t, r, b);
    }

    // ------------------------------------------------------------------ çizim
    public static GradientDrawable round(Context c, int fill, int stroke, float strokeDp, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) d.setStroke(Math.max(1, dp(c, strokeDp)), stroke);
        return d;
    }

    public static GradientDrawable gradient(Context c, int top, int bottom, int stroke, float strokeDp, float radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        d.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) d.setStroke(Math.max(1, dp(c, strokeDp)), stroke);
        return d;
    }

    /** Basılı ve odaklı durumları olan arka plan. */
    public static Drawable buttonBg(Context c, int style) {
        Drawable normal, pressed;
        switch (style) {
            case PRIMARY:
                normal = gradient(c, Theme.ORANGE, Theme.ORANGE_DEEP, Theme.GOLD, 2, 16);
                pressed = gradient(c, Theme.darken(Theme.ORANGE, 0.8f), Theme.darken(Theme.ORANGE_DEEP, 0.8f), Theme.GOLD, 2, 16);
                break;
            case GOLD:
                normal = gradient(c, Theme.GOLD, Theme.ORANGE, Theme.CREAM, 2, 16);
                pressed = gradient(c, Theme.darken(Theme.GOLD, 0.8f), Theme.darken(Theme.ORANGE, 0.8f), Theme.CREAM, 2, 16);
                break;
            case DANGER:
                normal = round(c, Theme.CARD, Theme.RED, 3, 16);
                pressed = round(c, Theme.CARD_HI, Theme.RED, 3, 16);
                break;
            default:
                normal = round(c, Theme.CARD, Theme.STROKE, 2, 16);
                pressed = round(c, Theme.CARD_HI, Theme.GOLD, 2, 16);
        }
        Drawable focused = round(c, Theme.CARD_HI, Theme.GOLD, 4, 16);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sl.addState(new int[]{android.R.attr.state_focused}, focused);
        sl.addState(new int[]{}, normal);
        return sl;
    }

    // ------------------------------------------------------------------ metin
    public static TextView label(Context c, CharSequence text, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLineSpacing(0, 1.08f);
        return t;
    }

    public static Button button(Context c, CharSequence text, int style) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(style == PRIMARY || style == GOLD ? Theme.DARK : Theme.CREAM);
        b.setBackground(buttonBg(c, style));
        b.setMinHeight(dp(c, 60));
        b.setMinimumHeight(dp(c, 60));
        b.setMinWidth(dp(c, 60));
        b.setMinimumWidth(dp(c, 60));
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
        b.setStateListAnimator(null);
        b.setElevation(0);
        return b;
    }

    // ------------------------------------------------------------------ erişilebilirlik
    /** Başlık işareti (TalkBack başlık gezintisi). */
    public static void heading(View v) {
        v.setFocusable(true);
        if (Build.VERSION.SDK_INT >= 28) v.setAccessibilityHeading(true);
    }

    /** Dekoratif görünüm: erişilebilirlik ağacından çıkar. */
    public static void decorative(View v) {
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Bu kapsayıcı tek bir düğme gibi okunsun; çocukları gizle. */
    public static void groupAsButton(View v) {
        v.setFocusable(true);
        v.setClickable(true);
        v.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
            }
        });
        hideChildren(v);
    }

    /** Bilgi grubu: tek odak, çocuklar okunmaz. */
    public static void groupAsText(View v) {
        v.setFocusable(true);
        hideChildren(v);
    }

    private static void hideChildren(View v) {
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) decorative(g.getChildAt(i));
        }
    }

    public static void setDesc(View v, CharSequence d) {
        CharSequence old = v.getContentDescription();
        if (old == null || !old.toString().contentEquals(d)) v.setContentDescription(d);
    }

    /**
     * Canlı bölge içeriğini günceller. announce=false iken değişiklik sessizdir (odaklanınca doğru okunur),
     * announce=true iken TalkBack kibarca duyurur.
     */
    public static void liveUpdate(View v, CharSequence d, boolean announce) {
        CharSequence old = v.getContentDescription();
        if (old != null && old.toString().contentEquals(d)) return;
        v.setAccessibilityLiveRegion(announce ? View.ACCESSIBILITY_LIVE_REGION_POLITE : View.ACCESSIBILITY_LIVE_REGION_NONE);
        v.setContentDescription(d);
    }

    public static void dim(View v, boolean dim) { v.setAlpha(dim ? 0.7f : 1f); }
}
