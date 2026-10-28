package com.doner.oyunu.engine;

/** Çalışan bir adım (yuvada). */
public final class ActiveStep {
    public final Order order;
    public final Step step;
    public final float total;
    public final int slot;
    float remaining;

    ActiveStep(Order order, Step step, float total, int slot) {
        this.order = order;
        this.step = step;
        this.total = total;
        this.remaining = total;
        this.slot = slot;
    }

    public float getRemaining() { return remaining; }
    public float progress() { return total <= 0 ? 1f : 1f - remaining / total; }
}
