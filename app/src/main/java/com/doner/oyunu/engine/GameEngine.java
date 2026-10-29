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
    private static final float READY_FRIES_SECONDS = 0.8f;
    private static final float TOPPING_SECONDS_EACH = 0.3f;
    private static final float APPRENTICE_SLOWDOWN = 1.2f;
    private static final double MISTAKE_PENALTY_EACH = 0.05;
    private static final double MISTAKE_PENALTY_MAX = 0.15;
    /** Süresiz mod (v0.8): hız bonusu yok; bahşiş oranı standart, çarpanla biraz düşük (süreli oyuncu haksız duruma düşmesin). */
    public static final double UNTIMED_TIP_RATE = 0.10;
    public static final double UNTIMED_TIP_MULT = 0.8;
    /** Süresiz modda her teslimde standart memnuniyet yıldızı (süreli "memnun" ile aynı). */
    public static final double UNTIMED_STAR_GAIN = 0.08;
    /** Süresiz modda geliş aralığı çarpanı (sıra boşaldıkça biraz daha canlı) ve boş sırada en çok bekleme (sn). */
    static final double UNTIMED_ARRIVAL_FACTOR = 1.25;
    static final float UNTIMED_IDLE_WAIT = 2.5f;
    private static final double PERFECT_BONUS = 0.02;
    private static final float ADD_SALAD_SECONDS = 0.6f;
    private static final float ADD_SAUCE_SECONDS = 0.5f;
    private static final float MIN_ADD_SECONDS = 0.25f;
    private static final int MAX_ADD_PER_INGREDIENT = 2;
    /** 2 porsiyonluk döner hazırlığı, 4 porsiyonluğun süresinin bu oranı kadar sürer. */
    private static final double PREP_SMALL_BATCH_FACTOR = 0.6;

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
    /** Hazır (dilimlenmiş) döner: döner malzeme id -> YARIM porsiyon sayacı (2 = 1 porsiyon). Gün sonunda atılır. */
    private final Map<String, Integer> readyMeat = new LinkedHashMap<>();
    /** Açık yarım: stoktan düşülmüş bir porsiyon/ekmeğin kalan yarısı (0 veya 1 yarım birim), malzeme id -> yarım. Gün içi. */
    private final Map<String, Integer> halfOpen = new HashMap<>();
    /** Hazır kızarmış patates (porsiyon). Gün sonunda atılır. */
    private int readyFries;
    /** Patatesli sipariş kararları için ayrı rastgele kaynak: ana müşteri akışının rastgeleliğini kaydırmaz. */
    private final Random friesRng;
    /** Oyuncu eylem sayacı: öğretici/öneri duyurularının bayatlığını denetlemek için (her oyuncu eyleminde artar). */
    private int actionVersion;
    /** Hazır tepsi: malzeme id -> kalan kullanım. Gün sonunda atılır. */
    private final Map<String, Integer> trayUses = new LinkedHashMap<>();
    /** Son OUT_OF_STOCK'a düşen adım: sipariş id -> {kalem, adım, boy ordinal (-1: otomatik/istenen)}. */
    private final Map<Integer, int[]> blocked = new HashMap<>();

    private State state = State.READY;
    /** Oyun saati (her zaman akar: ses/animasyon/Narrator için). */
    private float time;
    /** Gün saati: yalnızca süreli modda akar; gün süresi ve mesai buna göre ölçülür. */
    private float dayClock;
    /** Otomatik gelen müşteri sayısı (süresiz modda kotayı doldurur; senaryo siparişleri sayılmaz). */
    private int arrived;
    /** Günün müşteri kotası (gün başında yükseltmelerle sabitlenir). */
    private final int quota;
    private float spawnTimer = 1.5f;
    private int nextOrderId = 1;
    private int selectedId = -1;
    private int selectedItem = -1;
    private int nameCursor;
    private boolean spawning = true;
    /** v0.6 dengesi (ölçüm/karşılaştırma için): Gerçekçi akışın v0.7 yumuşatmaları ve bahşiş ödülü kapalı. */
    private final boolean v06Balance;

    /** save: motor bu nesneyi (para, stok, yıldız, gün, oyun akışı) doğrudan günceller. */
    public GameEngine(long seed, SaveData save) { this(seed, save, false); }

    /**
     * v06Balance = true: v0.6 denge kuralları (Gerçekçi akışta ek sabır ve bahşiş ödülü yok). Yalnızca denge ölçümü ve
     * v0.6 ile karşılaştırma testleri içindir; oyun her zaman {@link #GameEngine(long, SaveData)} kullanır.
     */
    public GameEngine(long seed, SaveData save, boolean v06Balance) {
        this.v06Balance = v06Balance;
        this.save = save;
        this.snapshot = save.copy();
        this.upgrades = new Upgrades(save);
        this.day = save.day;
        this.cfg = DayConfig.forDay(day);
        this.menu = Catalog.productsForDay(day);
        this.quota = cfg.quota(upgrades.arrivalMult());
        this.rng = new Random(seed);
        this.friesRng = new Random(seed ^ 0x5DEECE66DL);
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
    /**
     * Hizmet süresinin kalanı (0'a iner; sonrasında mesai uzatması sürer). Hazırlık aşamasında tam gün süresi.
     * Süresiz modda ({@link #isTimed()} false) anlamsızdır: gün süresi sabit döner; UI/Narrator {@link #getRemainingCustomers()} kullanır.
     */
    public float getTimeLeft() { return Math.max(0f, cfg.duration - dayClock); }
    public boolean isOvertime() { return isTimed() && dayClock >= cfg.duration; }
    /** Süre baskısı açık mı (süreli gün)? Ayar {@link SaveData#timePressure}; oyun ortasında {@link #setTimePressure} ile değişir. */
    public boolean isTimed() { return save.timePressure; }
    /** Süresiz modda günün müşteri kotası ({@link DayConfig#quota}, yükseltmeler dahil); süreli modda da bilgi olarak döner. */
    public int getCustomerQuota() { return quota; }
    /** Şimdiye dek otomatik gelen müşteri sayısı (senaryo siparişleri hariç). */
    public int getCustomersArrived() { return arrived; }
    /** Kalan müşteri: henüz gelmemiş kota + şu an bekleyen sipariş sayısı (süresiz modun ilerleme göstergesi). */
    public int getRemainingCustomers() { return Math.max(0, quota - arrived) + orders.size(); }
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
    /** Oyun akışı (Rahat / Dengeli / Gerçekçi); kayıttan okunur, oyun ortasında {@link #setFlowMode} ile değişir. */
    public FlowMode getFlowMode() { return save.flowMode == null ? FlowMode.DEFAULT : save.flowMode; }

    /**
     * Oyun akışını değiştirir (oyun ortasında da). Seçenek menüleri hemen yeni akışa göre kurulur; süren işler ve
     * gelmiş müşterilerin sabrı değişmez. Kayda yazılır ({@link SaveData#flowMode}); {@code onFlowModeChanged} verilir.
     */
    public void setFlowMode(FlowMode mode) {
        if (mode == null) return;
        save.flowMode = mode;
        save.flowChosen = true;
        for (GameListener l : snap()) l.onFlowModeChanged(mode);
    }

    /**
     * Süre baskısını açar/kapatır (oyun ortasında da). Kapatınca sayaç durur, müşteri sabrı donar (kimse gitmez),
     * kalan kota kadar müşteri gelir. Açınca gün saati, o ana dek gelen müşteri oranından az olmayacak biçimde ilerler.
     * Kayda yazılır ({@link SaveData#timePressure}); {@code onTimePressureChanged} verilir.
     */
    public void setTimePressure(boolean on) {
        if (save.timePressure == on) return;
        save.timePressure = on;
        if (on) dayClock = Math.max(dayClock, cfg.duration * Math.min(0.9f, arrived / (float) quota));
        for (GameListener l : snap()) l.onTimePressureChanged(on);
        if (!on) checkEnd();
    }

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
    /** Hazır dilimlenmiş döner, TAM porsiyon olarak (yarım porsiyon aşağı yuvarlanır). Tam sayım için {@link #getReadyMeatHalves}. */
    public int getReadyMeat(String meatId) { return getReadyMeatHalves(meatId) / 2; }
    /** Hazır dilimlenmiş döner, yarım porsiyon sayacı (2 = 1 porsiyon). */
    public int getReadyMeatHalves(String meatId) { Integer v = readyMeat.get(meatId); return v == null ? 0 : v; }
    /** Toplam hazır (dilimlenmiş) döner, tam porsiyon (yarım aşağı yuvarlanır). */
    public int getReadyDoner() { return getReadyDonerHalves() / 2; }
    /** Toplam hazır döner, yarım porsiyon sayacı. */
    public int getReadyDonerHalves() { int t = 0; for (int v : readyMeat.values()) t += v; return t; }
    public int getReadyDrinks() { return save.readyDrinks; }
    /** Hazır kızarmış patates (porsiyon). */
    public int getReadyFries() { return readyFries; }
    /**
     * Oyuncu eylem sayacı: oyuncunun yaptığı her eylemde (adım/hazırlık/malzeme başlatma, sipariş ve kalem seçimi, teslim) artar.
     * Konuşma sırası, bir duyuru yapıldığı andaki değeri saklayıp söylenmeden önce karşılaştırarak bayat ipucunu eler.
     */
    public int getActionVersion() { return actionVersion; }
    private void playerAction() { actionVersion++; }
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
        for (Product p : menu) ids.addAll(p.toppingPool(day));
        List<Ingredient> out = new ArrayList<>();
        for (Ingredient i : Catalog.ingredients()) if (ids.contains(i.id)) out.add(i);
        return out;
    }

    /** Bugünün menüsünde patatesli sipariş olabilir mi (patates açık ve en az bir ürün patates alıyor). */
    public boolean menuHasFries() {
        if (day < Catalog.FRIES_OPEN_DAY) return false;
        for (Product p : menu) if (p.friesOk()) return true;
        return false;
    }

    /** Bugünün menüsünde ayran var mı. */
    public boolean menuHasDrinkPrep() {
        for (Product p : menu) for (Step s : p.steps) if (s.isDrinkFill()) return true;
        return false;
    }

    // ---------------------------------------------------------------- eksik malzeme / acil alım
    /** Eksik malzeme sorgusunun baktığı adım: son OUT_OF_STOCK adımı, yoksa önerilen adım (topping hariç). {kalem, adım, boy ordinal} */
    private int[] missingRef(int orderId) {
        Order o = getOrder(orderId);
        if (o == null) return null;
        int[] ref = blocked.get(orderId);
        if (ref != null) {
            OrderLine line = o.getLine(ref[0]);
            if (line.isStepOpen(ref[1])) return ref;
        }
        int[] r = o.suggest(orderId == selectedId ? selectedItem : -1);
        if (r == null || r[0] < 0) return null;
        OrderLine line = o.getLine(r[0]);
        Step s = line.step(r[1]);
        if (s.isTopping()) return autoAvailable(line) ? new int[]{r[0], r[1], -1} : null;
        return new int[]{r[0], r[1], -1};
    }

    /** Eksik malzeme hesabının baktığı adımın stok ihtiyacı (topping adımında: otomatik eklemenin ihtiyacı). */
    private Map<String, Integer> refNeeds(Order o, int[] ref) {
        OrderLine line = o.getLine(ref[0]);
        Step s = line.step(ref[1]);
        if (s.isTopping()) return autoNeeds(line);
        return effectiveNeeds(s, refSize(o, ref));
    }

    private PortionSize refSize(Order o, int[] ref) {
        OrderLine line = o.getLine(ref[0]);
        return needSize(line, line.step(ref[1]), ref[2] < 0 ? null : PortionSize.values()[ref[2]]);
    }

    /** Verilen siparişin (son engellenen veya önerilen) adımı için eksik ilk malzeme (yoksa null). */
    public Ingredient missingIngredient(int orderId) {
        List<Ingredient> all = missingIngredients(orderId);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Eksik TÜM malzemeler (eksik yoksa boş liste). */
    public List<Ingredient> missingIngredients(int orderId) {
        List<Ingredient> out = new ArrayList<>();
        int[] ref = missingRef(orderId);
        if (ref == null) return out;
        Order o = getOrder(orderId);
        for (Map.Entry<String, Integer> e : refNeeds(o, ref).entrySet()) {
            if (!save.inventory.has(e.getKey(), e.getValue())) {
                Ingredient ing = Catalog.ingredient(e.getKey());
                if (ing != null) out.add(ing);
            }
        }
        return out;
    }

    private int emergencyBatches(Map<String, Integer> needs, Ingredient ing) {
        Integer need = needs.get(ing.id);
        int lack = (need == null ? 1 : need) - save.inventory.get(ing.id);
        return Math.max(1, (lack + Ingredient.EMERGENCY_BATCH - 1) / Ingredient.EMERGENCY_BATCH);
    }

    /** Eksik tüm malzemeleri tek seferde acil almanın toplam maliyeti (eksik yoksa 0). */
    public int emergencyCostForMissing(int orderId) {
        int[] ref = missingRef(orderId);
        if (ref == null) return 0;
        Order o = getOrder(orderId);
        Map<String, Integer> needs = refNeeds(o, ref);
        int total = 0;
        for (Ingredient ing : missingIngredients(orderId)) {
            total += ing.emergencyPrice() * Ingredient.EMERGENCY_BATCH * emergencyBatches(needs, ing);
        }
        return total;
    }

    /**
     * Siparişin engellenen/önerilen adımı için eksik TÜM malzemeleri tek hamlede acil alır. Hepsine para
     * yetmezse hiçbiri alınmaz, {@code onNotEnoughMoney(toplam)} bildirilir.
     */
    public boolean emergencyBuyMissing(int orderId) {
        if (state == State.FINISHED || state == State.FAILED) return false;
        int[] ref = missingRef(orderId);
        List<Ingredient> missing = missingIngredients(orderId);
        if (ref == null || missing.isEmpty()) return false;
        Order o = getOrder(orderId);
        Map<String, Integer> needs = refNeeds(o, ref);
        int total = emergencyCostForMissing(orderId);
        if (save.money < total) {
            for (GameListener l : snap()) l.onNotEnoughMoney(total);
            return false;
        }
        for (Ingredient ing : missing) {
            int batches = emergencyBatches(needs, ing);
            for (int i = 0; i < batches; i++) emergencyBuy(ing.id);
        }
        return true;
    }

    // ---- boy, açık yarım ve stoktan düşülecek miktar
    /**
     * Adımın boyu: dilimleme adımında oyuncunun seçtiği (verilmediyse istenen), ekmek/lavaş ısıtmada istenen boy,
     * diğerlerinde TAM (ölçeklenmez).
     */
    private PortionSize needSize(OrderLine line, Step s, PortionSize meatChoice) {
        if (s.isSized()) return meatChoice != null ? meatChoice : line.getSize();
        if (s.isBreadStep()) return line.getSize();
        return PortionSize.FULL;
    }

    /** Bu adımda bu malzeme boyla ölçeklenir mi (dilimleme adımında et, ekmek adımında ekmek/lavaş). */
    private static boolean scaled(Step s, String ingredientId) {
        Ingredient i = Catalog.ingredient(ingredientId);
        if (i == null) return false;
        if (s.isSized() && i.category == Ingredient.Category.ET) return true;
        return s.isBreadStep() && i.category == Ingredient.Category.EKMEK;
    }

    private int openHalf(String id) { Integer v = halfOpen.get(id); return v == null ? 0 : v; }

    /** Yarım birim ihtiyacı için stoktan düşmesi gereken TAM birim (açık yarım önce kullanılır). */
    private int rawUnitsFor(String id, int halves) {
        return Math.max(0, (halves - openHalf(id) + 1) / 2);
    }

    /** Stoktan yarım birim düşer (gerekirse yeni bir birim açar; kalan yarım "açık yarım" olur). */
    private void consumeHalves(String id, int halves) {
        int total = save.inventory.get(id) * 2 + openHalf(id);
        int rem = Math.max(0, total - halves);
        save.inventory.set(id, rem / 2);
        halfOpen.put(id, rem % 2);
    }

    /** Hazır stok kullanıldığında adımın gerçekten stoktan tüketeceği malzemeler (boy dahil). */
    private Map<String, Integer> effectiveNeeds(Step s, PortionSize sz) {
        Map<String, Integer> m = new LinkedHashMap<>();
        boolean rm = usesReadyMeat(s, sz);
        for (Map.Entry<String, Integer> e : s.needs.entrySet()) {
            String id = e.getKey();
            if (rm && id.equals(s.meatId())) continue;
            if (s.isDrinkFill() && id.equals(Catalog.AYRAN) && usesReadyDrink(s)) continue;
            if (s.isFry() && id.equals(Catalog.PATATES) && usesReadyFries(s)) continue;
            if (scaled(s, id)) {
                int raw = rawUnitsFor(id, e.getValue() * sz.halves);
                if (raw > 0) m.put(id, raw);
            } else {
                m.put(id, e.getValue());
            }
        }
        return m;
    }

    private boolean usesReadyMeat(Step s, PortionSize sz) {
        String meat = s.meatId();
        if (meat == null) return false;
        Integer need = s.needs.get(meat);
        return need != null && getReadyMeatHalves(meat) >= need * sz.halves;
    }

    private boolean usesReadyFries(Step s) {
        if (!s.isFry()) return false;
        Integer need = s.needs.get(Catalog.PATATES);
        return need != null && readyFries >= need;
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
        playerAction();
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
        playerAction();
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
     * NEEDS_TOPPINGS döner ve {@code onToppingSelectionNeeded} verilir (eski toplu {@link #confirmToppings} akışı;
     * v0.6 UI'ı bunun yerine {@link #addTopping}/{@link #finishToppings} veya {@link #applyStationOption} kullanır).
     * Döner dilimleme adımında boy ÖNCEDEN SEÇİLMEDİYSE müşterinin istediği boy varsayılır (boy hatası olmaz);
     * boy seçmek için {@code applyStationOption(..., "size:yarim|tam|duble")} kullanılır.
     */
    public StartResult startStep(int orderId, int itemIndex, int stationId) {
        return startStepImpl(orderId, itemIndex, stationId, null);
    }

    private boolean sizeAllowed(OrderLine line, PortionSize sz) { return sz.isOpen(day) || sz == line.getSize(); }

    private StartResult startStepImpl(int orderId, int itemIndex, int stationId, PortionSize explicitSize) {
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
        if (explicitSize != null) {
            if (foundLine < 0 || !o.getLine(foundLine).step(foundStep).isSized()) return StartResult.INVALID;
            if (!sizeAllowed(o.getLine(foundLine), explicitSize)) {
                for (GameListener l : snap()) l.onOptionRejected(o, "size:" + explicitSize.id, StartResult.LOCKED);
                return StartResult.LOCKED;
            }
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
        Map<String, Integer> need = effectiveNeeds(step, needSize(line, step, explicitSize));
        if (!save.inventory.hasAll(need)) {
            blocked.put(o.id, new int[]{foundLine, foundStep, explicitSize == null ? -1 : explicitSize.ordinal()});
            Ingredient missing = null;
            for (String id : need.keySet()) if (!save.inventory.has(id, need.get(id))) { missing = Catalog.ingredient(id); break; }
            for (GameListener l : snap()) l.onOutOfStock(o, missing);
            return StartResult.OUT_OF_STOCK;
        }
        launchLineStep(o, foundLine, foundStep, 0, explicitSize);
        return StartResult.STARTED;
    }

    /**
     * ESKİ toplu malzeme onayı (v0.5; geriye uyum için durur): kalemin malzemelerini tek hamlede seçer ve topping adımını
     * zamanlı başlatır. Tüm seçilenler stokta (veya hazır tepside) olmalı; stok seçilenler kadar tüketilir (tepsi varsa
     * önce tepsiden). Süre = temel + 0,3 sn/malzeme; tepsiyle karşılanan kısım oranında %50'ye kadar kısalır.
     * Kalemde {@link #addTopping} ile tek tek ekleme başlamışsa INVALID döner. Doğrulama (hedefle karşılaştırma) TESLİMDE yapılır.
     * Yeni UI bunu kullanmaz: {@link #addTopping} + {@link #finishToppings}.
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
        if (ti < 0 || line.isStepDone(ti) || line.addedTotal() > 0) return StartResult.INVALID;
        if (line.isStepRunning(ti)) return StartResult.ORDER_BUSY;
        Step step = line.step(ti);
        if (!line.isStepUnlocked(ti)) {
            for (GameListener l : snap()) l.onStepLocked(o, step);
            return StartResult.STEP_LOCKED;
        }
        Set<String> want = new LinkedHashSet<>(chosen == null ? Collections.<String>emptyList() : chosen);
        for (String id : want) {
            if (!line.getPool().contains(id)) {
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
        // havuz sırasında kaydet
        Set<String> ordered = new LinkedHashSet<>();
        for (String id : line.getPool()) if (want.contains(id)) ordered.add(id);
        int covered = 0;
        for (String id : ordered) {
            if (getTrayUses(id) > 0) { trayUses.put(id, getTrayUses(id) - 1); covered++; }
            else save.inventory.add(id, -1);
        }
        playerAction();
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

    // ---------------------------------------------------------------- tek tek malzeme ekleme (v0.6)
    /** Topping için kalem çözümü: itemIndex >= 0 ise o; yoksa seçili kalem, sonra malzeme adımı bitmemiş ilk kalem; yoksa -1. */
    private int resolveToppingItem(Order o, int itemIndex) {
        if (itemIndex >= 0) return itemIndex < o.lineCount() ? itemIndex : -1;
        List<Integer> order = new ArrayList<>();
        if (o.id == selectedId && selectedItem >= 0 && selectedItem < o.lineCount()) order.add(selectedItem);
        for (int i = 0; i < o.lineCount(); i++) if (!order.contains(i)) order.add(i);
        for (int i : order) {
            OrderLine l = o.getLine(i);
            int ti = l.product.toppingStepIndex();
            if (ti >= 0 && !l.isStepDone(ti) && (l.isStepUnlocked(ti) || l.isStepRunning(ti))) return i;
        }
        for (int i : order) {
            OrderLine l = o.getLine(i);
            int ti = l.product.toppingStepIndex();
            if (ti >= 0 && !l.isStepDone(ti)) return i;
        }
        return -1;
    }

    /** Tek malzemenin ekleme süresi (sn): salata 0,6, sos 0,5; hazır tepsiden yarısı; istasyon hızı ve tempo uygulanır. */
    private float addSeconds(Ingredient ing, boolean fromTray) {
        double d = ing.category == Ingredient.Category.SALATA ? ADD_SALAD_SECONDS : ADD_SAUCE_SECONDS;
        if (fromTray) d *= 0.5;
        d *= upgrades.speedMult(Station.SOS) * save.tempo;
        return (float) Math.max(MIN_ADD_SECONDS, d);
    }

    /**
     * SOS/SALATA istasyonu: kalemin topping adımına TEK malzeme ekler. Kısa süre alır (salata 0,6 sn, sos 0,5 sn; hazır
     * tepsiden yarısı), malzemeyi hemen tüketir ve süre sonunda {@code onToppingAdded} verir. Aynı malzeme ikinci kez
     * eklenirse "bol" olur; üçüncüsü MAX_REACHED ile reddedilir. Topping adımı kalemin diğer adımları bitince açılır.
     * itemIndex &lt; 0: otomatik kalem. Sonuçlar: STARTED, NO_ORDER, ORDER_READY, INVALID (havuz dışı/malzeme adımı yok),
     * ORDER_BUSY (bir ekleme sürüyor), STEP_LOCKED, MAX_REACHED, NO_SLOT, OUT_OF_STOCK, NOT_RUNNING.
     */
    public StartResult addTopping(int orderId, int itemIndex, String ingredientId) {
        if (state == State.FINISHED || state == State.FAILED) return StartResult.NOT_RUNNING;
        ensureStarted();
        Order o = getOrder(orderId);
        if (o == null) return StartResult.NO_ORDER;
        if (o.ready) return StartResult.ORDER_READY;
        int li = resolveToppingItem(o, itemIndex);
        if (li < 0) return StartResult.INVALID;
        OrderLine line = o.getLine(li);
        int ti = line.product.toppingStepIndex();
        if (ti < 0 || line.isStepDone(ti)) return StartResult.INVALID;
        if (line.isStepRunning(ti)) return StartResult.ORDER_BUSY;
        Step step = line.step(ti);
        if (!line.isStepUnlocked(ti)) {
            for (GameListener l : snap()) l.onStepLocked(o, step);
            return StartResult.STEP_LOCKED;
        }
        Ingredient ing = ingredientId == null ? null : Catalog.ingredient(ingredientId);
        if (ing == null || !line.getPool().contains(ingredientId)) {
            for (GameListener l : snap()) l.onToppingsRejected(o, li, StartResult.INVALID, ing);
            return StartResult.INVALID;
        }
        if (line.addedCount(ingredientId) >= MAX_ADD_PER_INGREDIENT) {
            for (GameListener l : snap()) l.onToppingsRejected(o, li, StartResult.MAX_REACHED, ing);
            return StartResult.MAX_REACHED;
        }
        if (playerActiveCount() >= upgrades.slots()) {
            for (GameListener l : snap()) l.onNoFreeSlot(o);
            return StartResult.NO_SLOT;
        }
        boolean tray = getTrayUses(ingredientId) > 0;
        if (!tray && !save.inventory.has(ingredientId, 1)) {
            for (GameListener l : snap()) l.onToppingsRejected(o, li, StartResult.OUT_OF_STOCK, ing);
            return StartResult.OUT_OF_STOCK;
        }
        if (tray) trayUses.put(ingredientId, getTrayUses(ingredientId) - 1);
        else save.inventory.add(ingredientId, -1);
        line.recordAdd(ingredientId, tray);
        playerAction();
        line.setRunning(ti, true);
        blocked.remove(o.id);
        float dur = addSeconds(ing, tray);
        Step syn = new Step(ing.name + " ekle", Station.SOS, dur, Collections.<String, Integer>emptyMap());
        ActiveStep a = new ActiveStep(o, li, ti, syn, dur, freeSlot(), 0, null, null, false);
        a.addId = ingredientId;
        a.addFromTray = tray;
        active.add(a);
        int count = line.addedCount(ingredientId);
        for (GameListener l : snap()) l.onToppingAddStarted(o, li, ing, count, dur);
        return StartResult.STARTED;
    }

    /**
     * Kalemin SON eklenen malzemesini geri alır: malzeme stoğa (tepsiden geldiyse tepsiye) iade edilir. Yalnızca malzeme
     * adımı bitirilmemişken ve bir ekleme sürmüyorken çalışır. Sonuçlar: STARTED (geri alındı), NOTHING_TO_UNDO,
     * ORDER_BUSY (ekleme sürüyor), NO_ORDER, ORDER_READY, INVALID, NOT_RUNNING. Reddedilince {@code onOptionRejected("undo", ...)}.
     */
    public StartResult removeLastTopping(int orderId, int itemIndex) {
        if (state == State.FINISHED || state == State.FAILED) return StartResult.NOT_RUNNING;
        Order o = getOrder(orderId);
        if (o == null) return StartResult.NO_ORDER;
        if (o.ready) return StartResult.ORDER_READY;
        int li = itemIndex;
        if (li < 0) {
            li = resolveToppingItem(o, -1);
            // eklenmiş malzemesi olan ilk kalemi tercih et
            for (int i = 0; i < o.lineCount(); i++) {
                OrderLine l = o.getLine(i);
                int t = l.product.toppingStepIndex();
                if (t >= 0 && !l.isStepDone(t) && l.addedTotal() > 0) { li = i; break; }
            }
        }
        if (li < 0 || li >= o.lineCount()) return StartResult.INVALID;
        OrderLine line = o.getLine(li);
        int ti = line.product.toppingStepIndex();
        if (ti < 0) return StartResult.INVALID;
        if (line.isStepDone(ti) || line.addedTotal() == 0) return rejectOption(o, "undo", StartResult.NOTHING_TO_UNDO);
        if (line.isStepRunning(ti)) return rejectOption(o, "undo", StartResult.ORDER_BUSY);
        boolean[] wasTray = new boolean[1];
        playerAction();
        String id = line.undoLast(wasTray);
        if (wasTray[0]) trayUses.put(id, getTrayUses(id) + 1);
        else save.inventory.add(id, 1);
        Ingredient ing = Catalog.ingredient(id);
        int remaining = line.addedCount(id);
        for (GameListener l : snap()) l.onToppingRemoved(o, li, ing, remaining);
        return StartResult.STARTED;
    }

    private StartResult rejectOption(Order o, String optionId, StartResult r) {
        for (GameListener l : snap()) l.onOptionRejected(o, optionId, r);
        return r;
    }

    /**
     * "Malzemeleri bitir" (eski Tamam): kalemin malzeme adımını tamamlar. Hiç malzeme eklenmemiş olabilir (sosuz).
     * Doğrulama (hedef küme ile karşılaştırma) eskisi gibi TESLİMDE yapılır. Anında biter (süre yok);
     * {@code onToppingsFinished} + {@code onStepFinished} (+ kalem/paket olayları) verilir.
     * Sonuçlar: STARTED (bitti), STEP_LOCKED (diğer adımlar bitmeli), ORDER_BUSY (ekleme sürüyor), INVALID, NO_ORDER,
     * ORDER_READY, NOT_RUNNING.
     */
    public StartResult finishToppings(int orderId, int itemIndex) {
        if (state == State.FINISHED || state == State.FAILED) return StartResult.NOT_RUNNING;
        ensureStarted();
        Order o = getOrder(orderId);
        if (o == null) return StartResult.NO_ORDER;
        if (o.ready) return StartResult.ORDER_READY;
        int li = resolveToppingItem(o, itemIndex);
        if (li < 0) return StartResult.INVALID;
        OrderLine line = o.getLine(li);
        int ti = line.product.toppingStepIndex();
        if (ti < 0 || line.isStepDone(ti)) return StartResult.INVALID;
        if (line.isStepRunning(ti)) return rejectOption(o, "finish", StartResult.ORDER_BUSY);
        Step step = line.step(ti);
        if (!line.isStepUnlocked(ti)) {
            for (GameListener l : snap()) l.onStepLocked(o, step);
            return StartResult.STEP_LOCKED;
        }
        line.finishChosen();
        line.setDone(ti);
        blocked.remove(o.id);
        playerAction();
        for (GameListener l : snap()) l.onToppingsFinished(o, li, line.getChosen());
        afterLineStep(o, line, step, 0, 0f);
        return StartResult.STARTED;
    }

    /** Kalemin eklenen malzemeleri ve adetleri (ekleme sırasında; 1 normal, 2 bol). Kalem yoksa boş. */
    public Map<String, Integer> getAddedToppings(int orderId, int itemIndex) {
        Order o = getOrder(orderId);
        if (o == null || itemIndex < 0 || itemIndex >= o.lineCount()) return new LinkedHashMap<>();
        return new LinkedHashMap<>(o.getLine(itemIndex).getAddedCounts());
    }

    /**
     * Eklenmiş bir malzemenin TÜM eklemelerini (bol dahil) çıkarır ve stoğa/tepsiye iade eder ("Soğan çıkar"; v0.7
     * Dengeli akışta istek farkı için, her akışta API olarak geçerli). Malzeme adımı bitmemiş ve ekleme sürmüyor olmalı.
     * Sonuçlar: STARTED, NOTHING_TO_UNDO (eklenmemiş), ORDER_BUSY, INVALID, NO_ORDER, ORDER_READY, NOT_RUNNING.
     */
    public StartResult removeTopping(int orderId, int itemIndex, String ingredientId) {
        if (state == State.FINISHED || state == State.FAILED) return StartResult.NOT_RUNNING;
        Order o = getOrder(orderId);
        if (o == null) return StartResult.NO_ORDER;
        if (o.ready) return StartResult.ORDER_READY;
        int li = itemIndex;
        if (li < 0) {
            li = resolveToppingItem(o, -1);
            for (int i = 0; i < o.lineCount(); i++) {
                OrderLine l = o.getLine(i);
                int t = l.product.toppingStepIndex();
                if (t >= 0 && !l.isStepDone(t) && l.addedCount(ingredientId) > 0) { li = i; break; }
            }
        }
        if (li < 0 || li >= o.lineCount()) return StartResult.INVALID;
        OrderLine line = o.getLine(li);
        int ti = line.product.toppingStepIndex();
        Ingredient ing = ingredientId == null ? null : Catalog.ingredient(ingredientId);
        if (ti < 0 || ing == null) return rejectOption(o, "remove:" + ingredientId, StartResult.INVALID);
        if (line.isStepDone(ti) || line.addedCount(ingredientId) == 0) return rejectOption(o, "remove:" + ingredientId, StartResult.NOTHING_TO_UNDO);
        if (line.isStepRunning(ti)) return rejectOption(o, "remove:" + ingredientId, StartResult.ORDER_BUSY);
        playerAction();
        refund(ingredientId, line.removeAll(ingredientId));
        for (GameListener l : snap()) l.onToppingRemoved(o, li, ing, 0);
        return StartResult.STARTED;
    }

    private void refund(String id, List<Boolean> trays) {
        for (boolean t : trays) {
            if (t) trayUses.put(id, getTrayUses(id) + 1);
            else save.inventory.add(id, 1);
        }
    }

    // ---------------------------------------------------------------- otomatik malzeme (v0.7 Rahat / Dengeli)
    /** Gerçekçi akışta "Standart malzemeleri ekle" kısayolunu açan çırak seviyesi (v0.7 ilerleme bağlantısı). */
    public static final int STANDARD_SET_APPRENTICE_LEVEL = 2;

    /**
     * Kalemde otomatik malzeme ("auto") sunulur mu: Rahat her zaman; Dengeli kalem başına bir kez; Gerçekçi yalnızca
     * çırak seviye 2+ iken ve kaleme henüz hiç malzeme eklenmemişken ("Standart malzemelerle bitir": çırak standart
     * malzemeleri hazır tutar). Adım bitmemiş olmalı; kilit/yuva bakılmaz.
     */
    private boolean autoAvailable(OrderLine line) {
        FlowMode m = getFlowMode();
        int ti = line.product.toppingStepIndex();
        if (ti < 0 || line.isStepDone(ti)) return false;
        switch (m) {
            case RAHAT: return true;
            case DENGELI: return !line.isAutoDone();
            default: return !v06Balance && standardSetUnlocked() && !line.isAutoDone() && line.addedTotal() == 0;
        }
    }

    /** Gerçekçi akışta standart set kısayolu açık mı (çırak seviye 2+). */
    public boolean standardSetUnlocked() { return upgrades.apprenticeLevel() >= STANDARD_SET_APPRENTICE_LEVEL; }

    /** Otomatik işin istediği küme: Rahat = hedef (istekler dahil), Dengeli = günün varsayılanı. */
    private Set<String> autoWanted(OrderLine line) {
        return getFlowMode().autoRequests() ? line.getTarget() : line.getDefaults();
    }

    /** Otomatik işin ekleyeceği malzemeler (havuz sırasında, henüz eklenmemiş olanlar). */
    private List<String> autoAddList(OrderLine line) {
        Set<String> want = autoWanted(line);
        List<String> out = new ArrayList<>();
        for (String id : line.getPool()) if (want.contains(id) && line.addedCount(id) == 0) out.add(id);
        return out;
    }

    /** Rahat: eklenmiş ama hedefte olmayan malzemeler (otomatik iş çıkarır ve stoğa iade eder). Dengeli'de boş. */
    private List<String> autoRemoveList(OrderLine line) {
        List<String> out = new ArrayList<>();
        if (!getFlowMode().autoRequests()) return out;
        for (String id : line.getAddedSet()) if (!line.getTarget().contains(id)) out.add(id);
        return out;
    }

    /** Otomatik işin stoktan isteyeceği malzemeler (tepside olanlar hariç): id -> 1. Sunulmuyorsa boş. */
    private Map<String, Integer> autoNeeds(OrderLine line) {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (!autoAvailable(line)) return m;
        for (String id : autoAddList(line)) if (getTrayUses(id) <= 0) m.put(id, 1);
        return m;
    }

    /** Otomatik işin süresi: tek tek eklemelerin süreleri toplamı (tepsiden yarısı) — Gerçekçi ile aynı iş süresi, yalnızca dokunuş yok. */
    private float autoPreviewSeconds(List<String> ids) {
        double d = 0;
        for (String id : ids) d += addSeconds(Catalog.ingredient(id), getTrayUses(id) > 0);
        return (float) Math.max(MIN_ADD_SECONDS, d);
    }

    /**
     * v0.7 "Malzemeleri ekle" (Rahat) / "Varsayılan malzemeleri ekle" (Dengeli): tek dokunuşla malzemeleri ekleyen ZAMANLI
     * iş (oyuncu yuvası kullanır). Süre = eklenecek her malzemenin tek tek ekleme süresi toplamı (tepsiden yarısı; istasyon
     * hızı ve tempo dahil). Stok başta tüketilir (önce tepsi). Bir malzeme bile yoksa hiçbir şey tüketilmez,
     * OUT_OF_STOCK + {@code onOutOfStock} (eksikler {@link #missingIngredients}/{@link #emergencyBuyMissing} ile acil alınır).
     * Rahat: hedef kümeyi kurar (eksikleri ekler, fazlaları çıkarıp iade eder) ve iş bitince malzeme adımı da biter.
     * Dengeli: günün varsayılan kümesini ekler; hedef varsayılanla aynıysa (istek farkı yok) adım biter, değilse açık kalır
     * ve oyuncu farkı (remove:/add:) yapıp "Malzemeleri bitir" der. Dengeli'de kalem başına bir kez.
     * Gerçekçi: yalnızca çırak seviye 2+ iken ve kalem boşken "Standart malzemelerle bitir" (varsayılan küme eklenir ve
     * adım biter — müşteri isteği varsa bu kısayolu kullanmak HATADIR, oyuncu isteği hatırlamalı); aksi halde INVALID. Sonuçlar: STARTED, OUT_OF_STOCK, NO_SLOT, STEP_LOCKED, ORDER_BUSY,
     * INVALID, NO_ORDER, ORDER_READY, NOT_RUNNING.
     */
    public StartResult autoToppings(int orderId, int itemIndex) {
        if (state == State.FINISHED || state == State.FAILED) return StartResult.NOT_RUNNING;
        ensureStarted();
        Order o = getOrder(orderId);
        if (o == null) return StartResult.NO_ORDER;
        if (o.ready) return StartResult.ORDER_READY;
        int li = resolveToppingItem(o, itemIndex);
        if (li < 0) return rejectOption(o, "auto", StartResult.INVALID);
        OrderLine line = o.getLine(li);
        int ti = line.product.toppingStepIndex();
        if (ti < 0 || line.isStepDone(ti) || !autoAvailable(line)) return rejectOption(o, "auto", StartResult.INVALID);
        if (line.isStepRunning(ti)) return rejectOption(o, "auto", StartResult.ORDER_BUSY);
        Step step = line.step(ti);
        if (!line.isStepUnlocked(ti)) {
            for (GameListener l : snap()) l.onStepLocked(o, step);
            return StartResult.STEP_LOCKED;
        }
        if (playerActiveCount() >= upgrades.slots()) {
            for (GameListener l : snap()) l.onNoFreeSlot(o);
            return StartResult.NO_SLOT;
        }
        List<String> add = autoAddList(line);
        for (String id : add) {
            if (getTrayUses(id) <= 0 && !save.inventory.has(id, 1)) {
                blocked.put(o.id, new int[]{li, ti, -1});
                Ingredient missing = Catalog.ingredient(id);
                for (GameListener l : snap()) l.onOutOfStock(o, missing);
                return StartResult.OUT_OF_STOCK;
            }
        }
        FlowMode mode = getFlowMode();
        List<String> removed = autoRemoveList(line);
        for (String id : removed) refund(id, line.removeAll(id));
        double d = 0;
        for (String id : add) {
            boolean tray = getTrayUses(id) > 0;
            d += addSeconds(Catalog.ingredient(id), tray);
            if (tray) trayUses.put(id, getTrayUses(id) - 1);
            else save.inventory.add(id, -1);
            line.recordAdd(id, tray);
        }
        float dur = (float) Math.max(MIN_ADD_SECONDS, d);
        if (mode != FlowMode.RAHAT) line.setAutoDone();
        playerAction();
        line.setRunning(ti, true);
        blocked.remove(o.id);
        Step syn = new Step(autoName(mode), Station.SOS, dur,
                Collections.<String, Integer>emptyMap());
        ActiveStep a = new ActiveStep(o, li, ti, syn, dur, freeSlot(), 0, null, null, false);
        a.autoAdded = add;
        a.autoRemoved = removed;
        a.autoMode = mode;
        active.add(a);
        List<Ingredient> ings = ingredients(add);
        for (GameListener l : snap()) l.onAutoToppingsStarted(o, li, ings, dur);
        return StartResult.STARTED;
    }

    private static List<Ingredient> ingredients(List<String> ids) {
        List<Ingredient> out = new ArrayList<>();
        for (String id : ids) { Ingredient i = Catalog.ingredient(id); if (i != null) out.add(i); }
        return out;
    }

    // ---------------------------------------------------------------- malzeme seçimi (eski panel API'si)
    /** Eski malzeme paneli seçenekleri (havuz sırasında; günün açık malzemeleri). Kalem topping istemiyorsa boş liste. */
    public List<ToppingOption> getToppingOptions(int orderId, int itemIndex) {
        List<ToppingOption> out = new ArrayList<>();
        Order o = getOrder(orderId);
        if (o == null || itemIndex < 0 || itemIndex >= o.lineCount()) return out;
        OrderLine line = o.getLine(itemIndex);
        boolean pre = DayConfig.toppingsPreselected(day);
        for (String id : line.getPool()) {
            Ingredient ing = Catalog.ingredient(id);
            int stock = save.inventory.get(id);
            int tray = getTrayUses(id);
            boolean isDef = line.getDefaults().contains(id);
            out.add(new ToppingOption(ing, stock > 0 || tray > 0, pre && isDef, isDef, stock, tray));
        }
        return out;
    }

    /** Eski panel açıldığında önceden seçili gelen malzemeler (1-3. gün varsayılan küme, sonra boş). */
    public Set<String> getInitialToppings(int orderId, int itemIndex) {
        Set<String> s = new LinkedHashSet<>();
        for (ToppingOption op : getToppingOptions(orderId, itemIndex)) if (op.selected) s.add(op.ingredient.id);
        return s;
    }

    /** Kalemin hedef malzeme kümesi ("Hedefi oku"): günün açık varsayılanı ± müşteri istekleri. */
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

    // ---------------------------------------------------------------- istasyon eylem menüsü (v0.6)
    /**
     * İstasyon eylem menüsünde bu istasyon için kalem çözümü: itemIndex &gt;= 0 ise o kalem; yoksa seçili kalem, sonra
     * istasyonda açık adımı olan ilk kalem, sonra istasyonda (kilitli/sürüyor da olsa) bitmemiş adımı olan ilk kalem.
     * Sipariş yoksa, kalemde iş yoksa veya PAKET istasyonuysa -1.
     */
    public int resolveStationItem(int orderId, int itemIndex, int stationId) {
        Order o = getOrder(orderId);
        Station st = Station.byId(stationId);
        if (o == null || st == null || st == Station.PAKET) return -1;
        if (itemIndex >= 0) return itemIndex < o.lineCount() ? itemIndex : -1;
        List<Integer> order = new ArrayList<>();
        if (o.id == selectedId && selectedItem >= 0 && selectedItem < o.lineCount()) order.add(selectedItem);
        for (int i = 0; i < o.lineCount(); i++) if (!order.contains(i)) order.add(i);
        for (int i : order) {
            OrderLine l = o.getLine(i);
            for (int si = 0; si < l.stepCount(); si++) if (l.step(si).station == st && l.isStepOpen(si)) return i;
        }
        for (int i : order) {
            OrderLine l = o.getLine(i);
            for (int si = 0; si < l.stepCount(); si++) if (l.step(si).station == st && !l.isStepDone(si)) return i;
        }
        return -1;
    }

    private static final String LOCK_SLOT = "Bir işlem sürüyor";

    /**
     * İstasyonun o anki eylem menüsü (TalkBack özel eylemleri ve seçenek paneli AYNI listeyi kullanır).
     * orderId &lt; 0 veya sipariş yok: yalnızca hazırlık seçenekleri. itemIndex &lt; 0: kalem otomatik
     * ({@link #resolveStationItem}). Kilitli seçenekler listede kalır ({@code enabled=false}, {@code lockReason}).
     * Gün bittiyse boş.
     */
    public List<StationOption> getStationOptions(int orderId, int itemIndex, int stationId) {
        List<StationOption> out = new ArrayList<>();
        Station st = Station.byId(stationId);
        if (st == null || state == State.FINISHED || state == State.FAILED) return out;
        Order o = getOrder(orderId);
        if (o != null && !o.ready && state == State.RUNNING) {
            if (st == Station.PAKET) {
                out.add(packOption(o));
            } else {
                int li = resolveStationItem(orderId, itemIndex, stationId);
                if (li >= 0) lineOptions(out, o, li, st);
            }
        }
        prepOptions(out, st);
        return out;
    }

    private boolean slotFree() { return playerActiveCount() < upgrades.slots(); }

    private StationOption packOption(Order o) {
        Step s = o.getPackStep();
        boolean ok = o.canPack() && slotFree();
        String reason = o.isPacking() ? "Paketleme sürüyor" : !o.allLinesDone() ? "Önce tüm kalemler hazır olmalı" : LOCK_SLOT;
        return new StationOption("step", StationOption.Group.STEP, s.name, s.name, ok, reason, null, false);
    }

    private void lineOptions(List<StationOption> out, Order o, int li, Station st) {
        OrderLine line = o.getLine(li);
        String who = o.lineCount() > 1 ? line.product.name + ": " : "";
        int normal = -1, normalFirstUndone = -1, sized = -1, top = -1;
        for (int si = 0; si < line.stepCount(); si++) {
            Step s = line.step(si);
            if (s.station != st || line.isStepDone(si)) continue;
            if (s.isTopping()) { if (top < 0) top = si; }
            else if (s.isSized()) { if (sized < 0) sized = si; }
            else {
                if (normalFirstUndone < 0) normalFirstUndone = si;
                if (normal < 0 && line.isStepOpen(si)) normal = si;
            }
        }
        if (normal < 0) normal = normalFirstUndone;
        if (normal >= 0) out.add(stepOption(o, line, normal, who));
        if (sized >= 0) {
            if (getFlowMode().autoSize(line.getSize())) out.add(stepOption(o, line, sized, who));
            else sizeOptions(out, o, line, sized, who);
        }
        if (top >= 0) toppingOptions(out, o, line, top, who);
    }

    private StationOption stepOption(Order o, OrderLine line, int si, String who) {
        Step s = line.step(si);
        PortionSize sz = needSize(line, s, null);
        boolean running = line.isStepRunning(si);
        boolean locked = !line.isStepUnlocked(si);
        boolean slot = slotFree();
        boolean ok = !running && !locked && slot;
        String reason = running ? "Çalışıyor" : locked ? "Önce kalemin önceki adımları bitmeli" : LOCK_SLOT;
        Map<String, Integer> need = effectiveNeeds(s, sz);
        boolean out = !save.inventory.hasAll(need);
        String sizeText = "";
        if (s.isBreadStep() && sz != PortionSize.FULL) sizeText = ", " + sz.spoken();
        else if (s.isSized() && line.hasSize() && PortionSize.HALF.isOpen(day)) sizeText = ", " + sz.spoken();
        String desc = who + s.name + sizeText + (out ? ", malzeme yok" : "");
        return new StationOption("step", StationOption.Group.STEP, who + s.name, desc, ok, reason, null, out);
    }

    /** Başlatılsaydı adımın süresi (sn) — hazır stok, boy ve hız dahil. */
    private float previewSeconds(OrderLine line, int si, PortionSize sz) {
        Step s = line.step(si);
        return lineStepSeconds(s, usesReadyMeat(s, sz), usesReadyDrink(s), usesReadyFries(s), 0, sz);
    }

    private void sizeOptions(List<StationOption> out, Order o, OrderLine line, int si, String who) {
        Step s = line.step(si);
        boolean running = line.isStepRunning(si);
        boolean locked = !line.isStepUnlocked(si);
        boolean slot = slotFree();
        String meat = s.meatId();
        int need = meat == null ? 1 : s.needs.get(meat);
        for (PortionSize sz : PortionSize.values()) {
            boolean open = sizeAllowed(line, sz);
            boolean ok = open && !running && !locked && slot;
            String reason = !open ? sz.unlockDay + ". günde açılır" : running ? "Dilimleme sürüyor"
                    : locked ? "Önce kalemin önceki adımları bitmeli" : LOCK_SLOT;
            boolean oos = !save.inventory.hasAll(effectiveNeeds(s, sz));
            String desc = who + sz.displayName + (oos ? ", malzeme yok" : "");
            out.add(new StationOption("size:" + sz.id, StationOption.Group.SIZE, sz.displayName, desc, ok, reason, meat, oos));
        }
    }

    private void toppingOptions(List<StationOption> out, Order o, OrderLine line, int ti, String who) {
        if (getFlowMode().autoToppings()) { autoToppingOptions(out, o, line, ti, who); return; }
        boolean locked = !line.isStepUnlocked(ti);
        boolean running = line.isStepRunning(ti);
        boolean slot = slotFree();
        String lockedReason = "Önce kalemin diğer adımları bitmeli";
        if (autoAvailable(line)) out.add(autoOption(line, who, locked, running, slot, lockedReason));
        for (String id : line.getPool()) {
            Ingredient ing = Catalog.ingredient(id);
            int cnt = line.addedCount(id);
            int stock = save.inventory.get(id);
            int tray = getTrayUses(id);
            boolean oos = stock <= 0 && tray <= 0;
            boolean maxed = cnt >= MAX_ADD_PER_INGREDIENT;
            String name = ing.name + (cnt == 1 ? " bol ekle" : " ekle");
            String state = oos ? ", stokta yok" : "";
            String added = cnt == 1 ? ", eklendi" : cnt >= 2 ? ", bol eklendi" : "";
            boolean ok = !locked && !running && slot && !maxed;
            String reason = locked ? lockedReason : running ? "Ekleme sürüyor" : maxed ? "En fazla bol, iki kez eklenir" : LOCK_SLOT;
            out.add(new StationOption("add:" + id, StationOption.Group.ADD, who + name, who + name + added + state,
                    ok, reason, id, oos));
        }
        boolean none = line.addedTotal() == 0;
        out.add(new StationOption("undo", StationOption.Group.CONTROL, "Son ekleneni geri al",
                "Son ekleneni geri al",
                !none && !running, none ? "Henüz malzeme eklenmedi" : "Ekleme sürüyor", null, false));
        out.add(new StationOption("finish", StationOption.Group.CONTROL, "Malzemeleri bitir",
                who + "Malzemeleri bitir",
                !locked && !running, locked ? lockedReason : "Ekleme sürüyor", null, false));
        out.add(new StationOption("read:added", StationOption.Group.READ, "Eklenenleri oku", "Eklenenleri oku",
                true, null, null, false));
        out.add(new StationOption("read:target", StationOption.Group.READ, "Hedefi oku", "Hedefi oku",
                true, null, null, false));
    }

    /**
     * Rahat/Dengeli malzeme menüsü: "auto" (tek dokunuş: Rahat'ta hedef, Dengeli'de varsayılan küme). Dengeli'de
     * otomatik eklemeden sonra istek farkı için eklenmişlere "X çıkar" (remove:x), eklenmemişlere "X ekle" (add:x),
     * "Malzemeleri bitir" ve okuma seçenekleri.
     */
    private void autoToppingOptions(List<StationOption> out, Order o, OrderLine line, int ti, String who) {
        FlowMode m = getFlowMode();
        boolean locked = !line.isStepUnlocked(ti);
        boolean running = line.isStepRunning(ti);
        boolean slot = slotFree();
        String lockedReason = "Önce kalemin diğer adımları bitmeli";
        if (autoAvailable(line)) out.add(autoOption(line, who, locked, running, slot, lockedReason));
        if (m == FlowMode.DENGELI && (line.isAutoDone() || line.addedTotal() > 0)) {
            removeOptions(out, line, who, locked, running, lockedReason);
            for (String id : line.getPool()) {
                if (line.addedCount(id) > 0) continue;
                Ingredient ing = Catalog.ingredient(id);
                int stock = save.inventory.get(id);
                int tray = getTrayUses(id);
                boolean oos = stock <= 0 && tray <= 0;
                String state = oos ? ", stokta yok" : "";
                String name = ing.name + " ekle";
                boolean ok = !locked && !running && slot;
                String reason = locked ? lockedReason : running ? "Ekleme sürüyor" : LOCK_SLOT;
                out.add(new StationOption("add:" + id, StationOption.Group.ADD, who + name, who + name + state, ok, reason, id, oos));
            }
            out.add(new StationOption("finish", StationOption.Group.CONTROL, "Malzemeleri bitir",
                    who + "Malzemeleri bitir",
                    !locked && !running, locked ? lockedReason : "Ekleme sürüyor", null, false));
            out.add(new StationOption("read:added", StationOption.Group.READ, "Eklenenleri oku", "Eklenenleri oku",
                    true, null, null, false));
        }
        out.add(new StationOption("read:target", StationOption.Group.READ, "Hedefi oku", "Hedefi oku",
                true, null, null, false));
    }

    /** Eklenmiş her malzeme için "X çıkar" (remove:x) seçenekleri (Dengeli istek farkı; Gerçekçi standart setten sonra). */
    private void removeOptions(List<StationOption> out, OrderLine line, String who, boolean locked, boolean running, String lockedReason) {
        for (String id : line.getPool()) {
            if (line.addedCount(id) <= 0) continue;
            Ingredient ing = Catalog.ingredient(id);
            String name = ing.name + " çıkar";
            out.add(new StationOption("remove:" + id, StationOption.Group.REMOVE, who + name,
                    who + name,
                    !locked && !running, locked ? lockedReason : "Ekleme sürüyor", id, false));
        }
    }

    /** Akışa göre otomatik ekleme işinin adı: Rahat "Malzemeleri ekle", Dengeli "Varsayılan…", Gerçekçi "Standart…". */
    static String autoName(FlowMode m) {
        switch (m) {
            case RAHAT: return "Malzemeleri ekle";
            case DENGELI: return "Varsayılan malzemeleri ekle";
            default: return "Standart malzemelerle bitir";
        }
    }

    private StationOption autoOption(OrderLine line, String who, boolean locked, boolean running, boolean slot, String lockedReason) {
        FlowMode m = getFlowMode();
        boolean oos = !save.inventory.hasAll(autoNeeds(line));
        String name = autoName(m);
        StringBuilder d = new StringBuilder(who).append(name);
        if (oos) d.append(", malzeme yok");
        String reason = locked ? lockedReason : running ? "Ekleme sürüyor" : LOCK_SLOT;
        return new StationOption("auto", StationOption.Group.STEP, who + name, d.toString(),
                !locked && !running && slot, reason, null, oos);
    }

    // ---- hazırlık seçenekleri
    private static final int[] DONER_PREP_PORTIONS = {4, 2};

    private void prepOptions(List<StationOption> out, Station st) {
        boolean slot = slotFree();
        if (st == Station.DONER) {
            List<Ingredient> meats = getPrepMeats();
            for (Ingredient m : meats) {
                for (int n : DONER_PREP_PORTIONS) {
                    String name = n + " porsiyon " + (meats.size() > 1 ? m.name.toLowerCase(new java.util.Locale("tr")) + " " : "") + "dilimle";
                    int cap = upgrades.readyCapacity(PrepKind.DONER) * 2;
                    boolean full = getReadyMeatHalves(m.id) + 2 * pendingPrep(PrepKind.DONER, m.id) + 2 * n > cap;
                    boolean lack = !save.inventory.has(m.id, n);
                    String reason = full ? "Hazır stok dolu" : lack ? "Malzeme yetmiyor: " + m.name : LOCK_SLOT;
                    out.add(new StationOption("prep:doner:" + m.id + ":" + n, StationOption.Group.PREP, name, name,
                            slot && !full && !lack, reason, m.id, false));
                }
            }
        } else if (st == Station.ICECEK && menuHasDrinkPrep()) {
            int cap = upgrades.readyCapacity(PrepKind.DRINK);
            boolean full = save.readyDrinks + pendingPrep(PrepKind.DRINK, null) + PrepKind.DRINK.batch > cap;
            boolean lack = !save.inventory.has(Catalog.AYRAN, PrepKind.DRINK.batch);
            String reason = full ? "Hazır stok dolu" : lack ? "Malzeme yetmiyor: Ayran şişesi" : LOCK_SLOT;
            String name = PrepKind.DRINK.batch + " bardak ayran doldur";
            out.add(new StationOption("prep:drink", StationOption.Group.PREP, name, name,
                    slot && !full && !lack, reason, Catalog.AYRAN, false));
        } else if (st == Station.IZGARA && menuHasFries()) {
            int cap = upgrades.readyCapacity(PrepKind.FRIES);
            boolean full = readyFries + pendingPrep(PrepKind.FRIES, null) + PrepKind.FRIES.batch > cap;
            boolean lack = !save.inventory.has(Catalog.PATATES, PrepKind.FRIES.batch);
            String reason = full ? "Hazır stok dolu" : lack ? "Malzeme yetmiyor: Patates" : LOCK_SLOT;
            String name = PrepKind.FRIES.batch + " porsiyon patates kızart";
            out.add(new StationOption("prep:fries", StationOption.Group.PREP, name, name,
                    slot && !full && !lack, reason, Catalog.PATATES, false));
        } else if (st == Station.SOS) {
            Set<String> pick = trayPick(false);
            boolean enough = pick.size() >= 2;
            String name = "Salata ve sos tepsisi hazırla";
            out.add(new StationOption("prep:tray", StationOption.Group.PREP, name, name,
                    slot && enough, enough ? LOCK_SLOT : "Yeterli malzeme yok", null, false));
        }
    }

    private float prepSeconds(PrepKind kind, int amount) {
        double d = kind.baseSeconds * (kind == PrepKind.DONER && amount < kind.batch ? PREP_SMALL_BATCH_FACTOR : 1.0)
                * upgrades.speedMult(kind.station) * save.tempo;
        return (float) Math.max(MIN_STEP_SECONDS, d);
    }

    /** Hazır tepsi için önerilen malzemeler: menünün (günün açık) en sık varsayılan malzemeleri, stokta ≥ 6 olanlar. */
    private Set<String> trayPick(boolean forApprentice) {
        Map<String, Integer> freq = new LinkedHashMap<>();
        for (Product p : menu) for (String id : p.defaultToppings(day)) {
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
            int limit = forApprentice ? 3 : PrepKind.TRAY.batch;
            if (getTrayUses(id) + pendingPrep(PrepKind.TRAY, id) <= limit && save.inventory.has(id, PrepKind.TRAY.batch + (forApprentice ? 2 : 0))
                    && getTrayUses(id) + pendingPrep(PrepKind.TRAY, id) + PrepKind.TRAY.batch <= upgrades.readyCapacity(PrepKind.TRAY)) pick.add(id);
        }
        return pick;
    }

    /**
     * Menü seçeneğini uygular — UI hem TalkBack eylemlerinden hem seçenek panelinden BU TEK GİRİŞ NOKTASINI çağırır.
     * optionId, {@link StationOption#id} değerlerinden biridir:
     * <ul>
     * <li>{@code step}: istasyondaki kalem/paketleme adımını başlatır (startStep ile aynı)</li>
     * <li>{@code size:yarim|tam|duble}: DONER istasyonunda boyu SEÇEREK dilimlemeyi başlatır (boy hatası teslimde)</li>
     * <li>{@code add:<malzeme>}: {@link #addTopping}; {@code undo}: {@link #removeLastTopping}; {@code finish}: {@link #finishToppings}</li>
     * <li>{@code read:added}, {@code read:target}: durum değişmez, {@code onStationInfo} verilir, INFO döner</li>
     * <li>{@code prep:doner:<et>:<4|2>}, {@code prep:drink}, {@code prep:tray}: hazırlık (sipariş gerekmez)</li>
     * </ul>
     * Dönüş: uygulandıysa STARTED (veya INFO); aksi halde reddetme nedeni (OUT_OF_STOCK, NO_SLOT, STEP_LOCKED, LOCKED,
     * MAX_REACHED, NOTHING_TO_UNDO, FULL, UNKNOWN_OPTION...). Her red için ilgili olay (Narrator duyurur) verilir.
     * itemIndex &lt; 0: kalem otomatik. Kilitli (enabled=false) seçenek uygulanırsa nedeni duyurulur, durum değişmez.
     */
    public StartResult applyStationOption(int orderId, int itemIndex, int stationId, String optionId) {
        if (state == State.FINISHED || state == State.FAILED) return StartResult.NOT_RUNNING;
        Station st = Station.byId(stationId);
        Order o = getOrder(orderId);
        if (optionId == null || st == null) return rejectOption(o, String.valueOf(optionId), StartResult.UNKNOWN_OPTION);
        if (optionId.equals("step")) {
            int li = st == Station.PAKET ? -1 : resolveStationItem(orderId, itemIndex, stationId);
            return startStepImpl(orderId, li >= 0 ? li : itemIndex, stationId, null);
        }
        if (optionId.startsWith("size:")) {
            PortionSize sz = PortionSize.byId(optionId.substring(5));
            if (sz == null || st != Station.DONER) return rejectOption(o, optionId, StartResult.UNKNOWN_OPTION);
            int li = resolveStationItem(orderId, itemIndex, stationId);
            return startStepImpl(orderId, li >= 0 ? li : itemIndex, stationId, sz);
        }
        if (optionId.startsWith("add:")) {
            if (st != Station.SOS) return rejectOption(o, optionId, StartResult.UNKNOWN_OPTION);
            return addTopping(orderId, itemIndex, optionId.substring(4));
        }
        if (optionId.equals("auto")) {
            if (st != Station.SOS) return rejectOption(o, optionId, StartResult.UNKNOWN_OPTION);
            return autoToppings(orderId, itemIndex);
        }
        if (optionId.startsWith("remove:")) {
            if (st != Station.SOS) return rejectOption(o, optionId, StartResult.UNKNOWN_OPTION);
            return removeTopping(orderId, itemIndex, optionId.substring(7));
        }
        if (optionId.equals("undo")) return removeLastTopping(orderId, itemIndex);
        if (optionId.equals("finish")) return finishToppings(orderId, itemIndex);
        if (optionId.equals("read:added") || optionId.equals("read:target")) {
            if (o == null) return StartResult.NO_ORDER;
            int li = resolveToppingItem(o, itemIndex);
            if (li < 0) return rejectOption(o, optionId, StartResult.INVALID);
            for (GameListener l : snap()) l.onStationInfo(o, li, optionId);
            return StartResult.INFO;
        }
        if (optionId.startsWith("prep:")) {
            String[] p = optionId.split(":");
            PrepResult r;
            if (p.length == 4 && p[1].equals("doner")) {
                int n;
                try { n = Integer.parseInt(p[3]); } catch (NumberFormatException ex) { return rejectOption(o, optionId, StartResult.UNKNOWN_OPTION); }
                r = startPrepDoner(p[2], n);
            } else if (p.length == 2 && p[1].equals("drink")) {
                r = startPrepDrink();
            } else if (p.length == 2 && p[1].equals("fries")) {
                r = startPrepFries();
            } else if (p.length == 2 && p[1].equals("tray")) {
                Set<String> pick = trayPick(false);
                r = startPrepTray(pick);
            } else {
                return rejectOption(o, optionId, StartResult.UNKNOWN_OPTION);
            }
            return fromPrep(r);
        }
        return rejectOption(o, optionId, StartResult.UNKNOWN_OPTION);
    }

    private static StartResult fromPrep(PrepResult r) {
        switch (r) {
            case STARTED: return StartResult.STARTED;
            case NO_SLOT: return StartResult.NO_SLOT;
            case OUT_OF_STOCK: return StartResult.OUT_OF_STOCK;
            case FULL: return StartResult.FULL;
            case INVALID: return StartResult.INVALID;
            default: return StartResult.NOT_RUNNING;
        }
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
        PrepResult r = launchPrep(kind, ids, 0, kind.batch);
        return r;
    }

    /** 4 porsiyon dilimle (eski API). */
    public PrepResult startPrepDoner(String meatId) {
        return startPrepDoner(meatId, PrepKind.DONER.batch);
    }

    /** Hazır dilimlenmiş döner: 4 porsiyon (6 sn) veya 2 porsiyon (3,6 sn). Başka miktar INVALID. */
    public PrepResult startPrepDoner(String meatId, int portions) {
        if (state == State.FINISHED || state == State.FAILED) return PrepResult.NOT_ALLOWED;
        if (state == State.READY) beginPrep();
        if (portions != 4 && portions != 2) return reject(PrepKind.DONER, PrepResult.INVALID, null);
        if (playerActiveCount() >= upgrades.slots()) return PrepResult.NO_SLOT;
        return launchPrep(PrepKind.DONER, Collections.singletonList(meatId), 0, portions);
    }

    public PrepResult startPrepDrink() { return startPrep(PrepKind.DRINK, Collections.<String>emptyList()); }

    /** Hazır patates: 4 porsiyon kızartır (Ocak, ~5 sn); gün sonunda atılır. Menüde patates yoksa (3. günden önce) INVALID. */
    public PrepResult startPrepFries() {
        if (state == State.FINISHED || state == State.FAILED) return PrepResult.NOT_ALLOWED;
        if (state == State.READY) beginPrep();
        if (!menuHasFries()) return reject(PrepKind.FRIES, PrepResult.INVALID, null);
        return startPrep(PrepKind.FRIES, Collections.<String>emptyList());
    }

    public PrepResult startPrepTray(Collection<String> ingredientIds) { return startPrep(PrepKind.TRAY, ingredientIds); }

    /** Devam eden hazırlıklarla üretilecek miktar (döner: porsiyon, içecek: bardak, tepsi: malzeme başına kullanım). */
    private int pendingPrep(PrepKind kind, String id) {
        int n = 0;
        for (ActiveStep a : active) {
            if (a.prep != kind) continue;
            if (kind == PrepKind.DRINK || kind == PrepKind.FRIES || (a.ids != null && a.ids.contains(id))) n += a.amount;
        }
        return n;
    }

    private Map<String, Integer> prepNeeds(PrepKind kind, Set<String> ids, int amount) {
        Map<String, Integer> m = new LinkedHashMap<>();
        switch (kind) {
            case DONER: for (String id : ids) m.put(id, amount); break;
            case DRINK: m.put(Catalog.AYRAN, amount); break;
            case FRIES: m.put(Catalog.PATATES, amount); break;
            default: for (String id : ids) m.put(id, amount); break;
        }
        return m;
    }

    /** Hazırlığı doğrular ve başlatır; worker 0 = oyuncu (yuva zaten kontrol edildi), >0 = çırak. */
    private PrepResult launchPrep(PrepKind kind, Collection<String> idsIn, int worker, int amount) {
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
                    if (i == null || !i.isTopping() || !i.isOpen(day)) return reject(kind, PrepResult.INVALID, null);
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
                if (getReadyMeatHalves(id) + 2 * (pendingPrep(kind, id) + amount) > 2 * cap) return reject(kind, PrepResult.FULL, null);
                break;
            }
            case DRINK:
                if (save.readyDrinks + pendingPrep(kind, null) + kind.batch > cap) return reject(kind, PrepResult.FULL, null);
                break;
            case FRIES:
                if (readyFries + pendingPrep(kind, null) + kind.batch > cap) return reject(kind, PrepResult.FULL, null);
                break;
            default:
                for (String id : ids) {
                    if (getTrayUses(id) + pendingPrep(kind, id) + kind.batch > cap) return reject(kind, PrepResult.FULL, null);
                }
                break;
        }
        Map<String, Integer> need = prepNeeds(kind, ids, amount);
        for (Map.Entry<String, Integer> e : need.entrySet()) {
            if (!save.inventory.has(e.getKey(), e.getValue())) {
                return reject(kind, PrepResult.OUT_OF_STOCK, Catalog.ingredient(e.getKey()));
            }
        }
        save.inventory.consumeAll(need);
        if (worker == 0) playerAction();
        double d = prepSeconds(kind, amount) * (worker > 0 ? APPRENTICE_SLOWDOWN : 1f);
        float dur = (float) Math.max(MIN_STEP_SECONDS, d);
        Step syn = new Step(kind.displayName, kind.station, dur, Collections.<String, Integer>emptyMap());
        ActiveStep a = new ActiveStep(null, -1, -1, syn, dur, worker == 0 ? freeSlot() : -1, worker, kind, ids, false);
        a.amount = amount;
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
        boolean timed = isTimed();
        float frac = o.customer.patienceFraction();
        // Süresiz modda hız bonusu yok: teslim standart memnuniyettir (sabır sabit; bahşiş oranı UNTIMED_TIP_RATE x UNTIMED_TIP_MULT).
        boolean fast = timed && frac >= 0.5f;
        boolean happy = !timed || frac >= 0.2f;
        int price = o.price();
        double tipRate = timed ? (fast ? 0.20 : (happy ? 0.08 : 0.0)) : UNTIMED_TIP_RATE;
        if (mistakes > 0) tipRate *= 0.5;
        double flowTip = v06Balance ? 1.0 : getFlowMode().tipMult;
        int tip = (int) Math.round(price * tipRate * o.customer.type.tipMult * upgrades.tipMult() * flowTip
                * (timed ? 1.0 : UNTIMED_TIP_MULT));
        playerAction();
        save.money += price + tip;
        summary.revenue += price;
        summary.tips += tip;
        summary.delivered++;
        remove(o);
        double penalty = Math.min(MISTAKE_PENALTY_MAX, MISTAKE_PENALTY_EACH * mistakes);
        if (mistakes > 0) {
            summary.mistakes++;
            if (check.sizeMistakeCount() > 0) summary.sizeMistakes++;
            for (GameListener l : snap()) l.onOrderMistake(o, check, penalty);
        } else if (check.perfect()) {
            summary.perfect++;
            for (GameListener l : snap()) l.onOrderPerfect(o);
        }
        for (GameListener l : snap()) l.onOrderDelivered(o, price, tip, happy && mistakes == 0);
        double gain = !timed ? UNTIMED_STAR_GAIN : (fast ? 0.12 : (happy ? 0.08 : 0.02));
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
            if (line.hasSize()) {
                c.sizedLines++;
                if (line.sizeMismatch()) c.addSize(new ToppingCheck.SizeMistake(line.product.name, line.getSize(), line.getChosenSize()));
            }
            if (!line.product.hasToppings()) continue;
            c.toppingLines++;
            Set<String> chosen = line.getChosen();
            if (chosen == null) chosen = new LinkedHashSet<>();
            Set<String> target = line.getTarget();
            for (String t : target) {
                if (!chosen.contains(t)) {
                    c.add(new ToppingCheck.Mistake(t, false, !line.getDefaults().contains(t), line.product.name));
                }
            }
            for (String t : chosen) {
                if (!target.contains(t)) {
                    c.add(new ToppingCheck.Mistake(t, true, line.getDefaults().contains(t), line.product.name));
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
        final boolean timed = isTimed();
        if (timed) dayClock += h;
        // 1) çalışan işler
        advanceTasks(h);
        if (state != State.RUNNING) return;
        // 2) sabır (süresiz modda donar: müşteri gitmez, uyarı yok)
        if (timed) for (Order o : new ArrayList<>(orders)) {
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
        // 3) geliş (süreli: gün süresi boyunca; süresiz: kota dolana dek, sıra boşaldıkça)
        if (spawning && (timed ? dayClock < cfg.duration : arrived < quota)) {
            spawnTimer -= h;
            if (!timed && orders.isEmpty() && spawnTimer > UNTIMED_IDLE_WAIT) spawnTimer = UNTIMED_IDLE_WAIT;
            if (spawnTimer <= 0f) {
                if (orders.size() < maxQueue()) {
                    spawnCustomer();
                    double fm = v06Balance ? 1.0 : getFlowMode().arrivalMult;
                    double mult = timed ? DayConfig.starMultiplier(save.stars) * upgrades.arrivalMult() * fm
                            : fm * UNTIMED_ARRIVAL_FACTOR;
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
        if (!isTimed()) {
            // Süresiz gün: kota kadar müşteri geldi ve tüm siparişler teslim edildi.
            if (arrived >= quota && orders.isEmpty()) finishDay();
        } else if (dayClock >= cfg.duration && orders.isEmpty()) {
            finishDay();
        } else if (dayClock >= cfg.duration + cfg.overtime) {
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

    private float lineStepSeconds(Step step, boolean readyMeat, boolean readyDrink, boolean readyFry, int worker, PortionSize sz) {
        double d;
        float min = MIN_STEP_SECONDS;
        if (readyMeat) { d = READY_MEAT_SECONDS * sz.timeMult; min = MIN_READY_SECONDS; }
        else if (readyDrink) { d = READY_DRINK_SECONDS; min = MIN_READY_SECONDS; }
        else if (readyFry) { d = READY_FRIES_SECONDS; min = MIN_READY_SECONDS; }
        else {
            d = step.baseSeconds * upgrades.speedMult(step.station);
            if (step.isSized() || step.isBreadStep()) d *= sz.timeMult;
        }
        d *= save.tempo;
        if (worker > 0) d *= APPRENTICE_SLOWDOWN;
        return (float) Math.max(min, d);
    }

    /**
     * Stok denetimi yapılmış açık bir kalem adımını başlatır (topping hariç). meatChoice: oyuncunun seçtiği boy
     * (dilimleme adımında; null = istenen boy). Et/ekmek boya göre, yarım porsiyon sayacıyla tüketilir.
     */
    private void launchLineStep(Order o, int li, int si, int worker, PortionSize meatChoice) {
        OrderLine line = o.getLine(li);
        Step step = line.step(si);
        PortionSize sz = needSize(line, step, meatChoice);
        boolean rm = usesReadyMeat(step, sz);
        boolean rd = usesReadyDrink(step);
        boolean rf = usesReadyFries(step);
        if (rf) readyFries -= step.needs.get(Catalog.PATATES);
        if (rm) {
            String m = step.meatId();
            readyMeat.put(m, getReadyMeatHalves(m) - step.needs.get(m) * sz.halves);
        }
        if (rd) save.readyDrinks -= step.needs.get(Catalog.AYRAN);
        for (Map.Entry<String, Integer> e : step.needs.entrySet()) {
            String id = e.getKey();
            if (rm && id.equals(step.meatId())) continue;
            if (rd && id.equals(Catalog.AYRAN)) continue;
            if (rf && id.equals(Catalog.PATATES)) continue;
            if (scaled(step, id)) consumeHalves(id, e.getValue() * sz.halves);
            else save.inventory.add(id, -e.getValue());
        }
        if (step.isSized()) line.setChosenSize(sz);
        float dur = lineStepSeconds(step, rm, rd, rf, worker, sz);
        if (worker == 0) playerAction();
        line.setRunning(si, true);
        blocked.remove(o.id);
        int slot = worker == 0 ? freeSlot() : -1;
        ActiveStep a = new ActiveStep(o, li, si, step, dur, slot, worker, null, null, false);
        a.size = step.isSized() || step.isBreadStep() ? sz : null;
        active.add(a);
        if (worker == 0) {
            if (step.isSized()) for (GameListener l : snap()) l.onSizeChosen(o, li, line.getSize(), sz);
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
        if (worker == 0) playerAction();
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
        if (a.autoAdded != null) { // otomatik malzeme işi bitti
            line.setRunning(a.stepIndex, false);
            // Rahat ve Gerçekçi (standart setle bitir): adım biter; Dengeli: yalnızca istek farkı yoksa biter
            boolean finish = a.autoMode != FlowMode.DENGELI || line.getAddedSet().equals(line.getTarget());
            List<Ingredient> added = ingredients(a.autoAdded), removed = ingredients(a.autoRemoved);
            for (GameListener l : snap()) l.onToppingsAutoAdded(o, a.itemIndex, added, removed, finish);
            if (finish) {
                Step ts = line.step(a.stepIndex);
                line.finishChosen();
                line.setDone(a.stepIndex);
                blocked.remove(o.id);
                for (GameListener l : snap()) l.onToppingsFinished(o, a.itemIndex, line.getChosen());
                afterLineStep(o, line, ts, 0, a.total);
            }
            return;
        }
        if (a.addId != null) { // tek malzeme eklendi: malzeme adımı BİTMEZ, yeni ekleme için açılır
            line.setRunning(a.stepIndex, false);
            Ingredient ing = Catalog.ingredient(a.addId);
            int count = line.addedCount(a.addId);
            for (GameListener l : snap()) l.onToppingAdded(o, a.itemIndex, ing, count);
            return;
        }
        line.setDone(a.stepIndex);
        afterLineStep(o, line, a.step, a.worker, a.total);
    }

    /** Kalem adımı bitti (zamanlı iş veya "Malzemeleri bitir"): olaylar, kalem/paketleme açılışı, öneri. */
    private void afterLineStep(Order o, OrderLine line, Step step, int worker, float total) {
        boolean player = worker == 0;
        if (player) for (GameListener l : snap()) l.onStepFinished(o, step, false);
        else {
            summary.apprenticeTasks++;
            ApprenticeAction aa = new ApprenticeAction(worker, false, o, line.index, step, null, false, total);
            for (GameListener l : snap()) l.onApprenticeAction(aa);
        }
        if (line.isDone()) for (GameListener l : snap()) l.onItemFinished(o, line.index);
        if (o.allLinesDone() && !o.readyToPackAnnounced) {
            o.readyToPackAnnounced = true;
            for (GameListener l : snap()) l.onOrderReadyToPack(o);
        }
        if (o.id == selectedId) hint(o);
    }

    private void finishPrep(ActiveStep a) {
        PrepKind kind = a.prep;
        int amount = a.amount > 0 ? a.amount : kind.batch;
        switch (kind) {
            case DONER: {
                String id = a.ids.iterator().next();
                readyMeat.put(id, getReadyMeatHalves(id) + 2 * amount);
                break;
            }
            case DRINK: save.readyDrinks += amount; break;
            case FRIES: readyFries += amount; break;
            default:
                for (String id : a.ids) trayUses.put(id, getTrayUses(id) + amount);
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
                        if (!save.inventory.hasAll(effectiveNeeds(s, needSize(line, s, null)))) continue;
                        launchLineStep(o, line.index, si, w, null);
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
        float left = prepPhase ? 999f : (isTimed() ? cfg.duration - dayClock
                : Math.max(0, quota - arrived) * cfg.arrivalInterval);
        // 1) içecek (israf yok: ertesi güne kalır)
        if (menuHasDrinkPrep() && save.readyDrinks + pendingPrep(PrepKind.DRINK, null) <= 4
                && save.inventory.has(Catalog.AYRAN, PrepKind.DRINK.batch + 2)) {
            if (launchPrep(PrepKind.DRINK, Collections.<String>emptyList(), w, PrepKind.DRINK.batch) == PrepResult.STARTED) return true;
        }
        if (left <= 25f) return false;
        // 2) döner
        for (Ingredient m : getPrepMeats()) {
            if (getReadyMeatHalves(m.id) + 2 * pendingPrep(PrepKind.DONER, m.id) <= 6 && save.inventory.has(m.id, PrepKind.DONER.batch + 2)) {
                if (launchPrep(PrepKind.DONER, Collections.singletonList(m.id), w, PrepKind.DONER.batch) == PrepResult.STARTED) return true;
            }
        }
        // 3) salata/sos tepsisi (seviye 2+): menünün en sık varsayılan malzemeleri (günün açık olanları)
        if (lvl >= 2) {
            Set<String> pick = trayPick(true);
            if (pick.size() >= 2) {
                if (launchPrep(PrepKind.TRAY, pick, w, PrepKind.TRAY.batch) == PrepResult.STARTED) return true;
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
        for (int k = 0; k < n; k++) items.add(maybeFries(pickProduct(menu, type)));
        List<PortionSize> sizes = rollSizes(items, type);
        String name = Catalog.NAMES[nameCursor++ % Catalog.NAMES.length];
        arrived++;
        addOrder(name, type, items, rollRequests(items), sizes);
    }

    /**
     * Patates girebilen üründe müşteri "patatesli" isteyebilir ({@link DayConfig#friesChance}, 3. günden açık, günle artar).
     * Karar ayrı rastgele kaynaktan gelir: 3. günden önce hiç rastgele sayı tüketilmez.
     */
    private Product maybeFries(Product p) {
        double chance = DayConfig.friesChance(day);
        if (chance <= 0 || !p.friesOk()) return p;
        return friesRng.nextDouble() < chance ? p.friesVariant() : p;
    }

    /** Döner içeren kalemler için boy: 1. gün yalnızca tam; 2. günden yarım; 4. günden duble (müşteri tipine göre olasılık). */
    private List<PortionSize> rollSizes(List<Product> items, CustomerType type) {
        List<PortionSize> out = new ArrayList<>();
        for (Product p : items) {
            if (!p.hasMeat()) { out.add(PortionSize.FULL); continue; }
            double r = rng.nextDouble();
            double hc = PortionSize.HALF.isOpen(day) ? type.halfChance : 0.0;
            double dc = PortionSize.DOUBLE.isOpen(day) ? type.doubleChance : 0.0;
            out.add(r < hc ? PortionSize.HALF : (r < hc + dc ? PortionSize.DOUBLE : PortionSize.FULL));
        }
        return out;
    }

    private Order addOrder(String name, CustomerType type, List<Product> items, List<Request> reqs, List<PortionSize> sizes) {
        // v0.7: akışın gerektirdiği elle dokunuşlar (boy seçimi, bitirme, istek farkları) insan süresidir; yükseltmeyle
        // kısalmaz, bu yüzden günle azalan workPatience yerine sabit çarpanla sabra eklenir.
        double hands = v06Balance ? 0.0 : Order.interactionWork(items, sizes, reqs, day, getFlowMode()) * Order.INTERACTION_PATIENCE;
        double pat = (type.patienceSeconds * cfg.patienceFactor + Order.estimateWork(items, sizes) * cfg.workPatience + hands)
                * upgrades.patienceMult() * save.tempo * (v06Balance ? 1.0 : getFlowMode().patienceMult);
        Customer c = new Customer(name, type, (float) pat);
        Order o = new Order(nextOrderId++, c, items, reqs, day, sizes);
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
        return spawnOrder(productIds, requestTexts, null);
    }

    /**
     * Boy da belirtilen senaryo siparişi: sizes, ürünlerle aynı sırada ({@link PortionSize}); null/kısa liste = tam ekmek.
     * Boy günün açılışına bakmaz (senaryo/test için).
     */
    public Order spawnOrder(List<String> productIds, List<String> requestTexts, List<PortionSize> sizes) {
        if (state == State.FINISHED || state == State.FAILED) return null;
        ensureStarted();
        if (state != State.RUNNING) return null;
        List<Product> items = new ArrayList<>();
        for (String id : productIds) {
            boolean fries = id.endsWith("+patates"); // senaryo/test: "ekmek_arasi+patates" = patatesli çeşit
            Product p = Catalog.product(fries ? id.substring(0, id.length() - "+patates".length()) : id);
            if (p == null || (fries && !p.friesOk())) return null;
            items.add(fries ? p.friesVariant() : p);
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
        return addOrder(name, type, items, reqs, sizes);
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
                    List<String> pool = p.toppingPool(day);
                    Set<String> cur = new LinkedHashSet<>(p.defaultToppings(day));
                    for (Request e : out) e.apply(pool, cur);
                    if (q.changes(p, pool, cur)) { useful = true; break; }
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
            portions += (e.getValue() + 1) / 2; // yarım porsiyon da bir porsiyon sayılır
            cost += (e.getValue() * Catalog.ingredient(e.getKey()).price + 1) / 2;
        }
        for (Map.Entry<String, Integer> e : trayUses.entrySet()) {
            portions += e.getValue();
            cost += e.getValue() * Catalog.ingredient(e.getKey()).price;
        }
        // hazır patates gün sonunda soğur, atılır
        if (readyFries > 0) {
            portions += readyFries;
            cost += readyFries * Catalog.ingredient(Catalog.PATATES).price;
        }
        // yarım kalan hazırlıkların tükettiği malzeme de boşa gider
        for (ActiveStep a : active) {
            if (a.prep == PrepKind.FRIES) {
                portions += a.amount;
                cost += a.amount * Catalog.ingredient(Catalog.PATATES).price;
                continue;
            }
            if (a.prep == null || a.prep == PrepKind.DRINK) continue;
            for (String id : a.ids) {
                portions += a.amount;
                cost += a.amount * Catalog.ingredient(id).price;
            }
        }
        readyMeat.clear();
        halfOpen.clear();
        trayUses.clear();
        readyFries = 0;
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
        summary.timed = isTimed();
        StringBuilder sb = new StringBuilder();
        for (Product p : Catalog.newProductsOnDay(day + 1)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p.name);
        }
        summary.newProductsNext = sb.toString();
        StringBuilder ni = new StringBuilder();
        List<Ingredient> newIng = Catalog.newIngredientsOnDay(day + 1);
        for (int i = 0; i < newIng.size(); i++) {
            if (i > 0) ni.append(", ");
            ni.append(newIng.get(i).name.toLowerCase(new java.util.Locale("tr")));
        }
        summary.newIngredientsNext = ni.toString();
        save.day = day + 1;
        if (save.day > save.bestDay) save.bestDay = save.day;
        for (GameListener l : snap()) l.onDayFinished(summary);
    }

    private void failDay() {
        state = State.FAILED;
        summary.failed = true;
        summary.timed = isTimed();
        summary.endStars = save.stars;
        orders.clear();
        active.clear();
        readyMeat.clear();
        halfOpen.clear();
        trayUses.clear();
        readyFries = 0;
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
