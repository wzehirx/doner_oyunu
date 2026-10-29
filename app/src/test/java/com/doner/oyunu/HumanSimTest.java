package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * "Makul insan" botu: her eylem (sipariş seçme, istasyona dokunma, teslim) en az {@code reaction} saniye alır,
 * siparişleri geliş sırasıyla (FIFO) işler, bazen yanlış istasyona dokunur. UI'dan bağımsız motor simülasyonu.
 */
public class HumanSimTest {

    static final class Human {
        final float reaction; final double slipChance; final Random rnd; float cooldown;
        int emergency, selects, slips;
        Human(float reaction, double slip, long seed) { this.reaction = reaction; this.slipChance = slip; this.rnd = new Random(seed); }

        void playDay(GameEngine e) {
            float guard = 0;
            while (e.isRunning() && guard < 1000f) {
                if (cooldown <= 0f && act(e)) cooldown = reaction * (0.8f + 0.4f * rnd.nextFloat());
                e.tick(0.1f);
                cooldown -= 0.1f;
                guard += 0.1f;
            }
        }

        boolean act(GameEngine e) {
            for (Order o : e.getOrders()) if (o.isReady()) { e.deliver(o.id); return true; }
            Order sel = e.getSelectedOrder();
            if (sel == null || sel.isRunning() || sel.isReady()) {
                // FIFO: en eski bekleyen ve çalışmayan sipariş
                Order pick = null;
                for (Order o : e.getOrders()) if (!o.isRunning() && !o.isReady()) { pick = o; break; }
                if (pick == null || e.getFreeSlots() <= 0) return false;
                e.selectOrder(pick.id); selects++;
                return true;
            }
            if (e.getFreeSlots() <= 0) return false;
            Step ns = sel.nextStep();
            if (ns == null) return false;
            Station need = ns.station;
            if (rnd.nextDouble() < slipChance) {
                Station wrong = Station.values()[rnd.nextInt(Station.values().length)];
                if (wrong != need) { e.startStep(sel.id, wrong.id); slips++; return true; }
            }
            StartResult r = e.startStep(sel.id, need.id);
            if (r == StartResult.NEEDS_TOPPINGS) {
                int li = e.openToppingItem(sel.id);
                e.confirmToppings(sel.id, li, e.getToppingTarget(sel.id, li));
            }
            if (r == StartResult.OUT_OF_STOCK) {
                Ingredient m = e.missingIngredient(sel.id);
                if (m != null && e.emergencyBuy(m.id)) emergency++;
            }
            return true;
        }
    }

    static final String[] PRIORITY = {"cirak", "ekmek_isitici", "doner_makinesi", "buzdolabi", "tabela", "oto_dilimleyici", "klima", "muzik", "masa", "dekor"};

    static void shop(SaveData save, boolean upgrades) {
        if (upgrades) {
            boolean bought = true;
            while (bought) {
                bought = false;
                int reserve = Shop.recommendedCost(save);
                for (String id : PRIORITY) {
                    Upgrade u = Catalog.upgrade(id);
                    int lv = save.level(id);
                    int price = u.priceForNext(lv);
                    if (price < 0 || save.day < u.unlockDayForNext(lv)) continue;
                    if (save.money - price >= reserve && Shop.buyUpgrade(save, id) == Shop.BuyResult.OK) { bought = true; break; }
                    if (id.equals("cirak") && save.money - price < reserve) break; // çırak için biriktir
                }
            }
        }
        Shop.buyRecommended(save);
    }

    /** Gün 1, farklı hız ve hata oranlarıyla: acımasız olmamalı. */
    @Test public void dayOneIsFairForReasonableAndSlowHumans() {
        float[] reactions = {1.5f, 2.5f, 3.5f};
        for (float rx : reactions) {
            int fails = 0, lost = 0, delivered = 0, runs = 0; double minStars = 9, profit = 0;
            for (long seed = 1; seed <= 40; seed++) {
                SaveData s = SaveData.newGame();
                GameEngine e = new GameEngine(seed, s);
                Human h = new Human(rx, 0.05, seed);
                h.playDay(e);
                DaySummary d = e.getSummary();
                runs++;
                if (d.failed) fails++;
                lost += d.lost; delivered += d.delivered; profit += d.profit();
                minStars = Math.min(minStars, d.endStars);
            }
            System.out.println(String.format("HUMAN day1 reaction=%.1f fails=%d/%d delivered/run=%.1f lost/run=%.1f profit/run=%.0f minEndStars=%.2f",
                    rx, fails, runs, delivered / (double) runs, lost / (double) runs, profit / runs, minStars));
            if (rx <= 2.5f) {
                assertEquals("1. gün makul oyuncuyu (tepki " + rx + "s) asla kaybettirmemeli", 0, fails);
                assertTrue("1. gün kâr pozitif olmalı (tepki " + rx + ")", profit / runs > 0);
            }
            assertTrue("1. günde müşteri kaybı yarıyı geçmemeli (tepki " + rx + ")", lost <= delivered);
        }
    }

    /** 1..10. günler: yükseltme alan makul oyuncu (1.5 sn) kampanyayı geçer; her günün istatistiği yazdırılır. */
    @Test public void reasonableHumanPlaysDays1To10() {
        int totalFails = 0;
        List<String> rows = new ArrayList<>();
        int campaigns = 12;
        int[] failsPerDay = new int[11];
        for (long seed = 1; seed <= campaigns; seed++) {
            SaveData save = SaveData.newGame();
            for (int d = 1; d <= 10; d++) {
                int attempts = 0; boolean ok = false;
                while (!ok && attempts < 4) {
                    shop(save, true);
                    GameEngine e = new GameEngine(seed * 131 + d * 17 + attempts, save);
                    Human h = new Human(1.5f, 0.04, seed * 7 + d);
                    h.playDay(e);
                    ok = !e.getSummary().failed;
                    if (!ok) failsPerDay[d]++;
                    if (seed == 1 || !ok) rows.add("seed" + seed + " gün" + d + " deneme" + (attempts + 1) + " " + (ok ? "OK" : "KAYIP")
                            + " teslim=" + e.getSummary().delivered + " giden=" + e.getSummary().lost + " kâr=" + e.getSummary().profit()
                            + " yıldız=" + String.format("%.2f", e.getSummary().endStars) + " acil=" + h.emergency + " yanlış=" + e.getSummary().wrongTaps);
                    attempts++;
                }
                if (!ok) { totalFails++; break; }
            }
        }
        for (String r : rows) System.out.println("HUMAN " + r);
        System.out.println("HUMAN başarısız-deneme/gün: " + java.util.Arrays.toString(failsPerDay));
        assertEquals("1.5 sn tepkili makul oyuncu 1-10. günleri (en çok 4 denemeyle) geçmeli", 0, totalFails);
        int early = failsPerDay[1] + failsPerDay[2] + failsPerDay[3];
        assertEquals("İlk 3 günde hiç kayıp olmamalı", 0, early);
    }

    /** Hiç yükseltme almayan makul oyuncu da ilk 5 günü geçmeli. */
    @Test public void noUpgradeHumanSurvivesFirstFiveDays() {
        for (long seed = 1; seed <= 12; seed++) {
            SaveData save = SaveData.newGame();
            for (int d = 1; d <= 5; d++) {
                int attempts = 0; boolean ok = false;
                while (!ok && attempts < 3) {
                    shop(save, false);
                    GameEngine e = new GameEngine(seed * 31 + d + attempts * 1000, save);
                    new Human(1.5f, 0.04, seed + d).playDay(e);
                    ok = !e.getSummary().failed;
                    attempts++;
                }
                assertTrue("yükseltmesiz makul oyuncu gün " + d + " (seed " + seed + ") geçmeli", ok);
            }
        }
    }

    /** Ekonomi: 1. gün sonunda market malzemesi (önerilen) bütçeyi eritmemeli. */
    @Test public void economyAfterDayOneLeavesRoomToShop() {
        SaveData s = SaveData.newGame();
        GameEngine e = new GameEngine(5, s);
        new Human(1.5f, 0.04, 5).playDay(e);
        DaySummary d = e.getSummary();
        int rec = Shop.recommendedCost(s);
        System.out.println("HUMAN gün1 sonrası kasa=" + s.money + " kira=" + d.rent + " gün2 önerilen malzeme=" + rec + " acil=" + d.emergencySpend);
        assertTrue("Gün 2 önerilen malzemeyi karşılayabilmeli", s.money >= rec);
        assertTrue("1. günde acil alım bütçeyi bitirmemeli", d.emergencySpend < 500);
    }
}
