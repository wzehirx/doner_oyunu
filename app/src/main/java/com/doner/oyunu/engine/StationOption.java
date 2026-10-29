package com.doner.oyunu.engine;

/**
 * İstasyon eylem menüsünün bir seçeneği (TalkBack özel eylemi veya seçenek paneli düğmesi).
 * {@link GameEngine#getStationOptions} üretir, {@link GameEngine#applyStationOption} uygular.
 */
public final class StationOption {
    /** Seçenek grubu (UI sıralama/ikon için). */
    public enum Group {
        /** Siparişin adımını başlatır (ekmeği ısıt, ayranı doldur, paketle...). */
        STEP,
        /** Döner boyu seçimi. */
        SIZE,
        /** Tek malzeme ekle. */
        ADD,
        /** Eklenmiş bir malzemeyi çıkar (v0.7 Dengeli akışta istek farkı: "Soğan çıkar"). */
        REMOVE,
        /** Son ekleneni geri al / malzemeleri bitir. */
        CONTROL,
        /** Bilgi okuma (eklenenleri oku, hedefi oku): seçilince yalnızca duyuru yapılır. */
        READ,
        /** Hazırlık (önceden dilimle, ayran doldur, tepsi hazırla). */
        PREP
    }

    /** Kararlı kimlik: "step", "auto" (v0.7 otomatik malzeme), "remove:soğan" (v0.7 çıkar), "size:yarim|tam|duble", "add:domates", "undo", "finish", "read:added", "read:target",
     *  "prep:doner:et_doner:4", "prep:doner:et_doner:2", "prep:drink", "prep:tray". */
    public final String id;
    public final Group group;
    /** Kısa Türkçe ad: "Domates ekle", "Duble", "Ekmeği ısıt". */
    public final String name;
    /** Durumu içeren açıklama: "Domates ekle, stokta 5, 1 kez eklendi". */
    public final String description;
    public final boolean enabled;
    /** Etkin değilse nedeni ("Stokta yok", "2. günde açılır"...), etkinse null. */
    public final String lockReason;
    /** Malzeme seçeneklerinde malzeme id'si, değilse null. */
    public final String ingredientId;
    /**
     * Malzeme stokta yok. Seçenek yine de ETKİN kalır: uygulanırsa {@code OUT_OF_STOCK} döner ve motor olay verir;
     * UI acil alımı ({@code emergencyBuy(ingredientId)} / {@code emergencyBuyMissing(orderId)}) önerebilir.
     */
    public final boolean outOfStock;

    StationOption(String id, Group group, String name, String description, boolean enabled, String lockReason,
                  String ingredientId, boolean outOfStock) {
        this.id = id;
        this.group = group;
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.lockReason = enabled ? null : lockReason;
        this.ingredientId = ingredientId;
        this.outOfStock = outOfStock;
    }

    /** TalkBack eylem etiketi: durumu içerir ("Ketçap ekle, stokta 5", "Duble, 4. günde açılır"). */
    public String label() {
        if (!enabled) return name + ", " + lockReason;
        return description;
    }

    /**
     * Tek dokunuşla (panel açmadan) uygulanacak seçenek; yoksa null (panel açılmalı). Kural: hazırlık dışı seçeneklerde
     * etkin işlem yoksa kilit nedenini duyurmak için ilk işlem seçeneği; okuma seçenekleri ("Hedefi oku") sayılmadan TEK
     * işlem seçeneği varsa o; yalnızca hazırlık varsa ve tek ise o. TalkBack Eylemleri listesi yine tüm seçenekleri içerir.
     */
    public static StationOption direct(java.util.List<StationOption> ops) {
        java.util.List<StationOption> np = new java.util.ArrayList<>();
        for (StationOption op : ops) if (op.group != Group.PREP) np.add(op);
        if (np.isEmpty()) return ops.size() == 1 ? ops.get(0) : null;
        boolean actionable = false;
        for (StationOption op : np) if (op.enabled && op.group != Group.READ) actionable = true;
        if (!actionable) {
            for (StationOption op : np) if (op.group != Group.READ) return op;
            return np.get(0);
        }
        StationOption only = null;
        int n = 0;
        for (StationOption op : np) if (op.group != Group.READ) { only = op; n++; }
        return n == 1 ? only : null;
    }

    @Override public String toString() { return id + (enabled ? "" : "(kilitli: " + lockReason + ")"); }
}
