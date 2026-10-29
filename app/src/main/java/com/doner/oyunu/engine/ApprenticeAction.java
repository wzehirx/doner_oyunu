package com.doner.oyunu.engine;

/** Çırağın bir işi (başlangıç veya bitiş). Order null ise hazırlık işidir. */
public final class ApprenticeAction {
    /** 1 tabanlı çırak numarası. */
    public final int worker;
    /** true: iş başladı; false: iş bitti. */
    public final boolean started;
    public final Order order;
    /** Kalem indeksi; paketleme ve hazırlıkta -1. */
    public final int itemIndex;
    /** Yapılan adım (hazırlıkta sentetik adım, paketlemede "Siparişi paketle"). */
    public final Step step;
    /** Hazırlık türü; sipariş adımında null. */
    public final PrepKind prep;
    public final boolean pack;
    public final float seconds;

    ApprenticeAction(int worker, boolean started, Order order, int itemIndex, Step step, PrepKind prep, boolean pack, float seconds) {
        this.worker = worker;
        this.started = started;
        this.order = order;
        this.itemIndex = itemIndex;
        this.step = step;
        this.prep = prep;
        this.pack = pack;
        this.seconds = seconds;
    }

    public boolean isPrep() { return prep != null; }
}
