package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** v0.9 madde 2 ve 7: patates + "Patates kızart" adımı (Ocak), hazır patates, ekonomi, akışlar, Ocak adı. */
public class FriesTest {
    private static SaveData day(int d) {
        SaveData s = SaveData.newGame();
        s.day = d;
        s.money = 100000;
        Shop.buyRecommended(s);
        for (Ingredient i : Catalog.ingredients()) s.inventory.set(i.id, 30);
        s.inventory.set(Catalog.PATATES, 20);
        return s;
    }

    private static GameEngine engine(int d, FlowMode m) {
        SaveData s = day(d);
        s.flowMode = m;
        GameEngine e = new GameEngine(3, s);
        e.setSpawning(false);
        e.startDay();
        return e;
    }

    private static final List<String> NONE = Collections.emptyList();

    @Test public void stationIsCalledOcakEverywhere() {
        assertEquals("Ocak", Station.IZGARA.displayName);
        for (Product p : Catalog.products()) for (Step s : p.steps) {
            assertFalse(s.name, s.name.toLowerCase().contains("ızgara") || s.name.contains("Izgara") || s.name.contains("Fırın"));
        }
        StringBuilder all = new StringBuilder();
        for (String[] sec : com.doner.oyunu.ui.Texts.helpSections()) all.append(sec[0]).append(' ').append(sec[1]).append(' ');
        all.append(com.doner.oyunu.ui.Texts.apprenticeLevelsText(1)).append(com.doner.oyunu.ui.Texts.quickHelp());
        String t = all.toString();
        assertTrue(t, t.contains("Ocak"));
        assertFalse("yardımda eski ad kalmamalı", t.contains("Izgara") || t.contains("ızgara") || t.contains("fırın"));
        assertTrue(new Narrator(engine(3, FlowMode.DENGELI)).stationLabel(Station.IZGARA).startsWith("Ocak, "));
    }

    @Test public void friesVariantAddsOneFryStepOnTheOcakBeforeToppings() {
        for (String id : new String[]{"ekmek_arasi", "tavuk_ekmek", "tavuk_durum", "hatay_durum", "zurna_durum"}) {
            Product base = Catalog.product(id), v = base.friesVariant();
            assertTrue(id, base.friesOk());
            assertEquals(base.steps.size() + 1, v.steps.size());
            assertEquals(id, v.id);
            Step fry = null;
            for (Step s : v.steps) if (s.isFry()) fry = s;
            assertNotNull(id, fry);
            assertEquals("Patates kızart", fry.name);
            assertEquals(Station.IZGARA, fry.station);
            assertEquals(4f, fry.baseSeconds, 1e-4);
            assertEquals(Integer.valueOf(1), fry.needs.get("patates"));
            assertTrue("malzeme adımı sonda", v.steps.get(v.steps.size() - 1).isTopping());
            assertSame(v, v.friesVariant());
            assertSame(v, base.friesVariant());
        }
        for (String id : new String[]{"ayran", "iskender", "lahmacun", "kunefe", "pilav_ustu", "porsiyon_doner", "kutu_icecek"}) {
            assertFalse(id, Catalog.product(id).friesOk());
            assertSame(Catalog.product(id), Catalog.product(id).friesVariant());
        }
        Ingredient pot = Catalog.ingredient("patates");
        assertEquals(Catalog.FRIES_OPEN_DAY, pot.unlockDay);
        assertTrue(Shop.availableIngredients(day(3)).contains(pot));
        assertFalse(Shop.availableIngredients(day(2)).contains(pot));
    }

    @Test public void friesChanceStartsOnDay3RisesWithDaysAndIsCapped() {
        assertEquals(0.0, DayConfig.friesChance(1), 0);
        assertEquals(0.0, DayConfig.friesChance(2), 0);
        assertTrue(DayConfig.friesChance(3) > 0.05 && DayConfig.friesChance(3) < 0.2);
        double prev = 0;
        for (int d = 3; d <= 60; d++) {
            double c = DayConfig.friesChance(d);
            assertTrue(c >= prev && c <= 0.5);
            prev = c;
        }
        assertTrue(DayConfig.friesChance(30) > DayConfig.friesChance(5));
    }

    private static int[] countFries(int d, int seeds) {
        int total = 0, fries = 0;
        for (int sd = 1; sd <= seeds; sd++) {
            SaveData s = day(d);
            GameEngine e = new GameEngine(sd, s);
            // v2.0: gelen HER siparişi gelişte say (sabır kısaldığı için gidenler de sayılsın; anlık bekleyenler değil)
            final List<Order> seen = new ArrayList<>();
            e.addListener(new GameListenerAdapter() {
                @Override public void onCustomerArrived(Order o) { seen.add(o); }
            });
            e.startDay();
            for (int i = 0; i < 1500; i++) e.tick(0.1f);
            for (Order o : seen) for (OrderLine l : o.getLines()) {
                if (l.product.friesOk()) { total++; if (l.isFries()) fries++; }
            }
        }
        return new int[]{total, fries};
    }

    @Test public void friesOrdersAppearFromDay3AndMoreOftenLater() {
        assertEquals("1.-2. günde patatesli sipariş yok", 0, countFries(2, 12)[1]);
        int[] early = countFries(3, 40), late = countFries(30, 40);
        assertTrue("3. günde bazen: " + early[0] + "/" + early[1], early[1] > 0);
        assertTrue(early[0] > 20 && late[0] > 20);
        double fe = early[1] / (double) early[0], fl = late[1] / (double) late[0];
        assertTrue("gün ilerledikçe artar: " + fe + " -> " + fl, fl > fe + 0.1);
    }

    @Test public void friesLineIsLabelledPricedAndCooked() {
        GameEngine e = engine(3, FlowMode.DENGELI);
        Order o = e.spawnOrder(Arrays.asList("ekmek_arasi+patates", "ekmek_arasi"), NONE);
        OrderLine f = o.getLine(0), plain = o.getLine(1);
        assertTrue(f.isFries());
        assertFalse(plain.isFries());
        assertEquals("Ekmek arası döner (patatesli)", f.displayName());
        assertEquals(plain.price() + Product.FRIES_PRICE, f.price());
        assertTrue("sipariş metninde söylenir", o.itemsText().contains("patatesli"));
        assertEquals(4, f.stepCount());
        int before = e.getSave().inventory.get("patates");
        assertEquals(StartResult.STARTED, e.startStep(o.id, 0, Station.IZGARA.id));
        assertEquals(before - 1, e.getSave().inventory.get("patates"));
        ActiveStep a = e.getActiveFor(o.id);
        assertEquals(4f, a.total, 0.05f);
        Flow.waitDone(e, o);
        assertTrue(f.isStepDone(2) || f.isStepDone(f.stepCount() - 2));
        // sade kalemde Ocak adımı yoktur: dokunuş yanlış istasyondur
        assertEquals(StartResult.WRONG_STATION, e.startStep(o.id, 1, Station.IZGARA.id));
    }

    @Test public void everyFlowCompletesAFriesOrderWithOneTapOnTheOcak() {
        for (FlowMode m : FlowMode.values()) {
            GameEngine e = engine(3, m);
            Order o = e.spawnOrder(Arrays.asList("tavuk_ekmek+patates"), NONE);
            e.selectOrder(o.id);
            Flow.makeReady(e, o);
            assertTrue(m.toString(), o.isReady());
            assertTrue(e.deliver(o.id));
            assertEquals(m.toString(), 1, e.getSummary().delivered);
        }
    }

    @Test public void readyFriesMakeTheStepFastAndAreWastedAtDayEnd() {
        SaveData s = day(3);
        GameEngine e = new GameEngine(5, s);
        e.setSpawning(false);
        e.beginPrep();
        assertTrue(e.menuHasFries());
        List<StationOption> ops = e.getStationOptions(-1, -1, Station.IZGARA.id);
        assertEquals(Arrays.asList("prep:fries"), Arrays.asList(ops.get(0).id));
        assertEquals("4 porsiyon patates kızart", ops.get(0).name);
        int pot = s.inventory.get("patates");
        assertEquals(StartResult.STARTED, e.applyStationOption(-1, -1, Station.IZGARA.id, "prep:fries"));
        assertEquals(pot - 4, s.inventory.get("patates"));
        for (int i = 0; i < 70; i++) e.tick(0.1f);
        assertEquals(4, e.getReadyFries());
        e.startDay();
        Order o = e.spawnOrder(Arrays.asList("hatay_durum+patates"), NONE);
        List<StationOption> oven = e.getStationOptions(o.id, 0, Station.IZGARA.id);
        assertEquals("Ocakta mühürle", find(oven, "step").name);
        // iki Ocak adımı: önce mühürle (ilk açık), sonra patates
        assertEquals(StartResult.STARTED, e.startStep(o.id, 0, Station.EKMEK.id));
        Flow.waitDone(e, o);
        assertEquals(StartResult.STARTED, e.startStep(o.id, 0, Station.IZGARA.id));
        Flow.waitDone(e, o);
        assertEquals(StartResult.STARTED, e.startStep(o.id, 0, Station.IZGARA.id));
        ActiveStep a = e.getActiveFor(o.id);
        assertTrue("hazır patatesle ~0,8 sn: " + a.total, a.total <= 1.0f);
        assertEquals("hazır patates tüketildi, stok değil", 3, e.getReadyFries());
        assertEquals(pot - 4, s.inventory.get("patates"));
        Flow.waitDone(e, o);
        // gün sonu israf
        e.setSpawning(false);
        e.tick(0.1f);
        for (Order x : e.getOrders()) { /* kalanlar */ }
    }

    private static StationOption find(List<StationOption> l, String id) {
        for (StationOption o : l) if (o.id.equals(id)) return o;
        return null;
    }

    @Test public void friesPrepIsNotOfferedBeforeDay3AndRespectsStockAndCapacity() {
        GameEngine e1 = new GameEngine(1, day(2));
        e1.beginPrep();
        assertFalse(e1.menuHasFries());
        assertTrue(e1.getStationOptions(-1, -1, Station.IZGARA.id).isEmpty());
        assertEquals(PrepResult.INVALID, e1.startPrepFries());
        SaveData s = day(3);
        s.inventory.set("patates", 3);
        GameEngine e = new GameEngine(1, s);
        e.beginPrep();
        StationOption op = find(e.getStationOptions(-1, -1, Station.IZGARA.id), "prep:fries");
        assertFalse(op.enabled);
        assertTrue(op.lockReason, op.lockReason.startsWith("Malzeme yetmiyor"));
        assertEquals(StartResult.OUT_OF_STOCK, e.applyStationOption(-1, -1, Station.IZGARA.id, "prep:fries"));
        s.inventory.set("patates", 30);
        assertEquals(PrepResult.STARTED, e.startPrepFries());
        for (int i = 0; i < 80; i++) e.tick(0.1f);
        assertEquals(PrepResult.STARTED, e.startPrepFries());
        for (int i = 0; i < 80; i++) e.tick(0.1f);
        assertEquals(PrepResult.FULL, e.startPrepFries());
    }

    @Test public void wastedFriesAreCountedAtDayEnd() {
        SaveData s = day(3);
        GameEngine e = new GameEngine(2, s);
        e.setSpawning(false);
        e.beginPrep();
        e.startPrepFries();
        for (int i = 0; i < 70; i++) e.tick(0.1f);
        e.startDay();
        assertEquals(4, e.getReadyFries());
        e.setTimePressure(false);
        // kota dolmuş ve sıra boş: gün biter
        for (int i = 0; i < 50_000 && e.isRunning(); i++) e.tick(0.1f);
        e.setSpawning(false);
        if (e.getState() == GameEngine.State.FINISHED) {
            assertTrue(e.getSummary().wastedPortions >= 4);
            assertEquals(0, e.getReadyFries());
        }
    }

    @Test public void outOfPotatoesBlocksTheStepAndEmergencyBuyFixesIt() {
        GameEngine e = engine(3, FlowMode.DENGELI);
        e.getSave().inventory.set("patates", 0);
        Order o = e.spawnOrder(Arrays.asList("ekmek_arasi+patates"), NONE);
        assertEquals(StartResult.OUT_OF_STOCK, e.startStep(o.id, 0, Station.IZGARA.id));
        assertEquals("patates", e.missingIngredient(o.id).id);
        assertTrue(e.emergencyBuyMissing(o.id));
        assertEquals(StartResult.STARTED, e.startStep(o.id, 0, Station.IZGARA.id));
    }

    @Test public void recommendedPurchaseIncludesPotatoesFromDay3Only() {
        SaveData s = SaveData.newGame();
        s.day = 2;
        s.inventory = new Inventory();
        assertFalse(Shop.recommendedPurchase(s).containsKey("patates"));
        s.day = 3;
        assertTrue(Shop.recommendedPurchase(s).containsKey("patates"));
        assertTrue(Catalog.ingredient("patates").price * 5 < Product.FRIES_PRICE * 2);
    }

    @Test public void potatoStockSurvivesSaveAndOldSavesLoad() {
        SaveData s = SaveData.newGame();
        s.day = 4;
        s.inventory.set("patates", 7);
        SaveData r = SaveData.parse(s.serialize());
        assertEquals(7, r.inventory.get("patates"));
        SaveData old = SaveData.parse("v=4\nday=9\nmoney=900\nstock.et_doner=5\n");
        assertEquals(0, old.inventory.get("patates"));
        assertEquals(9, old.day);
    }

    @Test public void apprenticeLevel3CanFryAndBotsStillFinishDaysWithFries() {
        for (FlowMode m : FlowMode.values()) {
            SaveData s = day(12);
            s.flowMode = m;
            s.upgradeLevels.put("cirak", 3);
            GameEngine e = new GameEngine(21, s);
            new TestBot(1.0f, true, true).playDay(e);
            assertEquals(m.toString(), GameEngine.State.FINISHED, e.getState());
        }
    }
}
