package com.doner.oyunu.engine;

/**
 * v2.7 "Anlatım düzeyi" ayarı (SaveData.narrationLevel). Yalnızca ekran okuyucu (TalkBack) AÇIKKEN geçerlidir: TalkBack
 * zaten odaktaki öğeyi okuduğundan oyun kendi anlatımını kısar. TalkBack kapalıyken her zaman {@link #HIGH} (eski davranış).
 * <ul>
 * <li>{@link #LOW} "Az" (TalkBack açıkken varsayılan): yalnızca {@link Importance#CRITICAL}.</li>
 * <li>{@link #MEDIUM} "Orta": CRITICAL + NORMAL.</li>
 * <li>{@link #HIGH} "Çok": her şey (v2.6 davranışı).</li>
 * </ul>
 * Oyuncunun açık okuma istekleri ("Durumu oku", "Stoğu oku", fatura/banka/fiyat okumaları) düzeyden bağımsız her zaman söylenir.
 */
public enum NarrationLevel {
    LOW(0, "Az", Importance.CRITICAL),
    MEDIUM(1, "Orta", Importance.NORMAL),
    HIGH(2, "Çok", Importance.VERBOSE);

    public final int id;
    public final String displayName;
    /** Bu düzeyde söylenen EN DÜŞÜK önem (CRITICAL < NORMAL < VERBOSE sıralamasında bu sınıfa kadar). */
    private final Importance floor;

    public static final NarrationLevel DEFAULT = LOW;

    NarrationLevel(int id, String displayName, Importance floor) {
        this.id = id;
        this.displayName = displayName;
        this.floor = floor;
    }

    /** Duyuru bu düzeyde söylenir mi. */
    public boolean allows(Importance imp) {
        return imp == null || imp.ordinal() <= floor.ordinal();
    }

    /** Kayıttaki sayıdan düzey (geçersiz: varsayılan). */
    public static NarrationLevel fromId(int id) {
        for (NarrationLevel l : values()) if (l.id == id) return l;
        return DEFAULT;
    }

    /** Etkin düzey: ekran okuyucu açıksa ayar, kapalıysa her zaman {@link #HIGH}. */
    public static NarrationLevel effective(NarrationLevel setting, boolean screenReaderOn) {
        return screenReaderOn ? (setting == null ? DEFAULT : setting) : HIGH;
    }
}
