package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CatalogTest {
    @Test public void productsReferenceExistingIngredients() {
        assertTrue(Catalog.products().size() >= 10);
        Set<String> ids = new HashSet<>();
        for (Product p : Catalog.products()) {
            assertTrue("benzersiz id " + p.id, ids.add(p.id));
            assertTrue(p.name + " 1-4 adım", p.steps.size() >= 1 && p.steps.size() <= 4);
            assertTrue(p.price > 0 && p.openDay >= 1);
            assertTrue("kar var: " + p.name, p.ingredientCost() < p.price);
            for (Step s : p.steps) {
                assertNotNull(s.station);
                assertTrue(s.baseSeconds > 0);
                for (Map.Entry<String, Integer> e : s.needs.entrySet()) {
                    assertNotNull(p.id + " malzeme " + e.getKey(), Catalog.ingredient(e.getKey()));
                    assertTrue(e.getValue() > 0);
                }
            }
        }
    }

    @Test public void everyIngredientIsUsed() {
        Set<String> used = new HashSet<>();
        for (Product p : Catalog.products()) {
            used.addAll(p.totalNeeds().keySet());
            used.addAll(p.friesVariant().totalNeeds().keySet()); // patates yalnızca "patatesli" çeşitte kullanılır
            used.addAll(p.toppingPool);
        }
        for (Ingredient i : Catalog.ingredients()) assertTrue(i.id + " kullanılmıyor", used.contains(i.id));
    }

    @Test public void openingDaysFollowSpec() {
        assertEquals(1, Catalog.product("ekmek_arasi").openDay);
        assertEquals(2, Catalog.product("ayran").openDay);
        assertEquals(3, Catalog.product("pilav_ustu").openDay);
        assertEquals(4, Catalog.product("tavuk_durum").openDay);
        assertEquals(6, Catalog.product("hatay_durum").openDay);
        assertEquals(8, Catalog.product("zurna_durum").openDay);
        assertEquals(10, Catalog.product("iskender").openDay);
        assertEquals(12, Catalog.product("porsiyon_doner").openDay);
        assertEquals(14, Catalog.product("lahmacun").openDay);
        assertEquals(16, Catalog.product("kunefe").openDay);
        assertEquals("1. gün tek ürün", 1, Catalog.productsForDay(1).size());
        // birikimli menü
        for (int d = 2; d <= 30; d++) {
            assertTrue(Catalog.productsForDay(d).containsAll(Catalog.productsForDay(d - 1)));
        }
        assertEquals(1, Catalog.newProductsOnDay(3).size());
    }

    @Test public void allStationsAreUsedAndIdsUnique() {
        Set<Station> used = new HashSet<>();
        for (Product p : Catalog.products()) for (Step s : p.steps) used.add(s.station);
        used.add(Station.PAKET); // PAKET artık yalnızca ortak "Siparişi paketle" adımı
        assertEquals(Station.values().length, used.size());
        Set<Integer> ids = new HashSet<>();
        for (Station s : Station.values()) {
            assertTrue(ids.add(s.id));
            assertSame(s, Station.byId(s.id));
        }
        assertNull(Station.byId(99));
    }

    @Test public void upgradesAreConsistent() {
        Set<String> ids = new HashSet<>();
        for (Upgrade u : Catalog.upgrades()) {
            assertTrue(ids.add(u.id));
            assertTrue(u.maxLevel() >= 1);
            assertEquals(u.prices.length, u.unlockDays.length);
            for (int i = 0; i < u.prices.length; i++) {
                assertTrue(u.prices[i] > 0);
                if (i > 0) {
                    assertTrue(u.prices[i] > u.prices[i - 1]);
                    assertTrue(u.unlockDays[i] >= u.unlockDays[i - 1]);
                }
            }
            for (Upgrade.Effect e : u.effects) {
                assertTrue(e.values.length >= u.maxLevel());
                if (e.type == Upgrade.EffectType.STATION_SPEED) {
                    assertNotNull(e.targets);
                    for (double v : e.values) assertTrue(v > 0 && v < 1);
                }
            }
        }
        assertTrue(new Upgrades(new SaveData()).slots() == 1);
        SaveData s = new SaveData();
        s.upgradeLevels.put("cirak", 2);
        assertEquals("çırak yuva eklemez", 1, new Upgrades(s).slots());
        assertEquals(2, new Upgrades(s).apprenticeLevel());
        assertEquals(3, Catalog.upgrade("cirak").maxLevel());
    }

    @Test public void customerTypesValid() {
        assertFalse(Catalog.typesForDay(1).isEmpty());
        for (CustomerType t : Catalog.customerTypes()) {
            assertTrue(t.patienceSeconds > 0 && t.weight > 0);
            for (String p : t.preferred) assertNotNull(Catalog.product(p));
            assertNotNull(t.hint);
        }
    }

    @Test public void dayConfigCurveIsMonotonicAndInfinite() {
        DayConfig prev = DayConfig.forDay(1);
        assertEquals(126f, prev.duration, 0.01f);
        assertEquals(1, prev.maxItems);
        for (int d = 2; d <= 80; d++) {
            DayConfig c = DayConfig.forDay(d);
            assertTrue(c.duration <= 300f && c.duration >= prev.duration);
            if (d <= 41) { // 40. günden sonra döngüsel dalgalanma olabilir
                assertTrue(c.arrivalInterval <= prev.arrivalInterval + 1e-4);
                assertTrue(c.patienceFactor <= prev.patienceFactor + 1e-4);
                assertTrue(c.maxItems >= prev.maxItems);
            }
            assertTrue(c.arrivalInterval >= 4.2f - 1e-4 && c.patienceFactor >= 0.5f - 1e-4);
            assertTrue(c.rent > prev.rent);
            assertTrue(c.maxItems <= 3);
            prev = c;
        }
        assertEquals(3, DayConfig.forDay(40).maxItems);
        assertEquals(300f, DayConfig.forDay(40).duration, 0.01f);
        assertTrue(DayConfig.forDay(200).intensity > DayConfig.forDay(41).intensity);
        assertEquals(0.6, DayConfig.starMultiplier(1.0), 1e-9);
        assertEquals(1.0, DayConfig.starMultiplier(3.0), 1e-9);
        assertEquals(1.4, DayConfig.starMultiplier(5.0), 1e-9);
    }
}
