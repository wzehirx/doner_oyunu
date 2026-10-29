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
    /** Malzemenin açıldığı ilk gün (sos/salata malzemeleri gün gün açılır; diğerleri 1). */
    public final int unlockDay;

    public Ingredient(String id, String name, String unit, int price) {
        this(id, name, unit, price, Category.DIGER);
    }

    public Ingredient(String id, String name, String unit, int price, Category category) {
        this(id, name, unit, price, category, 1);
    }

    public Ingredient(String id, String name, String unit, int price, Category category, int unlockDay) {
        this.id = id;
        this.name = name;
        this.unit = unit;
        this.price = price;
        this.category = category;
        this.unlockDay = unlockDay;
    }

    /** Gün N'de açık mı (market satar, müşteri isteği/varsayılan küme/seçenek kullanır). */
    public boolean isOpen(int day) { return day >= unlockDay; }

    /** Boya göre ölçeklenen malzeme mi (et ve ekmek/lavaş: yarım/tam/duble). */
    public boolean scalesWithSize() { return category == Category.ET || category == Category.EKMEK; }

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
