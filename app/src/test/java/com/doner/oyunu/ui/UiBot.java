package com.doner.oyunu.ui;

import static org.junit.Assert.*;

import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.OrderLine;
import com.doner.oyunu.engine.Station;
import com.doner.oyunu.engine.StationOption;
import com.doner.oyunu.engine.Step;

import java.util.List;
import java.util.Set;

/**
 * Arayüz testleri için "makul oyuncu": hazırlık, sipariş seçimi, istasyon, seçenek paneli (boy seçimi, tek tek malzeme,
 * malzemeleri bitir), paketleme, teslim. İki yol: panel (varsayılan) ya da TalkBack Eylemleri (viaActions).
 */
final class UiBot {
    private UiBot() {}

    /** Bot istasyon eylemlerini TalkBack özel eylemleriyle (panelsiz) mi uygulasın? */
    static boolean viaActions = false;

    /** Seçenek paneli açık mı? */
    static boolean panelOpen(UiHarness h) {
        return h.screen() instanceof GameScreen && h.field(h.screen(), "panel") != null;
    }

    static MenuPanel panelOf(UiHarness h) { return (MenuPanel) h.field(h.screen(), "panel"); }

    /** Panelde (ya da ekranda) etiketi "<ad>" ile başlayan tıklanabilir görünüm. */
    static View row(UiHarness h, String name) {
        for (View v : UiHarness.visibleViews(h.root())) {
            if (v.isClickable() && UiHarness.label(v).startsWith(name)) return v;
        }
        return null;
    }

    /** İstasyon düğmesinin özel eylem kimliği: etiketi "<ad>" ile başlayan eylem (yoksa -1). */
    static int actionIdByLabel(View tile, String startsWith) {
        for (AccessibilityNodeInfo.AccessibilityAction act : tile.createAccessibilityNodeInfo().getActionList()) {
            CharSequence l = act.getLabel();
            if (l != null && l.toString().startsWith(startsWith)) return act.getId();
        }
        return -1;
    }

    /** İstasyon düğmesinde seçeneği (id ile) hangi yolla olursa uygular; true: uygulandı/denendi. */
    static boolean applyViaActions(UiHarness h, Station st, String optionId) {
        View tile = h.stationTile(st);
        if (tile == null) return false;
        GameEngine e = h.engine();
        Order o = e.getSelectedOrder();
        List<StationOption> ops = e.getStationOptions(o == null ? -1 : o.id, -1, st.id);
        for (StationOption op : ops) {
            if (op.id.equals(optionId)) {
                int id = actionIdByLabel(tile, op.label());
                assertTrue("özel eylem düğmede yok: " + op.label(), id >= 0);
                return tile.performAccessibilityAction(id, null);
            }
        }
        return false;
    }

    private static boolean pickOption(UiHarness h, MenuPanel p, String optionId) {
        Button b = p.button(optionId);
        if (b == null) return false;
        b.performClick();
        return true;
    }

    /** Açık panelde makul hamle: boy seç, hedef malzemeleri tek tek ekle, bitir; hazırlık panelinde ilk seçenek. */
    static void panel(UiHarness h) {
        GameEngine e = h.engine();
        MenuPanel p = panelOf(h);
        Station st = Station.byId(p.stationId);
        Order o = p.orderId >= 0 ? e.getOrder(p.orderId) : null;
        List<StationOption> ops = e.getStationOptions(p.orderId, -1, p.stationId);
        if (o == null) { prepPick(h, p, ops); return; }
        int li = e.resolveStationItem(o.id, -1, st.id);
        if (li < 0) { h.click("Kapat"); return; }
        OrderLine line = o.getLine(li);
        if (st == Station.DONER) {
            String id = "size:" + line.getSize().id;
            for (StationOption op : ops) if (op.id.equals(id)) { if (op.enabled) pickOption(h, p, id); return; }
            for (StationOption op : ops) if (op.id.equals("step")) { if (op.enabled) pickOption(h, p, "step"); return; } // otomatik boy
            prepPick(h, p, ops);
            return;
        }
        if (st == Station.SOS) {
            for (StationOption op : ops) if (op.id.equals("auto") && autoOk(e, line)) { if (op.enabled) pickOption(h, p, "auto"); return; }
            boolean hasAdd = false;
            for (StationOption op : ops) if (op.group == StationOption.Group.ADD) hasAdd = true;
            if (!hasAdd) { prepPick(h, p, ops); return; }
            Set<String> target = e.getToppingTarget(o.id, li);
            for (StationOption op : ops) { // v0.7: istek farkı "X çıkar"
                if (op.group == StationOption.Group.REMOVE && !target.contains(op.ingredientId)) { if (op.enabled) pickOption(h, p, op.id); return; }
            }
            for (StationOption op : ops) {
                if (op.group != StationOption.Group.ADD) continue;
                String ing = op.ingredientId;
                if (target.contains(ing) && line.addedCount(ing) == 0) {
                    if (op.enabled) pickOption(h, p, op.id);
                    return; // eklenirken ya da elin doluyken bekle
                }
            }
            for (StationOption op : ops) {
                if (op.group == StationOption.Group.ADD && !target.contains(op.ingredientId) && line.addedCount(op.ingredientId) > 0) {
                    if (pickOption(h, p, "undo")) return;
                }
            }
            for (StationOption op : ops) if (op.id.equals("finish")) { if (op.enabled) pickOption(h, p, "finish"); return; }
            return;
        }
        for (StationOption op : ops) if (op.group == StationOption.Group.STEP && op.enabled) { pickOption(h, p, op.id); return; }
        h.click("Kapat");
    }

    /** "auto" kullanılabilir mi: Gerçekçi kısayolu ("Standart malzemelerle bitir") yalnızca istek farkı yoksa. */
    static boolean autoOk(GameEngine e, OrderLine line) {
        return e.getFlowMode() != com.doner.oyunu.engine.FlowMode.GERCEKCI || line.getTarget().equals(line.getDefaults());
    }

    private static void prepPick(UiHarness h, MenuPanel p, List<StationOption> ops) {
        for (StationOption op : ops) if (op.enabled) { pickOption(h, p, op.id); return; }
        h.click("Kapat");
    }

    /** Bot, sıradaki adım için istasyon düğmesine dokunur ya da eylemle seçer. */
    private static void doStation(UiHarness h, GameEngine e, Order sel, Station st) {
        View t = h.stationTile(st);
        assertNotNull("istasyon düğmesi yok " + st, t);
        if (!viaActions) { t.performClick(); return; }
        // TalkBack Eylemleri yolu: doğru seçeneği panelsiz uygula
        List<StationOption> ops = e.getStationOptions(sel.id, -1, st.id);
        int li = e.resolveStationItem(sel.id, -1, st.id);
        String want = null;
        if (st == Station.DONER && li >= 0) {
            OrderLine line = sel.getLine(li);
            for (StationOption op : ops) if (op.group == StationOption.Group.SIZE && op.id.equals("size:" + line.getSize().id)) want = op.id;
            if (want == null) for (StationOption op : ops) if (op.id.equals("step")) want = "step"; // otomatik boy
        } else if (st == Station.SOS && li >= 0 && sel.getLine(li).product.toppingStepIndex() >= 0
                && sel.getLine(li).isStepOpen(sel.getLine(li).product.toppingStepIndex())) {
            OrderLine line = sel.getLine(li);
            Set<String> target = e.getToppingTarget(sel.id, li);
            for (StationOption op : ops) if (op.id.equals("auto") && autoOk(e, line)) { if (op.enabled) want = "auto"; else return; }
            if (want == null) for (StationOption op : ops) {
                if (op.group == StationOption.Group.REMOVE && !target.contains(op.ingredientId)) { if (op.enabled) want = op.id; else return; break; }
            }
            if (want == null) for (StationOption op : ops) {
                if (op.group == StationOption.Group.ADD && target.contains(op.ingredientId) && line.addedCount(op.ingredientId) == 0) {
                    if (op.enabled) want = op.id; else return;
                    break;
                }
            }
            if (want == null) for (StationOption op : ops) if (op.id.equals("finish") && op.enabled) want = "finish";
            if (want == null) return;
        } else {
            for (StationOption op : ops) if (op.group == StationOption.Group.STEP) { want = op.id; break; }
        }
        if (want == null) { t.performClick(); return; }
        assertTrue("eylem uygulanabilmeli: " + want, applyViaActions(h, st, want));
    }

    /** Bir hamle: panel, acil al, teslim, sıradaki adım istasyonu... */
    static void step(UiHarness h) {
        GameEngine e = h.engine();
        if (h.findLabel("Devam et") != null) return; // duraklatıldı
        if (e.getState() == GameEngine.State.PREP || e.getState() == GameEngine.State.READY) {
            if (panelOpen(h)) h.click("Kapat");
            else h.skipPrep();
            return;
        }
        if (e.getState() != GameEngine.State.RUNNING) return;
        View em = h.findLabel("Acil al");
        if (em != null) { em.performClick(); return; }
        if (panelOpen(h)) { panel(h); return; }
        for (Order o : e.getOrders()) {
            if (o.isReady()) { h.click("Teslim et"); return; }
        }
        if (e.getFreeSlots() <= 0) return;
        Order sel = e.getSelectedOrder();
        if (sel == null || sel.isReady()) {
            for (Order o : e.getOrders()) if (!o.isReady()) { h.selectOrder(o); return; }
            return;
        }
        Step n = sel.nextStep();
        if (n != null) {
            doStation(h, e, sel, n.station);
            return;
        }
        // seçili siparişte başlatılacak iş yok (hepsi sürüyor): bekleyen başka siparişe geç
        for (Order o : e.getOrders()) {
            if (o.id != sel.id && !o.isReady() && o.nextStep() != null) { h.selectOrder(o); return; }
        }
    }

    /**
     * v2.0 sade düzenin YOLU: bot yalnızca "Sıradaki sipariş"in eylem satırlarıyla, "Sonraki sipariş" düğmesiyle ve
     * açılan seçenek panelleriyle oynar (istasyon düğmelerine ya da sipariş listesine dokunmaz).
     */
    static void stepViaRows(UiHarness h) {
        GameEngine e = h.engine();
        if (h.findLabel("Devam et") != null) return;
        if (e.getState() == GameEngine.State.PREP || e.getState() == GameEngine.State.READY) {
            if (panelOpen(h)) h.click("Kapat");
            else h.skipPrep();
            return;
        }
        if (e.getState() != GameEngine.State.RUNNING) return;
        View em = h.findLabel("Acil al");
        if (em != null) { em.performClick(); return; }
        if (panelOpen(h)) { panel(h); return; }
        GameScreen.FocusHeaderAccess fa = h.access();
        for (int i = 0; i < fa.visibleActions(); i++) {
            com.doner.oyunu.engine.ReadyAction a = fa.action(i);
            if (a == null) continue;
            if (a.kind == com.doner.oyunu.engine.ReadyAction.Kind.DELIVER || a.isDoable()) {
                assertTrue("eylem satırı tıklanabilir", fa.actionRow(i).performClick());
                return;
            }
        }
        // sıradakinde yapılacak iş yok: başka siparişte iş varsa "Sonraki sipariş"
        Order focus = e.getFocusOrder();
        Order sug = e.getSuggestedOrder();
        if (sug != null && focus != null && sug.id != focus.id) h.click("Sonraki sipariş");
    }

    static void playDayViaRows(UiHarness h, long maxMs) {
        long t = 0;
        while (h.screen() instanceof GameScreen && t < maxMs) {
            stepViaRows(h);
            h.run(100);
            t += 100;
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
