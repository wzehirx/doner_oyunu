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

/** v0.5 malzeme (topping) seçimi: istekler, hedef küme, seçim API'si, doğrulama, ceza/bonus, anlatım. */
public class ToppingTest {

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

    /** Ekmek arası döner siparişini ısıt+dilimle bitmiş (topping açık) hale getirir. */
    private static Order openTopping(GameEngine e, List<String> requests) {
        e.setSpawning(false);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), requests);
        assertNotNull(o);
        Flow.doItemStation(e, o, 0, Station.EKMEK);
        Flow.doItemStation(e, o, 0, Station.DONER);
        return o;
    }

    // ------------------------------------------------------------ istekler ve hedef
    @Test public void requestsChangeTheTargetSet() {
        GameEngine e = new GameEngine(1, day(3));
        e.setSpawning(false);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), Arrays.asList("soğansız", "acılı"));
        assertNotNull(o);
        assertEquals("soğansız, acılı", o.requestsText());
        assertTrue(o.hasRequests());
        assertEquals(set("domates", "marul", "ketcap", "aci_sos"), e.getToppingTarget(o.id, 0));
        assertEquals(set("domates", "marul", "ketcap", "aci_sos"), o.targetToppings(0));
        Order p = e.spawnOrder(Collections.singletonList("ekmek_arasi"), Collections.singletonList("sosuz"));
        assertEquals(set("domates", "marul", "sogan"), e.getToppingTarget(p.id, 0));
        Order q = e.spawnOrder(Collections.singletonList("ekmek_arasi"), Collections.singletonList("bol soslu"));
        assertEquals(set("domates", "marul", "sogan", "ketcap", "mayonez"), e.getToppingTarget(q.id, 0));
        Order plain = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        assertFalse(plain.hasRequests());
        assertEquals("", plain.requestsText());
        assertEquals(Catalog.product("ekmek_arasi").defaultToppings, e.getToppingTarget(plain.id, 0));
        assertNull("bilinmeyen istek", e.spawnOrder(Collections.singletonList("ekmek_arasi"), Collections.singletonList("uçuk")));
        assertNull("bilinmeyen ürün", e.spawnOrder(Collections.singletonList("yok"), NONE));
    }

    @Test public void requestChanceGrowsWithDays() {
        assertEquals(0.0, DayConfig.requestChance(1), 1e-9);
        assertEquals(0.2, DayConfig.requestChance(2), 1e-9);
        assertEquals(0.45, DayConfig.requestChance(3), 1e-9);
        assertEquals(0.45, DayConfig.requestChance(30), 1e-9);
        assertEquals(0, countRequests(1, 12));
        int d2 = countRequests(2, 12), d6 = countRequests(6, 12);
        assertTrue("2. günde bazı istekler (yüzde): " + d2, d2 > 0 && d2 < 45);
        assertTrue("sonraki günlerde daha sık: " + d2 + " < " + d6, d6 > d2 && d6 > 15);
    }

    /** Gün boyunca gelen siparişlerden isteği olanların oranı (yüzde, tam sayı). */
    private static int countRequests(int day, int seeds) {
        final int[] with = {0}, all = {0};
        for (long seed = 1; seed <= seeds; seed++) {
            SaveData s = SaveData.newGame();
            s.day = day;
            s.stars = 5.0;
            Shop.buyRecommended(s);
            GameEngine e = new GameEngine(seed, s);
            e.addListener(new GameListenerAdapter() {
                @Override public void onCustomerArrived(Order o) { all[0]++; if (o.hasRequests()) with[0]++; }
            });
            for (int i = 0; i < 3000 && e.isRunning(); i++) e.tick(0.1f);
        }
        return all[0] == 0 ? 0 : (int) Math.round(100.0 * with[0] / all[0]);
    }

    @Test public void requestsOnlyWhenTheyChangeSomething() {
        for (long seed = 1; seed <= 20; seed++) {
            SaveData s = day(8);
            GameEngine e = new GameEngine(seed, s);
            final List<Order> seen = new ArrayList<>();
            e.addListener(new GameListenerAdapter() {
                @Override public void onCustomerArrived(Order o) { seen.add(o); }
            });
            for (int i = 0; i < 1500; i++) e.tick(0.1f);
            for (Order o : seen) {
                if (!o.hasRequests()) continue;
                boolean anyDiff = false;
                for (int li = 0; li < o.lineCount(); li++) {
                    if (!o.getLine(li).getTarget().equals(o.getLine(li).product.defaultToppings)) anyDiff = true;
                }
                assertTrue("istek hedefi değiştirmeli: " + o.requestsText(), anyDiff);
                assertTrue(o.requests.size() <= 2);
            }
        }
    }

    // ------------------------------------------------------------ seçim API'si
    @Test public void sauceStationNeedsToppingsAndDoesNotStartTimer() {
        GameEngine e = new GameEngine(1, day(1));
        final List<String> ev = new ArrayList<>();
        e.addListener(new GameListenerAdapter() {
            @Override public void onToppingSelectionNeeded(Order o, int i) { ev.add("need" + i); }
            @Override public void onStepStarted(Order o, Step s, int slot, float sec) { ev.add("started:" + s.name); }
        });
        Order o = openTopping(e, NONE);
        ev.clear();
        assertEquals(StartResult.NEEDS_TOPPINGS, e.startStep(o.id, Station.SOS.id));
        assertEquals(Arrays.asList("need0"), ev);
        assertFalse(o.isRunning());
        assertEquals(1, e.getFreeSlots());
        assertEquals(0, e.getActiveSteps().size());
        assertEquals(0, e.openToppingItem(o.id));
    }

    @Test public void confirmStartsStepWithTimeAndConsumesOnlySelected() {
        SaveData s = day(1);
        GameEngine e = new GameEngine(1, s);
        Order o = openTopping(e, NONE);
        final float[] sl = {0};
        e.addListener(new GameListenerAdapter() {
            @Override public void onStepStarted(Order oo, Step st, int slot, float sec) { sl[0] = sec; }
        });
        int dom = s.inventory.get("domates"), sog = s.inventory.get("sogan"), ket = s.inventory.get("ketcap");
        assertEquals(StartResult.STARTED, e.confirmToppings(o.id, 0, set("domates", "ketcap")));
        assertEquals("temel 2,0 + 0,3 x 2", 2.6f, sl[0], 0.01f);
        assertEquals(dom - 1, s.inventory.get("domates"));
        assertEquals(ket - 1, s.inventory.get("ketcap"));
        assertEquals(sog, s.inventory.get("sogan"));
        assertEquals(set("domates", "ketcap"), o.getLine(0).getChosen());
        assertEquals(StartResult.ORDER_BUSY, e.confirmToppings(o.id, 0, set("domates")));
        Flow.waitDone(e, o);
        assertTrue(o.getLine(0).isDone());
    }

    @Test public void emptySelectionIsAllowed() {
        SaveData s = day(1);
        GameEngine e = new GameEngine(1, s);
        Order o = openTopping(e, NONE);
        final float[] sl = {0};
        e.addListener(new GameListenerAdapter() {
            @Override public void onStepStarted(Order oo, Step st, int slot, float sec) { sl[0] = sec; }
        });
        assertEquals(StartResult.STARTED, e.confirmToppings(o.id, 0, NONE));
        assertEquals(2.0f, sl[0], 0.01f);
        assertTrue(o.getLine(0).getChosen().isEmpty());
    }

    @Test public void unavailableOrForeignToppingsAreRejectedAndNothingConsumed() {
        SaveData s = day(1);
        s.inventory.set("sogan", 0);
        GameEngine e = new GameEngine(1, s);
        Order o = openTopping(e, NONE);
        final List<String> rej = new ArrayList<>();
        e.addListener(new GameListenerAdapter() {
            @Override public void onToppingsRejected(Order oo, int i, StartResult r, Ingredient ing) {
                rej.add(r + ":" + (ing == null ? "-" : ing.id));
            }
        });
        int dom = s.inventory.get("domates");
        assertEquals(StartResult.OUT_OF_STOCK, e.confirmToppings(o.id, 0, set("domates", "sogan")));
        assertEquals(dom, s.inventory.get("domates"));
        assertEquals(Arrays.asList("OUT_OF_STOCK:sogan"), rej);
        assertEquals(1, e.getFreeSlots());
        assertEquals(StartResult.INVALID, e.confirmToppings(o.id, 0, set("pirinc")));
        assertEquals(StartResult.INVALID, e.confirmToppings(o.id, 5, set("domates")));
        assertEquals(StartResult.NO_ORDER, e.confirmToppings(999, 0, set("domates")));
        // acil alımdan sonra seçilebilir
        assertTrue(e.emergencyBuy("sogan"));
        assertEquals(StartResult.STARTED, e.confirmToppings(o.id, 0, set("domates", "sogan")));
    }

    @Test public void confirmBeforeOtherStepsAreDoneIsLocked() {
        GameEngine e = new GameEngine(1, day(1));
        e.setSpawning(false);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        final List<String> ev = new ArrayList<>();
        e.addListener(new GameListenerAdapter() {
            @Override public void onStepLocked(Order oo, Step s) { ev.add(s.name); }
        });
        assertEquals(StartResult.STEP_LOCKED, e.startStep(o.id, Station.SOS.id));
        assertEquals(StartResult.STEP_LOCKED, e.confirmToppings(o.id, 0, set("domates")));
        assertEquals(2, ev.size());
        assertEquals(0, o.getLine(0).doneCount());
    }

    @Test public void optionsPreselectDefaultsOnlyOnDays1To3() {
        for (int d : new int[]{1, 2, 3, 4, 9}) {
            GameEngine e = new GameEngine(1, day(d));
            e.setSpawning(false);
            Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
            List<ToppingOption> opts = e.getToppingOptions(o.id, 0);
            assertEquals(Catalog.product("ekmek_arasi").toppingPool.size(), opts.size());
            Set<String> init = e.getInitialToppings(o.id, 0);
            if (d <= 3) assertEquals("gün " + d, Catalog.product("ekmek_arasi").defaultToppings, init);
            else assertTrue("gün " + d + " boş başlar", init.isEmpty());
            for (ToppingOption op : opts) {
                assertTrue(op.available);
                assertEquals(Catalog.product("ekmek_arasi").defaultToppings.contains(op.ingredient.id), op.isDefault);
            }
        }
    }

    @Test public void optionsShowAvailabilityFromStockAndTray() {
        SaveData s = day(1);
        s.inventory.set("tursu", 0);
        GameEngine e = new GameEngine(1, s);
        e.setSpawning(false);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        boolean found = false;
        for (ToppingOption op : e.getToppingOptions(o.id, 0)) {
            if (op.ingredient.id.equals("tursu")) { found = true; assertFalse(op.available); assertEquals(0, op.stock); }
        }
        assertTrue(found);
        assertTrue(e.getToppingOptions(o.id, 5).isEmpty());
        assertTrue(e.getToppingOptions(999, 0).isEmpty());
        Order ay = e.spawnOrder(Collections.singletonList("ayran"), NONE);
        assertTrue("ayran topping istemez", e.getToppingOptions(ay.id, 0).isEmpty());
        assertEquals(-1, e.openToppingItem(ay.id));
    }

    // ------------------------------------------------------------ doğrulama
    private static final class Rec extends GameListenerAdapter {
        int mistakes = -1; double penalty; boolean perfect; int tip = -1; boolean happy;
        ToppingCheck check;
        @Override public void onOrderMistake(Order o, ToppingCheck c, double p) { mistakes = c.mistakes(); penalty = p; check = c; }
        @Override public void onOrderPerfect(Order o) { perfect = true; }
        @Override public void onOrderDelivered(Order o, int m, int t, boolean h) { tip = t; happy = h; }
    }

    private static Rec deliverWith(Set<String> pick, List<String> requests, SaveData save, double[] starsOut) {
        GameEngine e = new GameEngine(1, save);
        Rec r = new Rec();
        e.addListener(r);
        Order o = openTopping(e, requests);
        assertEquals(StartResult.STARTED, e.confirmToppings(o.id, 0, pick));
        Flow.waitDone(e, o);
        Flow.makeReady(e, o);
        double before = save.stars;
        assertTrue(e.deliver(o.id));
        starsOut[0] = save.stars - before;
        return r;
    }

    @Test public void correctToppingsGivePerfectBonus() {
        double[] d = new double[1];
        Rec r = deliverWith(set("domates", "marul", "sogan", "ketcap"), NONE, day(1), d);
        assertTrue(r.perfect);
        assertEquals(-1, r.mistakes);
        assertTrue("normal kazanç + 0,02 bonus: " + d[0], d[0] >= 0.12 + 0.02 - 1e-9);
        assertTrue(r.tip > 0);
    }

    @Test public void wrongToppingsPenalisedAndTipHalved() {
        double[] good = new double[1], bad = new double[1];
        Rec rg = deliverWith(set("domates", "marul", "sogan", "ketcap"), NONE, day(1), good);
        // 1 hata: soğansız istenmişti
        Rec r1 = deliverWith(set("domates", "marul", "sogan", "ketcap"), Collections.singletonList("soğansız"), day(3), bad);
        assertEquals(1, r1.mistakes);
        assertEquals(0.05, r1.penalty, 1e-9);
        assertFalse(r1.perfect);
        assertEquals(1, r1.check.getMistakes().size());
        ToppingCheck.Mistake m = r1.check.getMistakes().get(0);
        assertEquals("sogan", m.ingredientId);
        assertTrue(m.extra && m.requested);
        assertEquals(bad[0], 0.12 - 0.05, 0.011);
        assertTrue("bahşiş yarıya iner: " + r1.tip + " vs " + rg.tip, r1.tip <= rg.tip / 2 + 1);
        assertFalse("hatalı sipariş memnun sayılmaz", r1.happy);
    }

    @Test public void penaltyCapsAtThreeMistakes() {
        double[] d = new double[1];
        // hiç seçme: 4 eksik
        Rec r = deliverWith(set(), NONE, day(1), d);
        assertEquals(4, r.mistakes);
        assertEquals(0.15, r.penalty, 1e-9);
        for (ToppingCheck.Mistake m : r.check.getMistakes()) assertFalse(m.extra);
        assertEquals(0.12 - 0.15, d[0], 0.011);
    }

    @Test public void extraAndMissingBothCount() {
        double[] d = new double[1];
        Rec r = deliverWith(set("domates", "marul", "sogan", "mayonez"), NONE, day(1), d);
        // ketçap eksik + mayonez fazla
        assertEquals(2, r.mistakes);
        assertEquals(0.10, r.penalty, 1e-9);
        boolean miss = false, extra = false;
        for (ToppingCheck.Mistake m : r.check.getMistakes()) {
            if (m.ingredientId.equals("ketcap") && !m.extra && !m.requested) miss = true;
            if (m.ingredientId.equals("mayonez") && m.extra && !m.requested) extra = true;
        }
        assertTrue(miss && extra);
    }

    @Test public void addRequestMissingIsFlaggedAsRequested() {
        double[] d = new double[1];
        Rec r = deliverWith(set("domates", "marul", "sogan", "ketcap"), Collections.singletonList("acılı"), day(3), d);
        assertEquals(1, r.mistakes);
        ToppingCheck.Mistake m = r.check.getMistakes().get(0);
        assertEquals("aci_sos", m.ingredientId);
        assertFalse(m.extra);
        assertTrue(m.requested);
    }

    @Test public void orderWithoutToppingLinesIsNeitherPerfectNorMistake() {
        SaveData s = day(3);
        GameEngine e = new GameEngine(1, s);
        e.setSpawning(false);
        Rec r = new Rec();
        e.addListener(r);
        Order o = e.spawnOrder(Collections.singletonList("ayran"), NONE);
        Flow.makeReady(e, o);
        assertTrue(e.deliver(o.id));
        assertFalse(r.perfect);
        assertEquals(-1, r.mistakes);
        assertEquals(0, e.getSummary().mistakes);
        assertEquals(0, e.getSummary().perfect);
    }

    @Test public void summaryCountsMistakesAndPerfects() {
        SaveData s = day(3);
        GameEngine e = new GameEngine(1, s);
        e.setSpawning(false);
        Order a = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        Flow.makeReady(e, a); // hedef küme -> kusursuz
        e.deliver(a.id);
        Order b = e.spawnOrder(Collections.singletonList("ekmek_arasi"), Collections.singletonList("soğansız"));
        Flow.doItemStation(e, b, 0, Station.EKMEK);
        Flow.doItemStation(e, b, 0, Station.DONER);
        e.startStep(b.id, Station.SOS.id);
        e.confirmToppings(b.id, 0, Catalog.product("ekmek_arasi").defaultToppings);
        Flow.waitDone(e, b);
        Flow.makeReady(e, b);
        e.deliver(b.id);
        assertEquals(1, e.getSummary().perfect);
        assertEquals(1, e.getSummary().mistakes);
    }

    @Test public void multipleToppingLinesAreEachChecked() {
        SaveData s = day(8);
        GameEngine e = new GameEngine(1, s);
        e.setSpawning(false);
        Rec r = new Rec();
        e.addListener(r);
        Order o = e.spawnOrder(Arrays.asList("ekmek_arasi", "tavuk_ekmek"), Collections.singletonList("marulsuz"));
        // ekmek arası: marul yok? hedef: domates, sogan, ketcap; tavuk: domates, mayonez
        assertEquals(set("domates", "sogan", "ketcap"), e.getToppingTarget(o.id, 0));
        assertEquals(set("domates", "mayonez"), e.getToppingTarget(o.id, 1));
        for (int li = 0; li < 2; li++) {
            Flow.doItemStation(e, o, li, Station.EKMEK);
            Flow.doItemStation(e, o, li, Station.DONER);
            e.startStep(o.id, li, Station.SOS.id);
            // her ikisinde de hedefi seç
            assertEquals(StartResult.STARTED, e.confirmToppings(o.id, li, e.getToppingTarget(o.id, li)));
            Flow.waitDone(e, o);
        }
        Flow.makeReady(e, o);
        e.deliver(o.id);
        assertTrue(r.perfect);
    }

    // ------------------------------------------------------------ anlatım
    @Test public void narratorTargetAndToggleTexts() {
        SaveData s = day(3);
        GameEngine e = new GameEngine(1, s);
        e.setSpawning(false);
        Narrator n = new Narrator(e);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), Arrays.asList("soğansız", "acılı"));
        assertEquals("Hedef, Ekmek arası döner: domates, marul, ketçap ve acı sos. Müşteri isteği: soğansız, acılı.",
                n.toppingTarget(o, 0));
        Order p = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        assertTrue(n.toppingTarget(p, 0).contains("Müşteri isteği yok"));
        Ingredient sogan = Catalog.ingredient("sogan");
        assertEquals("Soğan, eklenmedi", n.toppingState(sogan, false, true));
        assertEquals("Soğan, eklendi", n.toppingState(sogan, true, true));
        assertEquals("Soğan, stokta yok", n.toppingState(sogan, false, false));
        assertEquals("Hiç malzeme seçili değil.", n.toppingSelectionSummary(Collections.<String>emptyList()));
        assertEquals("Seçili: domates ve marul. Toplam 2 malzeme.", n.toppingSelectionSummary(set("domates", "marul")));
        assertTrue(n.customerArrived(o).contains("İstek: soğansız, acılı."));
        assertTrue(n.orderSelected(o).contains("İstek: soğansız, acılı."));
    }

    @Test public void narratorSpeaksToppingEventsAndComplaints() {
        SaveData s = day(3);
        s.inventory.set("sogan", 0);
        GameEngine e = new GameEngine(1, s);
        final List<String> said = new ArrayList<>();
        e.addListener(new Narrator(e, new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { said.add(text); }
        }));
        Order o = openTopping(e, Collections.singletonList("soğansız"));
        said.clear();
        e.startStep(o.id, Station.SOS.id);
        assertTrue(said.toString(), said.get(0).startsWith("Malzeme seçimi: Ekmek arası döner."));
        e.confirmToppings(o.id, 0, set("domates", "sogan"));
        assertTrue(said.toString(), said.get(1).contains("Soğan stokta yok, seçilemez."));
        e.confirmToppings(o.id, 0, set("domates", "marul", "mayonez"));
        Flow.waitDone(e, o);
        Flow.makeReady(e, o);
        said.clear();
        e.deliver(o.id);
        String t = said.get(0);
        assertTrue(t, t.startsWith("Hatalı sipariş."));
        assertTrue(t, t.contains("Ketçap eksik olmuş."));
        assertTrue(t, t.contains("Mayonez fazla olmuş."));
        assertTrue(t, t.contains("bahşiş yarıya indi"));
    }

    @Test public void complaintWordsRequestedRemovalAndAddition() {
        SaveData s = day(3);
        GameEngine e = new GameEngine(1, s);
        e.setSpawning(false);
        Narrator n = new Narrator(e);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), Arrays.asList("soğansız", "acılı"));
        ToppingCheck c = new ToppingCheck();
        c.add(new ToppingCheck.Mistake("sogan", true, true, "Ekmek arası döner"));
        c.add(new ToppingCheck.Mistake("aci_sos", false, true, "Ekmek arası döner"));
        String t = n.orderMistake(o, c, 0.10);
        assertTrue(t, t.contains("Soğan istememiştim."));
        assertTrue(t, t.contains("Acı sos istemiştim."));
    }
}
