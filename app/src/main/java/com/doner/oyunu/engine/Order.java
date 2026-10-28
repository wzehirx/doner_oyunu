package com.doner.oyunu.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bir müşterinin siparişi; ürünlerin adımları sırayla düzleştirilmiş. */
public final class Order {
    public final int id;
    public final Customer customer;
    public final List<Product> items;
    public final List<Step> steps;
    int nextStep;
    boolean running;
    boolean ready;
    int position;

    Order(int id, Customer customer, List<Product> items) {
        this.id = id;
        this.customer = customer;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        List<Step> all = new ArrayList<>();
        for (Product p : items) all.addAll(p.steps);
        this.steps = Collections.unmodifiableList(all);
    }

    /** Sırada yapılacak adım; hazırsa null. */
    public Step nextStep() { return nextStep < steps.size() ? steps.get(nextStep) : null; }
    public int getNextStepIndex() { return nextStep; }
    public boolean isRunning() { return running; }
    public boolean isReady() { return ready; }
    /** Sipariş yuvası numarası (1 tabanlı; stereo pan için 1 sol ... 3 sağ). */
    public int getPosition() { return position; }

    public int price() {
        int t = 0;
        for (Product p : items) t += p.price;
        return t;
    }

    public float workSeconds() {
        float t = 0;
        for (Step s : steps) t += s.baseSeconds;
        return t;
    }

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
