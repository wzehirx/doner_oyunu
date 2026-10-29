package com.doner.oyunu.engine;

/** Hazırlık (önceden yapılan iş) türleri. Hazırlık bir işlem yuvasını (veya çırağı) kullanır. */
public enum PrepKind {
    /** Dilimlenmiş döner: 4 porsiyon, ürünün döner malzemesi x4 tüketir; "Döneri dilimle" adımı ~0,8 sn olur. */
    DONER("Döner dilimleme", Station.DONER, 4, 6f),
    /** Doldurulmuş içecek (ayran): 4 bardak; gün sonunda atılmaz, ertesi güne kalır. */
    DRINK("İçecek doldurma", Station.ICECEK, 4, 4f),
    /** Hazır salata/sos tepsisi: seçilen malzemelerden 6'şar kullanım; topping seçimini hızlandırır. */
    TRAY("Salata ve sos tepsisi", Station.SOS, 6, 5f);

    public final String displayName;
    public final Station station;
    /** Parti başına verim (döner/içecek: porsiyon/bardak; tepsi: malzeme başına kullanım). */
    public final int batch;
    public final float baseSeconds;

    PrepKind(String displayName, Station station, int batch, float baseSeconds) {
        this.displayName = displayName;
        this.station = station;
        this.batch = batch;
        this.baseSeconds = baseSeconds;
    }
}
