package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** v0.6 istasyon eylem menüsü: getStationOptions / applyStationOption (TalkBack eylemi ve panel için tek giriş noktası). */
public class StationOptionTest {

    private static final List<String> NONE = Collections.emptyList();

    private static SaveData day(int d) {
        SaveData s = SaveData.newGame();
        s.flowMode = FlowMode.GERCEKCI; // v0.6 akışı = v0.7 Gerçekçi
        s.day = d;
        s.stars = 4.0;
        s.money = 50000;
        Shop.buyRecommended(s);
        return s;
    }

    private static StationOption find(List<StationOption> l, String id) {
        for (StationOption o : l) if (o.id.equals(id)) return o;
        return null;
    }

    private static List<String> ids(List<StationOption> l) {
        List<String> out = new ArrayList<>();
        for (StationOption o : l) out.add(o.id);
        return out;
    }

    private static Order openTopping(GameEngine e, String product) {
        e.setSpawning(false);
        Order o = e.spawnOrder(Collections.singletonList(product), NONE);
        Flow.doItemStation(e, o, 0, Station.EKMEK);
        Flow.doItemStation(e, o, 0, Station.DONER);
        return o;
    }

    // ------------------------------------------------------------ SOS istasyonu
    @Test public void sauceStationListsOneAddActionPerOpenIngredientPlusControls() {
        SaveData s = day(5);
        GameEngine e = new GameEngine(1, s);
        Order o = openTopping(e, "ekmek_arasi");
        List<StationOption> l = e.getStationOptions(o.id, 0, Station.SOS.id);
        assertEquals(Arrays.asList("add:domates", "add:marul", "add:sogan", "add:ketcap", "add:mayonez", "add:aci_sos",
                "undo", "finish", "read:added", "read:target", "prep:tray"), ids(l));
        StationOption dom = find(l, "add:domates");
        assertEquals("Domates ekle", dom.name);
        assertEquals("v0.9: etiket yalnızca ad", "Domates ekle", dom.description);
        assertEquals(dom.description, dom.label());
        assertEquals("domates", dom.ingredientId);
        assertEquals(StationOption.Group.ADD, dom.group);
        assertTrue(dom.enabled);
        assertFalse(dom.outOfStock);
        assertEquals("Ketçap ekle", find(l, "add:ketcap").description);
        assertFalse("henüz bir şey eklenmedi: geri al kilitli", find(l, "undo").enabled);
        assertEquals("Henüz malzeme eklenmedi", find(l, "undo").lockReason);
        assertTrue(find(l, "finish").enabled);
        assertEquals(StationOption.Group.READ, find(l, "read:target").group);
        assertEquals("Eklenenleri oku", find(l, "read:added").name);
        assertEquals("Malzemeleri bitir", find(l, "finish").name);
        assertEquals("Son ekleneni geri al", find(l, "undo").name);
    }

    @Test public void addOptionChangesToBolAndThenLocks() {
        GameEngine e = new GameEngine(1, day(5));
        Order o = openTopping(e, "ekmek_arasi");
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, 0, Station.SOS.id, "add:domates"));
        StationOption busy = find(e.getStationOptions(o.id, 0, Station.SOS.id), "add:marul");
        assertFalse(busy.enabled);
        assertEquals("Ekleme sürüyor", busy.lockReason);
        Flow.waitDone(e, o);
        List<StationOption> l = e.getStationOptions(o.id, 0, Station.SOS.id);
        StationOption bol = find(l, "add:domates");
        assertEquals("Domates bol ekle", bol.name);
        assertEquals("Domates bol ekle, eklendi", bol.description);
        assertTrue(bol.enabled);
        assertTrue(find(l, "undo").enabled);
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, 0, Station.SOS.id, "add:domates"));
        Flow.waitDone(e, o);
        StationOption maxed = find(e.getStationOptions(o.id, 0, Station.SOS.id), "add:domates");
        assertFalse(maxed.enabled);
        assertEquals("En fazla bol, iki kez eklenir", maxed.lockReason);
        assertTrue(maxed.description, maxed.description.contains("bol eklendi"));
        assertEquals(StartResult.MAX_REACHED, e.applyStationOption(o.id, 0, Station.SOS.id, "add:domates"));
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, 0, Station.SOS.id, "undo"));
        assertTrue(find(e.getStationOptions(o.id, 0, Station.SOS.id), "add:domates").enabled);
    }

    @Test public void addOptionsAreLockedUntilTheItemIsReadyForToppings() {
        GameEngine e = new GameEngine(1, day(5));
        e.setSpawning(false);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        List<StationOption> l = e.getStationOptions(o.id, 0, Station.SOS.id);
        StationOption add = find(l, "add:domates");
        assertFalse(add.enabled);
        assertEquals("Önce kalemin diğer adımları bitmeli", add.lockReason);
        assertEquals("Domates ekle, Önce kalemin diğer adımları bitmeli", add.label());
        assertFalse(find(l, "finish").enabled);
        assertEquals(StartResult.STEP_LOCKED, e.applyStationOption(o.id, 0, Station.SOS.id, "add:domates"));
        assertEquals(StartResult.STEP_LOCKED, e.applyStationOption(o.id, 0, Station.SOS.id, "finish"));
    }

    @Test public void outOfStockAddStaysEnabledButFlaggedAndApplyReportsIt() {
        SaveData s = day(5);
        s.inventory.set("sogan", 0);
        GameEngine e = new GameEngine(1, s);
        Order o = openTopping(e, "ekmek_arasi");
        StationOption sog = find(e.getStationOptions(o.id, 0, Station.SOS.id), "add:sogan");
        assertTrue(sog.enabled);
        assertTrue(sog.outOfStock);
        assertEquals("Soğan ekle, stokta yok", sog.description);
        assertEquals(StartResult.OUT_OF_STOCK, e.applyStationOption(o.id, 0, Station.SOS.id, "add:sogan"));
        assertTrue(e.emergencyBuy(sog.ingredientId));
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, 0, Station.SOS.id, "add:sogan"));
    }

    @Test public void trayShownInLabelAndFasterAddition() {
        SaveData s = day(5);
        GameEngine e = new GameEngine(1, s);
        e.beginPrep();
        e.startPrepTray(Arrays.asList("domates", "marul"));
        for (int i = 0; i < 60; i++) e.tick(0.1f);
        e.startDay();
        Order o = openTopping(e, "ekmek_arasi");
        assertEquals("v0.9: tepsi bilgisi etikette yok", "Domates ekle", find(e.getStationOptions(o.id, 0, Station.SOS.id), "add:domates").description);
    }

    @Test public void readOptionsInformWithoutChangingState() {
        SaveData s = day(5);
        GameEngine e = new GameEngine(1, s);
        final List<String> said = new ArrayList<>();
        e.addListener(new Narrator(e, new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { said.add(text); }
        }));
        final List<String> info = new ArrayList<>();
        e.addListener(new GameListenerAdapter() {
            @Override public void onStationInfo(Order o, int i, String optionId) { info.add(i + ":" + optionId); }
        });
        Order o = openTopping(e, "ekmek_arasi");
        said.clear();
        int dom = s.inventory.get("domates");
        assertEquals(StartResult.INFO, e.applyStationOption(o.id, 0, Station.SOS.id, "read:added"));
        assertEquals("Henüz hiçbir malzeme eklenmedi.", said.get(said.size() - 1));
        e.applyStationOption(o.id, 0, Station.SOS.id, "add:domates");
        Flow.waitDone(e, o);
        e.applyStationOption(o.id, 0, Station.SOS.id, "add:domates");
        Flow.waitDone(e, o);
        e.applyStationOption(o.id, 0, Station.SOS.id, "add:marul");
        Flow.waitDone(e, o);
        assertEquals(StartResult.INFO, e.applyStationOption(o.id, -1, Station.SOS.id, "read:added"));
        assertEquals("Eklenenler: domates bol ve marul.", said.get(said.size() - 1));
        assertEquals(StartResult.INFO, e.applyStationOption(o.id, 0, Station.SOS.id, "read:target"));
        assertTrue(said.get(said.size() - 1), said.get(said.size() - 1).startsWith("Hedef, Ekmek arası döner: domates, marul, soğan ve ketçap."));
        assertEquals(Arrays.asList("0:read:added", "0:read:added", "0:read:target"), info);
        assertEquals(dom - 2, s.inventory.get("domates"));
        assertEquals("okumak ekleme yapmaz", 3, o.getLine(0).addedTotal());
    }

    @Test public void finishOptionEndsTheItemAndOpensPacking() {
        GameEngine e = new GameEngine(1, day(5));
        Order o = openTopping(e, "ekmek_arasi");
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, 0, Station.SOS.id, "finish"));
        assertTrue(o.canPack());
        // artık SOS menüsünde ekleme yok, paket seçeneği var
        assertNull(find(e.getStationOptions(o.id, 0, Station.SOS.id), "add:domates"));
        StationOption pack = find(e.getStationOptions(o.id, -1, Station.PAKET.id), "step");
        assertEquals("Siparişi paketle", pack.name);
        assertTrue(pack.enabled);
        assertEquals("Siparişi paketle", pack.description);
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, -1, Station.PAKET.id, "step"));
        assertEquals("Paketleme sürüyor", find(e.getStationOptions(o.id, -1, Station.PAKET.id), "step").lockReason);
        Flow.waitDone(e, o);
        assertTrue(o.isReady());
        assertTrue("hazır sipariş için menü yok", e.getStationOptions(o.id, -1, Station.PAKET.id).isEmpty());
    }

    // ------------------------------------------------------------ diğer istasyonlar
    @Test public void packOptionIsLockedUntilAllLinesAreReady() {
        GameEngine e = new GameEngine(1, day(5));
        e.setSpawning(false);
        Order o = e.spawnOrder(Arrays.asList("ekmek_arasi", "ayran"), NONE);
        StationOption pack = find(e.getStationOptions(o.id, -1, Station.PAKET.id), "step");
        assertFalse(pack.enabled);
        assertEquals("Önce tüm kalemler hazır olmalı", pack.lockReason);
        assertEquals(StartResult.STEP_LOCKED, e.applyStationOption(o.id, -1, Station.PAKET.id, "step"));
    }

    @Test public void genericStepOptionsForBreadDrinkAndOven() {
        GameEngine e = new GameEngine(1, day(5));
        e.setSpawning(false);
        Order o = e.spawnOrder(Arrays.asList("ekmek_arasi", "ayran"), NONE);
        // ekmek istasyonu: çok kalemli siparişte kalem adı öneki
        StationOption bread = find(e.getStationOptions(o.id, -1, Station.EKMEK.id), "step");
        assertEquals("Ekmek arası döner: Ekmeği ısıt", bread.name);
        assertEquals("Ekmek arası döner: Ekmeği ısıt", bread.description);
        assertTrue(bread.enabled);
        // içecek istasyonu: ayran adımı + hazırlık
        List<StationOption> ice = e.getStationOptions(o.id, -1, Station.ICECEK.id);
        assertEquals(Arrays.asList("step", "prep:drink"), ids(ice));
        assertEquals("Ayran: Ayranı doldur", ice.get(0).name);
        assertEquals("4 bardak ayran doldur", ice.get(1).name);
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, -1, Station.ICECEK.id, "step"));
        assertEquals("Çalışıyor", find(e.getStationOptions(o.id, -1, Station.ICECEK.id), "step").lockReason);
        // v2.0: ayran doldurma aktif iş (eller dolu): ekmeği ısıtıcıya koymak da el ister, kilitli
        StationOption bread2 = find(e.getStationOptions(o.id, -1, Station.EKMEK.id), "step");
        assertFalse(bread2.enabled);
        assertEquals("Elin dolu", bread2.lockReason);
        assertEquals(StartResult.HANDS_BUSY, e.applyStationOption(o.id, -1, Station.EKMEK.id, "step"));
        // ayran bitince ekmek ısıtıcıya konur; ısıtıcı pişirirken eller serbest (koyma sonrası) ve ocak/ısıtıcı yeri sayılır
        for (int i = 0; i < 25; i++) e.tick(0.1f);
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, -1, Station.EKMEK.id, "step"));
        assertEquals("Çalışıyor", find(e.getStationOptions(o.id, -1, Station.EKMEK.id), "step").lockReason);
        assertEquals(1, e.getStationUsed(Station.EKMEK));
    }

    @Test public void sequentialStepsShowTheirLockReason() {
        GameEngine e = new GameEngine(1, day(16));
        e.setSpawning(false);
        Order o = e.spawnOrder(Collections.singletonList("kunefe"), NONE);
        StationOption first = find(e.getStationOptions(o.id, -1, Station.TATLI.id), "step");
        assertEquals("Kadayıfı tereyağıyla yoğur", first.name);
        assertTrue(first.enabled);
        // fırın adımı sıralı: önceki adımlar bitmeden kilitli
        StationOption oven = find(e.getStationOptions(o.id, -1, Station.IZGARA.id), "step");
        assertFalse(oven.enabled);
        assertEquals("Önce kalemin önceki adımları bitmeli", oven.lockReason);
        assertEquals(StartResult.STEP_LOCKED, e.applyStationOption(o.id, -1, Station.IZGARA.id, "step"));
    }

    @Test public void wrongStationStepOptionCostsTheUsualPenalty() {
        GameEngine e = new GameEngine(1, day(5));
        e.setSpawning(false);
        Order o = e.spawnOrder(Collections.singletonList("ayran"), NONE);
        assertTrue("alakasız istasyonda sipariş seçeneği yok", find(e.getStationOptions(o.id, -1, Station.EKMEK.id), "step") == null);
        assertEquals(StartResult.WRONG_STATION, e.applyStationOption(o.id, -1, Station.EKMEK.id, "step"));
        assertEquals(1, e.getSummary().wrongTaps);
    }

    @Test public void multiLineOrdersResolveTheLineByStationAndSelection() {
        GameEngine e = new GameEngine(1, day(8));
        e.setSpawning(false);
        Order o = e.spawnOrder(Arrays.asList("ekmek_arasi", "tavuk_ekmek"), NONE);
        assertEquals(0, e.resolveStationItem(o.id, -1, Station.EKMEK.id));
        assertEquals(1, e.resolveStationItem(o.id, 1, Station.EKMEK.id));
        e.selectOrder(o.id);
        e.selectItem(1);
        assertEquals("seçili kalem öncelikli", 1, e.resolveStationItem(o.id, -1, Station.EKMEK.id));
        StationOption bread = find(e.getStationOptions(o.id, -1, Station.EKMEK.id), "step");
        assertEquals("Ekmek arası tavuk döner: Ekmeği ısıt", bread.name);
        assertEquals(-1, e.resolveStationItem(o.id, -1, Station.PAKET.id));
        assertEquals(-1, e.resolveStationItem(999, -1, Station.EKMEK.id));
        assertEquals(-1, e.resolveStationItem(o.id, 9, Station.EKMEK.id));
        assertEquals("o istasyonda işi olmayan siparişte", -1, e.resolveStationItem(o.id, -1, Station.TATLI.id));
    }

    // ------------------------------------------------------------ hazırlık seçenekleri
    @Test public void prepOptionsExistWithoutAnOrderAndTrayStarts() {
        SaveData s = day(5);
        GameEngine e = new GameEngine(1, s);
        List<StationOption> sos = e.getStationOptions(-1, -1, Station.SOS.id);
        assertEquals(Collections.singletonList("prep:tray"), ids(sos));
        assertTrue(sos.get(0).enabled);
        assertEquals(Collections.singletonList("prep:drink"), ids(e.getStationOptions(-1, -1, Station.ICECEK.id)));
        assertTrue(e.getStationOptions(-1, -1, Station.EKMEK.id).isEmpty());
        assertTrue(e.getStationOptions(-1, -1, Station.PAKET.id).isEmpty());
        assertEquals(StartResult.STARTED, e.applyStationOption(-1, -1, Station.SOS.id, "prep:tray"));
        assertTrue(e.isPrepPhase());
        for (int i = 0; i < 60; i++) e.tick(0.1f);
        assertTrue("tepsi hazır: " + e.getTrayTotal(), e.getTrayTotal() >= 12);
        assertEquals(StartResult.STARTED, e.applyStationOption(-1, -1, Station.ICECEK.id, "prep:drink"));
        for (int i = 0; i < 50; i++) e.tick(0.1f);
        assertEquals(4, e.getReadyDrinks());
    }

    @Test public void prepOptionsAreDisabledWithReasons() {
        SaveData s = day(5);
        s.inventory.set("ayran", 2);
        GameEngine e = new GameEngine(1, s);
        StationOption drink = e.getStationOptions(-1, -1, Station.ICECEK.id).get(0);
        assertFalse(drink.enabled);
        assertEquals("Malzeme yetmiyor: Ayran şişesi", drink.lockReason);
        assertEquals(StartResult.OUT_OF_STOCK, e.applyStationOption(-1, -1, Station.ICECEK.id, "prep:drink"));
        s.inventory.set("et_doner", 3);
        StationOption four = e.getStationOptions(-1, -1, Station.DONER.id).get(0);
        assertFalse(four.enabled);
        assertTrue(four.lockReason, four.lockReason.startsWith("Malzeme yetmiyor"));
        assertTrue("2 porsiyon yapılabilir", find(e.getStationOptions(-1, -1, Station.DONER.id), "prep:doner:et_doner:2").enabled);
    }

    // ------------------------------------------------------------ hatalar
    @Test public void invalidOptionsAreRejectedSafely() {
        GameEngine e = new GameEngine(1, day(5));
        final List<String> rej = new ArrayList<>();
        e.addListener(new GameListenerAdapter() {
            @Override public void onOptionRejected(Order o, String id, StartResult r) { rej.add(id + ":" + r); }
        });
        Order o = openTopping(e, "ekmek_arasi");
        assertEquals(StartResult.UNKNOWN_OPTION, e.applyStationOption(o.id, 0, Station.SOS.id, null));
        assertEquals(StartResult.UNKNOWN_OPTION, e.applyStationOption(o.id, 0, Station.SOS.id, "x"));
        assertEquals(StartResult.UNKNOWN_OPTION, e.applyStationOption(o.id, 0, 99, "step"));
        assertEquals(StartResult.UNKNOWN_OPTION, e.applyStationOption(o.id, 0, Station.DONER.id, "add:domates"));
        assertEquals(StartResult.UNKNOWN_OPTION, e.applyStationOption(o.id, 0, Station.SOS.id, "prep:doner:et_doner:x"));
        assertEquals(StartResult.UNKNOWN_OPTION, e.applyStationOption(o.id, 0, Station.SOS.id, "prep:gizli"));
        assertEquals(StartResult.NO_ORDER, e.applyStationOption(999, 0, Station.SOS.id, "add:domates"));
        assertEquals(StartResult.NO_ORDER, e.applyStationOption(999, 0, Station.SOS.id, "read:target"));
        assertEquals(StartResult.INVALID, e.applyStationOption(o.id, 0, Station.SOS.id, "add:pirinc"));
        assertEquals(6, rej.size());
        assertEquals(0, o.getLine(0).addedTotal());
    }

    @Test public void noOptionsAfterTheDayEnds() {
        GameEngine e = new GameEngine(1, day(5));
        e.setSpawning(false);
        e.tick(1000f);
        assertFalse(e.isRunning());
        assertTrue(e.getStationOptions(-1, -1, Station.DONER.id).isEmpty());
        assertEquals(StartResult.NOT_RUNNING, e.applyStationOption(-1, -1, Station.DONER.id, "prep:doner:et_doner:4"));
    }

    @Test public void tapPathAndActionPathGiveTheSameResult() {
        // panel ve TalkBack eylemi aynı seçenek listesini kullanır: iki ayrı çağrı aynı listeyi verir, uygulama aynı sonucu
        GameEngine a = new GameEngine(3, day(5)), b = new GameEngine(3, day(5));
        Order oa = openTopping(a, "ekmek_arasi"), ob = openTopping(b, "ekmek_arasi");
        assertEquals(ids(a.getStationOptions(oa.id, 0, Station.SOS.id)), ids(b.getStationOptions(ob.id, 0, Station.SOS.id)));
        StartResult viaOption = a.applyStationOption(oa.id, 0, Station.SOS.id, "add:ketcap");
        StartResult direct = b.addTopping(ob.id, 0, "ketcap");
        assertEquals(direct, viaOption);
        assertEquals(b.getSave().inventory.get("ketcap"), a.getSave().inventory.get("ketcap"));
    }
}
