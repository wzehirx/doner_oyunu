package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * v0.8 Süre baskısı ayarı: kapalıyken (süresiz gün) sayaç yok, sabır sabit, müşteri gitmez, kota kadar müşteri gelip
 * tüm siparişler teslim edilince gün biter; ekonomi (hız bonusu yok, bahşiş x0,8); kayıt uyumu; gün ortasında geçiş.
 */
public class TimePressureTest {
    private static SaveData save(int d, boolean timed) {
        SaveData s = SaveData.newGame();
        s.day = d;
        s.stars = 3.0;
        s.money = 50000;
        s.timePressure = timed;
        Shop.buyRecommended(s);
        s.inventory.set("et_doner", 40);
        s.inventory.set("ekmek", 40);
        return s;
    }

    private static final class Rec extends GameListenerAdapter {
        int warnings, left, delivered, tip = -1, timeEvents;
        boolean lastTimed = true;
        boolean finished, failed;
        DaySummary summary;
        @Override public void onPatienceWarning(Order o, int l) { warnings++; }
        @Override public void onCustomerLeft(Order o) { left++; }
        @Override public void onOrderDelivered(Order o, int money, int t, boolean happy) { delivered++; tip = t; }
        @Override public void onTimePressureChanged(boolean timed) { timeEvents++; lastTimed = timed; }
        @Override public void onDayFinished(DaySummary s) { finished = true; summary = s; }
        @Override public void onDayFailed(DaySummary s) { failed = true; summary = s; }
    }

    // ------------------------------------------------------------------ kayıt
    @Test public void saveDefaultsToTimedAndRoundTrips() {
        assertTrue("yeni oyun süreli", SaveData.newGame().timePressure);
        SaveData s = SaveData.newGame();
        s.timePressure = false;
        SaveData t = SaveData.parse(s.serialize());
        assertFalse(t.timePressure);
        assertEquals(s.serialize(), t.serialize());
        assertTrue(s.serialize().startsWith("v=4\n"));
        assertTrue("copy ayarı korur", !s.copy().timePressure);
        s.timePressure = true;
        assertTrue(SaveData.parse(s.serialize()).timePressure);
    }

    @Test public void oldSavesAreTimed() {
        for (String v : new String[]{"v=1\nday=5\n", "v=2\nday=5\nmoney=900\n", "v=3\nday=9\nstars=3.5\n", "v=4\nday=3\nflow=rahat\nflowChosen=1\n"}) {
            SaveData s = SaveData.parse(v);
            assertTrue("eski kayıt = süreli: " + v, s.timePressure);
        }
        assertFalse(SaveData.parse("v=4\nday=3\ntimer=0\n").timePressure);
        assertTrue(SaveData.parse("v=4\nday=3\ntimer=1\n").timePressure);
        assertTrue("bozuk değer süreli kalır (güvenli varsayılan)", SaveData.parse("v=4\ntimer=belki\n").timePressure);
        assertTrue(SaveData.parse(null).timePressure);
    }

    // ------------------------------------------------------------------ kota
    @Test public void quotaIncludesRestaurantUpgrades() {
        SaveData s = save(10, false);
        DayConfig c = DayConfig.forDay(10);
        assertEquals(c.quota(1.0), DayConfig.quotaFor(s));
        assertEquals(c.quota(1.0), new GameEngine(1, s).getCustomerQuota());
        s.upgradeLevels.put("tabela", 3);
        double m = new Upgrades(s).arrivalMult();
        assertTrue("tabela müşteri çarpanı", m > 1.0);
        assertTrue("yükseltmeli kota daha büyük", DayConfig.quotaFor(s) > c.customerQuota);
        assertEquals(DayConfig.quotaFor(s), new GameEngine(1, s).getCustomerQuota());
    }

    @Test public void quotaDerivesFromDurationOverArrivalInterval() {
        assertEquals(7, DayConfig.forDay(1).customerQuota);
        for (int d = 1; d <= 60; d++) {
            DayConfig c = DayConfig.forDay(d);
            long raw = Math.round(c.duration / c.arrivalInterval * DayConfig.QUOTA_FACTOR);
            assertEquals("gün " + d, Math.max(DayConfig.QUOTA_MIN, Math.min(DayConfig.QUOTA_MAX, raw)), c.customerQuota);
            assertTrue(c.customerQuota >= DayConfig.QUOTA_MIN && c.customerQuota <= DayConfig.QUOTA_MAX);
            assertTrue("yükseltme çarpanı sınırlı", c.quota(5.0) <= DayConfig.QUOTA_MAX && c.quota(5.0) >= c.customerQuota);
            assertEquals("çarpan 1'in altına düşmez", c.customerQuota, c.quota(0.5));
        }
        assertTrue("kota günle artar", DayConfig.forDay(40).customerQuota > DayConfig.forDay(10).customerQuota);
        assertEquals("40. gün: 300 sn / 5,6 sn", 54, DayConfig.forDay(40).customerQuota);
        assertEquals("üst sınır (yükseltmeli, 54 x 1,25 = 67 -> 60)", DayConfig.QUOTA_MAX, DayConfig.forDay(40).quota(9.0));
    }

    // ------------------------------------------------------------------ süresiz gün: sabır, gitme yok, durum
    @Test public void untimedPatienceNeverDropsAndNobodyLeaves() {
        GameEngine e = new GameEngine(5, save(3, false));
        Rec r = new Rec();
        e.addListener(r);
        e.startDay();
        Order o = Flow.firstCustomer(e);
        float p0 = o.customer.getPatienceLeft();
        for (int i = 0; i < 3000; i++) e.tick(0.1f); // 300 sn
        assertEquals("sabır sabit", p0, o.customer.getPatienceLeft(), 1e-4f);
        assertEquals(1f, o.customer.patienceFraction(), 1e-6f);
        assertEquals("sabır uyarısı yok", 0, r.warnings);
        assertEquals("müşteri gitmedi", 0, r.left);
        assertEquals(0, e.getSummary().lost);
        assertEquals("yıldız düşmedi", 3.0, e.getStars(), 1e-9);
        assertEquals(GameEngine.State.RUNNING, e.getState());
        assertFalse("mesai yok", e.isOvertime());
        assertFalse(e.isTimed());
    }

    @Test public void untimedDoNothingBotNeverFinishesAndQueueStaysCapped() {
        for (int d : new int[]{1, 12, 30}) {
            GameEngine e = new GameEngine(9 + d, save(d, false));
            Rec r = new Rec();
            e.addListener(r);
            e.startDay();
            int cap = e.getConfig().baseQueue + e.getUpgrades().queueBonus();
            for (int i = 0; i < 6000; i++) e.tick(0.1f); // 10 dk sanal süre, hiç hareket yok
            assertEquals("gün " + d + ": gün hâlâ sürüyor", GameEngine.State.RUNNING, e.getState());
            assertFalse(r.finished);
            assertFalse(r.failed);
            assertEquals("gün " + d + ": bekleyen sipariş üst sınırda", cap, e.getOrders().size());
            assertTrue("kota aşılmaz", e.getCustomersArrived() <= e.getCustomerQuota());
            assertEquals("kayıp yok", 0, r.left);
            assertEquals(3.0, e.getStars(), 1e-9);
            int rem = e.getRemainingCustomers();
            assertEquals("kalan müşteri = kota - gelen + bekleyen", e.getCustomerQuota() - e.getCustomersArrived() + e.getOrders().size(), rem);
            assertTrue(rem > 0);
        }
    }

    @Test public void untimedStatusTextShowsRemainingCustomersNotTime() {
        GameEngine e = new GameEngine(5, save(2, false));
        Narrator n = new Narrator(e);
        e.addListener(n);
        e.startDay();
        String st = n.statusText();
        assertFalse(st, st.contains("kalan süre"));
        assertFalse(st, st.contains("mesai"));
        assertTrue(st, st.contains("kalan müşteri " + e.getCustomerQuota()));
        String start = n.dayStarted(2, e.getConfig());
        assertTrue(start, start.contains("Bu gün süre yok, " + e.getCustomerQuota() + " müşteri"));
        assertFalse(start, start.contains("Süre "));
        // süreli: eski metin
        GameEngine t = new GameEngine(5, save(2, true));
        Narrator nt = new Narrator(t);
        t.addListener(nt);
        t.startDay();
        assertTrue(nt.statusText().contains("kalan süre"));
        assertFalse(nt.statusText().contains("kalan müşteri"));
        assertTrue(nt.dayStarted(2, t.getConfig()).contains("Süre " + Math.round(t.getConfig().duration) + " saniye"));
    }

    // ------------------------------------------------------------------ süresiz gün: kota ve gün bitişi
    @Test public void untimedDayEndsOnlyAfterQuotaArrivedAndAllDelivered() {
        SaveData s = save(1, false);
        GameEngine e = new GameEngine(11, s);
        Rec r = new Rec();
        e.addListener(r);
        TestBot bot = new TestBot(1.0f);
        bot.playDay(e);
        assertEquals(GameEngine.State.FINISHED, e.getState());
        assertTrue(r.finished);
        assertFalse(r.failed);
        assertEquals("kota kadar müşteri geldi", e.getCustomerQuota(), e.getCustomersArrived());
        assertEquals("hepsi teslim", e.getCustomerQuota(), e.getSummary().delivered);
        assertEquals("kayıp yok", 0, e.getSummary().lost);
        assertFalse(e.getSummary().timed);
        assertEquals(0, e.getRemainingCustomers());
        assertEquals("gün ilerledi", 2, s.day);
        assertEquals("süresiz kayıt korunur", false, s.timePressure);
    }

    @Test public void untimedDayDoesNotEndWhileAnOrderIsPending() {
        GameEngine e = new GameEngine(11, save(1, false));
        e.startDay();
        // kota dolana dek geldiyse ve bir sipariş bekliyorsa bitmez
        for (int i = 0; i < 4000; i++) {
            e.tick(0.1f);
            if (e.getCustomersArrived() == e.getCustomerQuota()) break;
            // kapasite dolunca birini teslim ederek akışı sürdür
            if (e.getOrders().size() >= 2) {
                Order o = e.getOrders().get(0);
                Flow.makeReady(e, o);
                assertTrue(e.deliver(o.id));
            }
        }
        assertEquals(e.getCustomerQuota(), e.getCustomersArrived());
        assertEquals(GameEngine.State.RUNNING, e.getState());
        assertFalse("bekleyen sipariş varken gün bitmez", e.getOrders().isEmpty());
        List<Order> pending = new ArrayList<>(e.getOrders());
        for (int i = 0; i < pending.size(); i++) {
            Order o = pending.get(i);
            Flow.makeReady(e, o);
            assertEquals("teslimden önce gün sürer", GameEngine.State.RUNNING, e.getState());
            assertTrue(e.deliver(o.id));
        }
        for (int i = 0; i < 20 && e.getState() == GameEngine.State.RUNNING; i++) e.tick(0.1f);
        assertEquals(GameEngine.State.FINISHED, e.getState());
    }

    @Test public void untimedArrivalIsPacedAndBounded() {
        GameEngine e = new GameEngine(3, save(5, false));
        e.startDay();
        int cap = e.getConfig().baseQueue + e.getUpgrades().queueBonus();
        int maxSeen = 0;
        int arrivals = 0, last = 0;
        for (int i = 0; i < 1200 && e.getState() == GameEngine.State.RUNNING; i++) {
            e.tick(0.1f);
            maxSeen = Math.max(maxSeen, e.getOrders().size());
            if (e.getCustomersArrived() != last) { arrivals += e.getCustomersArrived() - last; last = e.getCustomersArrived(); }
        }
        assertTrue("bekleyen üst sınırı korunur", maxSeen <= cap);
        assertTrue("kota üstüne çıkmaz", e.getCustomersArrived() <= e.getCustomerQuota());
        // boş sırada beklemek en çok birkaç saniye: bot sırayı boşaltınca hemen yeni müşteri gelir
        GameEngine f = new GameEngine(3, save(5, false));
        f.startDay();
        Order first = Flow.firstCustomer(f);
        Flow.makeReady(f, first);
        f.deliver(first.id);
        int before = f.getCustomersArrived();
        for (int i = 0; i < 30; i++) f.tick(0.1f); // 3 sn
        assertTrue("sıra boşalınca makul sürede yeni müşteri", f.getCustomersArrived() > before || !f.getOrders().isEmpty());
    }

    // ------------------------------------------------------------------ ekonomi
    private static Order readyOrder(GameEngine e) {
        e.setSpawning(false);
        e.startDay();
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), Collections.<String>emptyList());
        assertNotNull(o);
        e.selectOrder(o.id);
        Flow.makeReady(e, o);
        return o;
    }

    @Test public void untimedDeliveryGivesNoSpeedBonusAndReducedTip() {
        GameEngine timed = new GameEngine(2, save(1, true));
        Rec rt = new Rec();
        timed.addListener(rt);
        Order ot = readyOrder(timed);
        double starsBefore = timed.getStars();
        timed.deliver(ot.id);
        double gainTimed = timed.getStars() - starsBefore;
        assertTrue("süreli hızlı teslim >= 0,12 + kusursuz", gainTimed >= 0.12 - 1e-9);

        GameEngine un = new GameEngine(2, save(1, false));
        Rec ru = new Rec();
        un.addListener(ru);
        Order ou = readyOrder(un);
        double sb = un.getStars();
        int priceU = ou.price();
        un.deliver(ou.id);
        double gainUntimed = un.getStars() - sb;
        assertEquals("fark = hız bonusu (0,12 - 0,08)", 0.04, gainTimed - gainUntimed, 1e-9);
        assertTrue("standart memnuniyet 0,08 + olası kusursuz bonus", gainUntimed >= GameEngine.UNTIMED_STAR_GAIN - 1e-9);
        int expectTip = (int) Math.round(priceU * GameEngine.UNTIMED_TIP_RATE * ou.customer.type.tipMult * GameEngine.UNTIMED_TIP_MULT * FlowMode.DEFAULT.tipMult);
        assertEquals("bahşiş = fiyat x %10 x tip x 0,8", expectTip, ru.tip);
        assertTrue("süresiz bahşiş süreli hızlı bahşişten düşük", ru.tip < rt.tip);
        assertEquals("satış geliri normal", ot.price(), un.getSummary().revenue);
    }

    @Test public void untimedMistakePenaltiesAndFailureOnlyFromMistakes() {
        // yanlış istasyon cezası aynen kalır
        GameEngine e = new GameEngine(2, save(1, false));
        Rec r = new Rec();
        e.addListener(r);
        e.setSpawning(false);
        e.startDay();
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), Collections.<String>emptyList());
        e.selectOrder(o.id);
        double s0 = e.getStars();
        assertEquals(StartResult.WRONG_STATION, e.startStep(o.id, -1, Station.TATLI.id));
        assertEquals("yanlış istasyon -0,02", s0 - 0.02, e.getStars(), 1e-9);
        // yıldız < 1,0 yalnızca hatalardan: gün başarısız olur
        SaveData low = save(1, false);
        low.stars = 1.05;
        GameEngine f = new GameEngine(2, low);
        Rec rf = new Rec();
        f.addListener(rf);
        f.setSpawning(false);
        f.startDay();
        Order of = f.spawnOrder(Collections.singletonList("ekmek_arasi"), Collections.<String>emptyList());
        f.selectOrder(of.id);
        for (int i = 0; i < 5 && f.getState() == GameEngine.State.RUNNING; i++) f.startStep(of.id, -1, Station.TATLI.id);
        assertEquals(GameEngine.State.FAILED, f.getState());
        assertTrue(rf.failed);
        assertFalse(rf.summary.timed);
        assertEquals("kayıp/zaman yoluyla değil", 0, rf.left);
        // hatalı malzemeli teslim cezası aynen: istek olmadan fazla malzeme ekle
        GameEngine g = new GameEngine(2, save(1, false));
        g.setSpawning(false);
        g.startDay();
        Order og = g.spawnOrder(Collections.singletonList("ekmek_arasi"), Collections.<String>emptyList());
        g.selectOrder(og.id);
        assertEquals(StartResult.STARTED, g.startStep(og.id, 0, Station.EKMEK.id)); Flow.waitDone(g, og);
        assertEquals(StartResult.STARTED, g.startStep(og.id, 0, Station.DONER.id)); Flow.waitDone(g, og);
        assertEquals(StartResult.STARTED, g.finishToppings(og.id, 0)); // hiç malzeme yok: eksik = hata
        assertEquals(StartResult.STARTED, g.applyStationOption(og.id, -1, Station.PAKET.id, "step")); Flow.waitDone(g, og);
        double before = g.getStars();
        g.deliver(og.id);
        assertTrue("hatalı sipariş cezası uygulanır: 0,08 memnuniyetin altında kazanç", g.getStars() < before + GameEngine.UNTIMED_STAR_GAIN);
        assertEquals(1, g.getSummary().mistakes);
    }

    // ------------------------------------------------------------------ gün ortasında geçiş
    @Test public void togglingMidDayFreezesAndResumesPatience() {
        GameEngine e = new GameEngine(5, save(3, true));
        Rec r = new Rec();
        e.addListener(r);
        e.startDay();
        Order o = Flow.firstCustomer(e);
        for (int i = 0; i < 100; i++) e.tick(0.1f);
        float p1 = o.customer.getPatienceLeft();
        assertTrue("süreli: sabır azalır", p1 < o.customer.patienceMax);
        float tl = e.getTimeLeft();
        e.setTimePressure(false);
        assertEquals(1, r.timeEvents);
        assertFalse(r.lastTimed);
        assertFalse(e.getSave().timePressure);
        for (int i = 0; i < 500; i++) e.tick(0.1f);
        assertEquals("kapalıyken sabır donar", p1, o.customer.getPatienceLeft(), 1e-4f);
        assertEquals("sayaç durur", tl, e.getTimeLeft(), 1e-3f);
        e.setTimePressure(false); // aynı değer: olay yok
        assertEquals(1, r.timeEvents);
        e.setTimePressure(true);
        assertEquals(2, r.timeEvents);
        assertTrue(r.lastTimed);
        for (int i = 0; i < 50; i++) e.tick(0.1f);
        assertTrue("açılınca sabır yine azalır", o.customer.getPatienceLeft() < p1);
        assertTrue(e.isTimed());
    }

    @Test public void narratorAnnouncesToggle() {
        GameEngine e = new GameEngine(5, save(2, true));
        final List<String> said = new ArrayList<>();
        Narrator n = new Narrator(e, new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { said.add(text); }
        });
        e.addListener(n);
        e.startDay();
        said.clear();
        e.setTimePressure(false);
        assertEquals(1, said.size());
        assertTrue(said.get(0), said.get(0).startsWith("Süre baskısı: kapalı"));
        assertTrue(said.get(0), said.get(0).contains("Kalan müşteri: " + e.getRemainingCustomers()));
        e.setTimePressure(true);
        assertTrue(said.get(1), said.get(1).startsWith("Süre baskısı: açık"));
        assertTrue(said.get(1), said.get(1).contains("Kalan süre"));
    }

    // ------------------------------------------------------------------ çırak, hazırlık, akış modları
    @Test public void untimedWorksWithApprenticePrepAndEveryFlowMode() {
        for (FlowMode m : FlowMode.values()) {
            SaveData s = save(8, false);
            s.flowMode = m;
            s.upgradeLevels.put("cirak", 2);
            GameEngine e = new GameEngine(21, s);
            Rec r = new Rec();
            e.addListener(r);
            TestBot bot = new TestBot(1.0f, true, true);
            bot.playDay(e);
            assertEquals(m + ": gün bitti", GameEngine.State.FINISHED, e.getState());
            assertEquals(m + ": hepsi teslim", e.getCustomerQuota(), e.getSummary().delivered);
            assertEquals(0, e.getSummary().lost);
            assertTrue(m + ": hazırlık yapıldı", e.getSummary().prepBatches > 0);
        }
    }

    // ------------------------------------------------------------------ gün içi hazırlık yalnızca istasyon menüsünden
    @Test public void inDayPrepOptionsExistAndWorkFromStationMenusInBothModes() {
        for (boolean timed : new boolean[]{true, false}) {
            SaveData s = save(2, timed);
            s.inventory.set("ayran", 20);
            GameEngine e = new GameEngine(4, s);
            e.setSpawning(false);
            e.startDay();
            Order o = e.spawnOrder(Arrays.asList("ekmek_arasi", "ayran"), Collections.<String>emptyList());
            e.selectOrder(o.id);
            // sipariş beklerken bile istasyon menüsünde hazırlık seçenekleri var
            List<StationOption> don = e.getStationOptions(o.id, -1, Station.DONER.id);
            assertNotNull(timed + " prep:doner:et_doner:4", find(don, "prep:doner:et_doner:4"));
            assertNotNull(find(don, "prep:doner:et_doner:2"));
            assertNotNull(find(e.getStationOptions(o.id, -1, Station.ICECEK.id), "prep:drink"));
            assertNotNull(find(e.getStationOptions(o.id, -1, Station.SOS.id), "prep:tray"));
            assertEquals(StartResult.STARTED, e.applyStationOption(o.id, -1, Station.DONER.id, "prep:doner:et_doner:4"));
            for (int i = 0; i < 80; i++) e.tick(0.1f);
            assertEquals(4, e.getReadyDoner());
            assertEquals(StartResult.STARTED, e.applyStationOption(o.id, -1, Station.ICECEK.id, "prep:drink"));
            for (int i = 0; i < 80; i++) e.tick(0.1f);
            assertTrue(e.getReadyDrinks() > 0);
            assertEquals(StartResult.STARTED, e.applyStationOption(o.id, -1, Station.SOS.id, "prep:tray"));
            for (int i = 0; i < 100; i++) e.tick(0.1f);
            assertTrue(e.getTrayTotal() > 0);
            // hazır stok istasyon etiketinde ve durum okumada
            Narrator n = new Narrator(e);
            assertTrue(n.stationLabel(Station.DONER), n.stationLabel(Station.DONER).contains("hazır 4 porsiyon"));
            assertTrue(n.stationLabel(Station.ICECEK), n.stationLabel(Station.ICECEK).contains("bardak ayran"));
            assertTrue(n.stationLabel(Station.SOS), n.stationLabel(Station.SOS).contains("hazır tepsi"));
            assertTrue(n.statusText().contains("Hazır: döner 4 porsiyon"));
        }
    }

    private static StationOption find(List<StationOption> ops, String id) {
        for (StationOption op : ops) if (op.id.equals(id)) return op;
        return null;
    }
}
