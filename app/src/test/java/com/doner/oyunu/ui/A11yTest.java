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
        h.click("Güne başla"); h.settle();
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

    @Test public void summaryScreens() {
        UiHarness h = gameHarness();
        DaySummary s = new DaySummary();
        s.day = 3; s.delivered = 5; s.lost = 1; s.revenue = 1200; s.tips = 80; s.rent = 360; s.moneyAfter = 5000;
        s.startStars = 3; s.endStars = 3.4; s.newProductsNext = "Tavuk dürüm"; s.wrongTaps = 2; s.emergencySpend = 30;
        h.act.show(new SummaryScreen(h.act, s)); h.settle();
        audit(h, "Özet(başarılı)");
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
