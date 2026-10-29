package com.doner.oyunu.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Satılan ürün (tarif dahil). Adımlar kalem içinde serbest sıralıdır (Step.sequential olanlar hariç). */
public final class Product {
    /** Tek tek malzeme eklemenin tahmini iş yükü (sn/malzeme: ekleme süresi + oyuncu eylemi); müşteri sabrı hesabında kullanılır. */
    static final float TOPPING_WORK_EACH = 1.5f;

    public final String id;
    public final String name;
    public final int openDay;
    public final int price;
    public final List<Step> steps;
    /** Varsayılan topping kümesi (malzeme id'leri, sıralı). Boş: ürün malzeme seçimi istemez. */
    public final Set<String> defaultToppings;
    /** Seçilebilir topping havuzu (4-8 malzeme). Boş: malzeme seçimi yok. */
    public final List<String> toppingPool;

    /** Patatesli kalemin fiyat farkı (TL; boyla ölçeklenmez). */
    public static final int FRIES_PRICE = 40;

    /** Patatesle sipariş edilebilir (patates ürüne girer; müşteri isteğiyle "patatesli" olur). */
    private boolean friesOk;
    /** Bu nesne bir ürünün "patatesli" çeşidi mi (adımlarda "Patates kızart" vardır). */
    private final boolean friesVariant;
    private Product variant;

    public Product(String id, String name, int openDay, int price, List<Step> steps) {
        this(id, name, openDay, price, steps, new ArrayList<String>(), new ArrayList<String>());
    }

    public Product(String id, String name, int openDay, int price, List<Step> steps,
                   List<String> defaultToppings, List<String> toppingPool) {
        this(id, name, openDay, price, steps, defaultToppings, toppingPool, false);
    }

    private Product(String id, String name, int openDay, int price, List<Step> steps,
                    List<String> defaultToppings, List<String> toppingPool, boolean friesVariant) {
        this.friesVariant = friesVariant;
        this.id = id;
        this.name = name;
        this.openDay = openDay;
        this.price = price;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.defaultToppings = Collections.unmodifiableSet(new LinkedHashSet<>(defaultToppings));
        this.toppingPool = Collections.unmodifiableList(new ArrayList<>(toppingPool));
    }

    /** Katalog kurulumu: bu ürün patatesli sipariş edilebilir. */
    void setFriesOk() { friesOk = true; }

    /** Patatesle sipariş edilebilir mi (ürün düzeyi; patatesli çeşidin kendisi için de true). */
    public boolean friesOk() { return friesOk || friesVariant; }

    /** Bu bir "patatesli" çeşit mi. */
    public boolean isFries() { return friesVariant; }

    /**
     * Ürünün patatesli çeşidi (aynı id ve ad; adımlara malzeme seçiminden hemen önce "Patates kızart" eklenir).
     * Patates kabul etmeyen üründe ya da çeşidin kendisinde kendisini döner.
     */
    public Product friesVariant() {
        if (friesVariant || !friesOk) return this;
        if (variant == null) {
            List<Step> st = new ArrayList<>(steps);
            int at = toppingStepIndex();
            if (at < 0) at = st.size();
            st.add(at, Catalog.FRY_STEP);
            Product v = new Product(id, name, openDay, price, st, new ArrayList<>(defaultToppings), toppingPool, true);
            v.friesOk = true;
            variant = v;
        }
        return variant;
    }

    /** Patatesli çeşit mi aynı ürünün sade hali mi: id eşleşmesi (çeşitler aynı id'yi taşır). */
    public boolean sameProduct(Product o) { return o != null && o.id.equals(id); }

    /** Gün N'de açık olan varsayılan topping'ler (sıralı). Henüz açılmamış malzemeler kümede yer almaz. */
    public Set<String> defaultToppings(int day) {
        Set<String> out = new LinkedHashSet<>();
        for (String id : defaultToppings) if (isOpenOn(id, day)) out.add(id);
        return out;
    }

    /** Gün N'de açık topping havuzu (havuz sırasında). */
    public List<String> toppingPool(int day) {
        List<String> out = new ArrayList<>();
        for (String id : toppingPool) if (isOpenOn(id, day)) out.add(id);
        return out;
    }

    private static boolean isOpenOn(String ingredientId, int day) {
        Ingredient i = Catalog.ingredient(ingredientId);
        return i != null && i.isOpen(day);
    }

    /** Döner (et) içeren ürün mü: sipariş kalemi boy taşır, DONER istasyonunda boy seçilir. */
    public boolean hasMeat() { return sizedStepIndex() >= 0; }

    /** Boy seçilen (dilimleme) adımın indeksi, yoksa -1. */
    public int sizedStepIndex() {
        for (int i = 0; i < steps.size(); i++) if (steps.get(i).isSized()) return i;
        return -1;
    }

    /** Topping (malzeme seçimi) adımının indeksi, yoksa -1. */
    public int toppingStepIndex() {
        for (int i = 0; i < steps.size(); i++) if (steps.get(i).isTopping()) return i;
        return -1;
    }

    public boolean hasToppings() { return toppingStepIndex() >= 0; }

    public float workSeconds() {
        float t = 0;
        for (Step s : steps) t += s.baseSeconds + (s.isTopping() ? TOPPING_WORK_EACH * defaultToppings.size() : 0f);
        return t;
    }

    /** Tüm adımların toplam malzeme ihtiyacı + (tüm) varsayılan topping kümesi (her biri 1 birim). */
    public Map<String, Integer> totalNeeds() { return totalNeeds(Integer.MAX_VALUE); }

    /** Gün N için: yalnızca o gün açık varsayılan topping'ler sayılır. */
    public Map<String, Integer> totalNeeds(int day) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Step s : steps) {
            for (Map.Entry<String, Integer> e : s.needs.entrySet()) {
                Integer old = m.get(e.getKey());
                m.put(e.getKey(), (old == null ? 0 : old) + e.getValue());
            }
        }
        for (String t : defaultToppings(day)) {
            Integer old = m.get(t);
            m.put(t, (old == null ? 0 : old) + 1);
        }
        return m;
    }

    /** Ham malzeme maliyeti (varsayılan topping ile). */
    public int ingredientCost() {
        int c = 0;
        for (Map.Entry<String, Integer> e : totalNeeds().entrySet()) {
            c += Catalog.ingredient(e.getKey()).price * e.getValue();
        }
        return c;
    }

    @Override
    public String toString() {
        return name;
    }
}
