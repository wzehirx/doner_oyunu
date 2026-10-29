package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

/** v0.7 oyun akışı denge ölçümü (geçici keşif). */
public class FlowBalanceTest {
    static String row(String name, Campaign c, int failedDay) {
        int mistakes = 0, days = 0;
        for (DaySummary d : c.results) { mistakes += d.mistakes; if (!d.failed) days++; }
        return String.format("%-22s ilkGeçilemeyen=%d başarısızDeneme=%d/%d kayıp/gün=%.2f hata/gün=%.2f dokunuş/sipariş=%.2f v04eşdeğeri=%.2f",
                name, failedDay, c.failedAttempts(), c.attempts(), c.lost / (double) c.attempts(), mistakes / (double) Math.max(1, days),
                c.tapsPerOrder(), c.v04TapsPerOrder()) + " para=" + c.save.money;
    }

    @Test public void explore() {
        for (long seed = 1; seed <= 1; seed++) {
            for (int k = 1; k < 2; k++) {
                FlowMode m = k == 0 ? FlowMode.GERCEKCI : FlowMode.values()[3 - k];
                Campaign c = new Campaign(m);
                c.v06Balance = k == 0;
                c.humanLike = true;
                int f = c.run(40, seed, 1.5f, true, 3, true, true);
                System.out.println("BAL seed" + seed + " " + row(k == 0 ? "v0.6" : m.displayName, c, f));
                if (k == 1 && seed == 1) for (DaySummary d : c.results) if (d.day >= 26)
                    System.out.println("BALD gün" + d.day + " fail=" + d.failed + " teslim=" + d.delivered + " kayıp=" + d.lost + " yıldız=" + String.format("%.2f", d.endStars) + " acil=" + d.emergencySpend);
            }
        }
    }
}
