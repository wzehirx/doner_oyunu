package com.doner.oyunu.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * v2.5 banka: kredi limiti (gün, yıldız, kredi notu), faiz (kredi notuna göre), vade (5/10/20 gün), günlük otomatik
 * taksit, erken kapatma, gecikme. Saf, deterministik; {@link SaveData} üzerinde çalışır.
 */
public final class Bank {
    private Bank() {}

    public static final int MIN_LOAN = 500;
    public static final int STEP = 100;
    public static final int MAX_LOANS = 3;
    /** Seçilebilir vadeler (gün). */
    public static final int[] TERMS = {5, 10, 20};
    /** Erken kapatmada ödenen kalan faiz payı. */
    public static final double EARLY_INTEREST_SHARE = 0.25;
    /** Kaçan taksite eklenen gecikme bedeli. */
    public static final double MISSED_FEE = 0.05;
    /** Art arda bu kadar taksit kaçınca yeni kredi verilmez. */
    public static final int BLOCK_STREAK = 3;

    public enum Result { OK, OVER_LIMIT, INVALID_AMOUNT, INVALID_TERM, TOO_MANY_LOANS, BLOCKED, NOT_ENOUGH_MONEY, UNKNOWN }

    /** Kredi teklifi (alınmadan önce gösterilir). */
    public static final class Quote {
        public final int amount, term, interest, installment, total;
        /** Günlük faiz oranı (0,008 = binde 8). */
        public final double dailyRate;
        Quote(int amount, int term, double rate) {
            this.amount = amount;
            this.term = term;
            this.dailyRate = rate;
            this.interest = (int) Math.round(amount * rate * term);
            this.total = amount + interest;
            this.installment = (total + term - 1) / term;
        }
    }

    /** Kredi notuna göre günlük faiz: ≥80 %0,6 · 60–79 %0,8 · 40–59 %1,2 · 20–39 %1,8 · <20 %2,5. */
    public static double dailyRate(int score) {
        if (score >= 80) return 0.006;
        if (score >= 60) return 0.008;
        if (score >= 40) return 0.012;
        if (score >= 20) return 0.018;
        return 0.025;
    }

    /** En yüksek faiz (kira kredisi limit dışı verildiğinde). */
    public static final double WORST_RATE = 0.025;

    /** Toplam kredi limiti (kullanılan dahil): aşağı100((2000 + 500·gün) × (0,6 + 0,1·yıldız) × sınırla(not/60; 0,25; 1,5)). */
    public static int totalLimit(SaveData save) {
        double star = 0.6 + 0.1 * Math.max(1.0, Math.min(5.0, save.stars));
        double score = Math.max(0.25, Math.min(1.5, save.creditScore / 60.0));
        double v = (2000 + 500.0 * Math.max(1, save.day)) * star * score;
        return (int) (Math.floor(v / STEP) * STEP);
    }

    /** Kalan anapara toplamı. */
    public static int outstandingPrincipal(SaveData save) {
        int t = 0;
        for (Loan l : save.loans) t += l.remainingPrincipal();
        return t;
    }

    /** Toplam borç (kalan + gecikme bedelleri). */
    public static int totalDebt(SaveData save) {
        int t = 0;
        for (Loan l : save.loans) t += l.totalOwed();
        return t;
    }

    /** Bugün gün sonunda alınacak taksitlerin toplamı (gecikmiş + bedel + bugünkü taksit). */
    public static int dueToday(SaveData save) {
        int t = 0;
        for (Loan l : save.loans) {
            int inst = l.installmentsLeft > 0 ? Math.min(l.installment(), l.remaining - l.dueNow) : l.remaining - l.dueNow;
            t += l.dueNow + l.fees + Math.max(0, inst);
        }
        return t;
    }

    /** Yeni kredi engelli mi (art arda {@link #BLOCK_STREAK} taksit kaçtıysa). */
    public static boolean blocked(SaveData save) {
        for (Loan l : save.loans) if (l.missedStreak >= BLOCK_STREAK) return true;
        return false;
    }

    /** Şu an alınabilecek en yüksek kredi (100'ün katı; engelliyse 0). */
    public static int available(SaveData save) {
        if (blocked(save) || save.loans.size() >= MAX_LOANS) return 0;
        int a = totalLimit(save) - outstandingPrincipal(save);
        return Math.max(0, (a / STEP) * STEP);
    }

    public static boolean validTerm(int term) {
        for (int t : TERMS) if (t == term) return true;
        return false;
    }

    /** Teklif: faiz, taksit, toplam (bugünkü kredi notuyla). Geçersiz tutar/vade için null. */
    public static Quote quote(SaveData save, int amount, int term) {
        if (amount < MIN_LOAN || amount % STEP != 0 || !validTerm(term)) return null;
        return new Quote(amount, term, dailyRate(save.creditScore));
    }

    /** Kredi alır: para kasaya girer, taksitler gün sonlarında alınır. */
    public static Result takeLoan(SaveData save, int amount, int term) { return takeLoan(save, amount, term, false); }

    /** rentLoan: kira faturası için; limit/engel aranmaz, en yüksek faiz (tahliye kilitlenmesi olmasın). */
    static Result takeLoan(SaveData save, int amount, int term, boolean rentLoan) {
        if (amount < MIN_LOAN || amount % STEP != 0) return Result.INVALID_AMOUNT;
        if (!validTerm(term)) return Result.INVALID_TERM;
        boolean normal = save.loans.size() < MAX_LOANS && !blocked(save) && amount <= available(save);
        if (!normal && !rentLoan) {
            if (save.loans.size() >= MAX_LOANS) return Result.TOO_MANY_LOANS;
            if (blocked(save)) return Result.BLOCKED;
            return Result.OVER_LIMIT;
        }
        double rate = normal ? dailyRate(save.creditScore) : WORST_RATE;
        Quote q = new Quote(amount, term, rate);
        Loan l = new Loan(save.nextFinanceId++, amount, q.interest, term, save.day);
        save.loans.add(l);
        save.money += amount;
        return Result.OK;
    }

    /** Krediyi erken kapatır: kalan anapara + kalan faizin %25'i + gecikme bedeli. Kredi notu +3. */
    public static Result repay(SaveData save, int loanId) {
        Loan l = loan(save, loanId);
        if (l == null) return Result.UNKNOWN;
        int p = l.payoffAmount();
        if (save.money < p) return Result.NOT_ENOUGH_MONEY;
        save.money -= p;
        save.loans.remove(l);
        Finance.adjustCredit(save, 3);
        return Result.OK;
    }

    /** Gecikmiş taksiti ve bedelini şimdi öder (gün sonunu beklemeden). */
    public static Result payOverdue(SaveData save, int loanId) {
        Loan l = loan(save, loanId);
        if (l == null) return Result.UNKNOWN;
        int p = l.dueNow + l.fees;
        if (p <= 0) return Result.OK;
        if (save.money < p) return Result.NOT_ENOUGH_MONEY;
        save.money -= p;
        l.remaining -= l.dueNow;
        l.dueNow = 0;
        l.fees = 0;
        l.missedStreak = 0;
        if (l.isClosed()) save.loans.remove(l);
        return Result.OK;
    }

    public static Loan loan(SaveData save, int loanId) {
        for (Loan l : save.loans) if (l.id == loanId) return l;
        return null;
    }

    public static List<Loan> loans(SaveData save) { return Collections.unmodifiableList(new ArrayList<>(save.loans)); }
}
