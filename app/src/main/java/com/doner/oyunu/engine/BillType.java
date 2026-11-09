package com.doner.oyunu.engine;

/**
 * v2.5 fatura türleri: kesildiği ilk gün ve dönem (gün). Fatura gün SONUNDA kesilir; son ödeme günü = kesildiği gün + 2
 * ({@link Finance#DUE_DAYS}). Tutarlar {@link Finance#billAmount}.
 */
public enum BillType {
    ELEKTRIK("elektrik", "Elektrik faturası", "elektrik", 3, 7),
    DOGALGAZ("dogalgaz", "Doğalgaz faturası", "doğalgaz", 4, 7),
    SU("su", "Su faturası", "su", 5, 7),
    KIRA("kira", "Kira", "kira", 7, 7),
    VERGI("vergi", "Vergi", "vergi", 10, 10);

    public final String id;
    /** Görünen ad ("Elektrik faturası", "Kira"). */
    public final String displayName;
    /** Kısa ad (cümle içinde, küçük harf): "elektrik", "kira". */
    public final String shortName;
    /** İlk kesildiği gün (gün sonu). */
    public final int firstDay;
    /** Kaç günde bir kesilir. */
    public final int period;

    BillType(String id, String displayName, String shortName, int firstDay, int period) {
        this.id = id;
        this.displayName = displayName;
        this.shortName = shortName;
        this.firstDay = firstDay;
        this.period = period;
    }

    /** Bu tür fatura gün {@code day}'in sonunda kesilir mi. */
    public boolean issuedOn(int day) { return day >= firstDay && (day - firstDay) % period == 0; }

    /** Bu türün {@code day} ya da sonrasında kesileceği ilk gün. */
    public int nextIssueDay(int day) {
        if (day <= firstDay) return firstDay;
        int k = (day - firstDay + period - 1) / period;
        return firstDay + k * period;
    }

    public static BillType byId(String id) {
        for (BillType t : values()) if (t.id.equals(id)) return t;
        return null;
    }
}
