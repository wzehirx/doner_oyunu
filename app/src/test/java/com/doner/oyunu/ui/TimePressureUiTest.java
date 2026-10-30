package com.doner.oyunu.ui;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import com.doner.oyunu.engine.DaySummary;
import com.doner.oyunu.engine.FlowMode;
import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Narrator;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.SaveData;
import com.doner.oyunu.engine.Station;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAccessibilityManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * v0.8 arayüz: Süre baskısı ayarı (Ayarlar, Duraklat menüsü, brifing), süresiz gün görünümü (kalan müşteri, sabır yok),
 * gün içinde hazırlık bölümü yok / istasyondan hazırlık, erişilebilirlik denetimi.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class TimePressureUiTest {

    private static A11yAudit audit(UiHarness h, String where) {
        h.idle(100);
        A11yAudit a = A11yAudit.run(h, where);
        if (!a.warnings.isEmpty()) System.out.println("A11Y-UYARI\n" + a.report());
        assertTrue(where + ": denetlenen etkileşimli görünüm yok", a.checkedInteractive > 0);
        assertTrue("Erişilebilirlik hataları:\n" + a.report(), a.errors.isEmpty());
        return a;
    }

    private static ShadowAccessibilityManager talkBackOn() {
        AccessibilityManager am = (AccessibilityManager) RuntimeEnvironment.getApplication().getSystemService(Context.ACCESSIBILITY_SERVICE);
        ShadowAccessibilityManager sam = shadowOf(am);
        sam.setEnabled(true);
        sam.setTouchExplorationEnabled(true);
        return sam;
    }

    private static boolean announced(ShadowAccessibilityManager sam, int from, String part) {
        List<AccessibilityEvent> all = new ArrayList<>(sam.getSentAccessibilityEvents());
        for (AccessibilityEvent ev : all.subList(Math.min(from, all.size()), all.size())) {
            if (ev.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT && !ev.getText().isEmpty() && ev.getText().get(0).toString().contains(part)) return true;
        }
        return false;
    }

    /** Süresiz (timed=false) ya da süreli gün; öğretici kapalı, senaryo siparişleri için müşteri gelişi kapalı. */
    private static UiHarness day(boolean timed, int dayNo, boolean spawning) {
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        h.act.save.tutorialDone = true;
        h.click("Yeni oyun"); h.settle();
        h.act.save.day = dayNo;
        h.act.save.money = 30_000;
        h.act.save.timePressure = timed;
        com.doner.oyunu.engine.Shop.buyRecommended(h.act.save);
        h.startDay();
        h.engine().setSpawning(spawning);
        assertEquals(timed, h.engine().isTimed());
        return h;
    }

    // ------------------------------------------------------------------ Ayarlar
    @Test public void settingsHasBigTimePressureToggleStatedInLabelAndPersisted() {
        UiHarness h = new UiHarness(false);
        assertTrue("varsayılan: süreli", h.act.save.timePressure);
        h.click("Ayarlar"); h.settle();
        assertNotNull("başlık", h.findText("Süre baskısı"));
        View b = h.findLabel("Süre baskısı: açık");
        assertNotNull("durum etikette: " + h.clickables(), b);
        assertEquals("düğme metni durumu söyler", "Süre baskısı: Açık", ((android.widget.Button) b).getText().toString());
        A11yAudit a = audit(h, "Ayarlar (süre baskısı)");
        assertEquals("aç/kapa düğmesi denetlendi", 1, a.checkedToggles);
        float dp = b.getHeight() / h.act.getResources().getDisplayMetrics().density;
        assertTrue("büyük düğme: " + dp, dp >= 56f);
        b.performClick(); h.idle(100);
        assertFalse(h.act.save.timePressure);
        View off = h.findLabel("Süre baskısı: kapalı");
        assertNotNull("kapalı durumu etikette", off);
        assertEquals("Süre baskısı: Kapalı", ((android.widget.Button) off).getText().toString());
        assertEquals("v0.9: etiket yalnızca ad ve durum", "Süre baskısı: kapalı", UiHarness.label(off));
        audit(h, "Ayarlar (süre baskısı kapalı)");
        h.act.reloadSave();
        assertFalse("kayıtta kalıcı", h.act.save.timePressure);
        assertFalse(SaveData.parse(h.act.save.serialize()).timePressure);
        // yeniden aç
        h.click("Geri"); h.settle();
        h.click("Ayarlar"); h.settle();
        assertNotNull("ayar ekrana yeniden yansır", h.findLabel("Süre baskısı: kapalı"));
        h.click("Süre baskısı: kapalı");
        assertTrue(h.act.save.timePressure);
        assertNotNull(h.findLabel("Süre baskısı: açık"));
        h.click("Süre baskısı: açık");
        h.click("Geri"); h.settle();
        h.click("Yeni oyun"); h.settle();
        assertFalse("yeni oyun ayarı korur", h.act.save.timePressure);
    }

    @Test public void settingAnnouncesChangeWithTalkBack() {
        ShadowAccessibilityManager sam = talkBackOn();
        UiHarness h = new UiHarness(false);
        h.click("Ayarlar"); h.settle();
        int base = sam.getSentAccessibilityEvents().size();
        h.click("Süre baskısı: açık");
        assertTrue(announced(sam, base, "Süre baskısı kapalı"));
        h.click("Süre baskısı: kapalı");
        assertTrue(announced(sam, base, "Süre baskısı açık"));
        h.controller.pause().stop().destroy();
    }

    // ------------------------------------------------------------------ brifing
    @Test public void briefingTellsTimerOrCustomerQuota() {
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        assertNotNull("süreli: süre satırı", h.findText("Süre: 126 saniye"));
        assertNotNull(h.findText("Süre baskısı: açık"));
        assertNull(h.findText("Bu gün süre yok"));
        h.act.save.timePressure = false;
        h.act.show(new BriefingScreen(h.act)); h.settle();
        int quota = DayConfigQuota(h.act.save);
        assertNotNull("süresiz: brifing satırı", h.findText("Bu gün süre yok, " + quota + " müşteri"));
        assertNotNull(h.findText("Süre baskısı: kapalı"));
        assertNull("süre satırı yok", h.findText("Süre: 126 saniye"));
        audit(h, "Brifing (süresiz)");
    }

    private static int DayConfigQuota(SaveData s) { return com.doner.oyunu.engine.DayConfig.quotaFor(s); }

    // ------------------------------------------------------------------ süresiz gün görünümü
    @Test public void untimedDayShowsRemainingCustomersNoTimerNoPatience() {
        UiHarness h = day(false, 3, false);
        GameEngine e = h.engine();
        e.setSpawning(true);
        h.run(30_000);
        assertNotNull("kalan müşteri başlığı", h.findText("Kalan müşteri"));
        assertNull("süre sayacı yok", h.findText("Mesai"));
        assertFalse(e.getOrders().isEmpty());
        String st = new Narrator(e).statusText();
        assertTrue(st, st.contains("kalan müşteri " + e.getRemainingCustomers()));
        assertFalse(st, st.contains("kalan süre"));
        View card = h.orderCard(e.getFocusOrder()); // v2.0: tek sipariş kartı (sıradaki)
        assertNotNull(card);
        String d = UiHarness.label(card);
        assertFalse("kart açıklamasında sabır yok: " + d, d.contains("sabır") || d.contains("saniye kaldı"));
        audit(h, "Oyun (süresiz)");
    }

    @Test public void timedDayStillShowsTimerAndPatience() {
        UiHarness t = day(true, 3, true);
        t.run(30_000);
        View tc = t.orderCard(t.engine().getFocusOrder());
        assertTrue(UiHarness.label(tc), UiHarness.label(tc).contains("sabır"));
        assertNotNull(t.findText("Süre"));
        assertNull(t.findText("Kalan müşteri"));
        audit(t, "Oyun (süreli)");
    }

    @Test public void untimedDayNobodyLeavesAndNoPatienceWarnings() {
        UiHarness h = day(false, 3, true);
        GameEngine e = h.engine();
        final int[] bad = {0};
        e.addListener(new com.doner.oyunu.engine.GameListenerAdapter() {
            @Override public void onPatienceWarning(Order o, int l) { bad[0]++; }
            @Override public void onCustomerLeft(Order o) { bad[0]++; }
        });
        h.run(300_000); // 5 dk, hiç hareket yok
        assertEquals("sabır uyarısı/gitme yok", 0, bad[0]);
        assertEquals(GameEngine.State.RUNNING, e.getState());
        assertEquals(e.getConfig().baseQueue + e.getUpgrades().queueBonus(), e.getOrders().size());
        assertEquals(3.0, e.getStars(), 1e-9);
        assertTrue(h.screen() instanceof GameScreen);
        audit(h, "Oyun (süresiz, 5 dk bekleme)");
    }

    // ------------------------------------------------------------------ Duraklat menüsünden değiştirme
    @Test public void pauseMenuTogglesTimePressureMidDay() {
        ShadowAccessibilityManager sam = talkBackOn();
        UiHarness h = day(true, 3, true);
        GameEngine e = h.engine();
        h.run(20_000);
        Order o = e.getOrders().get(0);
        float p0 = o.customer.getPatienceLeft();
        assertTrue(p0 < o.customer.patienceMax);
        h.click("Duraklat"); h.run(100);
        View tb = h.findLabel("Süre baskısı: açık");
        assertNotNull("duraklat menüsünde düğme: " + h.clickables(), tb);
        audit(h, "Duraklat (süre baskısı)");
        int base = sam.getSentAccessibilityEvents().size();
        tb.performClick(); h.run(100);
        assertFalse(e.isTimed());
        assertFalse("ana kayda yazıldı", h.act.save.timePressure);
        assertNotNull(h.findLabel("Süre baskısı: kapalı"));
        assertTrue(announced(sam, base, "Süre baskısı: kapalı"));
        h.click("Devam et"); h.run(200);
        assertNotNull("durum çubuğu kalan müşteriye döndü", h.findText("Kalan müşteri"));
        h.run(30_000);
        assertEquals("sabır donmuş", p0, o.customer.getPatienceLeft(), 0.5f);
        audit(h, "Oyun (ortada kapatıldı)");
        // geri aç
        h.click("Duraklat"); h.run(100);
        h.click("Süre baskısı: kapalı"); h.run(100);
        assertTrue(e.isTimed());
        assertTrue(h.act.save.timePressure);
        h.click("Devam et"); h.run(200);
        assertNull(h.findText("Kalan müşteri"));
        h.run(10_000);
        assertTrue("açılınca sabır yine azalır", o.customer.getPatienceLeft() < p0);
        h.controller.pause().stop().destroy();
    }

    // ------------------------------------------------------------------ gün içinde hazırlık bölümü yok
    private void prepSectionGone(boolean timed) {
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        h.act.save.tutorialDone = true;
        h.click("Yeni oyun"); h.settle();
        h.act.save.timePressure = timed;
        h.click("Güne başla"); h.settle();
        assertEquals(GameEngine.State.PREP, h.engine().getState());
        assertNotNull(h.findLabel("Döner dilimle: "));
        assertNotNull(h.findLabel("Hazır stoğu oku"));
        assertNotNull(h.findText("Hazır döner:"));
        assertEquals("hazırlıkta oyun kuralları denetlendi", 1, audit(h, "Hazırlık").checkedGameRules);
        h.click("Dükkânı aç"); h.run(300);
        GameEngine e = h.engine();
        e.setSpawning(false);
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), Collections.<String>emptyList());
        h.run(300);
        h.selectOrder(o); h.run(100);
        assertNull(h.findLabel("Döner dilimle: "));
        assertNull(h.findLabel("Salata ve sos tepsisi hazırla"));
        assertNull(h.findLabel("Hazır stoğu oku"));
        assertNull(h.findText("Hazır döner:"));
        assertEquals(1, audit(h, "Gün (hazırlık bölümü yok)").checkedGameRules);
    }

    @Test public void prepSectionIsGoneInTimedDayEvenWithAnOrder() { prepSectionGone(true); }

    @Test public void prepSectionIsGoneInUntimedDayEvenWithAnOrder() { prepSectionGone(false); }

    @Test public void readyStockStaysInStatusReadAndStationLabel() {
        UiHarness h = day(true, 1, false);
        GameEngine e = h.engine();
        assertEquals(com.doner.oyunu.engine.PrepResult.STARTED, e.startPrepDoner("et_doner", 4));
        h.run(8000);
        assertEquals(4, e.getReadyDoner());
        assertTrue(UiHarness.label(h.stationTile(Station.DONER)).contains("hazır 4 porsiyon"));
        h.click("Durumu oku"); h.run(100);
        assertTrue(new Narrator(e).statusText().contains("Hazır: döner 4 porsiyon"));
    }

    // ------------------------------------------------------------------ süresiz gün oynanır, özet
    @Test public void untimedDayCanBePlayedToTheSummary() {
        UiHarness h = day(false, 1, true);
        GameEngine e = h.engine();
        UiBot.playDay(h, 1_200_000);
        assertTrue("özet ekranı", h.screen() instanceof SummaryScreen);
        DaySummary s = e.getSummary();
        assertFalse(s.timed);
        assertEquals("kota kadar müşteri teslim", e.getCustomerQuota(), s.delivered);
        assertEquals(0, s.lost);
        assertNull("süresiz özette 'Giden müşteri' satırı yok", h.findText("Giden müşteri"));
        assertNotNull(h.findText("Süre baskısı"));
        assertNotNull(h.findText("Teslim edilen sipariş"));
        assertEquals(2, h.act.save.day);
        assertFalse("ayar kayıtta korunur", h.act.save.timePressure);
        audit(h, "Özet (süresiz)");
    }

    @Test public void helpExplainsTimePressureAndDayPrep() {
        UiHarness h = new UiHarness(false);
        StringBuilder all = new StringBuilder();
        for (String[] sec : Texts.helpSections()) all.append(sec[0]).append(' ').append(sec[1]).append(' ');
        String t = all.toString();
        assertTrue(t.contains("Süre baskısı"));
        assertTrue(t.contains("kalan müşteri"));
        assertTrue(t.contains("hazırlık düğmeleri ve hazır stok göstergeleri ekrandan kalkar"));
        assertFalse("eski hazırlık bölümü anlatımı kalmamalı", t.contains("hazırlık sayfanın altındaki Hazırlık bölümündedir"));
        assertTrue(Texts.quickHelp(FlowMode.DENGELI, false).contains("Süre baskısı kapalı"));
        assertTrue(Texts.quickHelp(FlowMode.DENGELI, true).contains("sabır tükenirse"));
        assertTrue(Texts.tutEnd(false).contains("Süre baskısı kapalı"));
        assertFalse(Texts.tutEnd(false).contains("Sabır azalınca"));
        h.click("Nasıl oynanır"); h.settle();
        assertNotNull(h.findText("Süre baskısı"));
    }
}
