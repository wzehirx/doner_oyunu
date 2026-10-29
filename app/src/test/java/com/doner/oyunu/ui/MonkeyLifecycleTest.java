package com.doner.oyunu.ui;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

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
        UiHarness h = new UiHarness(animations);
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

    @Test public void monkeySeed1() { monkey(1, false, false); }
    @Test public void monkeySeed2() { monkey(2, false, true); }
    @Test public void monkeyTalkBackSeed3() { monkey(3, true, false); }
    @Test public void monkeyTalkBackSeed4() { monkey(4, true, true); }

    @Test public void talkBackOnFullDayFlowNoCrash() {
        AccessibilityManager am = (AccessibilityManager) RuntimeEnvironment.getApplication().getSystemService(Context.ACCESSIBILITY_SERVICE);
        shadowOf(am).setEnabled(true);
        shadowOf(am).setTouchExplorationEnabled(true);
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.click("Güne başla"); h.settle();
        UiFlowTest.playDay(h, 400_000);
        h.run(20_000);
        assertTrue(h.screen() instanceof SummaryScreen);
        assertNotNull(h.findText("Net kazanç"));
    }

    /** Sistem karanlık/aydınlık modu (uiMode) veya yazı boyutu değişince etkinlik yeniden yaratılmamalı; gün kaybolmamalı. */
    @Test public void uiModeChangeMidDayKeepsGame() {
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.click("Güne başla"); h.settle();
        h.run(5000);
        Configuration c = new Configuration(h.act.getResources().getConfiguration());
        c.uiMode = (c.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        h.controller.configurationChange(c);
        h.idle(500);
        assertTrue("uiMode değişiminde oyun ekranı korunmalı", h.screen() instanceof GameScreen);
    }

    @Test public void fontScaleAndUiModeChangeMidDayKeepsSameActivityAndGame() {
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.click("Güne başla"); h.settle();
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
