package com.doner.oyunu.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bir ürünün tek hazırlık adımı. */
public final class Step {
    public final String name;
    public final Station station;
    public final float baseSeconds;
    /** malzeme id -> miktar (adım başlarken tüketilir). */
    public final Map<String, Integer> needs;

    public Step(String name, Station station, float baseSeconds, Map<String, Integer> needs) {
        this.name = name;
        this.station = station;
        this.baseSeconds = baseSeconds;
        this.needs = Collections.unmodifiableMap(new LinkedHashMap<>(needs));
    }
}
