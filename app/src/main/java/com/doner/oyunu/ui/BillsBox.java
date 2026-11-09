package com.doner.oyunu.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.doner.oyunu.engine.Bank;
import com.doner.oyunu.engine.Bill;
import com.doner.oyunu.engine.Finance;
import com.doner.oyunu.engine.Narrator;
import com.doner.oyunu.engine.SaveData;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * v2.5 fatura listesi (gün sonu / brifing / market "Faturalar" ekranı ve gün içi Duraklat > Faturalar paneli ortak):
 * özet satırı (açık fatura, toplam borç, kredi notu, kasa), her açık fatura için bilgi satırı + uyarısı ve büyük
 * <b>Öde / Ertele / Krediyle öde</b> düğmeleri (gün içinde yalnızca Öde), maaş borcu satırı (<b>Maaşı öde</b>). Gecikmiş
 * ve son günü gelen faturalar önce ({@link Finance#openBills} sırası). Her işlem sesli + metinle sonuçlanır; liste
 * değişince yeniden kurulur ve odak özet satırına döner.
 */
final class BillsBox extends LinearLayout {
    interface Host {
        SaveData save();
        /** Tutarların hesaplandığı gün (gün sonunda yarın = save.day; gün içinde bugün). */
        int day();
        Finance.PayResult pay(int billId);
        Finance.PayResult payWages();
        /** Ertele ve Krediyle öde gösterilsin mi (gün sonu/brifing/market; gün içinde değil). */
        boolean fullOptions();
        void tap(View v, Runnable r);
        void say(String text);
        void focus(View v);
        void sound(String sfx, boolean ok);
        /** Para/fatura değişti: kaydet, diğer göstergeleri yenile. */
        void changed();
    }

    private final Context ctx;
    private final Host host;
    final TextView header;
    private final LinearLayout list;
    /** Krediyle öde onayı bekleyen faturalar (ilk dokunuş teklifi okur, ikincisi uygular). */
    private final Set<Integer> loanConfirm = new HashSet<>();
    /** Ertelenen faturalar (yalnızca görsel not). */
    private final Set<Integer> deferred = new HashSet<>();

    BillsBox(Context ctx, Host host) {
        super(ctx);
        this.ctx = ctx;
        this.host = host;
        setOrientation(VERTICAL);
        header = Ui.label(ctx, "", 18, Theme.GOLD, true);
        header.setFocusable(true);
        header.setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 8));
        addView(header);
        list = new LinearLayout(ctx);
        list.setOrientation(VERTICAL);
        addView(list);
        rebuild();
    }

    /** Sesli özet (açık okuma): faturalar, maaş borcu, kredi notu, kasa. */
    static String readText(SaveData s, int day) {
        StringBuilder sb = new StringBuilder();
        List<Bill> bills = Finance.openBills(s);
        if (bills.isEmpty()) sb.append("Açık fatura yok.");
        else {
            sb.append(bills.size()).append(" açık fatura, toplam ").append(Narrator.fmtMoney(totalDue(s, day))).append('.');
            for (Bill b : bills) sb.append(' ').append(Narrator.billText(b, day));
        }
        if (s.wageDebt > 0) sb.append(" Maaş borcu ").append(Narrator.fmtMoney(s.wageDebt)).append(", ödemezsen çırak gelmez.");
        sb.append(" Kredi notu ").append(s.creditScore).append(". Kasada ").append(Narrator.fmtMoney(s.money)).append('.');
        return sb.toString();
    }

    static int totalDue(SaveData s, int day) {
        int t = 0;
        for (Bill b : s.bills) t += b.amountDue(day);
        return t;
    }

    /** Ödenecek bir şey (fatura ya da maaş borcu) var mı. */
    static boolean anythingDue(SaveData s) { return !s.bills.isEmpty() || s.wageDebt > 0; }

    void rebuild() {
        SaveData s = host.save();
        int day = host.day();
        List<Bill> bills = Finance.openBills(s);
        String head = bills.isEmpty() ? "Açık fatura yok." : "Açık fatura " + bills.size() + ", toplam borç " + totalDue(s, day) + " lira.";
        if (s.wageDebt > 0) head += " Maaş borcu " + s.wageDebt + " lira.";
        head += " Kredi notu " + s.creditScore + ". Kasada " + s.money + " lira.";
        header.setText(head);
        header.setContentDescription(head);
        list.removeAllViews();
        List<String> notices = Finance.notices(s, day);
        for (final Bill b : bills) addBill(b, day, notices);
        if (s.wageDebt > 0) addWage(s);
    }

    private void addBill(final Bill b, int day, List<String> notices) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(VERTICAL);
        boolean urgent = b.daysLeft(day) <= 0;
        c.setBackground(Ui.round(ctx, urgent ? Theme.CARD_HI : Theme.CARD, urgent ? Theme.RED : Theme.STROKE, urgent ? 3 : 1.5f, 16));
        int p = Ui.dp(ctx, 12);
        c.setPadding(p, p, p, p);
        String text = Narrator.billText(b, day);
        String warn = "";
        for (String n : notices) if (n.startsWith(b.type.displayName + " ")) { warn = n; break; }
        if (deferred.contains(b.id)) text += " Ertelendi.";
        TextView t = Ui.label(ctx, text, 17, urgent ? Theme.GOLD : Theme.CREAM, urgent);
        t.setFocusable(true);
        t.setContentDescription(text);
        c.addView(t);
        if (!warn.isEmpty() && !warn.equals(text)) {
            TextView w = Ui.label(ctx, warn, 15, Theme.MUTED, false);
            w.setFocusable(true);
            w.setPadding(0, Ui.dp(ctx, 4), 0, 0);
            c.addView(w);
        }
        final int due = b.amountDue(day);
        Button pay = Ui.button(ctx, "Öde: " + due + " lira", Ui.PRIMARY);
        pay.setContentDescription(cap("Öde: " + b.type.displayName + ", " + due + " lira"));
        host.tap(pay, new Runnable() { @Override public void run() { pay(b.id, due); } });
        c.addView(pay, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        if (host.fullOptions()) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(HORIZONTAL);
            Button defer = Ui.button(ctx, "Ertele", Ui.SECONDARY);
            defer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            defer.setContentDescription(cap("Ertele: " + b.type.displayName));
            final String deferText = warn.isEmpty() ? text : warn;
            host.tap(defer, new Runnable() { @Override public void run() { defer(b.id, deferText); } });
            boolean confirm = loanConfirm.contains(b.id);
            Button loan = Ui.button(ctx, confirm ? "Onayla: krediyle öde" : "Krediyle öde", confirm ? Ui.GOLD : Ui.SECONDARY);
            loan.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            loan.setContentDescription(cap((confirm ? "Emin misin? Krediyle öde: " : "Krediyle öde: ") + b.type.displayName));
            host.tap(loan, new Runnable() { @Override public void run() { payWithLoan(b.id); } });
            row.addView(defer, Ui.lp(ctx, 0, Ui.WRAP, 1f, 0, 6, 4, 0));
            row.addView(loan, Ui.lp(ctx, 0, Ui.WRAP, 1.4f, 4, 6, 0, 0));
            c.addView(row);
        }
        list.addView(c, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 5, 0, 5));
    }

    private void addWage(final SaveData s) {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(VERTICAL);
        c.setBackground(Ui.round(ctx, Theme.CARD_HI, Theme.RED, 3, 16));
        int p = Ui.dp(ctx, 12);
        c.setPadding(p, p, p, p);
        String text = "Maaş borcu " + s.wageDebt + " lira: ödemezsen çırak gelmez.";
        TextView t = Ui.label(ctx, text, 17, Theme.GOLD, true);
        t.setFocusable(true);
        t.setContentDescription(text);
        c.addView(t);
        final int due = s.wageDebt;
        Button pay = Ui.button(ctx, "Maaşı öde: " + due + " lira", Ui.PRIMARY);
        pay.setContentDescription("Maaşı öde: " + due + " lira");
        host.tap(pay, new Runnable() { @Override public void run() { payWages(due); } });
        c.addView(pay, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
        list.addView(c, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 5, 0, 5));
    }

    private static String cap(String s) { return s.length() <= 60 ? s : s.substring(0, 60); }

    private void pay(int billId, int due) {
        SaveData s = host.save();
        Bill b = Finance.bill(s, billId);
        if (b == null) { host.say(Texts.payResult(Finance.PayResult.UNKNOWN, 0)); rebuild(); return; }
        Finance.PayResult r = host.pay(billId);
        loanConfirm.remove(billId);
        if (r == Finance.PayResult.OK) {
            host.sound("sfx_coin", true);
            host.changed();
            rebuild();
            host.say(Narrator.billPaid(b, due) + " Kasada " + s.money + " lira.");
            host.focus(header);
        } else {
            host.sound("sfx_error", false);
            host.say(Texts.payResult(r, due) + (host.fullOptions() ? " Krediyle ödeyebilir ya da erteleyebilirsin." : ""));
        }
    }

    private void payWages(int due) {
        SaveData s = host.save();
        Finance.PayResult r = host.payWages();
        if (r == Finance.PayResult.OK) {
            host.sound("sfx_buy", true);
            host.changed();
            rebuild();
            host.say("Maaş ödendi: " + due + " lira. Çırak işe gelir. Kasada " + s.money + " lira.");
            host.focus(header);
        } else {
            host.sound("sfx_error", false);
            host.say(Texts.payResult(r, due));
        }
    }

    private void defer(int billId, String warn) {
        loanConfirm.remove(billId);
        deferred.add(billId);
        rebuild();
        host.say("Ertelendi. " + warn);
    }

    private void payWithLoan(int billId) {
        SaveData s = host.save();
        Bill b = Finance.bill(s, billId);
        if (b == null) { rebuild(); return; }
        int due = b.amountDue(host.day());
        int amount = Math.max(Bank.MIN_LOAN, ((due + Bank.STEP - 1) / Bank.STEP) * Bank.STEP);
        if (!loanConfirm.contains(billId)) {
            Bank.Quote q = Bank.quote(s, amount, LOAN_TERM);
            loanConfirm.clear();
            loanConfirm.add(billId);
            rebuild();
            host.say("Kredi teklifi: " + (q == null ? amount + " lira." : Narrator.quoteText(q))
                    + " Onaylamak için Onayla düğmesine dokun.");
            return;
        }
        loanConfirm.remove(billId);
        Bank.Result r = Finance.payWithLoan(s, billId, LOAN_TERM);
        if (r == Bank.Result.OK) {
            host.sound("sfx_coin", true);
            host.changed();
            rebuild();
            host.say("Kredi alındı, " + b.type.displayName.toLowerCase(new java.util.Locale("tr")) + " ödendi: " + due
                    + " lira. Taksit " + LOAN_TERM + " gün, gün sonunda kasadan. Kasada " + s.money + " lira.");
            host.focus(header);
        } else {
            host.sound("sfx_error", false);
            rebuild();
            host.say("Kredi alınamadı. " + Texts.bankResult(r));
        }
    }

    /** Krediyle ödemenin vadesi (gün). */
    static final int LOAN_TERM = 10;
}
