package com.doner.oyunu.engine;

import java.util.Set;

/**
 * Çalışan bir iş: sipariş adımı, paketleme veya hazırlık (v2.0 kaynak modeli). AKTİF iş, işi yapanın (oyuncu ya da
 * çırak) ellerini iş boyunca tutar. PASİF iş ({@link #isPassive()}) elleri yalnızca kısa koyma süresince tutar
 * ({@link #holdsHands()}), sonra istasyonun bir yerinde ({@link #getPlace()}) kendi başına pişer.
 */
public final class ActiveStep {
    /** Hazırlık işinde null. */
    public final Order order;
    public final Step step;
    public final float total;
    /** Oyuncu işinin sıra numarası (0..; eşzamanlı oyuncu işleri arasında; yalnızca ses/görsel anahtarı); çırak işinde -1. */
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
    /** Otomatik malzeme işi (v0.7, "auto"): eklenen ve (Rahat) çıkarılan malzemeler; değilse null. */
    java.util.List<String> autoAdded;
    java.util.List<String> autoRemoved;
    /** Otomatik işi başlatan akış (bitişte adımın kapanıp kapanmayacağı buna göre). */
    FlowMode autoMode;
    /** Dilimleme/ısıtma adımında kullanılan boy (boy gerektirmeyen işte null). */
    PortionSize size;
    /** v2.0: pasif iş (istasyon kendi pişirir). */
    boolean passive;
    /** v2.0: işi yapanın ellerinin bu işte kalan süresi (aktif işte = remaining; pasifte koyma süresi). */
    float handsLeft;
    /** v2.0: pasif işin istasyondaki yeri (0..kapasite-1); aktif işte -1. */
    int place = -1;

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
    /** Pasif iş mi (ekmek ısıtıcı, ocak: kendi pişirir). */
    public boolean isPassive() { return passive; }
    /** İş şu an işi yapanın ellerini tutuyor mu (aktif iş her zaman; pasif iş yalnızca koyarken). */
    public boolean holdsHands() { return !passive || handsLeft > 1e-6f; }
    /** Ellerin bu işte kalan süresi (sn). */
    public float getHandsRemaining() { return passive ? Math.max(0f, handsLeft) : remaining; }
    /** İşin istasyonu. */
    public Station getStation() { return step.station; }
    /** Pasif işin istasyondaki yeri (0 tabanlı göz/yer numarası); aktif işte -1. */
    public int getPlace() { return place; }
    public float progress() { return total <= 0 ? 1f : 1f - remaining / total; }
    public boolean isApprentice() { return worker > 0; }
    public boolean isPrep() { return prep != null; }
    public boolean isPack() { return pack; }
    /** Tek tek malzeme ekleme işi mi (kısa süreli, topping adımını tamamlamaz). */
    public boolean isToppingAdd() { return addId != null; }
    /** Otomatik malzeme işi mi (v0.7 Rahat/Dengeli "Malzemeleri ekle"). */
    public boolean isAutoToppings() { return autoAdded != null; }
    /** Eklenen malzemenin id'si (ekleme işi değilse null). */
    public String getAddedIngredient() { return addId; }
    /** Bu işin boyu (dilimleme/ekmek ısıtma); yoksa null. */
    public PortionSize getSize() { return size; }
    /** Hazırlıkta üretilen miktar (porsiyon/bardak/kullanım). */
    public int getAmount() { return amount; }
}
