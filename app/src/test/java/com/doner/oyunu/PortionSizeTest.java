package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** v0.6 döner porsiyon boyu: Yarım / Tam / Duble — açılış günleri, fiyat, süre, tüketim, boy hatası, hazır döner sayacı. */
public class PortionSizeTest {

    private static final List<String> NONE = Collections.emptyList();

    private static SaveData day(int d) {
        SaveData s = SaveData.newGame();
        s.flowMode = FlowMode.GERCEKCI; // v0.6 akışı = v0.7 Gerçekçi
        s.day = d;
        s.stars = 4.0;
        s.money = 50000;
        Shop.buyRecommended(s);
        s.inventory.set("et_doner", 30);
        s.inventory.set("tavuk_doner", 30);
        s.inventory.set("ekmek", 30);
        s.inventory.set("lavas", 30);
        return s;
    }

    private static Order spawn(GameEngine e, String product, PortionSize sz) {
        e.setSpawning(false);
        Order o = e.spawnOrder(Collections.singletonList(product), NONE, Collections.singletonList(sz));
        assertNotNull(o);
        return o;
    }

    /** Başlayan oyuncu adımının süresini yakalar. */
    private static final class Rec extends GameListenerAdapter {
        float seconds = -1;
        int mistakes = -1;
        double penalty;
        ToppingCheck check;
        int tip = -1;
        boolean perfect;
        final List<String> log = new ArrayList<>();
        @Override public void onStepStarted(Order o, Step s, int slot, float sec) { seconds = sec; }
        @Override public void onOrderMistake(Order o, ToppingCheck c, double p) { mistakes = c.mistakes(); penalty = p; check = c; }
        @Override public void onOrderDelivered(Order o, int m, int t, boolean h) { tip = t; }
        @Override public void onOrderPerfect(Order o) { perfect = true; }
        @Override public void onSizeChosen(Order o, int i, PortionSize ord, PortionSize ch) { log.add("size:" + ord.id + ">" + ch.id); }
        @Override public void onOptionRejected(Order o, String id, StartResult r) { log.add("opt:" + id + ":" + r); }
    }

    // ------------------------------------------------------------ takvim ve katalog
    @Test public void sizesOpenOnDays1_2_4() {
        assertEquals(1, PortionSize.FULL.unlockDay);
        assertEquals(2, PortionSize.HALF.unlockDay);
        assertEquals(4, PortionSize.DOUBLE.unlockDay);
        assertEquals(1, PortionSize.HALF.halves);
        assertEquals(2, PortionSize.FULL.halves);
        assertEquals(4, PortionSize.DOUBLE.halves);
        assertEquals(0.5, PortionSize.HALF.portions(), 1e-9);
        assertEquals(2.0, PortionSize.DOUBLE.portions(), 1e-9);
        assertEquals(0.60, PortionSize.HALF.priceMult, 1e-9);
        assertEquals(1.00, PortionSize.FULL.priceMult, 1e-9);
        assertEquals(1.70, PortionSize.DOUBLE.priceMult, 1e-9);
        assertEquals(0.6f, PortionSize.HALF.timeMult, 1e-6f);
        assertEquals(1.5f, PortionSize.DOUBLE.timeMult, 1e-6f);
        assertEquals("yarım ekmek", PortionSize.HALF.spoken());
        assertEquals("Tam ekmek", PortionSize.FULL.displayName);
        assertEquals("duble", PortionSize.DOUBLE.spoken());
        assertEquals(PortionSize.DOUBLE, PortionSize.byId("duble"));
        assertNull(PortionSize.byId("x"));
        assertEquals("0,5", PortionSize.halvesText(1));
        assertEquals("3,5", PortionSize.halvesText(7));
        assertEquals("4", PortionSize.halvesText(8));
    }

    @Test public void onlyMeatProductsCarrySizes() {
        for (Product p : Catalog.products()) {
            boolean meat = !p.id.equals("ayran") && !p.id.equals("kutu_icecek") && !p.id.equals("lahmacun") && !p.id.equals("kunefe");
            assertEquals(p.id, meat, p.hasMeat());
        }
        GameEngine e = new GameEngine(1, day(4));
        e.setSpawning(false);
        Order o = e.spawnOrder(Arrays.asList("ayran", "ekmek_arasi"), NONE, Arrays.asList(PortionSize.DOUBLE, PortionSize.DOUBLE));
        assertEquals("boy döner içermeyen kalemde yok sayılır", PortionSize.FULL, o.getLine(0).getSize());
        assertFalse(o.getLine(0).hasSize());
        assertEquals(PortionSize.DOUBLE, o.getLine(1).getSize());
        assertEquals(45 + 408, o.price());
    }

    @Test public void customersAskForSizesOnlyAfterTheyOpen() {
        for (int d : new int[]{1, 2, 3, 4, 7}) {
            Set<PortionSize> seen = EnumSet.noneOf(PortionSize.class);
            for (long seed = 1; seed <= 8; seed++) {
                SaveData s = day(d);
                s.stars = 5.0;
                GameEngine e = new GameEngine(seed, s);
                final Set<PortionSize> got = seen;
                e.addListener(new GameListenerAdapter() {
                    @Override public void onCustomerArrived(Order o) {
                        for (OrderLine l : o.getLines()) if (l.hasSize()) got.add(l.getSize());
                    }
                });
                for (int i = 0; i < 2500 && e.isRunning(); i++) e.tick(0.1f);
            }
            if (d == 1) assertEquals("1. gün yalnızca tam", EnumSet.of(PortionSize.FULL), seen);
            if (d == 2 || d == 3) assertEquals("yarım var, duble yok (gün " + d + ")", EnumSet.of(PortionSize.FULL, PortionSize.HALF), seen);
            if (d >= 4) assertEquals("gün " + d, EnumSet.allOf(PortionSize.class), seen);
        }
    }

    @Test public void pricesFollowSizeMultipliers() {
        GameEngine e = new GameEngine(1, day(4));
        e.setSpawning(false);
        assertEquals(144, spawn(e, "ekmek_arasi", PortionSize.HALF).price());
        assertEquals(240, spawn(e, "ekmek_arasi", PortionSize.FULL).price());
        assertEquals(408, spawn(e, "ekmek_arasi", PortionSize.DOUBLE).price());
        Order mix = e.spawnOrder(Arrays.asList("pilav_ustu", "ayran"), NONE, Arrays.asList(PortionSize.HALF, PortionSize.FULL));
        assertEquals(192 + 45, mix.price());
        assertEquals(192, mix.getLine(0).price());
    }

    @Test public void workEstimateGrowsWithSize() {
        GameEngine e = new GameEngine(1, day(4));
        e.setSpawning(false);
        float h = spawn(e, "ekmek_arasi", PortionSize.HALF).workSeconds();
        float f = spawn(e, "ekmek_arasi", PortionSize.FULL).workSeconds();
        float d = spawn(e, "ekmek_arasi", PortionSize.DOUBLE).workSeconds();
        assertTrue(h + " < " + f + " < " + d, h < f && f < d);
    }

    // ------------------------------------------------------------ boy seçimi: süre ve tüketim
    @Test public void chosenSizeScalesSlicingTime() {
        float[] expected = {2.1f, 3.5f, 5.25f};
        PortionSize[] sizes = PortionSize.values();
        for (int i = 0; i < sizes.length; i++) {
            GameEngine e = new GameEngine(1, day(4));
            Rec r = new Rec();
            e.addListener(r);
            Order o = spawn(e, "ekmek_arasi", sizes[i]);
            assertEquals(StartResult.STARTED, e.applyStationOption(o.id, 0, Station.DONER.id, "size:" + sizes[i].id));
            assertEquals(sizes[i].id, expected[i], r.seconds, 0.001f);
            assertEquals(sizes[i], o.getLine(0).getChosenSize());
            assertEquals(Arrays.asList("size:" + sizes[i].id + ">" + sizes[i].id), r.log);
        }
    }

    @Test public void meatIsConsumedInHalfPortionsWithOpenHalfCounter() {
        SaveData s = day(4);
        GameEngine e = new GameEngine(1, s);
        e.setSpawning(false);
        int et = s.inventory.get("et_doner");
        // yarım: ilk yarım bir porsiyon açar, ikinci yarım açık yarımı kullanır
        Order a = spawn(e, "ekmek_arasi", PortionSize.HALF);
        assertEquals(StartResult.STARTED, e.applyStationOption(a.id, 0, Station.DONER.id, "size:yarim"));
        assertEquals(et - 1, s.inventory.get("et_doner"));
        Flow.waitDone(e, a);
        Order b = spawn(e, "ekmek_arasi", PortionSize.HALF);
        assertEquals(StartResult.STARTED, e.applyStationOption(b.id, 0, Station.DONER.id, "size:yarim"));
        assertEquals("açık yarım kullanıldı, stok değişmez", et - 1, s.inventory.get("et_doner"));
        Flow.waitDone(e, b);
        Order c = spawn(e, "ekmek_arasi", PortionSize.HALF);
        e.applyStationOption(c.id, 0, Station.DONER.id, "size:yarim");
        assertEquals(et - 2, s.inventory.get("et_doner"));
        Flow.waitDone(e, c);
        // tam: 1 porsiyon (açık yarım kalmış: yarısı oradan, yarısı yeni porsiyondan -> toplam 1 porsiyon eksilir)
        Order d = spawn(e, "ekmek_arasi", PortionSize.FULL);
        e.applyStationOption(d.id, 0, Station.DONER.id, "size:tam");
        assertEquals("1 açık yarım + yeni porsiyonun yarısı", et - 3, s.inventory.get("et_doner"));
        Flow.waitDone(e, d);
        // duble: 2 porsiyon = 4 yarım; açık yarım var
        Order f = spawn(e, "ekmek_arasi", PortionSize.DOUBLE);
        e.applyStationOption(f.id, 0, Station.DONER.id, "size:duble");
        assertEquals(et - 5, s.inventory.get("et_doner"));
    }

    @Test public void doublePilavUsesFourPortionsAndHalfUsesOne() {
        SaveData s = day(4);
        GameEngine e = new GameEngine(1, s);
        int et = s.inventory.get("et_doner");
        Order o = spawn(e, "pilav_ustu", PortionSize.DOUBLE);
        Flow.doItemStation(e, o, 0, Station.IZGARA);
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, 0, Station.DONER.id, "size:duble"));
        assertEquals("pilavda temel 2 porsiyon x duble", et - 4, s.inventory.get("et_doner"));
        Order h = spawn(e, "pilav_ustu", PortionSize.HALF);
        Flow.waitDone(e, o);
        Flow.doItemStation(e, h, 0, Station.IZGARA);
        assertEquals(StartResult.STARTED, e.applyStationOption(h.id, 0, Station.DONER.id, "size:yarim"));
        assertEquals(et - 5, s.inventory.get("et_doner"));
    }

    @Test public void breadHeatingUsesOrderedSizeForTimeAndBread() {
        SaveData s = day(4);
        GameEngine e = new GameEngine(1, s);
        Rec r = new Rec();
        e.addListener(r);
        int ekmek = s.inventory.get("ekmek");
        Order h = spawn(e, "ekmek_arasi", PortionSize.HALF);
        assertEquals(StartResult.STARTED, e.applyStationOption(h.id, 0, Station.EKMEK.id, "step"));
        assertEquals("yarım ekmek daha kısa ısınır", 2.1f, r.seconds, 0.001f);
        assertEquals("yarım ekmek: açık yarım", ekmek - 1, s.inventory.get("ekmek"));
        Flow.waitDone(e, h);
        Order d = spawn(e, "ekmek_arasi", PortionSize.DOUBLE);
        e.applyStationOption(d.id, 0, Station.EKMEK.id, "step");
        assertEquals(5.25f, r.seconds, 0.001f);
        assertEquals("duble: 2 ekmek, biri açık yarımdan", ekmek - 3, s.inventory.get("ekmek"));
        Flow.waitDone(e, d);
        Order f = spawn(e, "tavuk_durum", PortionSize.DOUBLE);
        int lavas = s.inventory.get("lavas");
        e.applyStationOption(f.id, 0, Station.EKMEK.id, "step");
        assertEquals(lavas - 2, s.inventory.get("lavas"));
        assertEquals(4.5f, r.seconds, 0.001f);
    }

    @Test public void hatayScalesMeatButNotTheFillingMix() {
        SaveData s = day(6);
        GameEngine e = new GameEngine(1, s);
        s.inventory.set("lahmacun_harci", 10);
        int harc = s.inventory.get("lahmacun_harci"), et = s.inventory.get("et_doner");
        Order o = spawn(e, "hatay_durum", PortionSize.DOUBLE);
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, 0, Station.DONER.id, "size:duble"));
        assertEquals(harc - 1, s.inventory.get("lahmacun_harci"));
        assertEquals(et - 2, s.inventory.get("et_doner"));
    }

    @Test public void legacyStartStepAssumesOrderedSizeSoNoMistake() {
        GameEngine e = new GameEngine(1, day(4));
        Rec r = new Rec();
        e.addListener(r);
        Order o = spawn(e, "ekmek_arasi", PortionSize.DOUBLE);
        assertEquals(StartResult.STARTED, e.startStep(o.id, 0, Station.DONER.id));
        assertEquals(PortionSize.DOUBLE, o.getLine(0).getChosenSize());
        assertEquals(5.25f, r.seconds, 0.001f);
        assertFalse(o.getLine(0).sizeMismatch());
    }

    @Test public void apprenticeSlicesInTheOrderedSize() {
        SaveData s = day(4);
        s.upgradeLevels.put("cirak", 3);
        GameEngine e = new GameEngine(1, s);
        Order o = spawn(e, "ekmek_arasi", PortionSize.HALF);
        for (int i = 0; i < 200; i++) e.tick(0.1f);
        assertEquals(PortionSize.HALF, o.getLine(0).getChosenSize());
        assertFalse(o.getLine(0).sizeMismatch());
    }

    // ------------------------------------------------------------ boy hatası
    private static Rec deliverWith(PortionSize ordered, PortionSize chosen, SaveData save, double[] starsDelta) {
        GameEngine e = new GameEngine(1, save);
        Rec r = new Rec();
        e.addListener(r);
        Order o = spawn(e, "ekmek_arasi", ordered);
        Flow.doItemStation(e, o, 0, Station.EKMEK);
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, 0, Station.DONER.id, "size:" + chosen.id));
        Flow.waitDone(e, o);
        Flow.doItemStation(e, o, 0, Station.SOS);
        Flow.makeReady(e, o);
        double before = save.stars;
        assertTrue(e.deliver(o.id));
        starsDelta[0] = save.stars - before;
        if (r.mistakes < 0) assertEquals(0, e.getSummary().sizeMistakes);
        return r;
    }

    @Test public void correctSizeIsPerfectWrongSizeIsAMistake() {
        for (PortionSize sz : PortionSize.values()) {
            double[] d = new double[1];
            Rec r = deliverWith(sz, sz, day(4), d);
            assertTrue("doğru boy kusursuz: " + sz, r.perfect);
            assertEquals(-1, r.mistakes);
        }
    }

    @Test public void tooBigSizeIsAMistakeWithStarPenaltyAndHalvedTip() {
        double[] good = new double[1], bad = new double[1];
        Rec rg = deliverWith(PortionSize.HALF, PortionSize.HALF, day(4), good);
        Rec rb = deliverWith(PortionSize.HALF, PortionSize.FULL, day(4), bad);
        assertEquals(1, rb.mistakes);
        assertEquals(0.05, rb.penalty, 1e-9);
        assertEquals(1, rb.check.sizeMistakeCount());
        assertEquals(0, rb.check.ingredientMistakes());
        ToppingCheck.SizeMistake m = rb.check.getSizeMistakes().get(0);
        assertEquals(PortionSize.HALF, m.ordered);
        assertEquals(PortionSize.FULL, m.served);
        assertTrue(m.tooMuch());
        assertFalse(rb.perfect);
        assertTrue("bahşiş yarıya iner: " + rb.tip + " vs " + rg.tip, rb.tip <= rg.tip / 2 + 1);
        assertTrue("yıldız kazancı ceza kadar düşer", bad[0] < good[0] - 0.04);
    }

    @Test public void tooSmallSizeIsAMistakeToo() {
        double[] d = new double[1];
        Rec r = deliverWith(PortionSize.DOUBLE, PortionSize.FULL, day(4), d);
        assertEquals(1, r.mistakes);
        assertFalse(r.check.getSizeMistakes().get(0).tooMuch());
        r = deliverWith(PortionSize.DOUBLE, PortionSize.HALF, day(4), d);
        assertEquals("iki basamak fark da tek hata", 1, r.mistakes);
    }

    @Test public void sizeAndToppingMistakesAddUpAndSummaryCountsThem() {
        SaveData s = day(4);
        GameEngine e = new GameEngine(1, s);
        Rec r = new Rec();
        e.addListener(r);
        Order o = spawn(e, "ekmek_arasi", PortionSize.HALF);
        Flow.doItemStation(e, o, 0, Station.EKMEK);
        e.applyStationOption(o.id, 0, Station.DONER.id, "size:duble");
        Flow.waitDone(e, o);
        e.finishToppings(o.id, 0); // hiç malzeme eklenmedi: 4 eksik
        Flow.makeReady(e, o);
        e.deliver(o.id);
        assertEquals(5, r.mistakes);
        assertEquals("ceza tavanı", 0.15, r.penalty, 1e-9);
        assertEquals(1, e.getSummary().mistakes);
        assertEquals(1, e.getSummary().sizeMistakes);
    }

    @Test public void lockedSizeIsRejectedUnlessOrdered() {
        GameEngine e = new GameEngine(1, day(1));
        Rec r = new Rec();
        e.addListener(r);
        Order o = spawn(e, "ekmek_arasi", PortionSize.FULL);
        int et = e.getSave().inventory.get("et_doner");
        assertEquals(StartResult.LOCKED, e.applyStationOption(o.id, 0, Station.DONER.id, "size:yarim"));
        assertEquals(StartResult.LOCKED, e.applyStationOption(o.id, 0, Station.DONER.id, "size:duble"));
        assertEquals(Arrays.asList("opt:size:yarim:LOCKED", "opt:size:duble:LOCKED"), r.log);
        assertEquals(et, e.getSave().inventory.get("et_doner"));
        assertEquals(1, e.getFreeSlots());
        assertEquals(StartResult.UNKNOWN_OPTION, e.applyStationOption(o.id, 0, Station.DONER.id, "size:dev"));
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, 0, Station.DONER.id, "size:tam"));
        // senaryo: 1. günde duble sipariş edilmişse seçilebilir (çıkmaza girmez)
        GameEngine e2 = new GameEngine(1, day(1));
        Order p = spawn(e2, "ekmek_arasi", PortionSize.DOUBLE);
        assertEquals(StartResult.STARTED, e2.applyStationOption(p.id, 0, Station.DONER.id, "size:duble"));
    }

    @Test public void sizeOptionOnlyAtDonerStationAndOnlyForMeatSteps() {
        GameEngine e = new GameEngine(1, day(4));
        Order o = spawn(e, "ekmek_arasi", PortionSize.FULL);
        assertEquals(StartResult.UNKNOWN_OPTION, e.applyStationOption(o.id, 0, Station.EKMEK.id, "size:tam"));
        Order a = spawn(e, "ayran", PortionSize.FULL);
        assertEquals(StartResult.WRONG_STATION, e.applyStationOption(a.id, 0, Station.DONER.id, "size:tam"));
    }

    // ------------------------------------------------------------ menü seçenekleri
    @Test public void donerStationOptionsListSizesWithLocksByDay() {
        GameEngine e1 = new GameEngine(1, day(1));
        Order o1 = spawn(e1, "ekmek_arasi", PortionSize.FULL);
        List<StationOption> l1 = e1.getStationOptions(o1.id, 0, Station.DONER.id);
        StationOption half = find(l1, "size:yarim"), full = find(l1, "size:tam"), dbl = find(l1, "size:duble");
        assertNotNull(half);
        assertFalse(half.enabled);
        assertEquals("2. günde açılır", half.lockReason);
        assertTrue(full.enabled);
        assertNull(full.lockReason);
        assertFalse(dbl.enabled);
        assertEquals("4. günde açılır", dbl.lockReason);
        assertEquals("Yarım ekmek, 2. günde açılır", half.label());
        assertEquals("Yarım ekmek", half.name);
        assertEquals("Tam ekmek", full.name);
        assertEquals("Duble", dbl.name);
        assertEquals(StationOption.Group.SIZE, half.group);

        GameEngine e4 = new GameEngine(1, day(4));
        Order o4 = spawn(e4, "ekmek_arasi", PortionSize.HALF);
        List<StationOption> l4 = e4.getStationOptions(o4.id, 0, Station.DONER.id);
        for (String id : new String[]{"size:yarim", "size:tam", "size:duble"}) assertTrue(id, find(l4, id).enabled);
        assertEquals("Yarım ekmek", find(l4, "size:yarim").description);
        assertEquals("Tam ekmek", find(l4, "size:tam").description);
        assertEquals("Duble", find(l4, "size:duble").description);
        // doğru boyu işaretlemez: seçenekler siparişe göre farklı görünmez
        Order o4b = spawn(e4, "ekmek_arasi", PortionSize.DOUBLE);
        assertEquals(find(l4, "size:tam").description, find(e4.getStationOptions(o4b.id, 0, Station.DONER.id), "size:tam").description);
    }

    @Test public void sizeOptionsDisabledWhileSlicingIsRunning() {
        GameEngine e = new GameEngine(1, day(4));
        Order o = spawn(e, "ekmek_arasi", PortionSize.FULL);
        e.applyStationOption(o.id, 0, Station.DONER.id, "size:tam");
        StationOption full = find(e.getStationOptions(o.id, 0, Station.DONER.id), "size:tam");
        assertFalse(full.enabled);
        assertEquals("Dilimleme sürüyor", full.lockReason);
        Flow.waitDone(e, o);
        assertNull("dilimleme bitince boy seçenekleri kalkar", find(e.getStationOptions(o.id, 0, Station.DONER.id), "size:tam"));
    }

    @Test public void outOfStockSizeStaysEnabledButFlagged() {
        SaveData s = day(4);
        s.inventory.set("et_doner", 0);
        GameEngine e = new GameEngine(1, s);
        Order o = spawn(e, "ekmek_arasi", PortionSize.FULL);
        StationOption full = find(e.getStationOptions(o.id, 0, Station.DONER.id), "size:tam");
        assertTrue(full.enabled);
        assertTrue(full.outOfStock);
        assertTrue(full.description.endsWith("malzeme yok"));
        assertEquals(StartResult.OUT_OF_STOCK, e.applyStationOption(o.id, 0, Station.DONER.id, "size:tam"));
        assertEquals("et_doner", e.missingIngredient(o.id).id);
        assertTrue(e.emergencyBuyMissing(o.id));
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, 0, Station.DONER.id, "size:tam"));
    }

    @Test public void emergencyBuyForDoubleBuysEnoughMeat() {
        SaveData s = day(4);
        s.inventory.set("et_doner", 1);
        GameEngine e = new GameEngine(1, s);
        Order o = spawn(e, "ekmek_arasi", PortionSize.DOUBLE);
        assertEquals(StartResult.OUT_OF_STOCK, e.applyStationOption(o.id, 0, Station.DONER.id, "size:duble"));
        assertEquals("duble 2 porsiyon ister, 1 var", 1, e.missingIngredients(o.id).size());
        assertTrue(e.emergencyBuyMissing(o.id));
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, 0, Station.DONER.id, "size:duble"));
    }

    private static StationOption find(List<StationOption> l, String id) {
        for (StationOption o : l) if (o.id.equals(id)) return o;
        return null;
    }

    // ------------------------------------------------------------ hazır dilimlenmiş döner: yarım porsiyon sayacı
    private static void run(GameEngine e, float sec) { for (float t = 0; t < sec; t += 0.1f) e.tick(0.1f); }

    @Test public void readyDonerIsCountedInHalfPortions() {
        SaveData s = day(4);
        GameEngine e = new GameEngine(1, s);
        e.beginPrep();
        int et = s.inventory.get("et_doner");
        assertEquals(PrepResult.STARTED, e.startPrepDoner("et_doner"));
        assertEquals(et - 4, s.inventory.get("et_doner"));
        run(e, 6.2f);
        assertEquals(8, e.getReadyMeatHalves("et_doner"));
        assertEquals(4, e.getReadyMeat("et_doner"));
        assertEquals(4, e.getReadyDoner());
        assertEquals(8, e.getReadyDonerHalves());
        assertEquals(PrepResult.STARTED, e.startPrepDoner("et_doner", 2));
        assertEquals(et - 6, s.inventory.get("et_doner"));
        run(e, 3.7f);
        assertEquals(12, e.getReadyMeatHalves("et_doner"));
        assertEquals(6, e.getReadyDoner());
        assertEquals("kapasite 8 porsiyon: 6 + 4 > 8", PrepResult.FULL, e.startPrepDoner("et_doner"));
        assertEquals(PrepResult.INVALID, e.startPrepDoner("et_doner", 3));
        assertEquals(PrepResult.INVALID, e.startPrepDoner("et_doner", 0));
        assertEquals(PrepResult.STARTED, e.startPrepDoner("et_doner", 2));
        run(e, 3.7f);
        assertEquals(16, e.getReadyMeatHalves("et_doner"));
        assertEquals(PrepResult.FULL, e.startPrepDoner("et_doner", 2));
    }

    @Test public void twoPortionPrepTakesLessTimeThanFour() {
        GameEngine e = new GameEngine(1, day(4));
        final float[] sec = {0, 0};
        e.addListener(new GameListenerAdapter() {
            @Override public void onPrepStarted(PrepKind k, boolean app, float s) { if (sec[0] == 0) sec[0] = s; else sec[1] = s; }
        });
        e.beginPrep();
        e.startPrepDoner("et_doner", 4);
        run(e, 6.5f);
        e.startPrepDoner("et_doner", 2);
        assertEquals(6.0f, sec[0], 0.01f);
        assertEquals(3.6f, sec[1], 0.01f);
    }

    @Test public void readyDonerIsUsedByHalfFullAndDoubleInHalfUnits() {
        SaveData s = day(4);
        GameEngine e = new GameEngine(1, s);
        e.beginPrep();
        e.startPrepDoner("et_doner");
        run(e, 6.2f);
        e.startDay();
        Rec r = new Rec();
        e.addListener(r);
        int et = s.inventory.get("et_doner");
        Order a = spawn(e, "ekmek_arasi", PortionSize.HALF);
        assertEquals(StartResult.STARTED, e.applyStationOption(a.id, 0, Station.DONER.id, "size:yarim"));
        assertEquals("yarım ekmek hazırdan 0,5 porsiyon düşer", 7, e.getReadyMeatHalves("et_doner"));
        assertEquals(3, e.getReadyMeat("et_doner"));
        assertEquals("stoktan düşmez", et, s.inventory.get("et_doner"));
        assertEquals("hazır dilimle çok hızlı (en az 0,5 sn)", 0.5f, r.seconds, 0.001f);
        Flow.waitDone(e, a);
        Narrator n = new Narrator(e);
        assertEquals("Hazır: döner 3,5 porsiyon.", n.readyStockText());
        Order b = spawn(e, "ekmek_arasi", PortionSize.DOUBLE);
        assertEquals(StartResult.STARTED, e.applyStationOption(b.id, 0, Station.DONER.id, "size:duble"));
        assertEquals(3, e.getReadyMeatHalves("et_doner"));
        assertEquals(1.2f, r.seconds, 0.001f);
        Flow.waitDone(e, b);
        Order c = spawn(e, "ekmek_arasi", PortionSize.DOUBLE);
        e.applyStationOption(c.id, 0, Station.DONER.id, "size:duble");
        assertEquals("hazır yetmedi (3 < 4): hazır dokunulmaz, stoktan 2 porsiyon", 3, e.getReadyMeatHalves("et_doner"));
        assertEquals(et - 2, s.inventory.get("et_doner"));
        assertEquals(5.25f, r.seconds, 0.001f);
    }

    @Test public void leftoverHalfPortionsAreWastedAtDayEnd() {
        SaveData s = day(4);
        GameEngine e = new GameEngine(1, s);
        e.beginPrep();
        e.startPrepDoner("et_doner");
        run(e, 6.2f);
        e.startDay();
        Order a = spawn(e, "ekmek_arasi", PortionSize.HALF);
        e.applyStationOption(a.id, 0, Station.DONER.id, "size:yarim");
        Flow.waitDone(e, a);
        Flow.makeReady(e, a);
        e.deliver(a.id);
        e.setSpawning(false);
        e.tick(1000f);
        assertEquals(GameEngine.State.FINISHED, e.getState());
        assertEquals(0, e.getReadyDonerHalves());
        assertEquals("7 yarım = 4 porsiyon sayılır", 4, e.getSummary().wastedPortions);
        assertEquals((7 * 68 + 1) / 2, e.getSummary().wastedCost);
    }

    @Test public void donerPrepOptionsAreListedWithoutAnOrder() {
        GameEngine e = new GameEngine(1, day(2)); // menüde tek döner (et): kısa adlar
        List<StationOption> l = e.getStationOptions(-1, -1, Station.DONER.id);
        assertEquals(Arrays.asList("prep:doner:et_doner:4", "prep:doner:et_doner:2"), ids(l));
        StationOption four = l.get(0);
        assertEquals("4 porsiyon dilimle", four.name);
        assertEquals("2 porsiyon dilimle", l.get(1).name);
        assertEquals(StationOption.Group.PREP, four.group);
        assertTrue(four.enabled);
        assertEquals(StartResult.STARTED, e.applyStationOption(-1, -1, Station.DONER.id, "prep:doner:et_doner:2"));
        assertEquals("meşgulken kilitli", "Bir işlem sürüyor", e.getStationOptions(-1, -1, Station.DONER.id).get(0).lockReason);
        run(e, 4f);
        assertEquals(4, e.getReadyMeatHalves("et_doner"));
        assertEquals(StartResult.STARTED, e.applyStationOption(-1, -1, Station.DONER.id, "prep:doner:et_doner:4"));
        run(e, 7f);
        assertEquals(12, e.getReadyMeatHalves("et_doner"));
        assertEquals(StartResult.FULL, e.applyStationOption(-1, -1, Station.DONER.id, "prep:doner:et_doner:4"));
        assertEquals("kapasite dolunca kilitli", "Hazır stok dolu", e.getStationOptions(-1, -1, Station.DONER.id).get(0).lockReason);
    }

    private static List<String> ids(List<StationOption> l) {
        List<String> out = new ArrayList<>();
        for (StationOption o : l) out.add(o.id);
        return out;
    }
}
