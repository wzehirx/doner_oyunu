package com.doner.oyunu.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Malzeme stoğu. */
public final class Inventory {
    private final Map<String, Integer> stock = new LinkedHashMap<>();

    public int get(String id) {
        Integer v = stock.get(id);
        return v == null ? 0 : v;
    }

    public void set(String id, int qty) {
        if (qty <= 0) stock.remove(id); else stock.put(id, qty);
    }

    public void add(String id, int qty) { set(id, get(id) + qty); }

    public boolean has(String id, int qty) { return get(id) >= qty; }

    public boolean hasAll(Map<String, Integer> needs) {
        for (Map.Entry<String, Integer> e : needs.entrySet()) if (!has(e.getKey(), e.getValue())) return false;
        return true;
    }

    /** Yeterliyse düşer ve true döner. */
    public boolean consumeAll(Map<String, Integer> needs) {
        if (!hasAll(needs)) return false;
        for (Map.Entry<String, Integer> e : needs.entrySet()) add(e.getKey(), -e.getValue());
        return true;
    }

    public Map<String, Integer> asMap() { return Collections.unmodifiableMap(new LinkedHashMap<>(stock)); }

    public Inventory copy() {
        Inventory c = new Inventory();
        c.stock.putAll(stock);
        return c;
    }

    public int total() {
        int t = 0;
        for (int v : stock.values()) t += v;
        return t;
    }
}
