package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;
import com.doner.oyunu.ui.Texts;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** v0.6 sos/salata malzemeleri gün gün açılır: takvim, müşteri istekleri, varsayılan kümeler, market, öneri, duyuru. */
public class UnlockDayTest {

    private static SaveData day(int d) {
        SaveData s = SaveData.newGame();
        s.day = d;
        s.stars = 4.0;
        s.money = 20000;
        Shop.buyRecommended(s);
        return s;
    }

    private static List<String> ids(List<Ingredient> l) {
        List<String> out = new ArrayList<>();
        for (Ingredient i : l) out.add(i.id);
        return out;
    }

    // ------------------------------------------------------------ takvim
    @Test public void calendarMatchesDesign() {
        assertEquals(Arrays.asList("domates", "sogan", "marul"), ids(Catalog.newIngredientsOnDay(1)));
        assertTrue(Catalog.newIngredientsOnDay(2).isEmpty());
        assertEquals(Arrays.asList("ketcap", "mayonez"), ids(Catalog.newIngredientsOnDay(3)));
        assertTrue(Catalog.newIngredientsOnDay(4).isEmpty());
        assertEquals(Arrays.asList("aci_sos"), ids(Catalog.newIngredientsOnDay(5)));
        assertEquals(Arrays.asList("tursu"), ids(Catalog.newIngredientsOnDay(7)));
        assertEquals(Arrays.asList("yogurt"), ids(Catalog.newIngredientsOnDay(8))); // zurna dürüm ile
        assertEquals(Arrays.asList("nar_eksisi"), ids(Catalog.newIngredientsOnDay(9)));
        assertEquals(Arrays.asList("biber", "sumak"), ids(Catalog.newIngredientsOnDay(11)));
        assertEquals(Arrays.asList("sarimsakli_yogurt"), ids(Catalog.newIngredientsOnDay(13)));
        assertEquals(Arrays.asList("limon"), ids(Catalog.newIngredientsOnDay(14))); // lahmacun ile
        assertEquals(Arrays.asList("kozlenmis"), ids(Catalog.newIngredientsOnDay(15)));
        for (int d = 16; d <= 40; d++) assertTrue(Catalog.newIngredientsOnDay(d).isEmpty());
    }

    @Test public void everyToppingIngredientHasAnUnlockDayAndBasicsAreOpenFromDayOne() {
        int toppings = 0;
        for (Ingredient i : Catalog.ingredients()) {
            if (i.isTopping()) {
                toppings++;
                assertTrue(i.id, i.unlockDay >= 1 && i.unlockDay <= 15);
            } else {
                assertEquals(i.id + " temel malzeme", 1, i.unlockDay);
            }
            assertEquals(i.unlockDay <= 5, i.isOpen(5));
        }
        assertEquals(14, toppings);
        assertEquals("1. gün: 11 temel malzeme + domates, soğan, marul", 14, Catalog.ingredientsForDay(1).size());
        assertEquals(Catalog.ingredients().size(), Catalog.ingredientsForDay(15).size());
        assertTrue(Catalog.ingredientsForDay(1).size() < Catalog.ingredientsForDay(5).size());
    }

    @Test public void productsHaveUsableDefaultsAndPoolsOnTheirOpeningDay() {
        for (Product p : Catalog.products()) {
            if (!p.hasToppings()) continue;
            List<String> poolAtOpen = p.toppingPool(p.openDay);
            assertTrue(p.id + " havuz açılış gününde", poolAtOpen.size() >= 3);
            assertFalse(p.id + " varsayılan açılış gününde boş olamaz", p.defaultToppings(p.openDay).isEmpty());
            assertTrue(p.id, poolAtOpen.containsAll(p.defaultToppings(p.openDay)));
            assertTrue(p.id + " havuz en çok 8", p.toppingPool.size() <= 8 && p.toppingPool.size() >= 4);
            assertTrue(p.id, p.toppingPool.containsAll(p.defaultToppings));
            for (int d = p.openDay; d <= 20; d++) {
                for (String id : p.toppingPool(d)) assertTrue(p.id + " " + id + " gün " + d, Catalog.ingredient(id).isOpen(d));
                for (String id : p.defaultToppings(d)) assertTrue(p.id + " " + id, p.toppingPool(d).contains(id));
            }
            assertEquals("15. günden sonra her şey açık", new java.util.ArrayList<>(p.toppingPool), p.toppingPool(15));
        }
    }

    @Test public void closedIngredientsDropOutOfDefaultSets() {
        Product ekmek = Catalog.product("ekmek_arasi");
        assertEquals(Arrays.asList("domates", "marul", "sogan"), new ArrayList<>(ekmek.defaultToppings(1)));
        assertEquals(Arrays.asList("domates", "marul", "sogan", "ketcap"), new ArrayList<>(ekmek.defaultToppings(3)));
        assertEquals(Arrays.asList("domates", "marul", "sogan"), ekmek.toppingPool(2));
        Product hatay = Catalog.product("hatay_durum");
        assertEquals("tursu 7, nar ekşisi 9. günde açılır", Arrays.asList("sogan", "domates", "aci_sos"), new ArrayList<>(hatay.defaultToppings(6)));
        assertEquals(5, hatay.defaultToppings(9).size());
        Map<String, Integer> needs1 = ekmek.totalNeeds(1);
        assertFalse(needs1.containsKey("ketcap"));
        assertTrue(ekmek.totalNeeds(3).containsKey("ketcap"));
        assertTrue("parametresiz hepsi", ekmek.totalNeeds().containsKey("ketcap"));
    }

    // ------------------------------------------------------------ siparişler yalnızca açık malzeme kullanır
    private static final Map<String, String> REQUEST_INGREDIENT = new HashMap<>();
    static {
        REQUEST_INGREDIENT.put("soğansız", "sogan");
        REQUEST_INGREDIENT.put("domatessiz", "domates");
        REQUEST_INGREDIENT.put("marulsuz", "marul");
        REQUEST_INGREDIENT.put("turşusuz", "tursu");
        REQUEST_INGREDIENT.put("acılı", "aci_sos");
        REQUEST_INGREDIENT.put("acısız", "aci_sos");
    }

    @Test public void ordersAndRequestsOnlyUseOpenIngredients() {
        for (int d : new int[]{1, 2, 3, 4, 5, 6, 8, 10, 12, 14}) {
            int ordersWithRequests = 0;
            for (long seed = 1; seed <= 6; seed++) {
                SaveData s = day(d);
                s.stars = 5.0;
                GameEngine e = new GameEngine(seed, s);
                final int day = d;
                final int[] n = {0};
                e.addListener(new GameListenerAdapter() {
                    @Override public void onCustomerArrived(Order o) {
                        for (OrderLine l : o.getLines()) {
                            for (String id : l.getPool()) assertTrue(day + " havuz " + id, Catalog.ingredient(id).isOpen(day));
                            for (String id : l.getDefaults()) assertTrue(day + " varsayılan " + id, Catalog.ingredient(id).isOpen(day));
                            for (String id : l.getTarget()) assertTrue(day + " hedef " + id, Catalog.ingredient(id).isOpen(day));
                        }
                        for (Request q : o.requests) {
                            n[0]++;
                            String ing = REQUEST_INGREDIENT.get(q.text);
                            if (ing != null) assertTrue(day + " istek " + q.text, Catalog.ingredient(ing).isOpen(day));
                            assertTrue(day + " sos istekleri ketçap/mayonez açılmadan olmaz: " + q.text,
                                    !(q.text.equals("sosuz") || q.text.equals("bol soslu")) || day >= 3);
                        }
                    }
                });
                for (int i = 0; i < 2500 && e.isRunning(); i++) e.tick(0.1f);
                ordersWithRequests += n[0];
            }
            if (d >= 5) assertTrue("gün " + d + " istekler gelmeli", ordersWithRequests > 0);
        }
    }

    @Test public void spicyAndPickleRequestsAppearOnlyAfterTheyUnlock() {
        boolean spicyDay8 = false;
        for (long seed = 1; seed <= 12; seed++) {
            for (int d : new int[]{2, 4, 6, 8}) {
                SaveData s = day(d);
                s.stars = 5.0;
                GameEngine e = new GameEngine(seed, s);
                final int day = d;
                final boolean[] spicy = {false};
                e.addListener(new GameListenerAdapter() {
                    @Override public void onCustomerArrived(Order o) {
                        for (Request q : o.requests) {
                            if (q.text.equals("acılı") || q.text.equals("acısız")) spicy[0] = true;
                            if (q.text.equals("turşusuz")) assertTrue("turşu 7. gün", day >= 7);
                        }
                    }
                });
                for (int i = 0; i < 2500 && e.isRunning(); i++) e.tick(0.1f);
                if (d < 5) assertFalse("acı sos 5. günden önce istenmez (gün " + d + ")", spicy[0]);
                if (d == 8 && spicy[0]) spicyDay8 = true;
            }
        }
        assertTrue("8. günde acılı istekler çıkmalı", spicyDay8);
    }

    @Test public void toppingOptionsOfTheEngineListOnlyOpenIngredients() {
        for (int d : new int[]{2, 4, 5, 7, 11}) {
            GameEngine e = new GameEngine(1, day(d));
            e.setSpawning(false);
            Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), Collections.<String>emptyList());
            for (ToppingOption op : e.getToppingOptions(o.id, 0)) assertTrue(d + " " + op.ingredient.id, op.ingredient.isOpen(d));
            boolean hasAci = false;
            for (ToppingOption op : e.getToppingOptions(o.id, 0)) if (op.ingredient.id.equals("aci_sos")) hasAci = true;
            assertEquals("acı sos 5. günde", d >= 5, hasAci);
        }
    }

    // ------------------------------------------------------------ market ve öneri
    @Test public void marketSellsOnlyOpenIngredients() {
        SaveData s = SaveData.newGame();
        s.money = 1000;
        assertEquals(Shop.BuyResult.LOCKED, Shop.buyIngredient(s, "ketcap", 5));
        assertEquals(1000, s.money);
        assertEquals(0, s.inventory.get("ketcap"));
        assertEquals(Shop.BuyResult.OK, Shop.buyIngredient(s, "domates", 5));
        s.day = 3;
        assertEquals(Shop.BuyResult.OK, Shop.buyIngredient(s, "ketcap", 5));
        assertEquals(Shop.BuyResult.LOCKED, Shop.buyIngredient(s, "aci_sos", 5));
        assertEquals(Shop.BuyResult.UNKNOWN, Shop.buyIngredient(s, "yok", 5));
        s.day = 1;
        for (Ingredient i : Shop.availableIngredients(s)) assertTrue(i.id, i.isOpen(1));
        assertFalse(ids(Shop.availableIngredients(s)).contains("ketcap"));
        s.day = 3;
        assertTrue(ids(Shop.availableIngredients(s)).contains("ketcap"));
        assertFalse(ids(Shop.availableIngredients(s)).contains("aci_sos"));
    }

    @Test public void recommendationOnlyIncludesOpenIngredients() {
        for (int d = 1; d <= 20; d++) {
            SaveData s = SaveData.newGame();
            s.day = d;
            s.inventory = new Inventory();
            for (String id : Shop.recommendedPurchase(s).keySet()) {
                assertTrue("gün " + d + ": " + id, Catalog.ingredient(id).isOpen(d));
            }
        }
        SaveData s1 = SaveData.newGame();
        assertFalse("yeni oyunda ketçap yok", s1.inventory.get("ketcap") > 0);
        assertTrue(s1.inventory.get("domates") > 0);
        SaveData s3 = SaveData.newGame();
        s3.day = 3;
        s3.inventory = new Inventory();
        assertTrue(Shop.recommendedPurchase(s3).containsKey("ketcap"));
        assertTrue(Shop.recommendedPurchase(s3).containsKey("mayonez"));
        assertFalse(Shop.recommendedPurchase(s3).containsKey("aci_sos"));
    }

    @Test public void trayChoicesAndTrayPrepRespectUnlocks() {
        GameEngine e = new GameEngine(1, day(2));
        List<String> choices = ids(e.getPrepTrayChoices());
        assertTrue(choices.contains("domates"));
        assertFalse(choices.contains("ketcap"));
        assertEquals(PrepResult.INVALID, e.startPrepTray(Arrays.asList("domates", "ketcap")));
        assertEquals(PrepResult.STARTED, e.startPrepTray(Arrays.asList("domates", "marul")));
        GameEngine e5 = new GameEngine(1, day(5));
        assertTrue(ids(e5.getPrepTrayChoices()).contains("aci_sos"));
    }

    // ------------------------------------------------------------ duyuru
    @Test public void newIngredientAnnouncementTexts() {
        assertEquals("", Narrator.newIngredientsText(1));
        assertEquals("", Narrator.newIngredientsText(2));
        assertEquals("Yeni malzemeler: ketçap ve mayonez.", Narrator.newIngredientsText(3));
        assertEquals("Yeni malzeme: acı sos.", Narrator.newIngredientsText(5));
        assertEquals("Yeni malzeme: nar ekşisi.", Narrator.newIngredientsText(9));
        assertEquals("Yeni malzemeler: yeşil biber ve sumak.", Narrator.newIngredientsText(11));
        assertEquals("Yeni malzeme: sarımsaklı yoğurt sos.", Narrator.newIngredientsText(13));
        assertEquals("Yeni malzeme: közlenmiş biber ve domates.", Narrator.newIngredientsText(15));
        GameEngine e = new GameEngine(1, day(3));
        Narrator n = new Narrator(e);
        String t = n.dayStarted(3, DayConfig.forDay(3));
        assertTrue(t, t.contains("Yeni malzemeler: ketçap ve mayonez."));
        assertTrue(t, t.contains("Yeni ürün: Pilav üstü döner."));
        assertFalse(n.dayStarted(4, DayConfig.forDay(4)).contains("Yeni malzeme"));
        assertTrue(Texts.briefingLines(day(3)).contains("Yeni malzemeler: ketçap ve mayonez."));
        assertTrue(Texts.briefingLines(day(5)).contains("Yeni malzeme: acı sos."));
    }

    @Test public void dayEndSummaryAnnouncesTomorrowsNewIngredients() {
        GameEngine e = new GameEngine(1, day(2));
        e.setSpawning(false);
        e.tick(1000f);
        DaySummary sum = e.getSummary();
        assertEquals("ketçap, mayonez", sum.newIngredientsNext);
        assertTrue(new Narrator(e).dayFinished(sum).contains("Yarın yeni malzeme: ketçap, mayonez."));
        GameEngine e4 = new GameEngine(1, day(4));
        e4.setSpawning(false);
        e4.tick(1000f);
        assertEquals("acı sos", e4.getSummary().newIngredientsNext);
        GameEngine e5 = new GameEngine(1, day(5));
        e5.setSpawning(false);
        e5.tick(1000f);
        assertEquals("", e5.getSummary().newIngredientsNext);
    }
}
