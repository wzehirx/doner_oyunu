package com.doner.oyunu;

import com.doner.oyunu.engine.Catalog;
import com.doner.oyunu.engine.DaySummary;
import com.doner.oyunu.engine.FlowMode;
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
    /** v0.6 denge kuralları (karşılaştırma ölçümü için; bkz. GameEngine(seed, save, true)). */
    public boolean v06Balance;
    /**
     * İnsan benzeri bot (panel açmak ayrı tepki, tepki ±%20) ve insan gibi alışveriş: önce mutfak aletleri, çırak ve klima;
     * müşteri sayısını artıran restoran öğelerini (tabela, müzik, dekor, masa) yalnızca önceki gün en çok 1 müşteri
     * kaçırdıysa alır (yetişemeyen oyuncu dükkânı kalabalıklaştırmaz).
     */
    public boolean humanLike;
    static final String[] CROWD = {"tabela", "muzik", "dekor", "masa"};
    /** Toplam dokunuş / teslim (TestBot ölçütü), kayıp müşteri ve teslim sayıları (tüm denemeler). */
    public long taps, delivered, lost, v04Taps;

    /** Varsayılan: Gerçekçi akış (v0.6 davranışı; eski denge testleri bu akışı ölçer). */
    public Campaign() { this(FlowMode.GERCEKCI); }

    public Campaign(FlowMode flow) {
        save.flowMode = flow;
        save.flowChosen = true;
    }
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
                GameEngine e = new GameEngine(seed * 1000 + d * 7 + attempt, save, v06Balance);
                TestBot bot = new TestBot(reaction, prep, careful);
                if (humanLike) bot.humanLike(seed * 31 + d * 7 + attempt);
                bot.playDay(e);
                taps += bot.taps;
                v04Taps += bot.v04Taps;
                delivered += bot.delivered;
                lost += e.getSummary().lost;
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

    private int lastLost() { return results.isEmpty() ? 0 : results.get(results.size() - 1).lost; }

    /** Ölçüt: teslim edilen sipariş başına oyuncu dokunuşu. */
    public double tapsPerOrder() { return delivered == 0 ? 0 : taps / (double) delivered; }

    /** Aynı siparişlerin v0.4 akışındaki dokunuş eşdeğeri / teslim. */
    public double v04TapsPerOrder() { return delivered == 0 ? 0 : v04Taps / (double) delivered; }

    /** Toplam deneme sayısı (geçilen + başarısız). */
    public int attempts() { int n = 0; for (int a : attemptsPerDay) n += a; return n; }

    /** Başarısız deneme sayısı. */
    public int failedAttempts() { int n = 0; for (DaySummary d : results) if (d.failed) n++; return n; }

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
                if (humanLike && java.util.Arrays.asList(CROWD).contains(id) && lastLost() > 1) continue;
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
