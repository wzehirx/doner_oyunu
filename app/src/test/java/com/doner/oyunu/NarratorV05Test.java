package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** v0.5 duyuru metinleri: kalem bazlı sipariş, öneri, kilit, istasyon etiketi, gün sonu israfı. */
public class NarratorV05Test {
    private static final List<String> NONE = Collections.emptyList();

    private static GameEngine engine(int day) {
        SaveData s = SaveData.newGame();
        s.day = day;
        s.stars = 5.0;
        s.money = 50000;
        Shop.buyRecommended(s);
        GameEngine e = new GameEngine(3, s);
        e.setSpawning(false);
        return e;
    }

    @Test public void itemBasedOrderAnnouncements() {
        GameEngine e = engine(3);
        final List<String> said = new ArrayList<>();
        Narrator n = new Narrator(e, new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { said.add(text); }
        });
        e.addListener(n);
        Order o = e.spawnOrder(Arrays.asList("ekmek_arasi", "ayran"), Collections.singletonList("soğansız"));
        String arrive = said.get(said.size() - 1);
        assertTrue(arrive, arrive.startsWith("Yeni müşteri: " + o.customer.name + ", Ekmek arası döner ve Ayran. İstek: soğansız."));
        assertTrue(arrive, arrive.endsWith("Sipariş " + o.getPosition() + "."));
        e.selectOrder(o.id);
        assertTrue(said.toString(), said.contains("Önerilen: Ekmek arası döner: Ekmeği ısıt. Ekmek ısıtıcı."));
        assertTrue(e.selectItem(1));
        assertTrue(said.toString(), said.contains("Kalem 2 seçildi: Ayran."));
        assertTrue(said.toString(), said.contains("Önerilen: Ayran: Ayranı doldur. İçecek dolabı."));
        assertEquals(StartResult.STARTED, e.startStep(o.id, Station.ICECEK.id));
        assertTrue(said.get(said.size() - 1), said.get(said.size() - 1).startsWith("Ayran: Ayranı doldur başladı"));
        Flow.waitDone(e, o);
        String fin = null;
        for (String t : said) if (t.startsWith("Ayran: Ayranı doldur bitti.")) fin = t;
        assertNotNull(said.toString(), fin);
        assertEquals("v0.9: bitiş duyurusu kısa, sıradaki adım ayrı duyuru", "Ayran: Ayranı doldur bitti.", fin);
        assertFalse(fin.contains("Sıradaki"));
        assertTrue(said.toString(), said.contains("Önerilen: Ekmek arası döner: Ekmeği ısıt. Ekmek ısıtıcı."));
    }

    @Test public void lockedAndPackAnnouncements() {
        GameEngine e = engine(3);
        final List<String> said = new ArrayList<>();
        e.addListener(new Narrator(e, new Narrator.Output() {
            @Override public void say(String text, boolean urgent) { said.add(text); }
        }));
        Order o = e.spawnOrder(Collections.singletonList("ayran"), NONE);
        e.selectOrder(o.id);
        e.startStep(o.id, Station.PAKET.id);
        assertEquals("Paketlemeden önce tüm kalemler hazır olmalı.", said.get(said.size() - 1));
        Flow.doItemStation(e, o, 0, Station.ICECEK);
        assertTrue(said.toString(), said.contains("Ayranı doldur bitti."));
        assertTrue(said.toString(), said.contains("Önerilen: Siparişi paketle. Paketleme."));
        Flow.doItemStation(e, o, 0, Station.PAKET);
        assertTrue(said.toString(), said.contains("Sipariş hazır: " + o.customer.name + ". Teslim et."));
        Order t = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        e.startStep(t.id, Station.SOS.id);
        assertTrue(said.get(said.size() - 1), said.get(said.size() - 1).startsWith("Malzeme eklemeden önce"));
    }

    @Test public void stationLabelForPackAndReadyStock() {
        GameEngine e = engine(3);
        Narrator n = new Narrator(e);
        Order o = e.spawnOrder(Arrays.asList("ekmek_arasi", "ayran"), NONE);
        e.selectOrder(o.id);
        assertEquals("Ekmek ısıtıcı, boş, sıradaki: Ekmeği ısıt", n.stationLabel(Station.EKMEK));
        assertEquals("İçecek dolabı, boş, sıradaki: Ayranı doldur", n.stationLabel(Station.ICECEK));
        assertEquals("Paketleme, boş", n.stationLabel(Station.PAKET));
        e.getSave().readyDrinks = 3;
        assertTrue(n.stationLabel(Station.ICECEK).endsWith("hazır 3 bardak ayran"));
        Flow.doItemStation(e, o, 0, Station.EKMEK);
        Flow.doItemStation(e, o, 0, Station.DONER);
        Flow.doItemStation(e, o, 0, Station.SOS);
        Flow.doItemStation(e, o, 1, Station.ICECEK);
        assertEquals("Paketleme, boş, sıradaki: Siparişi paketle", n.stationLabel(Station.PAKET));
        assertEquals("", new Narrator(null).readyStockText());
    }

    @Test public void startResultTextCoversEveryResult() {
        Narrator n = new Narrator(null);
        for (StartResult r : StartResult.values()) {
            if (r == StartResult.STARTED) assertEquals("", n.startResultText(r));
            else assertFalse(r.name(), n.startResultText(r).isEmpty());
        }
        assertTrue(n.startResultText(StartResult.NEEDS_TOPPINGS).contains("Malzeme"));
    }

    @Test public void dayFinishedMentionsWasteAndMistakes() {
        DaySummary d = new DaySummary();
        d.day = 2; d.delivered = 5; d.revenue = 1000; d.rentDue = 330; d.rent = 330; d.moneyAfter = 5000; d.endStars = 4.0;
        d.wastedPortions = 6; d.wastedCost = 140; d.mistakes = 2;
        String t = new Narrator(null).dayFinished(d);
        assertTrue(t, t.contains("İsraf: 6 porsiyon hazır malzeme atıldı, değeri 140 lira."));
        assertTrue(t, t.contains("Hatalı malzemeli sipariş: 2."));
        d.wastedPortions = 0; d.mistakes = 0;
        t = new Narrator(null).dayFinished(d);
        assertFalse(t.contains("İsraf"));
    }

    @Test public void prepRejectedTexts() {
        Narrator n = new Narrator(null);
        assertTrue(n.prepRejected(PrepKind.DONER, PrepResult.FULL, null).contains("Hazır stok dolu"));
        assertTrue(n.prepRejected(PrepKind.DONER, PrepResult.OUT_OF_STOCK, Catalog.ingredient("et_doner")).contains("Et döner yetmiyor"));
        // v2.0: ortak yuva yok; eller dolu / istasyon dolu
        assertTrue(n.prepRejected(PrepKind.DRINK, PrepResult.HANDS_BUSY, null).contains("Elin dolu"));
        assertTrue(n.prepRejected(PrepKind.FRIES, PrepResult.STATION_FULL, null).contains("Ocak dolu"));
        assertFalse(n.prepRejected(PrepKind.TRAY, PrepResult.INVALID, null).isEmpty());
        assertFalse(n.prepRejected(PrepKind.TRAY, PrepResult.NOT_ALLOWED, null).isEmpty());
    }

    @Test public void joinNamesIsTurkishList() {
        assertEquals("domates, marul ve ketçap", Narrator.joinNames(Arrays.asList("domates", "marul", "ketcap")));
        assertEquals("soğan", Narrator.joinNames(Arrays.asList("sogan")));
        assertEquals("", Narrator.joinNames(Collections.<String>emptyList()));
    }
}
