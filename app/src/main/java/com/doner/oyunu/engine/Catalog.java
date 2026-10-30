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

    public static final String AYRAN = "ayran";
    public static final String PATATES = "patates";
    /** Patatesin açıldığı gün (market satar, müşteri "patatesli" ister). */
    public static final int FRIES_OPEN_DAY = 3;
    /** "Patates kızart": Ocak istasyonu, 4 sn, 1 porsiyon patates; hazır patates varsa 0,8 sn. */
    public static final Step FRY_STEP = new Step("Patates kızart", Station.IZGARA, 4.0f,
            java.util.Collections.singletonMap(PATATES, 1), Step.Kind.NORMAL, false, true);

    private static void ing(String id, String name, String unit, int price, Ingredient.Category cat) {
        INGREDIENTS.put(id, new Ingredient(id, name, unit, price, cat));
    }

    /** Gün gün açılan sos/salata malzemesi (design_v06 §3). */
    private static void ing(String id, String name, String unit, int price, Ingredient.Category cat, int unlockDay) {
        INGREDIENTS.put(id, new Ingredient(id, name, unit, price, cat, unlockDay));
    }

    private static Step st(String name, Station station, double sec, Object... needs) {
        return new Step(name, station, (float) sec, needMap(needs));
    }

    /** PASİF adım (v2.0): makine/ocak kendi pişirir; eller yalnızca koyarken meşgul, istasyon kapasitesi kullanılır. */
    private static Step pas(String name, Station station, double sec, Object... needs) {
        return new Step(name, station, (float) sec, needMap(needs), Step.Kind.NORMAL, false, true);
    }

    /** Sıralı PASİF adım (künefe/lahmacun "Ocakta pişir"). */
    private static Step seqPas(String name, Station station, double sec, Object... needs) {
        return new Step(name, station, (float) sec, needMap(needs), Step.Kind.NORMAL, true, true);
    }

    /** Sıralı adım: kalemin önceki tüm adımları bitmeden açılmaz. */
    private static Step seq(String name, Station station, double sec, Object... needs) {
        return new Step(name, station, (float) sec, needMap(needs), Step.Kind.NORMAL, true);
    }

    /** Malzeme seçimi (topping) adımı; SOS istasyonu, kalemin diğer adımlarından sonra. */
    private static Step top(String name, double sec) {
        return new Step(name, SOS, (float) sec, needMap(), Step.Kind.TOPPING, true);
    }

    private static Map<String, Integer> needMap(Object... needs) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < needs.length; i += 2) m.put((String) needs[i], (Integer) needs[i + 1]);
        return m;
    }

    private static List<String> l(String... a) {
        List<String> r = new ArrayList<>();
        Collections.addAll(r, a);
        return r;
    }

    private static void prod(String id, String name, int openDay, int price, Step... steps) {
        prod(id, name, openDay, price, l(), l(), steps);
    }

    private static void prod(String id, String name, int openDay, int price, List<String> defaults, List<String> pool, Step... steps) {
        List<Step> st = new ArrayList<>();
        Collections.addAll(st, steps);
        PRODUCTS.put(id, new Product(id, name, openDay, price, st, defaults, pool));
    }

    private static void up(String id, String name, Upgrade.Category c, String desc, int[] prices,
                           int[] days, Upgrade.Effect... e) {
        UPGRADES.put(id, new Upgrade(id, name, c, desc, prices, days, e));
    }

    private static Station[] s(Station... a) { return a; }

    static {
        Ingredient.Category ET = Ingredient.Category.ET, EKM = Ingredient.Category.EKMEK, SOSC = Ingredient.Category.SOS,
                SAL = Ingredient.Category.SALATA, ICE = Ingredient.Category.ICECEK, TAT = Ingredient.Category.TATLI,
                DIG = Ingredient.Category.DIGER;
        ing("et_doner", "Et döner", "porsiyon", 68, ET);
        ing("tavuk_doner", "Tavuk döner", "porsiyon", 40, ET);
        ing("ekmek", "Döner ekmeği", "adet", 6, EKM);
        ing("lavas", "Dürüm lavaşı", "adet", 5, EKM);
        ing("pirinc", "Pilavlık pirinç", "porsiyon", 6, DIG);
        ing("tereyagi", "Tereyağı", "porsiyon", 11, DIG);
        // Sos/salata malzemeleri gün gün açılır (design_v06 §3 takvimi):
        // 1 domates, marul, soğan; 3 ketçap, mayonez; 5 acı sos; 7 turşu; 9 nar ekşisi; 11 sumak, biber;
        // 13 sarımsaklı yoğurt sos; 15 közlenmiş biber/domates. Ek: süzme yoğurt 8 (zurna ile), limon 14 (lahmacun ile).
        ing("domates", "Domates", "porsiyon", 2, SAL, 1);
        ing("sogan", "Soğan", "porsiyon", 1, SAL, 1);
        ing("marul", "Marul", "adet", 4, SAL, 1);
        ing("yogurt", "Süzme yoğurt", "porsiyon", 4, SOSC, 8);
        ing("kadayif", "Tel kadayıf", "porsiyon", 17, TAT);
        ing("peynir", "Künefe peyniri", "porsiyon", 38, TAT);
        ing("lahmacun_harci", "Lahmacun harcı", "porsiyon", 27, DIG);
        ing(AYRAN, "Ayran şişesi", "adet", 14, ICE);
        ing("kutu_icecek", "Kutu içecek", "adet", 22, ICE);
        ing("ketcap", "Ketçap", "porsiyon", 2, SOSC, 3);
        ing("mayonez", "Mayonez", "porsiyon", 3, SOSC, 3);
        ing("aci_sos", "Acı sos", "porsiyon", 2, SOSC, 5);
        ing("tursu", "Turşu", "porsiyon", 3, SAL, 7);
        ing("nar_eksisi", "Nar ekşisi", "porsiyon", 3, SOSC, 9);
        ing("biber", "Yeşil biber", "porsiyon", 2, SAL, 11);
        ing("sumak", "Sumak", "porsiyon", 2, SAL, 11);
        ing("sarimsakli_yogurt", "Sarımsaklı yoğurt sos", "porsiyon", 4, SOSC, 13);
        ing("limon", "Limon", "porsiyon", 2, SAL, 14);
        ing("kozlenmis", "Közlenmiş biber ve domates", "porsiyon", 4, SAL, 15);
        ing(PATATES, "Patates", "porsiyon", 4, DIG, FRIES_OPEN_DAY);

        prod("ekmek_arasi", "Ekmek arası döner", 1, 240,
                l("domates", "marul", "sogan", "ketcap"),
                l("domates", "marul", "sogan", "ketcap", "mayonez", "aci_sos", "tursu", "sumak"),
                pas("Ekmeği ısıt", EKMEK, 3.5, "ekmek", 1),
                st("Döneri dilimle", DONER, 3.5, "et_doner", 1),
                top("Malzemeleri ekle", 2.0));
        prod(AYRAN, "Ayran", 2, 45,
                st("Ayranı doldur", ICECEK, 1.8, AYRAN, 1));
        prod("pilav_ustu", "Pilav üstü döner", 3, 320,
                l("domates", "sogan", "tursu"),
                l("domates", "sogan", "ketcap", "mayonez", "aci_sos", "tursu", "biber", "kozlenmis"),
                st("Pilavı tabağa al", IZGARA, 3.0, "pirinc", 1),
                st("Tereyağını gezdir", IZGARA, 2.0, "tereyagi", 1),
                st("Döneri dilimle", DONER, 4.5, "et_doner", 2),
                top("Garnitürü ekle", 2.0));
        prod("tavuk_durum", "Tavuk dürüm", 4, 210,
                l("marul", "domates", "mayonez"),
                l("marul", "domates", "sogan", "mayonez", "ketcap", "aci_sos", "tursu", "sarimsakli_yogurt"),
                pas("Lavaşı ısıt", EKMEK, 3.0, "lavas", 1),
                st("Tavuğu dilimle", DONER, 3.5, "tavuk_doner", 1),
                top("Malzemeleri ekle", 2.0));
        prod("kutu_icecek", "Kutu içecek", 5, 55,
                st("Dolaptan al", ICECEK, 1.0, "kutu_icecek", 1));
        prod("hatay_durum", "Hatay usulü dürüm", 6, 260,
                l("sogan", "domates", "tursu", "aci_sos", "nar_eksisi"),
                l("sogan", "domates", "tursu", "aci_sos", "nar_eksisi", "marul", "biber", "sumak"),
                pas("Lavaşı ısıt", EKMEK, 3.0, "lavas", 1),
                st("Harç ve döner ekle", DONER, 4.0, "lahmacun_harci", 1, "et_doner", 1),
                pas("Ocakta mühürle", IZGARA, 3.5),
                top("Malzemeleri ekle", 2.0));
        prod("tavuk_ekmek", "Ekmek arası tavuk döner", 7, 190,
                l("marul", "domates", "mayonez"),
                l("marul", "domates", "sogan", "mayonez", "ketcap", "aci_sos", "tursu", "sarimsakli_yogurt"),
                pas("Ekmeği ısıt", EKMEK, 3.0, "ekmek", 1),
                st("Tavuğu dilimle", DONER, 3.0, "tavuk_doner", 1),
                top("Malzemeleri ekle", 1.8));
        prod("zurna_durum", "Zurna dürüm", 8, 340,
                l("yogurt", "domates", "sogan"),
                l("yogurt", "domates", "sogan", "marul", "aci_sos", "tursu", "biber", "sarimsakli_yogurt"),
                pas("Lavaşı ısıt ve tereyağı sür", EKMEK, 3.5, "lavas", 1, "tereyagi", 1),
                st("Döneri dilimle", DONER, 5.0, "et_doner", 1),
                top("Malzemeleri ekle", 2.0));
        prod("iskender", "İskender", 10, 420,
                l("yogurt", "domates", "biber"),
                l("yogurt", "domates", "biber", "aci_sos", "tursu", "sarimsakli_yogurt", "kozlenmis", "sumak"),
                st("Ekmeği küp doğra", EKMEK, 3.5, "ekmek", 1),
                st("Döneri dilimle ve ser", DONER, 6.0, "et_doner", 2),
                st("Tereyağı ve domates sosunu gezdir", IZGARA, 5.0, "tereyagi", 2, "domates", 2),
                top("Yoğurt ve garnitürü ekle", 2.0));
        prod("porsiyon_doner", "Porsiyon döner", 12, 380,
                l("domates", "sogan", "biber"),
                l("domates", "sogan", "marul", "biber", "tursu", "aci_sos", "ketcap", "kozlenmis"),
                pas("Ekmeği ısıt", EKMEK, 3.0, "ekmek", 1),
                st("Döneri dilimle", DONER, 5.0, "et_doner", 2),
                top("Garnitürü diz", 2.0));
        prod("lahmacun", "Lahmacun", 14, 120,
                l("sogan", "domates", "limon"),
                l("sogan", "domates", "limon", "marul", "tursu", "aci_sos", "sumak"),
                st("Harcı lavaşa yay", SOS, 3.0, "lavas", 1, "lahmacun_harci", 1),
                seqPas("Ocakta pişir", IZGARA, 6.0),
                top("Malzemeleri ekle", 1.8));
        prod("kunefe", "Künefe", 16, 240,
                st("Kadayıfı tereyağıyla yoğur", TATLI, 3.5, "kadayif", 1, "tereyagi", 2),
                seq("Peyniri ser", TATLI, 3.0, "peynir", 1),
                seqPas("Ocakta pişir", IZGARA, 8.0),
                seq("Çevir ve servis et", TATLI, 3.0));

        // Patates dürümlere ve ekmek arası ürünlere girer: müşteri "patatesli" isterse kalemde "Patates kızart" adımı olur.
        for (String pid : new String[]{"ekmek_arasi", "tavuk_ekmek", "tavuk_durum", "hatay_durum", "zurna_durum"}) PRODUCTS.get(pid).setFriesOk();

        // Yükseltmeler (fiyat/etki araştırma notundan; 'cirak' motor tasarımı).
        up("cirak", "Çırak", MUTFAK, "Ek bir çift el: kendi başına çalışır. Seviye 1 paketler, içecek doldurur, boşta hazırlık yapar. Seviye 2 ekmek ve lavaş ısıtır. Seviye 3 döner dilimler ve ocağı kullanır. Malzemeyi hep sen koyarsın.",
                new int[]{3000, 9000, 24000}, new int[]{3, 8, 16},
                new Upgrade.Effect(APPRENTICE, null, 1, 2, 3));
        up("doner_makinesi", "Döner makinesi", MUTFAK, "Döner dilimleme adımlarını hızlandırır.",
                new int[]{4000, 15000, 48000}, new int[]{4, 10, 18},
                new Upgrade.Effect(STATION_SPEED, s(DONER), 0.85, 0.70, 0.55));
        up("ekmek_isitici", "Ekmek ısıtıcı", MUTFAK, "Daha geniş ve hızlı ekmek ısıtıcı: aynı anda 3, sonra 4 ekmek ya da lavaş.",
                new int[]{2500, 8000, 22000}, new int[]{3, 9, 17},
                new Upgrade.Effect(STATION_CAPACITY, s(EKMEK), 1, 2, 2),
                new Upgrade.Effect(STATION_SPEED, s(EKMEK), 0.85, 0.70, 0.55));
        up("ocak", "Geniş ocak", MUTFAK, "Ocağa göz eklenir: aynı anda 3, sonra 4 iş (patates, pişirme); ocak işleri hızlanır.",
                new int[]{2500, 8000, 20000}, new int[]{3, 9, 17},
                new Upgrade.Effect(STATION_CAPACITY, s(IZGARA), 1, 2, 2),
                new Upgrade.Effect(STATION_SPEED, s(IZGARA), 0.90, 0.80, 0.65));
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
                "Acelesi var.", "ekmek_arasi", "tavuk_ekmek").sizes(0.15f, 0.1f));
        TYPES.add(new CustomerType("aile", "Sabırlı aile", 1, 15, 60f, 1.0f, 0.5f,
                "Sabırlı bir aile, birden fazla ürün isteyebilir.", "pilav_ustu", "porsiyon_doner", "ayran").sizes(0.15f, 0.3f));
        TYPES.add(new CustomerType("ogrenci", "Öğrenci", 1, 15, 40f, 0.3f, 0.1f,
                "Öğrenci, bahşişi az.", "tavuk_ekmek", "kutu_icecek").sizes(0.45f, 0.05f));
        TYPES.add(new CustomerType("taksi", "Taksi şoförü", 2, 7, 30f, 0.8f, 0.0f,
                "Taksi şoförü, çabuk olmalı.", "tavuk_durum", "kutu_icecek").sizes(0.1f, 0.15f));
        TYPES.add(new CustomerType("yasli", "Yaşlı müşteri", 3, 10, 70f, 1.0f, 0.0f,
                "Yavaş ama sabırlı biri.", "ekmek_arasi", "ayran").sizes(0.5f, 0.0f));
        TYPES.add(new CustomerType("turist", "Turist", 5, 10, 45f, 1.8f, 0.3f,
                "Turist, bol bahşiş verebilir.", "lahmacun", "iskender", "kunefe").sizes(0.1f, 0.25f));
        TYPES.add(new CustomerType("esnaf", "Komşu esnaf", 7, 5, 50f, 1.0f, 0.1f,
                "Komşu esnaf, her gün gelir.", "ekmek_arasi", "ayran").sizes(0.3f, 0.1f));
        TYPES.add(new CustomerType("sikayetci", "Şikayetçi müşteri", 9, 5, 20f, 0.5f, 0.0f,
                "Şikayetçi, çok bekletme.", new String[0]).sizes(0.2f, 0.1f));
        TYPES.add(new CustomerType("gece", "Gece vardiyası işçisi", 13, 8, 35f, 1.0f, 0.2f,
                "Gece vardiyasından geliyor.", "porsiyon_doner", "hatay_durum").sizes(0.1f, 0.35f));
        TYPES.add(new CustomerType("influencer", "Influencer", 19, 5, 30f, 1.5f, 0.2f,
                "Influencer, servis hızına dikkat eder.", "iskender", "kunefe").sizes(0.05f, 0.4f));
    }

    public static List<Ingredient> ingredients() {
        return Collections.unmodifiableList(new ArrayList<>(INGREDIENTS.values()));
    }

    public static Ingredient ingredient(String id) { return INGREDIENTS.get(id); }

    /** Gün N'de açık tüm malzemeler (birikimli; market bunları satar). */
    public static List<Ingredient> ingredientsForDay(int day) {
        List<Ingredient> l = new ArrayList<>();
        for (Ingredient i : INGREDIENTS.values()) if (i.unlockDay <= day) l.add(i);
        return l;
    }

    /**
     * Tam o gün açılan yeni sos/salata malzemeleri (brifing duyurusu için). 1. günde açılış malzemeleri
     * (domates, marul, soğan) da döner; temel malzemeler (et, ekmek...) dahil değildir.
     */
    public static List<Ingredient> newIngredientsOnDay(int day) {
        List<Ingredient> l = new ArrayList<>();
        for (Ingredient i : INGREDIENTS.values()) if (i.isTopping() && i.unlockDay == day) l.add(i);
        return l;
    }

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
