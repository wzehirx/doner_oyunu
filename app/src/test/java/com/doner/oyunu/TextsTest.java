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
        s.upgradeLevels.put("cirak", 3);
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

    @Test public void summaryShowsWasteMistakesAndPrepRows() {
        DaySummary d = new DaySummary();
        d.day = 3; d.delivered = 4; d.rent = 100; d.moneyAfter = 900; d.startStars = 3; d.endStars = 3.1;
        d.wastedPortions = 7; d.wastedCost = 210; d.mistakes = 2; d.perfect = 5; d.prepBatches = 3; d.apprenticeTasks = 9;
        StringBuilder sb = new StringBuilder();
        for (String[] r : Texts.summaryRows(d)) sb.append(r[0]).append('=').append(r[1]).append(';');
        String t = sb.toString();
        assertTrue(t, t.contains("İsraf=7 porsiyon hazır malzeme atıldı, 210 lira"));
        assertTrue(t, t.contains("Hatalı malzemeli sipariş=2"));
        assertTrue(t, t.contains("Kusursuz malzemeli sipariş=5"));
        assertTrue(t, t.contains("Hazırlık=3 parti"));
        assertTrue(t, t.contains("Çırağın yaptığı iş=9"));
        d.wastedPortions = 0; d.wastedCost = 0;
        sb.setLength(0);
        for (String[] r : Texts.summaryRows(d)) sb.append(r[0]).append('=').append(r[1]).append(';');
        assertTrue(sb.toString(), sb.toString().contains("İsraf=yok"));
        d.prepBatches = 0;
        for (String[] r : Texts.summaryRows(d)) assertFalse("hazırlık yapılmadıysa israf satırı yok", r[0].equals("İsraf"));
    }

    @Test public void helpExplainsPrepToppingsAndApprentice() {
        StringBuilder sb = new StringBuilder();
        for (String[] s : Texts.helpSections()) sb.append(s[0]).append(' ').append(s[1]).append(' ');
        String all = sb.toString();
        for (String k : new String[]{"hazırlık aşamasına", "Dükkânı aç", "Hazırlık yapmadan aç", "Hedefi oku", "Tamam", "israf", "kalem",
                "Siparişi paketle", "Çırak", "Seviye 1", "Seviye 3", "çırak duyurularını"}) {
            assertTrue("yardımda geçmeli: " + k, all.contains(k));
        }
        assertTrue(Texts.quickHelp().contains("Hazırlık"));
        assertTrue(Texts.quickHelp().contains("Hedefi oku"));
    }

    @Test public void apprenticeMarketTextCoversThreeLevels() {
        SaveData s = SaveData.newGame();
        Upgrade cirak = Catalog.upgrade("cirak");
        String d = Texts.upgradeDescription(cirak, s);
        assertTrue(d, d.contains("Seviye 1") && d.contains("Seviye 2") && d.contains("Seviye 3"));
        assertFalse(d.contains("Şu an seviye"));
        s.upgradeLevels.put("cirak", 2);
        assertTrue(Texts.upgradeDescription(cirak, s).contains("Şu an seviye 2"));
        assertEquals(Catalog.upgrade("doner_makinesi").description, Texts.upgradeDescription(Catalog.upgrade("doner_makinesi"), s));
    }

    @Test public void lineDescriptionsExplainStatesAndLocks() {
        SaveData s = SaveData.newGame();
        GameEngine e = new GameEngine(5, s);
        e.setSpawning(false);
        e.startDay();
        Order o = e.spawnOrder(java.util.Arrays.asList("ekmek_arasi", "ayran"), java.util.Arrays.asList("soğansız"));
        s.inventory.add("ayran", 5);
        OrderLine l0 = o.getLine(0), l1 = o.getLine(1);
        String d0 = Texts.lineDescription(e, o, l0, false);
        assertTrue(d0, d0.contains("Sipariş " + o.getPosition() + ", kalem 1 / 2: Ekmek arası döner"));
        assertTrue(d0, d0.contains("Ekmeği ısıt bekliyor, Ekmek ısıtıcı"));
        assertTrue(d0, d0.contains("Malzemeleri ekle kilitli, önce kalemin diğer adımları bitmeli"));
        assertTrue(d0, d0.contains("seçmek için çift dokun"));
        assertTrue(Texts.lineDescription(e, o, l0, true).contains("Bu kalem seçili"));
        assertTrue(Texts.lineSteps(e, o, l0).contains("– Malzemeleri ekle  (kilitli)"));
        assertTrue(Texts.packDescription(o).contains("kilitli"));
        assertTrue(Texts.packText(o).contains("2 kalem bekliyor"));
        assertEquals(StartResult.STARTED, e.startStep(o.id, 1, Station.ICECEK.id));
        assertTrue(Texts.lineDescription(e, o, l1, false).contains("Ayranı doldur sürüyor"));
        assertTrue(Texts.lineSteps(e, o, l1).startsWith("▶ Ayranı doldur"));
        for (int i = 0; i < 40; i++) e.tick(0.1f);
        assertTrue(Texts.lineDescription(e, o, l1, false).contains("Kalem hazır"));
        assertTrue(Texts.orderDescription(o, true, null).contains("İstek: soğansız"));
        assertTrue(Texts.orderDescription(o, true, null).contains("2 kalem"));
    }

    @Test public void jobLabelsNameWhoDoesWhat() {
        SaveData s = SaveData.newGame();
        s.upgradeLevels.put("cirak", 1);
        GameEngine e = new GameEngine(5, s);
        e.beginPrep();
        assertEquals(PrepResult.STARTED, e.startPrepDoner("et_doner"));
        e.tick(0.2f);
        boolean player = false, helper = false;
        for (ActiveStep a : e.getActiveSteps()) {
            String l = Texts.jobLabel(e, a);
            if (a.isApprentice()) { helper = true; assertTrue(l, l.startsWith("Çırak: ")); }
            else { player = true; assertEquals("Sen: döner dilimleme", l); }
        }
        assertTrue(player);
        for (int i = 0; i < 20 && !helper; i++) e.tick(0.5f);
        for (ActiveStep a : e.getActiveSteps()) if (a.isApprentice()) { helper = true; assertTrue(Texts.jobLabel(e, a), Texts.jobLabel(e, a).startsWith("Çırak: ")); }
    }
}
