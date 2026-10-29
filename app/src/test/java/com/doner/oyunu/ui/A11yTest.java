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

    /** Hazırlık aşaması ekranı + tepsi paneli + gün içindeki hazırlık bölümü. */
    @Test public void prepScreenAndTrayPanel() {
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.click("Güne başla"); h.settle();
        audit(h, "Hazırlık(başlangıç)");
        h.click("Döner dilimle"); h.run(300);
        audit(h, "Hazırlık(iş sürüyor)");
        h.run(8000);
        audit(h, "Hazırlık(hazır döner var)");
        h.click("Salata ve sos tepsisi hazırla"); h.run(100);
        auditPanel(h, "Tepsi paneli");
        UiBot.row(h, "Domates").performClick(); h.run(50);
        audit(h, "Tepsi paneli(seçili)");
        h.click("Vazgeç"); h.run(100);
        audit(h, "Hazırlık(panel kapandı)");
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
        while (!UiBot.panelOpen(h) && t < 60_000) {
            if (o.getLine(0).isStepOpen(2)) { h.stationTile(com.doner.oyunu.engine.Station.SOS).performClick(); }
            else UiBot.step(h);
            h.run(100); t += 100;
        }
        assertTrue("panel açılmalı", UiBot.panelOpen(h));
        auditPanel(h, "Malzeme paneli");
        h.click("Hedefi oku"); h.run(50);
        auditPanel(h, "Malzeme paneli(hedef okundu)");
        UiBot.row(h, "Domates").performClick(); h.run(50);
        auditPanel(h, "Malzeme paneli(stokta yok, acil al)");
        assertNotNull(h.findLabel("Acil al"));
    }

    /** Panel denetimi: panelin kendi başlığı ilk odak ve başlık olmalı; tüm hedefler >=56dp. */
    private void auditPanel(UiHarness h, String where) {
        audit(h, where);
        SelectionPanel p = (SelectionPanel) h.field(h.screen(), "panel");
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
        s.wastedPortions = 5; s.wastedCost = 190; s.mistakes = 1; s.perfect = 3; s.prepBatches = 4; s.apprenticeTasks = 7;
        h.act.show(new SummaryScreen(h.act, s)); h.settle();
        audit(h, "Özet(başarılı)");
        assertNotNull("israf satırı", h.findText("İsraf"));
        assertNotNull(h.findText("Hatalı malzemeli sipariş"));
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
        h.click("Salata ve sos tepsisi hazırla"); h.run(100);
        auditPanel(h, "Tepsi paneli(yazı 2x)");
        h.click("Vazgeç"); h.run(100);
        GameEngine e2 = h.engine();
        e2.setSpawning(false);
        Order o2 = e2.spawnOrder(java.util.Arrays.asList("ekmek_arasi"), new ArrayList<String>());
        h.run(300);
        audit(h, "Sipariş kartı(yazı 2x)");
        h.orderCard(o2).performClick(); h.run(100);
        long tt = 0;
        while (!UiBot.panelOpen(h) && tt < 60_000) { UiBot.step(h); h.run(100); tt += 100; }
        assertTrue(UiBot.panelOpen(h));
        auditPanel(h, "Malzeme paneli(yazı 2x)");
        h.click("Vazgeç"); h.run(100);
        h.act.show(new SettingsScreen(h.act)); h.settle();
        audit(h, "Ayarlar(yazı 2x)");
        h.act.show(new ShopScreen(h.act)); h.settle();
        audit(h, "Market(yazı 2x)");
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
