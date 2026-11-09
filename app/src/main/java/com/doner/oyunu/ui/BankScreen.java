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
 * Narrator#bankText}); tutar (−/+ 500 lira, en az 500) ve vade (5/10/20 gün) seçimi, teklif ({@link Narrator#quoteText}),
 * Kredi al; aktif krediler (kalan borç, taksit, vade) için Erken kapat ve Gecikmiş taksiti öde. Her sonuç sesli ve
 * metinle; kredi listesi değişince yeniden kurulur, odak bilgi satırına döner.
 */
public final class BankScreen extends Screen {
    private final int from;
    private int amount = Bank.MIN_LOAN;
    private int term = 10;
    private TextView infoTv, amountTv, quoteTv;
    private Button takeBtn;
    private final Map<Integer, Button> termButtons = new HashMap<>();
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

        infoTv = Ui.label(ctx, "", 18, Theme.CREAM, false);
        infoTv.setFocusable(true);
        infoTv.setPadding(dp(4), dp(4), dp(4), dp(8));
        body.addView(infoTv);
        body.addView(button("Bankayı oku", Ui.SECONDARY, new Runnable() {
            @Override public void run() { a.speaker.force(readAll(a.save)); }
        }), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 2, 0, 6));

        // ---- kredi al
        body.addView(sectionHeading("Kredi al"));
        LinearLayout amountCard = card();
        amountTv = Ui.label(ctx, "", 19, Theme.CREAM, true);
        amountTv.setFocusable(true);
        amountCard.addView(amountTv);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button minus = Ui.button(ctx, "−  500 lira", Ui.SECONDARY);
        Button plus = Ui.button(ctx, "+  500 lira", Ui.SECONDARY);
        minus.setContentDescription("Tutarı azalt");
        plus.setContentDescription("Tutarı artır");
        tap(minus, new Runnable() { @Override public void run() { stepAmount(-1); } });
        tap(plus, new Runnable() { @Override public void run() { stepAmount(+1); } });
        row.addView(minus, Ui.lp(ctx, 0, Ui.WRAP, 1f, 0, 6, 4, 0));
        row.addView(plus, Ui.lp(ctx, 0, Ui.WRAP, 1f, 4, 6, 0, 0));
        amountCard.addView(row);
        body.addView(amountCard, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 4, 0, 4));

        LinearLayout termRow = new LinearLayout(ctx);
        termRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < Bank.TERMS.length; i++) {
            final int t = Bank.TERMS[i];
            Button b = Ui.button(ctx, t + " gün", Ui.SECONDARY);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            tap(b, new Runnable() { @Override public void run() { chooseTerm(t); } });
            termButtons.put(t, b);
            termRow.addView(b, Ui.lp(ctx, 0, Ui.WRAP, 1f, i == 0 ? 0 : 3, 4, i == Bank.TERMS.length - 1 ? 0 : 3, 4));
        }
        body.addView(termRow);

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

    /** Bankanın sesli özeti: not, limit, faiz, krediler. */
    static String readAll(SaveData s) {
        StringBuilder sb = new StringBuilder(Narrator.bankText(s));
        for (Loan l : Bank.loans(s)) sb.append(' ').append(Narrator.loanText(l));
        return sb.toString();
    }

    private void refresh() {
        SaveData s = a.save;
        String info = Narrator.bankText(s) + " Kasada " + s.money + " lira.";
        infoTv.setText(info);
        Ui.setDesc(infoTv, info);
        String at = "Tutar: " + amount + " lira";
        amountTv.setText(at);
        Ui.setDesc(amountTv, at);
        for (Map.Entry<Integer, Button> e : termButtons.entrySet()) {
            boolean sel = e.getKey() == term;
            Button b = e.getValue();
            b.setSelected(sel);
            b.setText(e.getKey() + " gün" + (sel ? " ✓" : ""));
            b.setContentDescription(e.getKey() + " gün vade" + (sel ? ", seçili" : ""));
            b.setBackground(Ui.buttonBg(ctx, sel ? Ui.GOLD : Ui.SECONDARY));
            b.setTextColor(sel ? Theme.DARK : Theme.CREAM);
        }
        Bank.Quote q = Bank.quote(s, amount, term);
        String qt = q == null ? "Teklif yok." : "Teklif: " + Narrator.quoteText(q);
        boolean can = q != null && amount <= Bank.available(s) && !Bank.blocked(s) && s.loans.size() < Bank.MAX_LOANS;
        if (!can) qt += " " + whyNot(s);
        quoteTv.setText(qt);
        Ui.setDesc(quoteTv, qt);
        takeBtn.setText("Kredi al: " + amount + " lira, " + term + " gün");
        takeBtn.setContentDescription("Kredi al: " + amount + " lira, " + term + " gün" + (can ? "" : ", alınamaz"));
        Ui.dim(takeBtn, !can);
        buildLoans();
    }

    private static String whyNot(SaveData s) {
        if (s.loans.size() >= Bank.MAX_LOANS) return Texts.bankResult(Bank.Result.TOO_MANY_LOANS);
        if (Bank.blocked(s)) return Texts.bankResult(Bank.Result.BLOCKED);
        return "Alınabilir limit " + Bank.available(s) + " lira.";
    }

    private String loansKey = null;

    /** Kredi listesi (yalnızca değişince yeniden kurulur). */
    private void buildLoans() {
        SaveData s = a.save;
        StringBuilder k = new StringBuilder();
        for (Loan l : s.loans) k.append(l.id).append(':').append(l.totalOwed()).append(':').append(l.getOverdue()).append('|');
        if (k.toString().equals(loansKey)) return;
        loansKey = k.toString();
        loansBox.removeAllViews();
        if (s.loans.isEmpty()) {
            loansBox.addView(paragraph("Kredi borcun yok."));
            return;
        }
        for (final Loan l : Bank.loans(s)) {
            LinearLayout c = card();
            TextView t = Ui.label(ctx, Narrator.loanText(l), 17, Theme.CREAM, false);
            t.setFocusable(true);
            t.setContentDescription(Narrator.loanText(l));
            c.addView(t);
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

    private void stepAmount(int dir) {
        int max = maxAmount(a.save);
        int next = amount + dir * 500;
        if (dir > 0 && next > max) next = max;
        if (next < Bank.MIN_LOAN) next = Bank.MIN_LOAN;
        if (next == amount) {
            a.sound.play("sfx_error");
            a.haptics.error();
            a.speaker.force((dir > 0 ? "En yüksek tutar: " : "En düşük tutar: ") + amount + " lira.");
            return;
        }
        amount = next;
        refresh();
        Bank.Quote q = Bank.quote(a.save, amount, term);
        a.speaker.force("Tutar " + amount + " lira." + (q == null ? "" : " " + Narrator.quoteText(q)));
    }

    private void chooseTerm(int t) {
        term = t;
        refresh();
        Bank.Quote q = Bank.quote(a.save, amount, term);
        a.speaker.force(t + " gün vade seçildi." + (q == null ? "" : " " + Narrator.quoteText(q)));
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
            a.speaker.force("Kredi alındı: " + took + " lira, " + term + " gün. Kasada " + s.money + " lira.");
            a.focusOn(infoTv);
        } else {
            a.sound.play("sfx_error");
            a.haptics.error();
            a.speaker.force("Kredi alınamadı. " + Texts.bankResult(r) + " " + (r == Bank.Result.OVER_LIMIT ? whyNot(s) : ""));
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
            a.speaker.force("Kredi erken kapatıldı: " + p + " lira. Kredi notu " + s.creditScore + ". Kasada " + s.money + " lira.");
            a.focusOn(infoTv);
        } else {
            a.sound.play("sfx_error");
            a.haptics.error();
            a.speaker.force(Texts.bankResult(r) + (r == Bank.Result.NOT_ENOUGH_MONEY ? " " + p + " lira gerekli." : ""));
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
            a.speaker.force("Gecikmiş taksit ödendi: " + p + " lira. Kasada " + s.money + " lira.");
            a.focusOn(infoTv);
        } else {
            a.sound.play("sfx_error");
            a.haptics.error();
            a.speaker.force(Texts.bankResult(r) + (r == Bank.Result.NOT_ENOUGH_MONEY ? " " + p + " lira gerekli." : ""));
        }
    }

    private void back() { a.saveNow(); a.show(Nav.make(a, from)); }

    @Override public boolean onBack() { back(); return true; }

    @Override public void onExit() { a.saveNow(); }
}
