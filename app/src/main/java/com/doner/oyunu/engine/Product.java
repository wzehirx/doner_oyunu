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
    public final String id;
    public final String name;
    public final int openDay;
    public final int price;
    public final List<Step> steps;
    /** Varsayılan topping kümesi (malzeme id'leri, sıralı). Boş: ürün malzeme seçimi istemez. */
    public final Set<String> defaultToppings;
    /** Seçilebilir topping havuzu (4-8 malzeme). Boş: malzeme seçimi yok. */
    public final List<String> toppingPool;

    public Product(String id, String name, int openDay, int price, List<Step> steps) {
        this(id, name, openDay, price, steps, new ArrayList<String>(), new ArrayList<String>());
    }

    public Product(String id, String name, int openDay, int price, List<Step> steps,
                   List<String> defaultToppings, List<String> toppingPool) {
        this.id = id;
        this.name = name;
        this.openDay = openDay;
        this.price = price;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.defaultToppings = Collections.unmodifiableSet(new LinkedHashSet<>(defaultToppings));
        this.toppingPool = Collections.unmodifiableList(new ArrayList<>(toppingPool));
    }

    /** Topping (malzeme seçimi) adımının indeksi, yoksa -1. */
    public int toppingStepIndex() {
        for (int i = 0; i < steps.size(); i++) if (steps.get(i).isTopping()) return i;
        return -1;
    }

    public boolean hasToppings() { return toppingStepIndex() >= 0; }

    public float workSeconds() {
        float t = 0;
        for (Step s : steps) t += s.baseSeconds + (s.isTopping() ? 0.3f * defaultToppings.size() : 0f);
        return t;
    }

    /** Tüm adımların toplam malzeme ihtiyacı + varsayılan topping kümesi (her biri 1 birim). */
    public Map<String, Integer> totalNeeds() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Step s : steps) {
            for (Map.Entry<String, Integer> e : s.needs.entrySet()) {
                Integer old = m.get(e.getKey());
                m.put(e.getKey(), (old == null ? 0 : old) + e.getValue());
            }
        }
        for (String t : defaultToppings) {
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
