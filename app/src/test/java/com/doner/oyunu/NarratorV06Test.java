package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** v0.6 duyurular: boy, tek tek ekleme, geri al, okuma eylemleri, reddedilen seçenekler, yeni boy/malzeme. */
public class NarratorV06Test {
    private static final List<String> NONE = Collections.emptyList();
    private final List<String> said = new ArrayList<>();

    private GameEngine engine(int day) {
        SaveData s = SaveData.newGame();
        s.flowMode = FlowMode.GERCEKCI; // v0.6 akışı = v0.7 Gerçekçi
        s.day = day;
        s.stars = 5.0;
        s.money = 50000;
        Shop.buyRecommended(s);
        s.inventory.set("et_doner", 30);
        GameEngine e = new GameEngine(3, s);
        e.setSpawning(false);
        e.addListener(new Narrator(e, new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { said.add(text); }
        }));
        return e;
    }

    private String last() { return said.get(said.size() - 1); }

    @Test public void arrivalAnnouncesSizeAndRequests() {
        GameEngine e = engine(5);
        Order o = e.spawnOrder(Arrays.asList("ekmek_arasi", "ayran"), Collections.singletonList("soğansız"),
                Arrays.asList(PortionSize.HALF, PortionSize.FULL));
        String t = last();
        assertTrue(t, t.startsWith("Yeni müşteri: " + o.customer.name + ", Ekmek arası döner (yarım ekmek) ve Ayran. İstek: soğansız."));
        Order d = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE, Collections.singletonList(PortionSize.DOUBLE));
        assertTrue(last(), last().contains("Ekmek arası döner (duble)."));
        Order f = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        assertFalse("tam ekmek ayrıca söylenmez", last().contains("ekmek)") || last().contains("(tam"));
        assertEquals("Ekmek arası döner (yarım ekmek) ve Ayran", o.itemsText());
        assertEquals("Ekmek arası döner (duble)", d.itemsText());
        assertEquals("Ekmek arası döner", f.itemsText());
        e.selectOrder(o.id);
        boolean found = false;
        for (String x : said) if (x.startsWith("Seçilen sipariş") && x.contains("Ekmek arası döner (yarım ekmek) ve Ayran")) found = true;
        assertTrue(said.toString(), found);
    }

    @Test public void hintAndStartAnnounceTheSize() {
        GameEngine e = engine(5);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE, Collections.singletonList(PortionSize.HALF));
        e.selectOrder(o.id);
        assertEquals("Önerilen: Ekmeği ısıt, yarım ekmek. Ekmek ısıtıcı.", last());
        Flow.doItemStation(e, o, 0, Station.EKMEK);
        said.clear();
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, 0, Station.DONER.id, "size:duble"));
        assertTrue(said.toString(), said.contains("Döneri dilimle başladı, duble, 5 saniye."));
        Flow.waitDone(e, o);
        Order p = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        Flow.doItemStation(e, p, 0, Station.EKMEK);
        said.clear();
        e.applyStationOption(p.id, 0, Station.DONER.id, "size:tam");
        assertTrue("2. günden itibaren tam ekmek de söylenir: " + said, said.contains("Döneri dilimle başladı, tam ekmek, 4 saniye."));
    }

    @Test public void dayOneDoesNotMentionSizes() {
        GameEngine e = engine(1);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        Flow.doItemStation(e, o, 0, Station.EKMEK);
        said.clear();
        e.applyStationOption(o.id, 0, Station.DONER.id, "size:tam");
        assertTrue(said.toString(), said.contains("Döneri dilimle başladı, 4 saniye."));
    }

    @Test public void addedUndoneAndFinishedAreSpoken() {
        GameEngine e = engine(5);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        Flow.doItemStation(e, o, 0, Station.EKMEK);
        Flow.doItemStation(e, o, 0, Station.DONER);
        said.clear();
        e.addTopping(o.id, 0, "domates");
        assertTrue("başlangıç sessiz", said.isEmpty());
        Flow.waitDone(e, o);
        assertEquals("Domates eklendi.", last());
        e.addTopping(o.id, 0, "domates");
        Flow.waitDone(e, o);
        assertEquals("Domates bol eklendi.", last());
        e.addTopping(o.id, 0, "domates");
        assertEquals("Domates zaten bol, en fazla iki kez eklenir.", last());
        e.removeLastTopping(o.id, 0);
        assertEquals("Domates geri alındı, stoğa döndü. Bir kez kaldı.", last());
        e.removeLastTopping(o.id, 0);
        assertEquals("Domates geri alındı, stoğa döndü.", last());
        e.removeLastTopping(o.id, 0);
        assertEquals("Geri alınacak malzeme yok.", last());
        e.addTopping(o.id, 0, "marul");
        e.removeLastTopping(o.id, 0);
        assertEquals("Bir ekleme sürüyor, bitmesini bekle.", last());
        Flow.waitDone(e, o);
        said.clear();
        e.finishToppings(o.id, 0);
        assertTrue(said.toString(), said.get(0).startsWith("Malzemeleri ekle bitti."));
        assertFalse("v0.9: bitiş duyurusu sıradaki adımı tekrarlamaz", said.get(0).contains("Önerilen"));
        Narrator n = new Narrator(e);
        assertEquals("Ekmek arası döner malzemeleri tamam. Eklenen: marul.", n.toppingsFinished(o, 0, o.getLine(0).getChosen()));
        assertEquals("Ekmek arası döner malzemeleri tamam. Hiç malzeme eklenmedi.", n.toppingsFinished(o, 0, Collections.<String>emptyList()));
    }

    @Test public void lockedAndRejectedOptionsAreSpoken() {
        GameEngine e = engine(1);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        Flow.doItemStation(e, o, 0, Station.EKMEK);
        e.applyStationOption(o.id, 0, Station.DONER.id, "size:yarim");
        assertEquals("Yarım ekmek boy 2. günde açılır.", last());
        e.applyStationOption(o.id, 0, Station.DONER.id, "size:duble");
        assertEquals("Duble boy 4. günde açılır.", last());
        e.applyStationOption(o.id, 0, Station.DONER.id, "bilinmeyen");
        assertEquals("Bilinmeyen seçenek.", last());
        Narrator n = new Narrator(e);
        assertEquals("Bu seçenek henüz açılmadı.", n.optionRejected(o, "x", StartResult.LOCKED));
        assertEquals("Bu seçenek şu an uygulanamaz.", n.optionRejected(o, "x", StartResult.INVALID));
        assertEquals("Hazır stok dolu.", n.optionRejected(o, "x", StartResult.FULL));
    }

    @Test public void sizeMistakeComplaint() {
        GameEngine e = engine(5);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE, Collections.singletonList(PortionSize.HALF));
        Flow.doItemStation(e, o, 0, Station.EKMEK);
        e.applyStationOption(o.id, 0, Station.DONER.id, "size:duble");
        Flow.waitDone(e, o);
        Flow.doItemStation(e, o, 0, Station.SOS);
        Flow.makeReady(e, o);
        said.clear();
        e.deliver(o.id);
        String t = said.get(0);
        assertTrue(t, t.startsWith("Hatalı sipariş. Yarım ekmek istemiştim, duble yapmışsın. Çok fazla."));
        assertTrue(t, t.contains("bahşiş yarıya indi"));
        Narrator n = new Narrator(e);
        ToppingCheck c = new ToppingCheck();
        c.addSize(new ToppingCheck.SizeMistake("Ekmek arası döner", PortionSize.DOUBLE, PortionSize.FULL));
        assertTrue(n.orderMistake(o, c, 0.05), n.orderMistake(o, c, 0.05).contains("Duble istemiştim, tam ekmek yapmışsın. Çok az."));
    }

    @Test public void newSizeAnnouncements() {
        assertEquals("", Narrator.newSizesText(1));
        assertEquals("Yeni boy: yarım ekmek. Döner makinesinde boyu sen seçersin.", Narrator.newSizesText(2));
        assertEquals("", Narrator.newSizesText(3));
        assertEquals("Yeni boy: duble. Döner makinesinde boyu sen seçersin.", Narrator.newSizesText(4));
        GameEngine e = engine(2);
        String t = new Narrator(e).dayStarted(2, DayConfig.forDay(2));
        assertTrue(t, t.contains("Yeni ürün: Ayran. Yeni boy: yarım ekmek."));
    }

    @Test public void stationLabelAndStatusMentionSizeAndHalfPortions() {
        GameEngine e = engine(5);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE, Collections.singletonList(PortionSize.HALF));
        e.selectOrder(o.id);
        Narrator n = new Narrator(e);
        String l = n.stationLabel(Station.DONER);
        assertTrue(l, l.contains("sıradaki: Döneri dilimle, yarım ekmek"));
        assertEquals("", n.readyStockText());
        e.beginPrep();
        e.startPrepDoner("et_doner", 2);
        for (int i = 0; i < 40; i++) e.tick(0.1f);
        assertEquals("Hazır: döner 2 porsiyon.", n.readyStockText());
        assertTrue(n.stationLabel(Station.DONER), n.stationLabel(Station.DONER).contains("hazır 2 porsiyon"));
        assertEquals("2 porsiyon döner dilimlendi. Hazır döner 2 porsiyon.", n.prepFinished(PrepKind.DONER, false, 2));
    }

    @Test public void everyStartResultHasText() {
        Narrator n = new Narrator(null);
        for (StartResult r : StartResult.values()) assertNotNull(r.name(), n.startResultText(r));
        assertEquals("", n.startResultText(StartResult.STARTED));
        for (StartResult r : StartResult.values()) {
            if (r != StartResult.STARTED) assertFalse(r.name(), n.startResultText(r).isEmpty());
        }
    }

    @Test public void optionLabelsAreSpokenAsTheyAre() {
        GameEngine e = engine(5);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        Narrator n = new Narrator(e);
        for (StationOption op : e.getStationOptions(o.id, 0, Station.SOS.id)) {
            assertFalse(op.id, n.optionLabel(op).isEmpty());
            assertEquals(op.label(), n.optionLabel(op));
        }
    }
}
