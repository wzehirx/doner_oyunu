package com.doner.oyunu.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bir ürünün tek hazırlık adımı. */
public final class Step {
    public enum Kind {
        NORMAL,
        /** Malzeme (topping) seçimi adımı: ürünün diğer tüm adımları bitince açılır; stok seçilenlerden tüketilir. */
        TOPPING
    }

    public final String name;
    public final Station station;
    public final float baseSeconds;
    /** malzeme id -> miktar (adım başlarken tüketilir; TOPPING adımında boş, seçilen malzemeler tüketilir). */
    public final Map<String, Integer> needs;
    public final Kind kind;
    /** true: kalemin önceki TÜM adımları bitmeden açılmaz (künefe gibi sıralı ürünler). TOPPING her zaman sıralıdır. */
    public final boolean sequential;
    /**
     * v2.0 kaynak modeli: PASİF iş (makine/ocak kendi pişirir: ekmek/lavaş ısıt, ocakta pişir/mühürle, patates kızart).
     * Oyuncunun elleri yalnızca kısa "koyma" süresince meşguldür; iş istasyonun KENDİ kapasitesini (göz/yer) kullanır.
     * false: AKTİF iş (dilimle, malzeme ekle, paketle, doldur...): iş boyunca eller meşgul.
     */
    public final boolean passive;

    public Step(String name, Station station, float baseSeconds, Map<String, Integer> needs) {
        this(name, station, baseSeconds, needs, Kind.NORMAL, false, false);
    }

    public Step(String name, Station station, float baseSeconds, Map<String, Integer> needs, Kind kind, boolean sequential) {
        this(name, station, baseSeconds, needs, kind, sequential, false);
    }

    public Step(String name, Station station, float baseSeconds, Map<String, Integer> needs, Kind kind, boolean sequential, boolean passive) {
        this.name = name;
        this.station = station;
        this.baseSeconds = baseSeconds;
        this.needs = Collections.unmodifiableMap(new LinkedHashMap<>(needs));
        this.kind = kind;
        this.sequential = sequential || kind == Kind.TOPPING;
        this.passive = passive && kind != Kind.TOPPING;
    }

    public boolean isTopping() { return kind == Kind.TOPPING; }

    /** Pasif iş mi (v2.0): makine kendi işler, eller yalnızca koyarken meşgul; istasyon kapasitesi kullanılır. */
    public boolean isPassive() { return passive; }

    /** Hazır (dilimlenmiş) döner ile hızlanabilen adımsa döner malzemesinin id'si, değilse null. */
    public String meatId() {
        if (station != Station.DONER) return null;
        for (String id : needs.keySet()) {
            Ingredient i = Catalog.ingredient(id);
            if (i != null && i.category == Ingredient.Category.ET) return id;
        }
        return null;
    }

    /** Döner boyu SEÇİLEN (dilimleme) adım mı: DONER istasyonunda et malzemesi tüketen adım. */
    public boolean isSized() { return meatId() != null; }

    /** Ekmek/lavaş ısıtma gibi boyla (yarım/tam/duble) süresi ve ekmek tüketimi ölçeklenen adım (sipariş edilen boy). */
    public boolean isBreadStep() {
        if (station != Station.EKMEK) return false;
        for (String id : needs.keySet()) {
            Ingredient i = Catalog.ingredient(id);
            if (i != null && i.category == Ingredient.Category.EKMEK) return true;
        }
        return false;
    }

    /** "Patates kızart": OCAK istasyonunda patates tüketen adım. */
    public boolean isFry() { return station == Station.IZGARA && needs.containsKey(Catalog.PATATES); }

    /** "Ayranı doldur" gibi hazır içecek ile hızlanabilen adım mı. */
    public boolean isDrinkFill() { return station == Station.ICECEK && needs.containsKey(Catalog.AYRAN); }
}
