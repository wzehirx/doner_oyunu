package com.doner.oyunu.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Bir günün deterministik simülasyonu. Saf Java.
 * Durumlar: READY -> (PREP) -> RUNNING -> FINISHED | FAILED. PREP, müşteri gelmeden önceki hazırlık aşamasıdır
 * (gün süresine sayılmaz). READY iken tick() veya bir sipariş eylemi günü kendiliğinden başlatır.
 */
public final class GameEngine {
    public enum State { READY, PREP, RUNNING, FINISHED, FAILED }

    private static final float MAX_SUBSTEP = 0.1f;
    private static final float MIN_STEP_SECONDS = 0.8f;
    private static final float MIN_READY_SECONDS = 0.5f;
    private static final double WRONG_STATION_PENALTY = 0.02;
    private static final float READY_MEAT_SECONDS = 0.8f;
    private static final float READY_DRINK_SECONDS = 0.5f;
    private static final float TOPPING_SECONDS_EACH = 0.3f;
    private static final float APPRENTICE_SLOWDOWN = 1.2f;
    private static final double MISTAKE_PENALTY_EACH = 0.05;
    private static final double MISTAKE_PENALTY_MAX = 0.15;
    private static final double PERFECT_BONUS = 0.02;

    private final SaveData save;
    private final SaveData snapshot;
    private final Upgrades upgrades;
    private final DayConfig cfg;
    private final Random rng;
    private final int day;
    private final List<Product> menu;
    private final List<GameListener> listeners = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private final List<ActiveStep> active = new ArrayList<>();
    private final DaySummary summary = new DaySummary();
    /** Hazır (dilimlenmiş) döner: döner malzeme id -> porsiyon. Gün sonunda atılır. */
    private final Map<String, Integer> readyMeat = new LinkedHashMap<>();
    /** Hazır tepsi: malzeme id -> kalan kullanım. Gün sonunda atılır. */
    private final Map<String, Integer> trayUses = new LinkedHashMap<>();
    /** Son OUT_OF_STOCK'a düşen adım: sipariş id -> {kalem, adım}. */
    private final Map<Integer, int[]> blocked = new HashMap<>();

    private State state = State.READY;
    private float time;
    private float spawnTimer = 1.5f;
    private int nextOrderId = 1;
    private int selectedId = -1;
    private int selectedItem = -1;
    private int nameCursor;
    private boolean spawning = true;

    /** save: motor bu nesneyi (para, stok, yıldız, gün) doğrudan günceller. */
    public GameEngine(long seed, SaveData save) {
        this.save = save;
        this.snapshot = save.copy();
        this.upgrades = new Upgrades(save);
        this.day = save.day;
        this.cfg = DayConfig.forDay(day);
        this.menu = Catalog.productsForDay(day);
        this.rng = new Random(seed);
        this.nameCursor = rng.nextInt(Catalog.NAMES.length);
        summary.day = day;
        summary.startStars = save.stars;
    }

    /** Otomatik müşteri gelişini kapatır/açar (öğretici ve senaryo testleri için; varsayılan açık). */
    public void setSpawning(boolean on) { spawning = on; }

    // ---------------------------------------------------------------- dinleyiciler
    public void addListener(GameListener l) { listeners.add(l); }
    public void removeListener(GameListener l) { listeners.remove(l); }

    // ---------------------------------------------------------------- durum sorguları
    public State getState() { return state; }
    /** Gün bitmedi (READY, PREP veya RUNNING). */
    public boolean isRunning() { return state == State.RUNNING || state == State.READY || state == State.PREP; }
    public boolean isPrepPhase() { return state == State.PREP; }
    public int getDay() { return day; }
    public DayConfig getConfig() { return cfg; }
    public SaveData getSave() { return save; }
    public int getMoney() { return save.money; }
    public double getStars() { return save.stars; }
    public float getElapsed() { return time; }
    /** Hizmet süresinin kalanı (0'a iner; sonrasında mesai uzatması sürer). Hazırlık aşamasında tam gün süresi. */
    public float getTimeLeft() { return Math.max(0f, cfg.duration - time); }
    public boolean isOvertime() { return time >= cfg.duration; }
    public int getSlotCount() { return upgrades.slots(); }
    /** Oyuncunun boş yuvası (çırak işleri yuva kullanmaz). */
    public int getFreeSlots() { return upgrades.slots() - playerActiveCount(); }
    public DaySummary getSummary() { return summary; }
    public Upgrades getUpgrades() { return upgrades; }
    public List<Order> getOrders() { return Collections.unmodifiableList(new ArrayList<>(orders)); }
    /** Tüm çalışan işler (oyuncu + çırak + hazırlık). */
    public List<ActiveStep> getActiveSteps() { return Collections.unmodifiableList(new ArrayList<>(active)); }
    public int getSelectedOrderId() { return selectedId; }
    /** Seçili sipariş içinde seçili kalem (-1 = otomatik). */
    public int getSelectedItem() { return selectedItem; }
    /** Çırak seviyesi (0..3) = otomatik işçi sayısı. */
    public int getApprenticeLevel() { return upgrades.apprenticeLevel(); }

    public Order getOrder(int id) {
        for (Order o : orders) if (o.id == id) return o;
        return null;
    }

    public Order getSelectedOrder() { return getOrder(selectedId); }

    /** Siparişin çalışan ilk işi (oyuncu işi öncelikli); yoksa null. */
    public ActiveStep getActiveFor(int orderId) {
        ActiveStep any = null;
        for (ActiveStep a : active) {
            if (a.order != null && a.order.id == orderId) {
                if (a.worker == 0) return a;
                if (any == null) any = a;
            }
        }
        return any;
    }

    /** Belirli kalemde çalışan iş (kalem adımı); yoksa null. Paketleme için itemIndex = -1. */
    public ActiveStep getActiveForItem(int orderId, int itemIndex) {
        for (ActiveStep a : active) if (a.order != null && a.order.id == orderId && a.itemIndex == itemIndex) return a;
        return null;
    }

    private int playerActiveCount() {
        int n = 0;
        for (ActiveStep a : active) if (a.worker == 0) n++;
        return n;
    }

    // ---------------------------------------------------------------- hazır stok sorguları
    public int getReadyMeat(String meatId) { Integer v = readyMeat.get(meatId); return v == null ? 0 : v; }
    /** Toplam hazır (dilimlenmiş) döner porsiyonu. */
    public int getReadyDoner() { int t = 0; for (int v : readyMeat.values()) t += v; return t; }
    public int getReadyDrinks() { return save.readyDrinks; }
    /** Hazır tepsideki malzemenin kalan kullanımı. */
    public int getTrayUses(String ingredientId) { Integer v = trayUses.get(ingredientId); return v == null ? 0 : v; }
    public int getTrayTotal() { int t = 0; for (int v : trayUses.values()) t += v; return t; }
    /** Kapasite: DONER ve DRINK için toplam adet (döner malzeme başına), TRAY için malzeme başına kullanım. */
    public int getReadyCapacity(PrepKind kind) { return upgrades.readyCapacity(kind); }

    /** Bugünün menüsünde kullanılan döner malzemeleri (hazırlanabilir): ET kategorisi. */
    public List<Ingredient> getPrepMeats() {
        List<Ingredient> out = new ArrayList<>();
        for (Product p : menu) {
            for (Step s : p.steps) {
                String m = s.meatId();
                Ingredient i = m == null ? null : Catalog.ingredient(m);
                if (i != null && !out.contains(i)) out.add(i);
            }
        }
        return out;
    }

    /** Hazır tepsiye konabilecek malzemeler: bugünün menüsündeki topping havuzlarının birleşimi (katalog sırası). */
    public List<Ingredient> getPrepTrayChoices() {
        Set<String> ids = new LinkedHashSet<>();
        for (Product p : menu) ids.addAll(p.toppingPool);
        List<Ingredient> out = new ArrayList<>();
        for (Ingredient i : Catalog.ingredients()) if (ids.contains(i.id)) out.add(i);
        return out;
    }

    /** Bugünün menüsünde ayran var mı. */
    public boolean menuHasDrinkPrep() {
        for (Product p : menu) for (Step s : p.steps) if (s.isDrinkFill()) return true;
        return false;
    }

    // ---------------------------------------------------------------- eksik malzeme / acil alım
    /** Eksik malzeme sorgusunun baktığı adım: son OUT_OF_STOCK adımı, yoksa önerilen adım (topping hariç). */
    private Step missingStep(int orderId) {
        Order o = getOrder(orderId);
        if (o == null) return null;
        int[] ref = blocked.get(orderId);
        if (ref != null) {
            OrderLine line = o.getLine(ref[0]);
            if (line.isStepOpen(ref[1])) return line.step(ref[1]);
        }
        int[] r = o.suggest(orderId == selectedId ? selectedItem : -1);
        if (r == null || r[0] < 0) return null;
        Step s = o.getLine(r[0]).step(r[1]);
        return s.isTopping() ? null : s;
    }

    /** Verilen siparişin (son engellenen veya önerilen) adımı için eksik ilk malzeme (yoksa null). */
    public Ingredient missingIngredient(int orderId) {
        List<Ingredient> all = missingIngredients(orderId);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Eksik TÜM malzemeler (eksik yoksa boş liste). */
    public List<Ingredient> missingIngredients(int orderId) {
        List<Ingredient> out = new ArrayList<>();
        Step s = missingStep(orderId);
        if (s == null) return out;
        for (Map.Entry<String, Integer> e : effectiveNeeds(s).entrySet()) {
            if (!save.inventory.has(e.getKey(), e.getValue())) {
                Ingredient ing = Catalog.ingredient(e.getKey());
                if (ing != null) out.add(ing);
            }
        }
        return out;
    }

    private int emergencyBatches(Step s, Ingredient ing) {
        Integer need = effectiveNeeds(s).get(ing.id);
        int lack = (need == null ? 1 : need) - save.inventory.get(ing.id);
        return Math.max(1, (lack + Ingredient.EMERGENCY_BATCH - 1) / Ingredient.EMERGENCY_BATCH);
    }

    /** Eksik tüm malzemeleri tek seferde acil almanın toplam maliyeti (eksik yoksa 0). */
    public int emergencyCostForMissing(int orderId) {
        Step s = missingStep(orderId);
        int total = 0;
        for (Ingredient ing : missingIngredients(orderId)) {
            total += ing.emergencyPrice() * Ingredient.EMERGENCY_BATCH * emergencyBatches(s, ing);
        }
        return total;
    }

    /**
     * Siparişin engellenen/önerilen adımı için eksik TÜM malzemeleri tek hamlede acil alır. Hepsine para
     * yetmezse hiçbiri alınmaz, {@code onNotEnoughMoney(toplam)} bildirilir.
     */
    public boolean emergencyBuyMissing(int orderId) {
        if (state == State.FINISHED || state == State.FAILED) return false;
        Step s = missingStep(orderId);
        List<Ingredient> missing = missingIngredients(orderId);
        if (s == null || missing.isEmpty()) return false;
        int total = emergencyCostForMissing(orderId);
        if (save.money < total) {
            for (GameListener l : snap()) l.onNotEnoughMoney(total);
            return false;
        }
        for (Ingredient ing : missing) {
            int batches = emergencyBatches(s, ing);
            for (int i = 0; i < batches; i++) emergencyBuy(ing.id);
        }
        return true;
    }

    /** Hazır stok kullanıldığında adımın gerçekten tüketeceği malzemeler. */
    private Map<String, Integer> effectiveNeeds(Step s) {
        Map<String, Integer> m = new LinkedHashMap<>(s.needs);
        if (usesReadyMeat(s)) m.remove(s.meatId());
        if (usesReadyDrink(s)) m.remove(Catalog.AYRAN);
        return m;
    }

    private boolean usesReadyMeat(Step s) {
        String meat = s.meatId();
        if (meat == null) return false;
        Integer need = s.needs.get(meat);
        return need != null && getReadyMeat(meat) >= need;
    }

    private boolean usesReadyDrink(Step s) {
        if (!s.isDrinkFill()) return false;
        Integer need = s.needs.get(Catalog.AYRAN);
        return need != null && save.readyDrinks >= need;
    }

    // ---------------------------------------------------------------- gün aşamaları
    /**
     * Gün öncesi hazırlık aşamasını başlatır (READY -> PREP). Bu aşamada müşteri gelmez, süre akmaz;
     * oyuncu startPrep ile hazırlık yapar, çırak (varsa) kendisi de hazırlık yapar. false: zaten başlamış.
     */
    public boolean beginPrep() {
        if (state != State.READY) return false;
        state = State.PREP;
        for (GameListener l : snap()) l.onPrepPhaseStarted(day);
        return true;
    }

    /** "Dükkânı aç": hazırlık aşamasını (varsa) bitirip günü başlatır. false: gün zaten başlamış/bitmiş. */
    public boolean startDay() {
        if (state != State.READY && state != State.PREP) return false;
        boolean wasPrep = state == State.PREP;
        state = State.RUNNING;
        if (wasPrep) for (GameListener l : snap()) l.onPrepPhaseEnded(day);
        for (GameListener l : snap()) l.onDayStarted(day, cfg);
        return true;
    }

    // ---------------------------------------------------------------- seçim eylemleri
    /** Siparişi seçer; ardından önerilen adım ipucu verilir. */
    public boolean selectOrder(int orderId) {
        Order o = getOrder(orderId);
        if (o == null) return false;
        selectedId = orderId;
        selectedItem = -1;
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

    /** Seçili siparişin bir kalemini seçer (istasyon dokunuşları önce bu kalemde çözülür). -1: otomatik. */
    public boolean selectItem(int itemIndex) {
        Order o = getSelectedOrder();
        if (o == null || itemIndex < -1 || itemIndex >= o.lineCount()) return false;
        selectedItem = itemIndex;
        if (itemIndex >= 0) for (GameListener l : snap()) l.onItemSelected(o, itemIndex);
        hint(o);
        return true;
    }

    // ---------------------------------------------------------------- sipariş adımları
    /** Kalem belirtmeden: seçili kalem öncelikli, sonra ilk uygun kalem; PAKET için paketleme. */
    public StartResult startStep(int orderId, int stationId) { return startStep(orderId, -1, stationId); }

    /**
     * Siparişin (verilen kalemin; itemIndex < 0: herhangi bir kalem; PAKET istasyonu her zaman siparişin ortak paketlemesidir) o istasyondaki açık adımını
     * başlatmayı dener. İstasyon tek bir uygun adıma çözülür. Topping adımında zamanlayıcı BAŞLAMAZ:
     * NEEDS_TOPPINGS döner ve {@code onToppingSelectionNeeded} verilir; UI malzemeleri seçtirip
     * {@link #confirmToppings} çağırır.
     */
    public StartResult startStep(int orderId, int itemIndex, int stationId) {
        if (state == State.FINISHED || state == State.FAILED) return StartResult.NOT_RUNNING;
        ensureStarted();
        Order o = getOrder(orderId);
        if (o == null) return StartResult.NO_ORDER;
        if (o.ready) return StartResult.ORDER_READY;
        Station tapped = Station.byId(stationId);
        if (itemIndex >= o.lineCount()) return StartResult.INVALID;

        int foundLine = -2, foundStep = -1;
        boolean busy = false;
        Step lockedStep = null;
        List<Integer> order = new ArrayList<>();
        if (itemIndex >= 0) order.add(itemIndex);
        else {
            if (o.id == selectedId && selectedItem >= 0 && selectedItem < o.lineCount()) order.add(selectedItem);
            for (int i = 0; i < o.lineCount(); i++) if (!order.contains(i)) order.add(i);
        }
        outer:
        for (int li : order) {
            OrderLine line = o.getLine(li);
            for (int si = 0; si < line.stepCount(); si++) {
                Step s = line.step(si);
                if (s.station != tapped || line.isStepDone(si)) continue;
                if (line.isStepRunning(si)) { busy = true; continue; }
                if (!line.isStepUnlocked(si)) { if (lockedStep == null) lockedStep = s; continue; }
                foundLine = li;
                foundStep = si;
                break outer;
            }
        }
        if (foundLine == -2 && tapped == Station.PAKET) { // paketleme sipariş düzeyindedir; itemIndex yok sayılır
            if (o.packing) busy = true;
            else if (o.allLinesDone()) { foundLine = -1; }
            else if (lockedStep == null) lockedStep = o.getPackStep();
        }
        if (foundLine == -2) {
            if (busy) return StartResult.ORDER_BUSY;
            if (lockedStep != null) {
                for (GameListener l : snap()) l.onStepLocked(o, lockedStep);
                return StartResult.STEP_LOCKED;
            }
            summary.wrongTaps++;
            Step expected = o.nextStep();
            Station exp = expected != null ? expected.station : Station.PAKET;
            for (GameListener l : snap()) l.onWrongStation(o, tapped, exp);
            changeStars(-WRONG_STATION_PENALTY);
            return StartResult.WRONG_STATION;
        }
        if (playerActiveCount() >= upgrades.slots()) {
            for (GameListener l : snap()) l.onNoFreeSlot(o);
            return StartResult.NO_SLOT;
        }
        if (foundLine == -1) {
            launchPack(o, 0);
            return StartResult.STARTED;
        }
        OrderLine line = o.getLine(foundLine);
        Step step = line.step(foundStep);
        if (step.isTopping()) {
            for (GameListener l : snap()) l.onToppingSelectionNeeded(o, foundLine);
            return StartResult.NEEDS_TOPPINGS;
        }
        Map<String, Integer> need = effectiveNeeds(step);
        if (!save.inventory.hasAll(need)) {
            blocked.put(o.id, new int[]{foundLine, foundStep});
            Ingredient missing = null;
            for (String id : need.keySet()) if (!save.inventory.has(id, need.get(id))) { missing = Catalog.ingredient(id); break; }
            for (GameListener l : snap()) l.onOutOfStock(o, missing);
            return StartResult.OUT_OF_STOCK;
        }
        launchLineStep(o, foundLine, foundStep, 0);
        return StartResult.STARTED;
    }

    /**
     * Malzeme seçimini onaylar ve topping adımını başlatır. Tüm seçilenler stokta (veya hazır tepside) olmalı;
     * stok seçilenler kadar tüketilir (tepsi varsa önce tepsiden). Süre = temel + 0,3 sn/malzeme; tepsiyle karşılanan
     * kısım oranında %50'ye kadar kısalır. Doğrulama (hedefle karşılaştırma) TESLİMDE yapılır.
     */
    public StartResult confirmToppings(int orderId, int itemIndex, Collection<String> chosen) {
        if (state == State.FINISHED || state == State.FAILED) return StartResult.NOT_RUNNING;
        ensureStarted();
        Order o = getOrder(orderId);
        if (o == null) return StartResult.NO_ORDER;
        if (o.ready) return StartResult.ORDER_READY;
        if (itemIndex < 0 || itemIndex >= o.lineCount()) return StartResult.INVALID;
        OrderLine line = o.getLine(itemIndex);
        int ti = line.product.toppingStepIndex();
        if (ti < 0 || line.isStepDone(ti)) return StartResult.INVALID;
        if (line.isStepRunning(ti)) return StartResult.ORDER_BUSY;
        Step step = line.step(ti);
        if (!line.isStepUnlocked(ti)) {
            for (GameListener l : snap()) l.onStepLocked(o, step);
            return StartResult.STEP_LOCKED;
        }
        Set<String> want = new LinkedHashSet<>(chosen == null ? Collections.<String>emptyList() : chosen);
        for (String id : want) {
            if (!line.product.toppingPool.contains(id)) {
                for (GameListener l : snap()) l.onToppingsRejected(o, itemIndex, StartResult.INVALID, Catalog.ingredient(id));
                return StartResult.INVALID;
            }
        }
        if (playerActiveCount() >= upgrades.slots()) {
            for (GameListener l : snap()) l.onNoFreeSlot(o);
            return StartResult.NO_SLOT;
        }
        for (String id : want) {
            if (getTrayUses(id) <= 0 && !save.inventory.has(id, 1)) {
                for (GameListener l : snap()) l.onToppingsRejected(o, itemIndex, StartResult.OUT_OF_STOCK, Catalog.ingredient(id));
                return StartResult.OUT_OF_STOCK;
            }
        }
        // pool sırasında kaydet
        Set<String> ordered = new LinkedHashSet<>();
        for (String id : line.product.toppingPool) if (want.contains(id)) ordered.add(id);
        int covered = 0;
        for (String id : ordered) {
            if (getTrayUses(id) > 0) { trayUses.put(id, getTrayUses(id) - 1); covered++; }
            else save.inventory.add(id, -1);
        }
        double frac = ordered.isEmpty() ? 0.0 : covered / (double) ordered.size();
        double d = (step.baseSeconds + TOPPING_SECONDS_EACH * ordered.size()) * (1.0 - 0.5 * frac)
                * upgrades.speedMult(step.station) * save.tempo;
        float dur = (float) Math.max(MIN_STEP_SECONDS, d);
        line.setChosen(ordered);
        line.setRunning(ti, true);
        blocked.remove(o.id);
        ActiveStep a = new ActiveStep(o, itemIndex, ti, step, dur, freeSlot(), 0, null, null, false);
        active.add(a);
        for (GameListener l : snap()) l.onStepStarted(o, step, a.slot, dur);
        return StartResult.STARTED;
    }

    /** Malzeme seçimi panelinin seçenekleri (havuz sırasında). Kalem topping istemiyorsa boş liste. */
    public List<ToppingOption> getToppingOptions(int orderId, int itemIndex) {
        List<ToppingOption> out = new ArrayList<>();
        Order o = getOrder(orderId);
        if (o == null || itemIndex < 0 || itemIndex >= o.lineCount()) return out;
        Product p = o.getLine(itemIndex).product;
        boolean pre = DayConfig.toppingsPreselected(day);
        for (String id : p.toppingPool) {
            Ingredient ing = Catalog.ingredient(id);
            int stock = save.inventory.get(id);
            int tray = getTrayUses(id);
            boolean isDef = p.defaultToppings.contains(id);
            out.add(new ToppingOption(ing, stock > 0 || tray > 0, pre && isDef, isDef, stock, tray));
        }
        return out;
    }

    /** Panel açıldığında önceden seçili gelen malzemeler (1-3. gün varsayılan küme, sonra boş). */
    public Set<String> getInitialToppings(int orderId, int itemIndex) {
        Set<String> s = new LinkedHashSet<>();
        for (ToppingOption op : getToppingOptions(orderId, itemIndex)) if (op.selected) s.add(op.ingredient.id);
        return s;
    }

    /** Kalemin hedef malzeme kümesi ("Hedefi oku"): varsayılan ± müşteri istekleri. */
    public Set<String> getToppingTarget(int orderId, int itemIndex) {
        Order o = getOrder(orderId);
        if (o == null || itemIndex < 0 || itemIndex >= o.lineCount()) return new LinkedHashSet<>();
        return new LinkedHashSet<>(o.getLine(itemIndex).getTarget());
    }

    /** Siparişte henüz malzeme seçimi bekleyen (açık) topping adımı olan ilk kalem; yoksa -1. */
    public int openToppingItem(int orderId) {
        Order o = getOrder(orderId);
        if (o == null) return -1;
        for (OrderLine l : o.getLines()) {
            int ti = l.product.toppingStepIndex();
            if (ti >= 0 && l.isStepOpen(ti)) return l.index;
        }
        return -1;
    }

    // ---------------------------------------------------------------- hazırlık eylemleri
    /**
     * Hazırlık başlatır (oyuncu, bir yuva kullanır). DONER: ids = {döner malzeme id'si}; DRINK: ids yok sayılır;
     * TRAY: ids = SOS/SALATA malzemeleri (her biri 6 kullanım, hazırlanırken stoktan 6'şar birim düşer).
     * READY durumunda önce hazırlık aşamasına geçer. Hazırlık HER AN yapılabilir (gün içi dahil).
     */
    public PrepResult startPrep(PrepKind kind, Collection<String> ids) {
        if (state == State.FINISHED || state == State.FAILED) return PrepResult.NOT_ALLOWED;
        if (state == State.READY) beginPrep();
        if (playerActiveCount() >= upgrades.slots()) return PrepResult.NO_SLOT;
        PrepResult r = launchPrep(kind, ids, 0);
        return r;
    }

    public PrepResult startPrepDoner(String meatId) {
        return startPrep(PrepKind.DONER, Collections.singletonList(meatId));
    }

    public PrepResult startPrepDrink() { return startPrep(PrepKind.DRINK, Collections.<String>emptyList()); }

    public PrepResult startPrepTray(Collection<String> ingredientIds) { return startPrep(PrepKind.TRAY, ingredientIds); }

    private int pendingPrep(PrepKind kind, String id) {
        int n = 0;
        for (ActiveStep a : active) {
            if (a.prep != kind) continue;
            if (kind == PrepKind.DRINK || (a.ids != null && a.ids.contains(id))) n += kind.batch;
        }
        return n;
    }

    private Map<String, Integer> prepNeeds(PrepKind kind, Set<String> ids) {
        Map<String, Integer> m = new LinkedHashMap<>();
        switch (kind) {
            case DONER: for (String id : ids) m.put(id, kind.batch); break;
            case DRINK: m.put(Catalog.AYRAN, kind.batch); break;
            default: for (String id : ids) m.put(id, kind.batch); break;
        }
        return m;
    }

    /** Hazırlığı doğrular ve başlatır; worker 0 = oyuncu (yuva zaten kontrol edildi), >0 = çırak. */
    private PrepResult launchPrep(PrepKind kind, Collection<String> idsIn, int worker) {
        Set<String> ids = new LinkedHashSet<>(idsIn == null ? Collections.<String>emptyList() : idsIn);
        switch (kind) {
            case DONER: {
                if (ids.size() != 1) return reject(kind, PrepResult.INVALID, null);
                Ingredient m = Catalog.ingredient(ids.iterator().next());
                if (m == null || m.category != Ingredient.Category.ET) return reject(kind, PrepResult.INVALID, null);
                break;
            }
            case TRAY: {
                if (ids.isEmpty()) return reject(kind, PrepResult.INVALID, null);
                for (String id : ids) {
                    Ingredient i = Catalog.ingredient(id);
                    if (i == null || !i.isTopping()) return reject(kind, PrepResult.INVALID, null);
                }
                break;
            }
            default: ids.clear(); break;
        }
        // kapasite (devam eden hazırlıklar dahil)
        int cap = upgrades.readyCapacity(kind);
        switch (kind) {
            case DONER: {
                String id = ids.iterator().next();
                if (getReadyMeat(id) + pendingPrep(kind, id) + kind.batch > cap) return reject(kind, PrepResult.FULL, null);
                break;
            }
            case DRINK:
                if (save.readyDrinks + pendingPrep(kind, null) + kind.batch > cap) return reject(kind, PrepResult.FULL, null);
                break;
            default:
                for (String id : ids) {
                    if (getTrayUses(id) + pendingPrep(kind, id) + kind.batch > cap) return reject(kind, PrepResult.FULL, null);
                }
                break;
        }
        Map<String, Integer> need = prepNeeds(kind, ids);
        for (Map.Entry<String, Integer> e : need.entrySet()) {
            if (!save.inventory.has(e.getKey(), e.getValue())) {
                return reject(kind, PrepResult.OUT_OF_STOCK, Catalog.ingredient(e.getKey()));
            }
        }
        save.inventory.consumeAll(need);
        double d = kind.baseSeconds * upgrades.speedMult(kind.station) * save.tempo * (worker > 0 ? APPRENTICE_SLOWDOWN : 1f);
        float dur = (float) Math.max(MIN_STEP_SECONDS, d);
        Step syn = new Step(kind.displayName, kind.station, dur, Collections.<String, Integer>emptyMap());
        ActiveStep a = new ActiveStep(null, -1, -1, syn, dur, worker == 0 ? freeSlot() : -1, worker, kind, ids, false);
        active.add(a);
        for (GameListener l : snap()) l.onPrepStarted(kind, worker > 0, dur);
        if (worker > 0) {
            ApprenticeAction aa = new ApprenticeAction(worker, true, null, -1, syn, kind, false, dur);
            for (GameListener l : snap()) l.onApprenticeAction(aa);
        }
        return PrepResult.STARTED;
    }

    private PrepResult reject(PrepKind kind, PrepResult r, Ingredient ing) {
        for (GameListener l : snap()) l.onPrepRejected(kind, r, ing);
        return r;
    }

    // ---------------------------------------------------------------- diğer eylemler
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

    /** Hazır (paketlenmiş) siparişi teslim eder. Hazır değilse false. */
    public boolean deliver(int orderId) {
        Order o = getOrder(orderId);
        if (o == null || !o.ready || state == State.FINISHED || state == State.FAILED) return false;
        ToppingCheck check = checkToppings(o);
        int mistakes = check.mistakes();
        float frac = o.customer.patienceFraction();
        boolean fast = frac >= 0.5f;
        boolean happy = frac >= 0.2f;
        int price = o.price();
        double tipRate = fast ? 0.20 : (happy ? 0.08 : 0.0);
        if (mistakes > 0) tipRate *= 0.5;
        int tip = (int) Math.round(price * tipRate * o.customer.type.tipMult * upgrades.tipMult());
        save.money += price + tip;
        summary.revenue += price;
        summary.tips += tip;
        summary.delivered++;
        remove(o);
        double penalty = Math.min(MISTAKE_PENALTY_MAX, MISTAKE_PENALTY_EACH * mistakes);
        if (mistakes > 0) {
            summary.mistakes++;
            for (GameListener l : snap()) l.onOrderMistake(o, check, penalty);
        } else if (check.perfect()) {
            summary.perfect++;
            for (GameListener l : snap()) l.onOrderPerfect(o);
        }
        for (GameListener l : snap()) l.onOrderDelivered(o, price, tip, happy && mistakes == 0);
        double gain = fast ? 0.12 : (happy ? 0.08 : 0.02);
        if (o.customer.type.id.equals("influencer") && fast) gain += 0.05;
        gain *= upgrades.starGainMult();
        if (check.perfect()) gain += PERFECT_BONUS * upgrades.starGainMult();
        changeStars(gain);
        if (penalty > 0) changeStars(-penalty);
        checkEnd();
        return true;
    }

    private ToppingCheck checkToppings(Order o) {
        ToppingCheck c = new ToppingCheck();
        for (OrderLine line : o.getLines()) {
            if (!line.product.hasToppings()) continue;
            c.toppingLines++;
            Set<String> chosen = line.getChosen();
            if (chosen == null) chosen = new LinkedHashSet<>();
            Set<String> target = line.getTarget();
            for (String t : target) {
                if (!chosen.contains(t)) {
                    c.add(new ToppingCheck.Mistake(t, false, !line.product.defaultToppings.contains(t), line.product.name));
                }
            }
            for (String t : chosen) {
                if (!target.contains(t)) {
                    c.add(new ToppingCheck.Mistake(t, true, line.product.defaultToppings.contains(t), line.product.name));
                }
            }
        }
        return c;
    }

    // ---------------------------------------------------------------- zaman
    public void tick(float dt) {
        if (state == State.FINISHED || state == State.FAILED) return;
        ensureStarted();
        while (dt > 1e-6f && (state == State.RUNNING || state == State.PREP)) {
            float h = Math.min(dt, MAX_SUBSTEP);
            if (state == State.PREP) stepPrep(h); else step(h);
            dt -= h;
        }
    }

    /** Gün henüz başlamadıysa (READY) başlatır ve onDayStarted'i tam bir kez bildirir. */
    private void ensureStarted() {
        if (state == State.READY) startDay();
    }

    private void stepPrep(float h) {
        advanceTasks(h);
        runApprentices();
    }

    private void step(float h) {
        time += h;
        // 1) çalışan işler
        advanceTasks(h);
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
        if (spawning && time < cfg.duration) {
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
        // 4) çırak
        runApprentices();
        // 5) gün sonu
        checkEnd();
    }

    private int maxQueue() { return cfg.baseQueue + upgrades.queueBonus(); }

    private void advanceTasks(float h) {
        for (ActiveStep a : new ArrayList<>(active)) {
            if (!active.contains(a)) continue;
            a.remaining -= h;
            if (a.remaining <= 0f) finishTask(a);
        }
    }

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

    // ---------------------------------------------------------------- iç mantık: iş başlatma/bitirme
    private int freeSlot() {
        int slot = 0;
        while (slotUsed(slot)) slot++;
        return slot;
    }

    private boolean slotUsed(int slot) {
        for (ActiveStep a : active) if (a.worker == 0 && a.slot == slot) return true;
        return false;
    }

    private float lineStepSeconds(Step step, boolean readyMeat, boolean readyDrink, int worker) {
        double d;
        float min = MIN_STEP_SECONDS;
        if (readyMeat) { d = READY_MEAT_SECONDS; min = MIN_READY_SECONDS; }
        else if (readyDrink) { d = READY_DRINK_SECONDS; min = MIN_READY_SECONDS; }
        else d = step.baseSeconds * upgrades.speedMult(step.station);
        d *= save.tempo;
        if (worker > 0) d *= APPRENTICE_SLOWDOWN;
        return (float) Math.max(min, d);
    }

    /** Stok denetimi yapılmış açık bir kalem adımını başlatır (topping hariç). */
    private void launchLineStep(Order o, int li, int si, int worker) {
        OrderLine line = o.getLine(li);
        Step step = line.step(si);
        boolean rm = usesReadyMeat(step);
        boolean rd = usesReadyDrink(step);
        if (rm) {
            String m = step.meatId();
            readyMeat.put(m, getReadyMeat(m) - step.needs.get(m));
        }
        if (rd) save.readyDrinks -= step.needs.get(Catalog.AYRAN);
        Map<String, Integer> need = new LinkedHashMap<>(step.needs);
        if (rm) need.remove(step.meatId());
        if (rd) need.remove(Catalog.AYRAN);
        save.inventory.consumeAll(need);
        float dur = lineStepSeconds(step, rm, rd, worker);
        line.setRunning(si, true);
        blocked.remove(o.id);
        int slot = worker == 0 ? freeSlot() : -1;
        ActiveStep a = new ActiveStep(o, li, si, step, dur, slot, worker, null, null, false);
        active.add(a);
        if (worker == 0) {
            for (GameListener l : snap()) l.onStepStarted(o, step, slot, dur);
        } else {
            ApprenticeAction aa = new ApprenticeAction(worker, true, o, li, step, null, false, dur);
            for (GameListener l : snap()) l.onApprenticeAction(aa);
        }
    }

    private void launchPack(Order o, int worker) {
        Step step = o.getPackStep();
        double d = step.baseSeconds * upgrades.speedMult(step.station) * save.tempo * (worker > 0 ? APPRENTICE_SLOWDOWN : 1f);
        float dur = (float) Math.max(MIN_STEP_SECONDS, d);
        o.packing = true;
        blocked.remove(o.id);
        int slot = worker == 0 ? freeSlot() : -1;
        ActiveStep a = new ActiveStep(o, -1, -1, step, dur, slot, worker, null, null, true);
        active.add(a);
        if (worker == 0) {
            for (GameListener l : snap()) l.onStepStarted(o, step, slot, dur);
        } else {
            ApprenticeAction aa = new ApprenticeAction(worker, true, o, -1, step, null, true, dur);
            for (GameListener l : snap()) l.onApprenticeAction(aa);
        }
    }

    private void finishTask(ActiveStep a) {
        active.remove(a);
        if (a.prep != null) { finishPrep(a); return; }
        Order o = a.order;
        boolean player = a.worker == 0;
        if (a.pack) {
            o.packing = false;
            o.ready = true;
            if (player) for (GameListener l : snap()) l.onStepFinished(o, a.step, true);
            else {
                summary.apprenticeTasks++;
                ApprenticeAction aa = new ApprenticeAction(a.worker, false, o, -1, a.step, null, true, a.total);
                for (GameListener l : snap()) l.onApprenticeAction(aa);
            }
            for (GameListener l : snap()) l.onOrderReady(o);
            return;
        }
        OrderLine line = o.getLine(a.itemIndex);
        line.setDone(a.stepIndex);
        if (player) for (GameListener l : snap()) l.onStepFinished(o, a.step, false);
        else {
            summary.apprenticeTasks++;
            ApprenticeAction aa = new ApprenticeAction(a.worker, false, o, a.itemIndex, a.step, null, false, a.total);
            for (GameListener l : snap()) l.onApprenticeAction(aa);
        }
        if (line.isDone()) for (GameListener l : snap()) l.onItemFinished(o, a.itemIndex);
        if (o.allLinesDone() && !o.readyToPackAnnounced) {
            o.readyToPackAnnounced = true;
            for (GameListener l : snap()) l.onOrderReadyToPack(o);
        }
        if (o.id == selectedId) hint(o);
    }

    private void finishPrep(ActiveStep a) {
        PrepKind kind = a.prep;
        int amount = kind.batch;
        switch (kind) {
            case DONER: {
                String id = a.ids.iterator().next();
                readyMeat.put(id, getReadyMeat(id) + kind.batch);
                break;
            }
            case DRINK: save.readyDrinks += kind.batch; break;
            default:
                for (String id : a.ids) trayUses.put(id, getTrayUses(id) + kind.batch);
                break;
        }
        summary.prepBatches++;
        if (a.worker > 0) {
            ApprenticeAction aa = new ApprenticeAction(a.worker, false, null, -1, a.step, kind, false, a.total);
            for (GameListener l : snap()) l.onApprenticeAction(aa);
        }
        for (GameListener l : snap()) l.onPrepFinished(kind, a.worker > 0, amount);
    }

    private void hint(Order o) {
        int[] r = o.suggest(o.id == selectedId ? selectedItem : -1);
        if (r == null) return;
        Step s = r[0] < 0 ? o.getPackStep() : o.getLine(r[0]).step(r[1]);
        for (GameListener l : snap()) l.onNextStepHint(o, s);
    }

    private void customerLeaves(Order o) {
        for (ActiveStep a : new ArrayList<>(active)) if (a.order == o) active.remove(a);
        remove(o);
        summary.lost++;
        for (GameListener l : snap()) l.onCustomerLeft(o);
        changeStars(-0.30);
        checkEnd();
    }

    private void remove(Order o) {
        orders.remove(o);
        blocked.remove(o.id);
        if (selectedId == o.id) { selectedId = -1; selectedItem = -1; }
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

    // ---------------------------------------------------------------- çırak (otomatik işçi)
    private boolean workerBusy(int w) {
        for (ActiveStep a : active) if (a.worker == w) return true;
        return false;
    }

    /** Çırak seviyesine göre üstlenebileceği adım mı. Topping seçimi HER ZAMAN oyuncudadır. */
    static boolean apprenticeCanDo(Step s, int level) {
        if (s.isTopping()) return false;
        switch (s.station) {
            case PAKET:
            case ICECEK: return level >= 1;
            case EKMEK: return level >= 2;
            default: return level >= 3; // DONER, IZGARA, TATLI, SOS (harç yayma)
        }
    }

    private void runApprentices() {
        int lvl = upgrades.apprenticeLevel();
        if (lvl <= 0) return;
        for (int w = 1; w <= lvl; w++) {
            if (workerBusy(w)) continue;
            if (!assignApprentice(w, lvl)) break;
        }
    }

    private boolean assignApprentice(int w, int lvl) {
        if (state == State.RUNNING && !orders.isEmpty()) {
            List<Order> cand = new ArrayList<>();
            for (Order o : orders) if (o.id != selectedId && !o.ready) cand.add(o);
            Collections.sort(cand, new Comparator<Order>() {
                @Override public int compare(Order a, Order b) {
                    return Float.compare(a.customer.getPatienceLeft(), b.customer.getPatienceLeft());
                }
            });
            for (Order o : cand) {
                for (OrderLine line : o.getLines()) {
                    for (int si = 0; si < line.stepCount(); si++) {
                        if (!line.isStepOpen(si)) continue;
                        Step s = line.step(si);
                        if (!apprenticeCanDo(s, lvl)) continue;
                        if (!save.inventory.hasAll(effectiveNeeds(s))) continue;
                        launchLineStep(o, line.index, si, w);
                        return true;
                    }
                }
                if (o.canPack() && lvl >= 1) {
                    launchPack(o, w);
                    return true;
                }
            }
        }
        return apprenticePrep(w, lvl);
    }

    /** Boşta kalan çırak hazırlık yapar (israfı sınırlamak için hedefli ve gün sonuna yakın değil). */
    private boolean apprenticePrep(int w, int lvl) {
        boolean prepPhase = state == State.PREP;
        float left = prepPhase ? 999f : cfg.duration - time;
        // 1) içecek (israf yok: ertesi güne kalır)
        if (menuHasDrinkPrep() && save.readyDrinks + pendingPrep(PrepKind.DRINK, null) <= 4
                && save.inventory.has(Catalog.AYRAN, PrepKind.DRINK.batch + 2)) {
            if (launchPrep(PrepKind.DRINK, Collections.<String>emptyList(), w) == PrepResult.STARTED) return true;
        }
        if (left <= 25f) return false;
        // 2) döner
        for (Ingredient m : getPrepMeats()) {
            if (getReadyMeat(m.id) + pendingPrep(PrepKind.DONER, m.id) <= 3 && save.inventory.has(m.id, PrepKind.DONER.batch + 2)) {
                if (launchPrep(PrepKind.DONER, Collections.singletonList(m.id), w) == PrepResult.STARTED) return true;
            }
        }
        // 3) salata/sos tepsisi (seviye 2+): menünün en sık varsayılan malzemeleri
        if (lvl >= 2) {
            Map<String, Integer> freq = new LinkedHashMap<>();
            for (Product p : menu) for (String id : p.defaultToppings) {
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
                if (getTrayUses(id) + pendingPrep(PrepKind.TRAY, id) <= 3 && save.inventory.has(id, PrepKind.TRAY.batch + 2)) pick.add(id);
            }
            if (pick.size() >= 2) {
                if (launchPrep(PrepKind.TRAY, pick, w) == PrepResult.STARTED) return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- müşteri üretimi
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
        int n = 1;
        while (n < cfg.maxItems && rng.nextDouble() < cfg.multiItemChance + type.extraItemChance * 0.5f) n++;
        List<Product> items = new ArrayList<>();
        for (int k = 0; k < n; k++) items.add(pickProduct(menu, type));
        String name = Catalog.NAMES[nameCursor++ % Catalog.NAMES.length];
        addOrder(name, type, items, rollRequests(items));
    }

    private Order addOrder(String name, CustomerType type, List<Product> items, List<Request> reqs) {
        double pat = (type.patienceSeconds * cfg.patienceFactor + Order.estimateWork(items) * cfg.workPatience)
                * upgrades.patienceMult() * save.tempo;
        Customer c = new Customer(name, type, (float) pat);
        Order o = new Order(nextOrderId++, c, items, reqs);
        int pos = 1;
        while (positionUsed(pos)) pos++;
        o.position = pos;
        orders.add(o);
        for (GameListener l : snap()) l.onCustomerArrived(o);
        return o;
    }

    /**
     * Senaryo yardımcısı (öğretici/test): belirtilen ürünlerle ve isteklerle ("soğansız", "acılı"...) hemen bir sipariş
     * ekler. Gün başlamadıysa başlatır; bilinmeyen ürün/istek varsa null döner. Kuyruk sınırına bakmaz.
     */
    public Order spawnOrder(List<String> productIds, List<String> requestTexts) {
        if (state == State.FINISHED || state == State.FAILED) return null;
        ensureStarted();
        if (state != State.RUNNING) return null;
        List<Product> items = new ArrayList<>();
        for (String id : productIds) {
            Product p = Catalog.product(id);
            if (p == null) return null;
            items.add(p);
        }
        List<Request> reqs = new ArrayList<>();
        for (String t : requestTexts) {
            Request found = null;
            for (Request q : Request.all()) if (q.text.equals(t)) found = q;
            if (found == null) return null;
            reqs.add(found);
        }
        String name = Catalog.NAMES[nameCursor++ % Catalog.NAMES.length];
        CustomerType type = Catalog.typesForDay(day).get(0);
        for (CustomerType t : Catalog.typesForDay(day)) if (t.id.equals("aile")) type = t;
        return addOrder(name, type, items, reqs);
    }

    /** Müşteri istekleri: olasılık gün ilerledikçe artar; yalnızca hedef kümeyi gerçekten değiştiren istekler seçilir. */
    private List<Request> rollRequests(List<Product> items) {
        List<Request> out = new ArrayList<>();
        double chance = DayConfig.requestChance(day);
        if (chance <= 0) return out;
        if (rng.nextDouble() >= chance) return out;
        int want = rng.nextDouble() < 0.7 ? 1 : 2;
        for (int k = 0; k < want; k++) {
            List<Request> cands = new ArrayList<>();
            for (Request q : Request.all()) {
                boolean dup = false;
                for (Request e : out) if (e.group.equals(q.group)) dup = true;
                if (dup) continue;
                boolean useful = false;
                for (Product p : items) {
                    if (!p.hasToppings()) continue;
                    Set<String> cur = new LinkedHashSet<>(p.defaultToppings);
                    for (Request e : out) e.apply(p, cur);
                    if (q.changes(p, cur)) { useful = true; break; }
                }
                if (useful) cands.add(q);
            }
            if (cands.isEmpty()) break;
            out.add(cands.get(rng.nextInt(cands.size())));
        }
        return out;
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

    // ---------------------------------------------------------------- gün sonu
    private void accountWaste() {
        int portions = 0, cost = 0;
        for (Map.Entry<String, Integer> e : readyMeat.entrySet()) {
            portions += e.getValue();
            cost += e.getValue() * Catalog.ingredient(e.getKey()).price;
        }
        for (Map.Entry<String, Integer> e : trayUses.entrySet()) {
            portions += e.getValue();
            cost += e.getValue() * Catalog.ingredient(e.getKey()).price;
        }
        // yarım kalan hazırlıkların tükettiği malzeme de boşa gider
        for (ActiveStep a : active) {
            if (a.prep == null || a.prep == PrepKind.DRINK) continue;
            for (String id : a.ids) {
                portions += a.prep.batch;
                cost += a.prep.batch * Catalog.ingredient(id).price;
            }
        }
        readyMeat.clear();
        trayUses.clear();
        summary.wastedPortions = portions;
        summary.wastedCost = cost;
    }

    private void finishDay() {
        state = State.FINISHED;
        accountWaste();
        active.clear();
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
        readyMeat.clear();
        trayUses.clear();
        selectedId = -1;
        selectedItem = -1;
        // Para ve stok kaybı yok: gün başı durumuna dön; yıldız en az 1.5.
        save.money = snapshot.money;
        save.inventory = snapshot.inventory.copy();
        save.readyDrinks = snapshot.readyDrinks;
        save.stars = Math.max(snapshot.stars, 1.5);
        summary.moneyAfter = save.money;
        for (GameListener l : snap()) l.onDayFailed(summary);
    }

    private List<GameListener> snap() { return new ArrayList<>(listeners); }
}
