package com.doner.oyunu.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.doner.oyunu.MainActivity;
import com.doner.oyunu.engine.Catalog;
import com.doner.oyunu.engine.Ingredient;
import com.doner.oyunu.engine.Narrator;
import com.doner.oyunu.engine.SaveData;
import com.doner.oyunu.engine.Shop;
import com.doner.oyunu.engine.Upgrade;
import com.doner.oyunu.engine.Upgrades;

import java.util.ArrayList;
import java.util.List;

/** Market: malzeme, mutfak aletleri, restoran öğeleri. Satın almalar yerinde güncellenir (odak kaybolmaz). */
public final class ShopScreen extends Screen {
    private static final int TAB_ING = 0, TAB_KITCHEN = 1, TAB_RESTO = 2;

    private TextView moneyView;
    private LinearLayout tabContent;
    private Button[] tabs = new Button[3];
    private int tab = TAB_ING;
    private Button recommended;
    private final List<Runnable> refreshers = new ArrayList<>();

    public ShopScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Market"; }

    @Override public String music() { return "music_shop"; }

    @Override public View create() {
        a.saveNow();
        LinearLayout root = rootColumn();
        root.addView(makeTitle("Market"));

        LinearLayout stat = new LinearLayout(ctx);
        stat.setOrientation(LinearLayout.HORIZONTAL);
        stat.setGravity(Gravity.CENTER_VERTICAL);
        stat.setBackground(Ui.round(ctx, Theme.CARD, Theme.STROKE, 1.5f, 16));
        stat.setPadding(dp(14), dp(8), dp(14), dp(8));
        stat.addView(new IconView(ctx, Icons.Kind.COIN, 30), Ui.lp(ctx, Ui.dp(ctx, 30), Ui.dp(ctx, 30), 0, 0, 8, 0));
        moneyView = Ui.label(ctx, "", 22, Theme.GOLD, true);
        stat.addView(moneyView, Ui.lp(ctx, 0, Ui.WRAP, 1f, 0, 0, 0, 0));
        TextView dayTv = Ui.label(ctx, "Gün " + a.save.day, 18, Theme.CREAM, true);
        stat.addView(dayTv);
        Ui.groupAsText(stat);
        root.addView(stat, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 0, 0, 6));
        statGroup = stat;

        // sekmeler
        LinearLayout tabRow = new LinearLayout(ctx);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Malzeme", "Mutfak aletleri", "Restoran"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Button b = Ui.button(ctx, names[i], Ui.SECONDARY);
            b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
            b.setPadding(dp(4), dp(8), dp(4), dp(8));
            tap(b, new Runnable() { @Override public void run() { selectTab(idx, true); } });
            tabs[i] = b;
            tabRow.addView(b, Ui.lp(ctx, 0, Ui.WRAP, 1f, i == 0 ? 0 : 3, 0, i == 2 ? 0 : 3, 0));
        }
        root.addView(tabRow, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 0, 0, 4));

        android.widget.ScrollView sv = new android.widget.ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        tabContent = column();
        sv.addView(tabContent, new android.view.ViewGroup.LayoutParams(Ui.MATCH, Ui.WRAP));
        root.addView(sv, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));

        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(button("Güne başla", Ui.PRIMARY, new Runnable() {
            @Override public void run() { a.saveNow(); a.show(new GameScreen(a)); }
        }), Ui.lp(ctx, 0, Ui.WRAP, 2f, 0, 8, 6, 0));
        bar.addView(button("Geri", Ui.SECONDARY, new Runnable() {
            @Override public void run() { a.saveNow(); a.show(new BriefingScreen(a)); }
        }), Ui.lp(ctx, 0, Ui.WRAP, 1f, 6, 8, 0, 0));
        root.addView(bar);

        selectTab(TAB_ING, false);
        return root;
    }

    private LinearLayout statGroup;

    private void refreshMoney() {
        moneyView.setText(Texts.money(a.save.money));
        statGroup.setContentDescription("Kasada " + a.save.money + " lira, gün " + a.save.day);
    }

    private void selectTab(int idx, boolean announce) {
        tab = idx;
        refreshers.clear();
        tabContent.removeAllViews();
        String[] names = {"Malzeme", "Mutfak aletleri", "Restoran"};
        for (int i = 0; i < 3; i++) {
            boolean sel = i == idx;
            tabs[i].setSelected(sel);
            tabs[i].setBackground(sel ? Ui.buttonBg(ctx, Ui.GOLD) : Ui.buttonBg(ctx, Ui.SECONDARY));
            tabs[i].setTextColor(sel ? Theme.DARK : Theme.CREAM);
            tabs[i].setContentDescription(names[i] + " sekmesi" + (sel ? ", seçili" : ""));
        }
        if (idx == TAB_ING) buildIngredients(); else buildUpgrades(idx == TAB_KITCHEN ? Upgrade.Category.MUTFAK : Upgrade.Category.RESTORAN);
        refreshMoney();
        runRefreshers();
        if (announce) a.speaker.announce(names[idx] + " sekmesi açıldı.", true);
    }

    private void runRefreshers() {
        for (Runnable r : refreshers) r.run();
        refreshMoney();
    }

    // ------------------------------------------------------------------ malzemeler
    private void buildIngredients() {
        recommended = Ui.button(ctx, "Önerilen malzemeleri al", Ui.PRIMARY);
        tap(recommended, new Runnable() { @Override public void run() { buyRecommended(); } });
        tabContent.addView(recommended, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 6));
        refreshers.add(new Runnable() {
            @Override public void run() {
                int cost = Shop.recommendedCost(a.save);
                recommended.setText(cost > 0 ? "Önerilen malzemeleri al  (" + cost + " TL)" : "Önerilen malzemeleri al  (stok yeterli)");
                recommended.setContentDescription(Texts.recommendedDescription(a.save));
                Ui.dim(recommended, cost <= 0);
            }
        });
        for (final Ingredient ing : Shop.availableIngredients(a.save)) addIngredientRow(ing);
    }

    private void addIngredientRow(final Ingredient ing) {
        LinearLayout c = card();
        final TextView info = Ui.label(ctx, "", 18, Theme.CREAM, false);
        info.setFocusable(true);
        c.addView(info);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int[] qtys = {1, 5, 10};
        for (int i = 0; i < qtys.length; i++) {
            final int q = qtys[i];
            final Button b = Ui.button(ctx, "+" + q, Ui.SECONDARY);
            b.setContentDescription(Texts.buyDescription(ing, q, a.save));
            tap(b, new Runnable() { @Override public void run() { buyIngredient(ing, q); } });
            refreshers.add(new Runnable() {
                @Override public void run() { b.setContentDescription(Texts.buyDescription(ing, q, a.save)); }
            });
            row.addView(b, Ui.lp(ctx, 0, Ui.WRAP, 1f, i == 0 ? 0 : 4, 6, i == 2 ? 0 : 4, 0));
        }
        c.addView(row);
        tabContent.addView(c, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 5, 0, 5));
        refreshers.add(new Runnable() {
            @Override public void run() {
                int cap = new Upgrades(a.save).stockCap();
                int stock = a.save.inventory.get(ing.id);
                info.setText(ing.name + "\nStok " + stock + " / " + cap + " " + ing.unit + "  ·  birim " + ing.price + " TL");
                info.setContentDescription(Texts.ingredientInfo(ing, stock, cap));
            }
        });
    }

    private void buyIngredient(Ingredient ing, int q) {
        SaveData s = a.save;
        int before = s.inventory.get(ing.id);
        int moneyBefore = s.money;
        Shop.BuyResult r = Shop.buyIngredient(s, ing.id, q);
        if (r == Shop.BuyResult.OK) {
            int got = s.inventory.get(ing.id) - before;
            a.sound.play("sfx_buy");
            a.haptics.buy();
            runRefreshers();
            a.speaker.announce(Texts.buyResult(r, ing.name, got, moneyBefore - s.money)
                    + " Stok " + s.inventory.get(ing.id) + ". Kasada " + s.money + " lira.", true);
            a.saveNow();
        } else {
            a.sound.play("sfx_error");
            a.haptics.error();
            int real = Shop.purchasableQty(s, ing.id, q);
            a.speaker.announce(Texts.buyResult(r, ing.name, real, Shop.ingredientCost(ing.id, real)), true);
        }
    }

    private void buyRecommended() {
        SaveData s = a.save;
        int need = Shop.recommendedCost(s);
        if (need <= 0) {
            a.sound.play("sfx_error");
            a.speaker.announce("Stok zaten yeterli, alınacak malzeme yok.", true);
            return;
        }
        int spent = Shop.buyRecommended(s);
        if (spent <= 0) {
            a.sound.play("sfx_error");
            a.haptics.error();
            a.speaker.announce("Yeterli paran yok.", true);
            return;
        }
        a.sound.play("sfx_buy");
        a.haptics.buy();
        runRefreshers();
        int left = Shop.recommendedCost(s);
        a.speaker.announce("Önerilen malzemeler alındı, " + spent + " lira harcandı."
                + (left > 0 ? " Paran yetmediği için bir kısmı alınamadı." : "") + " Kasada " + s.money + " lira.", true);
        a.saveNow();
    }

    // ------------------------------------------------------------------ yükseltmeler
    private void buildUpgrades(Upgrade.Category cat) {
        for (final Upgrade u : Catalog.upgrades()) {
            if (u.category != cat) continue;
            LinearLayout c = card();
            final TextView name = Ui.label(ctx, "", 20, Theme.GOLD, true);
            final TextView desc = Ui.label(ctx, "", 16, Theme.CREAM, false);
            final TextView status = Ui.label(ctx, "", 16, Theme.MUTED, false);
            final LinearLayout infoBox = column();
            infoBox.addView(name);
            infoBox.addView(desc);
            infoBox.addView(status);
            Ui.groupAsText(infoBox);
            c.addView(infoBox);
            final Button buy = Ui.button(ctx, "", Ui.PRIMARY);
            tap(buy, new Runnable() { @Override public void run() { buyUpgrade(u); } });
            c.addView(buy, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
            tabContent.addView(c, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 5, 0, 5));
            refreshers.add(new Runnable() {
                @Override public void run() {
                    SaveData s = a.save;
                    int lv = s.level(u.id);
                    boolean can = Texts.upgradeBuyable(u, s);
                    name.setText(u.name + "  ·  Seviye " + lv + " / " + u.maxLevel());
                    desc.setText(Texts.upgradeDescription(u, s));
                    String st = Texts.upgradeStatus(u, s);
                    status.setText(st);
                    infoBox.setContentDescription(Texts.upgradeInfo(u, s) + " " + st + ".");
                    buy.setText(Texts.upgradeButton(u, s));
                    buy.setContentDescription(lv >= u.maxLevel() ? u.name + ", en üst seviye"
                            : s.day < u.unlockDayForNext(lv) ? "Satın al: " + u.name + ", " + u.unlockDayForNext(lv) + ". günde açılır"
                            : "Satın al: " + u.name + ", " + u.priceForNext(lv) + " lira" + (can ? "" : ", paran yetmiyor"));
                    buy.setBackground(Ui.buttonBg(ctx, can ? Ui.PRIMARY : Ui.SECONDARY));
                    buy.setTextColor(can ? Theme.DARK : Theme.CREAM);
                    Ui.dim(buy, !can);
                }
            });
        }
    }

    private void buyUpgrade(Upgrade u) {
        SaveData s = a.save;
        int price = u.priceForNext(s.level(u.id));
        Shop.BuyResult r = Shop.buyUpgrade(s, u.id);
        if (r == Shop.BuyResult.OK) {
            a.sound.play("sfx_buy");
            a.haptics.buy();
            runRefreshers();
            a.speaker.announce(u.name + " seviye " + s.level(u.id) + " satın alındı, " + price
                    + " lira. Kasada " + s.money + " lira.", true);
            a.saveNow();
        } else {
            a.sound.play("sfx_error");
            a.haptics.error();
            String msg = r == Shop.BuyResult.LOCKED
                    ? u.name + " kilitli: " + u.unlockDayForNext(s.level(u.id)) + ". günde açılır."
                    : Texts.buyResult(r, u.name, 0, price);
            a.speaker.announce(msg, true);
        }
    }

    @Override public boolean onBack() { a.saveNow(); a.show(new BriefingScreen(a)); return true; }

    @Override public void onExit() { a.saveNow(); }
}
