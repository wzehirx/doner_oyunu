package com.doner.oyunu.engine;

/** Gün sonu özeti. */
public final class DaySummary {
    public int day;
    public boolean failed;
    public int revenue;
    public int tips;
    public int emergencySpend;
    public int rent;
    public int delivered;
    public int lost;
    public int wrongTaps;
    public double startStars;
    public double endStars;
    public int moneyAfter;
    /** Sonraki gün açılan yeni ürünler (adlar). */
    public String newProductsNext = "";

    public int profit() { return revenue + tips - emergencySpend - rent; }

    @Override
    public String toString() {
        return "Gün " + day + (failed ? " BAŞARISIZ" : "") + " gelir=" + revenue + " bahşiş=" + tips
                + " acil=" + emergencySpend + " kira=" + rent + " teslim=" + delivered + " kayıp=" + lost
                + " yıldız=" + startStars + "->" + endStars + " para=" + moneyAfter;
    }
}
