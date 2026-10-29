package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class EngineTest {

    static class Rec extends GameListenerAdapter {
        final List<String> log = new ArrayList<>();
        int arrived, left, delivered, ready, stepsDone, stepsStarted, wrong, outOfStock, warn1, warn2, emergency, noSlot;
        int days, failed;
        double lastStars = -1;
        DaySummary summary;
        @Override public void onCustomerArrived(Order o) { arrived++; log.add("arrive"); }
        @Override public void onCustomerLeft(Order o) { left++; log.add("left"); }
        @Override public void onOrderDelivered(Order o, int m, int t, boolean h) { delivered++; }
        @Override public void onOrderReady(Order o) { ready++; }
        @Override public void onStepFinished(Order o, Step s, boolean c) { stepsDone++; }
        @Override public void onStepStarted(Order o, Step s, int slot, float sec) { stepsStarted++; }
        @Override public void onWrongStation(Order o, Station t, Station e) { wrong++; }
        @Override public void onOutOfStock(Order o, Ingredient i) { outOfStock++; }
        int firstId = -1;
        @Override public void onPatienceWarning(Order o, int l) {
            if (firstId < 0) firstId = o.id;
            if (o.id != firstId) return;
            if (l == 1) warn1++; else warn2++;
        }
        @Override public void onEmergencyPurchase(Ingredient i, int q, int c) { emergency++; }
        @Override public void onNoFreeSlot(Order o) { noSlot++; }
        @Override public void onStarsChanged(double a, double b) { lastStars = b; }
        @Override public void onDayFinished(DaySummary s) { days++; summary = s; }
        @Override public void onDayFailed(DaySummary s) { failed++; summary = s; }
    }

    private static Order waitForCustomer(GameEngine e) {
        for (int i = 0; i < 600 && e.getOrders().isEmpty(); i++) e.tick(0.1f);
        assertFalse("müşteri gelmedi", e.getOrders().isEmpty());
        return e.getOrders().get(0);
    }

    @Test public void dayOneBotCompletesTheDay() {
        SaveData save = SaveData.newGame();
        GameEngine e = new GameEngine(42, save);
        Rec r = new Rec();
        e.addListener(r);
        new TestBot(1.0f).playDay(e);
        assertEquals(GameEngine.State.FINISHED, e.getState());
        assertEquals(1, r.days);
        assertEquals(0, r.failed);
        assertTrue("birkaç teslim", r.delivered >= 4);
        assertEquals(0, r.left);
        assertEquals(2, save.day);
        DaySummary s = r.summary;
        assertTrue(s.revenue > 0 && s.profit() > 0);
        assertEquals(300, s.rent);
        assertEquals(SaveData.START_MONEY + s.revenue + s.tips - s.rent - s.emergencySpend, save.money);
        assertTrue(save.stars > 3.0);
        assertTrue(e.getOrders().isEmpty());
        assertEquals(0, r.wrong);
        assertEquals(r.stepsStarted, r.stepsDone);
        assertEquals(r.delivered * 4, r.stepsDone); // gün 1: tek ürün, 4 adım
    }

    @Test public void stepFlowAndSelection() {
        GameEngine e = new GameEngine(1, SaveData.newGame());
        final List<String> ev = new ArrayList<>();
        e.addListener(new GameListenerAdapter() {
            @Override public void onOrderSelected(Order o) { ev.add("sel"); }
            @Override public void onNextStepHint(Order o, Step s) { ev.add("hint:" + s.name); }
        });
        Order o = waitForCustomer(e);
        assertTrue(e.selectOrder(o.id));
        assertEquals(o.id, e.getSelectedOrderId());
        assertEquals("sel", ev.get(0));
        assertEquals("hint:Ekmeği ısıt", ev.get(1));
        assertEquals(StartResult.STARTED, e.startStep(o.id, Station.EKMEK.id));
        assertTrue(o.isRunning());
        // tek yuva dolu: başka adım (serbest sıra) yuva yüzünden reddedilir, sıra yüzünden DEĞİL
        assertEquals(StartResult.NO_SLOT, e.startStep(o.id, Station.DONER.id));
        // aynı adım zaten sürüyor
        assertEquals(StartResult.ORDER_BUSY, e.startStep(o.id, Station.EKMEK.id));
        assertEquals(1, e.getActiveSteps().size());
        for (int i = 0; i < 100 && o.isRunning(); i++) e.tick(0.1f);
        assertFalse(o.isRunning());
        assertEquals(1, o.getNextStepIndex());
        assertEquals("hint:Döneri dilimle", ev.get(ev.size() - 1));
        assertFalse(e.deliver(o.id));
        assertEquals(StartResult.NO_ORDER, e.startStep(999, 1));
        assertTrue(e.selectNextOrder());
    }

    @Test public void wrongStationPenalized() {
        SaveData save = SaveData.newGame();
        GameEngine e = new GameEngine(1, save);
        Rec r = new Rec();
        e.addListener(r);
        Order o = waitForCustomer(e);
        double before = save.stars;
        assertEquals(StartResult.WRONG_STATION, e.startStep(o.id, Station.ICECEK.id));
        assertEquals(1, r.wrong);
        assertTrue(save.stars < before);
        assertEquals(0, o.getNextStepIndex());
        assertFalse(o.isRunning());
        assertEquals(StartResult.WRONG_STATION, e.startStep(o.id, 77));
    }

    @Test public void patienceRunsOutStarsDropAndWarnings() {
        SaveData save = SaveData.newGame();
        GameEngine e = new GameEngine(7, save);
        Rec r = new Rec();
        e.addListener(r);
        Order o = waitForCustomer(e);
        float pat = o.customer.patienceMax;
        assertTrue(pat > 20f);
        double before = save.stars;
        int safety = 0;
        while (r.left == 0 && safety++ < 2000) e.tick(0.1f);
        assertEquals(1, r.left);
        assertEquals(before - 0.30, r.lastStars, 1e-6);
        assertEquals(before - 0.30, save.stars, 1e-6);
        assertEquals(1, r.warn1);
        assertEquals(1, r.warn2);
        assertEquals(1, e.getSummary().lost);
        assertEquals(0, e.getSummary().revenue);
    }

    @Test public void leavingCustomerFreesSlot() {
        SaveData save = SaveData.newGame();
        GameEngine e = new GameEngine(7, save);
        Order o = waitForCustomer(e);
        assertEquals(StartResult.STARTED, e.startStep(o.id, Station.EKMEK.id));
        assertEquals(0, e.getFreeSlots());
        // oyuncu hareket etmeden sabır dolana kadar: önce adım biter, sonra müşteri gider
        for (int i = 0; i < 6000 && e.getOrder(o.id) != null; i++) e.tick(0.1f);
        assertNull(e.getOrder(o.id));
        assertEquals(e.getSlotCount(), e.getFreeSlots() + e.getActiveSteps().size());
    }

    @Test public void idleBotLosesQuickly() {
        SaveData save = SaveData.newGame();
        boolean failed = false;
        for (int d = 0; d < 6 && !failed; d++) {
            GameEngine e = new GameEngine(100 + d, save);
            Rec r = new Rec();
            e.addListener(r);
            int guard = 0;
            while (e.isRunning() && guard++ < 20000) e.tick(0.1f);
            assertFalse(e.isRunning());
            if (r.failed > 0) {
                failed = true;
                assertTrue(e.getSummary().failed);
                assertEquals(GameEngine.State.FAILED, e.getState());
            }
        }
        assertTrue("hiç hareket etmeyen bot kaybetmeli", failed);
    }

    @Test public void failedDayKeepsMoneyAndStock() {
        SaveData save = SaveData.newGame();
        save.stars = 1.2;
        int money = save.money;
        int et = save.inventory.get("et_doner");
        GameEngine e = new GameEngine(5, save);
        Rec r = new Rec();
        e.addListener(r);
        int guard = 0;
        while (e.isRunning() && guard++ < 20000) e.tick(0.1f);
        assertEquals(1, r.failed);
        assertEquals(money, save.money);
        assertEquals(et, save.inventory.get("et_doner"));
        assertEquals(1, save.day);
        assertTrue("tekrar denenebilir yıldız", save.stars >= 1.5);
        // başarısız gün sonrası tick etkisiz
        e.tick(5f);
        assertEquals(GameEngine.State.FAILED, e.getState());
    }

    @Test public void outOfStockAndEmergencyPurchase() {
        SaveData save = SaveData.newGame();
        save.inventory.set("ekmek", 0);
        save.money = 1000;
        GameEngine e = new GameEngine(3, save);
        Rec r = new Rec();
        e.addListener(r);
        Order o = waitForCustomer(e);
        assertEquals(StartResult.OUT_OF_STOCK, e.startStep(o.id, Station.EKMEK.id));
        assertEquals(1, r.outOfStock);
        Ingredient m = e.missingIngredient(o.id);
        assertEquals("ekmek", m.id);
        assertEquals(9, m.emergencyPrice()); // 6 * 1.5
        assertTrue(e.emergencyBuy("ekmek"));
        assertEquals(1000 - 9 * Ingredient.EMERGENCY_BATCH, save.money);
        assertEquals(Ingredient.EMERGENCY_BATCH, save.inventory.get("ekmek"));
        assertEquals(1, r.emergency);
        assertEquals(StartResult.STARTED, e.startStep(o.id, Station.EKMEK.id));
        assertEquals(Ingredient.EMERGENCY_BATCH - 1, save.inventory.get("ekmek"));
        assertEquals(9 * Ingredient.EMERGENCY_BATCH, e.getSummary().emergencySpend);
    }

    @Test public void emergencyBuyNeedsMoney() {
        SaveData save = SaveData.newGame();
        save.money = 5;
        GameEngine e = new GameEngine(3, save);
        final int[] needed = {0};
        e.addListener(new GameListenerAdapter() {
            @Override public void onNotEnoughMoney(int n) { needed[0] = n; }
        });
        assertFalse(e.emergencyBuy("et_doner"));
        assertEquals(5, save.money);
        assertEquals(Catalog.ingredient("et_doner").emergencyPrice() * Ingredient.EMERGENCY_BATCH, needed[0]);
        assertFalse(e.emergencyBuy("yok"));
    }

    @Test public void stockIsConsumedPerStep() {
        SaveData save = SaveData.newGame();
        int ekmek = save.inventory.get("ekmek");
        int et = save.inventory.get("et_doner");
        GameEngine e = new GameEngine(3, save);
        Order o = waitForCustomer(e);
        e.startStep(o.id, Station.EKMEK.id);
        assertEquals(ekmek - 1, save.inventory.get("ekmek"));
        assertEquals(et, save.inventory.get("et_doner"));
    }

    @Test public void slotsLimitParallelWork() {
        // çırak yuva sayısını ARTIRMAZ: oyuncunun yuvası hep 1
        SaveData save = SaveData.newGame();
        save.upgradeLevels.put("cirak", 1);
        GameEngine e = new GameEngine(9, save);
        assertEquals(1, e.getSlotCount());
        for (int i = 0; i < 2000 && e.getOrders().size() < 2; i++) e.tick(0.1f);
        assertEquals(2, e.getOrders().size());
        // tek yuvalı oyuncu: ikinci adım reddedilir
        SaveData s1 = SaveData.newGame();
        GameEngine e1 = new GameEngine(9, s1);
        Rec r1 = new Rec();
        e1.addListener(r1);
        for (int i = 0; i < 2000 && e1.getOrders().size() < 2; i++) e1.tick(0.1f);
        Order c = e1.getOrders().get(0), d = e1.getOrders().get(1);
        assertEquals(StartResult.STARTED, e1.startStep(c.id, Station.EKMEK.id));
        assertEquals(StartResult.NO_SLOT, e1.startStep(d.id, Station.EKMEK.id));
        assertEquals(1, r1.noSlot);
        assertEquals(0, e1.getFreeSlots());
    }

    @Test public void freeOrderWithinItemAndAcrossItems() {
        SaveData save = SaveData.newGame();
        save.day = 3; // ayran açık
        Shop.buyRecommended(save);
        GameEngine e = new GameEngine(4, save);
        Order o = e.spawnOrder(java.util.Arrays.asList("ekmek_arasi", "ayran"), java.util.Collections.<String>emptyList());
        assertNotNull(o);
        assertEquals(2, o.lineCount());
        int doner = Flow.itemOf(o, "ekmek_arasi"), ayran = Flow.itemOf(o, "ayran");
        // serbest sıra: önce ayran, sonra döner kalemi; dilimleme ısıtmadan önce de yapılabilir
        Flow.doItemStation(e, o, ayran, Station.ICECEK);
        assertTrue(o.getLine(ayran).isDone());
        assertFalse(o.allLinesDone());
        Flow.doItemStation(e, o, doner, Station.DONER);
        // topping adımı ısıt bitmeden açılmaz
        assertEquals(StartResult.STEP_LOCKED, e.startStep(o.id, doner, Station.SOS.id));
        // paketleme tüm kalemler bitmeden açılmaz
        assertEquals(StartResult.STEP_LOCKED, e.startStep(o.id, Station.PAKET.id));
        Flow.doItemStation(e, o, doner, Station.EKMEK);
        assertEquals(StartResult.NEEDS_TOPPINGS, e.startStep(o.id, doner, Station.SOS.id));
        assertEquals(StartResult.STARTED, e.confirmToppings(o.id, doner, e.getToppingTarget(o.id, doner)));
        Flow.waitDone(e, o);
        assertTrue(o.allLinesDone());
        assertFalse(o.isReady());
        // tek ortak paketleme: süre 1,5 + 0,5 x kalem
        final float[] secs = {0};
        e.addListener(new GameListenerAdapter() {
            @Override public void onStepStarted(Order oo, Step s, int slot, float sec) { secs[0] = sec; }
        });
        assertEquals(StartResult.STARTED, e.startStep(o.id, Station.PAKET.id));
        assertEquals(2.5f, secs[0], 0.01f);
        Flow.waitDone(e, o);
        assertTrue(o.isReady());
        assertTrue(e.deliver(o.id));
    }

    @Test public void packagingIsOnlyAfterAllItemsAndNoPackInsideItems() {
        for (Product p : Catalog.products()) {
            for (Step s : p.steps) assertNotEquals(p.name + " kalem-içi paket kalktı", Station.PAKET, s.station);
        }
    }

    @Test public void upgradesShortenSteps() {
        SaveData base = SaveData.newGame();
        SaveData fast = SaveData.newGame();
        fast.upgradeLevels.put("ekmek_isitici", 3);
        float tBase = firstStepSeconds(base);
        float tFast = firstStepSeconds(fast);
        assertTrue(tFast < tBase * 0.5f);
        SaveData slow = SaveData.newGame();
        slow.setTempo(1.6);
        assertEquals(tBase * 1.6f, firstStepSeconds(slow), 0.01f);
    }

    private float firstStepSeconds(SaveData s) {
        GameEngine e = new GameEngine(1, s);
        final float[] sec = {0};
        e.addListener(new GameListenerAdapter() {
            @Override public void onStepStarted(Order o, Step st, int slot, float seconds) { sec[0] = seconds; }
        });
        Order o = waitForCustomer(e);
        e.startStep(o.id, Station.EKMEK.id);
        return sec[0];
    }

    @Test public void deliveryPaysTipForFastService() {
        SaveData save = SaveData.newGame();
        GameEngine e = new GameEngine(11, save);
        final int[] got = new int[2];
        final boolean[] happy = {false};
        e.addListener(new GameListenerAdapter() {
            @Override public void onOrderDelivered(Order o, int m, int t, boolean h) { got[0] = m; got[1] = t; happy[0] = h; }
        });
        Order o = waitForCustomer(e);
        int moneyBefore = save.money;
        Flow.makeReady(e, o);
        assertTrue(e.deliver(o.id));
        assertEquals(240, got[0]);
        assertTrue(happy[0]);
        assertEquals(moneyBefore + got[0] + got[1], save.money);
        assertNull(e.getOrder(o.id));
        assertFalse(e.deliver(o.id));
    }

    @Test public void largeTickIsSubdivided() {
        SaveData save = SaveData.newGame();
        GameEngine e = new GameEngine(2, save);
        Rec r = new Rec();
        e.addListener(r);
        e.tick(500f); // tek seferde bütün gün
        assertFalse(e.isRunning());
    }
}
