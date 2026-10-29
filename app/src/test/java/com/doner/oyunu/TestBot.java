package com.doner.oyunu;

import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Ingredient;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.OrderLine;
import com.doner.oyunu.engine.PortionSize;
import com.doner.oyunu.engine.PrepKind;
import com.doner.oyunu.engine.PrepResult;
import com.doner.oyunu.engine.Station;
import com.doner.oyunu.engine.Product;
import com.doner.oyunu.engine.Step;
import com.doner.oyunu.engine.StartResult;
import com.doner.oyunu.engine.Catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test botu (v0.6 API): belirli bir tepki süresiyle (insan gibi) siparişleri en sabırsızdan başlayarak hazırlar.
 * Her eylem (boy seçimi, TEK malzeme ekleme, "Malzemeleri bitir", adım başlatma) bir tepki süresi alır.
 * prep: gün öncesi hazırlık aşamasını ve gün içi boşta hazırlığı yapar. careful: müşteri isteklerini ve istenen boyu
 * uygular (false: yalnızca varsayılan malzeme kümesi ve her zaman TAM ekmek -> istekli/boylu siparişler hatalı olur).
 */
public final class TestBot {
    private final float reaction;
    private final boolean prep;
    private final boolean careful;
    /** Tek tek malzeme eklemede aynı menüde ardışık eylem: tepki süresinin bu oranı (kas hafızası). */
    static final float ADD_REACTION_FACTOR = 0.5f;
    private float cooldown;
    private float delay;
    public int emergencyBuys;
    public int prepActions;

    /** Eski davranışa en yakın: hazırlık yapmayan, isteklere dikkat eden bot. */
    public TestBot(float reactionSeconds) { this(reactionSeconds, false, true); }

    public TestBot(float reactionSeconds, boolean prep, boolean careful) {
        this.reaction = reactionSeconds;
        this.delay = reactionSeconds;
        this.prep = prep;
        this.careful = careful;
    }

    /** Günü bitene (veya başarısız olana) kadar oynar. Güvenlik: 1000 sn. */
    public void playDay(GameEngine e) {
        float guard = 0;
        if (prep && e.getState() == GameEngine.State.READY) {
            e.beginPrep();
            while (e.getState() == GameEngine.State.PREP && guard < 300f) {
                if (cooldown <= 0f) {
                    if (prepStep(e, true)) cooldown = reaction; else break;
                }
                e.tick(0.1f);
                cooldown -= 0.1f;
                guard += 0.1f;
            }
            // son hazırlık işi bitsin
            for (int i = 0; i < 400 && !e.getActiveSteps().isEmpty(); i++) e.tick(0.1f);
            e.startDay();
        }
        guard = 0;
        while (e.isRunning() && guard < 1000f) {
            if (cooldown <= 0f && act(e)) { cooldown = delay; delay = reaction; }
            e.tick(0.1f);
            cooldown -= 0.1f;
            guard += 0.1f;
        }
    }

    private boolean act(GameEngine e) {
        List<Order> list = new ArrayList<>(e.getOrders());
        Collections.sort(list, new Comparator<Order>() {
            @Override public int compare(Order a, Order b) {
                return Float.compare(a.customer.getPatienceLeft(), b.customer.getPatienceLeft());
            }
        });
        for (Order o : list) {
            if (o.isReady()) { e.deliver(o.id); return true; }
        }
        if (e.getFreeSlots() > 0) {
            for (Order o : list) {
                if (o.isReady()) continue;
                Step s = o.nextStep();
                if (s == null) continue;
                if (e.getSelectedOrderId() != o.id) e.selectOrder(o.id);
                StartResult r = doStep(e, o, s);
                if (r == StartResult.STARTED) return true;
                if (r == StartResult.OUT_OF_STOCK) {
                    if (e.emergencyBuyMissing(o.id)) { emergencyBuys++; return true; }
                }
            }
            if (prep && prepStep(e, false)) return true;
        }
        return false;
    }

    /** Önerilen adımı yeni API ile yapar: boy seçimi, tek tek ekleme, bitir, ya da genel adım. */
    private StartResult doStep(GameEngine e, Order o, Step s) {
        if (s == o.getPackStep()) return e.applyStationOption(o.id, -1, Station.PAKET.id, "step");
        int li = lineOfOpenStep(o, s);
        if (li < 0) return StartResult.INVALID;
        if (s.isTopping()) return toppings(e, o, li);
        if (s.isSized()) {
            OrderLine line = o.getLine(li);
            PortionSize pick = careful ? line.getSize() : PortionSize.FULL;
            return e.applyStationOption(o.id, li, Station.DONER.id, "size:" + pick.id);
        }
        return e.applyStationOption(o.id, li, s.station.id, "step");
    }

    private static int lineOfOpenStep(Order o, Step s) {
        for (OrderLine l : o.getLines()) {
            for (int i = 0; i < l.stepCount(); i++) if (l.step(i) == s && l.isStepOpen(i)) return l.index;
        }
        return -1;
    }

    /** Hedef (veya dikkatsizse varsayılan) malzemelerden eksik olanı ekler; hepsi eklenince "Malzemeleri bitir". */
    private StartResult toppings(GameEngine e, Order o, int li) {
        OrderLine line = o.getLine(li);
        Set<String> want = new LinkedHashSet<>(careful ? line.getTarget() : line.getDefaults());
        for (String id : want) {
            if (line.addedCount(id) > 0) continue;
            StartResult r = e.applyStationOption(o.id, li, Station.SOS.id, "add:" + id);
            if (r == StartResult.OUT_OF_STOCK) {
                if (e.emergencyBuy(id)) { emergencyBuys++; return StartResult.STARTED; }
                return StartResult.OUT_OF_STOCK;
            }
            if (r == StartResult.STARTED) delay = reaction * ADD_REACTION_FACTOR;
            return r;
        }
        return e.applyStationOption(o.id, li, Station.SOS.id, "finish");
    }

    /** Hazırlık sırası: içecek, döner, tepsi. Pre-day: hedef daha yüksek. */
    private boolean prepStep(GameEngine e, boolean preDay) {
        if (e.getFreeSlots() <= 0) return false;
        float left = preDay ? 999f : e.getTimeLeft();
        if (e.menuHasDrinkPrep() && e.getReadyDrinks() <= (preDay ? 4 : 3)
                && e.getSave().inventory.has("ayran", 4)) {
            if (e.startPrepDrink() == PrepResult.STARTED) { prepActions++; return true; }
        }
        if (left > 25f) {
            for (Ingredient m : e.getPrepMeats()) {
                if (e.getReadyMeat(m.id) <= (preDay ? 0 : 3) && e.getSave().inventory.has(m.id, 4)) {
                    if (e.startPrepDoner(m.id) == PrepResult.STARTED) { prepActions++; return true; }
                }
            }
            Map<String, Integer> freq = new LinkedHashMap<>();
            for (Product p : Catalog.productsForDay(e.getDay())) for (String id : p.defaultToppings(e.getDay())) {
                Integer v = freq.get(id);
                freq.put(id, v == null ? 1 : v + 1);
            }
            List<String> ids = new ArrayList<>(freq.keySet());
            final Map<String, Integer> f = freq;
            Collections.sort(ids, new Comparator<String>() {
                @Override public int compare(String a, String b) { return f.get(b) - f.get(a); }
            });
            Set<String> pick = new LinkedHashSet<>();
            for (String id : ids) {
                if (pick.size() >= 5) break;
                if (e.getTrayUses(id) <= (preDay ? 0 : 2) && e.getSave().inventory.has(id, 6)) pick.add(id);
            }
            if (pick.size() >= 3 && e.startPrepTray(pick) == PrepResult.STARTED) { prepActions++; return true; }
        }
        return false;
    }
}
