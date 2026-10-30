package com.doner.oyunu.ui;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;

import com.doner.oyunu.engine.ActiveStep;
import com.doner.oyunu.engine.FlowMode;
import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.OrderLine;
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
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        h.click("Yeni oyun"); h.settle();
        h.click("Güne başla"); h.settle();
        assertEquals(GameEngine.State.PREP, h.engine().getState());
        return h;
    }

    private UiHarness day() {
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
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

    /** Hazırlık düğmesine dokunur, açılan panelden "4 porsiyon" der. */
    private void prepDoner4(UiHarness h) {
        h.click("Döner dilimle"); h.run(100);
        assertTrue("döner hazırlık paneli açılmalı", UiBot.panelOpen(h));
        h.click("4 porsiyon"); h.run(100);
        assertFalse("seçenek uygulanınca panel kapanmalı", UiBot.panelOpen(h));
    }

    /** Gün içi hazırlık: DONER istasyon düğmesine dokunur (sipariş yoksa hazırlık seçenekleri paneli açılır), "4 porsiyon" der. */
    private void prepDoner4ViaStation(UiHarness h) {
        View tile = h.stationTile(Station.DONER);
        assertNotNull(tile);
        tile.performClick(); h.run(100);
        assertTrue("döner istasyonunun hazırlık paneli açılmalı", UiBot.panelOpen(h));
        assertEquals(Station.DONER.id, UiBot.panelOf(h).stationId);
        h.click("4 porsiyon"); h.run(100);
        assertFalse("seçenek uygulanınca panel kapanmalı", UiBot.panelOpen(h));
    }

    private static boolean sosPanelOpen(UiHarness h) {
        return UiBot.panelOpen(h) && UiBot.panelOf(h).stationId == Station.SOS.id;
    }

    /** Seçili siparişin kalem/istasyon akışıyla topping adımının (SOS) seçenek paneline kadar gelir. */
    private void toPanel(UiHarness h, Order o) {
        h.selectOrder(o); h.run(100);
        long t = 0;
        while (!sosPanelOpen(h) && t < 60_000) { UiBot.step(h); h.run(100); t += 100; }
        assertTrue("sos seçenek paneli açılmalı", sosPanelOpen(h));
    }

    /** Panelde seçeneği (etiketi bu metinle başlayan) uygular ve eklemenin bitmesini bekler. */
    private void pick(UiHarness h, String labelStart) {
        View v = UiBot.row(h, labelStart);
        assertNotNull("panelde seçenek yok: " + labelStart + " mevcut=" + h.clickables(), v);
        v.performClick();
        h.run(900);
    }

    // ------------------------------------------------------------------ hazırlık
    @Test public void prepStockIndicatorsButtonsAndClockStays() {
        UiHarness h = prep();
        GameEngine e = h.engine();
        assertEquals(0, e.getReadyDoner());
        View cell = h.findText("Hazır döner: 0 porsiyon");
        assertNotNull("hazır döner göstergesi (sesli açıklamalı)", cell);
        prepDoner4(h);
        h.run(300);
        assertEquals(1, activePrepCount(e));
        assertEquals("oyuncu yuvası dolu", 0, e.getFreeSlots());
        assertNotNull("çalışan şeridi hazırlığı göstermeli", h.findText("Sen: döner dilimleme"));
        h.run(7000);
        assertEquals(4, e.getReadyDoner());
        assertEquals("hazırlıkta gün saati akmaz", 0f, e.getElapsed(), 1e-6f);
        assertNotNull(h.findText("Hazır döner: 4 porsiyon"));
        assertTrue(UiHarness.label(h.findLabel("Döner dilimle")).contains("hazır 4 / 8"));
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

    /** v0.8: hazırlık bölümü (düğmeler, hazır stok göstergeleri, stok okuma) yalnızca PREP'te görünür; gün içinde yoktur. */
    private static void assertPrepSectionVisible(UiHarness h, boolean visible) {
        for (String label : new String[]{"Döner dilimle: ", "Salata ve sos tepsisi hazırla", "Hazır stoğu oku"}) {
            if (visible) assertNotNull("hazırlıkta görünür: " + label, h.findLabel(label));
            else assertNull("gün içinde yok: " + label + " -> " + (h.findLabel(label) == null ? "" : UiHarness.label(h.findLabel(label))), h.findLabel(label));
        }
        if (visible) assertNotNull(h.findText("Hazır döner:"));
        else {
            assertNull("hazır stok göstergesi gün içinde yok", h.findText("Hazır döner:"));
            assertNull(h.findText("Hazır salata ve sos tepsisi"));
        }
    }

    @Test public void prepSectionOnlyInPrepPhaseAndPrepWorksFromStationsDuringDay() {
        UiHarness h = prep();
        GameEngine e = h.engine();
        assertPrepSectionVisible(h, true);
        h.click("Dükkânı aç"); h.run(300);
        assertEquals(GameEngine.State.RUNNING, e.getState());
        assertNull(h.findLabel("Dükkânı aç"));
        assertNull(h.findLabel("Hazırlık yapmadan aç"));
        assertNotNull(h.stationTile(Station.DONER));
        assertPrepSectionVisible(h, false);
        assertNull("Hazırlık bölüm başlığı gün içinde yok", h.findText("Hazırlık"));
        e.setSpawning(false);
        // gün içi hazırlık YALNIZCA istasyon düğmesinin menüsünden/panelinden
        prepDoner4ViaStation(h); h.run(200);
        assertEquals(1, activePrepCount(e));
        assertPrepSectionVisible(h, false);
        h.run(7000);
        assertEquals(4, e.getReadyDoner());
        assertTrue("saat gün içinde akar", e.getElapsed() > 5f);
        assertTrue("hazır stok istasyon etiketinde", UiHarness.label(h.stationTile(Station.DONER)).contains("hazır 4 porsiyon"));
        assertPrepSectionVisible(h, false);
    }

    @Test public void inDayPrepWorksViaTalkBackActionsAndPanelWithAnOrderPending() {
        UiHarness h = day();
        GameEngine e = h.engine();
        e.getSave().inventory.add("ayran", 10);
        Order o = spawn(h, list("ekmek_arasi"), list());
        h.selectOrder(o); h.run(100);
        // sipariş bekliyor: Döner makinesi düğmesinin Eylemler menüsünde hazırlık seçenekleri bulunur ve çalışır
        View tile = h.stationTile(Station.DONER);
        int id4 = UiBot.actionIdByLabel(tile, "4 porsiyon");
        assertTrue("gün içinde DONER eylem menüsünde hazırlık var", id4 >= 0);
        assertTrue(UiBot.actionIdByLabel(tile, "2 porsiyon") >= 0);
        assertTrue(tile.performAccessibilityAction(id4, null));
        h.run(200);
        assertEquals("hazırlık başladı", 1, activePrepCount(e));
        h.run(7000);
        assertEquals(4, e.getReadyDoner());
        // sos tezgahı: tepsi hazırlığı eylemi
        View sos = h.stationTile(Station.SOS);
        int tray = UiBot.actionIdByLabel(sos, "Salata ve sos tepsisi hazırla");
        assertTrue("SOS eylem menüsünde tepsi hazırlığı var", tray >= 0);
        assertTrue(sos.performAccessibilityAction(tray, null));
        h.run(7000);
        assertTrue("tepsi hazırlandı", e.getTrayTotal() > 0);
        assertNull("gün içinde hazırlık düğmesi yok", h.findLabel("Salata ve sos tepsisi hazırla"));
        // hazır stok, durum okumada ve istasyon etiketinde
        String status = new com.doner.oyunu.engine.Narrator(e).statusText();
        assertTrue(status, status.contains("Hazır: döner 4 porsiyon"));
        assertTrue(UiHarness.label(h.stationTile(Station.DONER)).contains("hazır 4 porsiyon"));
        assertTrue(UiHarness.label(h.stationTile(Station.SOS)).contains("hazır tepsi"));
    }

    @Test public void inDayDrinkPrepFromIcecekStationAndStockReadable() {
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        h.click("Yeni oyun"); h.settle();
        h.act.save.day = 2; // ayran menüde
        h.act.save.tutorialDone = true;
        com.doner.oyunu.engine.Shop.buyRecommended(h.act.save);
        h.startDay();
        GameEngine e = h.engine();
        e.setSpawning(false);
        assertTrue(e.menuHasDrinkPrep());
        assertNull(h.findLabel("Ayran doldur"));
        View tile = h.stationTile(Station.ICECEK);
        // sipariş yokken İçecek dolabı: tek seçenek (hazırlık) doğrudan uygulanır
        tile.performClick(); h.run(200);
        assertEquals("ayran doldurma başladı", 1, activePrepCount(e));
        assertFalse(UiBot.panelOpen(h));
        h.run(8000);
        assertTrue("hazır ayran var", e.getReadyDrinks() > 0);
        assertTrue(UiHarness.label(h.stationTile(Station.ICECEK)).contains("bardak ayran"));
        assertNull("hazır ayran göstergesi gün içinde yok", h.findText("Hazır ayran"));
    }

    @Test public void prepSkipOpensDayWithoutPrep() {
        UiHarness h = prep();
        h.click("Hazırlık yapmadan aç"); h.run(200);
        assertEquals(GameEngine.State.RUNNING, h.engine().getState());
        assertEquals(0, h.engine().getReadyDoner());
    }

    @Test public void trayButtonPreparesSuggestedTrayDirectly() {
        UiHarness h = prep();
        GameEngine e = h.engine();
        int dStock = e.getSave().inventory.get("domates");
        // tek seçenek: panel açılmadan doğrudan uygulanır
        h.click("Salata ve sos tepsisi hazırla"); h.run(100);
        assertFalse("tek eylemli hazırlık panel açmaz", UiBot.panelOpen(h));
        assertEquals(1, activePrepCount(e));
        assertEquals("malzeme başlarken düşer", dStock - 6, e.getSave().inventory.get("domates"));
        h.run(6000);
        assertEquals("1. günde ketçap yok: domates, marul, soğan", 18, e.getTrayTotal());
        assertNotNull(h.findText("Hazır salata ve sos tepsisi: 18 kullanım"));
    }

    @Test public void apprenticeWorkIsLabelledAndAnnouncedWhenSettingOn() { apprenticeCase(true); }

    @Test public void apprenticeWorkIsLabelledButSilentWhenSettingOff() { apprenticeCase(false); }

    private void apprenticeCase(boolean announce) {
        {
            AccessibilityManager am = (AccessibilityManager) RuntimeEnvironment.getApplication().getSystemService(Context.ACCESSIBILITY_SERVICE);
            ShadowAccessibilityManager sam = shadowOf(am);
            sam.setEnabled(true);
            sam.setTouchExplorationEnabled(true);
            UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
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
            h.run(15000); // motor ilerlesin: bayat olan başlangıç duyurusu atılır, bitiş duyurusu ("Çırak döner dilimledi") söylenir
            all = new ArrayList<>(sam.getSentAccessibilityEvents());
            for (AccessibilityEvent ev : all.subList(Math.min(base, all.size()), all.size())) {
                if (ev.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT && !ev.getText().isEmpty()
                        && ev.getText().get(0).toString().startsWith("Çırak")) spoke = true;
            }
            assertEquals("çırak duyurusu ayara uymalı (announce=" + announce + ")", announce, spoke);
            h.controller.pause().stop().destroy();
        }
    }

    // ------------------------------------------------------------------ sıradaki sipariş (v2.0: kalem/adım listesi yok)
    /**
     * v0.5'in kalem bazlı kartı (her kalem + her adım + paket satırı) v2.0'da kalktı: tek "Sıradaki sipariş" başlığı (kalemler
     * tek satır, istek) ve yalnızca ŞİMDİ yapılabilecekler. Aynı akış (ayran kalemi, döner boyu, kilitli paketleme) satırlarla.
     */
    @Test public void focusSectionShowsItemsRequestsAndOnlyReadyActions() {
        UiHarness h = day();
        GameEngine e = h.engine();
        e.getSave().inventory.add("ayran", 10);
        Order o = spawn(h, list("ekmek_arasi", "ayran"), list("soğansız"));
        assertNotNull(h.findText("İstek: soğansız"));
        assertNotNull("kalemler tek satırda", h.findText(o.itemsText()));
        assertNull("eski kalem satırı yok", itemRow(h, 1));
        String head = UiHarness.label(h.focusHeader());
        assertTrue(head, head.startsWith("Sipariş 1, " + o.customer.name + ", " + o.itemsText()));
        assertTrue(head, head.contains("soğansız"));
        View ayran = UiBot.row(h, "Ayran: Ayranı doldur");
        assertNotNull("ayran kaleminin işi: " + h.clickables(), ayran);
        assertNull("henüz açılmamış adım listede yok", UiBot.row(h, "Ekmek arası döner: Malzemeleri ekle"));
        assertNull("paketleme henüz yok", UiBot.row(h, "Siparişi paketle"));
        // tek dokunuş: ayran kalemi başlar (kalem seçmeye gerek yok)
        ayran.performClick(); h.run(200);
        assertTrue(o.getLine(1).isStepRunning(0));
        assertNotNull("süren iş satırı", UiBot.row(h, "Ayran: Ayranı doldur sürüyor"));
        h.run(3000);
        assertTrue(o.getLine(1).isDone());
        assertNull("biten iş listeden kalkar", UiBot.row(h, "Ayran: Ayranı doldur"));
        // döner: satır menü açar (Gerçekçi: boy seç)
        View doner = UiBot.row(h, "Ekmek arası döner: Döneri dilimle");
        assertNotNull(h.clickables().toString(), doner);
        assertTrue(UiHarness.label(doner), UiHarness.label(doner).contains("boy seç"));
        doner.performClick(); h.run(100);
        assertTrue("döner makinesi boy seçenekleri paneli", UiBot.panelOpen(h));
        assertEquals(Station.DONER.id, UiBot.panelOf(h).stationId);
        h.click("Tam ekmek"); h.run(200);
        assertFalse(UiBot.panelOpen(h));
        assertTrue(o.getLine(0).isStepRunning(1));
        h.run(5000);
        // paketleme hâlâ kilitli: Paketleme istasyonu nedenini söyler, yıldız cezası yok
        double stars = e.getStars();
        int wrong = e.getSummary().wrongTaps;
        h.stationTile(Station.PAKET).performClick(); h.run(100);
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
        h.selectOrder(o); h.run(100);
        h.stationTile(Station.SOS).performClick(); h.run(100);
        assertFalse(UiBot.panelOpen(h));
        assertEquals(0, e.getSummary().wrongTaps);
        assertEquals(3.0, e.getStars(), 1e-9);
        TextView notice = (TextView) h.field(h.screen(), "noticeTv");
        assertEquals(View.VISIBLE, notice.getVisibility());
        assertTrue(notice.getText().toString(), notice.getText().toString().contains("Malzeme eklemeden önce"));
    }

    // ------------------------------------------------------------------ malzeme ekleme (seçenek paneli)
    @Test public void toppingMenuAddsOneByOneBolUndoReadFinishPerfect() {
        UiHarness h = day();
        GameEngine e = h.engine();
        Order o = spawn(h, list("ekmek_arasi"), list("soğansız"));
        toPanel(h, o);
        MenuPanel p = UiBot.panelOf(h);
        float el = e.getElapsed();
        h.run(1000);
        assertTrue("seçenek paneli açıkken gün saati akar (eklemeler zamanlı iştir)", e.getElapsed() > el + 0.5f);
        // müşteri isteği panelde; başlık ve gruplar
        assertNotNull(h.findText("Müşteri isteği: soğansız"));
        assertTrue(p.titleView.isAccessibilityHeading());
        assertNotNull("Malzeme ekle grubu başlığı", h.findText("Malzeme ekle"));
        assertNotNull("Oku grubu başlığı", h.findText("Oku"));
        assertNotNull(h.findText("Henüz malzeme eklenmedi."));
        OrderLine line = o.getLine(0);
        // ilk ekleme: domates
        pick(h, "Domates ekle");
        assertEquals(1, line.addedCount("domates"));
        assertNotNull("durum satırı eklenen malzemeyi söyler", h.findText("Eklenenler: domates."));
        assertTrue("panel açık kalır (art arda ekleme)", sosPanelOpen(h));
        assertNull("eklenen malzemenin seçeneği artık 'bol' der", UiBot.row(h, "Domates ekle"));
        assertNotNull(UiBot.row(h, "Domates bol ekle"));
        // bol
        pick(h, "Domates bol ekle");
        assertEquals(2, line.addedCount("domates"));
        assertNotNull(h.findText("Eklenenler: domates bol."));
        // üçüncü: azami (kilitli) -> reddedilir, sayı artmaz
        View third = UiBot.row(h, "Domates");
        assertTrue(UiHarness.label(third), UiHarness.label(third).contains("En fazla bol"));
        third.performClick(); h.run(100);
        assertEquals(2, line.addedCount("domates"));
        // geri al
        pick(h, "Son ekleneni geri al");
        assertEquals(1, line.addedCount("domates"));
        // okuma seçenekleri: durum değişmez, sonuç panelde görünür
        h.click("Hedefi oku"); h.run(50);
        View target = h.findText("Hedef, Ekmek arası döner");
        assertNotNull("hedef panelde yazılı", target);
        assertTrue(UiHarness.label(target).contains("Müşteri isteği: soğansız"));
        assertFalse(UiHarness.label(target).contains("Soğan"));
        h.click("Eklenenleri oku"); h.run(50);
        assertNotNull(h.findText("Eklenenler: domates."));
        assertEquals(1, line.addedCount("domates"));
        // hedefin kalanı: marul, sonra bitir
        pick(h, "Marul ekle");
        assertTrue(sosPanelOpen(h));
        h.click("Malzemeleri bitir"); h.run(200);
        assertFalse("bitirince panel kapanır", UiBot.panelOpen(h));
        assertTrue(line.isStepDone(line.product.toppingStepIndex()));
        assertTrue(line.isDone());
        // kalem kartı eklenenleri gösterir
        assertNotNull(h.findText("Eklenen: domates ve marul"));
        long t = 0;
        while (e.getSummary().delivered == 0 && t < 60_000) { UiBot.step(h); h.run(100); t += 100; }
        assertEquals(1, e.getSummary().delivered);
        assertEquals("doğru malzemeler: kusursuz", 1, e.getSummary().perfect);
        assertEquals(0, e.getSummary().mistakes);
        TextView notice = (TextView) h.field(h.screen(), "noticeTv");
        assertTrue(notice.getText().toString(), notice.getText().toString().contains("Malzemeler tam"));
    }

    @Test public void wrongSelectionIsReportedAtDelivery() {
        UiHarness h = day();
        GameEngine e = h.engine();
        Order o = spawn(h, list("ekmek_arasi"), list("soğansız"));
        toPanel(h, o);
        // soğansız istendi ama soğan da eklenir
        pick(h, "Domates ekle");
        pick(h, "Marul ekle");
        pick(h, "Soğan ekle");
        h.click("Malzemeleri bitir"); h.run(200);
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
        View dom = UiBot.row(h, "Domates ekle");
        assertTrue(UiHarness.label(dom), UiHarness.label(dom).contains("stokta yok"));
        assertNull("henüz denenmedi: acil al yok", h.findLabel("Acil al"));
        dom.performClick(); h.run(100);
        assertEquals("stok yoksa eklenmez", 0, o.getLine(0).addedCount("domates"));
        View em = h.findLabel("Acil al: Domates");
        assertNotNull("panelde acil al seçeneği çıkmalı", em);
        int money = e.getMoney();
        em.performClick(); h.run(100);
        assertTrue(e.getMoney() < money);
        assertNull(h.findLabel("Acil al"));
        View dom2 = UiBot.row(h, "Domates ekle");
        assertFalse(UiHarness.label(dom2), UiHarness.label(dom2).contains("stokta yok"));
        pick(h, "Domates ekle");
        assertEquals(1, o.getLine(0).addedCount("domates"));
    }

    /** TalkBack açıkken de düğmenin varsayılan (çift) dokunuşu paneli açar; eylemler ek yoldur. */
    @Test public void talkBackOnDefaultActivationOpensPanelAndActionsStillWork() {
        AccessibilityManager am = (AccessibilityManager) RuntimeEnvironment.getApplication().getSystemService(Context.ACCESSIBILITY_SERVICE);
        shadowOf(am).setEnabled(true);
        shadowOf(am).setTouchExplorationEnabled(true);
        UiHarness h = day();
        h.act.save.tutorialDone = true;
        Order o = spawn(h, list("ekmek_arasi"), list());
        toPanel(h, o);
        assertTrue(UiBot.panelOf(h).titleView.isAccessibilityHeading());
        h.click("Kapat"); h.run(100);
        assertFalse(UiBot.panelOpen(h));
        View sos = h.stationTile(Station.SOS);
        int id = UiBot.actionIdByLabel(sos, "Domates ekle");
        assertTrue(id >= 0);
        assertTrue(sos.performAccessibilityAction(id, null));
        h.run(900);
        assertEquals(1, o.getLine(0).addedCount("domates"));
        assertFalse("eylem panel açmaz", UiBot.panelOpen(h));
        sos.performClick(); h.run(100);
        assertTrue("çift dokunuş paneli açar", sosPanelOpen(h));
    }

    @Test public void closePanelKeepsStepPendingAndReopens() {
        UiHarness h = day();
        Order o = spawn(h, list("ekmek_arasi"), list());
        toPanel(h, o);
        h.click("Kapat"); h.run(100);
        assertFalse(UiBot.panelOpen(h));
        assertFalse(o.isRunning());
        assertEquals(0, o.getLine(0).addedTotal());
        h.stationTile(Station.SOS).performClick(); h.run(100);
        assertTrue(sosPanelOpen(h));
        // geri tuşu da kapatır
        h.act.onBackPressed(); h.run(100);
        assertFalse(UiBot.panelOpen(h));
    }

    @Test public void donerMenuChoosesSizeAndWrongSizeIsAMistake() {
        UiHarness h = day();
        GameEngine e = h.engine();
        Order o = e.spawnOrder(list("ekmek_arasi"), list(), Arrays.asList(com.doner.oyunu.engine.PortionSize.HALF));
        h.run(300);
        assertNotNull("kartta yarım ekmek yazar", h.findText("Ekmek arası döner (yarım ekmek)"));
        h.selectOrder(o); h.run(100);
        long t = 0;
        while (!o.getLine(0).isStepDone(0) && t < 30_000) { // ekmek ısıt
            if (o.getLine(0).isStepOpen(0)) h.stationTile(Station.EKMEK).performClick();
            h.run(100); t += 100;
        }
        h.stationTile(Station.DONER).performClick(); h.run(100);
        assertTrue(UiBot.panelOpen(h));
        assertNotNull("Boy seç grubu başlığı", h.findText("Boy seç"));
        assertNotNull(UiBot.row(h, "Yarım ekmek"));
        assertNotNull(UiBot.row(h, "Tam ekmek"));
        assertNotNull(UiBot.row(h, "Duble"));
        // kilitli boy (duble 4. günde açılır): nedeni etikette, seçilirse uygulanmaz
        String dbl = UiHarness.label(UiBot.row(h, "Duble"));
        assertTrue(dbl, dbl.contains("4. günde açılır"));
        UiBot.row(h, "Duble").performClick(); h.run(100);
        assertTrue("kilitli boy panel kapatmaz", UiBot.panelOpen(h));
        assertNull(o.getLine(0).getChosenSize());
        // yanlış boy: tam ekmek (müşteri yarım istedi)
        UiBot.row(h, "Tam ekmek").performClick(); h.run(200);
        assertFalse(UiBot.panelOpen(h));
        assertEquals(com.doner.oyunu.engine.PortionSize.FULL, o.getLine(0).getChosenSize());
        assertTrue(o.getLine(0).sizeMismatch());
    }

    // ------------------------------------------------------------------ ses
    @Test public void stationSoundRunsForTheStepPausesWithGameAndStopsWhenDone() {
        UiHarness h = day();
        GameEngine e = h.engine();
        com.doner.oyunu.audio.SoundManager snd = h.act.sound;
        Order o = spawn(h, list("ekmek_arasi"), list());
        h.selectOrder(o); h.run(100);
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
        // hazırlık da istasyon sesi kullanır (gün içinde istasyon düğmesinden)
        prepDoner4ViaStation(h); h.run(200);
        assertEquals(1, snd.activeLoopCount());
        h.controller.pause(); h.idle(200);
        assertEquals("arka planda sesler durur", 0, snd.activeLoopCount());
        h.controller.resume(); h.idle(200);
        h.click("Devam et"); h.run(100);
        assertEquals(1, snd.activeLoopCount());
        h.run(8000);
        assertEquals(0, snd.activeLoopCount());
    }

    @Test public void toppingAddIsAShortOneShotNotALoop() {
        UiHarness h = day();
        com.doner.oyunu.audio.SoundManager snd = h.act.sound;
        Order o = spawn(h, list("ekmek_arasi"), list());
        toPanel(h, o);
        assertEquals(0, snd.activeLoopCount());
        UiBot.row(h, "Domates ekle").performClick(); h.run(100);
        assertEquals("ekleme sürerken istasyon döngü sesi kurulmaz (onStepStarted yok)", 0, snd.activeLoopCount());
        h.run(900);
        assertEquals(1, o.getLine(0).addedCount("domates"));
        assertEquals(0, snd.activeLoopCount());
        h.click("Malzemeleri bitir"); h.run(200);
        assertEquals(0, snd.activeLoopCount());
    }

    @Test public void apprenticeStepsPlayQuietCenteredSound() {
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
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
        prepDoner4(h);
        long t = 0;
        while (e.getReadyDoner() == 0 && t < 20_000) { h.run(100); t += 100; }
        h.click("Salata ve sos tepsisi hazırla"); h.run(100);
        t = 0;
        while (e.getTrayTotal() == 0 && t < 20_000) { h.run(100); t += 100; }
        assertEquals("1. günde ketçap yok: domates, marul, soğan", 18, e.getTrayTotal());
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
        assertTrue("seçenek paneli kullanıldı (boy seçimi, malzeme ekleme)", panels > 0);
        assertNotNull(h.findText("Hazırlık"));
        assertNotNull(h.findText("Teslim edilen sipariş"));
        assertTrue(h.act.save.day == 2);
    }

    // ------------------------------------------------------------------ tam gün: hazırlık -> boy -> tek tek malzeme -> bitir -> paketle -> teslim
    /** Bir gün (4. gün: yarım/tam/duble boylar açık, ketçap/mayonez açık) iki yolla oynanır; sonuçlar aynı kalitede olmalı. */
    private void fullDayWithSizesAndToppings(boolean viaActions) {
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        h.click("Yeni oyun"); h.settle();
        h.act.save.day = 4;
        h.act.save.money = 30_000;
        h.act.save.tutorialDone = true;
        com.doner.oyunu.engine.Shop.buyRecommended(h.act.save);
        h.click("Güne başla"); h.settle();
        GameEngine e = h.engine();
        assertEquals(GameEngine.State.PREP, e.getState());
        // --- hazırlık: döner dilimle (4 porsiyon) ve tepsi
        if (viaActions) {
            View d = h.findLabel("Döner dilimle");
            int id = UiBot.actionIdByLabel(d, "4 porsiyon");
            assertTrue(id >= 0);
            assertTrue(d.performAccessibilityAction(id, null));
        } else {
            prepDoner4(h);
        }
        assertFalse(UiBot.panelOpen(h));
        long t = 0;
        while (e.getReadyDoner() == 0 && t < 20_000) { h.run(100); t += 100; }
        assertEquals(4, e.getReadyDoner());
        if (viaActions) {
            View tr = h.findLabel("Salata ve sos tepsisi hazırla");
            assertTrue(tr.performAccessibilityAction(UiBot.actionIdByLabel(tr, "Salata ve sos tepsisi hazırla"), null));
        } else {
            h.click("Salata ve sos tepsisi hazırla");
        }
        h.run(100);
        t = 0;
        while (e.getTrayTotal() == 0 && t < 20_000) { h.run(100); t += 100; }
        assertTrue("tepsi hazır", e.getTrayTotal() > 0);
        h.click("Dükkânı aç"); h.run(200);
        e.setSpawning(true);
        // --- gün
        int panels = 0, adds = 0, sizes = 0;
        boolean was = false;
        UiBot.viaActions = viaActions;
        try {
            t = 0;
            while (h.screen() instanceof GameScreen && t < 500_000) {
                boolean now = UiBot.panelOpen(h);
                if (now && !was) panels++;
                was = now;
                UiBot.step(h); h.run(100); t += 100;
            }
        } finally { UiBot.viaActions = false; }
        h.run(20_000);
        assertTrue("özet ekranı", h.screen() instanceof SummaryScreen);
        com.doner.oyunu.engine.DaySummary sum = e.getSummary();
        assertTrue("sipariş teslim edildi", sum.delivered >= 3);
        assertEquals("doğru boy ve doğru malzemeler: hatasız", 0, sum.mistakes);
        assertEquals(0, sum.sizeMistakes);
        assertTrue("kusursuz siparişler", sum.perfect >= 3);
        if (viaActions) assertEquals("eylem yolu panel açmaz", 0, panels);
        else assertTrue("panel yolu panel açar (boy seçimi, malzeme ekleme)", panels > 0);
        assertNotNull(h.findText("Teslim edilen sipariş"));
        assertTrue(h.act.save.day >= 5);
    }

    @Test public void fullDayPrepSizeToppingsFinishPackDeliverViaPanel() { fullDayWithSizesAndToppings(false); }

    @Test public void fullDayPrepSizeToppingsFinishPackDeliverViaTalkBackActions() { fullDayWithSizesAndToppings(true); }

    @Test public void settingsHasApprenticeAnnounceSwitchThatPersists() {
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
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
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        h.click("Yeni oyun"); h.settle();
        h.click("Markete git"); h.settle();
        h.click("Mutfak aletleri"); h.settle();
        View info = h.findText("Çırak, seviye 0");
        assertNotNull(info);
        String d = UiHarness.label(info);
        assertTrue(d, d.contains("Seviye 1:") && d.contains("Seviye 2:") && d.contains("Seviye 3:"));
        assertTrue(d, d.contains("paketler") && d.contains("hazırlık") || d.contains("tepsi"));
        assertTrue(d, d.contains("Boy ve malzemeyi sen seçersin"));
    }
}
