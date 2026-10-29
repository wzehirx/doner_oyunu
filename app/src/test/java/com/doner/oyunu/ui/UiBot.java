package com.doner.oyunu.ui;

import static org.junit.Assert.*;

import android.view.View;

import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Ingredient;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.Step;
import com.doner.oyunu.engine.ToppingOption;

import java.util.Set;

/** Arayüz testleri için "makul oyuncu": hazırlık, sipariş seçimi, istasyon, malzeme paneli, paketleme, teslim. */
final class UiBot {
    private UiBot() {}

    /** Malzeme paneli açık mı (Tamam düğmesi görünür)? */
    static boolean panelOpen(UiHarness h) {
        return h.screen() instanceof GameScreen && h.field(h.screen(), "panel") != null;
    }

    /** Satır düğmesi: etiketi "<ad>," ile başlayan tıklanabilir görünüm. */
    static View row(UiHarness h, String name) {
        for (View v : UiHarness.visibleViews(h.root())) {
            if (v.isClickable() && UiHarness.label(v).startsWith(name + ",")) return v;
        }
        return null;
    }

    /** Panelde seçimi hedefe getirir (gerekirse stokta olmayanı acil alır) ve Tamam der. */
    static void toppingPanel(UiHarness h) {
        GameEngine e = h.engine();
        SelectionPanel p = (SelectionPanel) h.field(h.screen(), "panel");
        Set<String> target = e.getToppingTarget(p.orderId(), p.itemIndex());
        // stokta olmayan hedef malzemeyi acil al
        for (ToppingOption op : e.getToppingOptions(p.orderId(), p.itemIndex())) {
            if (!op.available && target.contains(op.ingredient.id)) {
                View r = row(h, op.ingredient.name);
                assertNotNull("satır yok: " + op.ingredient.name, r);
                r.performClick();
                h.click("Acil al");
            }
        }
        for (ToppingOption op : e.getToppingOptions(p.orderId(), p.itemIndex())) {
            Ingredient ing = op.ingredient;
            View r = row(h, ing.name);
            assertNotNull("satır yok: " + ing.name, r);
            boolean on = UiHarness.label(r).startsWith(ing.name + ", eklendi");
            if (op.available && on != target.contains(ing.id)) r.performClick();
        }
        h.click("Tamam");
    }

    /** Açık panel: malzeme seçimi ise hedefe göre doldurur, tepsi ise vazgeçer. */
    static void panel(UiHarness h) {
        SelectionPanel p = (SelectionPanel) h.field(h.screen(), "panel");
        if (p.mode() == SelectionPanel.Mode.TOPPING) toppingPanel(h);
        else h.click("Vazgeç");
    }

    /** Bir hamle: panel, acil al, teslim, sıradaki adım istasyonu... */
    static void step(UiHarness h) {
        GameEngine e = h.engine();
        if (h.findLabel("Devam et") != null) return; // duraklatıldı
        if (e.getState() == GameEngine.State.PREP || e.getState() == GameEngine.State.READY) {
            if (panelOpen(h)) h.click("Vazgeç");
            else h.skipPrep();
            return;
        }
        if (h.findLabel("Devam et") != null) return; // duraklatıldı
        if (e.getState() != GameEngine.State.RUNNING) return;
        if (panelOpen(h)) { panel(h); return; }
        View em = h.findLabel("Acil al");
        if (em != null) { em.performClick(); return; }
        for (Order o : e.getOrders()) {
            if (o.isReady()) { h.click("Teslim et"); return; }
        }
        if (e.getFreeSlots() <= 0) return;
        Order sel = e.getSelectedOrder();
        if (sel == null || sel.isReady()) {
            for (Order o : e.getOrders()) if (!o.isReady()) { View c = h.orderCard(o); if (c != null) { c.performClick(); return; } }
            return;
        }
        Step n = sel.nextStep();
        if (n != null) {
            View t = h.stationTile(n.station);
            assertNotNull("istasyon düğmesi yok " + n.station, t);
            t.performClick();
            return;
        }
        // seçili siparişte başlatılacak iş yok (hepsi sürüyor): bekleyen başka siparişe geç
        for (Order o : e.getOrders()) {
            if (o.id != sel.id && !o.isReady() && o.nextStep() != null) { View c = h.orderCard(o); if (c != null) c.performClick(); return; }
        }
    }

    static void playDay(UiHarness h, long maxMs) {
        long t = 0;
        while (h.screen() instanceof GameScreen && t < maxMs) {
            step(h);
            h.run(100);
            t += 100;
        }
    }
}
