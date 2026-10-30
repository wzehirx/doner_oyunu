package com.doner.oyunu.engine;

/** Satın alınmış yükseltmelerin toplam etkileri (SaveData üzerinden hesaplanır). */
public final class Upgrades {
    private final SaveData save;

    public Upgrades(SaveData save) { this.save = save; }

    private double sum(Upgrade.EffectType type) {
        double t = 0;
        for (Upgrade u : Catalog.upgrades()) {
            int lv = save.level(u.id);
            if (lv <= 0) continue;
            for (Upgrade.Effect e : u.effects) {
                if (e.type == type) t += e.values[Math.min(lv, e.values.length) - 1];
            }
        }
        return t;
    }

    /** İstasyon adım süresi çarpanı (1 = değişim yok). */
    public double speedMult(Station st) {
        double m = 1.0;
        for (Upgrade u : Catalog.upgrades()) {
            int lv = save.level(u.id);
            if (lv <= 0) continue;
            for (Upgrade.Effect e : u.effects) {
                if (e.type != Upgrade.EffectType.STATION_SPEED) continue;
                for (Station t : e.targets) {
                    if (t == st) m *= e.values[Math.min(lv, e.values.length) - 1];
                }
            }
        }
        return m;
    }

    /**
     * ESKİ ad (v0.9): oyuncunun aynı anda yapabileceği AKTİF iş sayısı = el sayısı. v2.0'da her zaman 1'dir (ortak yuva
     * yoktur; makineler kendi kapasitesiyle çalışır, ek el = çırak). {@link #hands()} ile aynı.
     */
    @Deprecated public int slots() { return hands(); }

    /** Oyuncunun eli: aynı anda 1 AKTİF iş (pasif işler yalnızca koyma anında eli tutar). Çırak kendi elleriyle çalışır. */
    public int hands() { return 1; }

    /** Pasif istasyonların temel kapasitesi (v2.0): ekmek ısıtıcı 2 yer, ocak 2 göz; diğer istasyonlar pasif iş almaz (0). */
    public static int baseCapacity(Station st) {
        if (st == Station.EKMEK || st == Station.IZGARA) return 2;
        return 0;
    }

    /** Pasif istasyonun aynı anda alabileceği iş sayısı (temel + yükseltme); pasif olmayan istasyonda 0. */
    public int stationCapacity(Station st) {
        int base = baseCapacity(st);
        if (base <= 0) return 0;
        double add = 0;
        for (Upgrade u : Catalog.upgrades()) {
            int lv = save.level(u.id);
            if (lv <= 0) continue;
            for (Upgrade.Effect e : u.effects) {
                if (e.type != Upgrade.EffectType.STATION_CAPACITY || e.targets == null) continue;
                for (Station t : e.targets) if (t == st) add += e.values[Math.min(lv, e.values.length) - 1];
            }
        }
        return base + (int) Math.round(add);
    }

    /** Çırak seviyesi 0..3: otomatik işçi sayısı ve yetenek seviyesi. */
    public int apprenticeLevel() { return (int) Math.round(sum(Upgrade.EffectType.APPRENTICE)); }

    /** Hazır stok kapasitesi (buzdolabı seviyesiyle artar): döner 8, içecek 8, tepsi (malzeme başına kullanım) 12. */
    public int readyCapacity(PrepKind kind) {
        int lv = save.level("buzdolabi");
        switch (kind) {
            case TRAY: return 12 + 6 * lv;
            default: return 8 + 4 * lv;
        }
    }
    public double patienceMult() { return 1.0 + sum(Upgrade.EffectType.PATIENCE); }
    public double arrivalMult() { return 1.0 + sum(Upgrade.EffectType.ARRIVAL); }
    public double tipMult() { return 1.0 + sum(Upgrade.EffectType.TIP); }
    public double starGainMult() { return 1.0 + sum(Upgrade.EffectType.STAR_GAIN); }
    public double touristMult() { return 1.0 + sum(Upgrade.EffectType.TOURIST); }
    public int queueBonus() { return (int) Math.round(sum(Upgrade.EffectType.QUEUE)); }

    /** Malzeme başına stok kapasitesi (buzdolabı). */
    public int stockCap() {
        double best = 30;
        for (Upgrade u : Catalog.upgrades()) {
            int lv = save.level(u.id);
            if (lv <= 0) continue;
            for (Upgrade.Effect e : u.effects) {
                if (e.type == Upgrade.EffectType.STOCK_CAP) best = Math.max(best, e.values[Math.min(lv, e.values.length) - 1]);
            }
        }
        return (int) best;
    }
}
