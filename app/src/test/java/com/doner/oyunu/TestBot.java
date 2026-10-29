package com.doner.oyunu;

import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Ingredient;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.OrderLine;
import com.doner.oyunu.engine.PrepKind;
import com.doner.oyunu.engine.PrepResult;
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
 * Test botu: belirli bir tepki süresiyle (insan gibi) siparişleri en sabırsızdan başlayarak hazırlar.
 * prep: gün öncesi hazırlık aşamasını ve gün içi boşta hazırlığı yapar. careful: malzeme seçiminde müşteri isteklerini
 * uygular (false: yalnızca varsayılan küme -> istekli siparişler hatalı olur).
 */
public final class TestBot {
    private final float reaction;
    private final boolean prep;
    private final boolean careful;
    private float cooldown;
    public int emergencyBuys;
    public int prepActions;

    /** Eski davranışa en yakın: hazırlık yapmayan, isteklere dikkat eden bot. */
    public TestBot(float reactionSeconds) { this(reactionSeconds, false, true); }

    public TestBot(float reactionSeconds, boolean prep, boolean careful) {
        this.reaction = reactionSeconds;
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
            if (cooldown <= 0f && act(e)) cooldown = reaction;
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
                StartResult r = e.startStep(o.id, -1, s.station.id);
                if (r == StartResult.STARTED) return true;
                if (r == StartResult.NEEDS_TOPPINGS) {
                    if (confirm(e, o)) return true;
                    continue;
                }
                if (r == StartResult.OUT_OF_STOCK) {
                    if (e.emergencyBuyMissing(o.id)) { emergencyBuys++; return true; }
                }
            }
            if (prep && prepStep(e, false)) return true;
        }
        return false;
    }

    private boolean confirm(GameEngine e, Order o) {
        int li = e.openToppingItem(o.id);
        if (li < 0) return false;
        Set<String> pick = new LinkedHashSet<>(careful ? e.getToppingTarget(o.id, li) : o.getLine(li).product.defaultToppings);
        for (int tries = 0; tries < 8; tries++) {
            StartResult r = e.confirmToppings(o.id, li, pick);
            if (r == StartResult.STARTED) return true;
            if (r != StartResult.OUT_OF_STOCK) return false;
            // stokta olmayan malzeme: acil al, olmazsa seçimden çıkar
            boolean fixed = false;
            for (String id : new ArrayList<>(pick)) {
                if (e.getSave().inventory.get(id) <= 0 && e.getTrayUses(id) <= 0) {
                    if (e.emergencyBuy(id)) { emergencyBuys++; fixed = true; }
                    else { pick.remove(id); fixed = true; }
                    break;
                }
            }
            if (!fixed) return false;
        }
        return false;
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
            for (Product p : Catalog.productsForDay(e.getDay())) for (String id : p.defaultToppings) {
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
