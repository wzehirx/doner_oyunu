package com.doner.oyunu.engine;

import java.util.Set;

/** Çalışan bir iş: sipariş adımı, paketleme veya hazırlık. Oyuncu işleri yuva (slot) kullanır, çırak işleri kullanmaz. */
public final class ActiveStep {
    /** Hazırlık işinde null. */
    public final Order order;
    public final Step step;
    public final float total;
    /** Oyuncu yuvası (0..); çırak işinde -1. */
    public final int slot;
    /** 0 = oyuncu, 1..3 = çırak numarası. */
    public final int worker;
    /** Kalem indeksi; paketleme ve hazırlıkta -1. */
    public final int itemIndex;
    final int stepIndex;
    /** Hazırlık türü (sipariş işinde null). */
    public final PrepKind prep;
    final Set<String> ids;
    final boolean pack;
    float remaining;
    /** Hazırlıkta üretilen miktar (döner: porsiyon 2/4, içecek: bardak, tepsi: malzeme başına kullanım). */
    int amount;
    /** Tek tek malzeme ekleme işi (SOS istasyonu): eklenen malzeme id'si; değilse null. */
    String addId;
    boolean addFromTray;
    /** Dilimleme/ısıtma adımında kullanılan boy (boy gerektirmeyen işte null). */
    PortionSize size;

    ActiveStep(Order order, int itemIndex, int stepIndex, Step step, float total, int slot, int worker,
               PrepKind prep, Set<String> ids, boolean pack) {
        this.order = order;
        this.itemIndex = itemIndex;
        this.stepIndex = stepIndex;
        this.step = step;
        this.total = total;
        this.remaining = total;
        this.slot = slot;
        this.worker = worker;
        this.prep = prep;
        this.ids = ids;
        this.pack = pack;
    }

    public float getRemaining() { return remaining; }
    public float progress() { return total <= 0 ? 1f : 1f - remaining / total; }
    public boolean isApprentice() { return worker > 0; }
    public boolean isPrep() { return prep != null; }
    public boolean isPack() { return pack; }
    /** Tek tek malzeme ekleme işi mi (kısa süreli, topping adımını tamamlamaz). */
    public boolean isToppingAdd() { return addId != null; }
    /** Eklenen malzemenin id'si (ekleme işi değilse null). */
    public String getAddedIngredient() { return addId; }
    /** Bu işin boyu (dilimleme/ekmek ısıtma); yoksa null. */
    public PortionSize getSize() { return size; }
    /** Hazırlıkta üretilen miktar (porsiyon/bardak/kullanım). */
    public int getAmount() { return amount; }
}
