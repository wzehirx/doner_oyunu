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
        assertTrue(d, d.contains("seçili"));
        assertTrue(d, d.startsWith("Sipariş " + o.getPosition() + ", " + o.customer.name + ", " + o.itemsText()));
        assertTrue("v0.9: kısa etiket", d.length() <= 60 + 20);
        assertFalse("v0.9: 'çift dokun' ipucu yok", d.contains("dokun"));
        String d2 = Texts.orderDescription(o, false, null);
        assertFalse(d2.contains("seçili"));
        assertFalse(d2, d2.contains("dokun"));
    }

    @Test public void stationDescriptionShowsStateAndNeed() {
        GameEngine e = runUntilOrder(SaveData.newGame());
        Narrator n = new Narrator(e);
        Order o = e.getOrders().get(0);
        e.selectOrder(o.id);
        Station need = o.nextStep().station;
        String d = Texts.stationDescription(n, e, need, true);
        assertTrue(d, d.contains("boş"));
        assertTrue(d, d.contains("sıradaki: " + o.nextStep().name));
        assertFalse("v0.9: ipucu cümlesi yok", d.contains("Dokununca"));
        assertEquals("Şimdi bu", Texts.stationVisualStatus(e, need));
        assertEquals(StartResult.STARTED, e.startStep(o.id, need.id));
        String d2 = Texts.stationDescription(n, e, need, true);
        assertTrue(d2, d2.contains("çalışıyor"));
        assertEquals("Çalışıyor", Texts.stationVisualStatus(e, need));
        String unused = Texts.stationDescription(n, e, Station.TATLI, false);
        assertTrue(unused, unused.contains("menüde yok"));
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
        assertTrue(d, d.matches("Önerilen malzemeleri al, \\d+ lira.*"));
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
        for (String k : new String[]{"hazırlık aşamasına", "Dükkânı aç", "Hazırlık yapmadan aç", "Hedefi oku", "Malzemeleri bitir", "TalkBack Eylemleri", "Eylemler menüsü", "yarım ekmek", "Boy seç", "israf", "kalem",
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
        assertEquals("Sipariş " + o.getPosition() + ", kalem 1, Ekmek arası döner, 0/3 adım", d0);
        assertEquals("Sipariş " + o.getPosition() + ", kalem 1, Ekmek arası döner, 0/3 adım, seçili", Texts.lineDescription(e, o, l0, true));
        assertTrue(Texts.lineSteps(e, o, l0).contains("– Malzemeleri ekle  (kilitli)"));
        assertEquals("Siparişi paketle, kilitli", Texts.packDescription(o));
        assertTrue(Texts.packText(o).contains("2 kalem bekliyor"));
        assertEquals(StartResult.STARTED, e.startStep(o.id, 1, Station.ICECEK.id));
        assertEquals("Sipariş " + o.getPosition() + ", kalem 2, Ayran, 0/1 adım, Ayranı doldur sürüyor", Texts.lineDescription(e, o, l1, false));
        assertTrue(Texts.lineSteps(e, o, l1).startsWith("▶ Ayranı doldur"));
        for (int i = 0; i < 40; i++) e.tick(0.1f);
        assertEquals("Sipariş " + o.getPosition() + ", kalem 2, Ayran, hazır", Texts.lineDescription(e, o, l1, false));
        assertTrue(Texts.orderDescription(o, true, null).contains(", soğansız"));
        assertTrue(Texts.orderDescription(o, true, null).contains("ayran") || Texts.orderDescription(o, true, null).contains("Ayran"));
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

    // ------------------------------------------------------------------ v0.6 metinleri
    @Test public void summaryShowsSizeMistakeRow() {
        DaySummary d = new DaySummary();
        d.day = 4; d.delivered = 3; d.rent = 100; d.moneyAfter = 900; d.startStars = 3; d.endStars = 3.1;
        d.mistakes = 2; d.sizeMistakes = 1;
        StringBuilder sb = new StringBuilder();
        for (String[] r : Texts.summaryRows(d)) sb.append(r[0]).append('=').append(r[1]).append(';');
        assertTrue(sb.toString(), sb.toString().contains("Yanlış boyda dilimlenen sipariş=1"));
        d.sizeMistakes = 0;
        for (String[] r : Texts.summaryRows(d)) assertFalse(r[0].contains("boy"));
    }

    @Test public void briefingAnnouncesNewIngredientsSizesAndMenus() {
        SaveData s = SaveData.newGame();
        s.day = 2;
        assertTrue(Texts.briefingLines(s).toString(), Texts.briefingLines(s).toString().contains("Yeni boy: yarım ekmek"));
        s.day = 3;
        assertTrue(Texts.briefingLines(s).toString(), Texts.briefingLines(s).toString().contains("Yeni malzemeler: ketçap ve mayonez"));
        s.day = 4;
        assertTrue(Texts.briefingLines(s).toString(), Texts.briefingLines(s).toString().contains("Yeni boy: duble"));
        s.day = 5;
        assertTrue(Texts.briefingLines(s).toString(), Texts.briefingLines(s).toString().contains("Yeni malzeme: acı sos"));
        String all = Texts.briefingLines(s).toString();
        assertTrue(all, all.contains("TalkBack Eylemleri"));
    }

    @Test public void tutorialAndHelpTeachSizeOneByOneToppingsAndTalkBackActions() {
        assertTrue(Texts.TUT_SIZE.contains("boyunu sen seçersin"));
        assertTrue(Texts.TUT_TOPPING.contains("tek tek"));
        assertTrue(Texts.TUT_TOPPING.contains("Malzemeleri bitir"));
        assertFalse("v0.9: öğretici kısa, Eylemler yardımda", Texts.TUT_SELECTED.contains("Eylemler"));
        assertTrue(Texts.quickHelp().contains("Eylemler menüsünden"));
        boolean actions = false, size = false, adding = false;
        for (String[] sec : Texts.helpSections()) {
            if (sec[0].contains("TalkBack Eylemleri") && sec[1].contains("Eylemler menüsünü")) actions = true;
            if (sec[0].equals("Döner boyu") && sec[1].contains("duble") && sec[1].contains("hatalı")) size = true;
            if (sec[0].equals("Malzeme ekleme") && sec[1].contains("bol") && sec[1].contains("Son ekleneni geri al")) adding = true;
        }
        assertTrue(actions); assertTrue(size); assertTrue(adding);
    }

    @Test public void lineTextsShowSizePriceAndAddedToppings() {
        SaveData s = SaveData.newGame();
        GameEngine e = new GameEngine(5, s);
        e.setSpawning(false);
        e.startDay();
        Order o = e.spawnOrder(java.util.Arrays.asList("ekmek_arasi"), java.util.Arrays.asList("soğansız"),
                java.util.Arrays.asList(com.doner.oyunu.engine.PortionSize.HALF));
        OrderLine l = o.getLine(0);
        String d = Texts.lineDescription(e, o, l, false);
        assertTrue(d, d.startsWith("Sipariş " + o.getPosition() + ", kalem 1, Ekmek arası döner (yarım ekmek)"));
        assertEquals("", Texts.addedLine(l));
        assertEquals("Henüz malzeme eklenmedi.", Texts.addedStatus(l));
        // adımları bitir, tek tek ekle
        e.startStep(o.id, 0, Station.EKMEK.id);
        for (int i = 0; i < 60; i++) e.tick(0.1f);
        e.applyStationOption(o.id, 0, Station.DONER.id, "size:yarim");
        for (int i = 0; i < 80; i++) e.tick(0.1f);
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, 0, Station.SOS.id, "add:domates"));
        for (int i = 0; i < 10; i++) e.tick(0.1f);
        assertEquals(StartResult.STARTED, e.applyStationOption(o.id, 0, Station.SOS.id, "add:domates"));
        for (int i = 0; i < 10; i++) e.tick(0.1f);
        assertEquals("Eklenen: domates bol", Texts.addedLine(l));
        assertEquals("Eklenenler: domates bol.", Texts.addedStatus(l));
        assertTrue(Texts.lineSteps(e, o, l).contains("Eklenen: domates bol"));
        assertTrue(Texts.lineDescription(e, o, l, false), Texts.lineDescription(e, o, l, false).contains("eklenenler: domates bol"));
        assertTrue(Texts.menuInfo(o).contains("Müşteri isteği: soğansız"));
        assertEquals("Hazırlık seçenekleri.", Texts.menuInfo(null));
    }

    @Test public void readyDonerIsShownInHalfPortions() {
        SaveData s = SaveData.newGame();
        GameEngine e = new GameEngine(5, s);
        e.beginPrep();
        assertEquals(PrepResult.STARTED, e.startPrepDoner("et_doner", 2));
        for (int i = 0; i < 60; i++) e.tick(0.1f);
        assertTrue(Texts.stockCellDescription(e, com.doner.oyunu.engine.PrepKind.DONER).contains("Hazır döner: 2 porsiyon"));
        assertTrue(Texts.prepButtonLabel(e, com.doner.oyunu.engine.PrepKind.DONER, Catalog.ingredient("et_doner")).contains("hazır 2 /"));
    }
}
