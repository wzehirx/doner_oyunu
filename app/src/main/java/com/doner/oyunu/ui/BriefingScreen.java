package com.doner.oyunu.ui;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.doner.oyunu.MainActivity;
import com.doner.oyunu.engine.Narrator;

import java.util.List;

/** Gün brifingi: yeni ürünler, hedef, oyun akışı (v0.7; buradan da değişir), öğretici notu. */
public final class BriefingScreen extends Screen {
    private TextView flowTv;
    private android.widget.Button flowBtn;

    public BriefingScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Gün " + a.save.day + " brifingi"; }

    @Override public View create() {
        LinearLayout root = rootColumn();
        TextView t = makeTitle("Gün " + a.save.day);
        t.setContentDescription("Gün " + a.save.day + " brifingi");
        root.addView(t);

        // üst özet: para ve yıldız (dekoratif simgeler + okunabilir metin)
        LinearLayout stat = new LinearLayout(ctx);
        stat.setOrientation(LinearLayout.HORIZONTAL);
        stat.setGravity(android.view.Gravity.CENTER_VERTICAL);
        stat.setBackground(Ui.round(ctx, Theme.CARD, Theme.STROKE, 1.5f, 16));
        stat.setPadding(dp(14), dp(10), dp(14), dp(10));
        IconView coin = new IconView(ctx, Icons.Kind.COIN, 30);
        stat.addView(coin, Ui.lp(ctx, Ui.dp(ctx, 30), Ui.dp(ctx, 30), 0, 0, 8, 0));
        TextView money = Ui.label(ctx, Texts.money(a.save.money), 20, Theme.GOLD, true);
        stat.addView(money, Ui.lp(ctx, 0, Ui.WRAP, 1f, 0, 0, 0, 0));
        StarsView stars = new StarsView(ctx, 22);
        stat.addView(stars, Ui.lp(ctx, Ui.WRAP, Ui.dp(ctx, 22), 0, 0, 8, 0));
        stars.setValue(a.save.stars);
        TextView starNum = Ui.label(ctx, Narrator.fmtStars(a.save.stars), 20, Theme.GOLD, true);
        stat.addView(starNum);
        Ui.groupAsText(stat);
        stat.setContentDescription("Kasada " + a.save.money + " lira, yıldız " + Narrator.fmtStars(a.save.stars));
        root.addView(stat, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 0, 0, 6));

        LinearLayout body = scrollBody(root);
        List<String> lines = Texts.briefingLines(a.save);
        for (String l : lines) {
            LinearLayout c = card();
            TextView tv = Ui.label(ctx, l, 18, Theme.CREAM, false);
            tv.setFocusable(true);
            if (l.startsWith("Oyun akışı") || l.startsWith("Seçim: Oyun akışı")) flowTv = tv;
            if (l.startsWith("Yeni ürün") || l.startsWith("Yeni malzeme") || l.startsWith("Yeni boy")) {
                c.setBackground(Ui.round(ctx, Theme.CARD_HI, Theme.GOLD, 3, 16));
                tv.setTextColor(Theme.GOLD);
                tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            }
            c.addView(tv);
            body.addView(c, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 5, 0, 5));
        }

        LinearLayout bar = column();
        flowBtn = button(Texts.flowButtonLabel(a.save.flowMode), Ui.SECONDARY, new Runnable() {
            @Override public void run() { cycleFlow(); }
        });
        flowBtn.setContentDescription(Texts.flowButtonDescription(a.save.flowMode));
        bar.addView(flowBtn, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 6));
        bar.addView(button("Markete git", Ui.PRIMARY, new Runnable() {
            @Override public void run() { a.show(new ShopScreen(a)); }
        }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 6));
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(button("Güne başla", Ui.GOLD, new Runnable() {
            @Override public void run() { a.save.flowChosen = true; a.saveNow(); a.show(new GameScreen(a)); }
        }), Ui.lp(ctx, 0, Ui.WRAP, 1f, 0, 0, 6, 0));
        row.addView(button("Ana menü", Ui.SECONDARY, new Runnable() {
            @Override public void run() { a.show(new MenuScreen(a)); }
        }), Ui.lp(ctx, 0, Ui.WRAP, 1f, 6, 0, 0, 0));
        bar.addView(row);
        root.addView(bar);
        return root;
    }

    /** Oyun akışını sıradakine geçirir (Rahat → Dengeli → Gerçekçi), kaydeder ve kısaca duyurur. */
    private void cycleFlow() {
        a.save.flowMode = a.save.flowMode.next();
        a.save.flowChosen = true;
        a.saveNow();
        flowBtn.setText(Texts.flowButtonLabel(a.save.flowMode));
        flowBtn.setContentDescription(Texts.flowButtonDescription(a.save.flowMode));
        if (flowTv != null) {
            String l = Texts.flowBriefingLine(a.save);
            flowTv.setText(l);
        }
        a.speaker.announce(a.save.flowMode.spoken(), true);
    }

    /** İlk seferde (akış henüz seçilmediyse) kısa sesli soru. */
    @Override public void onEnter() {
        if (!a.save.flowChosen) {
            a.speaker.say("Oyun akışı şu an " + a.save.flowMode.displayName + ". Rahat, Dengeli ya da Gerçekçi seçmek için Oyun akışı düğmesine dokun.", false);
        }
    }

    @Override public String music() { return "music_menu"; }

    @Override public boolean onBack() { a.show(new MenuScreen(a)); return true; }
}
