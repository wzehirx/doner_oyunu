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

    private static Upgrade up(String id, String name, Upgrade.Category c, String desc, int[] prices,
                           int[] days, Upgrade.Effect... e) {
        Upgrade u = new Upgrade(id, name, c, desc, prices, days, e);
        UPGRADES.put(id, u);
        return u;
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
        // v2.5 (gün 17–50): yeni ürünlerin malzemeleri ve geç açılan sos/salata malzemeleri
        ing("su", "Şişe su", "adet", 5, ICE, 19);
        ing("maydanoz", "Maydanoz", "porsiyon", 2, SAL, 20);
        ing("corba", "Mercimek çorbası", "porsiyon", 18, DIG, 21);
        ing("kasar", "Kaşar peyniri", "porsiyon", 14, DIG, 23);
        ing("salgam", "Şalgam", "bardak", 10, ICE, 25);
        ing("pul_biber", "Pul biber", "porsiyon", 1, SAL, 26);
        ing("sutlac", "Fırın sütlaç", "kase", 28, TAT, 28);
        ing("cay", "Çay", "bardak", 3, ICE, 31);
        ing("acili_ezme", "Acılı ezme", "porsiyon", 4, SOSC, 32);
        ing("kofte", "Köfte", "porsiyon", 45, ET, 34);
        ing("kirmizi_lahana", "Kırmızı lahana", "porsiyon", 3, SAL, 38);
        ing("baklava", "Fıstıklı baklava", "porsiyon", 55, TAT, 42);
        ing("tantuni_eti", "Tantuni eti", "porsiyon", 60, ET, 46);

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
                l("sogan", "domates", "limon", "marul", "tursu", "aci_sos", "sumak", "maydanoz"),
                st("Harcı lavaşa yay", SOS, 3.0, "lavas", 1, "lahmacun_harci", 1),
                seqPas("Ocakta pişir", IZGARA, 6.0),
                top("Malzemeleri ekle", 1.8));
        prod("kunefe", "Künefe", 16, 240,
                st("Kadayıfı tereyağıyla yoğur", TATLI, 3.5, "kadayif", 1, "tereyagi", 2),
                seq("Peyniri ser", TATLI, 3.0, "peynir", 1),
                seqPas("Ocakta pişir", IZGARA, 8.0),
                seq("Çevir ve servis et", TATLI, 3.0));
        // ---- v2.5: 60 günlük kampanya (gün 17–50), var olan istasyonlarla
        prod("tavuk_pilav", "Tavuk pilav üstü", 17, 280,
                l("domates", "sogan", "tursu"),
                l("domates", "sogan", "tursu", "biber", "mayonez", "sarimsakli_yogurt", "maydanoz", "kozlenmis"),
                st("Pilavı tabağa al", IZGARA, 3.0, "pirinc", 1),
                st("Tereyağını gezdir", IZGARA, 2.0, "tereyagi", 1),
                st("Tavuğu dilimle", DONER, 4.0, "tavuk_doner", 2),
                top("Garnitürü ekle", 2.0));
        prod("su", "Su", 19, 15,
                st("Suyu dolaptan al", ICECEK, 0.8, "su", 1));
        prod("corba", "Mercimek çorbası", 21, 90,
                st("Çorbayı kaseye koy", IZGARA, 1.6, "corba", 1),
                seq("Limonu yanına koy", SOS, 0.8, "limon", 1));
        prod("kasarli_durum", "Kaşarlı dürüm", 23, 300,
                l("domates", "marul", "ketcap"),
                l("domates", "marul", "sogan", "ketcap", "mayonez", "aci_sos", "tursu", "maydanoz"),
                pas("Lavaşı ısıt", EKMEK, 3.0, "lavas", 1),
                st("Döneri dilimle", DONER, 3.5, "et_doner", 1),
                pas("Kaşarı ocakta erit", IZGARA, 3.0, "kasar", 1),
                top("Malzemeleri ekle", 2.0));
        prod("salgam", "Şalgam", 25, 40,
                st("Şalgamı doldur", ICECEK, 1.5, "salgam", 1));
        prod("sutlac", "Fırın sütlaç", 28, 85,
                st("Sütlacı dolaptan al", TATLI, 1.2, "sutlac", 1));
        prod("cay", "Çay", 31, 20,
                st("Çayı doldur", ICECEK, 1.2, "cay", 1));
        prod("kofte_ekmek", "Köfte ekmek", 34, 230,
                l("domates", "sogan", "maydanoz"),
                l("domates", "sogan", "marul", "maydanoz", "tursu", "aci_sos", "ketcap", "kirmizi_lahana"),
                pas("Ekmeği ısıt", EKMEK, 3.0, "ekmek", 1),
                pas("Köfteyi ocakta pişir", IZGARA, 5.0, "kofte", 1),
                top("Malzemeleri ekle", 2.0));
        prod("tavuk_iskender", "Tavuk iskender", 38, 360,
                l("yogurt", "domates", "biber"),
                l("yogurt", "domates", "biber", "aci_sos", "tursu", "sarimsakli_yogurt", "kozlenmis", "pul_biber"),
                st("Ekmeği küp doğra", EKMEK, 3.5, "ekmek", 1),
                st("Tavuğu dilimle ve ser", DONER, 5.5, "tavuk_doner", 2),
                st("Tereyağı ve domates sosunu gezdir", IZGARA, 5.0, "tereyagi", 2, "domates", 2),
                top("Yoğurt ve garnitürü ekle", 2.0));
        prod("baklava", "Fıstıklı baklava", 42, 160,
                st("Baklavayı tabağa diz", TATLI, 1.5, "baklava", 1));
        prod("tantuni", "Tantuni", 46, 260,
                l("sogan", "domates", "maydanoz"),
                l("sogan", "domates", "maydanoz", "sumak", "limon", "aci_sos", "tursu", "pul_biber"),
                pas("Lavaşı ısıt", EKMEK, 2.5, "lavas", 1),
                st("Tantuniyi sacda çevir", IZGARA, 4.0, "tantuni_eti", 1),
                top("Malzemeleri ekle", 1.8));
        prod("karisik_tabak", "Karışık döner tabağı", 50, 520,
                l("domates", "sogan", "biber"),
                l("domates", "sogan", "biber", "marul", "tursu", "aci_sos", "kozlenmis", "acili_ezme"),
                pas("Ekmeği ısıt", EKMEK, 3.0, "ekmek", 1),
                st("Et ve tavuk dilimle", DONER, 6.0, "et_doner", 2, "tavuk_doner", 1),
                top("Garnitürü diz", 2.0));
        PRODUCTS.get("corba").setNeedsWater();
        PRODUCTS.get("cay").setNeedsWater();

        // Patates dürümlere ve ekmek arası ürünlere girer: müşteri "patatesli" isterse kalemde "Patates kızart" adımı olur.
        for (String pid : new String[]{"ekmek_arasi", "tavuk_ekmek", "tavuk_durum", "hatay_durum", "zurna_durum",
                "kasarli_durum", "kofte_ekmek"}) PRODUCTS.get(pid).setFriesOk();

        // Yükseltmeler (fiyat/etki araştırma notundan; 'cirak' motor tasarımı). v2.5: 1–3. seviyeler v2.0 ile aynı,
        // 4–5 (tabelada 6) eklendi; yeni: ikinci kasa, soğutmalı vitrin, havalandırma, jeneratör, su deposu.
        BillType E = BillType.ELEKTRIK, G = BillType.DOGALGAZ, W = BillType.SU;
        up("cirak", "Çırak", MUTFAK, "Ek eller: kendi başına çalışır. Seviye 1 paketler, teslim eder, içecek doldurur, boşta hazırlık yapar. Seviye 2 ikinci çırak, ekmek ve lavaş ısıtır. Seviye 3 üçüncü çırak, döner dilimler ve ocağı kullanır. Seviye 4 kalfa: daha hızlı. Seviye 5 dördüncü çırak. Malzemeyi hep sen koyarsın. Her gün maaş alır.",
                new int[]{3000, 9000, 24000, 38000, 58000}, new int[]{3, 8, 16, 30, 44},
                new Upgrade.Effect(APPRENTICE, null, 1, 2, 3, 4, 5)).uses(W);
        up("doner_makinesi", "Döner makinesi", MUTFAK, "Döner dilimleme adımlarını hızlandırır.",
                new int[]{4000, 15000, 48000, 70000, 100000}, new int[]{4, 10, 18, 32, 46},
                new Upgrade.Effect(STATION_SPEED, s(DONER), 0.85, 0.70, 0.55, 0.50, 0.45)).uses(E, G);
        up("ekmek_isitici", "Ekmek ısıtıcı", MUTFAK, "Daha geniş ve hızlı ekmek ısıtıcı: aynı anda 3, 4, sonra 5 ve 6 ekmek ya da lavaş.",
                new int[]{2500, 8000, 22000, 34000, 52000}, new int[]{3, 9, 17, 29, 43},
                new Upgrade.Effect(STATION_CAPACITY, s(EKMEK), 1, 2, 2, 3, 4),
                new Upgrade.Effect(STATION_SPEED, s(EKMEK), 0.85, 0.70, 0.55, 0.50, 0.45)).uses(E);
        up("ocak", "Geniş ocak", MUTFAK, "Ocağa göz eklenir: aynı anda 3, 4, sonra 5 ve 6 iş (patates, pişirme); ocak işleri hızlanır.",
                new int[]{2500, 8000, 20000, 32000, 50000}, new int[]{3, 9, 17, 29, 43},
                new Upgrade.Effect(STATION_CAPACITY, s(IZGARA), 1, 2, 2, 3, 4),
                new Upgrade.Effect(STATION_SPEED, s(IZGARA), 0.90, 0.80, 0.65, 0.60, 0.55)).uses(G);
        up("oto_dilimleyici", "Otomatik dilimleyici", MUTFAK, "Döner makinesinde ek hız kazandırır.",
                new int[]{3500, 12000, 34000, 52000, 78000}, new int[]{6, 12, 21, 35, 50},
                new Upgrade.Effect(STATION_SPEED, s(DONER), 0.75, 0.55, 0.45, 0.40, 0.36)).uses(E);
        up("buzdolabi", "Buzdolabı", MUTFAK, "Her malzemeden daha çok stok saklar.",
                new int[]{2000, 7500, 24000, 36000, 54000}, new int[]{2, 8, 16, 28, 42},
                new Upgrade.Effect(STOCK_CAP, null, 50, 100, 220, 300, 400)).uses(E);
        up("masa", "Masa takımı", RESTORAN, "Aynı anda daha çok müşteri bekleyebilir.",
                new int[]{3000, 9000, 24000, 36000, 54000}, new int[]{11, 22, 28, 38, 52},
                new Upgrade.Effect(QUEUE, null, 1, 2, 3, 3, 4),
                new Upgrade.Effect(TIP, null, 0, 0, 0.05, 0.08, 0.10)).uses(W);
        up("tabela", "Tabela", RESTORAN, "Daha çok müşteri çeker.",
                new int[]{1000, 4000, 12000, 20000, 32000, 48000}, new int[]{8, 15, 24, 34, 44, 54},
                new Upgrade.Effect(ARRIVAL, null, 0.10, 0.20, 0.35, 0.42, 0.50, 0.60)).uses(E);
        up("klima", "Klima", RESTORAN, "Müşteriler daha sabırlı bekler.",
                new int[]{2500, 7000, 18000, 28000, 42000}, new int[]{13, 21, 26, 37, 49},
                new Upgrade.Effect(PATIENCE, null, 0.10, 0.20, 0.30, 0.36, 0.42)).uses(E);
        up("muzik", "Müzik sistemi", RESTORAN, "Biraz daha müşteri, yıldız kazancı artar.",
                new int[]{1800, 5500, 15000, 24000, 36000}, new int[]{4, 14, 23, 33, 47},
                new Upgrade.Effect(ARRIVAL, null, 0.05, 0.10, 0.15, 0.18, 0.20),
                new Upgrade.Effect(STAR_GAIN, null, 0.05, 0.10, 0.15, 0.20, 0.25)).uses(E);
        up("dekor", "Dekor", RESTORAN, "Turistler çoğalır, bahşişler artar.",
                new int[]{2200, 6500, 20000, 30000, 45000}, new int[]{7, 16, 25, 36, 51},
                new Upgrade.Effect(TOURIST, null, 0.10, 0.20, 0.35, 0.45, 0.55),
                new Upgrade.Effect(TIP, null, 0.05, 0.10, 0.15, 0.18, 0.22),
                new Upgrade.Effect(ARRIVAL, null, 0.05, 0.10, 0.15, 0.18, 0.20));
        up("havalandirma", "Havalandırma", MUTFAK, "Dükkânda duman ve koku kalmaz: müşteriler biraz daha sabırlı, ocak işleri biraz hızlı.",
                new int[]{2500, 8000, 18000, 28000, 42000}, new int[]{10, 19, 28, 39, 50},
                new Upgrade.Effect(PATIENCE, null, 0.04, 0.08, 0.12, 0.15, 0.18),
                new Upgrade.Effect(STATION_SPEED, s(IZGARA), 0.95, 0.90, 0.85, 0.82, 0.80)).uses(E);
        up("sogutma", "Soğutmalı vitrin", MUTFAK, "İçecekler hızlı servis edilir, hazır stok için daha çok yer.",
                new int[]{3000, 9000, 20000, 30000, 45000}, new int[]{12, 20, 30, 40, 52},
                new Upgrade.Effect(STATION_SPEED, s(ICECEK), 0.90, 0.80, 0.70, 0.65, 0.60),
                new Upgrade.Effect(READY_CAP, null, 2, 4, 6, 8, 10)).uses(E);
        up("ikinci_kasa", "İkinci kasa", RESTORAN, "Paketleme hızlanır, sırada bir müşteri daha bekleyebilir.",
                new int[]{6000, 14000, 28000, 42000, 62000}, new int[]{20, 27, 35, 44, 53},
                new Upgrade.Effect(STATION_SPEED, s(PAKET), 0.80, 0.65, 0.55, 0.50, 0.45),
                new Upgrade.Effect(QUEUE, null, 1, 1, 2, 2, 2)).uses(E);
        up("jenerator", "Jeneratör", MUTFAK, "Elektrik kesilince devreye girer: 1. seviye kısmi kesintiyi, 2. seviye tam kesintiyi de hafifletir.",
                new int[]{8000, 22000}, new int[]{9, 30},
                new Upgrade.Effect(POWER_BACKUP, null, 1, 2));
        up("su_deposu", "Su deposu", MUTFAK, "Su kesilince depodan kullanılır: 1. seviye kısmi kesintiyi, 2. seviye tam kesintiyi de hafifletir.",
                new int[]{4000, 12000}, new int[]{11, 30},
                new Upgrade.Effect(WATER_BACKUP, null, 1, 2));

        // Müşteri tipleri (sabır sn). Ağırlıklar araştırma oranlarından.
        TYPES.add(new CustomerType("acele", "Acele eden iş insanı", 1, 20, 25f, 0.0f, 0.0f,
                "Acelesi var.", "ekmek_arasi", "tavuk_ekmek").sizes(0.15f, 0.1f).price(0.6f));
        TYPES.add(new CustomerType("aile", "Sabırlı aile", 1, 15, 60f, 1.0f, 0.5f,
                "Sabırlı bir aile, birden fazla ürün isteyebilir.", "pilav_ustu", "porsiyon_doner", "ayran").sizes(0.15f, 0.3f).price(1.0f));
        TYPES.add(new CustomerType("ogrenci", "Öğrenci", 1, 15, 40f, 0.3f, 0.1f,
                "Öğrenci, bahşişi az.", "tavuk_ekmek", "kutu_icecek").sizes(0.45f, 0.05f).price(1.8f));
        TYPES.add(new CustomerType("taksi", "Taksi şoförü", 2, 7, 30f, 0.8f, 0.0f,
                "Taksi şoförü, çabuk olmalı.", "tavuk_durum", "kutu_icecek").sizes(0.1f, 0.15f).price(1.1f));
        TYPES.add(new CustomerType("yasli", "Yaşlı müşteri", 3, 10, 70f, 1.0f, 0.0f,
                "Yavaş ama sabırlı biri.", "ekmek_arasi", "ayran").sizes(0.5f, 0.0f).price(1.2f));
        TYPES.add(new CustomerType("turist", "Turist", 5, 10, 45f, 1.8f, 0.3f,
                "Turist, bol bahşiş verebilir.", "lahmacun", "iskender", "kunefe").sizes(0.1f, 0.25f).price(0.4f));
        TYPES.add(new CustomerType("esnaf", "Komşu esnaf", 7, 5, 50f, 1.0f, 0.1f,
                "Komşu esnaf, her gün gelir.", "ekmek_arasi", "ayran").sizes(0.3f, 0.1f).price(1.0f));
        TYPES.add(new CustomerType("sikayetci", "Şikâyetçi müşteri", 9, 5, 20f, 0.5f, 0.0f,
                "Şikâyetçi, çok bekletme.", new String[0]).sizes(0.2f, 0.1f).price(1.3f));
        TYPES.add(new CustomerType("gece", "Gece vardiyası işçisi", 13, 8, 35f, 1.0f, 0.2f,
                "Gece vardiyasından geliyor.", "porsiyon_doner", "hatay_durum").sizes(0.1f, 0.35f).price(0.9f));
        TYPES.add(new CustomerType("influencer", "Influencer", 19, 5, 30f, 1.5f, 0.2f,
                "Influencer, servis hızına dikkat eder.", "iskender", "kunefe").sizes(0.05f, 0.4f).price(0.5f));
        // v2.5 yeni tipler
        TYPES.add(new CustomerType("kurye", "Yemek kuryesi", 15, 6, 28f, 0.4f, 0.3f,
                "Kurye, paketi hemen ister.", "ekmek_arasi", "tavuk_durum", "kutu_icecek").sizes(0.1f, 0.2f).price(1.4f));
        TYPES.add(new CustomerType("memur", "Memur", 18, 7, 35f, 0.8f, 0.2f,
                "Öğle arasında memur.", "pilav_ustu", "corba", "ayran").sizes(0.25f, 0.05f).price(1.0f));
        TYPES.add(new CustomerType("insaatci", "İnşaat işçisi", 24, 6, 40f, 0.6f, 0.3f,
                "Çok acıkmış, duble sever.", "porsiyon_doner", "kofte_ekmek", "ayran", "salgam").sizes(0.0f, 0.55f).price(1.5f));
        TYPES.add(new CustomerType("sporcu", "Sporcu", 27, 4, 35f, 0.9f, 0.1f,
                "Tavuk ve su sever.", "tavuk_pilav", "tavuk_durum", "su").sizes(0.2f, 0.2f).price(0.9f));
        TYPES.add(new CustomerType("gurme", "Gurme", 33, 3, 45f, 2.0f, 0.4f,
                "Lezzete bakar, fiyata pek bakmaz.", "iskender", "tantuni", "baklava").sizes(0.05f, 0.3f).price(0.3f));
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

    /** v2.5: tam o gün gelmeye başlayan müşteri tipleri (brifing duyurusu; 1. gün boş). */
    public static List<CustomerType> newTypesOnDay(int day) {
        List<CustomerType> l = new ArrayList<>();
        if (day <= 1) return l;
        for (CustomerType t : TYPES) if (t.minDay == day) l.add(t);
        return l;
    }

    /** v2.5: tam o gün açılan yükseltme seviyeleri (yükseltme -> açılan seviye; brifing/market duyurusu). */
    public static Map<Upgrade, Integer> newUpgradeLevelsOnDay(int day) {
        Map<Upgrade, Integer> m = new LinkedHashMap<>();
        for (Upgrade u : UPGRADES.values()) {
            for (int k = 0; k < u.unlockDays.length; k++) if (u.unlockDays[k] == day) { m.put(u, k + 1); break; }
        }
        return m;
    }

    public static List<CustomerType> typesForDay(int day) {
        List<CustomerType> l = new ArrayList<>();
        for (CustomerType t : TYPES) if (t.minDay <= day) l.add(t);
        return l;
    }
}
