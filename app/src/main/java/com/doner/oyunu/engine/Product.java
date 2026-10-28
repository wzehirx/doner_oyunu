package com.doner.oyunu.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Satılan ürün (tarif dahil). */
public final class Product {
    public final String id;
    public final String name;
    public final int openDay;
    public final int price;
    public final List<Step> steps;

    public Product(String id, String name, int openDay, int price, List<Step> steps) {
        this.id = id;
        this.name = name;
        this.openDay = openDay;
        this.price = price;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
    }

    public float workSeconds() {
        float t = 0;
        for (Step s : steps) t += s.baseSeconds;
        return t;
    }

    /** Tüm adımların toplam malzeme ihtiyacı. */
    public Map<String, Integer> totalNeeds() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Step s : steps) {
            for (Map.Entry<String, Integer> e : s.needs.entrySet()) {
                Integer old = m.get(e.getKey());
                m.put(e.getKey(), (old == null ? 0 : old) + e.getValue());
            }
        }
        return m;
    }

    /** Ham malzeme maliyeti. */
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
