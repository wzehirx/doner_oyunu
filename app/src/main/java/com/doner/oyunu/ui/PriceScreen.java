package com.doner.oyunu.ui;

import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.doner.oyunu.MainActivity;
import com.doner.oyunu.engine.Catalog;
import com.doner.oyunu.engine.Narrator;
import com.doner.oyunu.engine.Pricing;
import com.doner.oyunu.engine.Product;
import com.doner.oyunu.engine.SaveData;
import com.doner.oyunu.engine.UtilityStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * v2.5 Fiyatlar ekranı (brifing ve marketten): bugünün menüsündeki her ürün için tek satır ("Ayran, 25 lira, yüzde 100")
 * ve −%5 / +%5 düğmeleri; satırın TalkBack Eylemleri "Fiyatı artır" / "Fiyatı azalt" aynı işi yapar. Tahmini müşteri
 * etkisi ({@link Pricing#arrivalMultiplier}, oyunda {@code getPriceArrivalMultiplier}) metinle okunur. Değişiklik kayda
 * hemen yazılır, gün başında geçerli olur. Görünümler bir kez kurulur, değişince yerinde güncellenir (odak kaybolmaz).
 */
public final class PriceScreen extends Screen {
    private final int from;
    private TextView demandTv;
    private final List<Runnable> refreshers = new ArrayList<>();

    public PriceScreen(MainActivity a, int from) {
        super(a);
        this.from = from;
    }

    @Override public String title() { return "Fiyatlar"; }

    @Override public String music() { return from == Nav.SHOP ? "music_shop" : "music_menu"; }

    /** Bugünün menüsü (kesintide geçici olarak satılamayanlar da fiyatlanır). */
    static List<Product> menu(SaveData s) { return Catalog.productsForDay(s.day); }

    @Override public View create() {
        LinearLayout root = rootColumn();
        root.addView(makeTitle("Fiyatlar"));
        LinearLayout body = scrollBody(root);
        final SaveData s = a.save;

        body.addView(paragraph(Texts.PRICE_NOTE));
        demandTv = Ui.label(ctx, "", 18, Theme.GOLD, true);
        demandTv.setFocusable(true);
        demandTv.setPadding(dp(4), dp(4), dp(4), dp(8));
        body.addView(demandTv);
        Button demandBtn = button("Tahmini müşteri etkisi", Ui.SECONDARY, new Runnable() {
            @Override public void run() { a.speaker.force(Narrator.demandText(a.save)); }
        });
        demandBtn.setContentDescription("Tahmini müşteri etkisi");
        body.addView(demandBtn, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 2, 0, 6));
        Button resetBtn = button("Referans fiyata dön", Ui.SECONDARY, new Runnable() {
            @Override public void run() { resetAll(); }
        });
        resetBtn.setContentDescription("Referans fiyata dön, hepsi yüzde 100");
        body.addView(resetBtn, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 2, 0, 8));

        UtilityStatus u = UtilityStatus.of(s, s.day);
        for (final Product p : menu(s)) addRow(body, p, !u.productAvailable(p));

        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(button("Fiyatları oku", Ui.SECONDARY, new Runnable() {
            @Override public void run() { a.speaker.force(readAll(a.save)); }
        }), Ui.lp(ctx, 0, Ui.WRAP, 1f, 0, 8, 6, 0));
        bar.addView(button("Geri", Ui.PRIMARY, new Runnable() {
            @Override public void run() { back(); }
        }), Ui.lp(ctx, 0, Ui.WRAP, 1f, 6, 8, 0, 0));
        root.addView(bar);
        refreshAll();
        return root;
    }

    /** Tüm fiyatların sesli özeti (açık okuma). */
    static String readAll(SaveData s) {
        StringBuilder sb = new StringBuilder();
        for (Product p : menu(s)) sb.append(Narrator.priceText(s, p)).append(' ');
        return sb.append(Narrator.demandText(s)).toString();
    }

    private void addRow(LinearLayout body, final Product p, boolean off) {
        LinearLayout c = card();
        final Button info = Ui.button(ctx, "", Ui.SECONDARY);
        info.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tap(info, new Runnable() { @Override public void run() { a.speaker.force(Narrator.priceText(a.save, p)); } });
        ActionMenu.attachSimple(info, new String[]{"Fiyatı artır", "Fiyatı azalt"}, new ActionMenu.SimpleHandler() {
            @Override public void onAction(int index) { change(p, index == 0 ? +1 : -1); }
        });
        c.addView(info, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 0, 0, 4));
        if (off) {
            TextView note = Ui.label(ctx, "Bugün kesinti yüzünden satılmaz.", 15, Theme.MUTED, false);
            note.setFocusable(true);
            c.addView(note);
        }
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final Button minus = Ui.button(ctx, "−  yüzde 5", Ui.SECONDARY);
        final Button plus = Ui.button(ctx, "+  yüzde 5", Ui.SECONDARY);
        minus.setContentDescription(shortName(p) + " fiyatını azalt");
        plus.setContentDescription(shortName(p) + " fiyatını artır");
        tap(minus, new Runnable() { @Override public void run() { change(p, -1); } });
        tap(plus, new Runnable() { @Override public void run() { change(p, +1); } });
        row.addView(minus, Ui.lp(ctx, 0, Ui.WRAP, 1f, 0, 4, 4, 0));
        row.addView(plus, Ui.lp(ctx, 0, Ui.WRAP, 1f, 4, 4, 0, 0));
        c.addView(row);
        body.addView(c, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 5, 0, 5));
        refreshers.add(new Runnable() {
            @Override public void run() {
                SaveData s = a.save;
                String vis = Texts.priceRowVisual(s, p);
                if (!vis.contentEquals(info.getText())) info.setText(vis);
                Ui.setDesc(info, Texts.priceRowLabel(s, p));
                int pc = Pricing.percent(s, p.id);
                // v2.6: referanstan ayrılan fiyat altın renkle öne çıkar (durum metinde de var: "yüzde 110")
                info.setTextColor(pc != Pricing.DEFAULT_PERCENT ? Theme.GOLD : Theme.CREAM);
                Ui.dim(minus, pc <= Pricing.MIN_PERCENT);
                Ui.dim(plus, pc >= Pricing.MAX_PERCENT);
            }
        });
    }

    /** Ürün adı ("… fiyatını artır" etiketi en uzun adla da ≤60). */
    private static String shortName(Product p) { return p.name; }

    private void change(Product p, int dir) {
        SaveData s = a.save;
        int before = Pricing.percent(s, p.id);
        int after = dir > 0 ? Pricing.raise(s, p.id) : Pricing.lower(s, p.id);
        if (after == before) {
            a.sound.play("sfx_error");
            a.haptics.error();
            a.speaker.force((dir > 0 ? "En yüksek fiyat. " : "En düşük fiyat. ") + Narrator.priceText(s, p));
            return;
        }
        a.saveNow();
        refreshAll();
        a.speaker.force(Narrator.priceText(s, p) + " " + Narrator.demandText(s));
    }

    private void resetAll() {
        SaveData s = a.save;
        if (Pricing.allDefault(s)) {
            a.sound.play("sfx_error");
            a.speaker.force("Fiyatlar zaten referans fiyatta.");
            return;
        }
        Pricing.setAll(s, Pricing.DEFAULT_PERCENT);
        a.saveNow();
        refreshAll();
        a.speaker.force("Tüm fiyatlar referans fiyata döndü, yüzde 100. " + Narrator.demandText(s));
    }

    private void refreshAll() {
        for (Runnable r : refreshers) r.run();
        String d = Narrator.demandText(a.save);
        demandTv.setText(d);
        Ui.setDesc(demandTv, d);
    }

    private void back() { a.saveNow(); a.show(Nav.make(a, from)); }

    @Override public boolean onBack() { back(); return true; }

    @Override public void onExit() { a.saveNow(); }
}
