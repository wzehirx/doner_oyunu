package com.doner.oyunu.engine;

/** Malzeme. Fiyat TL / birim (oyun birimi). */
public final class Ingredient {
    public static final int EMERGENCY_BATCH = 3;

    public final String id;
    public final String name;
    public final String unit;
    public final int price;

    public Ingredient(String id, String name, String unit, int price) {
        this.id = id;
        this.name = name;
        this.unit = unit;
        this.price = price;
    }

    /** Acil alım birim fiyatı: +%50. */
    public int emergencyPrice() {
        return (int) Math.ceil(price * 1.5);
    }

    @Override
    public String toString() {
        return name;
    }
}
