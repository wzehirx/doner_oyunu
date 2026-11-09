package com.doner.oyunu.engine;

/**
 * v2.5 açık fatura. Gün {@link #issuedDay} sonunda kesilir; {@link #dueDay} gününe kadar (o gün dahil) ödenmeden gün
 * açılabilir. Gecikme günü = açılan gün − son ödeme günü; her gecikme günü tutarın %3'ü kadar gecikme bedeli ekler.
 * Ödenen fatura listeden çıkar (kısmi ödeme yalnızca haciz ile olur: {@link #getPaid()}).
 */
public final class Bill {
    public final int id;
    public final BillType type;
    /** Fatura tutarı (gecikme bedeli hariç). */
    public final int amount;
    public final int issuedDay;
    /** Son ödeme günü: bu gün açılırken fatura gecikmiş sayılmaz. */
    public final int dueDay;
    int paid;

    Bill(int id, BillType type, int amount, int issuedDay, int dueDay, int paid) {
        this.id = id;
        this.type = type;
        this.amount = amount;
        this.issuedDay = issuedDay;
        this.dueDay = dueDay;
        this.paid = paid;
    }

    /** Haciz ile ödenmiş kısım. */
    public int getPaid() { return paid; }

    /** {@code day} günü açılırken kaç gün gecikmiş (0 = zamanında). */
    public int daysLate(int day) { return Math.max(0, day - dueDay); }

    /** Son ödeme gününe kalan gün ({@code day} = son gün ise 0; gecikmişse negatif). */
    public int daysLeft(int day) { return dueDay - day; }

    /** Gecikme bedeli (her gecikme günü tutarın %3'ü). */
    public int lateFee(int day) { return (int) Math.round(amount * Finance.LATE_FEE_RATE * daysLate(day)); }

    /** {@code day} günü itibarıyla ödenecek tutar (tutar + gecikme bedeli − haciz ile ödenen). */
    public int amountDue(int day) { return Math.max(0, amount + lateFee(day) - paid); }

    /**
     * Zorluk kademesi ({@code day} günü açılırken): 0 yok, 1 kısmi (1–2 gün gecikme), 2 tam (3+ gün). Kira: 2 = ihtarname
     * (3–5 gün), 3 = tahliye (6+ gün). Vergi: 2 = haciz (4+ gün).
     */
    public int level(int day) {
        int late = daysLate(day);
        if (late <= 0) return 0;
        switch (type) {
            case KIRA: return late >= Finance.EVICTION_DAYS ? 3 : late >= 3 ? 2 : 1;
            case VERGI: return late >= 4 ? 2 : 1;
            default: return late >= 3 ? 2 : 1;
        }
    }

    String serialize() { return type.id + "," + amount + "," + issuedDay + "," + dueDay + "," + paid; }

    static Bill parse(int id, String v) {
        String[] p = v.split(",");
        if (p.length < 5) return null;
        BillType t = BillType.byId(p[0].trim());
        if (t == null) return null;
        int amount = Integer.parseInt(p[1].trim()), issued = Integer.parseInt(p[2].trim()), due = Integer.parseInt(p[3].trim()),
                paid = Integer.parseInt(p[4].trim());
        if (amount <= 0 || issued < 1 || due < issued) return null;
        return new Bill(id, t, amount, issued, due, Math.max(0, Math.min(amount * 10, paid)));
    }

    @Override public String toString() { return type.displayName + " " + amount + " (gün " + issuedDay + ", son " + dueDay + ")"; }
}
