package com.doner.oyunu.ui;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;

import com.doner.oyunu.engine.ActiveStep;
import com.doner.oyunu.engine.GameEngine;
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
import java.util.Arrays;
import java.util.List;

/** Hazırlık aşaması, kalem bazlı sipariş kartı, malzeme paneli ve çırak görselleri için uçtan uca arayüz testleri. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class PrepToppingUiTest {

    private UiHarness prep() {
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.click("Güne başla"); h.settle();
        assertEquals(GameEngine.State.PREP, h.engine().getState());
        return h;
    }

    private UiHarness day() {
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.startDay();
        h.engine().setSpawning(false);
        return h;
    }

    private Order spawn(UiHarness h, List<String> products, List<String> requests) {
        Order o = h.engine().spawnOrder(products, requests);
        assertNotNull(o);
        h.run(300);
        return o;
    }

    private static List<String> list(String... s) { return new ArrayList<>(Arrays.asList(s)); }

    /** Seçili siparişin kalem/istasyon akışıyla topping adımının paneline kadar gelir. */
    private void toPanel(UiHarness h, Order o) {
        h.orderCard(o).performClick(); h.run(100);
        long t = 0;
        while (!UiBot.panelOpen(h) && t < 60_000) { UiBot.step(h); h.run(100); t += 100; }
        assertTrue("malzeme paneli açılmalı", UiBot.panelOpen(h));
    }

    // ------------------------------------------------------------------ hazırlık
    @Test public void prepStockIndicatorsButtonsAndClockStays() {
        UiHarness h = prep();
        GameEngine e = h.engine();
        assertEquals(0, e.getReadyDoner());
        View cell = h.findText("Hazır döner: 0 porsiyon");
        assertNotNull("hazır döner göstergesi (sesli açıklamalı)", cell);
        h.click("Döner dilimle");
        h.run(300);
        assertEquals(1, activePrepCount(e));
        assertEquals("oyuncu yuvası dolu", 0, e.getFreeSlots());
        assertNotNull("çalışan şeridi hazırlığı göstermeli", h.findText("Sen: döner dilimleme"));
        h.run(7000);
        assertEquals(4, e.getReadyDoner());
        assertEquals("hazırlıkta gün saati akmaz", 0f, e.getElapsed(), 1e-6f);
        assertNotNull(h.findText("Hazır döner: 4 porsiyon"));
        assertTrue(UiHarness.label(h.findLabel("Döner dilimle")).contains("Hazır 4 / 8 porsiyon"));
        // hazır stoğu oku düğmesi çökmeden çalışır
        h.click("Hazır stoğu oku");
        // durumu oku hazırlığı ve hazır stoğu içerir
        String st = ((com.doner.oyunu.engine.Narrator) h.field(h.screen(), "narrator")).statusText();
        assertTrue(st, st.contains("hazırlık aşaması"));
        assertTrue(st, st.contains("döner 4 porsiyon"));
    }

    private static int activePrepCount(GameEngine e) {
        int n = 0;
        for (ActiveStep s : e.getActiveSteps()) if (s.isPrep()) n++;
        return n;
    }

    @Test public void prepSectionMovesBelowStationsWhenShopOpensAndPrepWorksDuringDay() {
        UiHarness h = prep();
        GameEngine e = h.engine();
        h.click("Dükkânı aç"); h.run(300);
        assertEquals(GameEngine.State.RUNNING, e.getState());
        assertNull(h.findLabel("Dükkânı aç"));
        assertNull(h.findLabel("Hazırlık yapmadan aç"));
        assertNotNull(h.stationTile(Station.DONER));
        e.setSpawning(false);
        // gün içinde de hazırlık yapılabilir
        h.click("Döner dilimle"); h.run(200);
        assertEquals(1, activePrepCount(e));
        h.run(7000);
        assertEquals(4, e.getReadyDoner());
        assertTrue("saat gün içinde akar", e.getElapsed() > 5f);
    }

    @Test public void prepSkipOpensDayWithoutPrep() {
        UiHarness h = prep();
        h.click("Hazırlık yapmadan aç"); h.run(200);
        assertEquals(GameEngine.State.RUNNING, h.engine().getState());
        assertEquals(0, h.engine().getReadyDoner());
    }

    @Test public void trayPanelPreparesSelectedIngredients() {
        UiHarness h = prep();
        GameEngine e = h.engine();
        h.click("Salata ve sos tepsisi hazırla"); h.run(100);
        assertTrue(UiBot.panelOpen(h));
        // hiçbiri seçili değilken Hazırla: başlamaz
        h.click("Hazırla"); h.run(100);
        assertTrue(UiBot.panelOpen(h));
        assertEquals(0, activePrepCount(e));
        View dom = UiBot.row(h, "Domates");
        assertTrue(UiHarness.label(dom), UiHarness.label(dom).startsWith("Domates, eklenmedi"));
        dom.performClick(); h.run(50);
        assertTrue(UiHarness.label(UiBot.row(h, "Domates")).startsWith("Domates, eklendi"));
        UiBot.row(h, "Marul").performClick(); h.run(50);
        assertNotNull(h.findText("Seçili: domates ve marul"));
        int dStock = e.getSave().inventory.get("domates");
        h.click("Hazırla"); h.run(100);
        assertFalse("panel kapanmalı", UiBot.panelOpen(h));
        assertEquals(1, activePrepCount(e));
        assertEquals("malzeme başlarken düşer", dStock - 6, e.getSave().inventory.get("domates"));
        h.run(6000);
        assertEquals(12, e.getTrayTotal());
        assertNotNull(h.findText("Hazır salata ve sos tepsisi: 12 kullanım"));
        // vazgeç: panel kapanır, bir şey başlamaz
        h.click("Salata ve sos tepsisi hazırla"); h.run(100);
        h.click("Vazgeç"); h.run(100);
        assertFalse(UiBot.panelOpen(h));
        assertEquals(0, activePrepCount(e));
    }

    @Test public void apprenticeWorkIsLabelledAndAnnouncedWhenSettingOn() { apprenticeCase(true); }

    @Test public void apprenticeWorkIsLabelledButSilentWhenSettingOff() { apprenticeCase(false); }

    private void apprenticeCase(boolean announce) {
        {
            AccessibilityManager am = (AccessibilityManager) RuntimeEnvironment.getApplication().getSystemService(Context.ACCESSIBILITY_SERVICE);
            ShadowAccessibilityManager sam = shadowOf(am);
            sam.setEnabled(true);
            sam.setTouchExplorationEnabled(true);
            UiHarness h = new UiHarness(false);
            h.click("Yeni oyun"); h.settle();
            h.act.save.upgradeLevels.put("cirak", 1);
            h.act.save.tutorialDone = true;
            h.act.save.apprenticeAnnounce = announce;
            int base = sam.getSentAccessibilityEvents().size();
            h.click("Güne başla"); h.settle();
            h.run(1500);
            assertNotNull("Çırak: döner dilimliyor etiketi (duyuru ayarından bağımsız)", h.findText("Çırak: döner dilimliyor"));
            boolean spoke = false;
            List<AccessibilityEvent> all = new ArrayList<>(sam.getSentAccessibilityEvents());
            for (AccessibilityEvent ev : all.subList(Math.min(base, all.size()), all.size())) {
                if (ev.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT && !ev.getText().isEmpty()
                        && ev.getText().get(0).toString().startsWith("Çırak")) spoke = true;
            }
            h.idle(15000);
            all = new ArrayList<>(sam.getSentAccessibilityEvents());
            for (AccessibilityEvent ev : all.subList(Math.min(base, all.size()), all.size())) {
                if (ev.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT && !ev.getText().isEmpty()
                        && ev.getText().get(0).toString().startsWith("Çırak")) spoke = true;
            }
            assertEquals("çırak duyurusu ayara uymalı (announce=" + announce + ")", announce, spoke);
            h.controller.pause().stop().destroy();
        }
    }

    // ------------------------------------------------------------------ kalem bazlı sipariş kartı
    @Test public void orderCardListsItemsRequestsAndPackRow() {
        UiHarness h = day();
        GameEngine e = h.engine();
        e.getSave().inventory.add("ayran", 10);
        Order o = spawn(h, list("ekmek_arasi", "ayran"), list("soğansız"));
        assertNotNull(h.findText("İstek: soğansız"));
        View k1 = itemRow(h, 1), k2 = itemRow(h, 2);
        assertNotNull(k1); assertNotNull(k2);
        String d1 = UiHarness.label(k1);
        assertTrue(d1, d1.contains("Ekmek arası döner"));
        assertTrue(d1, d1.contains("Malzemeleri ekle kilitli"));
        assertTrue(d1, d1.contains("Ekmeği ısıt bekliyor"));
        String pack = UiHarness.label(h.findLabel("Siparişi paketle"));
        assertTrue(pack, pack.contains("kilitli"));
        // kalem seç
        k2.performClick(); h.run(100);
        assertEquals(1, e.getSelectedItem());
        assertEquals(o.id, e.getSelectedOrderId());
        assertTrue(UiHarness.label(itemRow(h, 2)).contains("Bu kalem seçili"));
        // istasyon dokunuşu seçili kalemi yapar (ayran)
        h.stationTile(Station.ICECEK).performClick(); h.run(200);
        assertTrue(o.getLine(1).isStepRunning(0));
        assertTrue(UiHarness.label(itemRow(h, 2)).contains("Ayranı doldur sürüyor"));
        h.run(3000);
        assertTrue(o.getLine(1).isDone());
        assertTrue(UiHarness.label(itemRow(h, 2)).contains("Kalem hazır"));
        // serbest sıra: şimdi döner kalemini yap
        itemRow(h, 1).performClick(); h.run(100);
        h.stationTile(Station.DONER).performClick(); h.run(200);
        assertTrue(o.getLine(0).isStepRunning(1));
        h.run(5000);
        // paketleme hâlâ kilitli: yıldız cezası yok
        double stars = e.getStars();
        int wrong = e.getSummary().wrongTaps;
        h.click("Siparişi paketle"); h.run(100);
        assertEquals(stars, e.getStars(), 1e-9);
        assertEquals(wrong, e.getSummary().wrongTaps);
        assertNotNull("kilit açıklaması görünmeli", h.findText("Paketlemeden önce tüm kalemler hazır olmalı"));
    }

    private View itemRow(UiHarness h, int k) {
        for (View v : UiHarness.visibleViews(h.root())) {
            if (v.isClickable() && UiHarness.label(v).startsWith("Sipariş 1, kalem " + k)) return v;
        }
        return null;
    }

    @Test public void lockedToppingStepExplainsWithoutPenalty() {
        UiHarness h = day();
        GameEngine e = h.engine();
        Order o = spawn(h, list("ekmek_arasi"), list());
        h.orderCard(o).performClick(); h.run(100);
        h.stationTile(Station.SOS).performClick(); h.run(100);
        assertFalse(UiBot.panelOpen(h));
        assertEquals(0, e.getSummary().wrongTaps);
        assertEquals(3.0, e.getStars(), 1e-9);
        TextView notice = (TextView) h.field(h.screen(), "noticeTv");
        assertEquals(View.VISIBLE, notice.getVisibility());
        assertTrue(notice.getText().toString(), notice.getText().toString().contains("Malzeme eklemeden önce"));
    }

    // ------------------------------------------------------------------ malzeme paneli
    @Test public void toppingPanelFlowPerfectAndFrozenClock() {
        UiHarness h = day();
        GameEngine e = h.engine();
        Order o = spawn(h, list("ekmek_arasi"), list("soğansız"));
        toPanel(h, o);
        float el = e.getElapsed();
        h.run(4000);
        assertEquals("panel açıkken saat durur", el, e.getElapsed(), 1e-4f);
        // müşteri isteği panelde ve kartta görünür
        assertNotNull(h.findText("Müşteri isteği: soğansız"));
        // 1. gün: varsayılan malzemeler hazır seçili
        assertTrue(UiHarness.label(UiBot.row(h, "Soğan")).startsWith("Soğan, eklendi"));
        UiBot.row(h, "Soğan").performClick(); h.run(50);
        assertTrue(UiHarness.label(UiBot.row(h, "Soğan")).startsWith("Soğan, eklenmedi"));
        // hedefi oku
        h.click("Hedefi oku"); h.run(50);
        View target = h.findText("Hedef, Ekmek arası döner");
        assertNotNull(target);
        assertTrue(UiHarness.label(target).contains("Müşteri isteği: soğansız"));
        assertFalse(UiHarness.label(target).contains("Soğan"));
        h.click("Seçimi oku");
        h.click("Tamam"); h.run(200);
        assertFalse(UiBot.panelOpen(h));
        assertTrue(o.getLine(0).isStepRunning(2));
        assertTrue("saat yeniden akar", e.getElapsed() > el);
        UiBot.playDay(h, 1);
        long t = 0;
        while (e.getSummary().delivered == 0 && t < 60_000) { UiBot.step(h); h.run(100); t += 100; }
        assertEquals(1, e.getSummary().delivered);
        assertEquals("doğru seçim: kusursuz", 1, e.getSummary().perfect);
        assertEquals(0, e.getSummary().mistakes);
        TextView notice = (TextView) h.field(h.screen(), "noticeTv");
        assertTrue(notice.getText().toString(), notice.getText().toString().contains("Malzemeler tam"));
    }

    @Test public void wrongSelectionIsReportedAtDelivery() {
        UiHarness h = day();
        GameEngine e = h.engine();
        Order o = spawn(h, list("ekmek_arasi"), list("soğansız"));
        toPanel(h, o);
        // soğansız istendi ama soğan seçili bırakılır
        assertTrue(UiHarness.label(UiBot.row(h, "Soğan")).startsWith("Soğan, eklendi"));
        h.click("Tamam"); h.run(200);
        double stars = 0;
        long t = 0;
        while (e.getSummary().delivered == 0 && t < 60_000) {
            if (e.getOrders().size() == 1 && e.getOrders().get(0).isReady()) stars = e.getStars();
            UiBot.step(h); h.run(100); t += 100;
        }
        assertEquals(1, e.getSummary().delivered);
        assertEquals(1, e.getSummary().mistakes);
        TextView notice = (TextView) h.field(h.screen(), "noticeTv");
        String n = notice.getText().toString();
        assertTrue(n, n.contains("Hatalı sipariş"));
        assertTrue(n, n.contains("Soğan istememiştim"));
        assertTrue("yıldız düşmeli", e.getStars() < stars + 0.12);
    }

    @Test public void outOfStockToppingIsMarkedAndEmergencyBuyEnablesIt() {
        UiHarness h = day();
        GameEngine e = h.engine();
        e.getSave().inventory.set("domates", 0);
        Order o = spawn(h, list("ekmek_arasi"), list());
        toPanel(h, o);
        View dom = UiBot.row(h, "Domates");
        assertTrue(UiHarness.label(dom), UiHarness.label(dom).startsWith("Domates, stokta yok"));
        assertNotNull("stokta yok uyarısı", h.findText("Stokta yok: Domates"));
        assertNull(h.findLabel("Acil al"));
        dom.performClick(); h.run(50);
        View em = h.findLabel("Acil al: Domates");
        assertNotNull("acil al seçeneği çıkmalı", em);
        int money = e.getMoney();
        em.performClick(); h.run(100);
        assertTrue(e.getMoney() < money);
        assertTrue(UiHarness.label(UiBot.row(h, "Domates")).startsWith("Domates, eklenmedi"));
        assertNull(h.findLabel("Acil al"));
        UiBot.row(h, "Domates").performClick(); h.run(50);
        assertTrue(UiHarness.label(UiBot.row(h, "Domates")).startsWith("Domates, eklendi"));
    }

    @Test public void cancelPanelKeepsStepPendingAndReopens() {
        UiHarness h = day();
        Order o = spawn(h, list("ekmek_arasi"), list());
        toPanel(h, o);
        h.click("Vazgeç"); h.run(100);
        assertFalse(UiBot.panelOpen(h));
        assertFalse(o.isRunning());
        h.stationTile(Station.SOS).performClick(); h.run(100);
        assertTrue(UiBot.panelOpen(h));
    }

    // ------------------------------------------------------------------ ses
    @Test public void stationSoundRunsForTheStepPausesWithGameAndStopsWhenDone() {
        UiHarness h = day();
        GameEngine e = h.engine();
        com.doner.oyunu.audio.SoundManager snd = h.act.sound;
        Order o = spawn(h, list("ekmek_arasi"), list());
        h.orderCard(o).performClick(); h.run(100);
        assertEquals(0, snd.activeLoopCount());
        h.stationTile(Station.EKMEK).performClick(); h.run(200);
        assertTrue(o.isRunning());
        assertEquals("adım süresince istasyon sesi", 1, snd.activeLoopCount());
        // duraklat: ses durur; devam: sürer
        h.click("Duraklat"); h.run(100);
        assertEquals(0, snd.activeLoopCount());
        h.click("Devam et"); h.run(100);
        assertEquals(1, snd.activeLoopCount());
        // adım bitince ses biter
        long t = 0;
        while (o.isRunning() && t < 20_000) { h.run(100); t += 100; }
        assertFalse(o.isRunning());
        assertEquals(0, snd.activeLoopCount());
        // hazırlık da istasyon sesi kullanır
        h.click("Döner dilimle"); h.run(200);
        assertEquals(1, snd.activeLoopCount());
        h.controller.pause(); h.idle(200);
        assertEquals("arka planda sesler durur", 0, snd.activeLoopCount());
        h.controller.resume(); h.idle(200);
        h.click("Devam et"); h.run(100);
        assertEquals(1, snd.activeLoopCount());
        h.run(8000);
        assertEquals(0, snd.activeLoopCount());
    }

    @Test public void toppingPanelSilencesStationSoundsUntilClosed() {
        UiHarness h = day();
        com.doner.oyunu.audio.SoundManager snd = h.act.sound;
        Order o = spawn(h, list("ekmek_arasi"), list());
        toPanel(h, o);
        assertEquals(0, snd.activeLoopCount());
        h.click("Tamam"); h.run(200);
        assertEquals("seçimden sonra malzeme ekleme sesi çalar", 1, snd.activeLoopCount());
    }

    @Test public void apprenticeStepsPlayQuietCenteredSound() {
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.act.save.upgradeLevels.put("cirak", 1);
        h.click("Güne başla"); h.settle();
        h.run(1500);
        assertEquals("çırak hazırlığı sesi", 1, h.act.sound.activeLoopCount());
    }

    // ------------------------------------------------------------------ tam gün
    @Test public void fullDayPrepThenOrdersToppingsAndDeliveries() {
        UiHarness h = prep();
        GameEngine e = h.engine();
        h.click("Döner dilimle");
        long t = 0;
        while (e.getReadyDoner() == 0 && t < 20_000) { h.run(100); t += 100; }
        h.click("Salata ve sos tepsisi hazırla"); h.run(100);
        for (String n : new String[]{"Domates", "Marul", "Soğan", "Ketçap"}) UiBot.row(h, n).performClick();
        h.click("Hazırla"); h.run(100);
        t = 0;
        while (e.getTrayTotal() == 0 && t < 20_000) { h.run(100); t += 100; }
        assertEquals(24, e.getTrayTotal());
        h.click("Dükkânı aç"); h.run(200);
        int panels = 0;
        t = 0;
        boolean was = false;
        while (h.screen() instanceof GameScreen && t < 400_000) {
            boolean now = UiBot.panelOpen(h);
            if (now && !was) panels++;
            was = now;
            UiBot.step(h); h.run(100); t += 100;
        }
        h.run(20_000);
        assertTrue("özet ekranı", h.screen() instanceof SummaryScreen);
        assertTrue("malzeme paneli kullanıldı", panels > 0);
        assertNotNull(h.findText("Hazırlık"));
        assertNotNull(h.findText("Teslim edilen sipariş"));
        assertTrue(h.act.save.day == 2);
    }

    @Test public void settingsHasApprenticeAnnounceSwitchThatPersists() {
        UiHarness h = new UiHarness(false);
        h.click("Ayarlar"); h.settle();
        View sw = null;
        for (View v : UiHarness.visibleViews(h.root())) if (v instanceof android.widget.Switch && ((android.widget.Switch) v).getText().toString().startsWith("Çırak duyuruları")) sw = v;
        assertNotNull(sw);
        assertTrue(h.act.save.apprenticeAnnounce);
        sw.performClick(); h.idle(100);
        assertFalse(h.act.save.apprenticeAnnounce);
        h.click("Geri"); h.settle();
        assertEquals("kayıtta kalıcı", false, SaveData.parse(h.act.save.serialize()).apprenticeAnnounce);
        h.click("Yeni oyun"); h.settle();
        assertFalse("yeni oyun ayarı korur", h.act.save.apprenticeAnnounce);
    }

    @Test public void marketExplainsApprenticeLevels() {
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.click("Markete git"); h.settle();
        h.click("Mutfak aletleri"); h.settle();
        View info = h.findText("Çırak, seviye 0");
        assertNotNull(info);
        String d = UiHarness.label(info);
        assertTrue(d, d.contains("Seviye 1:") && d.contains("Seviye 2:") && d.contains("Seviye 3:"));
        assertTrue(d, d.contains("paketler") && d.contains("hazırlık") || d.contains("tepsi"));
        assertTrue(d, d.contains("Malzeme seçimi her zaman sende"));
    }
}
