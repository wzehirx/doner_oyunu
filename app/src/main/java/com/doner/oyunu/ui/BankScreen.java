package com.doner.oyunu.ui;

import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.doner.oyunu.MainActivity;
import com.doner.oyunu.engine.Bank;
import com.doner.oyunu.engine.Loan;
import com.doner.oyunu.engine.Narrator;
import com.doner.oyunu.engine.SaveData;

import java.util.HashMap;
import java.util.Map;

/**
 * v2.5 Banka ekranı (brifing, market, faturalar ekranından): kredi notu, limit, alınabilir tutar, faiz ({@link
 * Narrator#bankText}); tutar (en az 500; {@link AdjustControl}: −/+ 500, kaydırma çubuğu, yazarak 100'lük) ve vade (5/10/20 gün; seçenekli AdjustControl) seçimi, teklif ({@link Narrator#quoteText}),
 * Kredi al; aktif krediler (kalan borç, taksit, vade) için Erken kapat ve Gecikmiş taksiti öde. Her sonuç sesli ve
 * metinle.
 *
 * <p><b>v2.6:</b> bilgi satırı kısa (not, alınabilir, borç, kasa; ayrıntı "Bankayı oku"da); teklif "günlük taksit" önde.
 * Tek odak + tek duyuru: Kredi al / tutar / vade düğmeleri yerinde kalır, odak taşınmaz, yalnızca sonuç söylenir. Kredi
 * kapatılınca kartı kalkar: odak kredi listesinin ilk satırına taşınır, sonuç odak okumasının arkasından söylenir.
 */
public final class BankScreen extends Screen {
    private final int from;
    private int amount = Bank.MIN_LOAN;
    private int term = 10;
    private TextView infoTv, quoteTv;
    private Button takeBtn;
    private AdjustControl amountCtl, termCtl;
    private LinearLayout loansBox;

    public BankScreen(MainActivity a, int from) {
        super(a);
        this.from = from;
    }

    @Override public String title() { return "Banka"; }

    @Override public String music() { return from == Nav.SHOP ? "music_shop" : "music_menu"; }

    /** Seçilebilecek en yüksek tutar: alınabilir limit (100'e aşağı yuvarlı); limit yoksa en az tutar. */
    static int maxAmount(SaveData s) { return Math.max(Bank.MIN_LOAN, (Bank.available(s) / Bank.STEP) * Bank.STEP); }

    @Override public View create() {
        LinearLayout root = rootColumn();
        root.addView(makeTitle("Banka"));
        LinearLayout body = scrollBody(root);

        infoTv = Ui.label(ctx, "", 19, Theme.GOLD, true);
        infoTv.setFocusable(true);
        infoTv.setPadding(dp(4), dp(4), dp(4), dp(8));
        body.addView(infoTv);
        body.addView(button("Bankayı oku", Ui.SECONDARY, new Runnable() {
            @Override public void run() { a.speaker.force(readAll(a.save)); }
        }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 2, 0, 6));

        // ---- v2.6 acil destek (kasa önerilen stoğa yetmiyorsa; limit aranmaz): sıfır kasa / boş stok çıkışı
        aidBox = column();
        body.addView(aidBox);

        // ---- kredi al
        body.addView(sectionHeading("Kredi al"));
        // v2.7: tutar ve vade AdjustControl: − [kaydırma çubuğu] + ve değere dokununca sayısal klavye
        LinearLayout amountCard = card();
        amountCtl = new AdjustControl(ctx, adjustHost(), "Tutar", Bank.MIN_LOAN, maxAmount(a.save), Bank.STEP, 500,
                new AdjustControl.Format() {
            @Override public String shown(int v) { return v + " lira"; }
            @Override public String spoken(int v) {
                Bank.Quote q = Bank.quote(a.save, v, term);
                return "Tutar " + v + " lira." + (q == null ? "" : " Günlük taksit " + q.installment + " lira.");
            }
            @Override public String unit() { return "lira"; }
        });
        amountCtl.setButtonDescriptions("Tutarı azalt", "Tutarı artır");
        amountCtl.setListener(new AdjustControl.Listener() {
            @Override public void onChanged(int v, boolean settled) { amount = v; refresh(); }
        });
        amountCard.addView(amountCtl);
        body.addView(amountCard, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 4));

        LinearLayout termCard = card();
        termCtl = AdjustControl.choices(ctx, adjustHost(), "Vade", Bank.TERMS, new AdjustControl.Format() {
            @Override public String shown(int v) { return v + " gün"; }
            @Override public String spoken(int v) {
                Bank.Quote q = Bank.quote(a.save, amount, v);
                return v + " gün vade." + (q == null ? "" : " Günlük taksit " + q.installment + " lira, toplam " + q.total + " lira.");
            }
            @Override public String unit() { return "gün"; }
        });
        termCtl.setButtonDescriptions("Vadeyi azalt", "Vadeyi artır");
        termCtl.setListener(new AdjustControl.Listener() {
            @Override public void onChanged(int v, boolean settled) { term = v; refresh(); }
        });
        termCard.addView(termCtl);
        body.addView(termCard, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 4));

        quoteTv = Ui.label(ctx, "", 17, Theme.GOLD, false);
        quoteTv.setFocusable(true);
        quoteTv.setPadding(dp(4), dp(6), dp(4), dp(6));
        body.addView(quoteTv);
        takeBtn = button("", Ui.PRIMARY, new Runnable() { @Override public void run() { takeLoan(); } });
        body.addView(takeBtn, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 2, 0, 8));

        // ---- aktif krediler
        body.addView(sectionHeading("Kredilerin"));
        loansBox = column();
        body.addView(loansBox);

        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        if (!Nav.isBills(from)) {
            bar.addView(button("Faturalar", Ui.SECONDARY, new Runnable() {
                @Override public void run() { a.saveNow(); a.show(new BillsScreen(a, from)); }
            }), Ui.lp(ctx, 0, Ui.WRAP, 1f, 0, 8, 6, 0));
        }
        bar.addView(button("Geri", Ui.PRIMARY, new Runnable() {
            @Override public void run() { back(); }
        }), Ui.lp(ctx, 0, Ui.WRAP, 1f, !Nav.isBills(from) ? 6 : 0, 8, 0, 0));
        root.addView(bar);

        amount = Math.min(3000, maxAmount(a.save));
        amount = Math.max(Bank.MIN_LOAN, amount - amount % 500);
        refresh();
        return root;
    }

    /**
     * Bankanın sesli ÖZETİ (v2.6): not, limit, faiz, kredi sayısı ve borç; kredi varsa bugünkü taksit toplamı. Tek tek
     * krediler kendi satırlarına dokununca okunur (eskiden hepsi tek uzun duyuruydu).
     */
    static String readAll(SaveData s) {
        String t = Narrator.bankText(s);
        if (!s.loans.isEmpty()) t += " Bugün gün sonunda kasadan " + Narrator.fmtMoney(Bank.dueToday(s)) + " taksit alınır.";
        return t;
    }

    private LinearLayout aidBox;
    private Boolean aidShown;

    /** Acil destek bölümü: yalnızca alınabiliyorsa görünür (durum değişince kurulur). */
    private void buildAid() {
        boolean on = Bank.emergencyAidAvailable(a.save);
        if (aidShown != null && aidShown == on) return;
        aidShown = on;
        aidBox.removeAllViews();
        if (!on) return;
        LinearLayout c = card();
        c.setBackground(Ui.round(ctx, Theme.CARD_HI, Theme.GOLD, 3, 16));
        TextView t = Ui.label(ctx, Texts.emergencyAidNote(a.save), 17, Theme.CREAM, false);
        t.setFocusable(true);
        c.addView(t);
        c.addView(button(Texts.emergencyAidLabel(a.save), Ui.GOLD, new Runnable() {
            @Override public void run() { takeAid(); }
        }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        aidBox.addView(c, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 4));
    }

    private void takeAid() {
        SaveData s = a.save;
        int amt = Bank.emergencyAidAmount(s);
        Bank.Result r = Bank.takeEmergencyAid(s);
        if (r == Bank.Result.OK) {
            a.sound.play("sfx_coin");
            a.haptics.buy();
            a.saveNow();
            refresh();
            // bölüm kalktı: odak bilgi satırına, sonra tek duyuru
            a.focusThenSay(infoTv, "Acil destek kredisi alındı: " + amt + " lira, " + Bank.EMERGENCY_TERM + " gün. Kasada " + s.money + " lira.", com.doner.oyunu.engine.Importance.NORMAL);
        } else {
            a.sound.play("sfx_error");
            a.haptics.error();
            a.speaker.result("Acil destek alınamadı. " + Texts.bankResult(r), com.doner.oyunu.engine.Importance.CRITICAL);
        }
    }

    private void refresh() {
        SaveData s = a.save;
        String info = Texts.bankHeader(s);
        infoTv.setText(info);
        Ui.setDesc(infoTv, info);
        amountCtl.setRange(Bank.MIN_LOAN, maxAmount(s));
        amountCtl.setValue(amount);
        amount = amountCtl.getValue();
        termCtl.setValue(term);
        Bank.Quote q = Bank.quote(s, amount, term);
        String qt = q == null ? "Teklif yok." : Texts.quoteLine(q);
        boolean can = q != null && amount <= Bank.available(s) && !Bank.blocked(s) && s.loans.size() < Bank.MAX_LOANS;
        if (!can) qt += " " + whyNot(s);
        quoteTv.setText(qt);
        Ui.setDesc(quoteTv, qt);
        takeBtn.setText("Kredi al: " + amount + " lira, " + term + " gün");
        Ui.setDesc(takeBtn, "Kredi al: " + amount + " lira, " + term + " gün" + (can ? "" : ", alınamaz"));
        Ui.dim(takeBtn, !can);
        buildAid();
        buildLoans();
    }

    private static String whyNot(SaveData s) {
        if (s.loans.size() >= Bank.MAX_LOANS) return Texts.bankResult(Bank.Result.TOO_MANY_LOANS);
        if (Bank.blocked(s)) return Texts.bankResult(Bank.Result.BLOCKED);
        return "Alınabilir limit " + Bank.available(s) + " lira.";
    }

    private String loansKey = null;
    /** Kredi satırlarının bilgi görünümleri (işlem sonrası odak hedefi). */
    private final Map<Integer, View> loanTexts = new HashMap<>();
    private View firstLoanView;

    /** Kredi listesi (yalnızca değişince yeniden kurulur). */
    private void buildLoans() {
        SaveData s = a.save;
        StringBuilder k = new StringBuilder();
        for (Loan l : s.loans) k.append(l.id).append(':').append(l.totalOwed()).append(':').append(l.getOverdue()).append('|');
        if (k.toString().equals(loansKey)) return;
        loansKey = k.toString();
        loansBox.removeAllViews();
        loanTexts.clear();
        firstLoanView = null;
        if (s.loans.isEmpty()) {
            firstLoanView = paragraph("Kredi borcun yok.");
            loansBox.addView(firstLoanView);
            return;
        }
        for (final Loan l : Bank.loans(s)) {
            LinearLayout c = card();
            // kısa satır (≤60); tam ayrıntı satıra dokununca okunur
            final TextView t = Ui.label(ctx, Texts.loanLabel(l) + "\nGünlük taksit " + l.installment() + " lira", 17, Theme.CREAM, true);
            t.setFocusable(true);
            t.setMinHeight(dp(56));
            t.setGravity(android.view.Gravity.CENTER_VERTICAL);
            t.setContentDescription(Texts.loanLabel(l));
            tap(t, new Runnable() { @Override public void run() { a.speaker.force(Narrator.loanText(l)); } });
            c.addView(t);
            loanTexts.put(l.id, t);
            if (firstLoanView == null) firstLoanView = t;
            Button close = Ui.button(ctx, "Erken kapat: " + l.payoffAmount() + " lira", Ui.SECONDARY);
            close.setContentDescription("Erken kapat: " + l.principal + " liralık kredi, " + l.payoffAmount() + " lira");
            tap(close, new Runnable() { @Override public void run() { repay(l.id); } });
            c.addView(close, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
            if (l.getOverdue() > 0) {
                int due = l.getOverdue() + l.getFees();
                Button od = Ui.button(ctx, "Gecikmiş taksiti öde: " + due + " lira", Ui.DANGER);
                od.setContentDescription("Gecikmiş taksiti öde: " + due + " lira");
                tap(od, new Runnable() { @Override public void run() { payOverdue(l.id); } });
                c.addView(od, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
            }
            loansBox.addView(c, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 4));
        }
    }

    private void takeLoan() {
        SaveData s = a.save;
        Bank.Result r = Bank.takeLoan(s, amount, term);
        if (r == Bank.Result.OK) {
            a.sound.play("sfx_coin");
            a.haptics.buy();
            a.saveNow();
            int took = amount;
            amount = Math.max(Bank.MIN_LOAN, Math.min(amount, maxAmount(s)));
            refresh();
            // düğme yerinde: odak taşınmaz, tek duyuru
            a.speaker.result("Kredi alındı: " + took + " lira, " + term + " gün. Kasada " + s.money + " lira.", com.doner.oyunu.engine.Importance.NORMAL);
        } else {
            a.sound.play("sfx_error");
            a.haptics.error();
            a.speaker.result("Kredi alınamadı. " + Texts.bankResult(r) + (r == Bank.Result.OVER_LIMIT ? " " + whyNot(s) : ""), com.doner.oyunu.engine.Importance.CRITICAL);
        }
    }

    private void repay(int loanId) {
        SaveData s = a.save;
        Loan l = Bank.loan(s, loanId);
        int p = l == null ? 0 : l.payoffAmount();
        Bank.Result r = Bank.repay(s, loanId);
        if (r == Bank.Result.OK) {
            a.sound.play("sfx_coin");
            a.haptics.buy();
            a.saveNow();
            refresh();
            // kart kalktı: odak listenin ilk satırına (kalan kredi ya da "Kredi borcun yok."), sonra tek duyuru
            a.focusThenSay(firstLoanView, "Kredi kapatıldı: " + p + " lira. Kredi notu " + s.creditScore + ". Kasada " + s.money + " lira.", com.doner.oyunu.engine.Importance.NORMAL);
        } else {
            a.sound.play("sfx_error");
            a.haptics.error();
            a.speaker.result(Texts.bankResult(r) + (r == Bank.Result.NOT_ENOUGH_MONEY ? " " + p + " lira gerekli." : ""), com.doner.oyunu.engine.Importance.CRITICAL);
        }
    }

    private void payOverdue(int loanId) {
        SaveData s = a.save;
        Loan l = Bank.loan(s, loanId);
        int p = l == null ? 0 : l.getOverdue() + l.getFees();
        Bank.Result r = Bank.payOverdue(s, loanId);
        if (r == Bank.Result.OK) {
            a.sound.play("sfx_coin");
            a.haptics.buy();
            a.saveNow();
            refresh();
            View f = loanTexts.get(loanId);
            a.focusThenSay(f != null ? f : firstLoanView, "Gecikmiş taksit ödendi: " + p + " lira. Kasada " + s.money + " lira.", com.doner.oyunu.engine.Importance.NORMAL);
        } else {
            a.sound.play("sfx_error");
            a.haptics.error();
            a.speaker.result(Texts.bankResult(r) + (r == Bank.Result.NOT_ENOUGH_MONEY ? " " + p + " lira gerekli." : ""), com.doner.oyunu.engine.Importance.CRITICAL);
        }
    }

    private void back() { a.saveNow(); a.show(Nav.make(a, from)); }

    @Override public boolean onBack() { back(); return true; }

    @Override public void onExit() { a.saveNow(); }
}
