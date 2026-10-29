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

    /** Siparişin verildiği gün (açık malzemeler için). */
    public final int day;

    Order(int id, Customer customer, List<Product> items, List<Request> requests, int day, List<PortionSize> sizes) {
        this.id = id;
        this.day = day;
        this.customer = customer;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.requests = Collections.unmodifiableList(new ArrayList<>(requests));
        List<OrderLine> ls = new ArrayList<>();
        List<Step> all = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Product p = items.get(i);
            Set<String> target = new LinkedHashSet<>();
            List<String> pool = p.toppingPool(day);
            Set<String> defaults = p.defaultToppings(day);
            if (p.hasToppings()) {
                target.addAll(defaults);
                for (Request r : requests) r.apply(pool, target);
                // havuz sırasında tut
                Set<String> ordered = new LinkedHashSet<>();
                for (String id2 : pool) if (target.contains(id2)) ordered.add(id2);
                target = ordered;
            }
            PortionSize sz = sizes != null && i < sizes.size() ? sizes.get(i) : PortionSize.FULL;
            ls.add(new OrderLine(p, i, target, pool, defaults, sz));
            all.addAll(p.steps);
        }
        this.lines = Collections.unmodifiableList(ls);
        this.packStep = new Step("Siparişi paketle", Station.PAKET, packSeconds(items.size()), java.util.Collections.<String, Integer>emptyMap());
        all.add(packStep);
        this.steps = Collections.unmodifiableList(all);
    }

    static float packSeconds(int itemCount) { return 1.5f + 0.5f * itemCount; }

    /** Müşteri oluşmadan önce sabır hesabı için tahmini iş yükü (sn); boy dilimleme/ısıtma süresini ölçekler. */
    static float estimateWork(List<Product> items, List<PortionSize> sizes) {
        float t = packSeconds(items.size());
        for (int i = 0; i < items.size(); i++) {
            Product p = items.get(i);
            PortionSize sz = sizes != null && i < sizes.size() && p.hasMeat() ? sizes.get(i) : PortionSize.FULL;
            for (Step s : p.steps) {
                float w = s.baseSeconds + (s.isTopping() ? Product.TOPPING_WORK_EACH * p.defaultToppings.size() : 0f);
                if (s.isSized() || s.isBreadStep()) w *= sz.timeMult;
                t += w;
            }
        }
        return t;
    }

    /** v0.7: elle iş süresinin sabra çevrilme çarpanı (gün ilerledikçe azalmaz). */
    static float INTERACTION_PATIENCE = 1.0f;
    /** v0.7: Gerçekçi akışta elle eklenen her hedef malzeme için ek iş yükü (sn; menüde art arda dokunuş). */
    static float MANUAL_ADD_WORK = 1.0f;
    /** v0.7: boy seçimi elle yapılan kalem için ek iş yükü (sn): menüyü açmak + boyu seçmek. */
    static float SIZE_CHOICE_WORK = 3.0f;
    /** v0.7: malzemesi elle bitirilen kalem için ek iş yükü (sn): menüyü açmak + "Malzemeleri bitir". */
    static float TOPPING_FINISH_WORK = 3.0f;
    /** v0.7: elle yapılan her istek farkı (çıkar/ekle) için ek iş yükü (sn). */
    static float REQUEST_DIFF_WORK = 1.5f;

    /**
     * v0.7 sabır düzeltmesi: akışın oyuncudan istediği EK dokunuşların tahmini süresi (sn), müşteri sabrına eklenir
     * (v0.6 tahmini yalnızca makine süresini ve malzeme başına 1,5 sn'yi sayıyordu; menü açma, boy seçme ve bitirme
     * dokunuşları sayılmıyordu). Gerçekçi: boylu her kalemde boy seçimi, malzemeli her kalemde bitirme + istek farkları.
     * Dengeli: yalnızca elle yapılanlar (yarım/duble boy; istek farkı olan kalemde bitirme + farklar). Rahat: 0.
     */
    static float interactionWork(List<Product> items, List<PortionSize> sizes, List<Request> requests, int day, FlowMode mode) {
        if (mode == FlowMode.RAHAT) return 0f;
        float t = 0f;
        for (int i = 0; i < items.size(); i++) {
            Product p = items.get(i);
            PortionSize sz = sizes != null && i < sizes.size() && p.hasMeat() ? sizes.get(i) : PortionSize.FULL;
            if (p.hasMeat() && !mode.autoSize(sz)) t += SIZE_CHOICE_WORK;
            if (!p.hasToppings()) continue;
            Set<String> def = p.defaultToppings(day);
            Set<String> target = new LinkedHashSet<>(def);
            for (Request r : requests) r.apply(p.toppingPool(day), target);
            int diff = 0;
            for (String id : target) if (!def.contains(id)) diff++;
            for (String id : def) if (!target.contains(id)) diff++;
            if (mode == FlowMode.GERCEKCI) t += TOPPING_FINISH_WORK + MANUAL_ADD_WORK * target.size() + REQUEST_DIFF_WORK * diff;
            else if (diff > 0) t += TOPPING_FINISH_WORK + REQUEST_DIFF_WORK * diff;
        }
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

    /** Toplam fiyat (boya göre kalem fiyatlarının toplamı). */
    public int price() {
        int t = 0;
        for (OrderLine l : lines) t += l.price();
        return t;
    }

    public float workSeconds() {
        List<PortionSize> sz = new ArrayList<>();
        for (OrderLine l : lines) sz.add(l.getSize());
        return estimateWork(items, sz);
    }

    /** "Ekmek arası döner (yarım ekmek) ve Ayran" gibi; tam ekmek ayrıca belirtilmez. */
    public String itemsText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append(i == lines.size() - 1 ? " ve " : ", ");
            sb.append(lines.get(i).displayName());
        }
        return sb.toString();
    }
}
