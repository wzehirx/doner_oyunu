package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** v0.5: topping havuzları, malzeme kategorileri, adım bağımlılıkları. */
public class CatalogV05Test {

    @Test public void toppingProductsHavePoolsOf4To8AndDefaultsInsidePool() {
        int withToppings = 0;
        for (Product p : Catalog.products()) {
            if (!p.hasToppings()) {
                assertTrue(p.name + ": topping adımı yoksa varsayılan/havuz da yok", p.defaultToppings.isEmpty() && p.toppingPool.isEmpty());
                continue;
            }
            withToppings++;
            assertTrue(p.name + " havuz 4-8", p.toppingPool.size() >= 4 && p.toppingPool.size() <= 8);
            assertTrue(p.name + " varsayılan >= 3", p.defaultToppings.size() >= 3);
            assertTrue(p.toppingPool.containsAll(p.defaultToppings));
            assertEquals("havuzda tekrar yok", p.toppingPool.size(), new HashSet<>(p.toppingPool).size());
            for (String id : p.toppingPool) {
                Ingredient i = Catalog.ingredient(id);
                assertNotNull(p.id + " " + id, i);
                assertTrue(id + " SOS veya SALATA olmalı", i.isTopping());
            }
        }
        assertTrue(withToppings >= 9);
        assertFalse(Catalog.product("ayran").hasToppings());
        assertFalse(Catalog.product("kutu_icecek").hasToppings());
        assertFalse(Catalog.product("kunefe").hasToppings());
        assertTrue(Catalog.product("ekmek_arasi").defaultToppings.containsAll(
                java.util.Arrays.asList("domates", "marul", "sogan", "ketcap")));
        assertTrue(Catalog.product("hatay_durum").defaultToppings.containsAll(
                java.util.Arrays.asList("sogan", "domates", "tursu", "aci_sos", "nar_eksisi")));
    }

    @Test public void toppingStepIsLastSequentialAtSauceStation() {
        for (Product p : Catalog.products()) {
            if (!p.hasToppings()) continue;
            int ti = p.toppingStepIndex();
            assertEquals(p.name + ": topping son adım", p.steps.size() - 1, ti);
            Step s = p.steps.get(ti);
            assertEquals(Station.SOS, s.station);
            assertTrue(s.sequential && s.isTopping());
            assertTrue("topping adımı malzemeyi seçimden tüketir", s.needs.isEmpty());
            int topping = 0;
            for (Step x : p.steps) if (x.isTopping()) topping++;
            assertEquals(1, topping);
        }
    }

    @Test public void sauceAndSaladIngredientsHaveCategories() {
        Set<String> sos = new HashSet<>(java.util.Arrays.asList("yogurt", "ketcap", "mayonez", "aci_sos", "nar_eksisi"));
        Set<String> salata = new HashSet<>(java.util.Arrays.asList("domates", "sogan", "marul", "tursu", "biber", "limon"));
        for (String id : sos) assertEquals(id, Ingredient.Category.SOS, Catalog.ingredient(id).category);
        for (String id : salata) assertEquals(id, Ingredient.Category.SALATA, Catalog.ingredient(id).category);
        assertEquals(Ingredient.Category.ET, Catalog.ingredient("et_doner").category);
        assertEquals(Ingredient.Category.ET, Catalog.ingredient("tavuk_doner").category);
        assertEquals(Ingredient.Category.ICECEK, Catalog.ingredient("ayran").category);
    }

    @Test public void totalNeedsIncludeDefaultToppings() {
        Product p = Catalog.product("ekmek_arasi");
        assertEquals(Integer.valueOf(1), p.totalNeeds().get("ketcap"));
        assertEquals(Integer.valueOf(1), p.totalNeeds().get("ekmek"));
        assertTrue(p.ingredientCost() < p.price);
    }

    @Test public void sequentialStepsOnlyWhereOrderMatters() {
        Product k = Catalog.product("kunefe");
        assertFalse(k.steps.get(0).sequential);
        for (int i = 1; i < k.steps.size(); i++) assertTrue(k.steps.get(i).sequential);
        Product e = Catalog.product("ekmek_arasi");
        assertFalse(e.steps.get(0).sequential);
        assertFalse(e.steps.get(1).sequential);
    }

    @Test public void drinkAndMeatStepsAreRecognisedForPrep() {
        assertTrue(Catalog.product("ayran").steps.get(0).isDrinkFill());
        assertFalse(Catalog.product("kutu_icecek").steps.get(0).isDrinkFill());
        assertEquals("et_doner", Catalog.product("ekmek_arasi").steps.get(1).meatId());
        assertEquals("tavuk_doner", Catalog.product("tavuk_durum").steps.get(1).meatId());
        assertNull(Catalog.product("ekmek_arasi").steps.get(0).meatId());
    }

    @Test public void requestsExist() {
        Set<String> texts = new HashSet<>();
        for (Request r : Request.all()) texts.add(r.text);
        assertTrue(texts.containsAll(java.util.Arrays.asList("soğansız", "acılı", "acısız", "sosuz", "bol soslu")));
    }
}
