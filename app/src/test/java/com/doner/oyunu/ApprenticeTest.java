package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** v0.5 çırak: otomatik işçi, seviyeye göre yetenekler, öncelik, seçili siparişe dokunmama, hazırlık, duyurular. */
public class ApprenticeTest {

    private static final List<String> NONE = Collections.emptyList();

    private static SaveData save(int day, int cirak) {
        SaveData s = SaveData.newGame();
        s.day = day;
        s.stars = 5.0;
        s.money = 50000;
        if (cirak > 0) s.upgradeLevels.put("cirak", cirak);
        Shop.buyRecommended(s);
        return s;
    }

    private static GameEngine engine(int day, int cirak) {
        GameEngine e = new GameEngine(7, save(day, cirak));
        e.setSpawning(false);
        e.startDay();
        return e;
    }

    private static Order spawn(GameEngine e, String... products) {
        Order o = e.spawnOrder(Arrays.asList(products), NONE);
        assertNotNull(o);
        return o;
    }

    private static void run(GameEngine e, float seconds) {
        for (float t = 0; t < seconds; t += 0.1f) e.tick(0.1f);
    }

    private static final class Rec extends GameListenerAdapter {
        final List<ApprenticeAction> actions = new ArrayList<>();
        int playerStarted;
        @Override public void onApprenticeAction(ApprenticeAction a) { actions.add(a); }
        @Override public void onStepStarted(Order o, Step s, int slot, float sec) { playerStarted++; }
        int started() { int n = 0; for (ApprenticeAction a : actions) if (a.started) n++; return n; }
        int finished() { int n = 0; for (ApprenticeAction a : actions) if (!a.started) n++; return n; }
    }

    // ------------------------------------------------------------ seviye 0
    @Test public void noApprenticeNoAutomaticWork() {
        GameEngine e = engine(3, 0);
        Rec r = new Rec();
        e.addListener(r);
        Order a = spawn(e, "ayran"), b = spawn(e, "ayran");
        e.selectOrder(a.id);
        run(e, 30f);
        assertEquals(0, r.actions.size());
        assertEquals(0, a.getLine(0).doneCount());
        assertEquals(0, b.getLine(0).doneCount());
        assertEquals(0, e.getApprenticeLevel());
    }

    // ------------------------------------------------------------ seviye 1
    @Test public void level1PacksUnselectedOrderButNeverTheSelectedOne() {
        GameEngine e = engine(3, 1);
        Rec r = new Rec();
        e.addListener(r);
        Order a = spawn(e, "ayran"), b = spawn(e, "ayran");
        e.selectOrder(a.id);
        // oyuncu A'nın ayranını yapar; çırak da B'nin ayranını doldurur/paketler
        Flow.doItemStation(e, a, 0, Station.ICECEK);
        assertTrue(a.canPack());
        run(e, 15f);
        assertFalse("seçili siparişe çırak dokunmaz", a.isReady() || a.isPacking());
        assertTrue("seçili olmayan sipariş çırakla hazır", b.isReady());
        // seçimi bırakınca çırak A'yı paketler
        e.selectOrder(b.id);
        run(e, 4f);
        assertTrue(a.isReady());
        boolean packed = false;
        for (ApprenticeAction x : r.actions) if (x.pack && !x.started && x.order == a) packed = true;
        assertTrue(packed);
    }

    @Test public void apprenticeWorkDoesNotUseThePlayersSlotAndIsSlower() {
        GameEngine e = engine(3, 1);
        Rec r = new Rec();
        e.addListener(r);
        Order a = spawn(e, "ayran"), b = spawn(e, "ayran");
        e.selectOrder(a.id);
        run(e, 0.2f);
        // çırak B'nin ayranını dolduruyor
        assertEquals(1, e.getActiveSteps().size());
        ActiveStep act = e.getActiveSteps().get(0);
        assertTrue(act.isApprentice());
        assertEquals(1, act.worker);
        assertEquals(-1, act.slot);
        assertEquals(b, act.order);
        assertEquals("ayran 1,8 sn x %20 yavaş", 1.8f * 1.2f, act.total, 0.01f);
        assertEquals(1, e.getFreeSlots());
        assertEquals(1, e.getSlotCount());
        // oyuncu aynı anda kendi işini başlatabilir
        assertEquals(StartResult.STARTED, e.startStep(a.id, Station.ICECEK.id));
        assertEquals(0, e.getFreeSlots());
        assertEquals(2, e.getActiveSteps().size());
        assertEquals("çırak adımları onStepStarted vermez", 1, r.playerStarted);
    }

    @Test public void level1CannotHeatBreadOrSliceOrDoToppings() {
        GameEngine e = engine(3, 1);
        Order dummy = spawn(e, "ayran");
        Order c = spawn(e, "ekmek_arasi");
        e.selectOrder(dummy.id);
        run(e, 40f);
        assertEquals("seviye 1 ekmek ısıtmaz/dilimlemez", 0, c.getLine(0).doneCount());
    }

    // ------------------------------------------------------------ seviye 2-3
    @Test public void level2HeatsBreadButDoesNotSlice() {
        GameEngine e = engine(3, 2);
        Order dummy = spawn(e, "ayran");
        Order c = spawn(e, "ekmek_arasi");
        e.selectOrder(dummy.id);
        run(e, 40f);
        assertTrue("ekmek ısıtıldı", c.getLine(0).isStepDone(0));
        assertFalse("dilimleme seviye 3", c.getLine(0).isStepDone(1));
        assertFalse(c.getLine(0).isDone());
    }

    @Test public void level3SlicesAndGrillsButNeverToppings() {
        GameEngine e = engine(8, 3);
        Order dummy = spawn(e, "ayran");
        Order c = spawn(e, "ekmek_arasi");
        Order z = spawn(e, "zurna_durum");
        Order p = spawn(e, "pilav_ustu");
        e.selectOrder(dummy.id);
        run(e, 120f);
        for (Order o : Arrays.asList(c, z, p)) {
            OrderLine l = o.getLine(0);
            int ti = l.product.toppingStepIndex();
            for (int i = 0; i < l.stepCount(); i++) {
                if (i == ti) assertFalse(o.id + " topping çırağa verilmez", l.isStepDone(i));
                else assertTrue(l.product.name + " adım " + i + " çırakla bitmeli", l.isStepDone(i));
            }
            assertFalse(o.isReady());
        }
    }

    @Test public void apprenticeFinishesKunefeInSequence() {
        GameEngine e = engine(16, 3);
        Order dummy = spawn(e, "ayran");
        Order k = spawn(e, "kunefe");
        e.selectOrder(dummy.id);
        run(e, 80f);
        assertTrue(k.getLine(0).isDone());
        assertTrue(k.isReady());
    }

    @Test public void threeApprenticesWorkInParallel() {
        GameEngine e = engine(3, 3);
        Order dummy = spawn(e, "ayran");
        spawn(e, "ayran");
        spawn(e, "ayran");
        spawn(e, "ayran");
        e.selectOrder(dummy.id);
        run(e, 0.3f);
        int apprentices = 0;
        for (ActiveStep a : e.getActiveSteps()) if (a.isApprentice()) apprentices++;
        assertEquals(3, apprentices);
        assertEquals(1, e.getFreeSlots());
    }

    // ------------------------------------------------------------ öncelik ve stok
    @Test public void mostImpatientOrderFirst() {
        SaveData sv = save(3, 1);
        int stock = sv.inventory.get("ayran");
        sv.inventory.set("ayran", 0); // çırak çalışamasın
        GameEngine e = new GameEngine(7, sv);
        e.setSpawning(false);
        e.startDay();
        Rec r = new Rec();
        e.addListener(r);
        Order dummy = spawn(e, "ayran");
        e.selectOrder(dummy.id);
        Order older = spawn(e, "ayran");
        run(e, 10f);
        Order newer = spawn(e, "ayran");
        run(e, 1f);
        for (ApprenticeAction x : r.actions) assertTrue("ayran yokken sipariş işi yapılamaz", x.isPrep());
        r.actions.clear();
        sv.inventory.set("ayran", stock); // stok geldi: iki aday da hazır
        run(e, 0.3f);
        assertEquals(1, r.started());
        assertEquals("en sabırsız (en eski) önce", older, r.actions.get(0).order);
        assertNotNull(newer);
    }

    @Test public void apprenticeSkipsOutOfStockSilently() {
        SaveData s = save(3, 1);
        s.inventory.set("ayran", 0);
        GameEngine e = new GameEngine(7, s);
        e.setSpawning(false);
        e.startDay();
        final int[] oos = {0};
        e.addListener(new GameListenerAdapter() {
            @Override public void onOutOfStock(Order o, Ingredient i) { oos[0]++; }
        });
        Order a = spawn(e, "ayran"), b = spawn(e, "ayran");
        e.selectOrder(a.id);
        run(e, 10f);
        assertEquals(0, oos[0]);
        assertEquals(0, b.getLine(0).doneCount());
    }

    @Test public void apprenticeConsumesStockNormally() {
        SaveData s = save(3, 1);
        GameEngine e = new GameEngine(7, s);
        e.setSpawning(false);
        e.startDay();
        int ayran = s.inventory.get("ayran");
        Order a = spawn(e, "ayran"), b = spawn(e, "ayran");
        e.selectOrder(a.id);
        run(e, 0.2f);
        assertEquals(ayran - 1, s.inventory.get("ayran"));
    }

    @Test public void apprenticeUsesReadyStock() {
        SaveData s = save(3, 1);
        s.readyDrinks = 4;
        GameEngine e = new GameEngine(7, s);
        e.setSpawning(false);
        e.startDay();
        int ayran = s.inventory.get("ayran");
        Order a = spawn(e, "ayran"), b = spawn(e, "ayran");
        e.selectOrder(a.id);
        run(e, 0.2f);
        assertEquals(3, s.readyDrinks);
        assertEquals(ayran, s.inventory.get("ayran"));
        ActiveStep act = e.getActiveSteps().get(0);
        assertEquals("hazır içecek 0,5 sn x 1,2", 0.6f, act.total, 0.01f);
    }

    @Test public void customerLeavingMidApprenticeWorkIsHandled() {
        SaveData s = save(3, 1);
        GameEngine e = new GameEngine(7, s);
        e.setSpawning(false);
        e.startDay();
        Order a = spawn(e, "ayran"), b = spawn(e, "ayran");
        e.selectOrder(a.id);
        // tüm gün: kimse teslim etmez, müşteriler gider; çökme/takılma olmamalı
        for (int i = 0; i < 20000 && e.isRunning(); i++) e.tick(0.1f);
        assertFalse(e.isRunning());
        assertTrue(e.getActiveSteps().isEmpty());
    }

    // ------------------------------------------------------------ hazırlık (çırak boştayken)
    @Test public void level1PreparesDrinksAndDonerInPrepPhase() {
        SaveData s = save(3, 1);
        s.inventory.set("ayran", 30);
        GameEngine e = new GameEngine(7, s);
        Rec r = new Rec();
        e.addListener(r);
        e.beginPrep();
        run(e, 90f);
        assertEquals(GameEngine.State.PREP, e.getState());
        assertEquals("içecek hedefine kadar (en çok kapasite)", 8, e.getReadyDrinks());
        assertTrue("hazır döner: " + e.getReadyDoner(), e.getReadyDoner() >= 4);
        assertEquals("tepsi seviye 2+", 0, e.getTrayTotal());
        assertTrue(r.started() > 0);
        for (ApprenticeAction a : r.actions) assertTrue(a.isPrep());
        assertEquals(r.started(), r.finished());
    }

    @Test public void level2AlsoPreparesTray() {
        SaveData s = save(3, 2);
        GameEngine e = new GameEngine(7, s);
        e.beginPrep();
        run(e, 120f);
        assertTrue("tepsi hazır: " + e.getTrayTotal(), e.getTrayTotal() > 0);
    }

    @Test public void apprenticePrepDoesNotOverfillCapacity() {
        SaveData s = save(3, 3);
        GameEngine e = new GameEngine(7, s);
        e.beginPrep();
        run(e, 400f);
        assertTrue(e.getReadyDrinks() <= e.getReadyCapacity(PrepKind.DRINK));
        for (Ingredient m : e.getPrepMeats()) assertTrue(e.getReadyMeat(m.id) <= e.getReadyCapacity(PrepKind.DONER));
        for (Ingredient i : e.getPrepTrayChoices()) assertTrue(e.getTrayUses(i.id) <= e.getReadyCapacity(PrepKind.TRAY));
    }

    @Test public void apprenticePrepsWhenIdleDuringTheDay() {
        SaveData s = save(3, 1);
        GameEngine e = new GameEngine(7, s);
        e.setSpawning(false);
        e.startDay();
        run(e, 20f);
        assertTrue("gün içinde boşta hazırlık (içecek)", e.getReadyDrinks() >= 4);
        assertTrue("gün içinde boşta hazırlık (döner)", e.getReadyDoner() >= 4);
    }

    // ------------------------------------------------------------ duyurular
    @Test public void narratorAnnouncesApprenticeThrottledAndRespectsSetting() {
        SaveData s = save(3, 1);
        GameEngine e = new GameEngine(7, s);
        e.setSpawning(false);
        final List<String> said = new ArrayList<>();
        e.addListener(new Narrator(e, new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { said.add(text); }
        }));
        e.startDay();
        Order a = spawn(e, "ayran"), b = spawn(e, "ayran");
        e.selectOrder(a.id);
        said.clear();
        run(e, 8f);
        StringBuilder all = new StringBuilder();
        for (String t : said) all.append(t).append('\n');
        String text = all.toString();
        assertTrue(text, text.contains("Çırak " + b.getPosition() + " numaralı siparişte: Ayranı doldur."));
        assertTrue(text, text.contains("Çırak " + b.getPosition() + " numaralı siparişi paketledi."));
        // kapatılınca sessiz
        s.apprenticeAnnounce = false;
        said.clear();
        Order c = spawn(e, "ayran");
        e.selectOrder(a.id);
        run(e, 10f);
        for (String t : said) assertFalse(t, t.startsWith("Çırak"));
    }

    @Test public void apprenticeActionTexts() {
        GameEngine e = engine(3, 1);
        Narrator n = new Narrator(e);
        Rec r = new Rec();
        e.addListener(r);
        Order a = spawn(e, "ayran"), b = spawn(e, "ayran");
        e.selectOrder(a.id);
        run(e, 8f);
        boolean sawPackStart = false, sawFillStart = false;
        for (ApprenticeAction x : r.actions) {
            String t = n.apprenticeAction(x);
            if (x.pack && x.started) { sawPackStart = true; assertEquals("Çırak " + b.getPosition() + " numaralı siparişi paketliyor.", t); }
            if (!x.pack && x.started && !x.isPrep()) sawFillStart = true;
        }
        assertTrue(sawPackStart && sawFillStart);
    }

    @Test public void summaryCountsApprenticeTasks() {
        SaveData s = save(3, 1);
        GameEngine e = new GameEngine(7, s);
        e.setSpawning(false);
        e.startDay();
        Order a = spawn(e, "ayran"), b = spawn(e, "ayran");
        e.selectOrder(a.id);
        run(e, 10f);
        assertEquals(2, e.getSummary().apprenticeTasks); // doldur + paketle
        assertTrue(e.getSummary().prepBatches >= 0);
    }
}
