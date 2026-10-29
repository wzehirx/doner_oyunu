package com.doner.oyunu.engine;

/** Satın alınabilir yükseltme (mutfak aleti veya restoran öğesi). Seviye 0 = başlangıç. */
public final class Upgrade {
    public enum Category { MUTFAK, RESTORAN }

    public enum EffectType {
        /** Hedef istasyonlarda adım süresi çarpanı (değer = çarpan, küçük = hızlı). */
        STATION_SPEED,
        /** Ek eşzamanlı oyuncu yuvası (şu an hiçbir yükseltme kullanmıyor). */
        SLOTS,
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
        TOURIST
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

    public int maxLevel() { return prices.length; }

    /** level -> level+1 fiyatı; maksimumdaysa -1. */
    public int priceForNext(int level) {
        return level >= prices.length ? -1 : prices[level];
    }

    public int unlockDayForNext(int level) {
        return level >= prices.length ? Integer.MAX_VALUE : unlockDays[level];
    }
}
