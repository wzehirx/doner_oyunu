package com.doner.oyunu.engine;

/** Malzeme. Fiyat TL / birim (oyun birimi). */
public final class Ingredient {
    public static final int EMERGENCY_BATCH = 3;

    /** Malzeme kategorisi. SOS ve SALATA malzemeleri topping (malzeme seçimi) olarak seçilebilir. */
    public enum Category { ET, EKMEK, SOS, SALATA, ICECEK, TATLI, DIGER }

    public final String id;
    public final String name;
    public final String unit;
    public final int price;
    public final Category category;

    public Ingredient(String id, String name, String unit, int price) {
        this(id, name, unit, price, Category.DIGER);
    }

    public Ingredient(String id, String name, String unit, int price, Category category) {
        this.id = id;
        this.name = name;
        this.unit = unit;
        this.price = price;
        this.category = category;
    }

    /** Acil alım birim fiyatı: +%50. */
    public int emergencyPrice() {
        return (int) Math.ceil(price * 1.5);
    }

    /** Sos veya salata malzemesi mi (topping olarak seçilebilir, hazır tepsiye konabilir). */
    public boolean isTopping() { return category == Category.SOS || category == Category.SALATA; }

    @Override
    public String toString() {
        return name;
    }
}
