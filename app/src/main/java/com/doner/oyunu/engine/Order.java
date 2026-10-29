package com.doner.oyunu.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bir müşterinin siparişi: kalemler (OrderLine), müşteri istekleri ve ortak "Siparişi paketle" adımı.
 * Kalemler serbest sırayla yapılır; tüm kalemler bitince paketleme açılır; paketleme bitince sipariş hazırdır.
 */
public final class Order {
    public final int id;
    public final Customer customer;
    public final List<Product> items;
    public final List<Request> requests;
    /** Tüm kalemlerin adımları sırayla + sondaki paketleme adımı (özet/ilerleme için). */
    public final List<Step> steps;
    private final List<OrderLine> lines;
    private final Step packStep;
    boolean packing;
    boolean ready;
    boolean readyToPackAnnounced;
    int position;

    Order(int id, Customer customer, List<Product> items, List<Request> requests) {
        this.id = id;
        this.customer = customer;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.requests = Collections.unmodifiableList(new ArrayList<>(requests));
        List<OrderLine> ls = new ArrayList<>();
        List<Step> all = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Product p = items.get(i);
            Set<String> target = new LinkedHashSet<>();
            if (p.hasToppings()) {
                target.addAll(p.defaultToppings);
                for (Request r : requests) r.apply(p, target);
                // havuz sırasında tut
                Set<String> ordered = new LinkedHashSet<>();
                for (String id2 : p.toppingPool) if (target.contains(id2)) ordered.add(id2);
                target = ordered;
            }
            ls.add(new OrderLine(p, i, target));
            all.addAll(p.steps);
        }
        this.lines = Collections.unmodifiableList(ls);
        this.packStep = new Step("Siparişi paketle", Station.PAKET, packSeconds(items.size()), java.util.Collections.<String, Integer>emptyMap());
        all.add(packStep);
        this.steps = Collections.unmodifiableList(all);
    }

    static float packSeconds(int itemCount) { return 1.5f + 0.5f * itemCount; }

    /** Müşteri oluşmadan önce sabır hesabı için tahmini iş yükü (sn). */
    static float estimateWork(List<Product> items) {
        float t = packSeconds(items.size());
        for (Product p : items) t += p.workSeconds();
        return t;
    }

    // ------------------------------------------------------------ kalemler
    public int lineCount() { return lines.size(); }
    public OrderLine getLine(int i) { return lines.get(i); }
    public List<OrderLine> getLines() { return lines; }
    /** Ortak "Siparişi paketle" adımı (PAKET istasyonu). */
    public Step getPackStep() { return packStep; }

    public boolean allLinesDone() {
        for (OrderLine l : lines) if (!l.isDone()) return false;
        return true;
    }

    /** Paketleme şu an yapılabilir mi (tüm kalemler hazır, paketleme başlamadı ve bitmedi). */
    public boolean canPack() { return !ready && !packing && allLinesDone(); }
    public boolean isPacking() { return packing; }

    // ------------------------------------------------------------ durum
    /** Paketlendi, teslim edilebilir. */
    public boolean isReady() { return ready; }
    /** Bu siparişte şu an çalışan herhangi bir iş (kalem adımı veya paketleme) var mı. */
    public boolean isRunning() {
        if (packing) return true;
        for (OrderLine l : lines) if (l.isRunning()) return true;
        return false;
    }
    /** Sipariş yuvası numarası (1 tabanlı; stereo pan için 1 sol ... 3 sağ). */
    public int getPosition() { return position; }

    /** Biten adım sayısı (paketleme dahil); ilerleme çubuğu için. */
    public int getNextStepIndex() {
        int n = 0;
        for (OrderLine l : lines) n += l.doneCount();
        if (ready) n++;
        return n;
    }

    /** Önerilen sıradaki adım: ilk açık kalem adımı, hepsi bitince paketleme; yoksa (hepsi sürüyor/hazır) null. */
    public Step nextStep() {
        int[] r = suggest(-1);
        if (r == null) return null;
        return r[0] < 0 ? packStep : lines.get(r[0]).step(r[1]);
    }

    /**
     * Öneri: {kalemIndeksi, adımIndeksi}; paketleme için {-1,-1}; öneri yoksa null.
     * preferLine >= 0 ise o kalem öncelikli.
     */
    int[] suggest(int preferLine) {
        if (ready) return null;
        if (preferLine >= 0 && preferLine < lines.size()) {
            int s = lines.get(preferLine).firstOpenStep();
            if (s >= 0) return new int[]{preferLine, s};
        }
        for (OrderLine l : lines) {
            int s = l.firstOpenStep();
            if (s >= 0) return new int[]{l.index, s};
        }
        if (canPack()) return new int[]{-1, -1};
        return null;
    }

    // ------------------------------------------------------------ istekler
    public boolean hasRequests() { return !requests.isEmpty(); }

    /** "soğansız, acılı" (istek yoksa boş metin). */
    public String requestsText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < requests.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(requests.get(i).text);
        }
        return sb.toString();
    }

    /** Kalemin hedef malzeme kümesi (varsayılan ± istekler). */
    public Set<String> targetToppings(int lineIndex) { return lines.get(lineIndex).getTarget(); }

    public int price() {
        int t = 0;
        for (Product p : items) t += p.price;
        return t;
    }

    public float workSeconds() { return estimateWork(items); }

    /** "Ekmek arası döner, Ayran" gibi. */
    public String itemsText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(i == items.size() - 1 ? " ve " : ", ");
            sb.append(items.get(i).name);
        }
        return sb.toString();
    }
}
