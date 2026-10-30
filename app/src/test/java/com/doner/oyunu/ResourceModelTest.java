package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * v2.0 kaynak modeli (docs/design_v20.md): ortak "yuva" yok. Oyuncunun eli aynı anda 1 AKTİF iş yapar; PASİF işler
 * (ekmek/lavaş ısıt, ocakta pişir/mühürle, patates kızart) yalnızca koyarken eli tutar ve istasyonun KENDİ kapasitesini
 * kullanır (ekmek ısıtıcı 2 yer, ocak 2 göz; yükseltmeyle 4).
 */
public class ResourceModelTest {
    private static final List<String> NONE = Collections.emptyList();

    static SaveData day(int d, FlowMode m) {
        SaveData s = SaveData.newGame();
        s.day = d;
        s.money = 100000;
        s.stars = 4.0;
        s.flowMode = m;
        Shop.buyRecommended(s);
        for (Ingredient i : Catalog.ingredients()) s.inventory.set(i.id, 30);
        return s;
    }

    static GameEngine engine(int d, FlowMode m) { return engine(day(d, m)); }

    static GameEngine engine(SaveData s) {
        GameEngine e = new GameEngine(5, s);
        e.setSpawning(false);
        e.startDay();
        return e;
    }

    static void run(GameEngine e, float sec) { for (float t = 0; t < sec - 1e-4f; t += 0.1f) e.tick(0.1f); }

    static Order spawn(GameEngine e, String... ids) {
        Order o = e.spawnOrder(Arrays.asList(ids), NONE);
        assertNotNull(o);
        return o;
    }

    static final class Rec extends GameListenerAdapter {
        final List<String> log = new ArrayList<>();
        ActiveStep lastHands;
        int handsBusy, stationFull, handsFree, noSlot, already;
        Station fullStation;
        int fullUsed, fullCap;
        @Override public void onHandsBusy(Order o, ActiveStep cur) { handsBusy++; lastHands = cur; log.add("hands"); }
        @Override public void onStationFull(Order o, Station st, int used, int cap) {
            stationFull++; fullStation = st; fullUsed = used; fullCap = cap; log.add("full:" + st.displayName);
        }
        @Override public void onHandsFree(ActiveStep job) { handsFree++; log.add("free:" + job.step.name); }
        @Override public void onNoFreeSlot(Order o) { noSlot++; }
        @Override public void onAlreadyRunning(Order o, ActiveStep j) { already++; }
    }

    // ------------------------------------------------------------ oyuncunun geri bildirimi
    /** "Bir müşterinin patatesini kızartırken birinin ekmeğini ısıtıyorum, sonra bir daha ekmek ısıtıyorum." */
    @Test public void breadWarmsWhileFriesFryAndASecondBreadFits() {
        GameEngine e = engine(3, FlowMode.DENGELI);
        Rec r = new Rec();
        e.addListener(r);
        Order a = spawn(e, "ekmek_arasi+patates"), b = spawn(e, "ekmek_arasi"), c = spawn(e, "ekmek_arasi");
        int fry = 2; // ısıt, dilimle, kızart, malzeme
        assertTrue(a.getLine(0).step(fry).isFry());
        assertEquals(StartResult.STARTED, e.startStep(a.id, 0, Station.IZGARA.id));
        run(e, 0.7f);
        assertTrue("patates ocakta kendi kızarır, el boş", e.isHandsFree());
        assertEquals(StartResult.STARTED, e.startStep(b.id, 0, Station.EKMEK.id));
        run(e, 0.7f);
        assertEquals("bir daha ekmek", StartResult.STARTED, e.startStep(a.id, 0, Station.EKMEK.id));
        run(e, 0.7f);
        assertEquals(2, e.getStationUsed(Station.EKMEK));
        assertEquals(1, e.getStationUsed(Station.IZGARA));
        assertEquals("üç iş birden pişiyor, oyuncu elinde iş yok", 3, e.getActiveSteps().size());
        // ısıtıcı dolu: üçüncü ekmek "Ekmek ısıtıcı dolu (2/2)"; eller boş olsa da
        assertEquals(StartResult.STATION_FULL, e.startStep(c.id, 0, Station.EKMEK.id));
        assertEquals(Station.EKMEK, r.fullStation);
        assertEquals(2, r.fullUsed);
        assertEquals(2, r.fullCap);
        assertEquals("Ekmek ısıtıcı dolu (2/2)", e.stationFullText(Station.EKMEK));
        StationOption op = e.getStationOptions(c.id, 0, Station.EKMEK.id).get(0);
        assertFalse(op.enabled);
        assertEquals("Ekmek ısıtıcı dolu (2/2)", op.lockReason);
        // aynı anda aktif iş: dilimleme
        assertEquals(StartResult.STARTED, e.startStep(c.id, 0, Station.DONER.id));
        assertEquals(0, r.noSlot);
        // ilk ekmek bitince yer açılır
        run(e, 3.0f);
        assertTrue(e.hasStationRoom(Station.EKMEK));
    }

    @Test public void ocakFullWithTwoBurnersAndUpgradeAddsOne() {
        for (int lv = 0; lv <= 1; lv++) {
            SaveData s = day(3, FlowMode.DENGELI);
            if (lv > 0) s.upgradeLevels.put("ocak", lv);
            GameEngine e = engine(s);
            Rec r = new Rec();
            e.addListener(r);
            final List<String> said = new ArrayList<>();
            e.addListener(new Narrator(e, new Narrator.Output() {
                @Override public void say(String text, boolean urgent) { said.add(text); }
            }));
            Order[] os = {spawn(e, "ekmek_arasi+patates"), spawn(e, "ekmek_arasi+patates"), spawn(e, "ekmek_arasi+patates")};
            assertEquals(2 + lv, e.getStationCapacity(Station.IZGARA));
            for (int k = 0; k < 2; k++) {
                assertEquals(StartResult.STARTED, e.startStep(os[k].id, 0, Station.IZGARA.id));
                run(e, 0.7f);
            }
            StartResult third = e.startStep(os[2].id, 0, Station.IZGARA.id);
            if (lv == 0) {
                assertEquals(StartResult.STATION_FULL, third);
                assertEquals(1, r.stationFull);
                assertEquals(0, r.handsBusy);
                boolean heard = false;
                for (String t : said) if (t.startsWith("Ocak dolu (2/2).")) heard = true;
                assertTrue(said.toString(), heard);
                // hazırlık da aynı gözleri kullanır
                assertEquals(PrepResult.STATION_FULL, e.startPrepFries());
            } else {
                assertEquals("geniş ocak: 3. göz", StartResult.STARTED, third);
            }
        }
    }

    @Test public void handsBusyIsAnnouncedSeparatelyFromStationFull() {
        GameEngine e = engine(3, FlowMode.DENGELI);
        Rec r = new Rec();
        e.addListener(r);
        final List<String> said = new ArrayList<>();
        e.addListener(new Narrator(e, new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { said.add(text); }
        }));
        Order a = spawn(e, "ekmek_arasi", "ayran");
        int dn = Flow.itemOf(a, "ekmek_arasi"), ay = Flow.itemOf(a, "ayran");
        assertEquals(StartResult.STARTED, e.startStep(a.id, dn, Station.DONER.id));
        // dilimleme aktif: ikinci aktif iş (ayran) de, pasif iş (ekmek koymak) da el ister
        assertEquals(StartResult.HANDS_BUSY, e.startStep(a.id, ay, Station.ICECEK.id));
        assertEquals(StartResult.HANDS_BUSY, e.startStep(a.id, dn, Station.EKMEK.id));
        assertEquals(StartResult.HANDS_BUSY, e.applyStationOption(a.id, ay, Station.ICECEK.id, "step"));
        assertEquals(PrepResult.HANDS_BUSY, e.startPrepDrink());
        assertEquals(4, r.handsBusy);
        assertEquals("Döneri dilimle", r.lastHands.step.name);
        assertEquals(0, r.stationFull);
        String last = said.get(said.size() - 1);
        assertTrue(last, last.startsWith("Elin dolu"));
        boolean heard = false;
        for (String t : said) if (t.startsWith("Elin dolu: Ekmek arası döner: Döneri dilimle sürüyor")) heard = true;
        assertTrue(said.toString(), heard);
        for (String t : said) assertFalse("eski yuva metni yok: " + t, t.contains("yuva"));
        assertFalse(e.isHandsFree());
        assertSame(r.lastHands, e.getHandsJob());
        StationOption drink = e.getStationOptions(a.id, ay, Station.ICECEK.id).get(0);
        assertEquals("Elin dolu", drink.lockReason);
    }

    @Test public void passiveJobHoldsHandsOnlyForTheLoadTimeAndUsesPlaces() {
        GameEngine e = engine(3, FlowMode.DENGELI);
        Rec r = new Rec();
        e.addListener(r);
        Order a = spawn(e, "ekmek_arasi"), b = spawn(e, "ekmek_arasi");
        assertEquals(StartResult.STARTED, e.startStep(a.id, 0, Station.EKMEK.id));
        ActiveStep j = e.getHandsJob();
        assertNotNull(j);
        assertTrue(j.isPassive());
        assertTrue(j.holdsHands());
        assertEquals(GameEngine.PASSIVE_LOAD_SECONDS, j.getHandsRemaining(), 0.01f);
        assertEquals(0, j.getPlace());
        assertEquals(Station.EKMEK, j.getStation());
        assertEquals(3.5f, j.total, 0.01f);
        run(e, 0.5f);
        assertFalse(e.isHandsFree());
        run(e, 0.2f);
        assertTrue(e.isHandsFree());
        assertEquals(1, r.handsFree);
        assertFalse(j.holdsHands());
        assertEquals(StartResult.STARTED, e.startStep(b.id, 0, Station.EKMEK.id));
        ActiveStep j2 = e.getHandsJob();
        assertEquals("ikinci yer", 1, j2.getPlace());
        // tempo koyma süresini de ölçekler
        SaveData slow = day(3, FlowMode.DENGELI);
        slow.setTempo(1.5);
        GameEngine e2 = engine(slow);
        Order c = spawn(e2, "ekmek_arasi");
        e2.startStep(c.id, 0, Station.EKMEK.id);
        assertEquals(0.9f, e2.getHandsJob().getHandsRemaining(), 0.01f);
    }

    @Test public void activeJobsHoldHandsUntilTheyFinish() {
        GameEngine e = engine(3, FlowMode.DENGELI);
        Order a = spawn(e, "ayran"), b = spawn(e, "ayran");
        assertEquals(StartResult.STARTED, e.startStep(a.id, 0, Station.ICECEK.id));
        ActiveStep j = e.getHandsJob();
        assertFalse(j.isPassive());
        assertEquals(-1, j.getPlace());
        run(e, 1.5f);
        assertEquals(StartResult.HANDS_BUSY, e.startStep(b.id, 0, Station.ICECEK.id));
        run(e, 0.4f);
        assertEquals(StartResult.STARTED, e.startStep(b.id, 0, Station.ICECEK.id));
    }

    @Test public void stepStartedSaysHandsAreFreeForPassiveJobs() {
        GameEngine e = engine(3, FlowMode.DENGELI);
        final List<String> said = new ArrayList<>();
        e.addListener(new Narrator(e, new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { said.add(text); }
        }));
        Order a = spawn(e, "ekmek_arasi");
        said.clear();
        e.startStep(a.id, 0, Station.EKMEK.id);
        assertEquals("Ekmeği ısıt başladı, 4 saniye. Elin boş.", said.get(0));
        run(e, 0.7f);
        said.clear();
        e.startStep(a.id, 0, Station.DONER.id);
        assertTrue(said.get(0), said.get(0).startsWith("Döneri dilimle başladı"));
        assertFalse("aktif işte eklenmez: " + said.get(0), said.get(0).contains("Elin boş"));
        Narrator n = new Narrator(e);
        assertTrue(n.handsText(), n.handsText().startsWith("Elin dolu: Döneri dilimle"));
        assertTrue(n.handsText(), n.handsText().contains("Pişiyor: Ekmek ısıtıcı 1/2."));
        assertTrue(n.stationLabel(Station.EKMEK), n.stationLabel(Station.EKMEK).startsWith("Ekmek ısıtıcı, çalışıyor, 1/2 dolu"));
        assertTrue(n.statusText().contains("Elin dolu"));
    }

    @Test public void hintSuggestsPutAndGoPassiveStepsFirst() {
        GameEngine e = engine(3, FlowMode.DENGELI);
        final List<String> hints = new ArrayList<>();
        e.addListener(new GameListenerAdapter() {
            @Override public void onNextStepHint(Order o, Step s) { hints.add(s.name); }
        });
        Order a = spawn(e, "ekmek_arasi+patates");
        e.selectOrder(a.id);
        assertEquals("Ekmeği ısıt", hints.get(hints.size() - 1));
        assertEquals(StartResult.STARTED, e.startStep(a.id, 0, Station.EKMEK.id));
        run(e, 0.7f);
        e.selectOrder(a.id);
        assertEquals("koy-geç önce: patates ocağa, sonra dilimleme", "Patates kızart", hints.get(hints.size() - 1));
        assertEquals(StartResult.STARTED, e.startStep(a.id, 0, Station.IZGARA.id));
        run(e, 0.7f);
        e.selectOrder(a.id);
        assertEquals("Döneri dilimle", hints.get(hints.size() - 1));
        // oyuncu kalemi seçtiyse o kalemin adımı önce
        Order b = spawn(e, "ekmek_arasi", "ayran");
        e.selectOrder(b.id);
        e.selectItem(Flow.itemOf(b, "ayran"));
        assertEquals("Ayranı doldur", hints.get(hints.size() - 1));
    }

    @Test public void v06BalanceKeepsTheOldSingleSlot() {
        SaveData s = day(3, FlowMode.GERCEKCI);
        GameEngine e = new GameEngine(5, s, true);
        e.setSpawning(false);
        e.startDay();
        Order a = spawn(e, "ekmek_arasi");
        assertEquals(StartResult.STARTED, e.startStep(a.id, 0, Station.EKMEK.id));
        run(e, 1.0f);
        assertFalse("v0.6 ölçüm dengesi: her iş aktif (eski tek yuva)", e.getActiveSteps().get(0).isPassive());
        assertEquals(StartResult.HANDS_BUSY, e.startStep(a.id, 0, Station.DONER.id));
        assertEquals(0, e.getStationCapacity(Station.EKMEK));
    }

    @Test public void upgradesRaiseStationCapacityAndEconomyTable() {
        SaveData s = SaveData.newGame();
        Upgrades u = new Upgrades(s);
        assertEquals(2, u.stationCapacity(Station.EKMEK));
        assertEquals(2, u.stationCapacity(Station.IZGARA));
        assertEquals(0, u.stationCapacity(Station.DONER));
        assertEquals(1, u.hands());
        s.upgradeLevels.put("ekmek_isitici", 2);
        s.upgradeLevels.put("ocak", 3);
        s.upgradeLevels.put("cirak", 3);
        assertEquals(4, u.stationCapacity(Station.EKMEK));
        assertEquals(4, u.stationCapacity(Station.IZGARA));
        assertEquals("çırak oyuncunun elini artırmaz (kendi eli var)", 1, u.hands());
        Upgrade oc = Catalog.upgrade("ocak");
        assertNotNull(oc);
        assertEquals(3, oc.maxLevel());
        assertEquals(2500, oc.priceForNext(0));
        assertEquals(3, oc.unlockDayForNext(0));
        assertEquals(0.65, u.speedMult(Station.IZGARA), 1e-9);
        // market satın alır, kayıt saklar
        SaveData m = SaveData.newGame();
        m.day = 3;
        m.money = 10000;
        assertEquals(Shop.BuyResult.OK, Shop.buyUpgrade(m, "ocak"));
        assertEquals(1, SaveData.parse(m.serialize()).level("ocak"));
    }

    // ------------------------------------------------------------ kayıt uyumu
    @Test public void v09SaveLoadsIntoTheNewModel() {
        String v09 = "v=4\nday=9\nbestDay=9\nmoney=4321\nstars=3.7\nstock.ekmek=12\nstock.patates=6\nup.cirak=1\nup.ekmek_isitici=2\n"
                + "tempo=1.0\nspeech=1\nspeechRate=1.0\nvibration=1\nmusic=0.7\nsfx=1.0\nambience=0.35\ntutorial=1\n"
                + "apprenticeAnnounce=1\nreadyDrinks=3\nflow=dengeli\nflowChosen=1\ntimer=1\n";
        SaveData s = SaveData.parse(v09);
        assertEquals(9, s.day);
        assertEquals(4321, s.money);
        assertEquals(1, s.level("cirak"));
        assertEquals(2, s.level("ekmek_isitici"));
        assertEquals("yeni yükseltme eski kayıtta 0", 0, s.level("ocak"));
        assertEquals(4, new Upgrades(s).stationCapacity(Station.EKMEK));
        assertEquals(2, new Upgrades(s).stationCapacity(Station.IZGARA));
        assertTrue("kayıt biçimi aynı (v=4)", s.serialize().startsWith("v=4\n"));
        assertEquals(s.serialize(), SaveData.parse(s.serialize()).serialize());
        // eski kayıtla gün oynanır
        GameEngine e = new GameEngine(3, s);
        new TestBot(1.0f, true, true).playDay(e);
        assertFalse(e.isRunning());
    }

    // ------------------------------------------------------------ sıradaki sipariş, sıra özeti, şimdi yapılabilecekler
    @Test public void focusOrderIsSelectionOrTheMostUrgent() {
        GameEngine e = engine(3, FlowMode.DENGELI);
        assertNull(e.getFocusOrder());
        assertNull(e.getMostUrgentOrder());
        assertEquals("Bekleyen sipariş yok.", e.getQueueSummary().text);
        Order a = spawn(e, "ayran");
        run(e, 6f);
        Order b = spawn(e, "ayran");
        run(e, 1f);
        assertTrue(a.customer.patienceFraction() < b.customer.patienceFraction());
        assertSame("seçim yok: en az kalan sabır oranı", a, e.getFocusOrder());
        assertSame(a, e.getMostUrgentOrder());
        e.selectOrder(b.id);
        assertSame("oyuncu seçimi öncelikli", b, e.getFocusOrder());
        assertSame("en acil seçimden bağımsız", a, e.getMostUrgentOrder());
        QueueSummary q = e.getQueueSummary();
        assertEquals(2, q.orders);
        assertEquals(0, q.readyToDeliver);
        assertEquals(2, q.workable);
        assertEquals(b.id, q.focusOrderId);
        assertEquals(a.id, q.mostUrgentOrderId);
        assertEquals(a.customer.getPatienceLeft(), q.mostUrgentSecondsLeft, 0.01f);
        assertEquals(a.customer.patienceFraction(), q.mostUrgentFraction, 1e-4f);
        assertTrue(q.text, q.text.startsWith("2 sipariş. En acil: sipariş " + a.getPosition() + ", " + a.customer.name + ", "));
        assertTrue(q.text, q.text.endsWith(" saniye."));
        // önerilen: teslime hazır olan önce
        Flow.makeReady(e, b);
        assertSame(b, e.getSuggestedOrder());
        assertEquals(1, e.getQueueSummary().readyToDeliver);
        assertTrue(e.getQueueSummary().text.contains("1 teslime hazır"));
        assertTrue(e.deliver(b.id));
        assertTrue(e.selectSuggestedOrder());
        assertEquals(a.id, e.getSelectedOrderId());
    }

    @Test public void untimedQueueSummaryHasNoSeconds() {
        SaveData s = day(3, FlowMode.DENGELI);
        s.timePressure = false;
        GameEngine e = engine(s);
        Order a = spawn(e, "ayran");
        spawn(e, "ayran");
        QueueSummary q = e.getQueueSummary();
        assertEquals(-1f, q.mostUrgentSecondsLeft, 0f);
        assertEquals("süresiz: en eski en acil", a.id, q.mostUrgentOrderId);
        assertFalse(q.text, q.text.contains("saniye"));
    }

    @Test public void suggestedOrderSkipsOrdersThatAreOnlyWaitingForMachines() {
        GameEngine e = engine(3, FlowMode.DENGELI);
        Order a = spawn(e, "ekmek_arasi");
        run(e, 3f);
        Order b = spawn(e, "ayran");
        // a: ekmek ısıtıcıda, dilimleme sürüyor; yapılacak başka işi yok
        assertEquals(StartResult.STARTED, e.startStep(a.id, 0, Station.EKMEK.id));
        run(e, 0.7f);
        assertEquals(StartResult.STARTED, e.startStep(a.id, 0, Station.DONER.id));
        run(e, 0.1f);
        assertSame("a daha acil ama şu an yapılacak işi yok", a, e.getMostUrgentOrder());
        // eller dilimlemede: hiçbir sipariş 'şimdi' yapılamaz -> en acil
        assertSame(a, e.getSuggestedOrder());
        run(e, 3.5f);
        assertTrue(e.isHandsFree());
        // a'nın malzemesi açıldı: yine a (daha acil ve yapılabilir)
        assertSame(a, e.getSuggestedOrder());
        assertNotNull(b);
    }

    @Test public void readyActionsListPassiveFirstWithShortTextsAndOneTapOptions() {
        GameEngine e = engine(3, FlowMode.DENGELI);
        Order a = spawn(e, "ekmek_arasi+patates", "ayran");
        List<ReadyAction> l = e.getReadyActions(a.id);
        List<String> texts = new ArrayList<>();
        for (ReadyAction x : l) texts.add(x.text);
        assertEquals(Arrays.asList(
                "Ekmek arası döner: Ekmeği ısıt, Ekmek ısıtıcı",
                "Ekmek arası döner: Patates kızart, Ocak",
                "Ekmek arası döner: Döneri dilimle, Döner makinesi",
                "Ayran: Ayranı doldur, İçecek dolabı"), texts);
        for (ReadyAction x : l) {
            assertEquals(ReadyAction.Status.NOW, x.status);
            assertEquals(ReadyAction.Kind.STEP, x.kind);
            assertEquals("Dengeli, tam ekmek: hepsi tek dokunuş", "step", x.optionId);
            assertFalse(x.needsMenu);
            assertTrue(x.seconds > 0);
        }
        assertTrue(l.get(0).passive && l.get(1).passive && !l.get(2).passive);
        // listedeki seçenek doğrudan uygulanır
        ReadyAction first = l.get(0);
        assertEquals(StartResult.STARTED, e.applyStationOption(a.id, first.itemIndex, first.station.id, first.optionId));
        l = e.getReadyActions(a.id);
        assertEquals("koyarken diğerleri el bekler", ReadyAction.Status.HANDS_BUSY, l.get(0).status);
        assertTrue(l.get(0).text, l.get(0).text.endsWith(", elin dolu"));
        ReadyAction running = l.get(l.size() - 1);
        assertEquals(ReadyAction.Status.RUNNING, running.status);
        assertTrue(running.text, running.text.startsWith("Ekmek arası döner: Ekmeği ısıt sürüyor, 4 saniye, Ekmek ısıtıcı"));
        assertFalse(running.byApprentice);
        run(e, 0.7f);
        Narrator n = new Narrator(e);
        String t = n.readyActionsText(a.id);
        assertTrue(t, t.startsWith("Şimdi: Ekmek arası döner: Patates kızart, Ocak; "));
        assertTrue(t, t.contains(". Ekmek arası döner: Ekmeği ısıt sürüyor, "));
        String f = n.focusText();
        assertTrue(f, f.startsWith("Sipariş " + a.getPosition() + ": "));
    }

    @Test public void readyActionsShowMenuNeedsStockAndDelivery() {
        GameEngine e = engine(4, FlowMode.GERCEKCI);
        Order a = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE, Collections.singletonList(PortionSize.HALF));
        ReadyAction slice = null;
        for (ReadyAction x : e.getReadyActions(a.id)) if (x.station == Station.DONER) slice = x;
        assertNotNull(slice);
        assertTrue("Gerçekçi: boy seçimi menü ister", slice.needsMenu);
        assertNull(slice.optionId);
        assertTrue(slice.text, slice.text.endsWith(", boy seç"));
        // stok yoksa OUT_OF_STOCK (acil alım)
        e.getSave().inventory.set("ekmek", 0);
        ReadyAction bread = null;
        for (ReadyAction x : e.getReadyActions(a.id)) if (x.station == Station.EKMEK) bread = x;
        assertNotNull(bread);
        assertTrue("yapılabilir olanlar önce, bekleyenler sonra", e.getReadyActions(a.id).get(0).isDoable());
        assertEquals(ReadyAction.Status.OUT_OF_STOCK, bread.status);
        assertTrue(bread.text.endsWith(", malzeme yok"));
        e.getSave().inventory.set("ekmek", 30);
        Flow.makeReady(e, a);
        List<ReadyAction> l = e.getReadyActions(a.id);
        assertEquals(1, l.size());
        assertEquals(ReadyAction.Kind.DELIVER, l.get(0).kind);
        assertEquals("Teslim et", l.get(0).text);
        assertEquals("Teslime hazır.", new Narrator(e).readyActionsText(a.id));
        assertTrue(e.getReadyActions(999).isEmpty());
    }

    @Test public void readyActionsShowStationFullAndPacking() {
        GameEngine e = engine(3, FlowMode.DENGELI);
        Order a = spawn(e, "ekmek_arasi"), b = spawn(e, "ekmek_arasi"), c = spawn(e, "ekmek_arasi");
        e.startStep(a.id, 0, Station.EKMEK.id);
        run(e, 0.7f);
        e.startStep(b.id, 0, Station.EKMEK.id);
        run(e, 0.7f);
        ReadyAction cb = e.getReadyActions(c.id).get(1); // önce yapılabilir dilimleme, sonra bekleyen ekmek
        assertEquals(ReadyAction.Status.STATION_FULL, cb.status);
        assertEquals("Ekmeği ısıt, Ekmek ısıtıcı dolu (2/2)", cb.text);
        Order d = spawn(e, "ayran");
        e.startStep(d.id, 0, Station.ICECEK.id);
        Flow.waitDone(e, d);
        List<ReadyAction> l = e.getReadyActions(d.id);
        assertEquals(ReadyAction.Kind.PACK, l.get(0).kind);
        assertEquals("Siparişi paketle, Paketleme", l.get(0).text);
        assertEquals(StartResult.STARTED, e.applyStationOption(d.id, -1, Station.PAKET.id, l.get(0).optionId));
        assertEquals(ReadyAction.Status.RUNNING, e.getReadyActions(d.id).get(0).status);
    }
}
