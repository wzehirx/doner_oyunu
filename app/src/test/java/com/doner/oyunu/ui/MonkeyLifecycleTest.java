package com.doner.oyunu.ui;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import com.doner.oyunu.engine.FlowMode;
import com.doner.oyunu.engine.GameEngine;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class MonkeyLifecycleTest {

    /** Rastgele dokunma/geri/arka plan/döndürme: hiçbir durumda istisna çıkmamalı, hep bir ekran görünmeli. */
    private void monkey(long seed, boolean talkBack, boolean animations) {
        if (talkBack) {
            AccessibilityManager am = (AccessibilityManager) RuntimeEnvironment.getApplication().getSystemService(Context.ACCESSIBILITY_SERVICE);
            shadowOf(am).setEnabled(true);
            shadowOf(am).setTouchExplorationEnabled(true);
        }
        UiHarness h = new UiHarness(animations, FlowMode.GERCEKCI);
        Random rnd = new Random(seed);
        boolean paused = false;
        int gameFrames = 0;
        for (int i = 0; i < 1500; i++) {
            int dice = rnd.nextInt(100);
            if (paused) { h.controller.resume(); paused = false; h.idle(50); continue; }
            if (dice < 3) { h.act.onBackPressed(); }
            else if (dice < 5) { h.controller.pause(); paused = true; h.idle(rnd.nextInt(3000)); continue; }
            else if (dice < 6) { h.controller.configurationChange(); }
            else {
                List<View> cl = new ArrayList<>();
                for (View v : UiHarness.visibleViews(h.root())) if (v.isClickable() && v.isEnabled()) cl.add(v);
                // oyun ekranında teslim/seçim/istasyon dokunuşlarının çoğu anlamlı olsun
                if (!cl.isEmpty()) cl.get(rnd.nextInt(cl.size())).performClick();
            }
            if (h.screen() instanceof GameScreen) gameFrames++;
            h.run(rnd.nextInt(400));
            if (h.screen() == null) fail("ekran yok, adım " + i);
            // ekranlar arası geçişte oyun bittiyse özet ekranının kendiliğinden geldiğini bekle
        }
        if (paused) h.controller.resume();
        h.controller.pause().stop().destroy();
        h.idle(2000);
        System.out.println("MONKEY seed=" + seed + " talkback=" + talkBack + " oyun-adımı=" + gameFrames);
    }

    /** Hazırlık + oyun ekranında (çırak, istekli siparişler, paneller) rastgele dokunma/yaşam döngüsü. */
    private void gameMonkey(long seed, boolean talkBack, int day, int apprentice) {
        if (talkBack) {
            AccessibilityManager am = (AccessibilityManager) RuntimeEnvironment.getApplication().getSystemService(Context.ACCESSIBILITY_SERVICE);
            shadowOf(am).setEnabled(true);
            shadowOf(am).setTouchExplorationEnabled(true);
        }
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        h.click("Yeni oyun"); h.settle();
        h.act.save.day = day;
        h.act.save.money = 60_000;
        if (apprentice > 0) h.act.save.upgradeLevels.put("cirak", apprentice);
        com.doner.oyunu.engine.Shop.buyRecommended(h.act.save);
        h.click("Güne başla"); h.settle();
        assertTrue(h.screen() instanceof GameScreen);
        Random rnd = new Random(seed);
        boolean paused = false;
        int panels = 0;
        for (int i = 0; i < 1200; i++) {
            if (paused) { h.controller.resume(); paused = false; h.idle(50); continue; }
            int dice = rnd.nextInt(100);
            if (dice < 2) h.act.onBackPressed();
            else if (dice < 4) { h.controller.pause(); paused = true; h.idle(rnd.nextInt(2000)); continue; }
            else if (dice < 5) h.controller.configurationChange();
            else if (dice < 25) UiBot.step(h);           // anlamlı oyuncu hamlesi (panel, hazırlık dahil)
            else {
                List<View> cl = new ArrayList<>();
                for (View v : UiHarness.visibleViews(h.root())) if (v.isClickable() && v.isEnabled()) cl.add(v);
                // "Günü bırak" menüye atar: oyunda kalmak için seyrek seç
                if (!cl.isEmpty()) {
                    View v = cl.get(rnd.nextInt(cl.size()));
                    if (!UiHarness.label(v).contains("Günü bırak") && !UiHarness.label(v).contains("Emin misin")) v.performClick();
                }
            }
            if (h.screen() instanceof GameScreen && UiBot.panelOpen(h)) panels++;
            h.run(rnd.nextInt(300));
            if (h.screen() == null) fail("ekran yok, adım " + i);
            if (!(h.screen() instanceof GameScreen)) {
                // gün bitti (özet) ya da menü: oyuna geri dön
                if (h.screen() instanceof SummaryScreen) { h.click("Markete git"); h.settle(); h.click("Güne başla"); h.settle(); }
                else if (h.screen() instanceof BriefingScreen || h.screen() instanceof ShopScreen) { h.click("Güne başla"); h.settle(); }
                else { h.click("Yeni oyun"); h.settle(); h.click("Güne başla"); h.settle(); }
            }
        }
        if (paused) h.controller.resume();
        h.controller.pause().stop().destroy();
        h.idle(2000);
        System.out.println("GAMEMONKEY seed=" + seed + " day=" + day + " cirak=" + apprentice + " panel-adımı=" + panels);
    }

    @Test public void gameMonkeyDay1() { gameMonkey(11, false, 1, 0); }
    @Test public void gameMonkeyDay6Apprentice3() { gameMonkey(12, false, 6, 3); }
    @Test public void gameMonkeyTalkBackDay3Apprentice1() { gameMonkey(13, true, 3, 1); }

    private static boolean sosPanelOpen(UiHarness h) {
        return UiBot.panelOpen(h) && UiBot.panelOf(h).stationId == com.doner.oyunu.engine.Station.SOS.id;
    }

    /** Seçenek paneli açıkken arka plan, döndürme, geri tuşu, yok edilme. */
    @Test public void menuPanelSurvivesLifecycleAndBack() {
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        h.click("Yeni oyun"); h.settle();
        h.startDay();
        GameEngine e = h.engine();
        e.setSpawning(false);
        com.doner.oyunu.engine.Order o = e.spawnOrder(java.util.Arrays.asList("ekmek_arasi"), new ArrayList<String>());
        h.run(300);
        h.orderCard(o).performClick(); h.run(100);
        long t = 0;
        while (!sosPanelOpen(h) && t < 60_000) { UiBot.step(h); h.run(100); t += 100; }
        assertTrue(sosPanelOpen(h));
        // panel açıkken ekleme sürer, saat akar
        float el = e.getElapsed();
        UiBot.row(h, "Domates ekle").performClick(); h.run(1000);
        assertTrue("panel açıkken saat akar", e.getElapsed() > el);
        assertEquals(1, o.getLine(0).addedCount("domates"));
        // arka plan: duraklatma paneli seçenek panelinin üstüne gelir, alttaki gizlenir
        h.controller.pause(); h.idle(300); h.controller.resume(); h.idle(300);
        assertNotNull(h.findLabel("Devam et"));
        assertNull("duraklatmada alttaki panel gizli olmalı", h.findLabel("Malzemeleri bitir"));
        float paused = e.getElapsed();
        h.run(3000);
        assertEquals("duraklatmada saat durur", paused, e.getElapsed(), 1e-4f);
        h.click("Devam et"); h.run(300);
        assertTrue(sosPanelOpen(h));
        assertNotNull(h.findLabel("Malzemeleri bitir"));
        assertNotNull("eklenenler korunur", h.findText("Eklenenler: domates."));
        h.controller.configurationChange(); h.idle(300);
        assertTrue(sosPanelOpen(h));
        // geri tuşu paneli kapatır (eklenenler kalır), ikinci geri duraklatır
        h.act.onBackPressed(); h.run(100);
        assertFalse(UiBot.panelOpen(h));
        assertEquals(1, o.getLine(0).addedCount("domates"));
        assertFalse(o.isRunning());
        h.act.onBackPressed(); h.run(100);
        assertNotNull(h.findLabel("Devam et"));
        h.click("Devam et"); h.run(100);
        // yeniden aç ve panel açıkken yok et
        h.stationTile(com.doner.oyunu.engine.Station.SOS).performClick(); h.run(100);
        assertTrue(sosPanelOpen(h));
        h.controller.pause().stop().destroy();
        h.idle(2000);
    }

    /** Panel açıkken sipariş müşteriyle birlikte giderse panel kapanır, çökmez. */
    @Test public void menuPanelClosesWhenOrderDisappears() {
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        h.click("Yeni oyun"); h.settle();
        h.startDay();
        GameEngine e = h.engine();
        e.setSpawning(false);
        com.doner.oyunu.engine.Order o = e.spawnOrder(java.util.Arrays.asList("ekmek_arasi"), new ArrayList<String>());
        h.run(300);
        h.orderCard(o).performClick(); h.run(100);
        long t = 0;
        while (!sosPanelOpen(h) && t < 60_000) { UiBot.step(h); h.run(100); t += 100; }
        assertTrue(sosPanelOpen(h));
        // müşteri sabrı biter
        t = 0;
        while (e.getOrder(o.id) != null && t < 400_000) { h.run(500); t += 500; }
        assertNull(e.getOrder(o.id));
        h.run(300);
        assertFalse("sipariş gidince panel kapanır", UiBot.panelOpen(h));
        assertTrue(h.screen() instanceof GameScreen || h.screen() instanceof SummaryScreen);
    }

    /** Rastgele TalkBack özel eylemleri (eski/yanlış kimlikler dahil) çökmeden çalışır. */
    @Test public void customActionMonkeyNoCrash() {
        AccessibilityManager am = (AccessibilityManager) RuntimeEnvironment.getApplication().getSystemService(Context.ACCESSIBILITY_SERVICE);
        shadowOf(am).setEnabled(true);
        shadowOf(am).setTouchExplorationEnabled(true);
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        h.click("Yeni oyun"); h.settle();
        h.act.save.day = 6;
        h.act.save.money = 60_000;
        com.doner.oyunu.engine.Shop.buyRecommended(h.act.save);
        h.click("Güne başla"); h.settle();
        Random rnd = new Random(77);
        for (int i = 0; i < 600; i++) {
            if (!(h.screen() instanceof GameScreen)) break;
            List<View> btns = new ArrayList<>();
            for (View v : UiHarness.visibleViews(h.root())) if (v.isClickable()) btns.add(v);
            if (btns.isEmpty()) { h.run(200); continue; }
            View v = btns.get(rnd.nextInt(btns.size()));
            int dice = rnd.nextInt(10);
            if (dice < 5) {
                java.util.List<String> labels = A11yAudit.customActionLabels(v);
                if (!labels.isEmpty()) {
                    v.createAccessibilityNodeInfo();
                    v.performAccessibilityAction(ActionMenu.ACTION_BASE + rnd.nextInt(labels.size() + 2), null);
                }
            } else if (dice < 8) {
                UiBot.step(h);
            } else {
                v.performClick();
            }
            if (h.screen() instanceof GameScreen && h.findLabel("Dükkânı aç") != null && rnd.nextBoolean()) h.click("Dükkânı aç");
            h.run(rnd.nextInt(300));
        }
        h.controller.pause().stop().destroy();
        h.idle(1000);
    }

    @Test public void monkeySeed1() { monkey(1, false, false); }
    @Test public void monkeySeed2() { monkey(2, false, true); }
    @Test public void monkeyTalkBackSeed3() { monkey(3, true, false); }
    @Test public void monkeyTalkBackSeed4() { monkey(4, true, true); }

    @Test public void talkBackOnFullDayFlowNoCrash() {
        AccessibilityManager am = (AccessibilityManager) RuntimeEnvironment.getApplication().getSystemService(Context.ACCESSIBILITY_SERVICE);
        shadowOf(am).setEnabled(true);
        shadowOf(am).setTouchExplorationEnabled(true);
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        h.click("Yeni oyun"); h.settle();
        h.startDay();
        UiFlowTest.playDay(h, 400_000);
        h.run(20_000);
        assertTrue(h.screen() instanceof SummaryScreen);
        assertNotNull(h.findText("Net kazanç"));
    }

    /** Sistem karanlık/aydınlık modu (uiMode) veya yazı boyutu değişince etkinlik yeniden yaratılmamalı; gün kaybolmamalı. */
    @Test public void uiModeChangeMidDayKeepsGame() {
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        h.click("Yeni oyun"); h.settle();
        h.startDay();
        h.run(5000);
        Configuration c = new Configuration(h.act.getResources().getConfiguration());
        c.uiMode = (c.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        h.controller.configurationChange(c);
        h.idle(500);
        assertTrue("uiMode değişiminde oyun ekranı korunmalı", h.screen() instanceof GameScreen);
    }

    @Test public void fontScaleAndUiModeChangeMidDayKeepsSameActivityAndGame() {
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        h.click("Yeni oyun"); h.settle();
        h.startDay();
        h.run(5000);
        com.doner.oyunu.MainActivity before = h.act;
        float t = h.engine().getElapsed();
        Configuration c = new Configuration(h.act.getResources().getConfiguration());
        c.uiMode = (c.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        c.fontScale = 1.5f;
        h.controller.configurationChange(c);
        h.idle(500);
        assertSame("Etkinlik yeniden yaratılmamalı", before, h.controller.get());
        assertTrue(h.controller.get().currentScreen() instanceof GameScreen);
        assertTrue(h.engine().getElapsed() >= t);
    }
}
