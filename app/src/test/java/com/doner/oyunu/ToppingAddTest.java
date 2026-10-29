package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** v0.6 tek tek malzeme ekleme: addTopping / removeLastTopping / finishToppings, bol, azami, geri al, hedef doğrulama. */
public class ToppingAddTest {

    private static SaveData day(int d) {
        SaveData s = SaveData.newGame();
        s.day = d;
        s.stars = 4.0;
        s.money = 20000;
        Shop.buyRecommended(s);
        return s;
    }

    private static Set<String> set(String... ids) { return new LinkedHashSet<>(Arrays.asList(ids)); }

    private static final List<String> NONE = Collections.emptyList();

    /** Ekmek arası döner siparişi, ısıt + dilimle bitmiş (malzeme adımı açık). */
    private static Order openTopping(GameEngine e, List<String> requests) {
        e.setSpawning(false);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), requests);
        assertNotNull(o);
        Flow.doItemStation(e, o, 0, Station.EKMEK);
        Flow.doItemStation(e, o, 0, Station.DONER);
        return o;
    }

    private static final class Rec extends GameListenerAdapter {
        final List<String> log = new ArrayList<>();
        float lastSeconds;
        double penalty = -1;
        int mistakes = -1;
        boolean perfect;
        @Override public void onToppingAddStarted(Order o, int i, Ingredient ing, int count, float sec) {
            log.add("start:" + ing.id + ":" + count);
            lastSeconds = sec;
        }
        @Override public void onToppingAdded(Order o, int i, Ingredient ing, int count) { log.add("added:" + ing.id + ":" + count); }
        @Override public void onToppingRemoved(Order o, int i, Ingredient ing, int remaining) { log.add("removed:" + ing.id + ":" + remaining); }
        @Override public void onToppingsFinished(Order o, int i, Set<String> chosen) { log.add("finished:" + chosen); }
        @Override public void onToppingsRejected(Order o, int i, StartResult r, Ingredient ing) { log.add("rej:" + r + ":" + (ing == null ? "-" : ing.id)); }
        @Override public void onOptionRejected(Order o, String id, StartResult r) { log.add("opt:" + id + ":" + r); }
        @Override public void onOrderMistake(Order o, ToppingCheck c, double p) { mistakes = c.mistakes(); penalty = p; }
        @Override public void onOrderPerfect(Order o) { perfect = true; }
    }

    @Test public void addTakesShortTimeConsumesStockAndFiresEventsInOrder() {
        SaveData s = day(3);
        GameEngine e = new GameEngine(1, s);
        Order o = openTopping(e, NONE);
        Rec r = new Rec();
        e.addListener(r);
        int dom = s.inventory.get("domates"), ket = s.inventory.get("ketcap");
        assertEquals(StartResult.STARTED, e.addTopping(o.id, 0, "domates"));
        assertEquals("salata 0,6 sn", 0.6f, r.lastSeconds, 0.001f);
        assertEquals("stok hemen düşer", dom - 1, s.inventory.get("domates"));
        assertTrue("ekleme sürerken kalem meşgul", o.isRunning());
        assertEquals(0, e.getFreeSlots());
        assertEquals(1, e.getActiveSteps().size());
        assertTrue(e.getActiveSteps().get(0).isToppingAdd());
        assertEquals("domates", e.getActiveSteps().get(0).getAddedIngredient());
        // ekleme sürerken ikinci ekleme reddedilir (kalem meşgul)
        assertEquals(StartResult.ORDER_BUSY, e.addTopping(o.id, 0, "marul"));
        assertEquals(Arrays.asList("start:domates:1"), r.log);
        Flow.waitDone(e, o);
        assertEquals(Arrays.asList("start:domates:1", "added:domates:1"), r.log);
        assertFalse("malzeme adımı bitirilmeden kalem bitmez", o.getLine(0).isDone());
        assertEquals(1, e.getFreeSlots());
        assertEquals(StartResult.STARTED, e.addTopping(o.id, 0, "ketcap"));
        assertEquals("sos 0,5 sn", 0.5f, r.lastSeconds, 0.001f);
        assertEquals(ket - 1, s.inventory.get("ketcap"));
        Flow.waitDone(e, o);
        assertEquals(set("domates", "ketcap"), o.getLine(0).getChosen());
        assertEquals(2, o.getLine(0).addedTotal());
        assertEquals(1, o.getLine(0).addedCount("ketcap"));
    }

    @Test public void secondAddIsBolAndThirdIsRejected() {
        SaveData s = day(3);
        GameEngine e = new GameEngine(1, s);
        Order o = openTopping(e, NONE);
        Rec r = new Rec();
        e.addListener(r);
        int dom = s.inventory.get("domates");
        assertEquals(StartResult.STARTED, e.addTopping(o.id, 0, "domates"));
        Flow.waitDone(e, o);
        assertEquals(StartResult.STARTED, e.addTopping(o.id, 0, "domates"));
        Flow.waitDone(e, o);
        assertEquals("bol: ikinci eklemede count=2", Arrays.asList("start:domates:1", "added:domates:1", "start:domates:2", "added:domates:2"), r.log);
        assertEquals(2, o.getLine(0).addedCount("domates"));
        assertEquals(dom - 2, s.inventory.get("domates"));
        r.log.clear();
        assertEquals(StartResult.MAX_REACHED, e.addTopping(o.id, 0, "domates"));
        assertEquals(Arrays.asList("rej:MAX_REACHED:domates"), r.log);
        assertEquals("reddedilen ekleme tüketmez", dom - 2, s.inventory.get("domates"));
        assertEquals(2, o.getLine(0).addedCount("domates"));
        assertEquals(1, e.getFreeSlots());
    }

    @Test public void undoReturnsLastToppingToStock() {
        SaveData s = day(3);
        GameEngine e = new GameEngine(1, s);
        Order o = openTopping(e, NONE);
        Rec r = new Rec();
        e.addListener(r);
        assertEquals(StartResult.NOTHING_TO_UNDO, e.removeLastTopping(o.id, 0));
        assertEquals(Arrays.asList("opt:undo:NOTHING_TO_UNDO"), r.log);
        int dom = s.inventory.get("domates"), mar = s.inventory.get("marul");
        e.addTopping(o.id, 0, "domates");
        Flow.waitDone(e, o);
        e.addTopping(o.id, 0, "marul");
        assertEquals("ekleme sürerken geri alınamaz", StartResult.ORDER_BUSY, e.removeLastTopping(o.id, 0));
        Flow.waitDone(e, o);
        r.log.clear();
        assertEquals(StartResult.STARTED, e.removeLastTopping(o.id, 0));
        assertEquals("SON eklenen (marul) geri alınır", Arrays.asList("removed:marul:0"), r.log);
        assertEquals(mar, s.inventory.get("marul"));
        assertEquals(dom - 1, s.inventory.get("domates"));
        assertEquals(set("domates"), o.getLine(0).getChosen());
        // bol olanın bir kopyası geri alınır
        e.addTopping(o.id, 0, "domates");
        Flow.waitDone(e, o);
        assertEquals(2, o.getLine(0).addedCount("domates"));
        r.log.clear();
        assertEquals(StartResult.STARTED, e.removeLastTopping(o.id, 0));
        assertEquals(Arrays.asList("removed:domates:1"), r.log);
        assertEquals(1, o.getLine(0).addedCount("domates"));
        assertEquals(dom - 1, s.inventory.get("domates"));
        // bitirildikten sonra geri alınamaz
        assertEquals(StartResult.STARTED, e.finishToppings(o.id, 0));
        assertEquals(StartResult.NOTHING_TO_UNDO, e.removeLastTopping(o.id, 0));
    }

    @Test public void undoOfTrayToppingReturnsTheUseToTheTray() {
        SaveData s = day(3);
        GameEngine e = new GameEngine(1, s);
        e.beginPrep();
        assertEquals(PrepResult.STARTED, e.startPrepTray(Arrays.asList("domates", "marul")));
        for (int i = 0; i < 60; i++) e.tick(0.1f);
        e.startDay();
        Order o = openTopping(e, NONE);
        int dom = s.inventory.get("domates");
        Rec r = new Rec();
        e.addListener(r);
        assertEquals(6, e.getTrayUses("domates"));
        assertEquals(StartResult.STARTED, e.addTopping(o.id, 0, "domates"));
        assertEquals("tepsiyle ekleme süresi yarıya iner (0,6 -> 0,3)", 0.3f, r.lastSeconds, 0.001f);
        assertEquals(5, e.getTrayUses("domates"));
        assertEquals("stoktan düşmez", dom, s.inventory.get("domates"));
        Flow.waitDone(e, o);
        assertEquals(StartResult.STARTED, e.removeLastTopping(o.id, 0));
        assertEquals("tepsiye iade", 6, e.getTrayUses("domates"));
        assertEquals(dom, s.inventory.get("domates"));
    }

    @Test public void finishUsesTheSameTargetValidationAtDelivery() {
        SaveData s = day(3);
        GameEngine e = new GameEngine(1, s);
        Rec r = new Rec();
        e.addListener(r);
        Order o = openTopping(e, Collections.singletonList("soğansız"));
        assertEquals(set("domates", "marul", "ketcap"), e.getToppingTarget(o.id, 0));
        for (String id : set("domates", "marul", "ketcap")) {
            assertEquals(StartResult.STARTED, e.addTopping(o.id, 0, id));
            Flow.waitDone(e, o);
        }
        assertEquals(StartResult.STARTED, e.finishToppings(o.id, 0));
        assertTrue(r.log.contains("finished:[domates, marul, ketcap]"));
        assertTrue(o.getLine(0).isDone());
        Flow.makeReady(e, o);
        assertTrue(e.deliver(o.id));
        assertTrue(r.perfect);
        assertEquals(1, e.getSummary().perfect);
        assertEquals("teslim edilen sipariş kalkar", StartResult.NO_ORDER, e.addTopping(o.id, 0, "domates"));
    }

    @Test public void missingAndExtraToppingsArePenalisedExactlyLikeV05() {
        SaveData s = day(3);
        GameEngine e = new GameEngine(1, s);
        Rec r = new Rec();
        e.addListener(r);
        Order o = openTopping(e, Collections.singletonList("soğansız"));
        // soğan fazla, ketçap eksik
        for (String id : set("domates", "marul", "sogan")) {
            assertEquals(StartResult.STARTED, e.addTopping(o.id, 0, id));
            Flow.waitDone(e, o);
        }
        assertEquals(StartResult.STARTED, e.finishToppings(o.id, 0));
        Flow.makeReady(e, o);
        assertTrue(e.deliver(o.id));
        assertEquals(2, r.mistakes);
        assertEquals(0.10, r.penalty, 1e-9);
        assertFalse(r.perfect);
    }

    @Test public void bolDoesNotChangeValidationButCostsStock() {
        SaveData s = day(3);
        GameEngine e = new GameEngine(1, s);
        Rec r = new Rec();
        e.addListener(r);
        Order o = openTopping(e, NONE);
        for (String id : set("domates", "marul", "sogan", "ketcap")) {
            e.addTopping(o.id, 0, id);
            Flow.waitDone(e, o);
        }
        e.addTopping(o.id, 0, "ketcap"); // bol
        Flow.waitDone(e, o);
        e.finishToppings(o.id, 0);
        Flow.makeReady(e, o);
        e.deliver(o.id);
        assertTrue("bol malzeme hedef kümeyi bozmaz", r.perfect);
    }

    @Test public void emptyFinishIsValidSosuz() {
        SaveData s = day(3);
        GameEngine e = new GameEngine(1, s);
        Rec r = new Rec();
        e.addListener(r);
        Order o = openTopping(e, NONE);
        assertNull("hiç eklenmediyse seçim yok", o.getLine(0).getChosen());
        assertEquals(StartResult.STARTED, e.finishToppings(o.id, 0));
        assertTrue(o.getLine(0).getChosen().isEmpty());
        Flow.makeReady(e, o);
        e.deliver(o.id);
        assertEquals(4, r.mistakes); // 4 hedef malzeme eksik
        assertEquals(0.15, r.penalty, 1e-9);
    }

    @Test public void lockedUntilOtherStepsAreDoneAndBusyGuards() {
        GameEngine e = new GameEngine(1, day(3));
        e.setSpawning(false);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        Rec r = new Rec();
        final List<String> locked = new ArrayList<>();
        e.addListener(r);
        e.addListener(new GameListenerAdapter() {
            @Override public void onStepLocked(Order oo, Step st) { locked.add(st.name); }
        });
        assertEquals(StartResult.STEP_LOCKED, e.addTopping(o.id, 0, "domates"));
        assertEquals(StartResult.STEP_LOCKED, e.finishToppings(o.id, 0));
        assertEquals(Arrays.asList("Malzemeleri ekle", "Malzemeleri ekle"), locked);
        assertEquals(StartResult.NO_ORDER, e.addTopping(999, 0, "domates"));
        assertEquals(StartResult.INVALID, e.addTopping(o.id, 7, "domates"));
        Order ay = e.spawnOrder(Collections.singletonList("ayran"), NONE);
        assertEquals("topping'siz kalem", StartResult.INVALID, e.addTopping(ay.id, 0, "domates"));
        // ekleme sürerken "bitir" reddedilir
        Flow.doItemStation(e, o, 0, Station.EKMEK);
        Flow.doItemStation(e, o, 0, Station.DONER);
        e.addTopping(o.id, 0, "domates");
        assertEquals(StartResult.ORDER_BUSY, e.finishToppings(o.id, 0));
        assertTrue(r.log.contains("opt:finish:ORDER_BUSY"));
    }

    @Test public void onlyOpenPoolIngredientsCanBeAdded() {
        SaveData s = day(2);
        s.inventory.set("aci_sos", 10);
        s.inventory.set("pirinc", 10);
        GameEngine e = new GameEngine(1, s);
        Rec r = new Rec();
        e.addListener(r);
        Order o = openTopping(e, NONE);
        assertEquals("acı sos 5. günde açılır", StartResult.INVALID, e.addTopping(o.id, 0, "aci_sos"));
        assertEquals(StartResult.INVALID, e.addTopping(o.id, 0, "pirinc"));
        assertEquals(StartResult.INVALID, e.addTopping(o.id, 0, "yok"));
        assertEquals(StartResult.INVALID, e.addTopping(o.id, 0, null));
        assertEquals(10, s.inventory.get("aci_sos"));
        assertTrue(r.log.contains("rej:INVALID:aci_sos"));
        assertEquals("ketçap 3. günde açılır", StartResult.INVALID, e.addTopping(o.id, 0, "ketcap"));
        assertEquals(StartResult.STARTED, e.addTopping(o.id, 0, "marul"));
    }

    @Test public void outOfStockIsRejectedWithoutConsumingAndEmergencyBuyFixesIt() {
        SaveData s = day(3);
        s.inventory.set("sogan", 0);
        GameEngine e = new GameEngine(1, s);
        Rec r = new Rec();
        e.addListener(r);
        Order o = openTopping(e, NONE);
        assertEquals(StartResult.OUT_OF_STOCK, e.addTopping(o.id, 0, "sogan"));
        assertTrue(r.log.contains("rej:OUT_OF_STOCK:sogan"));
        assertEquals(1, e.getFreeSlots());
        assertFalse(o.isRunning());
        assertTrue(e.emergencyBuy("sogan"));
        assertEquals(StartResult.STARTED, e.addTopping(o.id, 0, "sogan"));
        assertEquals(Ingredient.EMERGENCY_BATCH - 1, s.inventory.get("sogan"));
    }

    @Test public void legacyBatchConfirmIsRejectedOnceAddingStarted() {
        GameEngine e = new GameEngine(1, day(3));
        Order o = openTopping(e, NONE);
        e.addTopping(o.id, 0, "domates");
        Flow.waitDone(e, o);
        assertEquals(StartResult.INVALID, e.confirmToppings(o.id, 0, set("domates")));
        // eski akış ayrı bir siparişte hâlâ çalışır
        Order p = openTopping(e, NONE);
        assertEquals(StartResult.NEEDS_TOPPINGS, e.startStep(p.id, Station.SOS.id));
        assertEquals(StartResult.STARTED, e.confirmToppings(p.id, 0, set("domates")));
    }

    @Test public void autoItemPicksTheLineWaitingForToppings() {
        GameEngine e = new GameEngine(1, day(8));
        e.setSpawning(false);
        Order o = e.spawnOrder(Arrays.asList("ayran", "ekmek_arasi"), NONE);
        Flow.doItemStation(e, o, 1, Station.EKMEK);
        Flow.doItemStation(e, o, 1, Station.DONER);
        assertEquals(StartResult.STARTED, e.addTopping(o.id, -1, "domates"));
        assertEquals(1, o.getLine(1).addedCount("domates"));
        Flow.waitDone(e, o);
        assertEquals(StartResult.STARTED, e.removeLastTopping(o.id, -1));
        assertEquals(0, o.getLine(1).addedCount("domates"));
        assertEquals(StartResult.STARTED, e.finishToppings(o.id, -1));
        assertTrue(o.getLine(1).isDone());
        assertTrue("ayran kaleminde ekleme yok", e.getAddedToppings(o.id, 0).isEmpty());
        assertTrue(e.getAddedToppings(999, 0).isEmpty());
    }

    @Test public void itemFinishAndReadyToPackEventsComeFromFinishToppings() {
        GameEngine e = new GameEngine(1, day(3));
        e.setSpawning(false);
        final List<String> ev = new ArrayList<>();
        e.addListener(new GameListenerAdapter() {
            @Override public void onStepFinished(Order o, Step s, boolean c) { ev.add("stepDone:" + s.name); }
            @Override public void onItemFinished(Order o, int i) { ev.add("item:" + i); }
            @Override public void onOrderReadyToPack(Order o) { ev.add("pack"); }
        });
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        Flow.doItemStation(e, o, 0, Station.EKMEK);
        Flow.doItemStation(e, o, 0, Station.DONER);
        ev.clear();
        Flow.doItemStation(e, o, 0, Station.SOS);
        assertEquals(Arrays.asList("stepDone:Malzemeleri ekle", "item:0", "pack"), ev);
        assertEquals(Station.PAKET, o.nextStep().station);
    }
}
