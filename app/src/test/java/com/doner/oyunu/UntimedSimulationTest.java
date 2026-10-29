package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

/**
 * v0.8 süresiz mod bot simülasyonları (rapor satırları "UNT" ile). Her simülasyon sanal süreyle ilerler ve bir koruma
 * sınırı vardır (TestBot: 1000 sn sanal gün; "hiçbir şey yapma" botu: 600 sn sanal) — sonsuz döngü olamaz.
 */
public class UntimedSimulationTest {
    private static Campaign untimed(FlowMode m, boolean human) {
        Campaign c = new Campaign(m, false);
        c.humanLike = human;
        return c;
    }

    private static String row(String name, Campaign c, int f) {
        int days = 0;
        long delivered = c.delivered;
        double tips = 0, rev = 0;
        for (DaySummary d : c.results) { if (!d.failed) days++; tips += d.tips; rev += d.revenue; }
        return String.format("%-22s ilkGeçilemeyen=%d başarısızDeneme=%d/%d kayıp=%d teslim=%d bahşiş/satış=%.3f para=%d gün=%d",
                name, f, c.failedAttempts(), c.attempts(), c.lost, delivered, tips / Math.max(1, rev), c.save.money, days);
    }

    @Test public void untimedBotPassesDays1To40InEveryFlowMode() {
        for (FlowMode m : FlowMode.values()) {
            for (long seed = 1; seed <= 2; seed++) {
                Campaign c = untimed(m, false);
                int f = c.run(40, seed, 0.8f, true, 2, true, true);
                System.out.println("UNT bot0.8 seed" + seed + " " + row(m.displayName, c, f));
                assertEquals(m + " süresiz: 1-40 geçilmeli (seed " + seed + ")", -1, f);
                assertEquals("müşteri kaybı olmaz", 0, c.lost);
                assertEquals("başarısız deneme olmaz", 0, c.failedAttempts());
            }
        }
    }

    @Test public void untimedHumanLikeSlowBotPasses1To40() {
        for (FlowMode m : FlowMode.values()) {
            Campaign c = untimed(m, true);
            int f = c.run(40, 3, 1.5f, true, 2, true, true);
            System.out.println("UNT insan1.5 " + row(m.displayName, c, f));
            assertEquals(m + " süresiz insan benzeri 1,5 sn bot 1-40 geçmeli", -1, f);
            assertEquals(0, c.lost);
        }
    }

    @Test public void untimedBotWithoutPrepOrUpgradesPassesToo() {
        Campaign c = untimed(FlowMode.GERCEKCI, false);
        int f = c.run(40, 4, 1.5f, false, 2, false, true);
        System.out.println("UNT yükseltmesiz hazırlıksız " + row("Gerçekçi", c, f));
        assertEquals("süresiz modda süre baskısı olmadığı için yükseltmesiz bot da geçer", -1, f);
    }

    @Test public void untimedIsSomewhatLessProfitableThanTimed() {
        Campaign t = new Campaign(FlowMode.DENGELI, true);
        Campaign u = new Campaign(FlowMode.DENGELI, false);
        int ft = t.run(15, 5, 0.8f, true, 3, true, true);
        int fu = u.run(15, 5, 0.8f, true, 3, true, true);
        System.out.println("UNT ekonomi süreli " + row("Dengeli süreli", t, ft));
        System.out.println("UNT ekonomi süresiz " + row("Dengeli süresiz", u, fu));
        assertEquals(-1, ft);
        assertEquals(-1, fu);
        double tipT = 0, revT = 0, tipU = 0, revU = 0;
        for (DaySummary d : t.results) { tipT += d.tips; revT += d.revenue; }
        for (DaySummary d : u.results) { tipU += d.tips; revU += d.revenue; }
        assertTrue("bahşiş oranı süresizde süreliden düşük", tipU / revU < tipT / revT);
        assertTrue("ama bahşiş yine verilir", tipU > 0);
        assertTrue("kazanç süreliden biraz düşük: " + u.save.money + " < " + t.save.money, u.save.money < t.save.money);
        assertTrue("ama çok düşük değil (en az %70): " + u.save.money + " vs " + t.save.money, u.save.money >= 0.7 * t.save.money);
    }

    @Test public void doNothingBotNeverFinishesAnUntimedDay() {
        for (int d = 1; d <= 40; d++) {
            SaveData s = SaveData.newGame();
            s.day = d;
            s.timePressure = false;
            s.money = 90000;
            Shop.buyRecommended(s);
            GameEngine e = new GameEngine(100 + d, s);
            e.startDay();
            final int[] left = {0};
            e.addListener(new GameListenerAdapter() {
                @Override public void onCustomerLeft(Order o) { left[0]++; }
            });
            for (int i = 0; i < 6000; i++) e.tick(0.1f); // 10 dk sanal
            assertEquals("gün " + d + ": hâlâ sürüyor", GameEngine.State.RUNNING, e.getState());
            assertEquals("gün " + d + ": bekleyen sipariş üst sınırda", e.getConfig().baseQueue + e.getUpgrades().queueBonus(), e.getOrders().size());
            assertEquals("gün " + d + ": kimse gitmedi", 0, left[0]);
            assertTrue(e.getCustomersArrived() < e.getCustomerQuota());
            assertEquals(3.0, e.getStars(), 1e-9);
        }
    }
}
