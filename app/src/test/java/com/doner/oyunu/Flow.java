package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import java.util.Set;

/** Test yardımcıları: motoru insan gibi sürmek için küçük eylemler. */
final class Flow {
    private Flow() {}

    static Order firstCustomer(GameEngine e) {
        for (int i = 0; i < 600 && e.getOrders().isEmpty(); i++) e.tick(0.1f);
        assertFalse("müşteri gelmedi", e.getOrders().isEmpty());
        return e.getOrders().get(0);
    }

    static void waitDone(GameEngine e, Order o) {
        for (int i = 0; i < 600 && o.isRunning(); i++) e.tick(0.1f);
        assertFalse("iş bitmedi", o.isRunning());
    }

    /** Kalemin hedef malzemelerini TEK TEK ekler (v0.6) ve "Malzemeleri bitir" der. Her ekleme bitene kadar beklenir. */
    static void addTargetAndFinish(GameEngine e, Order o, int item) {
        for (String id : e.getToppingTarget(o.id, item)) {
            assertEquals("ekleme " + id, StartResult.STARTED, e.addTopping(o.id, item, id));
            waitDone(e, o);
        }
        assertEquals(StartResult.STARTED, e.finishToppings(o.id, item));
    }

    /** Verilen kalemin verilen istasyondaki adımını başlatır (topping ise hedef kümeyi tek tek ekleyip bitirir) ve bitirir. */
    static void doItemStation(GameEngine e, Order o, int item, Station st) {
        if (st == Station.SOS && o.getLine(item).product.hasToppings() && o.getLine(item).isStepOpen(o.getLine(item).product.toppingStepIndex())) {
            addTargetAndFinish(e, o, item);
            return;
        }
        assertEquals(StartResult.STARTED, e.startStep(o.id, item, st.id));
        waitDone(e, o);
    }

    /** Siparişin önerilen sıradaki adımını yapar (topping: hedef küme tek tek eklenir). */
    static void doNext(GameEngine e, Order o) {
        Step s = o.nextStep();
        assertNotNull("öneri yok", s);
        if (s.isTopping()) {
            int li = e.openToppingItem(o.id);
            assertTrue(li >= 0);
            addTargetAndFinish(e, o, li);
            return;
        }
        assertEquals(StartResult.STARTED, e.startStep(o.id, -1, s.station.id));
        waitDone(e, o);
    }

    /** Sipariş paketlenene (hazır) kadar tüm adımları yapar. */
    static void makeReady(GameEngine e, Order o) {
        for (int guard = 0; guard < 30 && !o.isReady(); guard++) doNext(e, o);
        assertTrue(o.isReady());
    }

    /** Ürün adlarından kalem indeksi. */
    static int itemOf(Order o, String productId) {
        for (int i = 0; i < o.lineCount(); i++) if (o.getLine(i).product.id.equals(productId)) return i;
        return -1;
    }

    /** Belirli bir gün için, siparişin sonucunu görmek üzere yardımcı. */
    static Set<String> target(GameEngine e, Order o, int li) { return e.getToppingTarget(o.id, li); }
}
