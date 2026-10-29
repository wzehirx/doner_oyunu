package com.doner.oyunu.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Müşteri isteği: varsayılan topping kümesini değiştirir ("soğansız", "acılı", "sosuz", "bol soslu"...). */
public final class Request {
    enum Kind { REMOVE, ADD, NO_SAUCE, MORE_SAUCE }

    /** Ekranda/duyuruda görünen metin: "soğansız". */
    public final String text;
    final Kind kind;
    final String ingredientId;
    /** Aynı gruptan iki istek aynı siparişte olmaz. */
    final String group;

    private Request(String text, Kind kind, String ingredientId, String group) {
        this.text = text;
        this.kind = kind;
        this.ingredientId = ingredientId;
        this.group = group;
    }

    private static final List<Request> ALL;
    static {
        List<Request> l = new ArrayList<>();
        l.add(new Request("soğansız", Kind.REMOVE, "sogan", "sogan"));
        l.add(new Request("domatessiz", Kind.REMOVE, "domates", "domates"));
        l.add(new Request("marulsuz", Kind.REMOVE, "marul", "marul"));
        l.add(new Request("turşusuz", Kind.REMOVE, "tursu", "tursu"));
        l.add(new Request("acılı", Kind.ADD, "aci_sos", "aci"));
        l.add(new Request("acısız", Kind.REMOVE, "aci_sos", "aci"));
        l.add(new Request("sosuz", Kind.NO_SAUCE, null, "sos"));
        l.add(new Request("bol soslu", Kind.MORE_SAUCE, null, "sos"));
        ALL = Collections.unmodifiableList(l);
    }

    /** Tüm olası isteklerin listesi. */
    public static List<Request> all() { return ALL; }

    /** İsteği ürünün (zaten uygulanmış) hedef kümesine uygular. */
    void apply(Product p, Set<String> target) {
        switch (kind) {
            case REMOVE: target.remove(ingredientId); break;
            case ADD: if (p.toppingPool.contains(ingredientId)) target.add(ingredientId); break;
            case NO_SAUCE:
                for (String id : new ArrayList<>(target)) {
                    Ingredient i = Catalog.ingredient(id);
                    if (i != null && i.category == Ingredient.Category.SOS && !id.equals("aci_sos")) target.remove(id);
                }
                break;
            case MORE_SAUCE:
                for (String id : p.toppingPool) {
                    Ingredient i = Catalog.ingredient(id);
                    if (i != null && i.category == Ingredient.Category.SOS && !id.equals("aci_sos")) target.add(id);
                }
                break;
            default: break;
        }
    }

    /** Bu istek ürünün hedef kümesini gerçekten değiştirir mi (ürünün varsayılanına göre). */
    boolean changes(Product p, Set<String> current) {
        if (!p.hasToppings()) return false;
        Set<String> t = new LinkedHashSet<>(current);
        apply(p, t);
        return !t.equals(current);
    }

    @Override public String toString() { return text; }
}
