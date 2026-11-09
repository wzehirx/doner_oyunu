package com.doner.oyunu.engine;

/**
 * v2.5 banka kredisi: anapara + toplam faiz, vade (gün) kadar eşit taksit; taksit her gün sonunda kasadan otomatik
 * alınır ({@link Finance#endOfDay}). Kaçan taksit gecikmiş borca (+%5 gecikme bedeli) eklenir ve ilk fırsatta alınır.
 */
public final class Loan {
    public final int id;
    public final int principal;
    /** Toplam faiz (alındığı anda sabitlenir). */
    public final int interest;
    /** Vade (gün = taksit sayısı). */
    public final int term;
    public final int takenDay;
    /** Ödenmemiş toplam (anapara + faiz; vadesi gelmemiş taksitler dahil, gecikme bedeli hariç). */
    int remaining;
    /** Vadesi gelmiş ama ödenmemiş taksit tutarı (gecikmiş borç). */
    int dueNow;
    /** Birikmiş gecikme bedeli. */
    int fees;
    /** Kalan taksit günü. */
    int installmentsLeft;
    /** Art arda kaçan taksit günü. */
    int missedStreak;
    /** Toplam kaçan taksit. */
    int missedTotal;

    Loan(int id, int principal, int interest, int term, int takenDay) {
        this.id = id;
        this.principal = principal;
        this.interest = interest;
        this.term = term;
        this.takenDay = takenDay;
        this.remaining = principal + interest;
        this.installmentsLeft = term;
    }

    /** Günlük taksit (son taksit kalan kadardır). */
    public int installment() { return (principal + interest + term - 1) / term; }

    public int getRemaining() { return remaining; }
    /** Gecikmiş (vadesi gelip ödenmemiş) taksit tutarı. */
    public int getOverdue() { return dueNow; }
    public int getFees() { return fees; }
    public int getInstallmentsLeft() { return installmentsLeft; }
    public int getMissedStreak() { return missedStreak; }
    public int getMissedTotal() { return missedTotal; }
    /** Toplam borç: kalan + gecikme bedeli. */
    public int totalOwed() { return remaining + fees; }

    /** Kalan anaparanın tahmini (kalan borcun anapara payı; limit hesabında kullanılır). */
    public int remainingPrincipal() {
        long total = (long) principal + interest;
        return total <= 0 ? 0 : (int) Math.round(remaining * (double) principal / total);
    }

    /** Kalan faiz. */
    public int remainingInterest() { return Math.max(0, remaining - remainingPrincipal()); }

    /** Erken kapatma tutarı: kalan anapara + kalan faizin %25'i + gecikme bedeli. */
    public int payoffAmount() {
        return remainingPrincipal() + (int) Math.ceil(remainingInterest() * Bank.EARLY_INTEREST_SHARE) + fees;
    }

    public boolean isClosed() { return remaining <= 0 && fees <= 0; }

    String serialize() {
        return principal + "," + interest + "," + term + "," + takenDay + "," + remaining + "," + dueNow + "," + fees + ","
                + installmentsLeft + "," + missedStreak + "," + missedTotal;
    }

    static Loan parse(int id, String v) {
        String[] p = v.split(",");
        if (p.length < 10) return null;
        int[] n = new int[10];
        for (int i = 0; i < 10; i++) n[i] = Integer.parseInt(p[i].trim());
        if (n[0] <= 0 || n[1] < 0 || n[2] <= 0) return null;
        Loan l = new Loan(id, n[0], n[1], n[2], Math.max(1, n[3]));
        l.remaining = Math.max(0, Math.min(n[0] + n[1], n[4]));
        l.dueNow = Math.max(0, Math.min(l.remaining, n[5]));
        l.fees = Math.max(0, n[6]);
        l.installmentsLeft = Math.max(0, Math.min(n[2], n[7]));
        l.missedStreak = Math.max(0, n[8]);
        l.missedTotal = Math.max(0, n[9]);
        return l;
    }

    @Override public String toString() { return "Kredi " + principal + "+" + interest + " " + term + " gün, kalan " + remaining; }
}
