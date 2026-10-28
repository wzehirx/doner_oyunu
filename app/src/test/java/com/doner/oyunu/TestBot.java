package com.doner.oyunu;

import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Ingredient;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.StartResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Test botu: belirli bir tepki süresiyle (insan gibi) siparişleri sırayla hazırlar. */
public final class TestBot {
    private final float reaction;
    private float cooldown;
    public int emergencyBuys;

    public TestBot(float reactionSeconds) { this.reaction = reactionSeconds; }

    /** Günü bitene (veya başarısız olana) kadar oynar. Güvenlik: 1000 sn. */
    public void playDay(GameEngine e) {
        float guard = 0;
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
        for (Order o : list) {
            if (o.isRunning() || o.isReady()) continue;
            if (e.getFreeSlots() <= 0) return false;
            StartResult r = e.startStep(o.id, o.nextStep().station.id);
            if (r == StartResult.STARTED) return true;
            if (r == StartResult.OUT_OF_STOCK) {
                Ingredient m = e.missingIngredient(o.id);
                if (m != null && e.emergencyBuy(m.id)) { emergencyBuys++; return true; }
            }
        }
        return false;
    }
}
