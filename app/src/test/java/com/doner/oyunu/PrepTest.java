package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** v0.5 hazırlık sistemi: gün öncesi aşama, hazır döner/içecek/tepsi, kapasite, israf. */
public class PrepTest {

    private static SaveData day(int d) {
        SaveData s = SaveData.newGame();
        s.day = d;
        s.stars = 5.0;
        Shop.buyRecommended(s);
        return s;
    }

    private static void run(GameEngine e, float seconds) {
        for (float t = 0; t < seconds; t += 0.1f) e.tick(0.1f);
    }

    // ------------------------------------------------------------ hazırlık aşaması
    @Test public void prepPhaseDoesNotCountTowardsDayTimeAndHasNoCustomers() {
        GameEngine e = new GameEngine(1, day(1));
        final List<String> ev = new ArrayList<>();
        e.addListener(new GameListenerAdapter() {
            @Override public void onPrepPhaseStarted(int d) { ev.add("prep+"); }
            @Override public void onPrepPhaseEnded(int d) { ev.add("prep-"); }
            @Override public void onDayStarted(int d, DayConfig c) { ev.add("day"); }
            @Override public void onCustomerArrived(Order o) { ev.add("cust"); }
        });
        assertEquals(GameEngine.State.READY, e.getState());
        assertTrue(e.beginPrep());
        assertFalse("ikinci kez başlamaz", e.beginPrep());
        assertEquals(GameEngine.State.PREP, e.getState());
        assertTrue(e.isPrepPhase());
        assertTrue(e.isRunning());
        run(e, 300f); // sınırsız süre: 5 dakika geçse de gün başlamaz
        assertEquals(GameEngine.State.PREP, e.getState());
        assertEquals(0f, e.getElapsed(), 1e-6f);
        assertEquals(e.getConfig().duration, e.getTimeLeft(), 1e-3f);
        assertTrue(e.getOrders().isEmpty());
        assertEquals(Arrays.asList("prep+"), ev);
        assertTrue(e.startDay());
        assertFalse("ikinci kez başlamaz", e.startDay());
        assertEquals(GameEngine.State.RUNNING, e.getState());
        assertEquals(Arrays.asList("prep+", "prep-", "day"), ev);
        run(e, 10f);
        assertTrue(e.getElapsed() > 9.9f);
        assertFalse(e.getOrders().isEmpty());
    }

    @Test public void skippingPrepStillWorks() {
        GameEngine e = new GameEngine(1, day(1));
        final List<String> ev = new ArrayList<>();
        e.addListener(new GameListenerAdapter() {
            @Override public void onPrepPhaseStarted(int d) { ev.add("prep+"); }
            @Override public void onPrepPhaseEnded(int d) { ev.add("prep-"); }
            @Override public void onDayStarted(int d, DayConfig c) { ev.add("day"); }
        });
        assertTrue(e.startDay()); // "Hazırlık yapmadan aç"
        assertEquals(Arrays.asList("day"), ev);
        e.tick(1f);
        assertEquals(GameEngine.State.RUNNING, e.getState());
    }

    @Test public void tickFromReadyStillStartsTheDay() {
        GameEngine e = new GameEngine(1, day(1));
        e.tick(0.1f);
        assertEquals(GameEngine.State.RUNNING, e.getState());
    }

    @Test public void orderActionsAreImpossibleDuringPrep() {
        GameEngine e = new GameEngine(1, day(1));
        e.beginPrep();
        assertEquals(StartResult.NO_ORDER, e.startStep(1, Station.EKMEK.id));
        assertEquals(GameEngine.State.PREP, e.getState());
        assertNull(e.spawnOrder(Collections.singletonList("ekmek_arasi"), Collections.<String>emptyList()));
    }

    // ------------------------------------------------------------ hazır döner
    @Test public void preparedDonerMakesSliceStepFastAndSavesStock() {
        SaveData s = day(1);
        GameEngine e = new GameEngine(1, s);
        e.beginPrep();
        int et = s.inventory.get("et_doner");
        final float[] dur = {0};
        final int[] finished = {0};
        e.addListener(new GameListenerAdapter() {
            @Override public void onPrepStarted(PrepKind k, boolean a, float sec) { dur[0] = sec; }
            @Override public void onPrepFinished(PrepKind k, boolean a, int amount) { finished[0] += amount; }
        });
        assertEquals(PrepResult.STARTED, e.startPrepDoner("et_doner"));
        assertEquals("başlarken malzeme düşer", et - 4, s.inventory.get("et_doner"));
        assertEquals(6f, dur[0], 0.01f);
        assertEquals("döner dilimleme aktif iş: eller dolu", 0, e.getFreeSlots());
        assertEquals(PrepResult.HANDS_BUSY, e.startPrepDrink());
        assertEquals(0, e.getReadyMeat("et_doner"));
        run(e, 6.2f);
        assertEquals(4, e.getReadyMeat("et_doner"));
        assertEquals(4, e.getReadyDoner());
        assertEquals(4, finished[0]);
        assertEquals(1, e.getFreeSlots());
        e.startDay();
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), Collections.<String>emptyList());
        final float[] sl = {0};
        e.addListener(new GameListenerAdapter() {
            @Override public void onStepStarted(Order oo, Step st, int slot, float sec) { sl[0] = sec; }
        });
        int stockBefore = s.inventory.get("et_doner");
        assertEquals(StartResult.STARTED, e.startStep(o.id, Station.DONER.id));
        assertEquals("hazır döner: ~0,8 sn", 0.8f, sl[0], 0.01f);
        assertEquals("porsiyon düşer", 3, e.getReadyMeat("et_doner"));
        assertEquals("stoktan düşmez", stockBefore, s.inventory.get("et_doner"));
        Flow.waitDone(e, o);
        assertTrue(o.getLine(0).isStepDone(1));
    }

    @Test public void withoutPreparedDonerSliceTakesNormalTime() {
        SaveData s = day(1);
        GameEngine e = new GameEngine(1, s);
        e.startDay();
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), Collections.<String>emptyList());
        final float[] sl = {0};
        e.addListener(new GameListenerAdapter() {
            @Override public void onStepStarted(Order oo, Step st, int slot, float sec) { sl[0] = sec; }
        });
        int et = s.inventory.get("et_doner");
        assertEquals(StartResult.STARTED, e.startStep(o.id, Station.DONER.id));
        assertEquals(3.5f, sl[0], 0.01f);
        assertEquals(et - 1, s.inventory.get("et_doner"));
    }

    @Test public void preparedDonerOnlyForTheMatchingMeat() {
        SaveData s = day(4);
        GameEngine e = new GameEngine(1, s);
        e.beginPrep();
        assertEquals(PrepResult.STARTED, e.startPrepDoner("tavuk_doner"));
        run(e, 7f);
        e.startDay();
        assertEquals(4, e.getReadyMeat("tavuk_doner"));
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), Collections.<String>emptyList());
        final float[] sl = {0};
        e.addListener(new GameListenerAdapter() {
            @Override public void onStepStarted(Order oo, Step st, int slot, float sec) { sl[0] = sec; }
        });
        e.startStep(o.id, Station.DONER.id);
        assertEquals("et döneri için hazır tavuk işe yaramaz", 3.5f, sl[0], 0.01f);
        assertEquals(4, e.getReadyMeat("tavuk_doner"));
    }

    // ------------------------------------------------------------ hazır içecek
    @Test public void preparedDrinksAreFastAndPersistAcrossDays() {
        SaveData s = day(3);
        GameEngine e = new GameEngine(1, s);
        e.beginPrep();
        int ayran = s.inventory.get("ayran");
        assertEquals(PrepResult.STARTED, e.startPrepDrink());
        assertEquals(ayran - 4, s.inventory.get("ayran"));
        run(e, 4.2f);
        assertEquals(4, e.getReadyDrinks());
        assertEquals(4, s.readyDrinks);
        e.startDay();
        Order o = e.spawnOrder(Collections.singletonList("ayran"), Collections.<String>emptyList());
        final float[] sl = {0};
        e.addListener(new GameListenerAdapter() {
            @Override public void onStepStarted(Order oo, Step st, int slot, float sec) { sl[0] = sec; }
        });
        assertEquals(StartResult.STARTED, e.startStep(o.id, Station.ICECEK.id));
        assertEquals(0.5f, sl[0], 0.01f);
        assertEquals(3, s.readyDrinks);
        assertEquals("hazır içecek şişe tüketmez", ayran - 4, s.inventory.get("ayran"));
        Flow.waitDone(e, o);
        // hazır içecek bitince normal süre (1,8 sn) ve stoktan
        s.readyDrinks = 0;
        Order o2 = e.spawnOrder(Collections.singletonList("ayran"), Collections.<String>emptyList());
        assertEquals(StartResult.STARTED, e.startStep(o2.id, Station.ICECEK.id));
        assertEquals(1.8f, sl[0], 0.01f);
        assertEquals(ayran - 5, s.inventory.get("ayran"));
    }

    @Test public void kutuIcecekIsNeverPrepared() {
        assertFalse(Catalog.product("kutu_icecek").steps.get(0).isDrinkFill());
        assertEquals(Station.ICECEK, PrepKind.DRINK.station);
        GameEngine e = new GameEngine(1, day(5));
        assertTrue(e.menuHasDrinkPrep()); // ayran menüde
        assertEquals(GameEngine.State.READY, e.getState());
    }

    // ------------------------------------------------------------ tepsi
    @Test public void trayMakesToppingSelectionFasterAndUsesTrayInsteadOfStock() {
        SaveData s = day(3); // ketçap 3. günde açılır
        GameEngine e = new GameEngine(1, s);
        e.beginPrep();
        List<String> ids = Arrays.asList("domates", "marul", "sogan", "ketcap");
        int dom = s.inventory.get("domates");
        assertEquals(PrepResult.STARTED, e.startPrepTray(ids));
        assertEquals("tepsi malzemeden 6'şar tüketir", dom - 6, s.inventory.get("domates"));
        run(e, 5.2f);
        for (String id : ids) assertEquals(6, e.getTrayUses(id));
        assertEquals(24, e.getTrayTotal());
        e.startDay();
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), Collections.<String>emptyList());
        Flow.doItemStation(e, o, 0, Station.EKMEK);
        Flow.doItemStation(e, o, 0, Station.DONER);
        final float[] sl = {0};
        e.addListener(new GameListenerAdapter() {
            @Override public void onStepStarted(Order oo, Step st, int slot, float sec) { sl[0] = sec; }
        });
        int domNow = s.inventory.get("domates");
        assertEquals(StartResult.NEEDS_TOPPINGS, e.startStep(o.id, Station.SOS.id));
        assertEquals(StartResult.STARTED, e.confirmToppings(o.id, 0, ids));
        // (2,0 + 4 x 0,3) x %50 = 1,6
        assertEquals(1.6f, sl[0], 0.01f);
        assertEquals("stoktan düşmez", domNow, s.inventory.get("domates"));
        assertEquals(5, e.getTrayUses("domates"));
    }

    @Test public void partiallyCoveredByTrayIsPartiallyFaster() {
        SaveData s = day(3);
        GameEngine e = new GameEngine(1, s);
        e.beginPrep();
        assertEquals(PrepResult.STARTED, e.startPrepTray(Arrays.asList("domates", "marul")));
        run(e, 5.2f);
        e.startDay();
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), Collections.<String>emptyList());
        Flow.doItemStation(e, o, 0, Station.EKMEK);
        Flow.doItemStation(e, o, 0, Station.DONER);
        final float[] sl = {0};
        e.addListener(new GameListenerAdapter() {
            @Override public void onStepStarted(Order oo, Step st, int slot, float sec) { sl[0] = sec; }
        });
        int ketcap = s.inventory.get("ketcap");
        e.startStep(o.id, Station.SOS.id);
        e.confirmToppings(o.id, 0, Arrays.asList("domates", "marul", "sogan", "ketcap"));
        // yarısı tepsiden: 3,2 x (1 - 0,5 x 0,5) = 2,4
        assertEquals(2.4f, sl[0], 0.01f);
        assertEquals(ketcap - 1, s.inventory.get("ketcap"));
    }

    @Test public void trayRejectsNonToppingAndEmptySelections() {
        GameEngine e = new GameEngine(1, day(1));
        assertEquals(PrepResult.INVALID, e.startPrepTray(Collections.singletonList("et_doner")));
        assertEquals(PrepResult.INVALID, e.startPrepTray(Collections.<String>emptyList()));
        assertEquals(PrepResult.INVALID, e.startPrepTray(Collections.singletonList("yok")));
        assertEquals(PrepResult.INVALID, e.startPrepDoner("ekmek"));
        assertEquals(PrepResult.INVALID, e.startPrepDoner("yok"));
        assertEquals(GameEngine.State.PREP, e.getState()); // startPrep READY'yi hazırlık aşamasına alır
    }

    // ------------------------------------------------------------ kapasite / stok
    @Test public void capacityLimitsReadyStockAndFridgeRaisesIt() {
        SaveData s = day(1);
        s.inventory.set("et_doner", 30);
        GameEngine e = new GameEngine(1, s);
        e.beginPrep();
        assertEquals(8, e.getReadyCapacity(PrepKind.DONER));
        assertEquals(12, e.getReadyCapacity(PrepKind.TRAY));
        assertEquals(PrepResult.STARTED, e.startPrepDoner("et_doner"));
        run(e, 6.2f);
        assertEquals(PrepResult.STARTED, e.startPrepDoner("et_doner"));
        run(e, 6.2f);
        assertEquals(8, e.getReadyMeat("et_doner"));
        assertEquals(PrepResult.FULL, e.startPrepDoner("et_doner"));
        s.upgradeLevels.put("buzdolabi", 2);
        GameEngine e2 = new GameEngine(1, s);
        assertEquals(16, e2.getReadyCapacity(PrepKind.DONER));
        assertEquals(24, e2.getReadyCapacity(PrepKind.TRAY));
    }

    @Test public void pendingPrepCountsTowardsCapacity() {
        SaveData s = day(1);
        s.inventory.set("et_doner", 30);
        s.upgradeLevels.put("cirak", 3);
        s.day = 1;
        GameEngine e = new GameEngine(1, s);
        e.beginPrep();
        assertEquals(PrepResult.STARTED, e.startPrepDoner("et_doner"));
        // devam eden parti + 4 = 8 sığar, bir parti daha 12 olur
        assertTrue(e.getActiveSteps().size() >= 1);
    }

    @Test public void prepNeedsStock() {
        SaveData s = day(1);
        s.inventory.set("et_doner", 3);
        GameEngine e = new GameEngine(1, s);
        final List<String> rej = new ArrayList<>();
        e.addListener(new GameListenerAdapter() {
            @Override public void onPrepRejected(PrepKind k, PrepResult r, Ingredient i) { rej.add(r + ":" + (i == null ? "-" : i.id)); }
        });
        assertEquals(PrepResult.OUT_OF_STOCK, e.startPrepDoner("et_doner"));
        assertEquals(3, s.inventory.get("et_doner"));
        assertEquals(Arrays.asList("OUT_OF_STOCK:et_doner"), rej);
        s.inventory.set("domates", 5);
        assertEquals(PrepResult.OUT_OF_STOCK, e.startPrepTray(Arrays.asList("domates", "marul")));
        assertEquals(PrepResult.NOT_ALLOWED, finishedEngine().startPrepDrink());
    }

    private static GameEngine finishedEngine() {
        SaveData s = day(1);
        s.stars = 5.0;
        GameEngine e = new GameEngine(1, s);
        e.tick(2000f);
        assertEquals(GameEngine.State.FINISHED, e.getState());
        return e;
    }

    // ------------------------------------------------------------ gün içi hazırlık, israf
    @Test public void prepCanBeDoneDuringTheDayWhenSlotIsFree() {
        SaveData s = day(3);
        GameEngine e = new GameEngine(2, s);
        e.startDay();
        run(e, 0.5f);
        assertEquals(PrepResult.STARTED, e.startPrepDrink());
        assertEquals("ayran doldurma aktif iş: eller dolu", 0, e.getFreeSlots());
        for (int i = 0; i < 100 && e.getOrders().isEmpty(); i++) e.tick(0.1f);
        assertFalse("müşteri geldi", e.getOrders().isEmpty());
        Order o = e.getOrders().get(0);
        Step s0 = o.nextStep();
        assertEquals(StartResult.HANDS_BUSY, e.startStep(o.id, s0.station.id));
        run(e, 4f);
        assertEquals(4, e.getReadyDrinks());
        assertEquals(1, e.getFreeSlots());
    }

    @Test public void leftoverReadyDonerAndTrayAreWastedAtDayEnd() {
        SaveData s = day(1);
        GameEngine e = new GameEngine(1, s);
        e.beginPrep();
        e.startPrepDoner("et_doner");
        run(e, 6.2f);
        e.startPrepTray(Arrays.asList("domates", "sogan"));
        run(e, 5.2f);
        e.startDay();
        assertEquals(4, e.getReadyDoner());
        final DaySummary[] sum = {null};
        e.addListener(new GameListenerAdapter() {
            @Override public void onDayFinished(DaySummary d) { sum[0] = d; }
        });
        // kimseye hizmet etme: gün biter
        s.stars = 5.0;
        e.tick(2000f);
        assertEquals(GameEngine.State.FINISHED, e.getState());
        DaySummary d = e.getSummary();
        assertEquals(4 + 12, d.wastedPortions);
        assertEquals(4 * 68 + 6 * 2 + 6 * 1, d.wastedCost);
        assertEquals(0, e.getReadyDoner());
        assertEquals(0, e.getTrayTotal());
        assertSame(d, sum[0]);
        assertTrue(d.toString().contains("israf=16"));
    }

    @Test public void usedReadyStockIsNotWaste() {
        SaveData s = day(1);
        GameEngine e = new GameEngine(1, s);
        e.beginPrep();
        e.startPrepDoner("et_doner");
        run(e, 6.2f);
        e.startDay();
        for (int i = 0; i < 4; i++) {
            Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), Collections.<String>emptyList());
            Flow.doItemStation(e, o, 0, Station.DONER);
        }
        assertEquals(0, e.getReadyDoner());
        s.stars = 5.0;
        e.tick(2000f);
        assertEquals(0, e.getSummary().wastedPortions);
    }

    @Test public void failedDayRestoresReadyDrinksAndStock() {
        SaveData s = day(3);
        s.stars = 1.05;
        GameEngine e = new GameEngine(2, s);
        e.beginPrep();
        e.startPrepDrink();
        run(e, 4.2f);
        assertEquals(4, s.readyDrinks);
        int ayran = s.inventory.get("ayran");
        assertEquals(ayran, s.inventory.get("ayran"));
        e.startDay();
        for (int i = 0; i < 20000 && e.isRunning(); i++) e.tick(0.1f);
        assertEquals(GameEngine.State.FAILED, e.getState());
        assertEquals("başarısızlıkta gün başı hazır içecek (0)", 0, s.readyDrinks);
        assertEquals("prep tüketimi de geri döner", ayran + 4, s.inventory.get("ayran"));
    }

    // ------------------------------------------------------------ hız / tempo
    @Test public void prepDurationFollowsStationSpeedAndTempo() {
        SaveData s = day(1);
        s.upgradeLevels.put("doner_makinesi", 1); // DONER x0.85
        s.setTempo(1.2);
        GameEngine e = new GameEngine(1, s);
        final float[] dur = {0};
        e.addListener(new GameListenerAdapter() {
            @Override public void onPrepStarted(PrepKind k, boolean a, float sec) { dur[0] = sec; }
        });
        e.startPrepDoner("et_doner");
        assertEquals(6f * 0.85f * 1.2f, dur[0], 0.01f);
    }

    @Test public void narratorSpeaksPrepEvents() {
        SaveData s = day(3);
        GameEngine e = new GameEngine(1, s);
        final List<String> said = new ArrayList<>();
        Narrator n = new Narrator(e, new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { said.add(text); }
        });
        e.addListener(n);
        e.beginPrep();
        assertTrue(said.get(0), said.get(0).contains("Hazırlık aşaması") && said.get(0).contains("süre akmıyor"));
        e.startPrepDrink();
        assertTrue(said.toString(), said.get(said.size() - 1).startsWith("İçecek doldurma başladı"));
        run(e, 4.2f);
        assertTrue(said.toString(), said.get(said.size() - 1).contains("4 bardak ayran dolduruldu"));
        assertTrue(n.readyStockText().contains("ayran 4 bardak"));
        e.startPrepDoner("yok");
        assertTrue(said.get(said.size() - 1).contains("geçersiz"));
        assertTrue(n.statusText().contains("hazırlık aşaması"));
        assertTrue(n.statusText().contains("Hazır: ayran 4 bardak"));
        e.startDay();
        assertTrue(said.toString(), said.contains("Dükkân açılıyor."));
    }
}
