package com.doner.oyunu.engine;

/** Hazırlık istasyonları. */
public enum Station {
    DONER(1, "Döner makinesi"),
    EKMEK(2, "Ekmek ısıtıcı"),
    SOS(3, "Sos ve salata tezgahı"),
    IZGARA(4, "Izgara ve fırın"),
    PAKET(5, "Paketleme"),
    ICECEK(6, "İçecek dolabı"),
    TATLI(7, "Tatlı tezgahı");

    public final int id;
    public final String displayName;

    Station(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public static Station byId(int id) {
        for (Station s : values()) if (s.id == id) return s;
        return null;
    }
}
