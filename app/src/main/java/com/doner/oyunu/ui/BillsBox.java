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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fatura listesi (gün sonu / brifing / market "Faturalar" ekranı ve gün içi Duraklat > Faturalar paneli ortak): kısa özet
 * satırı (açık fatura, borç, maaş borcu, kasa), her açık fatura için kısa etiket ("Elektrik faturası 309 lira, 1 gün
 * gecikti") + tek cümlelik etki satırı ve büyük <b>Öde / Ertele / Krediyle öde</b> düğmeleri (gün içinde yalnızca Öde),
 * maaş borcu satırı (<b>Maaşı öde</b>). Gecikmiş ve son günü gelen faturalar önce ({@link Finance#openBills} sırası).
 *
 * <p><b>v2.6 (odak kaybı yok, tek odak + tek duyuru):</b> satırlar bir kez kurulur ve YERİNDE güncellenir; "Ertele" ve
 * "Krediyle öde"nin ilk dokunuşu hiçbir görünümü yok etmez (TalkBack odağı dokunulan düğmede kalır, düğme "Onayla"ya
 * dönüşür). Yalnızca ödenen faturanın kartı kalkar; o zaman odak özet satırına taşınır ve sonuç odak okumasının ARKASINDAN
 * bir kez söylenir ({@link Host#result}). Uzun açıklamalar fatura satırına dokununca (ayrıntı) okunur.
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
        /** Açık okuma (odak yerinde kalır). */
        void say(String text);
        /** İşlem sonucu: odağı {@code focus}'a taşır, ardından {@code text}'i bir kez söyler. */
        void result(View focus, String text);
        void sound(String sfx, boolean ok);
        /** Para/fatura değişti: kaydet, diğer göstergeleri yenile. */
        void changed();
    }

    /** Bir faturanın kalıcı satırı (yerinde güncellenir). */
    private final class Row {
        final int billId;
        final LinearLayout card;
        final TextView info, effect;
        final Button pay;
        Button defer, loan;
        int due;

        Row(final Bill b) {
            billId = b.id;
            card = new LinearLayout(ctx);
            card.setOrientation(VERTICAL);
            int p = Ui.dp(ctx, 12);
            card.setPadding(p, p, p, p);
            info = Ui.label(ctx, "", 18, Theme.CREAM, true);
            info.setFocusable(true);
            info.setMinHeight(Ui.dp(ctx, 56));
            info.setGravity(android.view.Gravity.CENTER_VERTICAL);
            host.tap(info, new Runnable() { @Override public void run() { detail(billId); } });
            card.addView(info);
            effect = Ui.label(ctx, "", 16, Theme.MUTED, false);
            effect.setFocusable(true);
            effect.setPadding(0, 0, 0, Ui.dp(ctx, 4));
            card.addView(effect);
            pay = Ui.button(ctx, "", Ui.PRIMARY);
            host.tap(pay, new Runnable() { @Override public void run() { pay(billId, due); } });
            card.addView(pay, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
            if (host.fullOptions()) {
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(HORIZONTAL);
                defer = Ui.button(ctx, "Ertele", Ui.SECONDARY);
                defer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                host.tap(defer, new Runnable() { @Override public void run() { defer(billId); } });
                loan = Ui.button(ctx, "", Ui.SECONDARY);
                loan.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                host.tap(loan, new Runnable() { @Override public void run() { payWithLoan(billId); } });
                row.addView(defer, Ui.lp(ctx, 0, Ui.WRAP, 1f, 0, 6, 4, 0));
                row.addView(loan, Ui.lp(ctx, 0, Ui.WRAP, 1.4f, 4, 6, 0, 0));
                card.addView(row);
            }
        }

        void update(Bill b, SaveData s, int day) {
            boolean urgent = b.daysLeft(day) <= 0;
            String key = urgent + "";
            if (!key.equals(card.getTag())) {
                card.setTag(key);
                card.setBackground(Ui.round(ctx, urgent ? Theme.CARD_HI : Theme.CARD, urgent ? Theme.RED : Theme.STROKE, urgent ? 3 : 1.5f, 16));
                info.setTextColor(urgent ? Theme.GOLD : Theme.CREAM);
            }
            String text = Texts.billLabel(b, day);
            if (deferred.contains(b.id)) text += ". Ertelendi.";
            setText(info, text);
            String e = Texts.billEffect(s, b, day);
            setText(effect, e);
            effect.setVisibility(e.isEmpty() ? GONE : VISIBLE);
            due = b.amountDue(day);
            String name = b.type.displayName;
            setText(pay, "Öde: " + due + " lira");
            Ui.setDesc(pay, Texts.clip("Öde: " + name + ", " + due + " lira", 60));
            if (defer != null) {
                Ui.setDesc(defer, Texts.clip("Ertele: " + name, 60));
                boolean confirm = loanConfirm.contains(b.id);
                setText(loan, confirm ? "Onayla: krediyle öde" : "Krediyle öde");
                Ui.setDesc(loan, Texts.clip((confirm ? "Emin misin? Krediyle öde: " : "Krediyle öde: ") + name, 60));
                if (!Boolean.valueOf(confirm).equals(loan.getTag())) {
                    loan.setTag(confirm);
                    loan.setBackground(Ui.buttonBg(ctx, confirm ? Ui.GOLD : Ui.SECONDARY));
                    loan.setTextColor(confirm ? Theme.DARK : Theme.CREAM);
                }
            }
        }
    }

    private static void setText(TextView v, String t) {
        if (!t.contentEquals(v.getText())) v.setText(t);
        if (!(v instanceof Button)) Ui.setDesc(v, t);
    }

    private final Context ctx;
    private final Host host;
    final TextView header;
    private final LinearLayout list;
    private final Map<Integer, Row> rows = new LinkedHashMap<>();
    private LinearLayout wageCard;
    private TextView wageInfo;
    private Button wagePay;
    private int wageDue;
    /** Krediyle öde onayı bekleyen faturalar (ilk dokunuş teklifi okur, ikincisi uygular). */
    private final Set<Integer> loanConfirm = new HashSet<>();
    /** Ertelenen faturalar (yalnızca görsel not). */
    private final Set<Integer> deferred = new HashSet<>();

    BillsBox(Context ctx, Host host) {
        super(ctx);
        this.ctx = ctx;
        this.host = host;
        setOrientation(VERTICAL);
        header = Ui.label(ctx, "", 19, Theme.GOLD, true);
        header.setFocusable(true);
        header.setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 8));
        addView(header);
        list = new LinearLayout(ctx);
        list.setOrientation(VERTICAL);
        addView(list);
        rebuild();
    }

    /** Sesli ÖZET (açık okuma): kaç fatura, toplam, en acili, maaş borcu, kasa. Tek tek faturalar satırlarındadır. */
    static String readText(SaveData s, int day) { return Texts.billsRead(s, day); }

    static int totalDue(SaveData s, int day) { return Texts.billsTotal(s, day); }

    /** Ödenecek bir şey (fatura ya da maaş borcu) var mı. */
    static boolean anythingDue(SaveData s) { return !s.bills.isEmpty() || s.wageDebt > 0; }

    /**
     * Listeyi kayıtla eşitler: var olan satırlar YERİNDE güncellenir, ödenen faturanın satırı kalkar, yeni fatura eklenir.
     * Hiçbir kalıcı görünüm yeniden yaratılmaz (TalkBack odağı korunur).
     */
    void rebuild() {
        SaveData s = host.save();
        int day = host.day();
        List<Bill> bills = Finance.openBills(s);
        setText(header, Texts.billsHeader(s, day));
        Set<Integer> alive = new HashSet<>();
        for (Bill b : bills) alive.add(b.id);
        for (Iterator<Map.Entry<Integer, Row>> it = rows.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Row> e = it.next();
            if (!alive.contains(e.getKey())) { list.removeView(e.getValue().card); it.remove(); }
        }
        loanConfirm.retainAll(alive);
        deferred.retainAll(alive);
        int pos = 0;
        for (Bill b : bills) {
            Row r = rows.get(b.id);
            if (r == null) {
                r = new Row(b);
                rows.put(b.id, r);
                list.addView(r.card, Math.min(pos, list.getChildCount()), Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 5, 0, 5));
            }
            r.update(b, s, day);
            pos++;
        }
        updateWage(s);
    }

    private void updateWage(SaveData s) {
        if (s.wageDebt <= 0) {
            if (wageCard != null) { list.removeView(wageCard); wageCard = null; }
            return;
        }
        if (wageCard == null) {
            wageCard = new LinearLayout(ctx);
            wageCard.setOrientation(VERTICAL);
            wageCard.setBackground(Ui.round(ctx, Theme.CARD_HI, Theme.RED, 3, 16));
            int p = Ui.dp(ctx, 12);
            wageCard.setPadding(p, p, p, p);
            wageInfo = Ui.label(ctx, "", 18, Theme.GOLD, true);
            wageInfo.setFocusable(true);
            wageCard.addView(wageInfo);
            wagePay = Ui.button(ctx, "", Ui.PRIMARY);
            host.tap(wagePay, new Runnable() { @Override public void run() { payWages(wageDue); } });
            wageCard.addView(wagePay, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
            list.addView(wageCard, Ui.lp(ctx, Ui.MATCH, Ui.WRAP, 0, 5, 0, 5));
        }
        wageDue = s.wageDebt;
        setText(wageInfo, "Maaş borcu " + s.wageDebt + " lira. Ödemezsen çırak gelmez.");
        setText(wagePay, "Maaşı öde: " + wageDue + " lira");
        Ui.setDesc(wagePay, "Maaşı öde: " + wageDue + " lira");
    }

    /** Testler: faturanın satır görünümleri (yerinde güncelleme denetimi). */
    View infoView(int billId) { Row r = rows.get(billId); return r == null ? null : r.info; }
    Button loanButton(int billId) { Row r = rows.get(billId); return r == null ? null : r.loan; }
    Button deferButton(int billId) { Row r = rows.get(billId); return r == null ? null : r.defer; }
    Button payButton(int billId) { Row r = rows.get(billId); return r == null ? null : r.pay; }

    /** Fatura satırına dokunuldu: ayrıntı (tam uyarı metni) okunur. */
    private void detail(int billId) {
        Bill b = Finance.bill(host.save(), billId);
        if (b == null) { rebuild(); return; }
        host.say(Texts.billDetail(host.save(), b, host.day()));
    }

    private String cash(SaveData s) { return " Kasada " + s.money + " lira."; }

    private void pay(int billId, int due) {
        SaveData s = host.save();
        Bill b = Finance.bill(s, billId);
        if (b == null) { rebuild(); host.result(header, Texts.payResult(Finance.PayResult.UNKNOWN, 0)); return; }
        Finance.PayResult r = host.pay(billId);
        loanConfirm.remove(billId);
        if (r == Finance.PayResult.OK) {
            host.sound("sfx_coin", true);
            host.changed();
            rebuild();
            // kart kalktı: odak özet satırına (kalan borç + kasa orada), sonuç bir kez ve kısa
            host.result(header, Narrator.billPaid(b, due) + cash(s));
        } else {
            host.sound("sfx_error", false);
            rebuild();
            host.say(Texts.payResult(r, due) + (host.fullOptions() ? " Krediyle öde ya da Ertele." : ""));
        }
    }

    private void payWages(int due) {
        SaveData s = host.save();
        Finance.PayResult r = host.payWages();
        if (r == Finance.PayResult.OK) {
            host.sound("sfx_buy", true);
            host.changed();
            rebuild();
            host.result(header, "Maaş ödendi: " + due + " lira. Çırak işe gelir." + cash(s));
        } else {
            host.sound("sfx_error", false);
            host.say(Texts.payResult(r, due));
        }
    }

    private void defer(int billId) {
        SaveData s = host.save();
        Bill b = Finance.bill(s, billId);
        if (b == null) { rebuild(); return; }
        loanConfirm.remove(billId);
        deferred.add(billId);
        rebuild(); // yerinde: odak "Ertele" düğmesinde kalır
        String e = Texts.billEffect(s, b, host.day());
        host.say("Ertelendi: " + b.type.displayName.toLowerCase(TR) + "." + (e.isEmpty() ? " Son ödemeye " + b.daysLeft(host.day()) + " gün var." : " " + e));
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
            rebuild(); // yerinde: aynı düğme "Onayla"ya döner, odak üzerinde kalır
            host.say("Kredi: " + (q == null ? amount + " lira." : Texts.quoteShort(q)) + " Onaylamak için tekrar dokun.");
            return;
        }
        loanConfirm.remove(billId);
        Bank.Result r = Finance.payWithLoan(s, billId, LOAN_TERM);
        if (r == Bank.Result.OK) {
            host.sound("sfx_coin", true);
            host.changed();
            rebuild();
            host.result(header, "Krediyle ödendi: " + b.type.displayName.toLowerCase(TR) + ", " + due + " lira. Taksit "
                    + LOAN_TERM + " gün, gün sonunda kasadan." + cash(s));
        } else {
            host.sound("sfx_error", false);
            rebuild();
            host.say("Kredi alınamadı. " + (b.type == com.doner.oyunu.engine.BillType.KIRA && r == Bank.Result.TOO_MANY_LOANS
                    ? "Kira kredisi sınırı doldu." : Texts.bankResult(r)));
        }
    }

    private static final java.util.Locale TR = new java.util.Locale("tr");

    /** Krediyle ödemenin vadesi (gün). */
    static final int LOAN_TERM = 10;
}
