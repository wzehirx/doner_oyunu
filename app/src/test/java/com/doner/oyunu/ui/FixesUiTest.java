package com.doner.oyunu.ui;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.view.View;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.ScrollView;

import com.doner.oyunu.engine.Catalog;
import com.doner.oyunu.engine.FlowMode;
import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Ingredient;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.Station;
import com.doner.oyunu.engine.Step;
import com.doner.oyunu.engine.Upgrades;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAccessibilityManager;

import java.util.ArrayList;
import java.util.List;

/** Bilinen hata düzeltmelerinin arayüz testleri. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class FixesUiTest {

    private static ShadowAccessibilityManager talkBack(boolean on) {
        AccessibilityManager am = (AccessibilityManager) RuntimeEnvironment.getApplication().getSystemService(Context.ACCESSIBILITY_SERVICE);
        ShadowAccessibilityManager s = shadowOf(am);
        s.setEnabled(on);
        s.setTouchExplorationEnabled(on);
        return s;
    }

    private UiHarness game() {
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        h.click("Yeni oyun"); h.settle();
        h.startDay();
        return h;
    }

    private static Order firstOrder(UiHarness h) {
        GameEngine e = h.engine();
        long t = 0;
        while (e.getOrders().isEmpty() && t < 30000) { h.run(100); t += 100; }
        assertFalse(e.getOrders().isEmpty());
        return e.getOrders().get(0);
    }

    private static boolean insideScrollView(View v) {
        ViewParent p = v.getParent();
        while (p != null) { if (p instanceof ScrollView) return true; p = p.getParent(); }
        return false;
    }

    // ------------------------------------------------------------ madde 1
    @Test public void emergencyButtonIsFixedOutsideTheScrollingListAndTakesFocusWithTalkBack() {
        ShadowAccessibilityManager sam = talkBack(true);
        UiHarness h = game();
        GameEngine e = h.engine();
        Button btn = (Button) h.field(h.screen(), "emergencyBtn");
        assertEquals("başta gizli", View.GONE, btn.getVisibility());
        assertFalse("kaydırılan sipariş listesinin içinde olmamalı", insideScrollView(btn));

        Order o = firstOrder(h);
        h.orderCard(o).performClick(); h.run(100);
        for (String id : new ArrayList<>(o.nextStep().needs.keySet())) e.getSave().inventory.set(id, 0);
        Ingredient miss = e.missingIngredient(o.id);
        int evBase = sam.getSentAccessibilityEvents().size();
        h.stationTile(o.nextStep().station).performClick(); h.run(200);

        assertEquals(View.VISIBLE, btn.getVisibility());
        assertFalse(insideScrollView(btn));
        String d = btn.getContentDescription().toString();
        assertTrue(d, d.contains(miss.name));
        assertTrue(d, d.contains(String.valueOf(e.emergencyCostForMissing(o.id))));
        assertTrue(d, d.contains("lira"));
        boolean a11yFocused = false;
        List<AccessibilityEvent> evs = new ArrayList<>(sam.getSentAccessibilityEvents());
        for (AccessibilityEvent ev : evs.subList(Math.min(evBase, evs.size()), evs.size())) {
            if (ev.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                    && Button.class.getName().contentEquals(ev.getClassName())
                    && ev.getText().toString().contains(miss.name)) a11yFocused = true;
        }
        assertTrue("TalkBack erişilebilirlik odağı acil al düğmesine taşınmalı", a11yFocused);
        // duyuru: eksik malzeme adı ve acil alım konuşuldu
        boolean announced = false;
        List<AccessibilityEvent> all = new ArrayList<>(sam.getSentAccessibilityEvents());
        for (AccessibilityEvent ev : all.subList(Math.min(evBase, all.size()), all.size())) {
            if (ev.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT && !ev.getText().isEmpty()
                    && ev.getText().get(0).toString().contains(miss.name)) announced = true;
        }
        assertTrue("eksik malzeme duyurulmalı", announced);
    }

    @Test public void oneTapBuysAllMissingIngredientsOfTheStep() {
        // Hatay dürüm: "Harç ve döner ekle" adımı iki malzeme ister (lahmacun harcı + et döner)
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        h.click("Yeni oyun"); h.settle();
        h.act.save.day = 6;
        h.act.save.money = 20_000;
        h.startDay();
        GameEngine e = h.engine();
        e.setSpawning(false);
        Order o = e.spawnOrder(java.util.Arrays.asList("hatay_durum"), new ArrayList<String>());
        assertNotNull(o);
        h.run(300);
        h.orderCard(o).performClick(); h.run(100);
        Step harc = o.getLine(0).step(1);
        assertEquals("Harç ve döner ekle", harc.name);
        assertEquals(Station.DONER, harc.station);
        for (String id : new ArrayList<>(harc.needs.keySet())) e.getSave().inventory.set(id, 0);
        h.stationTile(Station.DONER).performClick(); h.run(100);
        assertTrue("boy seçenekleri paneli", UiBot.panelOpen(h));
        UiBot.row(h, o.getLine(0).getSize().displayName).performClick(); h.run(200);
        assertFalse("stok yoksa panel kapanır, acil al düğmesi çıkar", UiBot.panelOpen(h));
        List<Ingredient> missing = e.missingIngredients(o.id);
        assertTrue("birden çok eksik", missing.size() >= 2);
        View b = h.findLabel("Acil al");
        assertNotNull(b);
        String d = UiHarness.label(b);
        for (Ingredient i : missing) assertTrue("açıklama " + i.name + " içermeli: " + d, d.contains(i.name));
        assertTrue(d, d.contains(String.valueOf(e.emergencyCostForMissing(o.id))));
        int money = e.getMoney();
        int cost = e.emergencyCostForMissing(o.id);
        b.performClick(); h.run(100);
        assertEquals("tek dokunuşla hepsi alınmalı", money - cost, e.getMoney());
        assertTrue(e.missingIngredients(o.id).isEmpty());
        assertNull("eksik kalmayınca düğme kapanır", h.findLabel("Acil al"));
        h.stationTile(Station.DONER).performClick(); h.run(100);
        UiBot.row(h, o.getLine(0).getSize().displayName).performClick(); h.run(200);
        assertTrue("adım başlamalı", o.isRunning());
    }

    // ------------------------------------------------------------ madde 4
    private static void playUntilEngineFinished(UiHarness h) {
        long t = 0;
        while (h.engine().isRunning() && t < 400_000) {
            UiFlowTest.botStep(h);
            h.run(100);
            t += 100;
        }
        assertEquals(GameEngine.State.FINISHED, h.engine().getState());
    }

    @Test public void summaryAppearsWithinAboutFourSecondsEvenIfSpeechNeverFinishes() {
        talkBack(false); // TTS yolu: Robolectric'te konuşma hiç bitmeyebilir -> üst sınır devreye girmeli
        UiHarness h = game();
        playUntilEngineFinished(h);
        assertTrue(h.screen() instanceof GameScreen || h.screen() instanceof SummaryScreen);
        long t = 0;
        while (h.screen() instanceof GameScreen && t < 14_000) { h.run(100); t += 100; }
        assertTrue("özet ekranına geçilmeli", h.screen() instanceof SummaryScreen);
        assertTrue("geçiş en çok ~4 sn sürmeli, sürdü: " + t, t <= 4500);
    }

    @Test public void backKeyDuringDayEndTransitionSkipsToSummary() {
        UiHarness h = game();
        playUntilEngineFinished(h);
        h.run(100);
        if (!(h.screen() instanceof GameScreen)) return; // zaten geçmiş
        h.act.onBackPressed();
        h.idle(50);
        assertTrue("geri tuşu yok sayılmamalı", h.screen() instanceof SummaryScreen);
        // özetten geri: ana menü
        h.act.onBackPressed(); h.settle();
        assertTrue(h.screen() instanceof MenuScreen);
    }

    // ------------------------------------------------------------ madde 5
    @Test public void summaryScreenShowsRentActuallyPaid() {
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        com.doner.oyunu.engine.DaySummary s = new com.doner.oyunu.engine.DaySummary();
        s.day = 2; s.rentDue = 330; s.rent = 120; s.moneyAfter = 0; s.endStars = 3; s.startStars = 3;
        List<String[]> rows = Texts.summaryRows(s);
        String rent = null;
        for (String[] r : rows) if (r[0].equals("Kira")) rent = r[1];
        assertNotNull(rent);
        assertTrue(rent, rent.contains("120 lira ödendi"));
        assertTrue(rent, rent.contains("330"));
        assertTrue(rent, rent.contains("210 lira ödenemedi"));
        s.rent = 330;
        for (String[] r : Texts.summaryRows(s)) if (r[0].equals("Kira")) assertEquals("330 lira", r[1]);
        h.act.show(new SummaryScreen(h.act, s)); h.settle();
        assertNotNull(h.findText("Kira"));
    }

    // ------------------------------------------------------------ madde 6
    @Test public void plusTenDescriptionReportsRealQuantityAndPriceWhenCapped() {
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        h.click("Yeni oyun"); h.settle();
        h.click("Markete git"); h.settle();
        Ingredient ing = Catalog.ingredients().get(0);
        int cap = new Upgrades(h.act.save).stockCap();
        h.act.save.money = 10_000;
        // stoğu kapasiteden 4 eksik yap
        h.act.save.inventory.set(ing.id, cap - 4);
        h.click("Restoran"); h.settle(); h.click("Malzeme"); h.settle();
        View b = h.findLabel("10 " + ing.name + " al");
        assertNotNull(b);
        String d = UiHarness.label(b);
        assertTrue(d, d.contains("yalnızca 4"));
        assertTrue(d, d.contains((4 * ing.price) + " lira"));
        assertFalse("10 birim fiyatını söylememeli: " + d, d.contains((10 * ing.price) + " lira"));
        // gerçekten 4 alınır ve tutar o kadardır
        int money = h.act.save.money;
        b.performClick(); h.idle(50);
        assertEquals(cap, h.act.save.inventory.get(ing.id));
        assertEquals(money - 4 * ing.price, h.act.save.money);
        // dolunca açıklama güncellenir
        View b2 = h.findLabel("10 " + ing.name + " al");
        assertNotNull(b2);
        String d2 = UiHarness.label(b2);
        assertTrue(d2, d2.contains("stok dolu"));
        assertFalse(d2, d2.contains("lira ödenir"));
        // +1 ve +5 de aynı şekilde
        String d1 = UiHarness.label(h.findLabel("5 " + ing.name + " al"));
        assertTrue(d1, d1.contains("stok dolu"));
    }

    @Test public void plusTenDescriptionIsPlainWhenRoomAndWarnsWhenMoneyShort() {
        UiHarness h = new UiHarness(false, FlowMode.GERCEKCI);
        h.click("Yeni oyun"); h.settle();
        h.click("Markete git"); h.settle();
        Ingredient ing = Catalog.ingredients().get(0);
        h.act.save.inventory.set(ing.id, 0);
        h.act.save.money = 10_000;
        h.click("Restoran"); h.settle(); h.click("Malzeme"); h.settle();
        String d = UiHarness.label(h.findLabel("10 " + ing.name + " al"));
        assertTrue(d, d.contains((10 * ing.price) + " lira"));
        assertFalse(d, d.contains("yalnızca"));
        h.act.save.money = 1;
        h.click("Restoran"); h.settle(); h.click("Malzeme"); h.settle();
        assertTrue(UiHarness.label(h.findLabel("10 " + ing.name + " al")).contains("Paran yetmiyor"));
    }
}
