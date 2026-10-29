package com.doner.oyunu.engine;

/** Gün sonu özeti. */
public final class DaySummary {
    public int day;
    public boolean failed;
    public int revenue;
    public int tips;
    public int emergencySpend;
    /** Gerçekten kasadan düşülen kira (para yetmezse rentDue'dan az olabilir). */
    public int rent;
    /** O gün için hesaplanan tam kira. */
    public int rentDue;
    public int delivered;
    public int lost;
    public int wrongTaps;
    public double startStars;
    public double endStars;
    public int moneyAfter;
    /** Gün sonunda atılan hazır döner porsiyonu + tepsi kullanımı (israf). */
    public int wastedPortions;
    /** Atılan hazır stokun malzeme değeri (TL; hazırlanırken zaten harcanmıştı). */
    public int wastedCost;
    /** Hatalı malzemeli teslim sayısı / kusursuz malzemeli teslim sayısı. */
    public int mistakes;
    public int perfect;
    /** Boy hatası içeren teslim sayısı (mistakes sayısına da dahildir). */
    public int sizeMistakes;
    /** Biten hazırlık işi sayısı (oyuncu + çırak) ve çırağın bitirdiği sipariş adımı sayısı. */
    public int prepBatches;
    public int apprenticeTasks;
    /** Sonraki gün açılan yeni ürünler (adlar). */
    public String newProductsNext = "";
    /** Sonraki gün açılan yeni sos/salata malzemeleri (adlar). */
    public String newIngredientsNext = "";

    /** Para yetmediği için ödenemeyen kira. */
    public int rentUnpaid() { return Math.max(0, rentDue - rent); }

    public int profit() { return revenue + tips - emergencySpend - rent; }

    @Override
    public String toString() {
        return "Gün " + day + (failed ? " BAŞARISIZ" : "") + " gelir=" + revenue + " bahşiş=" + tips
                + " acil=" + emergencySpend + " kira=" + rent + (rentUnpaid() > 0 ? "(eksik " + rentUnpaid() + ")" : "") + " teslim=" + delivered + " kayıp=" + lost
                + (wastedPortions > 0 ? " israf=" + wastedPortions : "") + " yıldız=" + startStars + "->" + endStars + " para=" + moneyAfter;
    }
}
