package com.doner.oyunu.engine;

/**
 * v2.5: gün açılırken ödenmemiş faturaların zorluk kademeleri ve motor etkileri (gün içinde fatura ödenirse yeniden
 * hesaplanır). Kademeler: 0 yok, 1 kısmi, 2 tam (kira 3 = tahliye). Jeneratör elektrik, su deposu su kademesini düşürür.
 */
public final class UtilityStatus {
    /** Etkin kademeler (koruyucu yükseltmeler sonrası). */
    public final int electric, gas, water, rent, tax;
    /** Faturanın kendi kademesi (koruma öncesi; jeneratör/su deposu çalışıyor mu bilgisi için). */
    public final int electricBill, waterBill;
    /** Maaş borcu var: çırak bugün gelmez. */
    public final boolean apprenticeAbsent;

    private UtilityStatus(int electric, int gas, int water, int rent, int tax, int electricBill, int waterBill, boolean absent) {
        this.electric = electric;
        this.gas = gas;
        this.water = water;
        this.rent = rent;
        this.tax = tax;
        this.electricBill = electricBill;
        this.waterBill = waterBill;
        this.apprenticeAbsent = absent;
    }

    /** Hiç kesinti yok (v2.0 davranışı). */
    public static final UtilityStatus NONE = new UtilityStatus(0, 0, 0, 0, 0, 0, 0, false);

    /** Kayıttaki açık faturalara göre {@code day} günü açılırken durum. */
    public static UtilityStatus of(SaveData save, int day) {
        int e = 0, g = 0, w = 0, r = 0, t = 0;
        for (Bill b : save.bills) {
            int lv = b.level(day);
            switch (b.type) {
                case ELEKTRIK: e = Math.max(e, lv); break;
                case DOGALGAZ: g = Math.max(g, lv); break;
                case SU: w = Math.max(w, lv); break;
                case KIRA: r = Math.max(r, lv); break;
                default: t = Math.max(t, lv); break;
            }
        }
        Upgrades up = new Upgrades(save);
        int pe = Math.max(0, e - up.powerBackup()), pw = Math.max(0, w - up.waterBackup());
        boolean absent = save.wageDebt > 0 && up.apprenticeLevel() > 0;
        if (e == 0 && g == 0 && w == 0 && r == 0 && t == 0 && !absent) return NONE;
        return new UtilityStatus(pe, g, pw, r, t, e, w, absent);
    }

    public boolean any() { return electric > 0 || gas > 0 || water > 0 || rent > 0 || tax > 0 || apprenticeAbsent; }

    /** İstasyon süresi çarpanı (1 = etkisiz). */
    public double speedMult(Station st) {
        double m = 1.0;
        if (electric >= 1 && (st == Station.EKMEK || st == Station.ICECEK)) m *= electric >= 2 ? 1.6 : 1.3;
        if (electric >= 2 && st == Station.DONER) m *= 1.3;
        if (gas == 1 && st == Station.IZGARA) m *= 1.3;
        if (water >= 1 && st == Station.SOS) m *= 1.25;
        return m;
    }

    /** Pasif istasyon kapasitesi kesintiyle: elektrik ekmek ısıtıcıyı, doğalgaz Ocak'ı kısar (kapalı Ocak = 0). */
    public int capacity(Station st, int cap) {
        if (cap <= 0) return cap;
        if (st == Station.EKMEK && electric >= 2) return 1;
        if (st == Station.EKMEK && electric == 1) return Math.max(1, cap - 1);
        if (st == Station.IZGARA && gas >= 2) return 0;
        if (st == Station.IZGARA && gas == 1) return 1;
        return cap;
    }

    /** Ocak kapalı (doğalgaz tam kesik): Ocak isteyen ürünler menüden çıkar, patates yok. */
    public boolean ocakClosed() { return gas >= 2; }
    /** Su kesik: salata ve sos tepsisi hazırlanamaz. */
    public boolean trayBlocked() { return water >= 1; }
    /** Su tam kesik: çay ve çorba menüden çıkar. */
    public boolean waterProductsOff() { return water >= 2; }
    /** İstasyon kesinti yüzünden kapalı mı (yalnızca Ocak). */
    public boolean stationClosed(Station st) { return st == Station.IZGARA && ocakClosed(); }
    /** Bu kesintiyle ürün satılabilir mi. */
    public boolean productAvailable(Product p) {
        if (waterProductsOff() && p.needsWater()) return false;
        if (ocakClosed()) for (Step s : p.steps) if (s.station == Station.IZGARA) return false;
        return true;
    }
    /** Müşteri gelişi çarpanı: Ocak kapalı ×0,9 (menü daraldı), kira ihtarnamesi ×0,9. */
    public double arrivalMult() {
        double m = 1.0;
        if (ocakClosed()) m *= 0.9;
        if (rent >= 2) m *= 0.9;
        return m;
    }
    /** Teslim yıldız kazancı çarpanı: su tam kesik ×0,6 (hijyen). */
    public double starGainMult() { return waterProductsOff() ? 0.6 : 1.0; }
    /** Gün başında bozulan hazır ayran oranı (elektrik). */
    public double drinkSpoilFraction() { return electric >= 2 ? 1.0 : electric == 1 ? 0.5 : 0.0; }
    /** Gün sonunda et döner ve tavuk döner stoğunun %25'i bozulur (elektrik tam kesik). */
    public boolean meatSpoils() { return electric >= 2; }
    /** Kira ihtarnamesi: gün başında yıldız cezası. */
    public double rentStarPenalty() { return rent == 2 ? 0.15 : 0.0; }
    /** Tahliye: dükkân açılamaz (kira 6+ gün gecikti). */
    public boolean evicted() { return rent >= 3; }
}
