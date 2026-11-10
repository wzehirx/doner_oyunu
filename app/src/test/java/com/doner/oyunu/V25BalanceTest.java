package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

/**
 * v2.5 denge ölçümleri (rapor satırları "V25" ile yazdırılır): yeni ekonomiyi kullanan akıllı bot (fiyat, fatura, kredi)
 * her akışta 1–60; insan benzeri 1,5 sn bot da geçer; hiçbir şey yapmayan ≤5 gün; faturayı hiç ödemeyen bot 6. günden
 * kesinti yaşar ve 15. günde tahliyeyle takılır; fiyat stratejilerinin hiçbiri her durumda baskın değil.
 */
public class V25BalanceTest {
    static String row(String name, Campaign c, int f) {
        int days = 0; long sales = 0, del = 0;
        for (DaySummary d : c.results) if (!d.failed) { days++; sales += d.revenue + d.tips; del += d.delivered; }
        return String.format("%-22s ilkGeçilemeyen=%d başarısızDeneme=%d/%d kayıp/gün=%.2f teslim=%d satış=%d servet=%d yıldız=%.2f kredi=%d faturaÖdendi=%d maaş=%d dokunuş/sipariş=%.2f",
                name, f, c.failedAttempts(), c.attempts(), c.lost / (double) Math.max(1, c.attempts()), del, sales, c.netWorth(), c.save.stars,
                c.loansTaken, c.billsPaid, c.wageSpend, c.tapsPerOrder());
    }

    @Test public void smartBotPassesDays1To60InEveryModeAt08And15Seconds() {
        for (FlowMode m : FlowMode.values()) {
            Campaign c = new Campaign(m);
            int f = c.run(60, 21, 0.8f, true, 3, true, true);
            System.out.println("V25 bot0.8 " + row(m.displayName, c, f));
            assertEquals(m + " 0,8 sn 1-60", -1, f);
            assertTrue("faturalar ödendi", c.billsPaid > 30);
            Campaign slow = new Campaign(m);
            int g = slow.run(60, 22, 1.5f, true, 3, true, true);
            System.out.println("V25 bot1.5 " + row(m.displayName, slow, g));
            assertEquals(m + " 1,5 sn 1-60", -1, g);
            assertEquals(61, slow.save.day);
        }
    }

    @Test public void humanLikeSlowBotPassesDays1To60InEveryMode() {
        for (FlowMode m : FlowMode.values()) {
            Campaign c = new Campaign(m);
            c.humanLike = true;
            int f = c.run(60, 1, 1.5f, true, 3, true, true);
            System.out.println("V25 insan1.5 " + row(m.displayName, c, f));
            assertEquals(m + " insan benzeri 1,5 sn 1-60", -1, f);
        }
    }

    @Test public void untimedSmartBotPassesDays1To60InEveryMode() {
        for (FlowMode m : FlowMode.values()) {
            Campaign c = new Campaign(m, false);
            int f = c.run(60, 3, 0.8f, true, 3, true, true);
            System.out.println("V25 süresiz " + row(m.displayName, c, f));
            assertEquals(m + " süresiz 1-60", -1, f);
        }
    }

    @Test public void idleBotFailsWithinFiveDays() {
        for (long seed = 1; seed <= 4; seed++) {
            SaveData s = SaveData.newGame();
            int failedAt = -1;
            for (int d = 1; d <= 60 && failedAt < 0; d++) {
                GameEngine e = new GameEngine(seed * 100 + d, s);
                e.tick(2000f);
                if (e.getSummary().failed) failedAt = d;
            }
            assertTrue("seed " + seed + " başarısız gün: " + failedAt, failedAt > 0 && failedAt <= 5);
        }
    }

    /** Hiç fatura ödemeyen bot: 6. günden elektrik, 7'den doğalgaz, 8'den su kesintisi; 15. günde tahliye ile takılır. */
    @Test public void neverPayingBotSuffersOutagesAndGetsEvicted() {
        for (FlowMode m : new FlowMode[]{FlowMode.DENGELI, FlowMode.GERCEKCI}) {
            Campaign c = new Campaign(m);
            c.payBills = false;
            c.save.autoPayBills = false; // v2.6: otomatik ödeme de kapalı (hiç ödemeyen oyuncu)
            int f = c.run(30, 4, 0.8f, true, 3, true, true);
            System.out.println("V25 ödemeyen " + row(m.displayName, c, f));
            assertEquals(m + ": kira 9. günde son, 6 gün gecikince (15. gün) dükkân açılamaz", 15, f);
            DaySummary last = c.results.get(c.results.size() - 1);
            assertEquals("tahliye", last.failReason);
            assertTrue(c.save.bills.size() >= 8);
            // kesintiler 6. günden başlar: kademeler gün gün artar
            SaveData probe = c.save.copy();
            UtilityStatus u6 = UtilityStatus.of(probe, 6), u8 = UtilityStatus.of(probe, 8), u12 = UtilityStatus.of(probe, 12);
            assertTrue(u6.electric >= 1);
            assertTrue(u8.electric >= 2 && u8.gas >= 1 && u8.water >= 1);
            assertTrue(u12.ocakClosed() && u12.waterProductsOff() && u12.rent >= 2);
            assertTrue("kredi notu çöktü: " + c.save.creditScore, c.save.creditScore < 20);
        }
        // aynı tohumla ödeyen bot geçer
        Campaign ok = new Campaign(FlowMode.DENGELI);
        assertEquals(-1, ok.run(30, 4, 0.8f, true, 3, true, true));
    }

    /**
     * Fiyat stratejileri (Dengeli, 3 tohum): düşük (%80), referans, yüksek (%125), uçlar (%50, %150) ve akıllı. Hiçbiri
     * her ölçütte ve her durumda en iyi değil: güçlü süreli oyuncuda yüksek fiyat en çok servet ama daha düşük yıldız/teslim;
     * süresizde referans en çok servet; erken günlerde (12 gün) süresizde düşük fiyat en kârlı; uçlar kaybettirir.
     */
    @Test public void priceStrategiesGiveDifferentOutcomesAndNoneDominates() {
        int[] pcs = {50, 80, 100, 125, 150};
        long[] worthT = new long[5], worthU = new long[5], delT = new long[5], delU = new long[5];
        double[] starsT = new double[5];
        for (int i = 0; i < pcs.length; i++) {
            for (long seed = 1; seed <= 3; seed++) {
                Campaign t = new Campaign(FlowMode.DENGELI);
                t.pricePercent = pcs[i];
                t.run(60, seed, 0.8f, true, 3, true, true);
                worthT[i] += t.netWorth() / 3; starsT[i] += t.save.stars / 3;
                for (DaySummary d : t.results) if (!d.failed) delT[i] += d.delivered / 3;
                Campaign u = new Campaign(FlowMode.DENGELI, false);
                u.pricePercent = pcs[i];
                u.run(60, seed, 0.8f, true, 3, true, true);
                worthU[i] += u.netWorth() / 3;
                for (DaySummary d : u.results) if (!d.failed) delU[i] += d.delivered / 3;
            }
            System.out.println(String.format("V25 fiyat %%%d süreli: servet=%d yıldız=%.2f teslim=%d | süresiz: servet=%d teslim=%d",
                    pcs[i], worthT[i], starsT[i], delT[i], worthU[i], delU[i]));
        }
        int lo = 1, ref = 2, hi = 3;
        assertTrue("uçlar kaybettirir", worthT[0] < worthT[ref] && worthT[4] < worthT[hi] && worthU[0] < worthU[ref] && worthU[4] < worthU[ref]);
        assertTrue("güçlü süreli oyuncuda yüksek fiyat daha kârlı", worthT[hi] > worthT[ref]);
        assertTrue("ama daha az müşteriye hizmet", delT[hi] < delT[ref]);
        assertTrue("süresizde referans yüksekten kârlı", worthU[ref] > worthU[hi]);
        assertTrue("düşük fiyat en çok müşteriye hizmet (süreli)", delT[lo] >= delT[ref] && delT[lo] > delT[hi]);
        assertTrue("düşük fiyat en çok müşteriye hizmet (süresiz)", delU[lo] > delU[ref] && delU[lo] > delU[hi]);
        assertTrue("düşük fiyat yıldızı korur", starsT[lo] >= starsT[hi]);
        // akıllı bot: duruma göre fiyatlar, süreli güçlü oyuncuda referanstan kârlı
        Campaign smart = new Campaign(FlowMode.DENGELI);
        smart.smartPrice = true;
        assertEquals(-1, smart.run(60, 1, 0.8f, true, 3, true, true));
        Campaign refc = new Campaign(FlowMode.DENGELI);
        refc.run(60, 1, 0.8f, true, 3, true, true);
        System.out.println("V25 akıllı fiyat " + row("Dengeli", smart, -1) + " | referans servet=" + refc.netWorth());
        assertTrue(smart.netWorth() > refc.netWorth());
    }

    @Test public void botTakesALoanWhenItCannotAffordStockAndRepaysLater() {
        Campaign c = new Campaign(FlowMode.DENGELI);
        c.save.money = 300; // kötü başlangıç: stok parası yok
        c.save.inventory = new Inventory();
        c.save.day = 2;
        c.run(4, 5, 0.8f, true, 3, true, true);
        assertTrue("kredi alındı", c.loansTaken >= 1);
        c.run(20, 5, 0.8f, true, 3, true, true);
        assertTrue("kredi kapandı ya da azaldı", Bank.totalDebt(c.save) < c.loanTotal + 1000);
    }
}
