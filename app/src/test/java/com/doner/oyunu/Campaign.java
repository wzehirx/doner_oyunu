package com.doner.oyunu;

import com.doner.oyunu.engine.Catalog;
import com.doner.oyunu.engine.DaySummary;
import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.SaveData;
import com.doner.oyunu.engine.Shop;
import com.doner.oyunu.engine.Upgrade;

import java.util.ArrayList;
import java.util.List;

/** 1..N günlük bot kampanyası; gün aralarında market alışverişi yapar. */
public final class Campaign {
    public static final String[] PRIORITY = {
            "cirak", "ekmek_isitici", "doner_makinesi", "buzdolabi", "tabela", "oto_dilimleyici",
            "klima", "muzik", "masa", "dekor"};

    public final SaveData save = SaveData.newGame();
    public final List<DaySummary> results = new ArrayList<>();
    public final List<Integer> attemptsPerDay = new ArrayList<>();
    public int firstUnpassedDay = -1;

    /** @return geçilemeyen ilk gün (hepsi geçildiyse -1) */
    public int run(int lastDay, long seed, float reaction, boolean buyUpgrades, int maxAttempts) {
        return run(lastDay, seed, reaction, buyUpgrades, maxAttempts, false, true);
    }

    /** prep: gün öncesi + gün içi hazırlık yapan bot; careful: malzeme seçiminde istekleri uygular. */
    public int run(int lastDay, long seed, float reaction, boolean buyUpgrades, int maxAttempts, boolean prep, boolean careful) {
        for (int d = 1; d <= lastDay; d++) {
            boolean passed = false;
            int attempt = 0;
            while (attempt < maxAttempts && !passed) {
                if (buyUpgrades) shop();
                else Shop.buyRecommended(save);
                GameEngine e = new GameEngine(seed * 1000 + d * 7 + attempt, save);
                new TestBot(reaction, prep, careful).playDay(e);
                results.add(e.getSummary());
                passed = !e.getSummary().failed;
                attempt++;
            }
            attemptsPerDay.add(attempt);
            if (!passed) {
                firstUnpassedDay = d;
                return d;
            }
        }
        return -1;
    }

    private void shop() {
        boolean bought = true;
        while (bought) {
            bought = false;
            int reserve = Shop.recommendedCost(save);
            for (String id : PRIORITY) {
                Upgrade u = Catalog.upgrade(id);
                int lv = save.level(id);
                int price = u.priceForNext(lv);
                if (price < 0 || save.day < u.unlockDayForNext(lv)) continue;
                if (save.money - price >= reserve) {
                    if (Shop.buyUpgrade(save, id) == Shop.BuyResult.OK) { bought = true; break; }
                } else if (id.equals("cirak")) {
                    break; // çırak için biriktir (akıllı oyuncu önce çırağı alır)
                }
            }
        }
        Shop.buyRecommended(save);
    }
}
