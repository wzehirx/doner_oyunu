package com.doner.oyunu.engine;

public enum StartResult {
    STARTED, NO_ORDER, WRONG_STATION,
    /** ESKİ (v0.9 ortak yuva). v2.0 motoru bunu DÖNDÜRMEZ; yerine {@link #HANDS_BUSY} ya da {@link #STATION_FULL}. */
    @Deprecated NO_SLOT,
    OUT_OF_STOCK,
    /** O iş (adım/paketleme/ekleme) zaten sürüyor (oyuncu ya da çırak yapıyor); {@code onAlreadyRunning} verilir. */
    ORDER_BUSY,
    ORDER_READY, NOT_RUNNING,
    /** Adım topping seçimi: zamanlayıcı BAŞLAMADI; UI malzeme panelini açıp confirmToppings çağırmalı. */
    NEEDS_TOPPINGS,
    /** Adım var ama henüz açılmadı (topping adımı için önce kalemin diğer adımları bitmeli; paketleme için tüm kalemler). */
    STEP_LOCKED,
    /** Geçersiz kalem/seçim (confirmToppings: havuz dışı malzeme, adım açık değil). */
    INVALID,
    // ---- v0.6 (applyStationOption / tek tek malzeme)
    /** Seçenek kilitli (ör. boy henüz açılmadı); {@code onOptionRejected} nedenini bildirir. */
    LOCKED,
    /** Malzeme zaten iki kez (bol) eklendi; daha fazlası reddedilir. */
    MAX_REACHED,
    /** Geri alınacak malzeme yok (ya da malzemeler bitirildi). */
    NOTHING_TO_UNDO,
    /** Hazır stok kapasitesi dolu (hazırlık seçenekleri). */
    FULL,
    /** Bilgi okuma seçeneği uygulandı (eklenenleri/hedefi oku): durum değişmedi, {@code onStationInfo} verildi. */
    INFO,
    /** Bilinmeyen seçenek kimliği. */
    UNKNOWN_OPTION,
    // ---- v2.0 kaynak modeli
    /** Oyuncunun eli dolu: başka bir AKTİF iş (ya da pasif işi koyma anı) sürüyor; {@code onHandsBusy} verilir. */
    HANDS_BUSY,
    /** Pasif istasyon dolu (ör. "Ocak dolu (2/2)"); {@code onStationFull} verilir. */
    STATION_FULL
}
