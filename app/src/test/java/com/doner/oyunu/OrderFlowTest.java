package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** v0.5 kalem bazlı sipariş akışı: serbest sıra, bağımlılıklar, kalem seçimi, tek ortak paketleme. */
public class OrderFlowTest {
    private static final List<String> NONE = Collections.emptyList();

    private static GameEngine engine(int day) {
        SaveData s = SaveData.newGame();
        s.day = day;
        s.stars = 5.0;
        s.money = 50000;
        Shop.buyRecommended(s);
        GameEngine e = new GameEngine(3, s);
        e.setSpawning(false);
        return e;
    }

    @Test public void anyItemAnyOrderAndStepsWithinAnItemInAnyOrder() {
        GameEngine e = engine(3);
        Order o = e.spawnOrder(Arrays.asList("ekmek_arasi", "ayran"), NONE);
        int d = Flow.itemOf(o, "ekmek_arasi"), a = Flow.itemOf(o, "ayran");
        // sırayı oyuncu seçer: önce döner kalemin DONER adımı, sonra ayran, sonra ekmek
        Flow.doItemStation(e, o, d, Station.DONER);
        Flow.doItemStation(e, o, a, Station.ICECEK);
        Flow.doItemStation(e, o, d, Station.EKMEK);
        Flow.doItemStation(e, o, d, Station.SOS);
        assertTrue(o.allLinesDone());
        assertEquals(StartResult.STARTED, e.startStep(o.id, Station.PAKET.id));
        Flow.waitDone(e, o);
        assertTrue(o.isReady());
        assertEquals("ayran 1 + döner kalemi 3 + paketleme 1", 5, o.getNextStepIndex());
    }

    @Test public void packagingOpensOnlyAfterAllItemsAndIsASingleStep() {
        GameEngine e = engine(3);
        Order o = e.spawnOrder(Arrays.asList("ayran", "ayran"), NONE);
        final List<String> ev = new ArrayList<>();
        e.addListener(new GameListenerAdapter() {
            @Override public void onItemFinished(Order oo, int i) { ev.add("item" + i); }
            @Override public void onOrderReadyToPack(Order oo) { ev.add("topack"); }
            @Override public void onOrderReady(Order oo) { ev.add("ready"); }
            @Override public void onStepLocked(Order oo, Step s) { ev.add("locked:" + s.name); }
        });
        Flow.doItemStation(e, o, 0, Station.ICECEK);
        assertEquals(StartResult.STEP_LOCKED, e.startStep(o.id, Station.PAKET.id));
        assertFalse(o.canPack());
        Flow.doItemStation(e, o, 1, Station.ICECEK);
        assertTrue(o.canPack());
        assertEquals(Arrays.asList("item0", "locked:Siparişi paketle", "item1", "topack"), ev);
        assertEquals(o.getPackStep(), o.nextStep());
        assertEquals(StartResult.STARTED, e.startStep(o.id, Station.PAKET.id));
        assertTrue(o.isPacking());
        assertEquals(StartResult.ORDER_BUSY, e.startStep(o.id, Station.PAKET.id));
        Flow.waitDone(e, o);
        assertEquals(Arrays.asList("item0", "locked:Siparişi paketle", "item1", "topack", "ready"), ev);
        assertEquals(StartResult.ORDER_READY, e.startStep(o.id, Station.PAKET.id));
    }

    @Test public void sequentialStepsAreLockedUntilPreviousDone() {
        GameEngine e = engine(16);
        Order o = e.spawnOrder(Collections.singletonList("kunefe"), NONE);
        // künefe: peynir (sıralı) henüz kilitli; ilk adım serbest
        assertEquals(StartResult.STEP_LOCKED, e.startStep(o.id, 0, Station.IZGARA.id));
        Flow.doItemStation(e, o, 0, Station.TATLI); // kadayıf yoğur
        Flow.doItemStation(e, o, 0, Station.TATLI); // peyniri ser
        assertEquals(StartResult.STARTED, e.startStep(o.id, 0, Station.IZGARA.id));
        Flow.waitDone(e, o);
        Flow.doItemStation(e, o, 0, Station.TATLI); // servis
        assertTrue(o.getLine(0).isDone());
    }

    @Test public void lahmacunOvenWaitsForFillingAndToppingsWaitForOven() {
        GameEngine e = engine(14);
        Order o = e.spawnOrder(Collections.singletonList("lahmacun"), NONE);
        assertEquals(StartResult.STEP_LOCKED, e.startStep(o.id, 0, Station.IZGARA.id));
        assertEquals(StartResult.STEP_LOCKED, e.startStep(o.id, 0, Station.PAKET.id));
        Flow.doItemStation(e, o, 0, Station.SOS); // harcı yay (topping değil)
        assertFalse(o.getLine(0).isStepDone(2));
        Flow.doItemStation(e, o, 0, Station.IZGARA);
        Flow.doItemStation(e, o, 0, Station.SOS); // şimdi topping
        assertTrue(o.getLine(0).isDone());
    }

    @Test public void selectedItemGetsPriorityAndSelectItemFiresEvent() {
        GameEngine e = engine(3);
        Order o = e.spawnOrder(Arrays.asList("ekmek_arasi", "ekmek_arasi"), NONE);
        final List<String> ev = new ArrayList<>();
        e.addListener(new GameListenerAdapter() {
            @Override public void onItemSelected(Order oo, int i) { ev.add("item" + i); }
            @Override public void onNextStepHint(Order oo, Step s) { ev.add("hint:" + s.name); }
        });
        assertFalse("sipariş seçili değilken kalem seçilemez", e.selectItem(1));
        e.selectOrder(o.id);
        assertTrue(e.selectItem(1));
        assertEquals(1, e.getSelectedItem());
        assertEquals(Arrays.asList("hint:Ekmeği ısıt", "item1", "hint:Ekmeği ısıt"), ev);
        assertFalse(e.selectItem(5));
        assertEquals(StartResult.STARTED, e.startStep(o.id, Station.EKMEK.id));
        assertTrue("seçili kalem (1) önce", o.getLine(1).isStepRunning(0));
        assertFalse(o.getLine(0).isStepRunning(0));
        assertNotNull(e.getActiveForItem(o.id, 1));
        assertNull(e.getActiveForItem(o.id, 0));
        // sipariş değişince kalem seçimi sıfırlanır
        Order o2 = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        e.selectOrder(o2.id);
        assertEquals(-1, e.getSelectedItem());
        assertTrue(e.selectItem(-1));
    }

    @Test public void explicitItemIndexRestrictsStationResolution() {
        GameEngine e = engine(3);
        Order o = e.spawnOrder(Arrays.asList("ekmek_arasi", "ayran"), NONE);
        assertEquals("ayran kaleminde EKMEK adımı yok", StartResult.WRONG_STATION, e.startStep(o.id, 1, Station.EKMEK.id));
        assertEquals(StartResult.INVALID, e.startStep(o.id, 7, Station.EKMEK.id));
        assertEquals(StartResult.STARTED, e.startStep(o.id, 0, Station.EKMEK.id));
        assertEquals(1, e.getSummary().wrongTaps);
    }

    @Test public void lockedStepDoesNotCostStarsButWrongStationDoes() {
        GameEngine e = engine(1);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        double st = e.getStars();
        assertEquals(StartResult.STEP_LOCKED, e.startStep(o.id, Station.SOS.id));
        assertEquals(st, e.getStars(), 1e-9);
        assertEquals(0, e.getSummary().wrongTaps);
        assertEquals(StartResult.WRONG_STATION, e.startStep(o.id, Station.TATLI.id));
        assertTrue(e.getStars() < st);
        assertEquals(1, e.getSummary().wrongTaps);
    }

    @Test public void suggestionFollowsOpenStepsAndReportsNullWhenAllRunning() {
        GameEngine e = engine(1);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        assertEquals("Ekmeği ısıt", o.nextStep().name);
        e.startStep(o.id, Station.EKMEK.id);
        assertEquals("çalışan adım önerilmez", "Döneri dilimle", o.nextStep().name);
        for (int i = 0; i < 100 && o.isRunning(); i++) e.tick(0.1f);
        e.startStep(o.id, Station.DONER.id);
        for (int i = 0; i < 100 && o.isRunning(); i++) e.tick(0.1f);
        assertEquals("Malzemeleri ekle", o.nextStep().name);
        assertEquals(2, o.getNextStepIndex());
        e.startStep(o.id, Station.SOS.id);
        e.confirmToppings(o.id, 0, e.getToppingTarget(o.id, 0));
        assertNull("her şey sürüyor", o.nextStep());
    }

    @Test public void stepFinishedFiresOrderCompleteOnlyForPackaging() {
        GameEngine e = engine(1);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        final List<Boolean> complete = new ArrayList<>();
        e.addListener(new GameListenerAdapter() {
            @Override public void onStepFinished(Order oo, Step s, boolean c) { complete.add(c); }
        });
        Flow.makeReady(e, o);
        assertEquals(Arrays.asList(false, false, false, true), complete);
    }

    @Test public void multiItemOrderPriceAndPatienceIncludePackaging() {
        GameEngine e = engine(3);
        Order o = e.spawnOrder(Arrays.asList("ekmek_arasi", "ayran"), NONE);
        assertEquals(240 + 45, o.price());
        // v0.6: tek tek ekleme; tahmini iş yükü malzeme başına 1,5 sn (ekleme + oyuncu eylemi)
        float expected = 3.5f + 3.5f + (2.0f + 4 * 1.5f) + 1.8f + (1.5f + 0.5f * 2);
        assertEquals(expected, o.workSeconds(), 0.01f);
        assertEquals(2.5f, o.getPackStep().baseSeconds, 1e-6f);
        assertEquals(Station.PAKET, o.getPackStep().station);
    }

    @Test public void customerLeavingClearsItsRunningWork() {
        GameEngine e = engine(1);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        e.startStep(o.id, Station.EKMEK.id);
        assertEquals(0, e.getFreeSlots());
        for (int i = 0; i < 20000 && e.getOrder(o.id) != null; i++) e.tick(0.1f);
        assertNull(e.getOrder(o.id));
        assertTrue(e.getActiveSteps().isEmpty());
        assertEquals(1, e.getFreeSlots());
    }
}
