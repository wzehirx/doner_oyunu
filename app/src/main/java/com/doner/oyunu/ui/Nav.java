package com.doner.oyunu.ui;

import com.doner.oyunu.MainActivity;

/** v2.5: Fiyatlar / Banka / Faturalar ekranlarının "Geri" hedefi (hangi ekrandan açıldıysa oraya döner). */
final class Nav {
    private Nav() {}

    static final int MENU = 0, BRIEFING = 1, SHOP = 2, SUMMARY = 3;
    /** Faturalar ekranı: {@code BILLS + kendi geri hedefi} (ör. BILLS + SHOP). */
    static final int BILLS = 100;

    static boolean isBills(int where) { return where >= BILLS; }

    static Screen make(MainActivity a, int where) {
        if (isBills(where)) return new BillsScreen(a, where - BILLS);
        switch (where) {
            case BRIEFING: return new BriefingScreen(a);
            case SHOP: return new ShopScreen(a);
            default: return new MenuScreen(a);
        }
    }
}
