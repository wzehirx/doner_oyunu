package com.doner.oyunu.engine;

/**
 * v2.0: siparişte "şimdi ne yapılabilir" listesinin bir satırı ({@link GameEngine#getReadyActions}). UI tüm kalemleri ve
 * adımları dolaşmak zorunda kalmadan sipariş kartını / sesli özeti bundan kurar.
 * <ul>
 * <li>{@link Status#NOW}: hemen başlatılabilir. {@link #optionId} doluysa {@code applyStationOption(orderId, itemIndex,
 *     station.id, optionId)} tek dokunuşla uygular; {@link #needsMenu} ise istasyonun seçenek menüsü açılmalı (boy seçimi,
 *     tek tek malzeme).</li>
 * <li>{@link Status#HANDS_BUSY}, {@link Status#STATION_FULL}, {@link Status#OUT_OF_STOCK}: adım açık ama şu an kaynak
 *     yok (elin dolu / "Ocak dolu (2/2)" / malzeme yok: acil alım).</li>
 * <li>{@link Status#RUNNING}: iş sürüyor ({@link #seconds} = kalan süre, {@link #byApprentice}).</li>
 * </ul>
 * Henüz açılmamış (sıra bekleyen) adımlar listede YOKTUR.
 */
public final class ReadyAction {
    public enum Kind {
        /** Kalem adımı (ısıt, dilimle, malzeme, doldur...). */
        STEP,
        /** "Siparişi paketle" (tüm kalemler hazır). */
        PACK,
        /** Sipariş paketli: teslim et ({@code engine.deliver(orderId)}). */
        DELIVER
    }

    public enum Status { NOW, HANDS_BUSY, STATION_FULL, OUT_OF_STOCK, RUNNING }

    public final Kind kind;
    public final Status status;
    public final int orderId;
    /** Kalem indeksi; PACK ve DELIVER'da -1. */
    public final int itemIndex;
    /** İstasyon; DELIVER'da null. */
    public final Station station;
    /** Tek dokunuşla uygulanacak seçenek ("step", "auto", "size:tam"...); menü gerekiyorsa ya da RUNNING/DELIVER'da null. */
    public final String optionId;
    /** Seçenek menüsü gerekli (boy seçimi, tek tek malzeme). */
    public final boolean needsMenu;
    /** Pasif iş (koy-geç: ekmek ısıtıcı, ocak). */
    public final boolean passive;
    /** RUNNING: işi çırak yapıyor. */
    public final boolean byApprentice;
    /** NOW: tahmini süre (sn); RUNNING: kalan süre; diğerlerinde 0. */
    public final float seconds;
    /** Adımın adı ("Ekmeği ısıt", "Siparişi paketle", "Teslim et"). */
    public final String stepName;
    /** Kısa Türkçe satır: "Ekmeği ısıt, Ekmek ısıtıcı" / "Patates kızart, Ocak dolu (2/2)" / "Ekmeği ısıt sürüyor, 2 saniye, çırak". */
    public final String text;

    ReadyAction(Kind kind, Status status, int orderId, int itemIndex, Station station, String optionId, boolean needsMenu,
                boolean passive, boolean byApprentice, float seconds, String stepName, String text) {
        this.kind = kind;
        this.status = status;
        this.orderId = orderId;
        this.itemIndex = itemIndex;
        this.station = station;
        this.optionId = optionId;
        this.needsMenu = needsMenu;
        this.passive = passive;
        this.byApprentice = byApprentice;
        this.seconds = seconds;
        this.stepName = stepName;
        this.text = text;
    }

    /** Şimdi başlatılabilir mi. */
    public boolean isDoable() { return status == Status.NOW; }

    @Override public String toString() { return kind + ":" + status + ":" + text; }
}
