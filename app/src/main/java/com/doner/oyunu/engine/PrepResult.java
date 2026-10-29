package com.doner.oyunu.engine;

/** startPrep sonucu. */
public enum PrepResult {
    STARTED,
    /** Gün bitti/başarısız. */
    NOT_ALLOWED,
    NO_SLOT,
    OUT_OF_STOCK,
    /** Hazır stok kapasitesi dolu (devam eden hazırlıklar dahil). */
    FULL,
    /** Geçersiz malzeme (döner için ET kategorisi, tepsi için SOS/SALATA gerekir). */
    INVALID
}
