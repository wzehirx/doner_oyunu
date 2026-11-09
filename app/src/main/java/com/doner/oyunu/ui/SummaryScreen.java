package com.doner.oyunu.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.doner.oyunu.MainActivity;
import com.doner.oyunu.engine.DaySummary;
import com.doner.oyunu.engine.Narrator;

import java.util.List;

/** Gün sonu özeti. Başarısızsa "Aynı günü tekrar dene". */
public final class SummaryScreen extends Screen {
    private final DaySummary summary;

    public SummaryScreen(MainActivity a, DaySummary s) {
        super(a);
        this.summary = s;
    }

    @Override public String title() {
        return summary.failed ? "Gün " + summary.day + " başarısız" : "Gün " + summary.day + " bitti";
    }

    @Override public String music() { return "music_menu"; }

    @Override public View create() {
        a.saveNow();
        LinearLayout root = rootColumn();
        TextView t = makeTitle(summary.failed ? "Gün " + summary.day + " başarısız" : "Gün " + summary.day + " bitti");
        t.setTextColor(summary.failed ? Theme.RED : Theme.GOLD);
        root.addView(t);

        // yıldız şeridi
        LinearLayout stars = new LinearLayout(ctx);
        stars.setOrientation(LinearLayout.HORIZONTAL);
        stars.setGravity(Gravity.CENTER_VERTICAL);
        stars.setBackground(Ui.round(ctx, Theme.CARD_HI, Theme.GOLD, 2, 16));
        stars.setPadding(dp(14), dp(10), dp(14), dp(10));
        StarsView sv = new StarsView(ctx, 28);
        sv.setValue(summary.endStars);
        stars.addView(sv, Ui.lp(ctx, Ui.WRAP, Ui.dp(ctx, 28), 0, 0, 12, 0));
        stars.addView(Ui.label(ctx, Narrator.fmtStars(summary.endStars), 26, Theme.GOLD, true));
        Ui.groupAsText(stars);
        stars.setContentDescription("Yıldız " + Narrator.fmtStars(summary.endStars) + " üzerinden beş");
        root.addView(stars, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 0, 0, 6));

        LinearLayout body = scrollBody(root);
        if (summary.failed) {
            body.addView(paragraph(evicted()
                    ? "Dükkân açılamadı: kira " + com.doner.oyunu.engine.Finance.EVICTION_DAYS + " gündür ödenmedi, tahliye kararı var. Faturalar ekranında kirayı öde; paran yetmezse Krediyle öde."
                    : "İtibarın çok düştü. Paran ve stokların gün başındaki haline döndü; aynı günü tekrar deneyebilirsin."));
        }
        List<String[]> rows = Texts.summaryRows(summary);
        for (String[] r : rows) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Ui.round(ctx, Theme.CARD, Theme.STROKE, 1, 14));
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            TextView l = Ui.label(ctx, r[0], 17, Theme.MUTED, false);
            TextView v = Ui.label(ctx, r[1], 19, Theme.CREAM, true);
            v.setGravity(Gravity.END);
            row.addView(l, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
            row.addView(v, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
            Ui.groupAsText(row);
            row.setContentDescription(r[0] + ": " + r[1]);
            body.addView(row, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 4));
        }
        if (!summary.failed && summary.newProductsNext != null && !summary.newProductsNext.isEmpty()) {
            LinearLayout c = card();
            c.setBackground(Ui.round(ctx, Theme.CARD_HI, Theme.GOLD, 3, 16));
            TextView tv = Ui.label(ctx, "Yarın yeni ürün: " + summary.newProductsNext, 19, Theme.GOLD, true);
            tv.setFocusable(true);
            c.addView(tv);
            body.addView(c, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 8, 0, 4));
        }

        if (!summary.failed && summary.newIngredientsNext != null && !summary.newIngredientsNext.isEmpty()) {
            LinearLayout c = card();
            c.setBackground(Ui.round(ctx, Theme.CARD_HI, Theme.GOLD, 3, 16));
            TextView tv = Ui.label(ctx, "Yarın yeni malzeme: " + summary.newIngredientsNext, 19, Theme.GOLD, true);
            tv.setFocusable(true);
            c.addView(tv);
            body.addView(c, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 8, 0, 4));
        }

        LinearLayout bar = column();
        // v2.5: açık fatura ya da maaş borcu varsa Faturalar ekranı (Öde / Ertele / Krediyle öde) en üstte
        if (evicted() || BillsBox.anythingDue(a.save)) {
            String bl = evicted() ? "Faturalar: kirayı öde" : "Faturalar";
            android.widget.Button bb = button(bl, Ui.GOLD, new Runnable() {
                @Override public void run() { a.show(new BillsScreen(a, Nav.SUMMARY)); }
            });
            bb.setContentDescription(bl + ", " + com.doner.oyunu.engine.Finance.openBills(a.save).size() + " açık fatura"
                    + (a.save.wageDebt > 0 ? ", maaş borcu" : ""));
            bar.addView(bb, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 6));
        }
        if (summary.failed && evicted()) {
            // tahliye: aynı günü denemek kira ödenmeden işe yaramaz; yol Faturalar
        } else if (summary.failed) {
            bar.addView(button("Aynı günü tekrar dene", Ui.PRIMARY, new Runnable() {
                @Override public void run() { a.show(new BriefingScreen(a)); }
            }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 6));
        } else {
            bar.addView(button("Markete git", Ui.PRIMARY, new Runnable() {
                @Override public void run() { a.show(new ShopScreen(a)); }
            }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 6));
        }
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(button(summary.failed ? "Markete git" : "Sonraki gün brifingi", Ui.SECONDARY, new Runnable() {
            @Override public void run() { a.show(summary.failed ? new ShopScreen(a) : new BriefingScreen(a)); }
        }), Ui.lp(ctx, 0, Ui.WRAP, 1f, 0, 0, 6, 0));
        row.addView(button("Ana menü", Ui.SECONDARY, new Runnable() {
            @Override public void run() { a.show(new MenuScreen(a)); }
        }), Ui.lp(ctx, 0, Ui.WRAP, 1f, 6, 0, 0, 0));
        bar.addView(row);
        root.addView(bar);
        return root;
    }

    private boolean evicted() { return "tahliye".equals(summary.failReason) && com.doner.oyunu.engine.UtilityStatus.of(a.save, a.save.day).evicted(); }

    @Override public boolean onBack() { a.show(new MenuScreen(a)); return true; }
}
