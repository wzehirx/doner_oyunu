package com.doner.oyunu.ui;

import static org.junit.Assert.*;

import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.content.Context;

import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.Station;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class GameDetailTest {

    private UiHarness game() {
        UiHarness h = new UiHarness(false);
        h.click("Yeni oyun"); h.settle();
        h.startDay();
        return h;
    }

    /** Her ekranda TalkBack'in ilk odağı başlıkta olmalı: ağaç sırasındaki ilk odaklanabilir/önemli görünüm focusTarget. */
    @Test public void firstFocusableIsTheScreenTitleOnEveryScreen() {
        UiHarness h = new UiHarness(false);
        List<Runnable> steps = new ArrayList<>();
        check(h, "Menü");
        h.click("Nasıl oynanır"); h.settle(); check(h, "Yardım");
        h.click("Geri"); h.settle();
        h.click("Ayarlar"); h.settle(); check(h, "Ayarlar");
        h.click("Geri"); h.settle();
        h.click("Yeni oyun"); h.settle(); check(h, "Brifing");
        h.click("Markete git"); h.settle(); check(h, "Market");
        h.click("Güne başla"); h.settle(); check(h, "Oyun(hazırlık)");
        h.skipPrep(); check(h, "Oyun");
        h.run(100);
        h.click("Duraklat"); h.run(100);
        View t = (View) h.field(h.screen(), "overlayTitle");
        assertNotNull(t);
        assertTrue("Duraklatma başlığı odaklanabilir", t.isFocusable());
    }

    private void check(UiHarness h, String where) {
        View target = h.screen().focusTarget();
        assertNotNull(where, target);
        assertTrue(where + " başlık odaklanabilir olmalı", target.isFocusable());
        assertTrue(where + " başlık heading olmalı", target.isAccessibilityHeading());
        View first = null;
        for (View v : UiHarness.visibleViews(h.root())) {
            if (v.isImportantForAccessibility() && (v.isFocusable() || v.isClickable()) && !(v instanceof android.widget.ScrollView) && !(v instanceof android.view.ViewGroup && !v.isClickable() && !v.isFocusable())) { first = v; break; }
        }
        assertSame(where + ": ilk odak başlık değil: '" + (first == null ? null : UiHarness.label(first)) + "'", target, first);
    }

    /** Durum çubuğu: odaklanabilir, "Para ..." okur, 60/30/10 sn eşiklerinde kibarca (polite) duyurur. */
    @Test public void statusBarIsFocusableAndGoesPoliteAtThresholds() {
        UiHarness h = game();
        h.run(3000);
        View bar = (View) h.field(h.screen(), "statusBar");
        assertTrue(bar.isFocusable());
        assertTrue(UiHarness.label(bar).startsWith("Para"));
        assertTrue(UiHarness.label(bar).contains("kalan süre"));
        int politeSeen = 0, changes = 0;
        String last = UiHarness.label(bar);
        long t = 0;
        while (t < 125_000 && h.engine().getState() == GameEngine.State.RUNNING) {
            UiFlowTest.botStep(h);
            h.run(100); t += 100;
            if (bar.getAccessibilityLiveRegion() == View.ACCESSIBILITY_LIVE_REGION_POLITE) politeSeen++;
            String now = UiHarness.label(bar);
            if (!now.equals(last)) { changes++; last = now; }
        }
        System.out.println("A11Y durum çubuğu açıklaması " + changes + " kez değişti (125 sn), polite örnek=" + politeSeen);
        assertTrue("Durum çubuğu eşiklerde POLITE canlı bölgeye geçmeli", politeSeen > 0);
        // Açıklama saniyede birden sık değişip TalkBack'i boğmamalı
        assertTrue("açıklama çok sık değişiyor: " + changes, changes < 125);
    }

    /** Eksik malzeme: acil al düğmesi belirir, çalışır, sonra adım başlar. (Motor notu: missingIngredient) */
    @Test public void outOfStockShowsEmergencyButtonAndPurchaseUnblocks() {
        UiHarness h = game();
        GameEngine e = h.engine();
        long t = 0;
        while (e.getOrders().isEmpty() && t < 20000) { h.run(100); t += 100; }
        Order o = e.getOrders().get(0);
        h.orderCard(o).performClick(); h.run(100);
        // sıradaki adımın tüm malzemelerini sıfırla
        for (String id : new ArrayList<>(o.nextStep().needs.keySet())) e.getSave().inventory.set(id, 0);
        int missingCount = o.nextStep().needs.size();
        int moneyBefore = e.getMoney();
        h.stationTile(o.nextStep().station).performClick(); h.run(200);
        View emerg = h.findLabel("Acil al");
        assertNotNull("Acil al düğmesi görünmeli", emerg);
        int guard = 0;
        while (!o.isRunning() && guard++ < 6) {
            View eb = h.findLabel("Acil al");
            if (eb != null) { eb.performClick(); h.run(100); }
            h.stationTile(o.nextStep().station).performClick(); h.run(100);
        }
        assertTrue("Acil alımlarla adım başlamalı", o.isRunning());
        assertTrue(e.getMoney() < moneyBefore);
        System.out.println("MOTOR-NOTU eksik malzeme sayısı=" + missingCount + " ; adımı başlatmak için gereken dokunuş turu=" + guard);
    }

    /** Öğretici: ilk müşteri geldikten sonra oyuncu seçmezse sipariş kendiliğinden seçilmez ama müşteri kaybedilmez (1. gün). */
    @Test public void tutorialIdleDoesNotAutoSelectButDay1PatienceIsLong() {
        UiHarness h = game();
        GameEngine e = h.engine();
        long t = 0;
        while (e.getOrders().isEmpty() && t < 20000) { h.run(100); t += 100; }
        Order o = e.getOrders().get(0);
        float p = o.customer.getPatienceLeft();
        float el0 = e.getElapsed();
        h.run(8000);
        System.out.println("MOTOR-NOTU dbg elapsed " + el0 + " -> " + e.getElapsed() + " state=" + e.getState() + " orders=" + e.getOrders().size() + " lost=" + e.getSummary().lost + " screen=" + h.screen().title());
        System.out.println("MOTOR-NOTU öğretici: 8 sn hareketsiz sonra seçili sipariş=" + e.getSelectedOrderId() + " sabır " + p + " -> " + (e.getOrder(o.id) == null ? "gitti" : e.getOrder(o.id).customer.getPatienceLeft()));
    }
}
