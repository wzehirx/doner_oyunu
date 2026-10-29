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

    /** Oyun ekranında "makul oyuncu" (bkz. UiBot). */
    static void botStep(UiHarness h) { UiBot.step(h); }

    static void playDay(UiHarness h, long maxMs) { UiBot.playDay(h, maxMs); }

    @Test public void menuNewGameBriefingShopDay() {
        UiHarness h = new UiHarness(false);
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
        // hazırlık aşaması: hazırlık kontrolleri var, sipariş ekranı yok, süre akmıyor
        assertEquals(GameEngine.State.PREP, h.engine().getState());
        assertNotNull(h.findLabel("Dükkânı aç"));
        assertNotNull(h.findLabel("Hazırlık yapmadan aç"));
        assertNotNull(h.findLabel("Döner dilimle"));
        assertNotNull(h.findLabel("Salata ve sos tepsisi hazırla"));
        assertNotNull(h.findLabel("Duraklat"));
        assertNotNull(h.findLabel("Durumu oku"));
        assertNull(h.findLabel("Teslim et"));
        assertNull(h.stationTile(Station.DONER));
        h.run(3000);
        assertEquals("Hazırlıkta saat akmamalı", 0f, h.engine().getElapsed(), 1e-6f);
        h.click("Dükkânı aç");
        h.run(200);
        assertEquals(GameEngine.State.RUNNING, h.engine().getState());
        assertNotNull(h.findLabel("Teslim et"));
        assertNotNull(h.findLabel("Duraklat"));
        assertNotNull(h.findLabel("Durumu oku"));
        assertNotNull(h.findLabel("Sonraki sipariş"));
        assertNull(h.findLabel("Dükkânı aç"));
        for (Station s : Station.values()) assertNotNull("istasyon " + s, h.stationTile(s));
    }

    @Test public void fullDayThroughUiReachesSummary() {
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.startDay();
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
        assertNotNull(h.findText("Yeni ürün: Ayran"));
    }

    /** 1. gün öğreticisi: hazırlık yap -> dükkânı aç -> sipariş seç -> istasyonlar -> malzeme paneli -> paketle -> teslim. */
    @Test public void tutorialRunsOnDayOneAndFinishes() {
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.click("Güne başla"); h.settle();
        GameScreen gs = (GameScreen) h.screen();
        GameEngine e = h.engine();
        assertFalse(h.act.save.tutorialDone);
        assertEquals("Hazırlık aşaması öğreticisi konuşulmalı", 1, h.field(gs, "tutorialStage"));
        // hazırlık yap: döner dilimle
        h.click("Döner dilimle");
        h.run(300);
        long t = 0;
        while (e.getReadyDoner() == 0 && t < 20_000) { h.run(100); t += 100; }
        assertTrue("hazır döner artmalı", e.getReadyDoner() > 0);
        assertEquals("Hazırlık bitti aşaması", 2, h.field(gs, "tutorialStage"));
        h.click("Dükkânı aç");
        h.run(200);
        assertEquals(3, h.field(gs, "tutorialStage"));
        // müşteri gelir, sipariş seçilir
        t = 0;
        while (e.getOrders().isEmpty() && t < 40_000) { h.run(100); t += 100; }
        assertFalse(e.getOrders().isEmpty());
        assertEquals(4, h.field(gs, "tutorialStage"));
        // öğreticide sipariş kendiliğinden seçilmez: oyuncu seçmeli
        h.run(3000);
        assertEquals("Öğreticide sipariş otomatik seçilmemeli", -1, e.getSelectedOrderId());
        // ilk teslime kadar oyna (malzeme paneli dahil)
        boolean panelSeen = false;
        t = 0;
        while (t < 150_000 && e.getSummary().delivered == 0) {
            if (UiBot.panelOpen(h)) panelSeen = true;
            botStep(h); h.run(100); t += 100;
        }
        assertTrue("1. günde ilk teslim yapılabilmeli", e.getSummary().delivered > 0);
        assertTrue("malzeme seçimi paneli kullanılmalı", panelSeen);
        assertEquals("Öğretici son aşamaya ulaşmalı", GameScreen.TUTORIAL_END, h.field(gs, "tutorialStage"));
        assertEquals(Boolean.FALSE, h.field(gs, "tutorialActive"));
        assertEquals("Doğru malzemeler: kusursuz sipariş", 1, e.getSummary().perfect);
    }

    @Test public void wrongStationCostsStarsAndDoesNotCrash() {
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.startDay();
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
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.startDay();
        // startDelay içinde (1.6 sn) ve siparişsizken her şeye dokun
        for (View v : UiHarness.visibleViews(h.root())) if (v.isClickable() && !UiHarness.label(v).contains("Duraklat")) v.performClick();
        h.run(300);
        assertTrue(h.screen() instanceof GameScreen);
        assertEquals(0, h.engine().getSummary().wrongTaps);
    }

    @Test public void pauseResumeStopsClock() {
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.startDay();
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
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.startDay();
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
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.startDay();
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
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.startDay();
        playDay(h, 400_000);
        h.run(20_000);
        assertTrue(h.screen() instanceof SummaryScreen);
        int money = h.act.save.money;
        double stars = h.act.save.stars;
        h.controller.pause().stop().destroy();
        // süreç ölümü benzetimi: yeni Activity, aynı SharedPreferences
        UiHarness h2 = new UiHarness(false);
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
        UiHarness h = new UiHarness(false);
        assertTrue(h.screen() instanceof MenuScreen);
        h.click("Devam et"); h.settle();
        assertTrue(h.screen() instanceof BriefingScreen);
        h.startDay();
        h.run(5000);
        assertTrue(h.screen() instanceof GameScreen);
    }
}
