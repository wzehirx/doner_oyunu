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
        java.util.Set<String> warn = new java.util.HashSet<>(com.doner.oyunu.engine.Finance.notices(a.save, a.save.day));
        String cut = Texts.utilitySummary(a.save);
        if (!cut.isEmpty()) warn.add(cut);
        for (String l : lines) {
            LinearLayout c = card();
            TextView tv = Ui.label(ctx, l, 18, Theme.CREAM, false);
            tv.setFocusable(true);
            if (l.startsWith("Oyun akışı") || l.startsWith("Seçim: Oyun akışı")) flowTv = tv;
            if (warn.contains(l)) { // v2.5: fatura/kesinti/maaş uyarısı: kırmızı çerçeve, en üstte
                c.setBackground(Ui.round(ctx, Theme.CARD_HI, Theme.RED, 3, 16));
                tv.setTextColor(Theme.GOLD);
                tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            } else if (l.startsWith("Yeni ürün") || l.startsWith("Yeni malzeme") || l.startsWith("Yeni boy") || l.startsWith("Yeni müşteri")) {
                c.setBackground(Ui.round(ctx, Theme.CARD_HI, Theme.GOLD, 3, 16));
                tv.setTextColor(Theme.GOLD);
                tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            }
            c.addView(tv);
            body.addView(c, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 5, 0, 5));
        }

        LinearLayout bar = column();
        // v2.5: Faturalar, Fiyatlar, Banka (tek dokunuş; Faturalar açık fatura sayısını söyler)
        LinearLayout fin = new LinearLayout(ctx);
        fin.setOrientation(LinearLayout.HORIZONTAL);
        int open = com.doner.oyunu.engine.Finance.openBills(a.save).size();
        android.widget.Button billsBtn = button(open > 0 ? "Faturalar (" + open + ")" : "Faturalar", open > 0 || a.save.wageDebt > 0 ? Ui.GOLD : Ui.SECONDARY, new Runnable() {
            @Override public void run() { a.show(new BillsScreen(a, Nav.BRIEFING)); }
        });
        billsBtn.setContentDescription(open > 0 ? "Faturalar, " + open + " açık fatura" : "Faturalar, açık fatura yok");
        android.widget.Button pricesBtn = button("Fiyatlar", Ui.SECONDARY, new Runnable() {
            @Override public void run() { a.show(new PriceScreen(a, Nav.BRIEFING)); }
        });
        android.widget.Button bankBtn = button("Banka", Ui.SECONDARY, new Runnable() {
            @Override public void run() { a.show(new BankScreen(a, Nav.BRIEFING)); }
        });
        for (android.widget.Button b : new android.widget.Button[]{billsBtn, pricesBtn, bankBtn}) {
            b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
            b.setPadding(dp(4), dp(8), dp(4), dp(8));
        }
        fin.addView(billsBtn, Ui.lp(ctx, 0, Ui.WRAP, 1.2f, 0, 0, 3, 0));
        fin.addView(pricesBtn, Ui.lp(ctx, 0, Ui.WRAP, 1f, 3, 0, 3, 0));
        fin.addView(bankBtn, Ui.lp(ctx, 0, Ui.WRAP, 1f, 3, 0, 0, 0));
        bar.addView(fin, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 2));
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
        if (evicted()) {
            // v2.5 tahliye: dükkân açılamaz; "Güne başla" yerine faturalara yönlendir
            row.addView(button("Faturalar: kirayı öde", Ui.GOLD, new Runnable() {
                @Override public void run() { a.show(new BillsScreen(a, Nav.BRIEFING)); }
            }), Ui.lp(ctx, 0, Ui.WRAP, 1f, 0, 0, 6, 0));
        } else {
            row.addView(button("Güne başla", Ui.GOLD, new Runnable() {
                @Override public void run() { a.save.flowChosen = true; a.saveNow(); a.show(new GameScreen(a)); }
            }), Ui.lp(ctx, 0, Ui.WRAP, 1f, 0, 0, 6, 0));
        }
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

    private boolean evicted() { return com.doner.oyunu.engine.UtilityStatus.of(a.save, a.save.day).evicted(); }

    /**
     * v2.5: fatura/kesinti/maaş uyarıları kısa ve sesli (önce en ağır; motor gün açılışında yeniden söyler). İlk seferde
     * (akış henüz seçilmediyse) kısa sesli soru.
     */
    @Override public void onEnter() {
        List<String> n = com.doner.oyunu.engine.Finance.notices(a.save, a.save.day);
        if (!n.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String x : n) { if (sb.length() > 0) sb.append(' '); sb.append(x); }
            if (evicted()) sb.append(" Faturalar düğmesinden kirayı öde.");
            a.speaker.say(sb.toString(), false, com.doner.oyunu.engine.Narrator.CAT_BILLS, null);
        }
        if (!a.save.flowChosen) {
            a.speaker.say("Oyun akışı şu an " + a.save.flowMode.displayName + ". Rahat, Dengeli ya da Gerçekçi seçmek için Oyun akışı düğmesine dokun.", false);
        }
    }

    @Override public String music() { return "music_menu"; }

    @Override public boolean onBack() { a.show(new MenuScreen(a)); return true; }
}
