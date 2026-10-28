package com.doner.oyunu.engine;

/** Sıradaki müşteri. */
public final class Customer {
    public final String name;
    public final CustomerType type;
    public final float patienceMax;
    float patienceLeft;
    int warnLevel;

    public Customer(String name, CustomerType type, float patienceMax) {
        this.name = name;
        this.type = type;
        this.patienceMax = patienceMax;
        this.patienceLeft = patienceMax;
    }

    public float getPatienceLeft() { return patienceLeft; }

    public float patienceFraction() { return Math.max(0f, patienceLeft / patienceMax); }

    /** 0 normal, 1 azalıyor, 2 kritik. */
    public int getWarnLevel() { return warnLevel; }
}
