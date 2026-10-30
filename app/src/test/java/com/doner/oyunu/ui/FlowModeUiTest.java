package com.doner.oyunu.ui;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import com.doner.oyunu.engine.ActiveStep;
import com.doner.oyunu.engine.DaySummary;
import com.doner.oyunu.engine.FlowMode;
import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.PortionSize;
import com.doner.oyunu.engine.SaveData;
import com.doner.oyunu.engine.Station;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAccessibilityManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** v0.7 oyun akışı arayüzü: ayarlar, brifing, duraklat menüsü, tek dokunuş otomatik malzeme/boy, duyurular, erişilebilirlik. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class FlowModeUiTest {

    private static void audit(UiHarness h, String where) {
        h.idle(100);
        A11yAudit a = A11yAudit.run(h, where);
        if (!a.warnings.isEmpty()) System.out.println("A11Y-UYARI\n" + a.report());
        assertTrue(where + ": denetlenen etkileşimli görünüm yok", a.checkedInteractive > 0);
        assertTrue("Erişilebilirlik hataları:\n" + a.report(), a.errors.isEmpty());
    }

    private static ShadowAccessibilityManager talkBackOn() {
        AccessibilityManager am = (AccessibilityManager) RuntimeEnvironment.getApplication().getSystemService(Context.ACCESSIBILITY_SERVICE);
        ShadowAccessibilityManager sam = shadowOf(am);
        sam.setEnabled(true);
        sam.setTouchExplorationEnabled(true);
        return sam;
    }

    private static List<String> announcements(ShadowAccessibilityManager sam, int from) {
        List<String> out = new ArrayList<>();
        List<AccessibilityEvent> all = new ArrayList<>(sam.getSentAccessibilityEvents());
        for (AccessibilityEvent ev : all.subList(Math.min(from, all.size()), all.size())) {
            if (ev.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT && !ev.getText().isEmpty()) out.add(ev.getText().get(0).toString());
        }
        return out;
    }

    private static boolean anyContains(List<String> l, String s) {
        for (String t : l) if (t.contains(s)) return true;
        return false;
    }

    /** Akışlı gün: öğretici kapalı, müşteri gelişi kapalı (senaryo siparişleri). */
    private static UiHarness day(FlowMode m, int dayNo) {
        UiHarness h = new UiHarness(false, m);
        h.act.save.tutorialDone = true;
        h.click("Yeni oyun"); h.settle();
        h.act.save.day = dayNo;
        h.act.save.money = 30_000;
        com.doner.oyunu.engine.Shop.buyRecommended(h.act.save);
        h.startDay();
        h.engine().setSpawning(false);
        assertEquals(m, h.engine().getFlowMode());
        return h;
    }

    private static Order spawn(UiHarness h, PortionSize size, String... requests) {
        Order o = h.engine().spawnOrder(Collections.singletonList("ekmek_arasi"), Arrays.asList(requests), Collections.singletonList(size));
        assertNotNull(o);
        h.run(300);
        h.selectOrder(o);
        h.run(100);
        return o;
    }

    private static void tapStation(UiHarness h, Station st) {
        View t = h.stationTile(st);
        assertNotNull("istasyon düğmesi " + st, t);
        t.performClick();
        h.run(100);
    }

    private static void waitIdle(UiHarness h, Order o) {
        long t = 0;
        while (o.isRunning() && t < 20_000) { h.run(100); t += 100; }
        assertFalse(o.isRunning());
    }

    private static void packAndDeliver(UiHarness h, Order o) {
        tapStation(h, Station.PAKET);
        waitIdle(h, o);
        assertTrue(o.isReady());
        h.click("Teslim et");
        h.run(300);
    }

    // ------------------------------------------------------------------ ayarlar
    @Test public void settingsOffersThreeFlowsDefaultDengeliAndPersists() {
        UiHarness h = new UiHarness(false);
        assertEquals(FlowMode.DENGELI, h.act.save.flowMode);
        h.click("Ayarlar"); h.settle();
        assertNotNull("başlık", h.findText("Oyun akışı"));
        View den = h.findLabel("Dengeli, seçili");
        assertNotNull("Dengeli varsayılan ve seçili okunur: " + h.clickables(), den);
        assertTrue(UiHarness.label(den).contains("önerilen"));
        assertNotNull(h.findLabel("Rahat"));
        assertNotNull(h.findLabel("Gerçekçi"));
        audit(h, "Ayarlar (akış)");
        assertEquals("seçili akış düğmesi denetlendi", 1, A11yAudit.run(h, "Ayarlar (seçili)").checkedSelected);
        h.click("Rahat");
        assertEquals(FlowMode.RAHAT, h.act.save.flowMode);
        assertTrue(h.act.save.flowChosen);
        assertNotNull(h.findLabel("Rahat, seçili"));
        assertNotNull(h.findLabel("Dengeli, önerilen"));
        h.act.reloadSave();
        assertEquals("kayıtta kalıcı", FlowMode.RAHAT, h.act.save.flowMode);
        assertEquals(FlowMode.RAHAT, SaveData.parse(h.act.save.serialize()).flowMode);
        // yeni oyun ayarı korur
        h.click("Geri"); h.settle();
        h.click("Yeni oyun"); h.settle();
        assertEquals("yeni oyunda akış korunur", FlowMode.RAHAT, h.act.save.flowMode);
    }

    // ------------------------------------------------------------------ brifing
    @Test public void briefingTellsFlowAsksOnceAndCyclesWithOneButton() {
        ShadowAccessibilityManager sam = talkBackOn();
        UiHarness h = new UiHarness(false);
        int base = sam.getSentAccessibilityEvents().size();
        h.click("Yeni oyun"); h.settle();
        assertTrue(h.screen() instanceof BriefingScreen);
        assertNotNull("brifing satırı", h.findText("Oyun akışı: Dengeli."));
        assertTrue("ilk seferde kısa sesli soru", anyContains(announcements(sam, base), "Oyun akışı şu an Dengeli"));
        View b = h.findLabel("Oyun akışı: Dengeli");
        assertNotNull(h.clickables().toString(), b);
        audit(h, "Brifing (akış)");
        b.performClick(); h.idle(100);
        assertEquals(FlowMode.GERCEKCI, h.act.save.flowMode);
        assertTrue(anyContains(announcements(sam, base), "Oyun akışı: Gerçekçi."));
        assertNotNull(h.findLabel("Oyun akışı: Gerçekçi"));
        assertNotNull("brifing satırı güncellenir", h.findText("Oyun akışı: Gerçekçi."));
        h.click("Oyun akışı: Gerçekçi");
        assertEquals(FlowMode.RAHAT, h.act.save.flowMode);
        h.click("Güne başla"); h.settle();
        assertTrue(h.act.save.flowChosen);
        assertEquals(FlowMode.RAHAT, h.engine().getFlowMode());
        h.controller.pause().stop().destroy();
    }

    // ------------------------------------------------------------------ Rahat: istasyon başına tek dokunuş
    @Test public void rahatIsOneTapPerStationWithAutoSizeAndToppings() {
        UiHarness h = day(FlowMode.RAHAT, 5);
        GameEngine e = h.engine();
        Order o = spawn(h, PortionSize.HALF, "soğansız");
        tapStation(h, Station.EKMEK);
        waitIdle(h, o);
        tapStation(h, Station.DONER);
        assertFalse("Rahat: boy paneli açılmaz", UiBot.panelOpen(h));
        ActiveStep a = e.getActiveFor(o.id);
        assertNotNull(a);
        assertEquals("istenen boy otomatik", PortionSize.HALF, a.getSize());
        waitIdle(h, o);
        tapStation(h, Station.SOS);
        assertFalse("Rahat: malzeme paneli açılmaz", UiBot.panelOpen(h));
        assertNotNull("otomatik iş sürüyor", e.getActiveFor(o.id));
        assertTrue(e.getActiveFor(o.id).isAutoToppings());
        waitIdle(h, o);
        assertNotNull("kısa duyuru ekranda da görünür", h.findText("Malzemeler eklendi: domates, marul ve ketçap. İstek uygulandı: soğansız."));
        assertTrue(o.getLine(0).isDone());
        packAndDeliver(h, o);
        DaySummary s = e.getSummary();
        assertEquals(1, s.delivered);
        assertEquals("Rahat: istek ve boy doğru", 0, s.mistakes);
        assertEquals(1, s.perfect);
    }

    // ------------------------------------------------------------------ Dengeli: istek farkı elle
    @Test public void dengeliDefaultsOneTapThenRequestDifferenceInPanel() {
        ShadowAccessibilityManager sam = talkBackOn();
        UiHarness h = day(FlowMode.DENGELI, 5);
        GameEngine e = h.engine();
        int base = sam.getSentAccessibilityEvents().size();
        Order o = spawn(h, PortionSize.FULL, "soğansız");
        tapStation(h, Station.EKMEK);
        waitIdle(h, o);
        tapStation(h, Station.DONER);
        assertFalse("tam ekmek: panel yok", UiBot.panelOpen(h));
        waitIdle(h, o);
        tapStation(h, Station.SOS);
        assertFalse("varsayılanlar tek dokunuş", UiBot.panelOpen(h));
        waitIdle(h, o);
        List<String> said = announcements(sam, base);
        assertTrue(said.toString(), anyContains(said, "Varsayılan malzemeler eklendi: domates, marul, soğan ve ketçap."));
        assertTrue(said.toString(), anyContains(said, "İstek: soğansız. Farkı sen yap"));
        assertFalse("istek farkı var: adım açık", o.getLine(0).isDone());
        tapStation(h, Station.SOS);
        assertTrue("fark için panel açılır", UiBot.panelOpen(h));
        assertNotNull("Malzeme çıkar grubu başlığı", h.findText("Malzeme çıkar"));
        audit(h, "Dengeli fark paneli");
        View rm = UiBot.row(h, "Soğan çıkar");
        assertNotNull(h.clickables().toString(), rm);
        rm.performClick(); h.run(200);
        assertTrue("panel art arda kullanım için açık kalır", UiBot.panelOpen(h));
        assertFalse(o.getLine(0).getAddedSet().contains("sogan"));
        UiBot.row(h, "Malzemeleri bitir").performClick(); h.run(200);
        assertFalse(UiBot.panelOpen(h));
        assertTrue(o.getLine(0).isDone());
        packAndDeliver(h, o);
        assertEquals(0, e.getSummary().mistakes);
        assertEquals(1, e.getSummary().perfect);
        h.controller.pause().stop().destroy();
    }

    @Test public void dengeliHalfSizeOpensSizePanel() {
        UiHarness h = day(FlowMode.DENGELI, 5);
        Order o = spawn(h, PortionSize.HALF);
        tapStation(h, Station.EKMEK);
        waitIdle(h, o);
        tapStation(h, Station.DONER);
        assertTrue("yarım ekmek: boyu oyuncu seçer", UiBot.panelOpen(h));
        assertNotNull(UiBot.row(h, "Yarım ekmek"));
        UiBot.row(h, "Yarım ekmek").performClick(); h.run(200);
        assertFalse(UiBot.panelOpen(h));
        waitIdle(h, o);
        assertEquals(PortionSize.HALF, o.getLine(0).getChosenSize());
    }

    // ------------------------------------------------------------------ oyun ortasında değişim + TalkBack eylemleri
    @Test public void pauseMenuChangesFlowMidDayAndTalkBackActionsFollow() {
        talkBackOn();
        UiHarness h = day(FlowMode.RAHAT, 5);
        GameEngine e = h.engine();
        Order o = spawn(h, PortionSize.FULL);
        tapStation(h, Station.EKMEK);
        waitIdle(h, o);
        tapStation(h, Station.DONER);
        waitIdle(h, o);
        View sos = h.stationTile(Station.SOS);
        assertTrue("Rahat eylemi", UiBot.actionIdByLabel(sos, "Malzemeleri ekle") >= 0);
        assertTrue("okuma eylemi de var", UiBot.actionIdByLabel(sos, "Hedefi oku") >= 0);
        h.click("Duraklat"); h.run(100);
        View fb = h.findLabel("Oyun akışı: Rahat");
        assertNotNull(h.clickables().toString(), fb);
        audit(h, "Duraklat (akış)");
        fb.performClick(); h.run(100);
        assertEquals(FlowMode.DENGELI, e.getFlowMode());
        assertEquals("ayar ana kayda da yazılır", FlowMode.DENGELI, h.act.save.flowMode);
        assertNotNull(h.findLabel("Oyun akışı: Dengeli"));
        h.click("Devam et"); h.run(200);
        sos = h.stationTile(Station.SOS);
        assertTrue("Dengeli eylemi", UiBot.actionIdByLabel(sos, "Varsayılan malzemeleri ekle") >= 0);
        assertTrue(sos.performAccessibilityAction(UiBot.actionIdByLabel(sos, "Varsayılan malzemeleri ekle"), null));
        h.run(100);
        assertFalse("eylem paneli açmaz", UiBot.panelOpen(h));
        waitIdle(h, o);
        assertTrue("istek yok: adım bitti", o.getLine(0).isDone());
        // Gerçekçi'ye geç: tek tek ekleme eylemleri geri gelir (yeni sipariş)
        e.setFlowMode(FlowMode.GERCEKCI);
        Order p = spawn(h, PortionSize.FULL);
        tapStation(h, Station.EKMEK);
        waitIdle(h, p);
        View don = h.stationTile(Station.DONER);
        assertTrue("Gerçekçi: boy eylemleri", UiBot.actionIdByLabel(don, "Tam ekmek") >= 0);
    }

    // ------------------------------------------------------------------ stok yok -> acil al
    @Test public void autoToppingsOutOfStockShowsEmergencyBuyAndThenWorks() {
        UiHarness h = day(FlowMode.DENGELI, 3);
        GameEngine e = h.engine();
        Order o = spawn(h, PortionSize.FULL);
        tapStation(h, Station.EKMEK);
        waitIdle(h, o);
        tapStation(h, Station.DONER);
        waitIdle(h, o);
        e.getSave().inventory.set("marul", 0);
        h.run(100);
        tapStation(h, Station.SOS);
        h.run(200);
        View em = h.findLabel("Acil al");
        assertNotNull("acil al düğmesi: " + h.clickables(), em);
        assertTrue(UiHarness.label(em), UiHarness.label(em).contains("Marul"));
        em.performClick(); h.run(200);
        assertTrue(e.getSave().inventory.get("marul") > 0);
        tapStation(h, Station.SOS);
        waitIdle(h, o);
        assertTrue(o.getLine(0).isDone());
    }

    // ------------------------------------------------------------------ tam gün, her akış
    private static void fullDay(FlowMode m, boolean viaActions) { fullDay(m, viaActions, 20260101L); }

    private static void fullDay(FlowMode m, boolean viaActions, long seed) {
        GameScreen.testSeed = seed;
        try { fullDayImpl(m, viaActions, seed); } finally { GameScreen.testSeed = null; }
    }

    private static void fullDayImpl(FlowMode m, boolean viaActions, long seed) {
        UiHarness h = new UiHarness(false, m);
        h.act.save.tutorialDone = true;
        h.click("Yeni oyun"); h.settle();
        h.act.save.day = 5;
        h.act.save.money = 30_000;
        com.doner.oyunu.engine.Shop.buyRecommended(h.act.save);
        h.startDay();
        GameEngine e = h.engine();
        boolean audited = false;
        UiBot.viaActions = viaActions;
        try {
            long t = 0;
            while (h.screen() instanceof GameScreen && t < 500_000) {
                if (!audited && UiBot.panelOpen(h)) { audit(h, m + " gün içi panel"); audited = true; }
                UiBot.step(h); h.run(100); t += 100;
            }
        } finally { UiBot.viaActions = false; }
        h.run(20_000);
        assertTrue(m + " özet ekranı", h.screen() instanceof SummaryScreen);
        DaySummary s = e.getSummary();
        assertFalse(m + " gün başarısız olmamalı", s.failed);
        assertTrue(m + " seed " + seed + " sipariş teslim edildi: " + s.delivered, s.delivered >= 3);
        assertEquals(m + " hatasız", 0, s.mistakes);
        assertEquals(6, h.act.save.day);
        assertEquals("akış ayarı gün sonunda korunur", m, h.act.save.flowMode);
    }

    @Test public void uiBotPlaysAFullDayInRahat() { fullDay(FlowMode.RAHAT, false); }

    @Test public void uiBotPlaysAFullDayInDengeli() { fullDay(FlowMode.DENGELI, false); }

    @Test public void uiBotPlaysAFullDayInDengeliViaTalkBackActions() { fullDay(FlowMode.DENGELI, true); }

    @Test public void uiBotPlaysAFullDayInGercekci() { fullDay(FlowMode.GERCEKCI, false); }

    /** Eski dalgalanma (rastgele tohum) için: sabit tohumlarla her akışta tam gün (Robolectric her testte kayıtları sıfırlar). */
    @Test public void fullDayRahatSeed1() { fullDay(FlowMode.RAHAT, false, 1L); }
    @Test public void fullDayDengeliSeed1() { fullDay(FlowMode.DENGELI, false, 1L); }
    @Test public void fullDayGercekciSeed1() { fullDay(FlowMode.GERCEKCI, false, 1L); }
    @Test public void fullDayRahatSeed2() { fullDay(FlowMode.RAHAT, false, 2L); }
    @Test public void fullDayDengeliSeed2() { fullDay(FlowMode.DENGELI, false, 2L); }
    @Test public void fullDayGercekciSeed2() { fullDay(FlowMode.GERCEKCI, false, 2L); }
    @Test public void fullDayRahatSeed3() { fullDay(FlowMode.RAHAT, false, 3L); }
    @Test public void fullDayDengeliSeed3() { fullDay(FlowMode.DENGELI, false, 3L); }
    @Test public void fullDayGercekciSeed3() { fullDay(FlowMode.GERCEKCI, false, 3L); }

    // ------------------------------------------------------------------ yardım
    @Test public void helpScreenExplainsFlows() {
        UiHarness h = new UiHarness(false);
        h.click("Nasıl oynanır"); h.settle();
        assertNotNull(h.findText("Oyun akışı"));
        audit(h, "Yardım (akış)");
        String all = "";
        for (String[] s : Texts.helpSections()) all += s[0] + " " + s[1] + " ";
        for (String k : new String[]{"Rahat", "Dengeli", "Gerçekçi", "Standart malzemelerle bitir", "Varsayılan malzemeler eklendi", "Acil al"}) {
            assertTrue("yardımda: " + k, all.contains(k));
        }
    }
}
