package com.doner.oyunu.ui;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import com.doner.oyunu.audio.SoundManager;
import com.doner.oyunu.engine.ActiveStep;
import com.doner.oyunu.engine.Catalog;
import com.doner.oyunu.engine.DaySummary;
import com.doner.oyunu.engine.FlowMode;
import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Ingredient;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.ReadyAction;
import com.doner.oyunu.engine.Station;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAccessibilityManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * v2.0 sade oyun ekranı (docs/design_v20.md "UI kararları"): tek "Sıradaki sipariş" bölümü, şimdi yapılabilecekler,
 * tek satırlık sıra özeti, eller ve istasyon doluluğu, odak kararlılığı, pasif istasyon ses döngüleri, çırak olayları,
 * içecek ses havuzu, sade düzenle tam gün (her akış, süreli/süresiz).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class ScreenV20UiTest {

    /** Önceki (v0.9) ekranda aynı 10 siparişle ölçülen tıklanabilir öğe sayısı (kart başlığı + kalemler + paket satırları). */
    static final int BEFORE_CLICKABLES_10_ORDERS = 46;

    @After public void reset() { GameScreen.testSeed = null; UiBot.viaActions = false; }

    /** Çalınan sesleri kaydeden çıkış (sönümleme dahil). */
    static final class Rec implements SoundManager.Sink {
        final List<String> names = new ArrayList<>();
        final List<Integer> loops = new ArrayList<>();
        final List<Integer> stopped = new ArrayList<>();
        int next = 1;

        @Override public int start(String name, float l, float r, float rate, int loop) {
            names.add(name);
            loops.add(loop);
            return next++;
        }

        @Override public void stop(int stream) { stopped.add(stream); }

        int count(String prefix, int loop) {
            int n = 0;
            for (int i = 0; i < names.size(); i++) if (names.get(i).startsWith(prefix) && loops.get(i) == loop) n++;
            return n;
        }

        int indexOf(String prefix, int from) {
            for (int i = Math.max(0, from); i < names.size(); i++) if (names.get(i).startsWith(prefix)) return i;
            return -1;
        }
    }

    private static UiHarness day(FlowMode m, int dayNo, boolean timed) {
        UiHarness h = new UiHarness(false, m);
        h.act.save.tutorialDone = true;
        h.act.save.timePressure = timed;
        h.click("Yeni oyun"); h.settle();
        h.act.save.day = dayNo;
        h.act.save.money = 30_000;
        for (Ingredient i : Catalog.ingredients()) h.act.save.inventory.set(i.id, 30);
        h.startDay();
        h.engine().setSpawning(false);
        return h;
    }

    private static Order spawn(UiHarness h, String... products) {
        Order o = h.engine().spawnOrder(Arrays.asList(products), Collections.<String>emptyList());
        assertNotNull(o);
        h.run(300);
        return o;
    }

    private static void audit(UiHarness h, String where) {
        h.idle(100);
        A11yAudit a = A11yAudit.run(h, where);
        assertTrue("Erişilebilirlik hataları:\n" + a.report(), a.errors.isEmpty());
    }

    private static TextView notice(UiHarness h) { return (TextView) h.field(h.screen(), "noticeTv"); }

    private static ShadowAccessibilityManager talkBack() {
        AccessibilityManager am = (AccessibilityManager) RuntimeEnvironment.getApplication().getSystemService(Context.ACCESSIBILITY_SERVICE);
        ShadowAccessibilityManager sam = shadowOf(am);
        sam.setEnabled(true);
        sam.setTouchExplorationEnabled(true);
        return sam;
    }

    // ------------------------------------------------------------------ 1. tek "Sıradaki sipariş" bölümü
    @Test public void tenOrdersShowOneFocusSectionAndFewLabelledClickables() {
        UiHarness h = day(FlowMode.DENGELI, 3, true);
        GameEngine e = h.engine();
        String[][] mix = {{"ekmek_arasi", "ayran"}, {"ekmek_arasi"}, {"ayran"}, {"ekmek_arasi", "ekmek_arasi"}, {"ekmek_arasi+patates"},
                {"ekmek_arasi", "ayran"}, {"ekmek_arasi"}, {"ayran", "ekmek_arasi"}, {"ekmek_arasi"}, {"ekmek_arasi", "ayran"}};
        for (String[] m : mix) assertNotNull(e.spawnOrder(Arrays.asList(m), Collections.<String>emptyList()));
        h.run(2000);
        assertEquals(10, e.getOrders().size());
        List<View> clk = h.visibleClickables();
        int focusStops = 0;
        for (View v : UiHarness.visibleViews(h.root())) {
            if ((v.isFocusable() || v.isClickable()) && v.isImportantForAccessibility() && !(v instanceof android.widget.ScrollView)) focusStops++;
        }
        System.out.println("V20-SAYIM 10 sipariş: tıklanabilir önce=" + BEFORE_CLICKABLES_10_ORDERS + " sonra=" + clk.size() + " odak noktası=" + focusStops);
        assertTrue("10 siparişle tıklanabilir öğe ≤ " + A11yAudit.MAX_GAME_CLICKABLES + ": " + h.clickables(), clk.size() <= A11yAudit.MAX_GAME_CLICKABLES);
        assertTrue("önceki ekrandan belirgin az", clk.size() * 2 <= BEFORE_CLICKABLES_10_ORDERS);
        int orderButtons = 0;
        for (View v : clk) {
            String l = UiHarness.label(v);
            assertFalse("etiketsiz tıklanabilir", l.trim().isEmpty());
            if (l.startsWith("Sipariş ")) orderButtons++;
        }
        assertEquals("ekranda tek sipariş düğmesi (sıradaki)", 1, orderButtons);
        Order focus = e.getFocusOrder();
        assertSame(h.focusHeader(), h.orderCard(focus));
        String q = UiHarness.label(h.access().queue());
        assertTrue(q, q.startsWith("Sırada 9 sipariş daha"));
        assertTrue("süreli: en acil öne çıkar: " + q, q.contains("en acili") && q.contains("sabır"));
        assertTrue("eylem satırı ≤ " + GameScreen.MAX_ACTIONS, h.access().visibleActions() <= GameScreen.MAX_ACTIONS);
        A11yAudit a = A11yAudit.run(h, "10 sipariş");
        assertTrue(a.report(), a.errors.isEmpty());
        assertEquals("v2.0 kuralları denetlendi", 1, a.checkedV20);
        // sipariş listesi: tüm siparişler, her biri tek düğme; seçince liste kapanır, odak başlığa döner
        h.access().queue().performClick(); h.idle(50);
        assertTrue(h.access().ordersPanelOpen());
        audit(h, "Sipariş listesi");
        Order last = e.getOrders().get(9);
        for (Order o : e.getOrders()) assertNotNull("listede " + o.id, h.access().orderButton(o.id));
        h.access().orderButton(last.id).performClick(); h.run(100);
        assertFalse(h.access().ordersPanelOpen());
        assertEquals(last.id, e.getSelectedOrderId());
        assertEquals(last.id, e.getFocusOrder().id);
        assertTrue(UiHarness.label(h.focusHeader()).startsWith("Sipariş " + last.getPosition() + ", " + last.customer.name));
    }

    @Test public void untimedQueueAndHeaderNeverMentionPatience() {
        UiHarness h = day(FlowMode.DENGELI, 3, false);
        for (int i = 0; i < 4; i++) spawn(h, "ekmek_arasi");
        String q = UiHarness.label(h.access().queue());
        assertTrue(q, q.startsWith("Sırada 3 sipariş daha"));
        assertFalse(q, q.contains("sabır"));
        assertFalse(UiHarness.label(h.focusHeader()).contains("sabır"));
        audit(h, "Süresiz sıra");
    }

    @Test public void nextOrderButtonSelectsSuggestedThenCycles() {
        UiHarness h = day(FlowMode.DENGELI, 2, true);
        GameEngine e = h.engine();
        Order a = spawn(h, "ekmek_arasi");
        Order b = spawn(h, "ayran");
        h.run(2000); // otomatik seçim (önerilen)
        assertTrue("seçim yoksa kendiliğinden seçilir", e.getSelectedOrderId() >= 0);
        for (int k = 0; k < 3; k++) {
            int sel0 = e.getSelectedOrderId();
            Order sug = e.getSuggestedOrder();
            h.click("Sonraki sipariş");
            if (sug.id != sel0) assertEquals("önerilen seçilir", sug.id, e.getSelectedOrderId());
            else assertNotEquals("zaten önerilendeyse sıradakine geçer", sel0, e.getSelectedOrderId());
            assertTrue(e.getSelectedOrderId() == a.id || e.getSelectedOrderId() == b.id);
        }
    }

    // ------------------------------------------------------------------ 2. odak kararlılığı
    private static Set<View> identities(View root) {
        Set<View> s = Collections.newSetFromMap(new IdentityHashMap<View, Boolean>());
        s.addAll(UiHarness.all(root));
        return s;
    }

    private static int focusEvents(ShadowAccessibilityManager sam, int from) {
        int n = 0;
        List<AccessibilityEvent> all = new ArrayList<>(sam.getSentAccessibilityEvents());
        for (AccessibilityEvent ev : all.subList(Math.min(from, all.size()), all.size())) {
            if (ev.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) n++;
        }
        return n;
    }

    /** Son erişilebilirlik odağı olayının (TYPE_VIEW_ACCESSIBILITY_FOCUSED) metni; yoksa null. */
    private static String lastFocusText(ShadowAccessibilityManager sam, int from) {
        String t = null;
        List<AccessibilityEvent> all = new ArrayList<>(sam.getSentAccessibilityEvents());
        for (AccessibilityEvent ev : all.subList(Math.min(from, all.size()), all.size())) {
            if (ev.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) t = String.valueOf(ev.getContentDescription());
        }
        return t;
    }

    /**
     * Odak kararlılığı. Not: Robolectric her yerleşim geçişinde erişilebilirlik odağı bayrağını siler (değişmeyen menü
     * ekranında da); bu yüzden odak, uygulamanın gönderdiği odak olaylarıyla ve ekranın izlediği odak yeriyle denetlenir:
     * tick'lerde HİÇ odak olayı yok (odak kaydırılmaz), görünümler aynı örnekler, etiketler yerinde güncellenir; işlem
     * sonrası tek bir odak taşıması ilk satıra; gizlenen satırdaki odak ilk satıra kurtarılır.
     */
    @Test public void focusStaysPutAcrossTicksLabelsUpdateInPlaceAndActionReturnsFocus() {
        ShadowAccessibilityManager sam = talkBack();
        UiHarness h = day(FlowMode.DENGELI, 1, true);
        Order o = spawn(h, "ekmek_arasi");
        h.selectOrder(o); h.run(300);
        GameScreen.FocusHeaderAccess fa = h.access();
        View header = fa.header(), hands = fa.hands();
        View[] rows = new View[GameScreen.MAX_ACTIONS];
        for (int i = 0; i < rows.length; i++) rows[i] = fa.actionRow(i);
        assertEquals("Elin boş", UiHarness.label(hands));
        assertTrue(UiHarness.label(rows[0]), UiHarness.label(rows[0]).startsWith("Ekmeği ısıt"));
        assertTrue(UiHarness.label(rows[1]), UiHarness.label(rows[1]).startsWith("Döneri dilimle"));

        // odak başlıkta; 3 sn tick (sabır, saat) -> odak taşınmaz, görünümler yeniden yaratılmaz, etiket sabit
        assertTrue(header.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
        assertSame(header, fa.a11yFocus());
        Set<View> before = identities(h.root());
        int ev0 = sam.getSentAccessibilityEvents().size();
        String headLabel = UiHarness.label(header);
        h.run(3000);
        assertEquals("tick sırasında odak taşınmaz", 0, focusEvents(sam, ev0));
        assertSame("odak yeri değişmedi", header, fa.a11yFocus());
        assertEquals("etiket saniye saniye değişmez", headLabel, UiHarness.label(header));
        assertEquals("görünüm örnekleri aynı (yeniden kurulmaz)", before, identities(h.root()));

        // ikinci satırdan iş (dilimle, aktif): odak ilk satıra TEK kez taşınır; eller etiketi yerinde güncellenir
        assertTrue(rows[1].performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
        int ev1 = sam.getSentAccessibilityEvents().size();
        rows[1].performClick(); h.run(150);
        assertTrue(o.getLine(0).isStepRunning(1));
        assertSame(hands, fa.hands());
        assertTrue(UiHarness.label(hands), UiHarness.label(hands).startsWith("Elin dolu: Döneri dilimle"));
        assertEquals("işten sonra tek odak taşıması", 1, focusEvents(sam, ev1));
        assertEquals(UiHarness.label(rows[0]), lastFocusText(sam, ev1));
        assertSame("odak ilk satırda", rows[0], fa.a11yFocus());
        assertTrue(UiHarness.label(rows[0]), UiHarness.label(rows[0]).contains("elin dolu"));
        assertTrue(UiHarness.label(rows[1]), UiHarness.label(rows[1]).startsWith("Döneri dilimle sürüyor"));
        int ev2 = sam.getSentAccessibilityEvents().size();
        h.run(1000);
        assertEquals("kalıcı: tick'te yeniden odak yok", 0, focusEvents(sam, ev2));
        for (int i = 0; i < rows.length; i++) assertSame("satır havuzu yeniden kullanılır", rows[i], fa.actionRow(i));

        // odaktaki satır gizlenince (iş bitti, liste kısaldı) odak kaybolmaz: ilk satıra geçer
        assertTrue(rows[1].performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
        assertSame(rows[1], fa.a11yFocus());
        int ev3 = sam.getSentAccessibilityEvents().size();
        long t = 0;
        while (o.getLine(0).isStepRunning(1) && t < 10_000) { h.run(100); t += 100; }
        h.run(200);
        assertEquals(View.GONE, rows[1].getVisibility());
        assertEquals("gizlenen satırdan odak ilk satıra: " + lastFocusText(sam, ev3), UiHarness.label(rows[0]), lastFocusText(sam, ev3));
        assertSame(rows[0], fa.a11yFocus());
        assertEquals("Elin boş", UiHarness.label(hands));
        assertEquals(before, identities(h.root()));
        // odak bölüm dışına (istasyon) geçince ekran onu bölüm içinde saymaz; tick'ler yine odak taşımaz
        assertTrue(h.stationTile(Station.DONER).performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
        assertNull(fa.a11yFocus());
        int ev4 = sam.getSentAccessibilityEvents().size();
        h.run(1500);
        assertEquals(0, focusEvents(sam, ev4));
        audit(h, "Odak kararlılığı");
    }

    @Test public void deliveringFromTheActionRowReturnsFocusToTheOrderHeader() {
        talkBack();
        UiHarness h = day(FlowMode.RAHAT, 2, true);
        GameEngine e = h.engine();
        Order a = spawn(h, "ayran");
        spawn(h, "ayran");
        h.selectOrder(a); h.run(100);
        long t = 0;
        while (!a.isReady() && t < 30_000) { UiBot.stepViaRows(h); h.run(100); t += 100; }
        assertTrue(a.isReady());
        if (e.getFocusOrder().id != a.id) h.selectOrder(a);
        h.run(100);
        assertEquals(ReadyAction.Kind.DELIVER, h.access().action(0).kind);
        assertEquals("Teslim et", UiHarness.label(h.access().actionRow(0)));
        h.access().actionRow(0).performClick(); h.run(200);
        assertNull(e.getOrder(a.id));
        assertSame("teslimden sonra odak sıradaki sipariş başlığında", h.access().header(), h.access().a11yFocus());
    }

    // ------------------------------------------------------------------ 3. eller, istasyon doluluğu, patates + iki ekmek
    @Test public void friesAndTwoBreadsFromTheUiShowHandsAndFullnessWithOneLoopPerStation() {
        UiHarness h = day(FlowMode.DENGELI, 3, true);
        GameEngine e = h.engine();
        Rec rec = new Rec();
        h.act.sound.setSinkForTest(rec);
        Order o1 = spawn(h, "ekmek_arasi+patates");
        Order o2 = spawn(h, "ekmek_arasi");
        Order o3 = spawn(h, "ekmek_arasi");
        h.selectOrder(o1); h.run(100);
        View fries = UiBot.row(h, "Patates kızart");
        assertNotNull(h.clickables().toString(), fries);
        assertTrue("pasif iş istasyonla söylenir", UiHarness.label(fries).startsWith("Patates kızart, Ocak"));
        fries.performClick(); h.run(100);
        assertTrue("koyarken eller dolu", UiHarness.label(h.access().hands()).startsWith("Elin dolu: Patates kızart"));
        assertTrue(UiHarness.label(h.stationTile(Station.IZGARA)), UiHarness.label(h.stationTile(Station.IZGARA)).startsWith("Ocak 1/2"));
        // koyma bitmeden ikinci iş: "Elin dolu" (hata sesi + duyuru + titreşim)
        int err0 = rec.count("sfx_error", 0);
        UiBot.row(h, "Ekmeği ısıt").performClick(); h.run(100);
        assertTrue(notice(h).getText().toString(), notice(h).getText().toString().startsWith("Elin dolu"));
        assertTrue("hata sesi", rec.count("sfx_error", 0) > err0);
        h.run(700);
        assertTrue("koyma bitti: eller boş, patates pişiyor", UiHarness.label(h.access().hands()).startsWith("Elin boş"));
        assertTrue(UiHarness.label(h.access().hands()).contains("Ocak 1/2"));
        assertTrue("serbest ipucu (hafif tık)", rec.count("sfx_tick", 0) >= 1);
        // ekmek 1 (aynı sipariş) ve ekmek 2 (başka sipariş)
        UiBot.row(h, "Ekmeği ısıt").performClick(); h.run(800);
        assertTrue(UiHarness.label(h.stationTile(Station.EKMEK)), UiHarness.label(h.stationTile(Station.EKMEK)).startsWith("Ekmek ısıtıcı 1/2"));
        h.selectOrder(o2); h.run(100);
        UiBot.row(h, "Ekmeği ısıt").performClick(); h.run(800);
        String ek = UiHarness.label(h.stationTile(Station.EKMEK));
        assertTrue(ek, ek.startsWith("Ekmek ısıtıcı 2/2, dolu"));
        assertEquals(2, e.getStationUsed(Station.EKMEK));
        assertEquals(1, e.getStationUsed(Station.IZGARA));
        // üçüncü ekmek: satır "dolu" der, dokununca "Ekmek ısıtıcı dolu (2/2)"
        h.selectOrder(o3); h.run(100);
        View third = UiBot.row(h, "Ekmeği ısıt");
        assertTrue(UiHarness.label(third), UiHarness.label(third).contains("Ekmek ısıtıcı dolu (2/2)"));
        int err1 = rec.count("sfx_error", 0);
        third.performClick(); h.run(100);
        assertTrue(notice(h).getText().toString(), notice(h).getText().toString().startsWith("Ekmek ısıtıcı dolu (2/2)"));
        assertTrue(rec.count("sfx_error", 0) > err1);
        assertEquals(2, e.getStationUsed(Station.EKMEK));
        // ses: aynı ısıtıcıda iki ekmek TEK döngü; ocak ayrı döngü; oyuncu başka işe geçse de sürer
        Set<String> keys = h.access().stationLoopKeys();
        assertEquals(keys.toString(), new java.util.HashSet<>(Arrays.asList(
                GameScreen.stationLoopKey(Station.EKMEK, "sfx_bread_warm"), GameScreen.stationLoopKey(Station.IZGARA, "sfx_fry"))), keys);
        assertEquals("iki ekmek için tek ısıtıcı döngüsü", 1, rec.count("sfx_bread_warm", -1));
        assertEquals(1, rec.count("sfx_fry", -1));
        assertEquals(2, h.act.sound.activeLoopCount());
        audit(h, "İki ekmek + patates");
        long t = 0;
        while ((e.getStationUsed(Station.EKMEK) > 0 || e.getStationUsed(Station.IZGARA) > 0) && t < 20_000) { h.run(100); t += 100; }
        h.run(200);
        assertTrue("istasyonlar boşalınca döngüler durur", h.access().stationLoopKeys().isEmpty());
        assertEquals(0, h.act.sound.activeLoopCount());
        assertTrue(UiHarness.label(h.stationTile(Station.EKMEK)).startsWith("Ekmek ısıtıcı 0/2"));
        assertEquals("ekmek ısıtıcı döngüsü yeniden başlatılmadı", 1, rec.count("sfx_bread_warm", -1));
    }

    @Test public void openMenuRefreshesWhenHandsBecomeFree() {
        UiHarness h = day(FlowMode.GERCEKCI, 2, true);
        Order o = spawn(h, "ekmek_arasi");
        h.selectOrder(o); h.run(100);
        UiBot.row(h, "Ekmeği ısıt").performClick(); h.run(50); // koyma: eller 0,6 sn dolu
        View doner = UiBot.row(h, "Döneri dilimle");
        assertTrue(UiHarness.label(doner), UiHarness.label(doner).contains("elin dolu"));
        doner.performClick(); h.run(50);
        assertTrue("boy menüsü açılır", UiBot.panelOpen(h));
        String size = o.getLine(0).getSize().displayName;
        assertTrue(UiHarness.label(UiBot.row(h, size)), UiHarness.label(UiBot.row(h, size)).contains("Elin dolu"));
        h.run(800);
        assertFalse("eller boşalınca menü yenilenir", UiHarness.label(UiBot.row(h, size)).contains("Elin dolu"));
        UiBot.row(h, size).performClick(); h.run(200);
        assertFalse(UiBot.panelOpen(h));
        assertTrue(o.getLine(0).isStepRunning(1));
    }

    // ------------------------------------------------------------------ 4. çırak olayları
    @Test public void apprenticeHelpsTheSelectedOrderAndAlreadyRunningIsAnnounced() {
        UiHarness h = new UiHarness(false, FlowMode.DENGELI);
        h.act.save.tutorialDone = true;
        h.click("Yeni oyun"); h.settle();
        h.act.save.day = 2;
        h.act.save.upgradeLevels.put("cirak", 1);
        for (Ingredient i : Catalog.ingredients()) h.act.save.inventory.set(i.id, 30);
        h.startDay();
        GameEngine e = h.engine();
        e.setSpawning(false);
        Rec rec = new Rec();
        h.act.sound.setSinkForTest(rec);
        assertTrue("gün içinde çırak satırı", UiHarness.label((View) h.field(h.screen(), "workersBox")).startsWith("Çırak"));
        Order o = spawn(h, "ekmek_arasi", "ayran");
        h.selectOrder(o); h.run(100);
        UiBot.row(h, "Ekmek arası döner: Döneri dilimle").performClick(); h.run(100);
        long t = 0;
        while (!o.getLine(1).isStepRunning(0) && !o.getLine(1).isDone() && t < 20_000) { h.run(100); t += 100; }
        assertTrue("çırak seçili siparişin ayranını doldurur", o.getLine(1).isStepRunning(0));
        ActiveStep job = e.getActiveForItem(o.id, 1);
        assertNotNull(job);
        assertTrue(job.isApprentice());
        h.run(100);
        assertTrue(UiHarness.label((View) h.field(h.screen(), "workersBox")).contains("Ayranı doldur"));
        View run = UiBot.row(h, "Ayran: Ayranı doldur sürüyor");
        assertNotNull(h.clickables().toString(), run);
        assertTrue(UiHarness.label(run), UiHarness.label(run).endsWith(", çırak"));
        // oyuncu çırağın işine dokunur: "zaten sürüyor, çırak yapıyor" (uyarı sesi + duyuru)
        while (!e.isHandsFree() && t < 20_000) { h.run(50); t += 50; }
        if (o.getLine(1).isStepRunning(0)) {
            int warn0 = rec.count("sfx_warning", 0);
            h.stationTile(Station.ICECEK).performClick(); h.run(100);
            String n = notice(h).getText().toString();
            assertTrue(n, n.contains("zaten sürüyor") && n.contains("çırak"));
            assertTrue("uyarı sesi", rec.count("sfx_warning", 0) > warn0);
        }
        t = 0;
        while (!o.getLine(1).isDone() && t < 20_000) { h.run(100); t += 100; }
        assertTrue(o.getLine(1).isDone());
        assertTrue("ayran bitince bardak sesi", rec.indexOf("sfx_drink_glass_", 0) >= 0);
        audit(h, "Çırak");
    }

    // ------------------------------------------------------------------ 5. içecek sesleri (havuz)
    @Test public void drinkStepsUseTheirOwnVariantPools() {
        UiHarness h = day(FlowMode.DENGELI, 5, true);
        GameEngine e = h.engine();
        Rec rec = new Rec();
        h.act.sound.setSinkForTest(rec);
        Order ay = spawn(h, "ayran");
        h.selectOrder(ay); h.run(100);
        UiBot.row(h, "Ayranı doldur").performClick(); h.run(100);
        int a0 = rec.indexOf("sfx_drink_ayran_", 0);
        assertTrue("ayran doldurma: ayran dökme varyantı " + rec.names, a0 >= 0);
        long t = 0;
        while (!ay.getLine(0).isDone() && t < 10_000) { h.run(100); t += 100; }
        assertTrue("bitişte bardak " + rec.names, rec.indexOf("sfx_drink_glass_", a0) > a0);
        assertEquals("eski kısa döngü yok", -1, rec.indexOf("sfx_pour", 0));
        Order can = spawn(h, "kutu_icecek");
        h.selectOrder(can); h.run(100);
        int from = rec.names.size();
        UiBot.row(h, "Dolaptan al").performClick(); h.run(100);
        int f0 = rec.indexOf("sfx_drink_fridge_", from);
        assertTrue("dolaptan alma: dolap varyantı " + rec.names.subList(from, rec.names.size()), f0 >= 0);
        t = 0;
        while (!can.getLine(0).isDone() && t < 10_000) { h.run(100); t += 100; }
        assertTrue("kutu açma " + rec.names.subList(from, rec.names.size()), rec.indexOf("sfx_drink_can_", f0) > f0);
        // teslim: bardak/kutu tezgâha (glass)
        t = 0;
        while (!can.isReady() && t < 10_000) { UiBot.stepViaRows(h); h.run(100); t += 100; }
        h.selectOrder(can); h.run(100);
        int d0 = rec.names.size();
        h.access().actionRow(0).performClick(); h.run(600);
        assertNull(e.getOrder(can.id));
        int del = rec.indexOf("sfx_deliver", d0);
        assertTrue(del >= 0);
        assertTrue("teslimde bardak sesi " + rec.names.subList(d0, rec.names.size()), rec.indexOf("sfx_drink_glass_", del) > del);
        assertEquals(-1, rec.indexOf("sfx_pour", 0));
    }

    // ------------------------------------------------------------------ 6. Market, metinler
    @Test public void marketListsTheWideStoveUpgrade() {
        UiHarness h = new UiHarness(false, FlowMode.DENGELI);
        h.click("Yeni oyun"); h.settle();
        h.act.save.day = 3;
        h.act.save.money = 50_000;
        h.act.startBriefing(); h.settle();
        h.click("Markete git"); h.settle();
        h.click("Mutfak aletleri"); h.settle();
        View info = h.findText("Geniş ocak");
        assertNotNull("Geniş ocak markette: " + h.clickables(), info);
        audit(h, "Market (geniş ocak)");
    }

    @Test public void textsTeachTheNewModelWithoutTheOldSlotWord() {
        StringBuilder all = new StringBuilder();
        for (String[] s : Texts.helpSections()) all.append(s[0]).append(' ').append(s[1]).append(' ');
        for (FlowMode m : FlowMode.values()) { all.append(Texts.quickHelp(m, true)).append(Texts.quickHelp(m, false)); }
        all.append(Texts.apprenticeLevelsText(1)).append(Texts.TUT_CUSTOMER).append(Texts.TUT_SELECTED).append(Texts.TUT_PASSIVE)
                .append(Texts.tutEnd(true)).append(Texts.tutEnd(false));
        com.doner.oyunu.engine.SaveData s = com.doner.oyunu.engine.SaveData.newGame();
        for (String l : Texts.briefingLines(s)) all.append(l);
        String t = all.toString();
        assertFalse("'yuva' kelimesi kalmadı", t.toLowerCase(new java.util.Locale("tr")).contains("yuva"));
        for (String k : new String[]{"Elin dolu", "Ocak dolu", "Geniş ocak", "başka işe geç", "Sıradaki sipariş", "kendi eli"})
            assertTrue("metinlerde: " + k, t.contains(k));
        assertTrue(Texts.apprenticeLevelsText(0).contains("diğer siparişlere"));
    }

    @Test public void actionAndStationLabelsAreShort() {
        UiHarness h = day(FlowMode.GERCEKCI, 9, true);
        GameEngine e = h.engine();
        Order o = spawn(h, "ekmek_arasi+patates", "ayran", "pilav_ustu");
        h.selectOrder(o); h.run(100);
        for (int i = 0; i < h.access().visibleActions(); i++) {
            String l = UiHarness.label(h.access().actionRow(i));
            assertTrue(l, l.length() <= 60 && !l.isEmpty());
        }
        for (Station st : Station.values()) {
            String l = Texts.stationShort(e, st, true);
            assertTrue(l, l.length() <= 60 && l.startsWith(st.displayName));
        }
        audit(h, "Çok kalemli sipariş");
    }

    // ------------------------------------------------------------------ 7. sade düzenle tam gün (yalnızca eylem satırları)
    private static void fullDayViaRows(FlowMode m, boolean timed) {
        GameScreen.testSeed = 7L;
        UiHarness h = new UiHarness(false, m);
        h.act.save.tutorialDone = true;
        h.act.save.timePressure = timed;
        h.click("Yeni oyun"); h.settle();
        h.act.save.day = 5;
        h.act.save.money = 30_000;
        com.doner.oyunu.engine.Shop.buyRecommended(h.act.save);
        h.startDay();
        GameEngine e = h.engine();
        assertEquals(timed, e.isTimed());
        boolean audited = false;
        long t = 0;
        while (h.screen() instanceof GameScreen && t < 900_000) {
            if (!audited && e.getOrders().size() >= 2 && !UiBot.panelOpen(h)) { audit(h, m + " sade gün"); audited = true; }
            UiBot.stepViaRows(h); h.run(100); t += 100;
        }
        h.run(20_000);
        assertTrue(m + " özet ekranı", h.screen() instanceof SummaryScreen);
        DaySummary s = e.getSummary();
        assertFalse(m + " gün başarısız olmamalı", s.failed);
        assertTrue(m + " teslim: " + s.delivered, s.delivered >= 3);
        assertEquals(m + " hatasız", 0, s.mistakes);
        if (!timed) assertEquals("süresiz: kimse gitmez", 0, s.lost);
        assertEquals(6, h.act.save.day);
    }

    @Test public void fullDayViaRowsRahatTimed() { fullDayViaRows(FlowMode.RAHAT, true); }
    @Test public void fullDayViaRowsDengeliTimed() { fullDayViaRows(FlowMode.DENGELI, true); }
    @Test public void fullDayViaRowsGercekciTimed() { fullDayViaRows(FlowMode.GERCEKCI, true); }
    @Test public void fullDayViaRowsRahatUntimed() { fullDayViaRows(FlowMode.RAHAT, false); }
    @Test public void fullDayViaRowsDengeliUntimed() { fullDayViaRows(FlowMode.DENGELI, false); }
    @Test public void fullDayViaRowsGercekciUntimed() { fullDayViaRows(FlowMode.GERCEKCI, false); }
}
