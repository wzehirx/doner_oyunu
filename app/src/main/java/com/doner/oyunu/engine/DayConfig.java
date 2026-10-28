package com.doner.oyunu.engine;

/** Gün N için zorluk parametreleri (saf fonksiyon). */
public final class DayConfig {
    public final int day;
    /** Zorluk yoğunluğu; 40. günden sonra döngüsel/yavaş artar. */
    public final double intensity;
    public final float duration;
    public final float overtime = 30f;
    /** Ortalama müşteri geliş aralığı (sn), 3 yıldız ve yükseltmesiz. */
    public final float arrivalInterval;
    /** Müşteri tipi sabrı çarpanı (gün ilerledikçe düşer). */
    public final float patienceFactor;
    /** Siparişin iş yüküne bağlı ek sabır (iş sn başına). */
    public final float workPatience;
    public final int maxItems;
    public final float multiItemChance;
    public final int baseQueue;
    public final int rent;

    private DayConfig(int day) {
        this.day = day;
        double i;
        if (day <= 40) {
            i = day;
        } else {
            int over = day - 40;
            // 10 günlük dalgalarla (döngüsel) ve her 20 günde bir kalıcı artışla yükselir.
            i = 40 + (over % 10) * 0.4 + (over / 10) * 3.0;
        }
        this.intensity = i;
        this.duration = Math.min(300f, 120f + 6f * day);
        this.arrivalInterval = (float) Math.max(4.2, 18.0 - 0.31 * i);
        this.patienceFactor = (float) Math.max(0.5, 1.0 - 0.010 * i);
        this.workPatience = (float) Math.max(0.7, 1.5 - 0.018 * i);
        this.maxItems = i < 5 ? 1 : (i < 14 ? 2 : 3);
        this.multiItemChance = (float) Math.min(0.75, 0.15 + i * 0.015);
        this.baseQueue = i < 10 ? 2 : (i < 25 ? 3 : 4);
        this.rent = 300 + 30 * (day - 1);
    }

    public static DayConfig forDay(int day) {
        if (day < 1) day = 1;
        return new DayConfig(day);
    }

    /** Yıldıza göre geliş sıklığı çarpanı: 1★ 0.6 ... 5★ 1.4. */
    public static double starMultiplier(double stars) {
        double s = Math.max(1.0, Math.min(5.0, stars));
        return 0.6 + 0.2 * (s - 1.0);
    }

    /** Sipariş başına beklenen ürün sayısı (market önerisi için). */
    public double expectedItemsPerOrder() {
        return 1.0 + (maxItems - 1) * multiItemChance * 0.8;
    }
}
