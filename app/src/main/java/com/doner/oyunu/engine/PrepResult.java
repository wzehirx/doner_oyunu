package com.doner.oyunu.engine;

/** startPrep sonucu. */
public enum PrepResult {
    STARTED,
    /** Gün bitti/başarısız. */
    NOT_ALLOWED,
    /** ESKİ (v0.9). v2.0 motoru döndürmez: {@link #HANDS_BUSY} / {@link #STATION_FULL}. */
    @Deprecated NO_SLOT,
    OUT_OF_STOCK,
    /** Hazır stok kapasitesi dolu (devam eden hazırlıklar dahil). */
    FULL,
    /** Geçersiz malzeme (döner için ET kategorisi, tepsi için SOS/SALATA gerekir). */
    INVALID,
    /** v2.0: oyuncunun eli dolu (başka aktif iş sürüyor). */
    HANDS_BUSY,
    /** v2.0: pasif istasyon dolu (patates hazırlığı için ocak gözleri). */
    STATION_FULL
}
