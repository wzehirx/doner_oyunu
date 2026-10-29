package com.doner.oyunu.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.doner.oyunu.MainActivity;

/** Tüm ekranların tabanı. Ekranlar programatik View ağacı üretir. */
public abstract class Screen {
    protected final MainActivity a;
    protected final Context ctx;
    protected TextView titleView;

    protected Screen(MainActivity a) {
        this.a = a;
        this.ctx = a;
    }

    /** Okunan ekran başlığı (pane title). */
    public abstract String title();

    public abstract View create();

    /** Müzik adı (res/raw) ya da null. */
    public String music() { return "music_menu"; }

    /** Ambiyans döngüsü adı (res/raw) ya da null: yalnızca gün ekranı dükkân ambiyansı çalar (v0.9). */
    public String ambience() { return null; }

    /** Ekrana girişte odaklanacak görünüm (varsayılan: başlık). */
    public View focusTarget() { return titleView; }

    public void onEnter() { }

    public void onExit() { }

    public void onPause() { }

    public void onResume() { }

    /** true: geri tuşu işlendi. */
    public boolean onBack() { return false; }

    // ------------------------------------------------------------------ yardımcılar
    protected int dp(float v) { return Ui.dp(ctx, v); }

    protected LinearLayout column() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    protected LinearLayout rootColumn() {
        LinearLayout l = column();
        l.setPadding(dp(16), dp(18), dp(16), dp(12));
        l.setLayoutParams(new ViewGroup.LayoutParams(Ui.MATCH, Ui.MATCH));
        return l;
    }

    /** Kök sütuna kaydırılabilir gövde ekler ve iç sütunu döner. */
    protected LinearLayout scrollBody(LinearLayout root) {
        ScrollView sv = new ScrollView(ctx);
        sv.setFillViewport(false);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout body = column();
        body.setPadding(0, 0, 0, dp(8));
        sv.addView(body, new ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP));
        root.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        return body;
    }

    protected TextView makeTitle(String text) {
        TextView t = Ui.label(ctx, text, 30, Theme.GOLD, true);
        Ui.heading(t);
        t.setPadding(0, dp(4), 0, dp(10));
        titleView = t;
        return t;
    }

    protected TextView sectionHeading(String text) {
        TextView t = Ui.label(ctx, text, 22, Theme.GOLD, true);
        Ui.heading(t);
        t.setPadding(0, dp(14), 0, dp(6));
        return t;
    }

    protected TextView paragraph(String text) {
        TextView t = Ui.label(ctx, text, 18, Theme.CREAM, false);
        t.setFocusable(true);
        t.setPadding(0, dp(4), 0, dp(6));
        return t;
    }

    protected Button button(String text, int style, final Runnable action) {
        Button b = Ui.button(ctx, text, style);
        tap(b, action);
        return b;
    }

    /** Dokunma: tık sesi + hafif titreşim + eylem. */
    protected void tap(View v, final Runnable action) {
        v.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                a.sound.play("sfx_tap");
                a.haptics.tap();
                action.run();
            }
        });
    }

    protected LinearLayout card() {
        LinearLayout c = column();
        c.setBackground(Ui.round(ctx, Theme.CARD, Theme.STROKE, 1.5f, 16));
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        return c;
    }
}
