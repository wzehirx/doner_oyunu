package com.doner.oyunu.engine;

/** Müşteri tipi. */
public final class CustomerType {
    public final String id;
    public final String name;
    public final int minDay;
    public final int weight;
    public final float patienceSeconds;
    public final float tipMult;
    /** Çoklu ürün sipariş eğilimi (0..1 ek olasılık). */
    public final float extraItemChance;
    public final String[] preferred;
    /** Duyuru için kısa tanım: "Acelesi var." */
    public final String hint;

    public CustomerType(String id, String name, int minDay, int weight, float patienceSeconds,
                        float tipMult, float extraItemChance, String hint, String... preferred) {
        this.id = id;
        this.name = name;
        this.minDay = minDay;
        this.weight = weight;
        this.patienceSeconds = patienceSeconds;
        this.tipMult = tipMult;
        this.extraItemChance = extraItemChance;
        this.hint = hint;
        this.preferred = preferred;
    }

    public boolean prefers(String productId) {
        for (String p : preferred) if (p.equals(productId)) return true;
        return false;
    }
}
