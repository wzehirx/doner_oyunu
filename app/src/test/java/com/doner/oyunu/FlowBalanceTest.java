package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

/**
 * v0.7 oyun akışı denge ölçümleri (rapor satırları "BAL" ile yazdırılır):
 * <ul>
 * <li>her akışta hazırlık yapan + yükseltmeli bot gün 1–40'ı geçer; Gerçekçi'de insan benzeri 1,5 sn bot da geçer;</li>
 * <li>Gerçekçi (v0.7) aynı insan benzeri botla v0.6 dengesinden ölçülerek daha adil: daha az başarısız deneme, daha az kaçan müşteri, daha az dokunuş;</li>
 * <li>Rahat ve Dengeli'de sipariş başına dokunuş, aynı siparişlerin v0.4 akışındaki eşdeğerinden fazla değil (v0.4 hissi);</li>
 * <li>yükseltmesiz bot her akışta 10. günden sonra ama 40. günden önce takılır (ilerleme yükseltme ister).</li>
 * </ul>
 */
public class FlowBalanceTest {
    static String row(String name, Campaign c, int failedDay) {
        int mistakes = 0, days = 0;
        for (DaySummary d : c.results) { mistakes += d.mistakes; if (!d.failed) days++; }
        return String.format("%-9s ilkGeçilemeyen=%d başarısızDeneme=%d/%d kayıp/gün=%.2f hata/gün=%.2f dokunuş/sipariş=%.2f v0.4eşdeğeri=%.2f para=%d",
                name, failedDay, c.failedAttempts(), c.attempts(), c.lost / (double) c.attempts(), mistakes / (double) Math.max(1, days),
                c.tapsPerOrder(), c.v04TapsPerOrder(), c.save.money);
    }

    private static Campaign human(FlowMode m, boolean v06, long seed, int days) {
        Campaign c = new Campaign(m);
        c.v06Balance = v06;
        c.humanLike = true;
        c.firstUnpassedDay = c.run(days, seed, 1.5f, true, 3, true, true);
        return c;
    }

    @Test public void prepAndUpgradeBotPassesDays1To40InEveryMode() {
        for (FlowMode m : FlowMode.values()) {
            Campaign c = new Campaign(m);
            int f = c.run(40, 21, 0.8f, true, 3, true, true);
            System.out.println("BAL bot0.8 " + row(m.displayName, c, f));
            assertEquals(m + " 0,8 sn hazırlıklı yükseltmeli bot 1-40 geçmeli", -1, f);
            Campaign slow = new Campaign(m);
            int g = slow.run(40, 22, 1.5f, true, 3, true, true);
            System.out.println("BAL bot1.5 " + row(m.displayName, slow, g));
            assertEquals(m + " 1,5 sn hazırlıklı yükseltmeli bot 1-40 geçmeli", -1, g);
        }
    }

    @Test public void humanLikeBotPassesGercekciDays1To40AndItIsFairerThanV06() {
        for (long seed = 1; seed <= 4; seed++) {
            Campaign old = human(FlowMode.GERCEKCI, true, seed, 40);
            Campaign now = human(FlowMode.GERCEKCI, false, seed, 40);
            System.out.println("BAL insan1.5 seed" + seed + " " + row("v0.6", old, old.firstUnpassedDay));
            System.out.println("BAL insan1.5 seed" + seed + " " + row("Gerçekçi", now, now.firstUnpassedDay));
            assertEquals("Gerçekçi: insan benzeri 1,5 sn bot (seed " + seed + ") 1-40 geçmeli", -1, now.firstUnpassedDay);
            assertTrue("v0.6 bu botla 40 günü geçemiyordu (karşılaştırma tabanı): seed " + seed, old.firstUnpassedDay > 0);
            // aynı ilk 20 günde (ikisi de oynadı) ölç: daha az kaçan müşteri ve daha az dokunuş
            Campaign old20 = human(FlowMode.GERCEKCI, true, seed, 20), now20 = human(FlowMode.GERCEKCI, false, seed, 20);
            double lostOld = old20.lost / (double) old20.attempts(), lostNow = now20.lost / (double) now20.attempts();
            System.out.println(String.format("BAL insan1.5 seed%d gün1-20 kayıp/gün v0.6=%.2f Gerçekçi=%.2f dokunuş/sipariş v0.6=%.2f Gerçekçi=%.2f başarısızDeneme v0.6=%d Gerçekçi=%d",
                    seed, lostOld, lostNow, old20.tapsPerOrder(), now20.tapsPerOrder(), old20.failedAttempts(), now20.failedAttempts()));
            assertTrue("Gerçekçi gün 1-20'de v0.6'dan az müşteri kaçırır: " + lostNow + " < " + lostOld, lostNow < lostOld);
            assertTrue("Gerçekçi başarısız deneme v0.6'dan fazla değil", now20.failedAttempts() <= old20.failedAttempts());
            assertTrue("Gerçekçi dokunuş/sipariş v0.6'dan fazla değil", now20.tapsPerOrder() <= old20.tapsPerOrder() + 0.05);
            assertTrue("40 gün boyunca Gerçekçi v0.6'dan az başarısız deneme", now.failedAttempts() < old.failedAttempts());
        }
    }

    @Test public void humanLikeBotPassesRahatAndDengeliToo() {
        for (FlowMode m : new FlowMode[]{FlowMode.RAHAT, FlowMode.DENGELI}) {
            for (long seed = 1; seed <= 2; seed++) {
                Campaign c = human(m, false, seed, 40);
                System.out.println("BAL insan1.5 seed" + seed + " " + row(m.displayName, c, c.firstUnpassedDay));
                assertEquals(m + " insan benzeri bot 1-40 geçmeli (seed " + seed + ")", -1, c.firstUnpassedDay);
                assertEquals(m + " hatasız", 0, mistakes(c));
            }
        }
    }

    private static int mistakes(Campaign c) {
        int n = 0;
        for (DaySummary d : c.results) n += d.mistakes;
        return n;
    }

    /** "v0.4 hissi": sipariş başına dokunuş. Rahat < Dengeli < Gerçekçi; Rahat ve Dengeli v0.4 eşdeğerini aşmaz. */
    @Test public void tapsPerOrderShowRahatAndDengeliAreCloseToV04() {
        double[] taps = new double[3], v04 = new double[3];
        for (FlowMode m : FlowMode.values()) {
            Campaign c = human(m, false, 3, 40);
            taps[m.ordinal()] = c.tapsPerOrder();
            v04[m.ordinal()] = c.v04TapsPerOrder();
            // ilk 7 gün (Gerçekçi'de çırak seviye 2 kısayolu henüz yok): saf el emeği
            Campaign early = human(m, false, 3, 7);
            System.out.println(String.format("BAL dokunuş %-9s 40 gün: %.2f (v0.4 eşdeğeri %.2f) | gün 1-7: %.2f (v0.4 eşdeğeri %.2f)",
                    m.displayName, c.tapsPerOrder(), c.v04TapsPerOrder(), early.tapsPerOrder(), early.v04TapsPerOrder()));
        }
        Campaign v06 = human(FlowMode.GERCEKCI, true, 3, 7);
        System.out.println(String.format("BAL dokunuş v0.6      gün 1-7: %.2f (v0.4 eşdeğeri %.2f)", v06.tapsPerOrder(), v06.v04TapsPerOrder()));
        int r = FlowMode.RAHAT.ordinal(), d = FlowMode.DENGELI.ordinal(), g = FlowMode.GERCEKCI.ordinal();
        assertTrue("Rahat < Dengeli", taps[r] < taps[d]);
        assertTrue("Dengeli < Gerçekçi", taps[d] < taps[g]);
        assertTrue("Rahat v0.4'ten az dokunuş: " + taps[r] + " <= " + v04[r], taps[r] <= v04[r]);
        assertTrue("Dengeli v0.4 kadar ya da az dokunuş: " + taps[d] + " <= " + v04[d], taps[d] <= v04[d]);
    }

    @Test public void withoutUpgradesEveryModeEventuallyFails() {
        for (FlowMode m : FlowMode.values()) {
            for (boolean prep : new boolean[]{false, true}) {
                Campaign c = new Campaign(m);
                int f = c.run(40, 1, 0.8f, false, 3, prep, true);
                System.out.println("BAL yükseltmesiz prep=" + prep + " " + row(m.displayName, c, f));
                assertTrue(m + " yükseltmesiz bot 40. güne kadar takılmalı (prep=" + prep + ")", f > 0);
                assertTrue(m + " ilk günler rahat olmalı (prep=" + prep + ", gün " + f + ")", f > 10);
            }
        }
    }

    @Test public void carelessBotsMakeMistakesInDengeliAndGercekciButNotInRahat() {
        for (FlowMode m : FlowMode.values()) {
            Campaign c = new Campaign(m);
            c.run(10, 2, 0.8f, false, 3, true, false);
            int n = mistakes(c);
            System.out.println("BAL dikkatsiz " + m.displayName + " hata=" + n);
            if (m == FlowMode.RAHAT) assertEquals("Rahat: istek ve boyu sistem uygular", 0, n);
            else assertTrue(m + ": isteği yok sayan bot hata yapar", n > 0);
        }
    }
}
