package com.doner.oyunu.engine;

/** Satın alınabilir yükseltme (mutfak aleti veya restoran öğesi). Seviye 0 = başlangıç. */
public final class Upgrade {
    public enum Category { MUTFAK, RESTORAN }

    public enum EffectType {
        /** Hedef istasyonlarda adım süresi çarpanı (değer = çarpan, küçük = hızlı). */
        STATION_SPEED,
        /**
         * ESKİ (v0.9'a dek): ek ortak işlem yuvası. v2.0'da ortak yuva yoktur; hiçbir yükseltme kullanmaz ve etkisi yoktur
         * (yerine {@link #STATION_CAPACITY}). Kayıt/derleme uyumu için durur.
         */
        @Deprecated SLOTS,
        /** Çırak seviyesi = otomatik işçi sayısı ve yetenek seviyesi (1..3). */
        APPRENTICE,
        /** Sabır çarpanına eklenen oran. */
        PATIENCE,
        /** Müşteri geliş sıklığına eklenen oran. */
        ARRIVAL,
        /** Bahşiş çarpanına eklenen oran. */
        TIP,
        /** Yıldız artışlarına eklenen oran. */
        STAR_GAIN,
        /** Malzeme başına stok kapasitesi (mutlak değer). */
        STOCK_CAP,
        /** Aynı anda bekleyen sipariş üst sınırına ek. */
        QUEUE,
        /** Turist müşteri ağırlığına eklenen oran. */
        TOURIST,
        /** v2.0: hedef PASİF istasyonun aynı anda alabileceği iş sayısına ek (ekmek ısıtıcı yeri, ocak gözü). */
        STATION_CAPACITY,
        /** v2.5: hazır stok kapasitesine ek (döner porsiyon, ayran bardak, patates porsiyon; tepside ×1,5). */
        READY_CAP,
        /** v2.5: jeneratör: elektrik kesintisini bu kadar kademe karşılar. */
        POWER_BACKUP,
        /** v2.5: su deposu: su kesintisini bu kadar kademe karşılar. */
        WATER_BACKUP
    }

    /** values[k-1] = k. seviyedeki toplam (kümülatif) değer. */
    public static final class Effect {
        public final EffectType type;
        public final Station[] targets;
        public final double[] values;

        public Effect(EffectType type, Station[] targets, double... values) {
            this.type = type;
            this.targets = targets;
            this.values = values;
        }
    }

    public final String id;
    public final String name;
    public final Category category;
    public final String description;
    /** prices[k] = k -> k+1 seviyesi fiyatı. */
    public final int[] prices;
    /** unlockDays[k] = k+1. seviyenin açıldığı gün. */
    public final int[] unlockDays;
    public final Effect[] effects;
    /** v2.5: bu yükseltmenin artırdığı faturalar (elektrik/doğalgaz/su; boş olabilir). Bkz. {@link Finance#billAmount}. */
    public BillType[] utilities = new BillType[0];

    public Upgrade(String id, String name, Category category, String description,
                   int[] prices, int[] unlockDays, Effect... effects) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.prices = prices;
        this.unlockDays = unlockDays;
        this.effects = effects;
    }

    /** Katalog kurulumu: faturaya etkisi. */
    Upgrade uses(BillType... t) { this.utilities = t; return this; }

    /** Bu yükseltme verilen faturayı artırır mı. */
    public boolean raises(BillType t) {
        for (BillType u : utilities) if (u == t) return true;
        return false;
    }

    public int maxLevel() { return prices.length; }

    /** level -> level+1 fiyatı; maksimumdaysa -1. */
    public int priceForNext(int level) {
        return level >= prices.length ? -1 : prices[level];
    }

    public int unlockDayForNext(int level) {
        return level >= prices.length ? Integer.MAX_VALUE : unlockDays[level];
    }
}
