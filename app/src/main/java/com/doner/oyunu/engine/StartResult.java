package com.doner.oyunu.engine;

public enum StartResult {
    STARTED, NO_ORDER, WRONG_STATION, NO_SLOT, OUT_OF_STOCK, ORDER_BUSY, ORDER_READY, NOT_RUNNING,
    /** Adım topping seçimi: zamanlayıcı BAŞLAMADI; UI malzeme panelini açıp confirmToppings çağırmalı. */
    NEEDS_TOPPINGS,
    /** Adım var ama henüz açılmadı (topping adımı için önce kalemin diğer adımları bitmeli; paketleme için tüm kalemler). */
    STEP_LOCKED,
    /** Geçersiz kalem/seçim (confirmToppings: havuz dışı malzeme, adım açık değil). */
    INVALID
}
