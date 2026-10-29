package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Bilinen hata düzeltmeleri için motor/anlatıcı testleri. */
public class EngineFixesTest {

    private static Order firstCustomer(GameEngine e) {
        for (int i = 0; i < 600 && e.getOrders().isEmpty(); i++) e.tick(0.1f);
        assertFalse("müşteri gelmedi", e.getOrders().isEmpty());
        return e.getOrders().get(0);
    }

    private static void doStep(GameEngine e, Order o) {
        assertEquals(StartResult.STARTED, e.startStep(o.id, o.nextStep().station.id));
        for (int i = 0; i < 200 && o.isRunning(); i++) e.tick(0.1f);
        assertFalse(o.isRunning());
    }

    // ------------------------------------------------------------ madde 8: startStep ilk tick'ten önce
    @Test public void startStepBeforeFirstTickStillAnnouncesDayStartedOnce() {
        GameEngine e = new GameEngine(7, SaveData.newGame());
        final List<String> log = new ArrayList<>();
        e.addListener(new GameListenerAdapter() {
            @Override public void onDayStarted(int day, DayConfig c) { log.add("start" + day); }
            @Override public void onCustomerArrived(Order o) { log.add("arrive"); }
        });
        // gün başlamadan: olmayan sipariş
        assertEquals(StartResult.NO_ORDER, e.startStep(99, Station.EKMEK.id));
        assertEquals("startStep gün başlatmış sayılmalı", GameEngine.State.RUNNING, e.getState());
        assertEquals(1, log.size());
        assertEquals("start1", log.get(0));
        e.tick(0.5f);
        e.tick(5f);
        int starts = 0;
        for (String s : log) if (s.startsWith("start")) starts++;
        assertEquals("onDayStarted tam bir kez gelmeli", 1, starts);
        assertEquals("onDayStarted her şeyden önce gelmeli", "start1", log.get(0));
    }

    @Test public void dayStartedBeforeFirstCustomerWhenTickedNormally() {
        GameEngine e = new GameEngine(7, SaveData.newGame());
        final List<String> log = new ArrayList<>();
        e.addListener(new GameListenerAdapter() {
            @Override public void onDayStarted(int day, DayConfig c) { log.add("start"); }
            @Override public void onCustomerArrived(Order o) { log.add("arrive"); }
        });
        firstCustomer(e);
        assertEquals("start", log.get(0));
        assertEquals("arrive", log.get(1));
    }

    // ------------------------------------------------------------ madde 1: birden çok eksik malzeme
    private static Order toSauceStep(GameEngine e) {
        Order o = firstCustomer(e);
        doStep(e, o); // ekmek
        doStep(e, o); // et
        assertEquals("sos adımı", Station.SOS, o.nextStep().station);
        assertTrue("sos adımı 3 malzeme ister", o.nextStep().needs.size() >= 2);
        return o;
    }

    @Test public void missingIngredientsListsAllAndFirstMatches() {
        GameEngine e = new GameEngine(11, SaveData.newGame());
        Order o = toSauceStep(e);
        assertTrue(e.missingIngredients(o.id).isEmpty());
        assertNull(e.missingIngredient(o.id));
        for (String id : o.nextStep().needs.keySet()) e.getSave().inventory.set(id, 0);
        List<Ingredient> all = e.missingIngredients(o.id);
        assertEquals(o.nextStep().needs.size(), all.size());
        assertEquals(all.get(0).id, e.missingIngredient(o.id).id);
        // yalnızca bir tanesi eksikse yalnızca o listelenir
        String keep = all.get(0).id;
        for (Ingredient i : all) e.getSave().inventory.set(i.id, i.id.equals(keep) ? 0 : 5);
        assertEquals(1, e.missingIngredients(o.id).size());
    }

    @Test public void emergencyBuyMissingBuysEveryMissingIngredientInOneCall() {
        SaveData save = SaveData.newGame();
        GameEngine e = new GameEngine(11, save);
        Order o = toSauceStep(e);
        for (String id : o.nextStep().needs.keySet()) save.inventory.set(id, 0);
        int n = o.nextStep().needs.size();
        int expected = e.emergencyCostForMissing(o.id);
        int exp2 = 0;
        for (Ingredient i : e.missingIngredients(o.id)) exp2 += i.emergencyPrice() * Ingredient.EMERGENCY_BATCH;
        assertEquals(exp2, expected);
        int money = save.money;
        final int[] purchases = {0};
        e.addListener(new GameListenerAdapter() {
            @Override public void onEmergencyPurchase(Ingredient i, int q, int c) { purchases[0]++; }
        });
        assertTrue(e.emergencyBuyMissing(o.id));
        assertEquals(n, purchases[0]);
        assertEquals(money - expected, save.money);
        assertEquals(expected, e.getSummary().emergencySpend);
        assertTrue(e.missingIngredients(o.id).isEmpty());
        assertEquals(StartResult.STARTED, e.startStep(o.id, Station.SOS.id));
        assertFalse("eksik kalmadıysa tekrar alınmaz", e.emergencyBuyMissing(o.id));
    }

    @Test public void emergencyBuyMissingIsAtomicWhenMoneyShort() {
        SaveData save = SaveData.newGame();
        GameEngine e = new GameEngine(11, save);
        Order o = toSauceStep(e);
        for (String id : o.nextStep().needs.keySet()) save.inventory.set(id, 0);
        int cost = e.emergencyCostForMissing(o.id);
        save.money = cost - 1;
        final int[] needed = {0};
        e.addListener(new GameListenerAdapter() {
            @Override public void onNotEnoughMoney(int n) { needed[0] = n; }
        });
        assertFalse(e.emergencyBuyMissing(o.id));
        assertEquals(cost, needed[0]);
        assertEquals("hiçbiri alınmamalı", cost - 1, save.money);
        assertEquals(o.nextStep().needs.size(), e.missingIngredients(o.id).size());
        assertEquals(0, e.getSummary().emergencySpend);
    }

    @Test public void emergencyBuyMissingCoversNeedLargerThanOneBatch() {
        SaveData save = SaveData.newGame();
        GameEngine e = new GameEngine(5, save);
        Order o = firstCustomer(e);
        // sıradaki adımın ihtiyacını tek partiden (3) büyük gibi test etmek için stoğu 0 yapıp ihtiyaç kadar parti beklenir
        for (String id : o.nextStep().needs.keySet()) save.inventory.set(id, 0);
        assertTrue(e.emergencyBuyMissing(o.id));
        assertTrue(e.missingIngredients(o.id).isEmpty());
    }

    @Test public void narratorOutOfStockNamesAllMissingAndTotalPrice() {
        SaveData save = SaveData.newGame();
        GameEngine e = new GameEngine(11, save);
        Order o = toSauceStep(e);
        for (String id : o.nextStep().needs.keySet()) save.inventory.set(id, 0);
        final List<String> said = new ArrayList<>();
        e.addListener(new Narrator(e, new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { said.add(text); }
        }));
        assertEquals(StartResult.OUT_OF_STOCK, e.startStep(o.id, Station.SOS.id));
        String t = said.get(said.size() - 1);
        for (Ingredient i : e.missingIngredients(o.id)) assertTrue(t, t.contains(i.name));
        assertTrue(t, t.contains(e.emergencyCostForMissing(o.id) + " lira"));
    }

    // ------------------------------------------------------------ madde 5: kira
    private static GameEngine finishWithMoney(int money, Narrator[] outN, final List<String> said) {
        SaveData save = SaveData.newGame();
        save.stars = 5.0;
        save.money = money;
        GameEngine e = new GameEngine(3, save);
        Narrator n = new Narrator(e, new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { said.add(text); }
        });
        e.addListener(n);
        outN[0] = n;
        for (int i = 0; i < 20000 && e.isRunning(); i++) e.tick(0.1f);
        assertEquals(GameEngine.State.FINISHED, e.getState());
        return e;
    }

    @Test public void rentIsOnlyWhatWasActuallyDeductedWhenMoneyIsShort() {
        List<String> said = new ArrayList<>();
        GameEngine e = finishWithMoney(100, new Narrator[1], said);
        DaySummary s = e.getSummary();
        assertEquals(300, s.rentDue);
        assertEquals("yalnızca eldeki para düşülür", 100, s.rent);
        assertEquals(200, s.rentUnpaid());
        assertEquals(0, s.moneyAfter);
        assertEquals(0, e.getSave().money);
        assertEquals(s.moneyAfter, e.getMoney());
        // net, kasadaki gerçek değişimle tutarlı
        assertEquals(s.moneyAfter - 100, s.profit());
        String t = said.get(said.size() - 1);
        assertTrue(t, t.contains("yalnızca 100 lira ödenebildi"));
        assertTrue(t, t.contains("300 lira"));
        assertTrue(t, t.contains("200 lira ödenemedi"));
        assertTrue(t, t.contains("Net eksi") || t.contains("Net " + s.profit()));
        assertFalse(t, t.contains("kira 300 lira. Net"));
    }

    @Test public void rentFullyPaidWhenAffordable() {
        List<String> said = new ArrayList<>();
        GameEngine e = finishWithMoney(5000, new Narrator[1], said);
        DaySummary s = e.getSummary();
        assertEquals(300, s.rent);
        assertEquals(300, s.rentDue);
        assertEquals(0, s.rentUnpaid());
        assertEquals(5000 - 300 + s.revenue + s.tips - s.emergencySpend, s.moneyAfter);
        String t = said.get(said.size() - 1);
        assertTrue(t, t.contains("kira 300 lira. Net"));
        assertFalse(t, t.contains("ödenemedi"));
    }

    @Test public void rentWithNoMoneyAtAllChargesNothing() {
        GameEngine e = finishWithMoney(0, new Narrator[1], new ArrayList<String>());
        assertEquals(0, e.getSummary().rent);
        assertEquals(300, e.getSummary().rentUnpaid());
        assertTrue(e.getMoney() >= 0);
    }

    // ------------------------------------------------------------ madde 7: yıldız anlatımı
    @Test public void starsTrackerAnnouncesVisibleChangeAndSwallowsInvisibleOnes() {
        Narrator.StarsTracker t = new Narrator.StarsTracker();
        t.reset(3.0);
        assertTrue("3,0 -> 2,7 duyurulmalı", t.changed(3.0, 2.7));
        assertFalse("2,7 -> 2,68 ekranda aynı", t.changed(2.7, 2.68));
        assertFalse(t.changed(2.68, 2.66));
        assertTrue("birikince 2,6", t.changed(2.66, 2.64));
        assertFalse(t.changed(2.64, 2.64));
    }

    @Test public void starsTrackerNeverMissesAVisibleChangeOverRandomWalk() {
        Random r = new Random(1);
        Narrator.StarsTracker t = new Narrator.StarsTracker();
        double s = 3.0;
        t.reset(s);
        String displayed = Narrator.fmtStars(s);
        for (int i = 0; i < 20000; i++) {
            double d = (r.nextInt(7) - 3) * 0.02 + (r.nextInt(10) == 0 ? (r.nextBoolean() ? 0.3 : -0.3) : 0);
            double n = Math.max(1.0, Math.min(5.0, s + d));
            boolean announced = t.changed(s, n);
            String shown = Narrator.fmtStars(n);
            if (!shown.equals(displayed)) {
                assertTrue("görünen değer " + displayed + " -> " + shown + " sessiz kaldı", announced);
                displayed = shown;
            } else {
                assertFalse("görünen değer değişmedi ama duyuruldu: " + shown, announced);
            }
            s = n;
        }
    }

    @Test public void narratorSpeaksStarChangesOnlyWhenDisplayedValueChanges() {
        SaveData save = SaveData.newGame();
        GameEngine e = new GameEngine(2, save);
        final List<String> said = new ArrayList<>();
        Narrator n = new Narrator(e, new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { said.add(text); }
        });
        n.onDayStarted(1, e.getConfig());
        said.clear();
        n.onStarsChanged(3.0, 2.98);
        n.onStarsChanged(2.98, 2.96);
        assertTrue("görünür değişim yok, sessiz", said.isEmpty());
        n.onStarsChanged(2.96, 2.94);
        assertEquals(1, said.size());
        assertEquals("Yıldız 2,9 oldu, düştü.", said.get(0));
        n.onStarsChanged(2.94, 3.3);
        assertEquals("Yıldız 3,3 oldu, yükseldi.", said.get(1));
        said.clear();
        n.onStarsChanged(3.3, 3.0);
        assertEquals(1, said.size());
        assertEquals("Yıldız 3,0 oldu, düştü.", said.get(0));
    }

    // ------------------------------------------------------------ madde 6: stok kapasitesi
    @Test public void purchasableQtyRespectsStockCap() {
        SaveData save = SaveData.newGame();
        int cap = new Upgrades(save).stockCap();
        String id = Catalog.ingredients().get(0).id;
        save.inventory.set(id, cap - 4);
        assertEquals(4, Shop.purchasableQty(save, id, 10));
        assertEquals(3, Shop.purchasableQty(save, id, 3));
        save.inventory.set(id, cap);
        assertEquals(0, Shop.purchasableQty(save, id, 10));
        assertEquals(0, Shop.purchasableQty(save, "yok", 10));
        assertEquals(0, Shop.purchasableQty(save, id, 0));
    }
}
