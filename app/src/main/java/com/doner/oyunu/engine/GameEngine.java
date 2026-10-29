package com.doner.oyunu.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Bir günün deterministik simülasyonu. Saf Java. */
public final class GameEngine {
    public enum State { READY, RUNNING, FINISHED, FAILED }

    private static final float MAX_SUBSTEP = 0.1f;
    private static final float MIN_STEP_SECONDS = 0.8f;
    private static final double WRONG_STATION_PENALTY = 0.02;

    private final SaveData save;
    private final SaveData snapshot;
    private final Upgrades upgrades;
    private final DayConfig cfg;
    private final Random rng;
    private final int day;
    private final List<GameListener> listeners = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private final List<ActiveStep> active = new ArrayList<>();
    private final DaySummary summary = new DaySummary();

    private State state = State.READY;
    private float time;
    private float spawnTimer = 1.5f;
    private int nextOrderId = 1;
    private int selectedId = -1;
    private int nameCursor;

    /** save: motor bu nesneyi (para, stok, yıldız, gün) doğrudan günceller. */
    public GameEngine(long seed, SaveData save) {
        this.save = save;
        this.snapshot = save.copy();
        this.upgrades = new Upgrades(save);
        this.day = save.day;
        this.cfg = DayConfig.forDay(day);
        this.rng = new Random(seed);
        this.nameCursor = rng.nextInt(Catalog.NAMES.length);
        summary.day = day;
        summary.startStars = save.stars;
    }

    // ---------------------------------------------------------------- dinleyiciler
    public void addListener(GameListener l) { listeners.add(l); }
    public void removeListener(GameListener l) { listeners.remove(l); }

    // ---------------------------------------------------------------- durum sorguları
    public State getState() { return state; }
    public boolean isRunning() { return state == State.RUNNING || state == State.READY; }
    public int getDay() { return day; }
    public DayConfig getConfig() { return cfg; }
    public SaveData getSave() { return save; }
    public int getMoney() { return save.money; }
    public double getStars() { return save.stars; }
    public float getElapsed() { return time; }
    /** Hizmet süresinin kalanı (0'a iner; sonrasında mesai uzatması sürer). */
    public float getTimeLeft() { return Math.max(0f, cfg.duration - time); }
    public boolean isOvertime() { return time >= cfg.duration; }
    public int getSlotCount() { return upgrades.slots(); }
    public int getFreeSlots() { return upgrades.slots() - active.size(); }
    public DaySummary getSummary() { return summary; }
    public Upgrades getUpgrades() { return upgrades; }
    public List<Order> getOrders() { return Collections.unmodifiableList(new ArrayList<>(orders)); }
    public List<ActiveStep> getActiveSteps() { return Collections.unmodifiableList(new ArrayList<>(active)); }
    public int getSelectedOrderId() { return selectedId; }

    public Order getOrder(int id) {
        for (Order o : orders) if (o.id == id) return o;
        return null;
    }

    public Order getSelectedOrder() { return getOrder(selectedId); }

    public ActiveStep getActiveFor(int orderId) {
        for (ActiveStep a : active) if (a.order.id == orderId) return a;
        return null;
    }

    /** Verilen siparişin bir sonraki adımı için eksik ilk malzeme (yoksa null). */
    public Ingredient missingIngredient(int orderId) {
        List<Ingredient> all = missingIngredients(orderId);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Verilen siparişin bir sonraki adımı için eksik TÜM malzemeler (eksik yoksa boş liste). */
    public List<Ingredient> missingIngredients(int orderId) {
        List<Ingredient> out = new ArrayList<>();
        Order o = getOrder(orderId);
        if (o == null || o.nextStep() == null) return out;
        for (java.util.Map.Entry<String, Integer> e : o.nextStep().needs.entrySet()) {
            if (!save.inventory.has(e.getKey(), e.getValue())) {
                Ingredient ing = Catalog.ingredient(e.getKey());
                if (ing != null) out.add(ing);
            }
        }
        return out;
    }

    /** Bir malzeme için, sıradaki adımın ihtiyacını karşılayacak acil alım parti sayısı (en az 1). */
    private int emergencyBatches(Order o, Ingredient ing) {
        Integer need = o.nextStep().needs.get(ing.id);
        int lack = (need == null ? 1 : need) - save.inventory.get(ing.id);
        return Math.max(1, (lack + Ingredient.EMERGENCY_BATCH - 1) / Ingredient.EMERGENCY_BATCH);
    }

    /** Eksik tüm malzemeleri tek seferde acil almanın toplam maliyeti (eksik yoksa 0). */
    public int emergencyCostForMissing(int orderId) {
        Order o = getOrder(orderId);
        int total = 0;
        for (Ingredient ing : missingIngredients(orderId)) {
            total += ing.emergencyPrice() * Ingredient.EMERGENCY_BATCH * emergencyBatches(o, ing);
        }
        return total;
    }

    /**
     * Siparişin sıradaki adımı için eksik TÜM malzemeleri tek hamlede acil alır (ihtiyaç tek partiyi aşarsa
     * gereken parti kadar). Hepsine para yetmezse hiçbiri alınmaz, {@code onNotEnoughMoney(toplam)} bildirilir.
     */
    public boolean emergencyBuyMissing(int orderId) {
        if (state == State.FINISHED || state == State.FAILED) return false;
        Order o = getOrder(orderId);
        List<Ingredient> missing = missingIngredients(orderId);
        if (o == null || missing.isEmpty()) return false;
        int total = emergencyCostForMissing(orderId);
        if (save.money < total) {
            for (GameListener l : snap()) l.onNotEnoughMoney(total);
            return false;
        }
        for (Ingredient ing : missing) {
            int batches = emergencyBatches(o, ing);
            for (int i = 0; i < batches; i++) emergencyBuy(ing.id);
        }
        return true;
    }

    // ---------------------------------------------------------------- eylemler
    /** Siparişi seçer; ardından sıradaki adım ipucu verilir. */
    public boolean selectOrder(int orderId) {
        Order o = getOrder(orderId);
        if (o == null) return false;
        selectedId = orderId;
        for (GameListener l : snap()) l.onOrderSelected(o);
        hint(o);
        return true;
    }

    /** Sıradaki siparişe geçer (döngüsel). Sipariş yoksa false. */
    public boolean selectNextOrder() {
        if (orders.isEmpty()) return false;
        int idx = -1;
        for (int i = 0; i < orders.size(); i++) if (orders.get(i).id == selectedId) idx = i;
        return selectOrder(orders.get((idx + 1) % orders.size()).id);
    }

    /** Seçili siparişin sıradaki adımını verilen istasyonda başlatmayı dener. */
    public StartResult startStep(int orderId, int stationId) {
        if (state == State.FINISHED || state == State.FAILED) return StartResult.NOT_RUNNING;
        ensureStarted();
        Order o = getOrder(orderId);
        if (o == null) return StartResult.NO_ORDER;
        if (o.ready) return StartResult.ORDER_READY;
        if (o.running) return StartResult.ORDER_BUSY;
        Step step = o.nextStep();
        Station tapped = Station.byId(stationId);
        if (tapped != step.station) {
            summary.wrongTaps++;
            for (GameListener l : snap()) l.onWrongStation(o, tapped, step.station);
            changeStars(-WRONG_STATION_PENALTY);
            return StartResult.WRONG_STATION;
        }
        if (active.size() >= upgrades.slots()) {
            for (GameListener l : snap()) l.onNoFreeSlot(o);
            return StartResult.NO_SLOT;
        }
        Ingredient missing = missingIngredient(orderId);
        if (missing != null) {
            for (GameListener l : snap()) l.onOutOfStock(o, missing);
            return StartResult.OUT_OF_STOCK;
        }
        save.inventory.consumeAll(step.needs);
        float dur = (float) (step.baseSeconds * upgrades.speedMult(step.station) * save.tempo);
        dur = Math.max(MIN_STEP_SECONDS, dur);
        int slot = 0;
        while (slotUsed(slot)) slot++;
        ActiveStep a = new ActiveStep(o, step, dur, slot);
        active.add(a);
        o.running = true;
        for (GameListener l : snap()) l.onStepStarted(o, step, slot, dur);
        return StartResult.STARTED;
    }

    /** Acil alım: +%50 fiyatla {@link Ingredient#EMERGENCY_BATCH} birim. */
    public boolean emergencyBuy(String ingredientId) {
        Ingredient ing = Catalog.ingredient(ingredientId);
        if (ing == null || state == State.FINISHED || state == State.FAILED) return false;
        int qty = Ingredient.EMERGENCY_BATCH;
        int cost = ing.emergencyPrice() * qty;
        if (save.money < cost) {
            for (GameListener l : snap()) l.onNotEnoughMoney(cost);
            return false;
        }
        save.money -= cost;
        save.inventory.add(ingredientId, qty);
        summary.emergencySpend += cost;
        for (GameListener l : snap()) l.onEmergencyPurchase(ing, qty, cost);
        return true;
    }

    /** Hazır siparişi teslim eder. Hazır değilse false. */
    public boolean deliver(int orderId) {
        Order o = getOrder(orderId);
        if (o == null || !o.ready || state == State.FINISHED || state == State.FAILED) return false;
        float frac = o.customer.patienceFraction();
        boolean fast = frac >= 0.5f;
        boolean happy = frac >= 0.2f;
        int price = o.price();
        double tipRate = fast ? 0.20 : (happy ? 0.08 : 0.0);
        int tip = (int) Math.round(price * tipRate * o.customer.type.tipMult * upgrades.tipMult());
        save.money += price + tip;
        summary.revenue += price;
        summary.tips += tip;
        summary.delivered++;
        remove(o);
        for (GameListener l : snap()) l.onOrderDelivered(o, price, tip, happy);
        double gain = fast ? 0.12 : (happy ? 0.08 : 0.02);
        if (o.customer.type.id.equals("influencer") && fast) gain += 0.05;
        changeStars(gain * upgrades.starGainMult());
        checkEnd();
        return true;
    }

    // ---------------------------------------------------------------- zaman
    public void tick(float dt) {
        if (state == State.FINISHED || state == State.FAILED) return;
        ensureStarted();
        while (dt > 1e-6f && (state == State.RUNNING)) {
            float h = Math.min(dt, MAX_SUBSTEP);
            step(h);
            dt -= h;
        }
    }

    /** Gün henüz başlamadıysa başlatır ve onDayStarted'i tam bir kez bildirir. */
    private void ensureStarted() {
        if (state == State.READY) start();
    }

    private void start() {
        state = State.RUNNING;
        for (GameListener l : snap()) l.onDayStarted(day, cfg);
    }

    private void step(float h) {
        time += h;
        // 1) çalışan adımlar
        for (ActiveStep a : new ArrayList<>(active)) {
            a.remaining -= h;
            if (a.remaining <= 0f) finishStep(a);
        }
        if (state != State.RUNNING) return;
        // 2) sabır
        for (Order o : new ArrayList<>(orders)) {
            Customer c = o.customer;
            c.patienceLeft -= h;
            if (c.patienceLeft <= 0f) {
                customerLeaves(o);
                if (state != State.RUNNING) return;
                continue;
            }
            float f = c.patienceFraction();
            int lvl = f <= 0.2f ? 2 : (f <= 0.5f ? 1 : 0);
            if (lvl > c.warnLevel) {
                c.warnLevel = lvl;
                for (GameListener l : snap()) l.onPatienceWarning(o, lvl);
            }
        }
        // 3) geliş
        if (time < cfg.duration) {
            spawnTimer -= h;
            if (spawnTimer <= 0f) {
                if (orders.size() < maxQueue()) {
                    spawnCustomer();
                    double mult = DayConfig.starMultiplier(save.stars) * upgrades.arrivalMult();
                    spawnTimer = (float) (cfg.arrivalInterval * (0.75 + 0.5 * rng.nextDouble()) / mult);
                } else {
                    spawnTimer = 0.5f;
                }
            }
        }
        // 4) gün sonu
        checkEnd();
    }

    private int maxQueue() { return cfg.baseQueue + upgrades.queueBonus(); }

    private void checkEnd() {
        if (state != State.RUNNING) return;
        if (time >= cfg.duration && orders.isEmpty()) {
            finishDay();
        } else if (time >= cfg.duration + cfg.overtime) {
            // Mesai bitti: kalan siparişler cezasız kapanır.
            for (Order o : new ArrayList<>(orders)) remove(o);
            finishDay();
        }
    }

    // ---------------------------------------------------------------- iç mantık
    private void finishStep(ActiveStep a) {
        active.remove(a);
        Order o = a.order;
        o.running = false;
        o.nextStep++;
        boolean complete = o.nextStep >= o.steps.size();
        for (GameListener l : snap()) l.onStepFinished(o, a.step, complete);
        if (complete) {
            o.ready = true;
            for (GameListener l : snap()) l.onOrderReady(o);
        } else if (o.id == selectedId) {
            hint(o);
        }
    }

    private void hint(Order o) {
        Step s = o.nextStep();
        if (s != null && !o.running) for (GameListener l : snap()) l.onNextStepHint(o, s);
    }

    private void customerLeaves(Order o) {
        ActiveStep a = getActiveFor(o.id);
        if (a != null) active.remove(a);
        remove(o);
        summary.lost++;
        for (GameListener l : snap()) l.onCustomerLeft(o);
        changeStars(-0.30);
        checkEnd();
    }

    private void remove(Order o) {
        orders.remove(o);
        if (selectedId == o.id) selectedId = -1;
    }

    private boolean slotUsed(int slot) {
        for (ActiveStep a : active) if (a.slot == slot) return true;
        return false;
    }

    private void changeStars(double delta) {
        double old = save.stars;
        double now = Math.min(5.0, old + delta);
        save.stars = now;
        if (Math.abs(now - old) > 1e-9) {
            for (GameListener l : snap()) l.onStarsChanged(old, now);
        }
        if (now < 1.0 && state == State.RUNNING) failDay();
    }

    private void spawnCustomer() {
        List<CustomerType> types = Catalog.typesForDay(day);
        double total = 0;
        double[] w = new double[types.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = types.get(i).weight * (types.get(i).id.equals("turist") ? upgrades.touristMult() : 1.0);
            total += w[i];
        }
        double r = rng.nextDouble() * total;
        CustomerType type = types.get(types.size() - 1);
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r <= 0) { type = types.get(i); break; }
        }
        List<Product> menu = Catalog.productsForDay(day);
        int n = 1;
        while (n < cfg.maxItems && rng.nextDouble() < cfg.multiItemChance + type.extraItemChance * 0.5f) n++;
        List<Product> items = new ArrayList<>();
        for (int k = 0; k < n; k++) items.add(pickProduct(menu, type));
        String name = Catalog.NAMES[nameCursor++ % Catalog.NAMES.length];
        Order tmp = new Order(nextOrderId++, null, items);
        double pat = (type.patienceSeconds * cfg.patienceFactor + tmp.workSeconds() * cfg.workPatience)
                * upgrades.patienceMult() * save.tempo;
        Customer c = new Customer(name, type, (float) pat);
        Order o = new Order(tmp.id, c, items);
        int pos = 1;
        while (positionUsed(pos)) pos++;
        o.position = pos;
        orders.add(o);
        for (GameListener l : snap()) l.onCustomerArrived(o);
    }

    private boolean positionUsed(int pos) {
        for (Order o : orders) if (o.position == pos) return true;
        return false;
    }

    private Product pickProduct(List<Product> menu, CustomerType type) {
        double total = 0;
        double[] w = new double[menu.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = type.prefers(menu.get(i).id) ? 3.0 : 1.0;
            total += w[i];
        }
        double r = rng.nextDouble() * total;
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r <= 0) return menu.get(i);
        }
        return menu.get(menu.size() - 1);
    }

    private void finishDay() {
        state = State.FINISHED;
        int rent = Math.max(0, Math.min(cfg.rent, save.money));
        save.money -= rent;
        summary.rentDue = cfg.rent;
        summary.rent = rent; // gerçekten düşülen tutar
        summary.endStars = save.stars;
        summary.moneyAfter = save.money;
        summary.failed = false;
        StringBuilder sb = new StringBuilder();
        for (Product p : Catalog.newProductsOnDay(day + 1)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p.name);
        }
        summary.newProductsNext = sb.toString();
        save.day = day + 1;
        if (save.day > save.bestDay) save.bestDay = save.day;
        for (GameListener l : snap()) l.onDayFinished(summary);
    }

    private void failDay() {
        state = State.FAILED;
        summary.failed = true;
        summary.endStars = save.stars;
        orders.clear();
        active.clear();
        selectedId = -1;
        // Para ve stok kaybı yok: gün başı durumuna dön; yıldız en az 1.5.
        save.money = snapshot.money;
        save.inventory = snapshot.inventory.copy();
        save.stars = Math.max(snapshot.stars, 1.5);
        summary.moneyAfter = save.money;
        for (GameListener l : snap()) l.onDayFailed(summary);
    }

    private List<GameListener> snap() { return new ArrayList<>(listeners); }
}
