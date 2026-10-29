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
import com.doner.oyunu.engine.StationOption;
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
    /** Süresiz modda bir günün sanal süre koruması (sn). */
    static final float UNTIMED_GUARD_SECONDS = 6000f;
    private float cooldown;
    private float delay;
    public int emergencyBuys;
    public int prepActions;
    /**
     * Oyuncu dokunuş sayısı (v0.7 ölçütü): her istasyon seçeneği, sipariş seçimi, teslim, acil alım bir dokunuş;
     * seçenek paneli gerektiren (tek dokunuşla uygulanmayan) istasyonda paneli açmak da bir dokunuş (panel art arda
     * eklemelerde açık kalır). Hazırlık hariç (sipariş başına ölçüt için).
     */
    public int taps;
    /** Teslim edilen sipariş sayısı (bot sayar). */
    public int delivered;
    /** Aynı siparişler v0.4 akışıyla (her adım tek dokunuş, kalem başına paketleme) kaç dokunuş tutardı. */
    public int v04Taps;
    /** İnsan benzeri: panel açmak da bir tepki süresi alır, tepki ±%20 oynar, ardışık ekleme indirimi 0,7. */
    private boolean humanLike;
    private java.util.Random jitter;
    private String panelKey;

    /** İnsan benzeri bot (gerçekçi akış ölçümü için): panel açmak ayrı bir tepki, tepki süresi ±%20. */
    public TestBot humanLike(long seed) { this.humanLike = true; this.jitter = new java.util.Random(seed); return this; }

    /** Bot ölçütü: teslim başına dokunuş. */
    public double tapsPerOrder() { return delivered == 0 ? 0 : taps / (double) delivered; }

    /** Eski davranışa en yakın: hazırlık yapmayan, isteklere dikkat eden bot. */
    public TestBot(float reactionSeconds) { this(reactionSeconds, false, true); }

    public TestBot(float reactionSeconds, boolean prep, boolean careful) {
        this.reaction = reactionSeconds;
        this.delay = reactionSeconds;
        this.prep = prep;
        this.careful = careful;
    }

    /** Günü bitene (veya başarısız olana) kadar oynar. Güvenlik: süreli 1000 sn, süresiz 6000 sn sanal süre. */
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
        // Süresiz günde (kota kadar müşteri) bot daha uzun oynar; yine de sanal süreyle sınırlıdır (sonsuz döngü olamaz).
        final float limit = e.isTimed() ? 1000f : UNTIMED_GUARD_SECONDS;
        while (e.isRunning() && guard < limit) {
            if (cooldown <= 0f && act(e)) {
                cooldown = delay;
                if (humanLike) cooldown *= 0.8f + 0.4f * jitter.nextFloat();
                delay = reaction;
            }
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
        if (humanLike && panelKey != null) {
            // Panel açıkken (gerçek arayüzde) önce paneldeki seçim yapılır; teslim vb. panel kapanınca.
            Order po = e.getOrder(Integer.parseInt(panelKey.substring(0, panelKey.indexOf(':'))));
            Step ps = po == null || po.isReady() ? null : po.nextStep();
            if (ps != null && e.getFreeSlots() > 0) {
                StartResult r = doStep(e, po, ps);
                if (r == StartResult.STARTED || r == StartResult.INFO) return true;
                // panelde stok yoksa insan "Acil al" der (panel açık kalır)
                if (r == StartResult.OUT_OF_STOCK && e.emergencyBuyMissing(po.id)) { emergencyBuys++; taps++; return true; }
            }
            panelKey = null; // seçenek uygulanamadı: panel kapatıldı (Kapat bir dokunuş)
            taps++;
            return true;
        }
        if (humanLike) {
            // İnsan gibi: seçili siparişi bitirmeye çalışır; yalnızca sabrı çok azalan (≤%25) bir sipariş araya girer.
            Order sel = e.getSelectedOrder();
            if (sel != null && !sel.isReady() && sel.nextStep() != null && sel.customer.patienceFraction() > 0f) {
                boolean urgent = false;
                for (Order o : list) if (o != sel && !o.isReady() && o.nextStep() != null && o.customer.patienceFraction() <= 0.25f) urgent = true;
                if (!urgent) { list.remove(sel); list.add(0, sel); }
            }
        }
        for (Order o : list) {
            if (o.isReady()) {
                int v04 = 2; // v0.4 eşdeğeri: seç + teslim + kalem başına (her adım bir dokunuş + kalem paketi)
                for (OrderLine l : o.getLines()) v04 += l.stepCount() + 1;
                e.deliver(o.id); taps++; delivered++; v04Taps += v04; panelKey = null; return true;
            }
        }
        if (e.getFreeSlots() > 0) {
            for (Order o : list) {
                if (o.isReady()) continue;
                Step s = o.nextStep();
                if (s == null) continue;
                if (e.getSelectedOrderId() != o.id) { e.selectOrder(o.id); taps++; panelKey = null; }
                StartResult r = doStep(e, o, s);
                if (r == StartResult.STARTED || r == StartResult.INFO) return true;
                if (r == StartResult.OUT_OF_STOCK) {
                    if (e.emergencyBuyMissing(o.id)) { emergencyBuys++; taps++; return true; }
                }
            }
            if (prep && prepStep(e, false)) return true;
        }
        return false;
    }

    /**
     * İstasyon seçeneğini uygular ve dokunuşu sayar: seçenek listesi tek dokunuşla uygulanmıyorsa (panel) panel açma da
     * bir dokunuştur; insan benzeri botta panel açmak ayrıca bir tepki süresi alır. Panel art arda eklemelerde açık kalır.
     */
    private StartResult apply(GameEngine e, Order o, int li, Station st, String optionId) {
        List<StationOption> ops = e.getStationOptions(o.id, li, st.id);
        boolean panel = StationOption.direct(ops) == null;
        String key = o.id + ":" + li + ":" + st.id;
        if (panel && !key.equals(panelKey)) {
            taps++;
            if (humanLike) { panelKey = key; return StartResult.INFO; } // panel açıldı: seçim bir sonraki tepkide
        }
        StartResult r = e.applyStationOption(o.id, li, st.id, optionId);
        taps++;
        boolean keepOpen = panel && (optionId.startsWith("add:") || optionId.startsWith("remove:") || optionId.equals("undo")
                || optionId.startsWith("read:") || r != StartResult.STARTED);
        panelKey = keepOpen ? key : null;
        return r;
    }

    private static boolean has(List<StationOption> ops, String id) {
        for (StationOption op : ops) if (op.id.equals(id)) return true;
        return false;
    }

    /** Önerilen adımı seçenek menüsüyle yapar (her akışta): boy seçimi / otomatik boy, malzeme, bitir, ya da genel adım. */
    private StartResult doStep(GameEngine e, Order o, Step s) {
        if (s == o.getPackStep()) return apply(e, o, -1, Station.PAKET, "step");
        int li = lineOfOpenStep(o, s);
        if (li < 0) return StartResult.INVALID;
        if (s.isTopping()) return toppings(e, o, li);
        if (s.isSized()) {
            OrderLine line = o.getLine(li);
            List<StationOption> ops = e.getStationOptions(o.id, li, Station.DONER.id);
            PortionSize pick = careful ? line.getSize() : PortionSize.FULL;
            if (has(ops, "size:" + pick.id)) return apply(e, o, li, Station.DONER, "size:" + pick.id);
            return apply(e, o, li, Station.DONER, "step"); // akış boyu otomatik seçiyor
        }
        return apply(e, o, li, s.station, "step");
    }

    private static int lineOfOpenStep(Order o, Step s) {
        for (OrderLine l : o.getLines()) {
            for (int i = 0; i < l.stepCount(); i++) if (l.step(i) == s && l.isStepOpen(i)) return l.index;
        }
        return -1;
    }

    /**
     * Malzeme adımı (her akışta seçenek menüsüyle): "auto" sunuluyorsa önce onu (Rahat/Dengeli); sonra hedef (veya
     * dikkatsizse varsayılan) kümeye göre fazlaları çıkarır, eksikleri tek tek ekler; hepsi tamamsa "Malzemeleri bitir".
     */
    private StartResult toppings(GameEngine e, Order o, int li) {
        OrderLine line = o.getLine(li);
        List<StationOption> ops = e.getStationOptions(o.id, li, Station.SOS.id);
        // Gerçekçi kısayolu ("Standart malzemelerle bitir") adımı bitirir: dikkatli bot yalnızca istek farkı yoksa kullanır
        boolean shortcutOk = e.getFlowMode() != com.doner.oyunu.engine.FlowMode.GERCEKCI || !careful
                || line.getTarget().equals(line.getDefaults());
        if (has(ops, "auto") && shortcutOk) {
            StartResult r = apply(e, o, li, Station.SOS, "auto");
            if (r == StartResult.OUT_OF_STOCK && e.emergencyBuyMissing(o.id)) { emergencyBuys++; taps++; return StartResult.STARTED; }
            return r;
        }
        Set<String> want = new LinkedHashSet<>(careful ? line.getTarget() : line.getDefaults());
        for (String id : new ArrayList<>(line.getAddedSet())) {
            if (!want.contains(id) && has(ops, "remove:" + id)) {
                StartResult r = apply(e, o, li, Station.SOS, "remove:" + id);
                if (r == StartResult.STARTED) delay = reaction * addFactor();
                return r;
            }
        }
        for (String id : want) {
            if (line.addedCount(id) > 0) continue;
            StartResult r = apply(e, o, li, Station.SOS, "add:" + id);
            if (r == StartResult.OUT_OF_STOCK) {
                if (e.emergencyBuy(id)) { emergencyBuys++; taps++; return StartResult.STARTED; }
                return StartResult.OUT_OF_STOCK;
            }
            if (r == StartResult.STARTED) delay = reaction * addFactor();
            return r;
        }
        return apply(e, o, li, Station.SOS, "finish");
    }

    private float addFactor() { return humanLike ? 0.7f : ADD_REACTION_FACTOR; }

    /** Hazırlık sırası: içecek, döner, tepsi. Pre-day: hedef daha yüksek. */
    private boolean prepStep(GameEngine e, boolean preDay) {
        if (e.getFreeSlots() <= 0) return false;
        float left = preDay || !e.isTimed() ? 999f : e.getTimeLeft();
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
