package com.doner.oyunu.ui;

import static org.junit.Assert.*;

import android.content.SharedPreferences;
import android.view.View;

import com.doner.oyunu.MainActivity;
import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.SaveData;
import com.doner.oyunu.engine.Station;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Gerçek Activity + gerçek View ağacı ile uçtan uca ekran akışı. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class UiFlowTest {

    /** Oyun ekranında "makul oyuncu": seç, doğru istasyona dokun, hazırsa teslim et. */
    static void botStep(UiHarness h) {
        GameEngine e = h.engine();
        if (e.getState() != GameEngine.State.RUNNING) return;
        for (Order o : e.getOrders()) {
            if (o.isReady()) { h.click("Teslim et"); return; }
        }
        Order sel = e.getSelectedOrder();
        if (sel == null) {
            for (Order o : e.getOrders()) if (!o.isRunning()) { View c = h.orderCard(o); if (c != null) { c.performClick(); return; } }
            return;
        }
        if (!sel.isRunning() && sel.nextStep() != null && e.getFreeSlots() > 0) {
            Station st = sel.nextStep().station;
            View t = h.stationTile(st);
            assertNotNull("istasyon düğmesi yok " + st, t);
            t.performClick();
        } else if (sel.isRunning() || sel.nextStep() == null) {
            // başka bekleyen sipariş varsa ona geç
            for (Order o : e.getOrders()) if (!o.isRunning() && !o.isReady() && o.id != sel.id && e.getFreeSlots() > 0) { View c = h.orderCard(o); if (c != null) c.performClick(); return; }
        }
    }

    static void playDay(UiHarness h, long maxMs) {
        long t = 0;
        while (h.screen() instanceof GameScreen && t < maxMs) {
            botStep(h);
            h.run(100);
            t += 100;
        }
    }

    @Test public void menuNewGameBriefingShopDay() {
        UiHarness h = new UiHarness();
        assertTrue(h.screen() instanceof MenuScreen);
        assertNull("Kayıt yokken Devam et olmamalı", h.findLabel("Devam et"));
        h.click("Yeni oyun");
        h.settle();
        assertTrue(h.screen() instanceof BriefingScreen);
        assertNotNull(h.findText("Yeni ürün: Ekmek arası döner"));
        h.click("Markete git");
        h.settle();
        assertTrue(h.screen() instanceof ShopScreen);
        h.click("Güne başla");
        h.settle();
        assertTrue(h.screen() instanceof GameScreen);
        assertNotNull(h.findLabel("Teslim et"));
        assertNotNull(h.findLabel("Duraklat"));
        assertNotNull(h.findLabel("Durumu oku"));
        assertNotNull(h.findLabel("Sonraki sipariş"));
        for (Station s : Station.values()) assertNotNull("istasyon " + s, h.stationTile(s));
    }

    @Test public void fullDayThroughUiReachesSummary() {
        UiHarness h = new UiHarness();
        h.click("Yeni oyun"); h.settle();
        h.click("Güne başla"); h.settle();
        assertTrue(h.screen() instanceof GameScreen);
        playDay(h, 400_000);
        // beginFinish en az 1.8 sn sonra Özet ekranına geçer
        h.run(20_000);
        assertTrue("Gün sonu özetine ulaşılmalı, ekran=" + h.screen().title(), h.screen() instanceof SummaryScreen);
        assertNotNull(h.findText("Teslim edilen sipariş"));
        assertNotNull(h.findText("Net kazanç"));
        assertNotNull(h.findLabel("Markete git"));
        h.click("Markete git"); h.settle();
        assertTrue(h.screen() instanceof ShopScreen);
        h.click("Geri"); h.settle();
        assertTrue(h.screen() instanceof BriefingScreen);
        assertEquals(2, h.act.save.day);
        assertNotNull(h.findText("Yeni ürün: İçecek"));
    }

    @Test public void tutorialRunsOnDayOneAndFinishes() {
        UiHarness h = new UiHarness();
        h.click("Yeni oyun"); h.settle();
        h.click("Güne başla"); h.settle();
        GameScreen gs = (GameScreen) h.screen();
        assertFalse(h.act.save.tutorialDone);
        // ilk teslime kadar oyna
        long t = 0;
        while (t < 120_000 && h.engine().getSummary().delivered == 0) { botStep(h); h.run(100); t += 100; }
        assertTrue("1. günde ilk teslim yapılabilmeli", h.engine().getSummary().delivered > 0);
        assertEquals("Öğretici aşaması 5'e ulaşmalı", 5, h.field(gs, "tutorialStage"));
        assertEquals(Boolean.FALSE, h.field(gs, "tutorialActive"));
    }

    @Test public void wrongStationCostsStarsAndDoesNotCrash() {
        UiHarness h = new UiHarness();
        h.click("Yeni oyun"); h.settle();
        h.click("Güne başla"); h.settle();
        GameEngine e = h.engine();
        long t = 0;
        while (e.getOrders().isEmpty() && t < 20000) { h.run(100); t += 100; }
        assertFalse(e.getOrders().isEmpty());
        Order o = e.getOrders().get(0);
        h.orderCard(o).performClick();
        h.run(100);
        Station need = o.nextStep().station;
        Station wrong = need == Station.TATLI ? Station.SOS : Station.TATLI;
        double before = e.getStars();
        h.stationTile(wrong).performClick();
        h.run(100);
        assertEquals(1, e.getSummary().wrongTaps);
        assertEquals(before - 0.02, e.getStars(), 1e-6);
        assertFalse(o.isRunning());
        h.stationTile(need).performClick();
        h.run(50);
        assertTrue(o.isRunning());
    }

    @Test public void tapWithoutOrderOrBeforeStartGivesFeedbackNoCrash() {
        UiHarness h = new UiHarness();
        h.click("Yeni oyun"); h.settle();
        h.click("Güne başla"); h.settle();
        // startDelay içinde (1.6 sn) ve siparişsizken her şeye dokun
        for (View v : UiHarness.visibleViews(h.root())) if (v.isClickable() && !UiHarness.label(v).contains("Duraklat")) v.performClick();
        h.run(300);
        assertTrue(h.screen() instanceof GameScreen);
        assertEquals(0, h.engine().getSummary().wrongTaps);
    }

    @Test public void pauseResumeStopsClock() {
        UiHarness h = new UiHarness();
        h.click("Yeni oyun"); h.settle();
        h.click("Güne başla"); h.settle();
        h.run(4000);
        GameEngine e = h.engine();
        float t0 = e.getElapsed();
        assertTrue(t0 > 0);
        h.click("Duraklat");
        h.run(100);
        assertNotNull("Duraklatma paneli", h.findLabel("Devam et"));
        float t1 = e.getElapsed();
        h.run(10_000);
        assertEquals("Duraklatılmışken zaman akmamalı", t1, e.getElapsed(), 0.01f);
        // panel açıkken arka plan düğmeleri erişilebilirlik ağacında olmamalı
        assertNull(h.findLabel("Teslim et"));
        h.click("Devam et");
        h.run(3000);
        assertTrue(e.getElapsed() > t1 + 2f);
        assertNull(h.findLabel("Devam et"));
        assertNotNull(h.findLabel("Teslim et"));
    }

    @Test public void backgroundingAutoPausesAndBackKeyTogglesPause() {
        UiHarness h = new UiHarness();
        h.click("Yeni oyun"); h.settle();
        h.click("Güne başla"); h.settle();
        h.run(3000);
        GameEngine e = h.engine();
        h.controller.pause();
        h.idle(100);
        float t = e.getElapsed();
        h.idle(5000);
        assertEquals(t, e.getElapsed(), 0.01f);
        h.controller.resume();
        h.idle(200);
        assertNotNull("Öne gelince duraklatma paneli görünmeli", h.findLabel("Devam et"));
        assertEquals("Oyun kendiliğinden devam etmemeli", t, e.getElapsed(), 0.3f);
        // geri tuşu: duraklatıldıysa devam
        h.act.onBackPressed();
        h.run(500);
        assertNull(h.findLabel("Devam et"));
        // geri tuşu: oyun sürerken duraklat
        h.act.onBackPressed();
        h.run(100);
        assertNotNull(h.findLabel("Devam et"));
        // çıkış iki dokunuş ister
        h.click("Günü bırak");
        assertTrue(h.screen() instanceof GameScreen);
        h.click("Emin misin");
        h.settle();
        assertTrue(h.screen() instanceof MenuScreen);
        assertEquals("Günü bırakmak ilerlemeyi bozmamalı", 1, h.act.save.day);
    }

    @Test public void rotationAndStopStartDoNotCrash() {
        UiHarness h = new UiHarness();
        h.click("Yeni oyun"); h.settle();
        h.click("Güne başla"); h.settle();
        h.run(2500);
        h.controller.configurationChange();
        h.idle(300);
        h.controller.pause().stop();
        h.idle(300);
        h.controller.start().resume();
        h.idle(500);
        assertTrue(h.screen() instanceof GameScreen);
        h.controller.destroy();
        h.idle(1000); // destroy sonrası bekleyen Handler/Choreographer işleri çökmemeli
    }

    @Test public void saveAndContinueAcrossRecreate() {
        UiHarness h = new UiHarness();
        h.click("Yeni oyun"); h.settle();
        h.click("Güne başla"); h.settle();
        playDay(h, 400_000);
        h.run(20_000);
        assertTrue(h.screen() instanceof SummaryScreen);
        int money = h.act.save.money;
        double stars = h.act.save.stars;
        h.controller.pause().stop().destroy();
        // süreç ölümü benzetimi: yeni Activity, aynı SharedPreferences
        UiHarness h2 = new UiHarness();
        assertTrue(h2.screen() instanceof MenuScreen);
        assertNotNull("Kayıt varken Devam et görünmeli", h2.findLabel("Devam et"));
        assertEquals(2, h2.act.save.day);
        assertEquals(money, h2.act.save.money);
        assertEquals(stars, h2.act.save.stars, 1e-9);
        h2.click("Devam et"); h2.settle();
        assertTrue(h2.screen() instanceof BriefingScreen);
        // yeni oyun onayı
        h2.act.show(new MenuScreen(h2.act)); h2.settle();
        h2.click("Yeni oyun");
        assertTrue("Onay istemeli", h2.screen() instanceof MenuScreen);
        h2.click("Emin misin"); h2.settle();
        assertTrue(h2.screen() instanceof BriefingScreen);
        assertEquals(1, h2.act.save.day);
    }

    @Test public void corruptSaveDoesNotCrash() {
        android.content.Context c = org.robolectric.RuntimeEnvironment.getApplication();
        SharedPreferences p = c.getSharedPreferences("doner_oyunu", android.content.Context.MODE_PRIVATE);
        p.edit().putString("save", "day=abc\nmoney=-5\n\u0000garbage\nstars=NaN\nstock.et=999999999999\n=\n").putBoolean("game_started", true).commit();
        UiHarness h = new UiHarness();
        assertTrue(h.screen() instanceof MenuScreen);
        h.click("Devam et"); h.settle();
        assertTrue(h.screen() instanceof BriefingScreen);
        h.click("Güne başla"); h.settle();
        h.run(5000);
        assertTrue(h.screen() instanceof GameScreen);
    }
}
