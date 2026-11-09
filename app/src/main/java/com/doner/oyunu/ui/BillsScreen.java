package com.doner.oyunu.ui;

import android.view.View;
import android.widget.LinearLayout;

import com.doner.oyunu.MainActivity;
import com.doner.oyunu.engine.Finance;
import com.doner.oyunu.engine.SaveData;
import com.doner.oyunu.engine.UtilityStatus;

/**
 * v2.5 Faturalar ekranı (gün sonu özetinden, brifingten, marketten; tahliyede zorunlu yol): {@link BillsBox} (Öde /
 * Ertele / Krediyle öde, Maaşı öde), "Faturaları oku", Banka; altta Markete git / Güne başla (tahliyede Güne başla yok).
 */
public final class BillsScreen extends Screen {
    private final int from;
    private BillsBox box;
    private LinearLayout bar;

    public BillsScreen(MainActivity a, int from) {
        super(a);
        this.from = from;
    }

    @Override public String title() { return "Faturalar"; }

    @Override public View create() {
        a.saveNow();
        LinearLayout root = rootColumn();
        root.addView(makeTitle("Faturalar"));
        LinearLayout body = scrollBody(root);
        box = new BillsBox(ctx, new BillsBox.Host() {
            @Override public SaveData save() { return a.save; }
            @Override public int day() { return a.save.day; }
            @Override public Finance.PayResult pay(int billId) { return Finance.pay(a.save, billId); }
            @Override public Finance.PayResult payWages() { return Finance.payWages(a.save); }
            @Override public boolean fullOptions() { return true; }
            @Override public void tap(View v, Runnable r) { BillsScreen.this.tap(v, r); }
            @Override public void say(String text) { a.speaker.force(text); }
            @Override public void focus(View v) { a.focusOn(v); }
            @Override public void sound(String sfx, boolean ok) {
                a.sound.play(sfx);
                if (ok) a.haptics.buy(); else a.haptics.error();
            }
            @Override public void changed() { a.saveNow(); buildBar(); }
        });
        body.addView(box);
        body.addView(button("Faturaları oku", Ui.SECONDARY, new Runnable() {
            @Override public void run() { a.speaker.force(BillsBox.readText(a.save, a.save.day)); }
        }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 8, 0, 4));
        bar = column();
        root.addView(bar);
        buildBar();
        return root;
    }

    private boolean evicted() { return UtilityStatus.of(a.save, a.save.day).evicted(); }

    /** Alt çubuk: Banka + Geri/Markete git; tahliye yoksa Güne başla. Tahliye kalkınca (kira ödenince) yeniden kurulur. */
    private void buildBar() {
        if (bar == null) return;
        bar.removeAllViews();
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(button("Banka", Ui.SECONDARY, new Runnable() {
            @Override public void run() { a.saveNow(); a.show(new BankScreen(a, Nav.BILLS + from)); }
        }), Ui.lp(ctx, 0, Ui.WRAP, 1f, 0, 8, 6, 0));
        if (from == Nav.SHOP) {
            row.addView(button("Geri", Ui.SECONDARY, new Runnable() {
                @Override public void run() { back(); }
            }), Ui.lp(ctx, 0, Ui.WRAP, 1f, 6, 8, 0, 0));
        } else {
            row.addView(button("Markete git", Ui.PRIMARY, new Runnable() {
                @Override public void run() { a.saveNow(); a.show(new ShopScreen(a)); }
            }), Ui.lp(ctx, 0, Ui.WRAP, 1f, 6, 8, 0, 0));
        }
        bar.addView(row);
        if (!evicted()) {
            bar.addView(button("Güne başla", Ui.GOLD, new Runnable() {
                @Override public void run() { a.save.flowChosen = true; a.saveNow(); a.show(new GameScreen(a)); }
            }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        }
    }

    @Override public void onEnter() {
        if (evicted()) a.speaker.say("Tahliye kararı var: kirayı ödemeden dükkân açılamaz. Paran yetmezse Krediyle öde.", true);
    }

    private void back() { a.saveNow(); a.show(Nav.make(a, from == Nav.SUMMARY ? Nav.BRIEFING : from)); }

    @Override public boolean onBack() { back(); return true; }

    @Override public void onExit() { a.saveNow(); }
}
