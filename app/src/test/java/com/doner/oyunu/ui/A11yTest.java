package com.doner.oyunu.ui;

import static org.junit.Assert.*;

import android.view.View;

import com.doner.oyunu.engine.DaySummary;
import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.SaveData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Tüm ekranlarda erişilebilirlik denetimi. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class A11yTest {
    private final List<String> allWarnings = new ArrayList<>();

    private void audit(UiHarness h, String where) {
        h.idle(100);
        A11yAudit a = A11yAudit.run(h, where);
        allWarnings.addAll(a.warnings);
        if (!a.warnings.isEmpty()) System.out.println("A11Y-UYARI\n" + a.report());
        assertTrue(where + ": denetlenen etkileşimli görünüm yok", a.checkedInteractive > 0);
        assertTrue("Erişilebilirlik hataları:\n" + a.report(), a.errors.isEmpty());
    }

    private UiHarness gameHarness() {
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.startDay();
        return h;
    }

    @Test public void menuHelpSettings() {
        UiHarness h = new UiHarness(false);
        audit(h, "Menü(kayıtsız)");
        h.click("Nasıl oynanır"); h.settle();
        audit(h, "Yardım");
        h.click("Geri"); h.settle();
        h.click("Ayarlar"); h.settle();
        audit(h, "Ayarlar");
        h.click("Geri"); h.settle();
        h.click("Yeni oyun"); h.settle();
        audit(h, "Brifing gün 1");
        h.act.show(new MenuScreen(h.act)); h.settle();
        audit(h, "Menü(kayıtlı)");
        h.click("Yeni oyun");
        audit(h, "Menü(yeni oyun onayı)");
    }

    @Test public void briefingAndShopOnVariousDays() {
        for (int day : new int[]{1, 2, 3, 8, 10, 25}) {
            UiHarness h = new UiHarness(false);
            h.click("Yeni oyun"); h.settle();
            h.act.save.day = day;
            h.act.save.money = 200_000;
            h.act.startBriefing(); h.settle();
            audit(h, "Brifing gün " + day);
            h.click("Markete git"); h.settle();
            audit(h, "Market/malzeme gün " + day);
            h.click("Mutfak aletleri"); h.settle();
            audit(h, "Market/mutfak gün " + day);
            h.click("Restoran"); h.settle();
            audit(h, "Market/restoran gün " + day);
        }
    }

    @Test public void gameScreenStates() {
        UiHarness h = gameHarness();
        audit(h, "Oyun(başlangıç)");
        h.run(8000);
        GameEngine e = h.engine();
        assertFalse(e.getOrders().isEmpty());
        audit(h, "Oyun(sipariş var)");
        Order o = e.getOrders().get(0);
        h.orderCard(o).performClick();
        h.run(100);
        audit(h, "Oyun(seçili)");
        // siparişi bitir -> hazır
        long t = 0;
        while (!o.isReady() && t < 60_000 && e.getOrder(o.id) != null) { UiFlowTest.botStep(h); h.run(100); t += 100; }
        h.run(200);
        audit(h, "Oyun(hazır/akış)");
        h.click("Duraklat"); h.run(100);
        audit(h, "Oyun(duraklatıldı)");
        h.click("Nasıl oynanır"); h.run(100);
        audit(h, "Oyun(duraklatıldı+yardım)");
    }

    /** Hazırlık aşaması ekranı + hazırlık seçenek paneli + gün içindeki hazırlık bölümü. */
    @Test public void prepScreenAndPrepPanel() {
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.click("Güne başla"); h.settle();
        audit(h, "Hazırlık(başlangıç)");
        h.click("Döner dilimle"); h.run(100);
        auditPanel(h, "Hazırlık paneli(döner)");
        assertNotNull(h.findLabel("4 porsiyon"));
        assertNotNull(h.findLabel("2 porsiyon"));
        h.click("Kapat"); h.run(100);
        audit(h, "Hazırlık(panel kapandı)");
        h.click("Döner dilimle"); h.run(100);
        h.click("4 porsiyon"); h.run(300);
        audit(h, "Hazırlık(iş sürüyor)");
        h.run(8000);
        audit(h, "Hazırlık(hazır döner var)");
        h.click("Salata ve sos tepsisi hazırla"); h.run(300);
        audit(h, "Hazırlık(tepsi sürüyor)");
        h.click("Dükkânı aç"); h.run(300);
        audit(h, "Gün(hazırlık bölümü altta)");
    }

    /** Hazırlık aşamasında çırak çalışırken ve çırak duyuruları kapalıyken. */
    @Test public void prepScreenWithApprentice() {
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.act.save.upgradeLevels.put("cirak", 3);
        h.click("Güne başla"); h.settle();
        h.run(2500);
        assertNotNull("çırak işi görünmeli", h.findText("Çırak"));
        audit(h, "Hazırlık(çırak)");
    }

    /** Kalemli sipariş kartı, müşteri istekleri, malzeme paneli (stokta olmayan malzeme dahil). */
    @Test public void orderCardItemsAndToppingPanel() {
        UiHarness h = gameHarness();
        GameEngine e = h.engine();
        e.setSpawning(false);
        e.getSave().inventory.add("ayran", 10);
        Order o = e.spawnOrder(java.util.Arrays.asList("ekmek_arasi", "ayran"), java.util.Arrays.asList("soğansız"));
        assertNotNull(o);
        h.run(300);
        audit(h, "Sipariş kartı(kalemli, istekli)");
        assertNotNull(h.findText("İstek: soğansız"));
        h.orderCard(o).performClick(); h.run(100);
        audit(h, "Sipariş kartı(seçili)");
        View item2 = null;
        for (View v : UiHarness.visibleViews(h.root())) if (v.isClickable() && UiHarness.label(v).startsWith("Sipariş 1, kalem 2")) item2 = v;
        assertNotNull(item2);
        item2.performClick(); h.run(100);
        audit(h, "Sipariş kartı(kalem seçili)");
        // sos tezgahı: kilitli -> açıklama, panel açılmaz
        h.stationTile(com.doner.oyunu.engine.Station.SOS).performClick(); h.run(100);
        assertFalse(UiBot.panelOpen(h));
        audit(h, "Sipariş kartı(kilitli bildirimi)");
        // topping adımına kadar ilerle
        e.getSave().inventory.set("domates", 0);
        long t = 0;
        while (!sosPanelOpen(h) && t < 60_000) {
            if (o.getLine(0).isStepOpen(2) && !UiBot.panelOpen(h)) { h.stationTile(com.doner.oyunu.engine.Station.SOS).performClick(); }
            else UiBot.step(h);
            h.run(100); t += 100;
        }
        assertTrue("sos paneli açılmalı", sosPanelOpen(h));
        auditPanel(h, "Seçenek paneli(SOS)");
        h.click("Hedefi oku"); h.run(50);
        auditPanel(h, "Seçenek paneli(hedef okundu)");
        h.click("Domates ekle"); h.run(100);
        auditPanel(h, "Seçenek paneli(stokta yok, acil al)");
        assertNotNull(h.findLabel("Acil al"));
    }

    private static boolean sosPanelOpen(UiHarness h) {
        return UiBot.panelOpen(h) && UiBot.panelOf(h).stationId == com.doner.oyunu.engine.Station.SOS.id;
    }

    /** Panel denetimi: panelin kendi başlığı ilk odak ve başlık olmalı; tüm hedefler >=56dp. */
    private void auditPanel(UiHarness h, String where) {
        audit(h, where);
        MenuPanel p = UiBot.panelOf(h);
        assertNotNull(where, p);
        assertTrue(where, p.titleView.isAccessibilityHeading());
        View first = null;
        for (View v : UiHarness.visibleViews(h.root())) {
            if (v.isImportantForAccessibility() && (v.isFocusable() || v.isClickable()) && !(v instanceof android.widget.ScrollView)) { first = v; break; }
        }
        assertSame(where + ": panelde ilk odak başlık değil: '" + (first == null ? null : UiHarness.label(first)) + "'", p.titleView, first);
        for (View v : UiHarness.visibleViews(h.root())) {
            if (v.isClickable() && v.isImportantForAccessibility()) {
                float w = v.getWidth() / h.act.getResources().getDisplayMetrics().density;
                float hh = v.getHeight() / h.act.getResources().getDisplayMetrics().density;
                assertTrue(where + ": hedef <56dp: '" + UiHarness.label(v) + "' " + w + "x" + hh, hh >= 56f && w >= 56f);
            }
        }
    }

    @Test public void summaryScreens() {
        UiHarness h = gameHarness();
        DaySummary s = new DaySummary();
        s.day = 3; s.delivered = 5; s.lost = 1; s.revenue = 1200; s.tips = 80; s.rent = 360; s.moneyAfter = 5000;
        s.startStars = 3; s.endStars = 3.4; s.newProductsNext = "Tavuk dürüm"; s.wrongTaps = 2; s.emergencySpend = 30;
        s.wastedPortions = 5; s.wastedCost = 190; s.mistakes = 1; s.sizeMistakes = 1; s.newIngredientsNext = "ketçap ve mayonez"; s.perfect = 3; s.prepBatches = 4; s.apprenticeTasks = 7;
        h.act.show(new SummaryScreen(h.act, s)); h.settle();
        audit(h, "Özet(başarılı)");
        assertNotNull("israf satırı", h.findText("İsraf"));
        assertNotNull(h.findText("Hatalı malzemeli sipariş"));
        assertNotNull("boy hatası satırı", h.findText("Yanlış boyda dilimlenen sipariş"));
        assertNotNull("yarın yeni malzeme", h.findText("Yarın yeni malzeme: ketçap ve mayonez"));
        DaySummary f = new DaySummary();
        f.day = 3; f.failed = true; f.startStars = 3; f.endStars = 0.9; f.moneyAfter = 5000; f.rent = 360;
        h.act.show(new SummaryScreen(h.act, f)); h.settle();
        audit(h, "Özet(başarısız)");
    }

    @Test public void largeFontDoesNotCrashAndKeepsTargets() {
        org.robolectric.RuntimeEnvironment.setFontScale(2.0f);
        UiHarness h = gameHarness();
        h.run(8000);
        audit(h, "Oyun(yazı 2x)");
        h.click("Duraklat"); h.run(100);
        audit(h, "Oyun(duraklat, yazı 2x)");
        h.click("Devam et"); h.run(100);
        h.click("Döner dilimle"); h.run(100);
        auditPanel(h, "Hazırlık paneli(yazı 2x)");
        h.click("Kapat"); h.run(100);
        GameEngine e2 = h.engine();
        e2.setSpawning(false);
        Order o2 = e2.spawnOrder(java.util.Arrays.asList("ekmek_arasi"), new ArrayList<String>());
        h.run(300);
        audit(h, "Sipariş kartı(yazı 2x)");
        h.orderCard(o2).performClick(); h.run(100);
        long tt = 0;
        while (!sosPanelOpen(h) && tt < 60_000) { UiBot.step(h); h.run(100); tt += 100; }
        assertTrue(sosPanelOpen(h));
        auditPanel(h, "Sos seçenek paneli(yazı 2x)");
        h.click("Kapat"); h.run(100);
        h.act.show(new SettingsScreen(h.act)); h.settle();
        audit(h, "Ayarlar(yazı 2x)");
        h.act.show(new ShopScreen(h.act)); h.settle();
        audit(h, "Market(yazı 2x)");
    }

    // ------------------------------------------------------------------ TalkBack Eylemleri (özel eylemler)
    private static java.util.List<String> lst(String... x) { return new ArrayList<>(java.util.Arrays.asList(x)); }

    private static boolean anyStartsWith(java.util.List<String> labels, String prefix) {
        for (String l : labels) if (l.startsWith(prefix)) return true;
        return false;
    }

    private static boolean anyContains(java.util.List<String> labels, String part) {
        for (String l : labels) if (l.contains(part)) return true;
        return false;
    }

    /** Aynı durumu motordan alıp düğmedeki eylem etiketleriyle birebir karşılaştırır. */
    private void assertActionsMatchEngine(UiHarness h, com.doner.oyunu.engine.Station st, Order sel) {
        GameEngine e = h.engine();
        View tile = h.stationTile(st);
        assertNotNull(tile);
        java.util.List<String> labels = A11yAudit.customActionLabels(tile);
        java.util.List<com.doner.oyunu.engine.StationOption> ops = e.getStationOptions(sel == null ? -1 : sel.id, -1, st.id);
        assertEquals(st + " eylem sayısı", ops.size(), labels.size());
        for (int i = 0; i < ops.size(); i++) {
            assertEquals(st + " eylem " + i, ops.get(i).label(), labels.get(i));
            assertFalse(labels.get(i).trim().isEmpty());
        }
    }

    @Test public void stationAndPrepButtonsExposeTurkishCustomActions() {
        UiHarness h = gameHarness();
        GameEngine e = h.engine();
        e.setSpawning(false);
        e.getSave().inventory.add("ayran", 10);
        Order o = e.spawnOrder(lst("ekmek_arasi", "ayran"), lst("soğansız"));
        h.run(300);
        h.orderCard(o).performClick(); h.run(100);
        audit(h, "Oyun(eylemler)");
        A11yAudit a = A11yAudit.run(h, "eylemler");
        assertTrue("özel eylemler denetlenmeli", a.checkedActions > 0);
        // döner makinesi: boy seçenekleri (dilimleme henüz kilitli ama listede), hazırlık seçenekleri
        for (com.doner.oyunu.engine.Station st : com.doner.oyunu.engine.Station.values()) assertActionsMatchEngine(h, st, o);
        java.util.List<String> ekmek = A11yAudit.customActionLabels(h.stationTile(com.doner.oyunu.engine.Station.EKMEK));
        assertTrue(ekmek.toString(), anyContains(ekmek, "Ekmeği ısıt"));
        java.util.List<String> sos = A11yAudit.customActionLabels(h.stationTile(com.doner.oyunu.engine.Station.SOS));
        assertTrue(sos.toString(), anyContains(sos, "Domates ekle"));
        assertTrue("kilit nedeni etikette", anyContains(sos, "Önce kalemin diğer adımları bitmeli"));
        assertTrue(sos.toString(), anyContains(sos, "Malzemeleri bitir"));
        assertTrue(sos.toString(), anyContains(sos, "Hedefi oku"));
        java.util.List<String> pack = A11yAudit.customActionLabels(h.stationTile(com.doner.oyunu.engine.Station.PAKET));
        assertEquals(pack.toString(), 1, pack.size());
        assertTrue(pack.toString(), pack.get(0).startsWith("Siparişi paketle"));
        // hazırlık düğmesi
        View prep = h.findLabel("Döner dilimle");
        assertNotNull(prep);
        java.util.List<String> pl = A11yAudit.customActionLabels(prep);
        assertEquals(pl.toString(), 2, pl.size());
        assertTrue(pl.toString(), anyStartsWith(pl, "4 porsiyon"));
        assertTrue(pl.toString(), anyStartsWith(pl, "2 porsiyon"));
        View tray = h.findLabel("Salata ve sos tepsisi hazırla");
        assertTrue(A11yAudit.customActionLabels(tray).get(0).startsWith("Salata ve sos tepsisi hazırla"));
    }

    @Test public void customActionListFollowsStateChanges() {
        UiHarness h = gameHarness();
        GameEngine e = h.engine();
        e.setSpawning(false);
        Order o = e.spawnOrder(lst("ekmek_arasi"), lst());
        h.run(300);
        h.orderCard(o).performClick(); h.run(100);
        View sos = h.stationTile(com.doner.oyunu.engine.Station.SOS);
        assertTrue(anyContains(A11yAudit.customActionLabels(sos), "Önce kalemin diğer adımları bitmeli"));
        // ekmek ısıt + dilimle: topping adımı açılınca eylemler etkin olur
        UiBot.viaActions = true;
        try {
            long t = 0;
            while (!o.getLine(0).isStepOpen(o.getLine(0).product.toppingStepIndex()) && t < 60_000) { UiBot.step(h); h.run(100); t += 100; }
        } finally { UiBot.viaActions = false; }
        assertFalse(UiBot.panelOpen(h));
        java.util.List<String> labels = A11yAudit.customActionLabels(sos);
        assertFalse(labels.toString(), anyContains(labels, "Önce kalemin diğer adımları bitmeli"));
        assertTrue(labels.toString(), anyStartsWith(labels, "Domates ekle, stokta"));
        View tile = h.stationTile(com.doner.oyunu.engine.Station.SOS);
        int id = UiBot.actionIdByLabel(tile, "Domates ekle");
        assertTrue(id >= 0);
        assertTrue(tile.performAccessibilityAction(id, null));
        h.run(900);
        assertEquals(1, o.getLine(0).addedCount("domates"));
        java.util.List<String> after = A11yAudit.customActionLabels(tile);
        assertTrue("ikinci eklemede etiket 'bol' olur: " + after, anyStartsWith(after, "Domates bol ekle"));
        assertFalse(UiBot.panelOpen(h));
    }

    /** Eylemle (panelsiz) ve panelle aynı dizi aynı sonucu verir. */
    @Test public void actionPathAndPanelPathGiveTheSameResult() {
        UiHarness h = gameHarness();
        GameEngine e = h.engine();
        e.setSpawning(false);
        Order oa = e.spawnOrder(lst("ekmek_arasi"), lst("soğansız"));
        Order ob = e.spawnOrder(lst("ekmek_arasi"), lst("soğansız"));
        h.run(300);
        int toppingIdx = oa.getLine(0).product.toppingStepIndex();
        UiBot.viaActions = true;
        try {
            for (Order o : new Order[]{oa, ob}) {
                h.orderCard(o).performClick(); h.run(100);
                long t = 0;
                while (!o.getLine(0).isStepOpen(toppingIdx) && t < 60_000) { UiBot.step(h); h.run(100); t += 100; }
                assertTrue(o.getLine(0).isStepOpen(toppingIdx));
            }
        } finally { UiBot.viaActions = false; }
        int stock0 = e.getSave().inventory.get("domates");
        // A: eylemlerle
        h.orderCard(oa).performClick(); h.run(100);
        com.doner.oyunu.engine.Station sos = com.doner.oyunu.engine.Station.SOS;
        for (String lab : new String[]{"Domates ekle", "Marul ekle", "Domates bol ekle", "Son ekleneni geri al"}) {
            View tile = h.stationTile(sos);
            int id = UiBot.actionIdByLabel(tile, lab);
            assertTrue("eylem yok: " + lab, id >= 0);
            assertTrue(tile.performAccessibilityAction(id, null));
            h.run(900);
            assertFalse("eylem panel açmaz", UiBot.panelOpen(h));
        }
        java.util.Map<String, Integer> addedA = new java.util.LinkedHashMap<>(oa.getLine(0).getAddedCounts());
        int stockA = e.getSave().inventory.get("domates");
        View tileA = h.stationTile(sos);
        assertTrue(tileA.performAccessibilityAction(UiBot.actionIdByLabel(tileA, "Malzemeleri bitir"), null));
        h.run(200);
        // B: panelle (aynı dizi)
        h.orderCard(ob).performClick(); h.run(100);
        h.stationTile(sos).performClick(); h.run(100);
        assertTrue(UiBot.panelOpen(h));
        for (String lab : new String[]{"Domates ekle", "Marul ekle", "Domates bol ekle", "Son ekleneni geri al"}) {
            View v = UiBot.row(h, lab);
            assertNotNull("panel seçeneği yok: " + lab, v);
            v.performClick();
            h.run(900);
        }
        java.util.Map<String, Integer> addedB = new java.util.LinkedHashMap<>(ob.getLine(0).getAddedCounts());
        int stockB = e.getSave().inventory.get("domates");
        assertEquals("aynı eklenenler", addedA, addedB);
        assertEquals("eylem yolu: domates net 1 azalır", stock0 - 1, stockA);
        assertEquals("iki yol aynı miktarda tüketir", stockA - 1, stockB);
        h.click("Malzemeleri bitir"); h.run(200);
        assertFalse(UiBot.panelOpen(h));
        assertEquals(oa.getLine(0).getChosen(), ob.getLine(0).getChosen());
        assertTrue(oa.getLine(0).isStepDone(toppingIdx));
        assertTrue(ob.getLine(0).isStepDone(toppingIdx));
    }

    @Test public void noGestureOnlyInteractionsInSource() throws Exception {
        File dir = new File("src/main/java/com/doner/oyunu");
        List<String> bad = new ArrayList<>();
        String[] banned = {"OnTouchListener", "setOnLongClickListener", "OnDragListener", "GestureDetector", "ScaleGestureDetector", "onTouchEvent", "SeekBar", "setOnGenericMotion", "MotionEvent", "startDrag", "OnLongClick"};
        scan(dir, banned, bad);
        assertTrue("Dokunma-hareket kodu bulundu: " + bad, bad.isEmpty());
    }

    private void scan(File d, String[] banned, List<String> bad) throws Exception {
        File[] fs = d.listFiles();
        assertNotNull("kaynak dizini bulunamadı " + d.getAbsolutePath(), fs);
        for (File f : fs) {
            if (f.isDirectory()) { scan(f, banned, bad); continue; }
            if (!f.getName().endsWith(".java")) continue;
            String src = new String(Files.readAllBytes(f.toPath()), "UTF-8");
            for (String b : banned) if (src.contains(b)) bad.add(f.getName() + ":" + b);
        }
    }
}
