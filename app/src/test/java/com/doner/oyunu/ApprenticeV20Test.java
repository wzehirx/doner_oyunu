package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * v2.0 çırak: güvenilir ve öngörülebilir. Kök neden ("bazen çalışıyor bazen çalışmıyor"): çırak seçili siparişe HİÇ
 * dokunmuyordu; arayüz seçimsiz kalınca 1,5 sn'de bir siparişi kendiliğinden seçtiği için tek/iki siparişli günlerde
 * çırağın yapabileceği tek iş çoğu zaman seçili siparişteydi. Ayrıca çırağın sipariş işi duyuruları 4 sn kısıtıyla
 * düşüyordu (yaptığı iş duyulmuyordu).
 */
public class ApprenticeV20Test {
    private static final List<String> NONE = Collections.emptyList();

    private static GameEngine engine(int day, int cirak) {
        SaveData s = ResourceModelTest.day(day, FlowMode.DENGELI);
        if (cirak > 0) s.upgradeLevels.put("cirak", cirak);
        return ResourceModelTest.engine(s);
    }

    private static void run(GameEngine e, float sec) { ResourceModelTest.run(e, sec); }

    static final class Rec extends GameListenerAdapter {
        final List<ApprenticeAction> actions = new ArrayList<>();
        final List<Float> times = new ArrayList<>();
        GameEngine e;
        @Override public void onApprenticeAction(ApprenticeAction a) { actions.add(a); times.add(e.getElapsed()); }
        List<String> orderSteps() {
            List<String> out = new ArrayList<>();
            for (ApprenticeAction a : actions) if (a.started && !a.isPrep()) out.add(a.step.name);
            return out;
        }
    }

    private static Rec rec(GameEngine e) { Rec r = new Rec(); r.e = e; e.addListener(r); return r; }

    /** Oyuncunun durumu: UI tek siparişi kendiliğinden seçer, oyuncu ekmeği/döneri yapar; çırak 1 ayranı ve paketi MUTLAKA yapar. */
    @Test public void level1HelpsTheOnlySelectedOrderEveryTime() {
        for (long seed = 1; seed <= 5; seed++) {
            SaveData s = ResourceModelTest.day(3, FlowMode.DENGELI);
            s.upgradeLevels.put("cirak", 1);
            GameEngine e = new GameEngine(seed, s);
            e.setSpawning(false);
            e.startDay();
            Rec r = rec(e);
            Order o = e.spawnOrder(Arrays.asList("ekmek_arasi", "ayran"), NONE);
            e.selectOrder(o.id);
            int dn = Flow.itemOf(o, "ekmek_arasi");
            assertEquals(StartResult.STARTED, e.startStep(o.id, dn, Station.EKMEK.id));
            run(e, 0.7f);
            assertEquals(StartResult.STARTED, e.startStep(o.id, dn, Station.DONER.id));
            for (int i = 0; i < 100 && !o.getLine(dn).isStepUnlocked(2); i++) e.tick(0.1f);
            assertEquals(StartResult.STARTED, e.applyStationOption(o.id, dn, Station.SOS.id, "auto"));
            for (int i = 0; i < 200 && !o.isReady(); i++) e.tick(0.1f);
            assertTrue("seed " + seed + ": sipariş çırakla paketlendi", o.isReady());
            assertEquals("seed " + seed, Arrays.asList("Ayranı doldur", "Siparişi paketle"), r.orderSteps());
        }
    }

    @Test public void levelRulesAreExact() {
        // her seviyenin yaptığı işler (sipariş adımları); topping hiçbir seviyede çırağa verilmez
        String[][] expect = {
                {"Ayranı doldur", "Siparişi paketle"},
                {"Ayranı doldur", "Ekmeği ısıt", "Siparişi paketle"},
                {"Ayranı doldur", "Döneri dilimle", "Ekmeği ısıt", "Patates kızart", "Siparişi paketle"}};
        for (int lvl = 1; lvl <= 3; lvl++) {
            GameEngine e = engine(3, lvl);
            Rec r = rec(e);
            Order dummy = e.spawnOrder(Collections.singletonList("kutu_icecek"), NONE); // 5. gün ürünü değil: senaryo
            assertNotNull(dummy);
            e.selectOrder(dummy.id);
            Order a = e.spawnOrder(Collections.singletonList("ayran"), NONE);
            Order b = e.spawnOrder(Collections.singletonList("ekmek_arasi+patates"), NONE);
            for (int i = 0; i < 600; i++) { e.tick(0.1f); e.selectOrder(dummy.id); } // oyuncu sürekli eylemde: seçili sipariş dışarıda
            java.util.Set<String> did = new java.util.TreeSet<>(r.orderSteps());
            assertEquals("seviye " + lvl, new java.util.TreeSet<>(Arrays.asList(expect[lvl - 1])), did);
            OrderLine bl = b.getLine(0);
            assertFalse("malzeme hep oyuncuda", bl.isStepDone(bl.product.toppingStepIndex()));
            assertTrue(a.isReady());
        }
    }

    @Test public void idleApprenticeTakesAvailableWorkInTheSameTick() {
        GameEngine e = engine(3, 3);
        Rec r = rec(e);
        Order sel = e.spawnOrder(Collections.singletonList("kutu_icecek"), NONE);
        e.selectOrder(sel.id);
        List<Order> work = new ArrayList<>();
        for (int k = 0; k < 6; k++) work.add(e.spawnOrder(Collections.singletonList("ekmek_arasi+patates"), NONE));
        boolean[] busyEver = new boolean[4];
        for (int i = 0; i < 400; i++) {
            e.tick(0.1f);
            e.selectOrder(sel.id); // oyuncu sürekli eylemde: seçiliye dokunmasın
            // her tick sonunda: eli boş bir çırak varsa, çırağın yapabileceği açık iş kalmamış olmalı
            boolean idle = false;
            for (int w = 1; w <= 3; w++) {
                boolean busy = false;
                for (ActiveStep a : e.getActiveSteps()) if (a.worker == w && a.holdsHands()) busy = true;
                if (busy) busyEver[w] = true; else idle = true;
            }
            if (idle) assertFalse("tick " + i + ": boşta çırak varken alınabilecek iş kaldı", apprenticeWorkLeft(e, work));
        }
        for (int w = 1; w <= 3; w++) assertTrue("çırak " + w + " çalıştı", busyEver[w]);
        int ready = 0;
        for (Order o : work) {
            OrderLine l = o.getLine(0);
            for (int si = 0; si < l.stepCount(); si++) if (si != l.product.toppingStepIndex()) assertTrue(l.isStepDone(si));
            if (o.isReady()) ready++;
        }
        assertEquals("malzeme oyuncuda kaldığı için hiçbiri paketlenmez", 0, ready);
        assertTrue(r.actions.size() > 0);
    }

    /** Seviye 3 çırağın yapabileceği (malzeme dışı), kaynak (stok + istasyon yeri) uygun açık iş var mı. */
    private static boolean apprenticeWorkLeft(GameEngine e, List<Order> orders) {
        for (Order o : orders) {
            if (o.isReady()) continue;
            if (o.canPack()) return true;
            for (OrderLine l : o.getLines()) {
                for (int si = 0; si < l.stepCount(); si++) {
                    Step s = l.step(si);
                    if (!l.isStepOpen(si) || s.isTopping()) continue;
                    if (s.isPassive() && !e.hasStationRoom(s.station)) continue;
                    return true;
                }
            }
        }
        return false;
    }

    @Test public void apprenticeUsesStationCapacityAndPassiveCookIsNotSlowed() {
        GameEngine e = engine(3, 2);
        Order sel = e.spawnOrder(Collections.singletonList("kutu_icecek"), NONE);
        e.selectOrder(sel.id);
        Order a = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        Order b = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        Order c = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        run(e, 0.1f);
        ActiveStep j = null;
        for (ActiveStep x : e.getActiveSteps()) if (x.isApprentice()) j = x;
        assertNotNull(j);
        assertTrue(j.isPassive());
        assertEquals("makine pişirir: çırak yavaşlığı pişirmeye uygulanmaz", 3.5f, j.total, 0.01f);
        assertEquals("koyma %20 yavaş", GameEngine.PASSIVE_LOAD_SECONDS * 1.2f, j.getHandsRemaining(), 0.02f);
        run(e, 1.0f);
        assertEquals("çırak koy-geç: iki ekmek ısıtıcıda", 2, e.getStationUsed(Station.EKMEK));
        // oyuncu üçüncü ekmeği koyamaz: ısıtıcı dolu
        e.selectOrder(c.id);
        StartResult res = e.startStep(c.id, 0, Station.EKMEK.id);
        if (c.getLine(0).isStepRunning(0)) assertEquals(StartResult.ORDER_BUSY, res);
        else assertEquals(StartResult.STATION_FULL, res);
        assertNotNull(a);
        assertNotNull(b);
    }

    @Test public void apprenticeAnnouncementsAreConsistent() {
        GameEngine e = engine(3, 3);
        final List<String> said = new ArrayList<>();
        e.addListener(new Narrator(e, new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { said.add(text); }
        }));
        Order sel = e.spawnOrder(Collections.singletonList("kutu_icecek"), NONE);
        e.selectOrder(sel.id);
        Order a = e.spawnOrder(Collections.singletonList("ayran"), NONE);
        Order b = e.spawnOrder(Collections.singletonList("ayran"), NONE);
        Order c = e.spawnOrder(Collections.singletonList("ayran"), NONE);
        said.clear();
        run(e, 0.2f);
        // üç çırak aynı anda üç sipariş işine başladı: üçü de duyurulur (eski 4 sn kısıtı yalnızca hazırlıkta)
        int starts = 0;
        for (String t : said) if (t.contains("numaralı siparişte: Ayranı doldur")) starts++;
        assertEquals(said.toString(), 3, starts);
        for (Order o : Arrays.asList(a, b, c)) {
            boolean heard = false;
            for (String t : said) if (t.equals("Çırak " + o.getPosition() + " numaralı siparişte: Ayranı doldur.")) heard = true;
            assertTrue(heard);
        }
    }
}
