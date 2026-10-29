package com.doner.oyunu.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Siparişin bir kalemi (bir ürün) ve kendi adım durumu. */
public final class OrderLine {
    public final Product product;
    public final int index;
    private final boolean[] done;
    private final boolean[] running;
    private final Set<String> target;
    private final List<String> pool;
    private final Set<String> defaults;
    /** İstenen boy (döner içermeyen üründe FULL ve anlamsız). */
    private final PortionSize size;
    private PortionSize chosenSize;
    private Set<String> chosen;
    /** Tek tek eklenen malzemeler: id -> adet (1 normal, 2 bol). */
    private final Map<String, Integer> added = new LinkedHashMap<>();
    /** Ekleme geçmişi (geri al için): malzeme id'leri ve tepsiden mi geldiği. */
    private final List<String> history = new ArrayList<>();
    private final List<Boolean> fromTray = new ArrayList<>();

    OrderLine(Product product, int index, Set<String> target, List<String> pool, Set<String> defaults, PortionSize size) {
        this.product = product;
        this.index = index;
        this.done = new boolean[product.steps.size()];
        this.running = new boolean[product.steps.size()];
        this.target = target;
        this.pool = Collections.unmodifiableList(new ArrayList<>(pool));
        this.defaults = Collections.unmodifiableSet(new LinkedHashSet<>(defaults));
        this.size = product.hasMeat() && size != null ? size : PortionSize.FULL;
    }

    /** Siparişin verildiği günün açık topping havuzu (havuz sırasında). */
    public List<String> getPool() { return pool; }
    /** Siparişin verildiği günün açık varsayılan topping kümesi. */
    public Set<String> getDefaults() { return defaults; }

    /** Döner içeren kalem mi (boy taşır). */
    public boolean hasSize() { return product.hasMeat(); }
    /** Müşterinin istediği boy (döner içermeyen kalemde FULL). */
    public PortionSize getSize() { return size; }
    /** Oyuncunun DONER istasyonunda seçtiği boy; dilimleme başlamadıysa null. */
    public PortionSize getChosenSize() { return chosenSize; }
    void setChosenSize(PortionSize s) { chosenSize = s; }
    /** Boy hatası var mı (seçim yapıldı ve istenenden farklı). */
    public boolean sizeMismatch() { return hasSize() && chosenSize != null && chosenSize != size; }

    /** Boya göre fiyat (TL). */
    public int price() { return hasSize() ? (int) Math.round(product.price * size.priceMult) : product.price; }

    /** "Ekmek arası döner" / "Ekmek arası döner (yarım ekmek)" / "(duble)". Tam ekmek ayrıca belirtilmez. */
    public String displayName() {
        if (hasSize() && size != PortionSize.FULL) return product.name + " (" + size.spoken() + ")";
        return product.name;
    }

    // ---- tek tek ekleme durumu
    /** Eklenen malzemeler ve adetleri (1 normal, 2 bol), ekleme sırasında. */
    public Map<String, Integer> getAddedCounts() { return Collections.unmodifiableMap(added); }
    /** Ekleme geçmişi: her ekleme bir eleman (bol: aynı id iki kez). */
    public List<String> getAddedList() { return Collections.unmodifiableList(history); }
    public int addedCount(String id) { Integer v = added.get(id); return v == null ? 0 : v; }
    public int addedTotal() { return history.size(); }

    void recordAdd(String id, boolean tray) {
        added.put(id, addedCount(id) + 1);
        history.add(id);
        fromTray.add(tray);
    }

    /** Son eklemeyi geri alır; geri alınan malzemenin id'si (yoksa null). {@code wasTray[0]} tepsiden gelip gelmediğini yazar. */
    String undoLast(boolean[] wasTray) {
        if (history.isEmpty()) return null;
        int last = history.size() - 1;
        String id = history.remove(last);
        wasTray[0] = fromTray.remove(last);
        int c = addedCount(id) - 1;
        if (c <= 0) added.remove(id); else added.put(id, c);
        return id;
    }

    /**
     * Malzemenin TÜM eklemelerini kaldırır (bol dahil). Her kaldırılan ekleme için tepsiden gelip gelmediği listeye yazılır
     * (iade için). Eklenmemişse boş liste.
     */
    List<Boolean> removeAll(String id) {
        List<Boolean> trays = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).equals(id)) {
                history.remove(i);
                trays.add(fromTray.remove(i));
            }
        }
        added.remove(id);
        return trays;
    }

    /** Dengeli akışta varsayılan malzemeler otomatik eklendi mi (bir kez yapılır). */
    private boolean autoDone;
    public boolean isAutoDone() { return autoDone; }
    void setAutoDone() { autoDone = true; }

    /** Şu ana dek eklenen malzeme kümesi (havuz sırasında; bol sayılmaz). */
    public Set<String> getAddedSet() { return Collections.unmodifiableSet(orderedKeys()); }

    public int stepCount() { return product.steps.size(); }
    public Step step(int i) { return product.steps.get(i); }
    public boolean isStepDone(int i) { return done[i]; }
    public boolean isStepRunning(int i) { return running[i]; }

    void setDone(int i) { done[i] = true; running[i] = false; }
    void setRunning(int i, boolean r) { running[i] = r; }

    public boolean isDone() {
        for (boolean d : done) if (!d) return false;
        return true;
    }

    public boolean isRunning() {
        for (boolean r : running) if (r) return true;
        return false;
    }

    public int doneCount() {
        int n = 0;
        for (boolean d : done) if (d) n++;
        return n;
    }

    /** Bağımlılık kuralı: sıralı adım önceki hepsi bitince, topping adımı diğer tüm adımlar bitince açılır. */
    public boolean isStepUnlocked(int i) {
        Step s = product.steps.get(i);
        if (s.isTopping()) {
            for (int k = 0; k < done.length; k++) if (k != i && !done[k]) return false;
            return true;
        }
        if (s.sequential) {
            for (int k = 0; k < i; k++) if (!done[k]) return false;
        }
        return true;
    }

    /** Başlatılabilir: bitmemiş, çalışmıyor ve açık. */
    public boolean isStepOpen(int i) { return !done[i] && !running[i] && isStepUnlocked(i); }

    /** İlk açık adım indeksi, yoksa -1. */
    public int firstOpenStep() {
        for (int i = 0; i < done.length; i++) if (isStepOpen(i)) return i;
        return -1;
    }

    /** Müşteri isteklerine göre doğru malzeme kümesi (topping'i olmayan kalemde boş). */
    public Set<String> getTarget() { return Collections.unmodifiableSet(target); }

    /** Oyuncunun seçtiği malzemeler (havuz sırasında); hiçbir şey eklenmediyse ve bitirilmediyse null. */
    public Set<String> getChosen() {
        if (chosen != null) return Collections.unmodifiableSet(chosen);
        if (added.isEmpty()) return null;
        return Collections.unmodifiableSet(orderedKeys());
    }

    private Set<String> orderedKeys() {
        Set<String> out = new LinkedHashSet<>();
        for (String id : pool) if (added.containsKey(id)) out.add(id);
        return out;
    }

    /** Tek tek ekleme bitirilirken seçimi sabitler. */
    void finishChosen() { chosen = orderedKeys(); }

    void setChosen(Set<String> c) { chosen = new LinkedHashSet<>(c); }
}
