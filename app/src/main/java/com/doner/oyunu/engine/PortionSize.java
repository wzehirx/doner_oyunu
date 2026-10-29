package com.doner.oyunu.engine;

/**
 * Döner porsiyon boyu (dilimleme). Döner içeren kalemlerde sipariş kalemi bir boy taşır; oyuncu DONER istasyonunda
 * dilimleme boyunu seçer, seçim istenenden farklıysa "boy hatası" olur.
 * Sıra (ordinal) küçükten büyüğe: HALF &lt; FULL &lt; DOUBLE.
 */
public enum PortionSize {
    /** Yarım ekmek: 0,5 porsiyon, ürün fiyatının %60'ı, 2. günden itibaren istenebilir. */
    HALF("yarim", "Yarım ekmek", 1, 0.60, 0.6f, 2),
    /** Tam ekmek: 1 porsiyon, %100, ilk günden itibaren. */
    FULL("tam", "Tam ekmek", 2, 1.00, 1.0f, 1),
    /** Duble: 2 porsiyon, %170, 4. günden itibaren. */
    DOUBLE("duble", "Duble", 4, 1.70, 1.5f, 4);

    public final String id;
    /** Oyuncuya gösterilen ad: "Yarım ekmek", "Tam ekmek", "Duble". */
    public final String displayName;
    /** İç sayım: yarım porsiyon birimi (HALF 1, FULL 2, DOUBLE 4). Et/ekmek tüketimi ve hazır döner bu birimle tutulur. */
    public final int halves;
    public final double priceMult;
    /** Dilimleme (ve ekmek ısıtma) süresi çarpanı. */
    public final float timeMult;
    /** Müşterinin isteyebileceği / oyuncunun seçebileceği ilk gün. */
    public final int unlockDay;

    PortionSize(String id, String displayName, int halves, double priceMult, float timeMult, int unlockDay) {
        this.id = id;
        this.displayName = displayName;
        this.halves = halves;
        this.priceMult = priceMult;
        this.timeMult = timeMult;
        this.unlockDay = unlockDay;
    }

    /** Porsiyon cinsinden (0,5 / 1 / 2). */
    public double portions() { return halves / 2.0; }

    public boolean isOpen(int day) { return day >= unlockDay; }

    /** Duyuru metni: "yarım ekmek", "tam ekmek", "duble". */
    public String spoken() { return displayName.toLowerCase(new java.util.Locale("tr")); }

    public static PortionSize byId(String id) {
        for (PortionSize s : values()) if (s.id.equals(id)) return s;
        return null;
    }

    /** "0,5 porsiyon" / "1 porsiyon" / "2 porsiyon" biçimi için yarım porsiyon sayacından metin. */
    public static String halvesText(int halves) {
        return (halves / 2) + (halves % 2 == 1 ? ",5" : "");
    }
}
