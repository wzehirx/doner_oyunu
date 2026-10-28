package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;
import com.doner.oyunu.ui.Texts;

import org.junit.Test;

import java.util.List;

/** Saf JVM: arayüz metin / contentDescription üreticileri. */
public class TextsTest {

    private static GameEngine runUntilOrder(SaveData s) {
        GameEngine e = new GameEngine(7, s);
        for (int i = 0; i < 400 && e.getOrders().isEmpty(); i++) e.tick(0.1f);
        assertFalse(e.getOrders().isEmpty());
        return e;
    }

    @Test public void formatting() {
        assertEquals("1:05", Texts.clock(65f));
        assertEquals("0:00", Texts.clock(-3f));
        assertEquals("120 TL", Texts.money(120));
        assertEquals("1,3 kat", Texts.times(1.25));
        assertEquals("%70", Texts.percentShort(0.7));
        assertEquals("70 yüzde", Texts.volumeSpoken(0.7));
    }

    @Test public void secondsAreBucketed() {
        assertEquals(3, Texts.roundedSeconds(0.5f));
        assertEquals(10, Texts.roundedSeconds(8.1f));
        assertEquals(30, Texts.roundedSeconds(21f));
        assertEquals(120, Texts.roundedSeconds(500f));
    }

    @Test public void patienceWords() {
        assertEquals("sabır iyi", Texts.patienceWord(0.9f));
        assertEquals("sabır azalıyor", Texts.patienceWord(0.4f));
        assertEquals("sabır kritik", Texts.patienceWord(0.1f));
    }

    @Test public void orderDescriptionIsCompleteTurkishSentence() {
        GameEngine e = runUntilOrder(SaveData.newGame());
        Order o = e.getOrders().get(0);
        e.selectOrder(o.id);
        String d = Texts.orderDescription(o, true, null);
        assertTrue(d, d.contains(o.customer.name));
        assertTrue(d, d.contains("Seçili"));
        assertTrue(d, d.contains(o.nextStep().name));
        assertTrue(d, d.contains(o.nextStep().station.displayName));
        assertTrue(d, d.contains("sabır"));
        String d2 = Texts.orderDescription(o, false, null);
        assertTrue(d2, d2.contains("Seçmek için"));
        assertFalse(d2.contains("Seçili."));
    }

    @Test public void stationDescriptionShowsStateAndNeed() {
        GameEngine e = runUntilOrder(SaveData.newGame());
        Narrator n = new Narrator(e);
        Order o = e.getOrders().get(0);
        e.selectOrder(o.id);
        Station need = o.nextStep().station;
        String d = Texts.stationDescription(n, e, need, true);
        assertTrue(d, d.contains("boş"));
        assertTrue(d, d.contains("için gerekli"));
        assertEquals("Şimdi bu", Texts.stationVisualStatus(e, need));
        assertEquals(StartResult.STARTED, e.startStep(o.id, need.id));
        String d2 = Texts.stationDescription(n, e, need, true);
        assertTrue(d2, d2.contains("çalışıyor"));
        assertEquals("Çalışıyor", Texts.stationVisualStatus(e, need));
        String unused = Texts.stationDescription(n, e, Station.TATLI, false);
        assertTrue(unused, unused.contains("kullanılmıyor"));
    }

    @Test public void deliverDescriptionNamesReadyOrders() {
        GameEngine e = runUntilOrder(SaveData.newGame());
        assertTrue(Texts.deliverDescription(e).contains("hazır sipariş yok"));
    }

    @Test public void stationsInUseGrowWithDays() {
        assertTrue(Texts.stationsInUse(1).contains(Station.DONER));
        assertFalse(Texts.stationsInUse(1).contains(Station.TATLI));
        assertTrue(Texts.stationsInUse(16).contains(Station.TATLI));
    }

    @Test public void upgradeStatusExplainsWhy() {
        SaveData s = SaveData.newGame();
        Upgrade cirak = Catalog.upgrade("cirak");
        s.day = 1;
        assertTrue(Texts.upgradeStatus(cirak, s).startsWith("Kilitli"));
        s.day = 3;
        s.money = 10;
        assertTrue(Texts.upgradeStatus(cirak, s).startsWith("Paran yetmiyor"));
        assertFalse(Texts.upgradeBuyable(cirak, s));
        s.money = 99999;
        assertTrue(Texts.upgradeStatus(cirak, s).startsWith("Satın alınabilir"));
        assertTrue(Texts.upgradeBuyable(cirak, s));
        s.upgradeLevels.put("cirak", 2);
        assertEquals("En üst seviyede", Texts.upgradeStatus(cirak, s));
    }

    @Test public void recommendedDescriptionDependsOnStock() {
        SaveData s = SaveData.newGame();
        s.inventory = new Inventory();
        String d = Texts.recommendedDescription(s);
        assertTrue(d, d.contains("Tahmini maliyet"));
        Shop.buyRecommended(s);
        s.money = 100000;
        Shop.buyRecommended(s);
        assertTrue(Texts.recommendedDescription(s).contains("yeterli"));
    }

    @Test public void briefingMentionsNewProductsAndRent() {
        SaveData s = SaveData.newGame();
        List<String> l = Texts.briefingLines(s);
        String all = String.join(" ", l);
        assertTrue(all, all.contains("Yeni ürün"));
        assertTrue(all, all.contains("kira"));
        assertTrue(all, all.contains("Öğretici"));
        s.day = 3;
        assertTrue(String.join(" ", Texts.briefingLines(s)).contains("Pilav üstü döner"));
    }

    @Test public void summaryRowsCoverMoney() {
        DaySummary d = new DaySummary();
        d.day = 2; d.delivered = 5; d.lost = 1; d.revenue = 1200; d.tips = 80; d.rent = 330; d.moneyAfter = 5950;
        d.startStars = 3.0; d.endStars = 3.4; d.emergencySpend = 100;
        StringBuilder sb = new StringBuilder();
        for (String[] r : Texts.summaryRows(d)) sb.append(r[0]).append('=').append(r[1]).append(';');
        String t = sb.toString();
        assertTrue(t, t.contains("Teslim edilen sipariş=5"));
        assertTrue(t, t.contains("Acil alım=100 lira"));
        assertTrue(t, t.contains("Net kazanç=850 lira"));
        assertTrue(t, t.contains("3,4"));
        d.revenue = 0; d.tips = 0; d.emergencySpend = 0;
        StringBuilder neg = new StringBuilder();
        for (String[] r : Texts.summaryRows(d)) neg.append(r[1]).append(';');
        assertTrue(neg.toString(), neg.toString().contains("eksi 330 lira"));
    }

    @Test public void helpIsComplete() {
        List<String[]> h = Texts.helpSections();
        assertTrue(h.size() >= 6);
        for (String[] s : h) { assertFalse(s[0].isEmpty()); assertTrue(s[1].length() > 30); }
    }
}
