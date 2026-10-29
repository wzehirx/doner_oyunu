package com.doner.oyunu.engine;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Siparişin bir kalemi (bir ürün) ve kendi adım durumu. */
public final class OrderLine {
    public final Product product;
    public final int index;
    private final boolean[] done;
    private final boolean[] running;
    private final Set<String> target;
    private Set<String> chosen;

    OrderLine(Product product, int index, Set<String> target) {
        this.product = product;
        this.index = index;
        this.done = new boolean[product.steps.size()];
        this.running = new boolean[product.steps.size()];
        this.target = target;
    }

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

    /** Oyuncunun seçtiği malzemeler; topping adımı henüz başlamadıysa null. */
    public Set<String> getChosen() { return chosen == null ? null : Collections.unmodifiableSet(chosen); }

    void setChosen(Set<String> c) { chosen = new LinkedHashSet<>(c); }
}
