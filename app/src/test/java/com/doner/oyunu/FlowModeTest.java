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

/** v0.7 oyun akışı (Rahat / Dengeli / Gerçekçi): seçenek menüleri, otomatik malzeme/boy, stok, süre, sabır, kayıt. */
public class FlowModeTest {
    private static final List<String> NONE = Collections.emptyList();

    private static SaveData day(int d, FlowMode m) {
        SaveData s = SaveData.newGame();
        s.day = d;
        s.stars = 4.0;
        s.money = 50000;
        s.flowMode = m;
        Shop.buyRecommended(s);
        s.inventory.set("et_doner", 30);
        s.inventory.set("ekmek", 30);
        return s;
    }

    private static GameEngine engine(int d, FlowMode m) {
        GameEngine e = new GameEngine(7, day(d, m));
        e.setSpawning(false);
        e.startDay();
        return e;
    }

    private static Set<String> set(String... ids) { return new LinkedHashSet<>(Arrays.asList(ids)); }

    private static List<String> ids(List<StationOption> ops) {
        List<String> out = new ArrayList<>();
        for (StationOption op : ops) out.add(op.id);
        return out;
    }

    private static StationOption find(List<StationOption> ops, String id) {
        for (StationOption op : ops) if (op.id.equals(id)) return op;
        return null;
    }

    /** Ekmek arası döner, istenen boy ve isteklerle; ısıtma yapılır, dilimleme oyuncuya kalır. */
    private static Order order(GameEngine e, PortionSize size, String... requests) {
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), Arrays.asList(requests), Collections.singletonList(size));
        assertNotNull(o);
        e.selectOrder(o.id);
        assertEquals(StartResult.STARTED, e.startStep(o.id, 0, Station.EKMEK.id));
        Flow.waitDone(e, o);
        return o;
    }

    private static void slice(GameEngine e, Order o, String optionId) {
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, 0, Station.DONER.id, optionId));
        Flow.waitDone(e, o);
    }

    private static final class Rec extends GameListenerAdapter {
        final List<String> log = new ArrayList<>();
        boolean perfect;
        int mistakes = -1;
        int tip = -1;
        float autoSeconds;
        @Override public void onAutoToppingsStarted(Order o, int i, List<Ingredient> adding, float sec) { log.add("autoStart:" + adding.size()); autoSeconds = sec; }
        @Override public void onToppingsAutoAdded(Order o, int i, List<Ingredient> added, List<Ingredient> removed, boolean finished) {
            log.add("autoAdded:" + added.size() + ":" + removed.size() + ":" + finished);
        }
        @Override public void onToppingsFinished(Order o, int i, Set<String> chosen) { log.add("finished:" + chosen); }
        @Override public void onOutOfStock(Order o, Ingredient ing) { log.add("oos:" + ing.id); }
        @Override public void onFlowModeChanged(FlowMode m) { log.add("flow:" + m.id); }
        @Override public void onOrderPerfect(Order o) { perfect = true; }
        @Override public void onOrderMistake(Order o, ToppingCheck c, double p) { mistakes = c.mistakes(); }
        @Override public void onOrderDelivered(Order o, int money, int tip, boolean happy) { this.tip = tip; }
    }

    private static void packAndDeliver(GameEngine e, Order o) {
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, -1, Station.PAKET.id, "step"));
        Flow.waitDone(e, o);
        assertTrue(o.isReady());
        assertTrue(e.deliver(o.id));
    }

    // ------------------------------------------------------------------ varsayılan, kayıt
    @Test public void defaultIsDengeliAndSaveRoundTrips() {
        assertEquals(FlowMode.DENGELI, FlowMode.DEFAULT);
        SaveData s = SaveData.newGame();
        assertEquals(FlowMode.DENGELI, s.flowMode);
        assertFalse(s.flowChosen);
        for (FlowMode m : FlowMode.values()) {
            s.flowMode = m;
            s.flowChosen = true;
            SaveData t = SaveData.parse(s.serialize());
            assertEquals(m, t.flowMode);
            assertTrue(t.flowChosen);
            assertEquals(s.serialize(), t.serialize());
        }
        assertSame(FlowMode.RAHAT, FlowMode.byId("rahat"));
        assertNull(FlowMode.byId("yok"));
        assertEquals(FlowMode.DENGELI, FlowMode.RAHAT.next());
        assertEquals(FlowMode.GERCEKCI, FlowMode.DENGELI.next());
        assertEquals(FlowMode.RAHAT, FlowMode.GERCEKCI.next());
    }

    @Test public void oldV06AndV05SavesLoadWithDefaultFlowAndBadValuesAreIgnored() {
        String v06 = "v=3\nday=9\nbestDay=9\nmoney=7000\nstars=3.5\nstock.domates=12\ntempo=1.0\nspeech=1\nreadyDrinks=2\n";
        SaveData s = SaveData.parse(v06);
        assertEquals(9, s.day);
        assertEquals(12, s.inventory.get("domates"));
        assertEquals("eski kayıtta akış yok: varsayılan", FlowMode.DENGELI, s.flowMode);
        assertFalse("eski kayıtta akış henüz seçilmedi", s.flowChosen);
        assertTrue(s.serialize().startsWith("v=4\n"));
        assertTrue(s.serialize().contains("flow=dengeli"));
        SaveData bad = SaveData.parse("v=4\nday=3\nflow=uçan\nflowChosen=x\n");
        assertEquals(FlowMode.DENGELI, bad.flowMode);
        assertFalse(bad.flowChosen);
        assertEquals(FlowMode.GERCEKCI, SaveData.parse("v=4\nflow=gercekci\n").flowMode);
    }

    // ------------------------------------------------------------------ Rahat
    @Test public void rahatSizeIsAutomaticOneTapAndNeverWrong() {
        GameEngine e = engine(5, FlowMode.RAHAT);
        Order o = order(e, PortionSize.HALF);
        List<StationOption> ops = e.getStationOptions(o.id, -1, Station.DONER.id);
        assertNull("boy seçeneği yok", find(ops, "size:yarim"));
        StationOption step = find(ops, "step");
        assertNotNull(step);
        assertTrue(step.label(), step.label().contains("yarım ekmek"));
        assertFalse("v0.9: etiket kısa, 'boy otomatik' ayrıntısı yok", step.label().contains("boy otomatik"));
        assertSame("tek dokunuş: panel açılmaz", step, StationOption.direct(ops));
        slice(e, o, "step");
        assertEquals(PortionSize.HALF, o.getLine(0).getChosenSize());
        assertFalse(o.getLine(0).sizeMismatch());
    }

    @Test public void rahatAutoToppingsApplyRequestsConsumeStockTakeTimeAndFinish() {
        GameEngine e = engine(5, FlowMode.RAHAT);
        Rec r = new Rec();
        e.addListener(r);
        Order o = order(e, PortionSize.FULL, "soğansız", "acılı");
        slice(e, o, "step");
        List<StationOption> ops = e.getStationOptions(o.id, -1, Station.SOS.id);
        assertEquals(Arrays.asList("auto", "read:target", "prep:tray"), ids(ops));
        assertEquals("auto", StationOption.direct(ops).id);
        Set<String> target = e.getToppingTarget(o.id, 0);
        assertFalse(target.contains("sogan"));
        assertTrue(target.contains("aci_sos"));
        int dom = e.getSave().inventory.get("domates"), sog = e.getSave().inventory.get("sogan"), aci = e.getSave().inventory.get("aci_sos");
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, -1, Station.SOS.id, "auto"));
        assertEquals("stok hemen düşer", dom - 1, e.getSave().inventory.get("domates"));
        assertEquals(aci - 1, e.getSave().inventory.get("aci_sos"));
        assertEquals("istenmeyen soğan tüketilmez", sog, e.getSave().inventory.get("sogan"));
        // süre: tek tek eklemelerin toplamı (salata 0,6, sos 0,5)
        float expect = 0;
        for (String id : target) expect += Catalog.ingredient(id).category == Ingredient.Category.SALATA ? 0.6f : 0.5f;
        assertEquals(expect, r.autoSeconds, 0.01f);
        assertTrue("iş sürerken kalem meşgul", o.isRunning());
        assertEquals(StartResult.ORDER_BUSY, e.applyStationOption(o.id, -1, Station.SOS.id, "auto"));
        Flow.waitDone(e, o);
        assertTrue(r.log.toString(), r.log.contains("autoAdded:" + target.size() + ":0:true"));
        assertTrue(o.getLine(0).isDone());
        assertEquals(target, o.getLine(0).getChosen());
        packAndDeliver(e, o);
        assertTrue("Rahat: istekler dahil kusursuz", r.perfect);
    }

    @Test public void rahatAutoAfterSwitchFromGercekciAddsMissingAndRemovesExtras() {
        GameEngine e = engine(5, FlowMode.GERCEKCI);
        Order o = order(e, PortionSize.FULL, "soğansız");
        slice(e, o, "size:tam");
        int sog = e.getSave().inventory.get("sogan");
        assertEquals(StartResult.STARTED, e.addTopping(o.id, 0, "sogan"));
        Flow.waitDone(e, o);
        assertEquals(sog - 1, e.getSave().inventory.get("sogan"));
        Rec r = new Rec();
        e.addListener(r);
        e.setFlowMode(FlowMode.RAHAT);
        assertEquals(FlowMode.RAHAT, e.getSave().flowMode);
        assertTrue(r.log.contains("flow:rahat"));
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, -1, Station.SOS.id, "auto"));
        assertEquals("fazla soğan stoğa döndü", sog, e.getSave().inventory.get("sogan"));
        Flow.waitDone(e, o);
        assertTrue(r.log.toString(), r.log.get(r.log.size() - 2).startsWith("autoAdded:") && r.log.get(r.log.size() - 2).contains(":1:true"));
        assertEquals(e.getToppingTarget(o.id, 0), o.getLine(0).getChosen());
    }

    // ------------------------------------------------------------------ Dengeli
    @Test public void dengeliFullSizeIsAutomaticButHalfAndDoubleAreChosenByHand() {
        GameEngine e = engine(5, FlowMode.DENGELI);
        Order full = order(e, PortionSize.FULL);
        List<StationOption> ops = e.getStationOptions(full.id, -1, Station.DONER.id);
        assertNull(find(ops, "size:tam"));
        assertEquals("step", StationOption.direct(ops).id);
        Order half = order(e, PortionSize.HALF);
        ops = e.getStationOptions(half.id, -1, Station.DONER.id);
        assertNotNull(find(ops, "size:yarim"));
        assertNotNull(find(ops, "size:tam"));
        assertNotNull(find(ops, "size:duble"));
        assertNull("boy farkı elle: panel açılır", StationOption.direct(ops));
        slice(e, half, "size:tam");
        assertTrue("yanlış boy yine hata", half.getLine(0).sizeMismatch());
    }

    @Test public void dengeliWithoutRequestIsOneTapAndFinishes() {
        GameEngine e = engine(3, FlowMode.DENGELI);
        Rec r = new Rec();
        e.addListener(r);
        Order o = order(e, PortionSize.FULL);
        slice(e, o, "step");
        List<StationOption> ops = e.getStationOptions(o.id, -1, Station.SOS.id);
        StationOption auto = find(ops, "auto");
        assertNotNull(auto);
        assertEquals("Varsayılan malzemeleri ekle", auto.name);
        assertEquals("v0.9: etiket yalnızca ad", "Varsayılan malzemeleri ekle", auto.label());
        assertSame(auto, StationOption.direct(ops));
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, -1, Station.SOS.id, "auto"));
        Flow.waitDone(e, o);
        assertTrue(o.getLine(0).isDone());
        packAndDeliver(e, o);
        assertTrue(r.perfect);
    }

    @Test public void dengeliRequestLeavesStepOpenAndDifferenceIsDoneByHand() {
        GameEngine e = engine(5, FlowMode.DENGELI);
        Rec r = new Rec();
        e.addListener(r);
        Order o = order(e, PortionSize.FULL, "soğansız", "acılı");
        slice(e, o, "step");
        int sog = e.getSave().inventory.get("sogan");
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, -1, Station.SOS.id, "auto"));
        Flow.waitDone(e, o);
        assertTrue(r.log.toString(), r.log.contains("autoAdded:4:0:false"));
        assertFalse("istek farkı var: adım açık kalır", o.getLine(0).isDone());
        assertTrue("varsayılan soğan eklendi", o.getLine(0).getAddedSet().contains("sogan"));
        List<StationOption> ops = e.getStationOptions(o.id, -1, Station.SOS.id);
        assertNull("Dengeli'de otomatik bir kez", find(ops, "auto"));
        StationOption rm = find(ops, "remove:sogan");
        assertNotNull(ids(ops).toString(), rm);
        assertEquals("Soğan çıkar", rm.name);
        assertEquals(StationOption.Group.REMOVE, rm.group);
        assertNotNull(find(ops, "add:aci_sos"));
        assertNotNull(find(ops, "finish"));
        assertNull("panel açılır", StationOption.direct(ops));
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, -1, Station.SOS.id, "remove:sogan"));
        assertEquals("çıkarılan soğan stoğa döner", sog, e.getSave().inventory.get("sogan"));
        assertEquals(StartResult.NOTHING_TO_UNDO, e.applyStationOption(o.id, -1, Station.SOS.id, "remove:sogan"));
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, -1, Station.SOS.id, "add:aci_sos"));
        Flow.waitDone(e, o);
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, -1, Station.SOS.id, "finish"));
        packAndDeliver(e, o);
        assertTrue(r.perfect);
        assertEquals(-1, r.mistakes);
    }

    @Test public void dengeliIgnoringTheRequestIsStillAMistake() {
        GameEngine e = engine(5, FlowMode.DENGELI);
        Rec r = new Rec();
        e.addListener(r);
        Order o = order(e, PortionSize.FULL, "soğansız");
        slice(e, o, "step");
        e.applyStationOption(o.id, -1, Station.SOS.id, "auto");
        Flow.waitDone(e, o);
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, -1, Station.SOS.id, "finish"));
        packAndDeliver(e, o);
        assertEquals(1, r.mistakes);
    }

    // ------------------------------------------------------------------ Gerçekçi
    @Test public void gercekciKeepsV06MenuUntilApprenticeLevel2UnlocksStandardSet() {
        GameEngine e = engine(5, FlowMode.GERCEKCI);
        Order o = order(e, PortionSize.FULL);
        slice(e, o, "size:tam");
        List<StationOption> ops = e.getStationOptions(o.id, -1, Station.SOS.id);
        assertNull("çırak yokken kısayol yok", find(ops, "auto"));
        assertNotNull(find(ops, "add:domates"));
        assertNotNull(find(ops, "undo"));

        SaveData s = day(5, FlowMode.GERCEKCI);
        s.upgradeLevels.put("cirak", 2);
        GameEngine g = new GameEngine(7, s);
        g.setSpawning(false);
        g.startDay();
        assertTrue(g.standardSetUnlocked());
        Rec r = new Rec();
        g.addListener(r);
        Order p = order(g, PortionSize.FULL);
        g.selectOrder(p.id);
        slice(g, p, "size:tam");
        ops = g.getStationOptions(p.id, -1, Station.SOS.id);
        StationOption auto = find(ops, "auto");
        assertNotNull(auto);
        assertEquals("Standart malzemelerle bitir", auto.name);
        assertEquals("v0.9: etiket yalnızca ad", "Standart malzemelerle bitir", auto.label());
        assertNull("Gerçekçi: tüm el seçenekleri durur, panel açılır", StationOption.direct(ops));
        assertNotNull(find(ops, "add:domates"));
        assertEquals(StartResult.STARTED, g.applyStationOption(p.id, -1, Station.SOS.id, "auto"));
        Flow.waitDone(g, p);
        assertTrue("kısayol adımı bitirir", p.getLine(0).isDone());
        packAndDeliver(g, p);
        assertTrue(r.perfect);

        // bir malzeme eklendiyse kısayol kalkar; v0.6 dengesinde hiç yok
        Order q = order(g, PortionSize.FULL);
        g.selectOrder(q.id);
        slice(g, q, "size:tam");
        g.addTopping(q.id, 0, "domates");
        Flow.waitDone(g, q);
        assertNull(find(g.getStationOptions(q.id, -1, Station.SOS.id), "auto"));
        GameEngine v06 = new GameEngine(7, s.copy(), true);
        v06.setSpawning(false);
        v06.startDay();
        Order w = order(v06, PortionSize.FULL);
        slice(v06, w, "size:tam");
        assertNull("v0.6 dengesi: kısayol yok", find(v06.getStationOptions(w.id, -1, Station.SOS.id), "auto"));
    }

    @Test public void gercekciStandardSetOnARequestOrderIsAMistake() {
        SaveData s = day(5, FlowMode.GERCEKCI);
        s.upgradeLevels.put("cirak", 2);
        GameEngine g = new GameEngine(7, s);
        g.setSpawning(false);
        g.startDay();
        Rec r = new Rec();
        g.addListener(r);
        Order p = order(g, PortionSize.FULL, "soğansız");
        slice(g, p, "size:tam");
        assertEquals(StartResult.STARTED, g.applyStationOption(p.id, -1, Station.SOS.id, "auto"));
        Flow.waitDone(g, p);
        packAndDeliver(g, p);
        assertEquals("isteği unutan kısayol hatadır", 1, r.mistakes);
    }

    @Test public void autoIsRejectedInGercekciWithoutApprentice() {
        GameEngine e = engine(5, FlowMode.GERCEKCI);
        Rec r = new Rec();
        e.addListener(r);
        Order o = order(e, PortionSize.FULL);
        slice(e, o, "size:tam");
        assertEquals(StartResult.INVALID, e.applyStationOption(o.id, -1, Station.SOS.id, "auto"));
        assertTrue(o.getLine(0).getAddedSet().isEmpty());
    }

    @Test public void gercekciTipBonusAndMorePatienceThanV06() {
        SaveData a = day(12, FlowMode.GERCEKCI), b = a.copy();
        GameEngine now = new GameEngine(3, a), old = new GameEngine(3, b, true);
        for (GameEngine e : new GameEngine[]{now, old}) { e.setSpawning(false); e.startDay(); }
        List<String> items = Arrays.asList("ekmek_arasi", "ayran");
        List<PortionSize> sizes = Arrays.asList(PortionSize.HALF, PortionSize.FULL);
        Order x = now.spawnOrder(items, Collections.singletonList("soğansız"), sizes);
        Order y = old.spawnOrder(items, Collections.singletonList("soğansız"), sizes);
        assertTrue("Gerçekçi v0.7 sabrı v0.6'dan fazla: " + x.customer.getPatienceLeft() + " > " + y.customer.getPatienceLeft(),
                x.customer.getPatienceLeft() > y.customer.getPatienceLeft() * 1.15f);
        // Rahat: elle iş yok, sabır v0.6 ile aynı formül (yumuşatma Gerçekçi'ye özgü)
        SaveData c = day(12, FlowMode.RAHAT);
        GameEngine rahat = new GameEngine(3, c), rahatOld = new GameEngine(3, c.copy(), true);
        for (GameEngine e : new GameEngine[]{rahat, rahatOld}) { e.setSpawning(false); e.startDay(); }
        assertEquals(rahatOld.spawnOrder(items, NONE, sizes).customer.getPatienceLeft(),
                rahat.spawnOrder(items, NONE, sizes).customer.getPatienceLeft(), 0.001f);
        assertEquals(1.15, FlowMode.GERCEKCI.tipMult, 1e-9);
        assertEquals(1.0, FlowMode.DENGELI.tipMult, 1e-9);
        assertEquals(1.0, FlowMode.RAHAT.tipMult, 1e-9);
    }

    @Test public void tipIsHigherInGercekciForTheSameFastOrder() {
        int[] tips = new int[2];
        for (int k = 0; k < 2; k++) {
            GameEngine e = engine(5, k == 0 ? FlowMode.GERCEKCI : FlowMode.DENGELI);
            Rec r = new Rec();
            e.addListener(r);
            Order o = e.spawnOrder(Collections.singletonList("ayran"), NONE);
            assertEquals(StartResult.STARTED, e.startStep(o.id, 0, Station.ICECEK.id));
            Flow.waitDone(e, o);
            packAndDeliver(e, o);
            tips[k] = r.tip;
        }
        assertTrue(tips[0] + " > " + tips[1], tips[0] > tips[1]);
    }

    // ------------------------------------------------------------------ stok yok / acil al
    @Test public void autoOutOfStockConsumesNothingAndEmergencyBuyMissingFixesIt() {
        for (FlowMode m : new FlowMode[]{FlowMode.RAHAT, FlowMode.DENGELI}) {
            GameEngine e = engine(3, m);
            Rec r = new Rec();
            e.addListener(r);
            Order o = order(e, PortionSize.FULL);
            slice(e, o, "step");
            e.getSave().inventory.set("marul", 0);
            int dom = e.getSave().inventory.get("domates");
            StationOption auto = find(e.getStationOptions(o.id, -1, Station.SOS.id), "auto");
            assertTrue(m + " seçenek etkin kalır", auto.enabled);
            assertTrue(m + " ama stok yok işaretli", auto.outOfStock);
            assertTrue(auto.label(), auto.label().contains("malzeme yok"));
            assertEquals(StartResult.OUT_OF_STOCK, e.applyStationOption(o.id, -1, Station.SOS.id, "auto"));
            assertEquals("hiçbir şey tüketilmez", dom, e.getSave().inventory.get("domates"));
            assertTrue(r.log.contains("oos:marul"));
            List<Ingredient> miss = e.missingIngredients(o.id);
            assertEquals(1, miss.size());
            assertEquals("marul", miss.get(0).id);
            assertTrue(e.emergencyCostForMissing(o.id) > 0);
            int money = e.getMoney();
            assertTrue(e.emergencyBuyMissing(o.id));
            assertTrue(e.getMoney() < money);
            assertTrue(e.missingIngredients(o.id).isEmpty());
            assertEquals(StartResult.STARTED, e.applyStationOption(o.id, -1, Station.SOS.id, "auto"));
            Flow.waitDone(e, o);
            assertTrue(o.getLine(0).isDone());
        }
    }

    @Test public void autoUsesReadyTrayFirstAndIsFaster() {
        GameEngine e = engine(3, FlowMode.RAHAT);
        Order o0 = order(e, PortionSize.FULL);
        slice(e, o0, "step");
        Rec r = new Rec();
        e.addListener(r);
        e.applyStationOption(o0.id, -1, Station.SOS.id, "auto");
        float noTray = r.autoSeconds;
        Flow.waitDone(e, o0);
        assertEquals(PrepResult.STARTED, e.startPrepTray(set("domates", "marul", "sogan")));
        for (int i = 0; i < 100 && e.getTrayUses("domates") == 0; i++) e.tick(0.1f);
        int domStock = e.getSave().inventory.get("domates");
        int trayDom = e.getTrayUses("domates");
        Order o = order(e, PortionSize.FULL);
        slice(e, o, "step");
        e.applyStationOption(o.id, -1, Station.SOS.id, "auto");
        assertEquals("tepsiden düşer", trayDom - 1, e.getTrayUses("domates"));
        assertEquals("stoktan düşmez", domStock, e.getSave().inventory.get("domates"));
        assertTrue(r.autoSeconds + " < " + noTray, r.autoSeconds < noTray);
    }

    // ------------------------------------------------------------------ duyurular
    @Test public void narratorAnnouncesAutoToppingsAutoSizeAndFlowChanges() {
        final List<String> said = new ArrayList<>();
        GameEngine e = engine(5, FlowMode.DENGELI);
        e.addListener(new Narrator(e, new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { said.add(text); }
        }));
        Order o = order(e, PortionSize.FULL, "soğansız");
        slice(e, o, "step");
        assertTrue(said.toString(), said.toString().contains("Döneri dilimle başladı, boy otomatik: tam ekmek"));
        e.applyStationOption(o.id, -1, Station.SOS.id, "auto");
        assertTrue(said.toString(), said.get(said.size() - 1).startsWith("Varsayılan malzemeler ekleniyor"));
        Flow.waitDone(e, o);
        String done = said.get(said.size() - 1);
        assertEquals("Varsayılan malzemeler eklendi: domates, marul, soğan ve ketçap. İstek: soğansız. Farkı sen yap, sonra Malzemeleri bitir.", done);
        e.setFlowMode(FlowMode.RAHAT);
        assertTrue(said.get(said.size() - 1).startsWith("Oyun akışı: Rahat."));
        Narrator n = new Narrator(e);
        assertTrue(n.statusText().contains("Oyun akışı: Rahat."));
        assertTrue(n.dayStarted(5, DayConfig.forDay(5)).contains("Oyun akışı: Rahat."));
        assertEquals("Yeni boy: yarım ekmek. Rahat akışta boy otomatik seçilir.", Narrator.newSizesText(2, FlowMode.RAHAT));
        assertTrue(Narrator.newSizesText(4, FlowMode.DENGELI).startsWith("Yeni boy: duble. Dengeli akışta tam ekmek otomatik"));
        assertEquals(Narrator.newSizesText(2), Narrator.newSizesText(2, FlowMode.GERCEKCI));
        assertEquals("", Narrator.newSizesText(3, FlowMode.RAHAT));
    }

    @Test public void rahatAnnouncementSaysRequestWasApplied() {
        final List<String> said = new ArrayList<>();
        GameEngine e = engine(5, FlowMode.RAHAT);
        e.addListener(new Narrator(e, new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { said.add(text); }
        }));
        Order o = order(e, PortionSize.HALF, "soğansız");
        slice(e, o, "step");
        assertTrue(said.toString(), said.toString().contains("boy otomatik: yarım ekmek"));
        e.applyStationOption(o.id, -1, Station.SOS.id, "auto");
        Flow.waitDone(e, o);
        boolean found = false;
        for (String t : said) if (t.equals("Malzemeler eklendi: domates, marul ve ketçap. İstek uygulandı: soğansız.")) found = true;
        assertTrue(said.toString(), found);
    }

    // ------------------------------------------------------------------ tüm akışlar
    @Test public void idleBotFailsWithinFiveDaysInEveryMode() {
        for (FlowMode m : FlowMode.values()) {
            for (long seed = 1; seed <= 4; seed++) {
                SaveData s = SaveData.newGame();
                s.flowMode = m;
                int failedAt = -1;
                for (int d = 1; d <= 40 && failedAt < 0; d++) {
                    GameEngine e = new GameEngine(seed * 100 + d, s);
                    e.tick(2000f);
                    if (e.getSummary().failed) failedAt = d;
                }
                assertTrue(m + " seed " + seed + " başarısız gün: " + failedAt, failedAt > 0 && failedAt <= 5);
            }
        }
    }

    @Test public void eachModeIsDeterministic() {
        for (FlowMode m : FlowMode.values()) {
            Campaign a = new Campaign(m), b = new Campaign(m);
            a.run(8, 5, 0.9f, true, 3, true, true);
            b.run(8, 5, 0.9f, true, 3, true, true);
            assertEquals(a.save.serialize(), b.save.serialize());
            assertEquals(a.taps, b.taps);
        }
    }

    @Test public void modeCanChangeMidDayAndMenusFollow() {
        GameEngine e = engine(5, FlowMode.GERCEKCI);
        Order o = order(e, PortionSize.HALF);
        assertNotNull(find(e.getStationOptions(o.id, -1, Station.DONER.id), "size:yarim"));
        e.setFlowMode(FlowMode.RAHAT);
        assertNull(find(e.getStationOptions(o.id, -1, Station.DONER.id), "size:yarim"));
        assertNotNull(find(e.getStationOptions(o.id, -1, Station.DONER.id), "step"));
        e.setFlowMode(FlowMode.DENGELI);
        assertNotNull("yarım ekmek istendi: Dengeli'de boy elle", find(e.getStationOptions(o.id, -1, Station.DONER.id), "size:yarim"));
        assertTrue(e.getSave().flowChosen);
    }

    @Test public void apprenticeStillNeverDoesToppingsInAnyMode() {
        for (FlowMode m : FlowMode.values()) {
            SaveData s = day(8, m);
            s.upgradeLevels.put("cirak", 3);
            GameEngine e = new GameEngine(9, s);
            e.setSpawning(false);
            e.startDay();
            Order dummy = e.spawnOrder(Collections.singletonList("ayran"), NONE);
            Order c = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
            e.selectOrder(dummy.id);
            for (int i = 0; i < 1200; i++) e.tick(0.1f);
            OrderLine l = c.getLine(0);
            assertFalse(m + ": malzeme adımı çırağa verilmez", l.isStepDone(l.product.toppingStepIndex()));
        }
    }
}
