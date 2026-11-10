package com.doner.oyunu.ui;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.doner.oyunu.MainActivity;
import com.doner.oyunu.engine.Narrator;

import java.util.List;

/**
 * Gün brifingi: yeni ürünler, hedef, oyun akışı (v0.7; buradan da değişir), öğretici notu.
 *
 * <p><b>v2.6:</b> önemli satırlar (uyarı, kasa, fatura, yeni ürün/malzeme, stok) üstte; her gün aynı olan açıklamalar
 * (hedef, menü, hazırlık, süre baskısı, TalkBack notu) 2. günden itibaren "Ayrıntıları göster" düğmesinin altında
 * (TalkBack'te 12-15 satır yerine 5-7). Fatura uyarıları sesli olarak KISA özetle söylenir ({@link Texts#noticesBrief});
 * oyun ekranı aynı günün uyarısını yinelemez. Geciken faturalar otomatik ödeme açıksa burada ödenir. İflasta "Güne başla"
 * yerine "Yeni oyun"; kasa stoğa yetmiyor ve banka limiti de yoksa "Acil destek kredisi al".
 */
public final class BriefingScreen extends Screen {
    private TextView flowTv;
    private android.widget.Button flowBtn;
    /** Gün açılışında otomatik ödenen faturaların cümlesi (yoksa ""). */
    private String autoPaidLine = "";
    private com.doner.oyunu.engine.Finance.Bankruptcy bankrupt = com.doner.oyunu.engine.Finance.Bankruptcy.NONE;
    private boolean aid;

    /** Her gün aynı olan açıklama satırı mı (2. günden itibaren "Ayrıntılar" altında). */
    static boolean isDetailLine(String l) {
        return l.startsWith("Hedef:") || l.startsWith("Bugünkü menü:") || l.startsWith("Önce hazırlık aşaması")
                || l.startsWith("Süre baskısı:") || l.startsWith("İstasyon seçeneklerini") || l.startsWith("Oyun akışı:")
                || l.startsWith("Bugün yeni ürün yok") || l.startsWith("Stoğun bugün için yeterli");
    }

    public BriefingScreen(MainActivity a) { super(a); }

    @Override public String title() { return "Gün " + a.save.day + " brifingi"; }

    @Override public View create() {
        // v2.6: otomatik ödeme açıksa geciken faturalar (kasa yetiyorsa) gün açılmadan ödenir; kesinti hiç başlamaz
        List<com.doner.oyunu.engine.Bill> paid = com.doner.oyunu.engine.Finance.autoPayAtOpen(a.save);
        if (!paid.isEmpty()) {
            List<String> n = new java.util.ArrayList<>();
            for (com.doner.oyunu.engine.Bill b : paid) n.add(b.type.displayName.toLowerCase(new java.util.Locale("tr")) + " " + b.getPaid() + " lira");
            autoPaidLine = "Otomatik ödendi: " + Texts.join(n) + ".";
            a.saveNow();
        }
        bankrupt = com.doner.oyunu.engine.Finance.bankruptcy(a.save);
        aid = bankrupt == com.doner.oyunu.engine.Finance.Bankruptcy.NONE && com.doner.oyunu.engine.Bank.emergencyAidAvailable(a.save)
                && com.doner.oyunu.engine.Bank.available(a.save) < com.doner.oyunu.engine.Bank.emergencyAidAmount(a.save);
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
        if (bankrupt != com.doner.oyunu.engine.Finance.Bankruptcy.NONE) body.addView(alertCard(Texts.bankruptcyText(bankrupt, a.save.day)), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 5, 0, 5));
        if (aid) body.addView(alertCard(Texts.emergencyAidNote(a.save)), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 5, 0, 5));
        if (!autoPaidLine.isEmpty()) {
            LinearLayout c = card();
            TextView tv = Ui.label(ctx, autoPaidLine, 18, Theme.GREEN, true);
            tv.setFocusable(true);
            c.addView(tv);
            body.addView(c, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 5, 0, 5));
        }
        final List<View> details = new java.util.ArrayList<>();
        boolean collapse = a.save.day > 1 && a.save.flowChosen;
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
            if (collapse && !warn.contains(l) && isDetailLine(l)) { c.setVisibility(View.GONE); details.add(c); }
        }
        if (!details.isEmpty()) {
            final android.widget.Button more = Ui.button(ctx, "Ayrıntıları göster", Ui.SECONDARY);
            more.setContentDescription("Ayrıntıları göster, " + details.size() + " satır");
            tap(more, new Runnable() {
                @Override public void run() {
                    for (View d : details) d.setVisibility(View.VISIBLE);
                    more.setVisibility(View.GONE);
                    // düğme kalktı: odak açılan ilk satıra, tek duyuru
                    a.focusThenSay(((android.view.ViewGroup) details.get(0)).getChildAt(0), details.size() + " ayrıntı satırı açıldı.");
                }
            });
            body.addView(more, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 5, 0, 5));
        }

        LinearLayout bar = column();
        if (aid) {
            android.widget.Button ab = button(Texts.emergencyAidLabel(a.save), Ui.GOLD, new Runnable() {
                @Override public void run() { takeAid(); }
            });
            bar.addView(ab, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 2));
        }
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
            b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
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
        if (bankrupt != com.doner.oyunu.engine.Finance.Bankruptcy.NONE) {
            // v2.6 iflas: çıkış yolu yok; yeni oyun (ayarlar ve en iyi gün korunur)
            row.addView(button("Yeni oyun", Ui.GOLD, new Runnable() {
                @Override public void run() { a.restartAfterBankruptcy(); a.startBriefing(); }
            }), Ui.lp(ctx, 0, Ui.WRAP, 1f, 0, 0, 6, 0));
        } else if (evicted()) {
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

    private LinearLayout alertCard(String text) {
        LinearLayout c = card();
        c.setBackground(Ui.round(ctx, Theme.CARD_HI, Theme.RED, 3, 16));
        TextView tv = Ui.label(ctx, text, 19, Theme.GOLD, true);
        tv.setFocusable(true);
        c.addView(tv);
        return c;
    }

    /** Acil destek kredisi: para kasaya girer, brifing yenilenir. */
    private void takeAid() {
        int amount = com.doner.oyunu.engine.Bank.emergencyAidAmount(a.save);
        com.doner.oyunu.engine.Bank.Result r = com.doner.oyunu.engine.Bank.takeEmergencyAid(a.save);
        if (r == com.doner.oyunu.engine.Bank.Result.OK) {
            a.sound.play("sfx_coin");
            a.haptics.buy();
            a.saveNow();
            aidTaken = "Acil destek kredisi alındı: " + amount + " lira. Kasada " + a.save.money + " lira. Markete uğra.";
            BriefingScreen next = new BriefingScreen(a);
            next.aidTaken = aidTaken;
            a.show(next);
        } else {
            a.sound.play("sfx_error");
            a.haptics.error();
            a.speaker.force("Acil destek alınamadı. " + Texts.bankResult(r));
        }
    }

    /** Yenilenen brifingin girişte söyleyeceği sonuç (acil destek alındı). */
    private String aidTaken;

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
     * Girişte sesli: iflas (varsa yalnızca o); yoksa otomatik ödenenler + fatura uyarılarının KISA özeti (v2.6; uzun
     * metinler kartlarda ve Faturalar ekranında; oyun ekranı aynı günün uyarısını yinelemez). İlk seferde (akış henüz
     * seçilmediyse) kısa sesli soru.
     */
    @Override public void onEnter() {
        if (bankrupt != com.doner.oyunu.engine.Finance.Bankruptcy.NONE) {
            a.speaker.say(Texts.bankruptcyText(bankrupt, a.save.day), true);
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (aidTaken != null) sb.append(aidTaken);
        if (!autoPaidLine.isEmpty()) sb.append(sb.length() > 0 ? " " : "").append(autoPaidLine);
        String brief = Texts.noticesBrief(a.save, a.save.day);
        if (!brief.isEmpty()) {
            sb.append(sb.length() > 0 ? " " : "").append(brief);
            if (evicted()) sb.append(" Faturalar düğmesinden kirayı öde.");
            a.briefedNoticesDay = a.save.day;
        }
        if (aid) sb.append(sb.length() > 0 ? " " : "").append(Texts.emergencyAidNote(a.save));
        if (sb.length() > 0) a.speaker.say(sb.toString(), false, com.doner.oyunu.engine.Narrator.CAT_BILLS, null);
        if (!a.save.flowChosen) {
            a.speaker.say("Oyun akışı şu an " + a.save.flowMode.displayName + ". Rahat, Dengeli ya da Gerçekçi seçmek için Oyun akışı düğmesine dokun.", false);
        }
    }

    @Override public String music() { return "music_menu"; }

    @Override public boolean onBack() { a.show(new MenuScreen(a)); return true; }
}
