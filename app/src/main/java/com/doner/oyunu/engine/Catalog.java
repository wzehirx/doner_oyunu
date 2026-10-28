package com.doner.oyunu.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.doner.oyunu.engine.Station.*;
import static com.doner.oyunu.engine.Upgrade.Category.*;
import static com.doner.oyunu.engine.Upgrade.EffectType.*;

/** Statik katalog: malzemeler, ürünler, yükseltmeler, müşteri tipleri. */
public final class Catalog {
    private static final Map<String, Ingredient> INGREDIENTS = new LinkedHashMap<>();
    private static final Map<String, Product> PRODUCTS = new LinkedHashMap<>();
    private static final Map<String, Upgrade> UPGRADES = new LinkedHashMap<>();
    private static final List<CustomerType> TYPES = new ArrayList<>();
    public static final String[] NAMES = {
            "Ahmet", "Ayşe", "Mehmet", "Fatma", "Ali", "Zeynep", "Mustafa", "Elif", "Hasan", "Emine",
            "Can", "Deniz", "Burak", "Selin", "Kemal", "Leyla", "Murat", "Hülya", "Emre", "Gül",
            "Osman", "Merve", "Yusuf", "Büşra", "İbrahim", "Esra", "Kerem", "Sevgi"};

    private Catalog() {}

    private static void ing(String id, String name, String unit, int price) {
        INGREDIENTS.put(id, new Ingredient(id, name, unit, price));
    }

    private static Step st(String name, Station station, double sec, Object... needs) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < needs.length; i += 2) m.put((String) needs[i], (Integer) needs[i + 1]);
        return new Step(name, station, (float) sec, m);
    }

    private static void prod(String id, String name, int openDay, int price, Step... steps) {
        List<Step> l = new ArrayList<>();
        Collections.addAll(l, steps);
        PRODUCTS.put(id, new Product(id, name, openDay, price, l));
    }

    private static void up(String id, String name, Upgrade.Category c, String desc, int[] prices,
                           int[] days, Upgrade.Effect... e) {
        UPGRADES.put(id, new Upgrade(id, name, c, desc, prices, days, e));
    }

    private static Station[] s(Station... a) { return a; }

    static {
        ing("et_doner", "Et döner", "porsiyon", 68);
        ing("tavuk_doner", "Tavuk döner", "porsiyon", 40);
        ing("ekmek", "Döner ekmeği", "adet", 6);
        ing("lavas", "Dürüm lavaşı", "adet", 5);
        ing("pirinc", "Pilavlık pirinç", "porsiyon", 6);
        ing("tereyagi", "Tereyağı", "porsiyon", 11);
        ing("domates", "Domates", "porsiyon", 2);
        ing("sogan", "Soğan", "porsiyon", 1);
        ing("marul", "Marul", "adet", 4);
        ing("yogurt", "Süzme yoğurt", "porsiyon", 4);
        ing("kadayif", "Tel kadayıf", "porsiyon", 17);
        ing("peynir", "Künefe peyniri", "porsiyon", 38);
        ing("lahmacun_harci", "Lahmacun harcı", "porsiyon", 27);
        ing("ayran", "Ayran şişesi", "adet", 14);
        ing("kutu_icecek", "Kutu içecek", "adet", 22);

        prod("ekmek_arasi", "Ekmek arası döner", 1, 240,
                st("Ekmeği ısıt", EKMEK, 3.5, "ekmek", 1),
                st("Döneri dilimle", DONER, 3.5, "et_doner", 1),
                st("Yoğurt, domates ve soğan ekle", SOS, 3.0, "yogurt", 1, "domates", 1, "sogan", 1),
                st("Kapat ve kağıda sar", PAKET, 2.0));
        prod("ayran", "Ayran", 2, 45,
                st("Dolaptan şişeyi al", ICECEK, 1.2, "ayran", 1),
                st("Bardağa koy", ICECEK, 1.2),
                st("Teslime hazırla", PAKET, 1.0));
        prod("pilav_ustu", "Pilav üstü döner", 3, 320,
                st("Pilavı tabağa al", IZGARA, 3.0, "pirinc", 1),
                st("Tereyağını gezdir", IZGARA, 2.0, "tereyagi", 1),
                st("Döneri dilimle", DONER, 4.5, "et_doner", 2),
                st("Pilavın üstüne yerleştir", PAKET, 3.5));
        prod("tavuk_durum", "Tavuk dürüm", 4, 210,
                st("Lavaşı ısıt", EKMEK, 3.0, "lavas", 1),
                st("Tavuğu dilimle", DONER, 3.5, "tavuk_doner", 1),
                st("Yoğurt, marul ve domates ekle", SOS, 3.0, "yogurt", 1, "marul", 1, "domates", 1),
                st("Dürümü sar", PAKET, 2.5));
        prod("kutu_icecek", "Kutu içecek", 5, 55,
                st("Dolaptan kutuyu al", ICECEK, 1.0, "kutu_icecek", 1),
                st("Kutuyu tepsiye koy", ICECEK, 1.0),
                st("Teslime hazırla", PAKET, 1.0));
        prod("hatay_durum", "Hatay usulü dürüm", 6, 260,
                st("Lavaşı ısıt", EKMEK, 3.0, "lavas", 1),
                st("Harç ve döner ekle", DONER, 4.0, "lahmacun_harci", 1, "et_doner", 1),
                st("Soğan, domates ve marul ekle", SOS, 3.5, "sogan", 1, "domates", 1, "marul", 1),
                st("Izgarada mühürle", IZGARA, 3.5),
                st("Sıkı sar", PAKET, 2.0));
        prod("tavuk_ekmek", "Ekmek arası tavuk döner", 7, 190,
                st("Ekmeği ısıt", EKMEK, 3.0, "ekmek", 1),
                st("Tavuğu dilimle", DONER, 3.0, "tavuk_doner", 1),
                st("Yoğurt, marul ve domates ekle", SOS, 2.5, "yogurt", 1, "marul", 1, "domates", 1),
                st("Kapat ve sar", PAKET, 1.5));
        prod("zurna_durum", "Zurna dürüm", 8, 340,
                st("Lavaşı ısıt ve tereyağı sür", EKMEK, 3.5, "lavas", 1, "tereyagi", 1),
                st("Döneri dilimle", DONER, 5.0, "et_doner", 1),
                st("Yoğurt ve domates ekle", SOS, 3.5, "yogurt", 1, "domates", 1),
                st("Uzun sar", PAKET, 3.0));
        prod("iskender", "İskender", 10, 420,
                st("Ekmeği küp doğra", EKMEK, 3.5, "ekmek", 1),
                st("Döneri dilimle ve ser", DONER, 6.0, "et_doner", 2),
                st("Tereyağı ve domates sosunu gezdir", IZGARA, 5.0, "tereyagi", 2, "domates", 2),
                st("Yoğurdu ekle", SOS, 3.0, "yogurt", 2),
                st("Servise hazırla", PAKET, 2.5));
        prod("porsiyon_doner", "Porsiyon döner", 12, 380,
                st("Ekmeği ısıt", EKMEK, 3.0, "ekmek", 1),
                st("Döneri dilimle", DONER, 5.0, "et_doner", 2),
                st("Domates ve soğanı diz", SOS, 3.0, "domates", 2, "sogan", 1),
                st("Tabağı hazırla", PAKET, 2.0));
        prod("lahmacun", "Lahmacun", 14, 120,
                st("Harcı lavaşa yay", SOS, 3.0, "lavas", 1, "lahmacun_harci", 1),
                st("Fırına ver", IZGARA, 6.0),
                st("Domates ve soğan ekle", SOS, 2.5, "domates", 1, "sogan", 1),
                st("Sar", PAKET, 2.5));
        prod("kunefe", "Künefe", 16, 240,
                st("Kadayıfı tereyağıyla yoğur", TATLI, 3.5, "kadayif", 1, "tereyagi", 2),
                st("Peyniri ser", TATLI, 3.0, "peynir", 1),
                st("Fırında pişir", IZGARA, 8.0),
                st("Çevir ve servis et", TATLI, 3.0));

        // Yükseltmeler (fiyat/etki araştırma notundan; 'cirak' motor tasarımı).
        up("cirak", "Çırak", MUTFAK, "Aynı anda bir işlem daha yapar.",
                new int[]{3000, 9000}, new int[]{3, 8},
                new Upgrade.Effect(SLOTS, null, 1, 2));
        up("doner_makinesi", "Döner makinesi", MUTFAK, "Döner dilimleme adımlarını hızlandırır.",
                new int[]{4000, 15000, 48000}, new int[]{4, 10, 18},
                new Upgrade.Effect(STATION_SPEED, s(DONER), 0.85, 0.70, 0.55));
        up("ekmek_isitici", "Ekmek ısıtıcı", MUTFAK, "Ekmek ve lavaş ısıtma adımlarını hızlandırır.",
                new int[]{2500, 8000, 22000}, new int[]{3, 9, 17},
                new Upgrade.Effect(STATION_SPEED, s(EKMEK), 0.70, 0.50, 0.40));
        up("oto_dilimleyici", "Otomatik dilimleyici", MUTFAK, "Döner makinesinde ek hız kazandırır.",
                new int[]{3500, 12000, 34000}, new int[]{6, 12, 21},
                new Upgrade.Effect(STATION_SPEED, s(DONER), 0.75, 0.55, 0.45));
        up("buzdolabi", "Buzdolabı", MUTFAK, "Her malzemeden daha çok stok saklar.",
                new int[]{2000, 7500, 24000}, new int[]{2, 8, 16},
                new Upgrade.Effect(STOCK_CAP, null, 50, 100, 220));
        up("masa", "Masa takımı", RESTORAN, "Aynı anda daha çok müşteri bekleyebilir.",
                new int[]{3000, 9000, 24000}, new int[]{11, 22, 28},
                new Upgrade.Effect(QUEUE, null, 1, 2, 3),
                new Upgrade.Effect(TIP, null, 0, 0, 0.05));
        up("tabela", "Tabela", RESTORAN, "Daha çok müşteri çeker.",
                new int[]{1000, 4000, 12000}, new int[]{8, 15, 24},
                new Upgrade.Effect(ARRIVAL, null, 0.10, 0.20, 0.35));
        up("klima", "Klima", RESTORAN, "Müşteriler daha sabırlı bekler.",
                new int[]{2500, 7000, 18000}, new int[]{13, 21, 26},
                new Upgrade.Effect(PATIENCE, null, 0.10, 0.20, 0.30));
        up("muzik", "Müzik sistemi", RESTORAN, "Biraz daha müşteri, yıldız kazancı artar.",
                new int[]{1800, 5500, 15000}, new int[]{4, 14, 23},
                new Upgrade.Effect(ARRIVAL, null, 0.05, 0.10, 0.15),
                new Upgrade.Effect(STAR_GAIN, null, 0.05, 0.10, 0.15));
        up("dekor", "Dekor", RESTORAN, "Turistler çoğalır, bahşişler artar.",
                new int[]{2200, 6500, 20000}, new int[]{7, 16, 25},
                new Upgrade.Effect(TOURIST, null, 0.10, 0.20, 0.35),
                new Upgrade.Effect(TIP, null, 0.05, 0.10, 0.15),
                new Upgrade.Effect(ARRIVAL, null, 0.05, 0.10, 0.15));

        // Müşteri tipleri (sabır sn). Ağırlıklar araştırma oranlarından.
        TYPES.add(new CustomerType("acele", "Acele eden iş insanı", 1, 20, 25f, 0.0f, 0.0f,
                "Acelesi var.", "ekmek_arasi", "tavuk_ekmek"));
        TYPES.add(new CustomerType("aile", "Sabırlı aile", 1, 15, 60f, 1.0f, 0.5f,
                "Sabırlı bir aile, birden fazla ürün isteyebilir.", "pilav_ustu", "porsiyon_doner", "ayran"));
        TYPES.add(new CustomerType("ogrenci", "Öğrenci", 1, 15, 40f, 0.3f, 0.1f,
                "Öğrenci, bahşişi az.", "tavuk_ekmek", "kutu_icecek"));
        TYPES.add(new CustomerType("taksi", "Taksi şoförü", 2, 7, 30f, 0.8f, 0.0f,
                "Taksi şoförü, çabuk olmalı.", "tavuk_durum", "kutu_icecek"));
        TYPES.add(new CustomerType("yasli", "Yaşlı müşteri", 3, 10, 70f, 1.0f, 0.0f,
                "Yavaş ama sabırlı biri.", "ekmek_arasi", "ayran"));
        TYPES.add(new CustomerType("turist", "Turist", 5, 10, 45f, 1.8f, 0.3f,
                "Turist, bol bahşiş verebilir.", "lahmacun", "iskender", "kunefe"));
        TYPES.add(new CustomerType("esnaf", "Komşu esnaf", 7, 5, 50f, 1.0f, 0.1f,
                "Komşu esnaf, her gün gelir.", "ekmek_arasi", "ayran"));
        TYPES.add(new CustomerType("sikayetci", "Şikayetçi müşteri", 9, 5, 20f, 0.5f, 0.0f,
                "Şikayetçi, çok bekletme.", new String[0]));
        TYPES.add(new CustomerType("gece", "Gece vardiyası işçisi", 13, 8, 35f, 1.0f, 0.2f,
                "Gece vardiyasından geliyor.", "porsiyon_doner", "hatay_durum"));
        TYPES.add(new CustomerType("influencer", "Influencer", 19, 5, 30f, 1.5f, 0.2f,
                "Influencer, servis hızına dikkat eder.", "iskender", "kunefe"));
    }

    public static List<Ingredient> ingredients() {
        return Collections.unmodifiableList(new ArrayList<>(INGREDIENTS.values()));
    }

    public static Ingredient ingredient(String id) { return INGREDIENTS.get(id); }

    public static List<Product> products() {
        return Collections.unmodifiableList(new ArrayList<>(PRODUCTS.values()));
    }

    public static Product product(String id) { return PRODUCTS.get(id); }

    public static List<Upgrade> upgrades() {
        return Collections.unmodifiableList(new ArrayList<>(UPGRADES.values()));
    }

    public static Upgrade upgrade(String id) { return UPGRADES.get(id); }

    public static List<CustomerType> customerTypes() { return Collections.unmodifiableList(TYPES); }

    /** Gün N'de açık tüm ürünler (birikimli). */
    public static List<Product> productsForDay(int day) {
        List<Product> l = new ArrayList<>();
        for (Product p : PRODUCTS.values()) if (p.openDay <= day) l.add(p);
        return l;
    }

    /** Tam o gün açılan ürünler. */
    public static List<Product> newProductsOnDay(int day) {
        List<Product> l = new ArrayList<>();
        for (Product p : PRODUCTS.values()) if (p.openDay == day) l.add(p);
        return l;
    }

    public static List<CustomerType> typesForDay(int day) {
        List<CustomerType> l = new ArrayList<>();
        for (CustomerType t : TYPES) if (t.minDay <= day) l.add(t);
        return l;
    }
}
