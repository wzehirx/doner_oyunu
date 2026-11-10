package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** v2.5: banka (limit, faiz, vade, taksit, gecikme, erken kapatma, kira kredisi) ve fiyat/talep (design_v25 §2, §5). */
public class V25BankPricingTest {
    static SaveData day(int d) { return ResourceModelTest.day(d, FlowMode.DENGELI); }

    // ------------------------------------------------------------ banka
    @Test public void limitDependsOnDayStarsAndCreditScore() {
        SaveData s = SaveData.newGame();
        assertEquals(60, s.creditScore);
        assertEquals("gün 1, 3★, not 60", 2200, Bank.totalLimit(s)); // (2000 + 500) × 0,9 × 1 = 2250 -> 2200
        s.day = 30;
        s.stars = 5.0;
        assertEquals(18700, Bank.totalLimit(s));
        s.creditScore = 30;
        assertEquals("not yarıya inince limit yarıya", 9300, Bank.totalLimit(s));
        s.creditScore = 100;
        assertTrue(Bank.totalLimit(s) > 18700);
        assertEquals(Bank.totalLimit(s), Bank.available(s));
        assertEquals(0.006, Bank.dailyRate(85), 1e-12);
        assertEquals(0.008, Bank.dailyRate(60), 1e-12);
        assertEquals(0.012, Bank.dailyRate(45), 1e-12);
        assertEquals(0.018, Bank.dailyRate(25), 1e-12);
        assertEquals(0.025, Bank.dailyRate(5), 1e-12);
    }

    @Test public void quoteTakeRepayAndValidation() {
        SaveData s = SaveData.newGame();
        s.day = 10;
        Bank.Quote q = Bank.quote(s, 3000, 10);
        assertEquals(240, q.interest); // 3000 × 0,008 × 10
        assertEquals(3240, q.total);
        assertEquals(324, q.installment);
        assertNull(Bank.quote(s, 450, 10));
        assertNull(Bank.quote(s, 3050, 10));
        assertNull(Bank.quote(s, 3000, 7));
        assertEquals(Bank.Result.INVALID_AMOUNT, Bank.takeLoan(s, 450, 10));
        assertEquals(Bank.Result.INVALID_TERM, Bank.takeLoan(s, 1000, 3));
        assertEquals(Bank.Result.OVER_LIMIT, Bank.takeLoan(s, 100000, 10));
        int money = s.money;
        assertEquals(Bank.Result.OK, Bank.takeLoan(s, 3000, 10));
        assertEquals(money + 3000, s.money);
        Loan l = s.loans.get(0);
        assertEquals(3240, l.getRemaining());
        assertEquals(10, l.getInstallmentsLeft());
        assertEquals(Bank.totalLimit(s) - 3000, Bank.available(s));
        // erken kapatma: kalan anapara + kalan faizin %25'i
        assertEquals(3000 + 60, l.payoffAmount());
        int credit = s.creditScore;
        assertEquals(Bank.Result.OK, Bank.repay(s, l.id));
        assertEquals(money - 60, s.money);
        assertTrue(s.loans.isEmpty());
        assertEquals("v2.6: aynı gün al-kapat kredi notu ödülü vermez", credit, s.creditScore);
        assertTrue(Narrator.quoteText(q).equals("3000 lira, 10 gün: faiz 240 lira, günlük taksit 324 lira, toplam 3240 lira."));
        assertTrue(Narrator.bankText(s).startsWith("Kredi notu 60. Limit "));
    }

    @Test public void installmentsAreCollectedAtDayEndAndMissedOnesCostFeesAndCredit() {
        SaveData s = day(10);
        assertEquals(Bank.Result.OK, Bank.takeLoan(s, 1000, 5));
        Loan l = s.loans.get(0);
        int inst = l.installment(); // (1000 + 40) / 5 = 208
        assertEquals(208, inst);
        int money = s.money;
        GameEngine e = V25FinanceTest.finishEmptyDay(s);
        assertEquals(inst, e.getSummary().installmentsDue);
        assertEquals(inst, e.getSummary().installmentsPaid);
        assertEquals(money - inst, s.money);
        assertEquals(1040 - inst, l.getRemaining());
        assertEquals(4, l.getInstallmentsLeft());
        // para yok: taksit kaçar, %5 bedel, not −5
        s.money = 0;
        int credit = s.creditScore;
        GameEngine f = V25FinanceTest.finishEmptyDay(s);
        assertEquals(1, f.getSummary().installmentsMissed);
        assertEquals(inst, l.getOverdue());
        assertEquals(Math.round(inst * 0.05), l.getFees());
        assertTrue(s.creditScore <= credit - 5);
        assertTrue(new Narrator(f).dayFinished(f.getSummary()).contains("Kredi taksiti ödenemedi"));
        assertTrue(Finance.notices(s, s.day).toString().contains("Kredi taksiti gecikti"));
        // art arda 3 kaçan taksit: yeni kredi yok
        V25FinanceTest.finishEmptyDay(s);
        V25FinanceTest.finishEmptyDay(s);
        assertTrue(Bank.blocked(s));
        assertEquals(0, Bank.available(s));
        assertEquals(Bank.Result.BLOCKED, Bank.takeLoan(s, 500, 5));
        // para gelince gecikmiş borç otomatik alınır
        s.money = 100000;
        V25FinanceTest.finishEmptyDay(s);
        assertEquals(0, l.getOverdue());
        assertEquals(0, l.getMissedStreak());
        assertFalse(Bank.blocked(s));
    }

    @Test public void loanClosesAfterLastInstallmentAndAtMostThreeLoans() {
        SaveData s = day(10);
        assertEquals(Bank.Result.OK, Bank.takeLoan(s, 500, 5));
        for (int i = 0; i < 5; i++) V25FinanceTest.finishEmptyDay(s);
        assertTrue("vade bitince kredi kapanır", s.loans.isEmpty());
        s.stars = 5;
        s.creditScore = 90;
        for (int i = 0; i < 3; i++) assertEquals(Bank.Result.OK, Bank.takeLoan(s, 500, 20));
        assertEquals(Bank.Result.TOO_MANY_LOANS, Bank.takeLoan(s, 500, 20));
        // kayıt gidiş-dönüş
        SaveData r = SaveData.parse(s.serialize());
        assertEquals(3, r.loans.size());
        assertEquals(s.serialize(), r.serialize());
    }

    @Test public void rentLoanIsGrantedEvenOverTheLimitSoEvictionCannotSoftLock() {
        SaveData s = day(15);
        s.money = 0;
        s.creditScore = 0;
        s.stars = 1.0;
        Bill rent = Finance.issue(s, BillType.KIRA, 9000, 7);
        Bill elec = Finance.issue(s, BillType.ELEKTRIK, 9000, 7);
        assertEquals(Bank.Result.OVER_LIMIT, Finance.payWithLoan(s, elec.id, 10));
        assertEquals(Bank.Result.OK, Finance.payWithLoan(s, rent.id, 10));
        assertNull(Finance.bill(s, rent.id));
        Loan l = s.loans.get(0);
        assertEquals("en yüksek faiz", (int) Math.round(l.principal * Bank.WORST_RATE * 10), l.interest);
        assertTrue(new GameEngine(1, s).startDay());
    }

    // ------------------------------------------------------------ fiyat ayarı
    @Test public void pricePercentClampsStepsAndPersists() {
        SaveData s = SaveData.newGame();
        assertTrue(Pricing.allDefault(s));
        assertEquals(100, Pricing.percent(s, "ekmek_arasi"));
        assertEquals(240, Pricing.priceOf(s, Catalog.product("ekmek_arasi")));
        assertTrue(Pricing.setPercent(s, "ekmek_arasi", 112));
        // v2.7: her tam yüzde geçerli (eskiden %5'e yuvarlanırdı); −/+ düğmeleri bir sonraki %5 basamağına gider
        assertEquals("tam yüzde korunur", 112, Pricing.percent(s, "ekmek_arasi"));
        assertEquals(269, Pricing.priceOf(s, Catalog.product("ekmek_arasi")));
        assertEquals("112 -> 115", 115, Pricing.raise(s, "ekmek_arasi"));
        assertEquals("115 -> 120", 120, Pricing.raise(s, "ekmek_arasi"));
        Pricing.setPercent(s, "ekmek_arasi", 112);
        assertEquals("112 -> 110", 110, Pricing.lower(s, "ekmek_arasi"));
        assertEquals("110 -> 105", 105, Pricing.lower(s, "ekmek_arasi"));
        Pricing.setPercent(s, "ekmek_arasi", 149);
        assertEquals(150, Pricing.raise(s, "ekmek_arasi"));
        assertEquals(150, Pricing.raise(s, "ekmek_arasi"));
        Pricing.setPercent(s, "ekmek_arasi", 51);
        assertEquals(50, Pricing.lower(s, "ekmek_arasi"));
        Pricing.setPercent(s, "ekmek_arasi", 115);
        Pricing.setPercent(s, "ayran", 999);
        assertEquals(150, Pricing.percent(s, "ayran"));
        Pricing.setPercent(s, "ayran", 10);
        assertEquals(50, Pricing.percent(s, "ayran"));
        assertEquals(50, Pricing.lower(s, "ayran"));
        assertFalse(Pricing.setPercent(s, "yok", 120));
        SaveData r = SaveData.parse(s.serialize());
        assertEquals(115, Pricing.percent(r, "ekmek_arasi"));
        Pricing.setPercent(s, "ekmek_arasi", 100);
        assertFalse("referansa dönen kayıttan çıkar", s.prices.containsKey("ekmek_arasi"));
        assertEquals("Ekmek arası döner: 240 lira, referans fiyat.", Narrator.priceText(s, Catalog.product("ekmek_arasi")));
        assertEquals("Ayran: 23 lira, yüzde 50.", Narrator.priceText(s, Catalog.product("ayran")));
    }

    @Test public void orderPriceUsesThePlayersPriceFixedAtOrderTime() {
        SaveData s = day(4);
        Pricing.setPercent(s, "ekmek_arasi", 120);
        GameEngine e = ResourceModelTest.engine(s);
        Order o = e.spawnOrder(java.util.Arrays.asList("ekmek_arasi", "ayran"), Collections.<String>emptyList(),
                java.util.Arrays.asList(PortionSize.HALF, PortionSize.FULL));
        assertEquals(144, o.getLine(0).referencePrice());
        assertEquals("yarım ekmek × %120", 173, o.getLine(0).price());
        assertEquals(45, o.getLine(1).price());
        assertEquals(173 + 45, o.price());
        assertEquals(189, o.referencePrice());
        Pricing.setPercent(s, "ekmek_arasi", 80);
        assertEquals("sipariş anında sabitlendi", 173 + 45, o.price());
    }

    @Test public void demandFallsWithPriceFasterForSensitiveCustomers() {
        assertEquals(1.0, Pricing.demand(1.0, 1.8), 0);
        assertTrue(Pricing.demand(1.2, 1.0) < 1.0);
        assertTrue(Pricing.demand(0.8, 1.0) > 1.0);
        assertTrue(Pricing.demand(1.3, 1.8) < Pricing.demand(1.3, 0.4));
        assertTrue("çok pahalıda hızla düşer", Pricing.demand(1.5, 1.0) < 0.5 * Pricing.demand(1.25, 1.0));
        assertEquals(Pricing.MIN_DEMAND, Pricing.demand(1.5, 1.8), 1e-9);
        assertEquals(Pricing.MAX_DEMAND, Pricing.demand(0.5, 1.8), 1e-9);
        CustomerType student = null, tourist = null, gurme = null;
        for (CustomerType t : Catalog.customerTypes()) {
            if (t.id.equals("ogrenci")) student = t;
            if (t.id.equals("turist")) tourist = t;
            if (t.id.equals("gurme")) gurme = t;
        }
        assertTrue(student.priceSensitivity > tourist.priceSensitivity && tourist.priceSensitivity > gurme.priceSensitivity);
        assertTrue("yüksek yıldız duyarlılığı azaltır", Pricing.sensitivity(student, 5) < Pricing.sensitivity(student, 1));
        SaveData s = day(20);
        assertEquals(1.0, Pricing.arrivalMultiplier(s), 0);
        Pricing.setAll(s, 125);
        double high = Pricing.arrivalMultiplier(s);
        Pricing.setAll(s, 80);
        double low = Pricing.arrivalMultiplier(s);
        assertTrue(high + " < 1 < " + low, high < 0.8 && low > 1.2);
        // pahalı menüde öğrenci az, turist görece çok
        Pricing.setAll(s, 130);
        List<Product> menu = Catalog.productsForDay(20);
        assertTrue(Pricing.typeDemand(s, student, menu, 4) < Pricing.typeDemand(s, tourist, menu, 4));
        assertEquals("Tahmini müşteri: değişmez.", Narrator.demandText(day(3)));
        assertTrue(Narrator.demandText(s).endsWith("daha az."));
    }

    @Test public void priceChangesArrivalsQuotaAndShoppingAdvice() {
        SaveData ref = day(15), high = day(15), low = day(15);
        Pricing.setAll(high, 130);
        Pricing.setAll(low, 75);
        ref.timePressure = high.timePressure = low.timePressure = false;
        int qr = DayConfig.quotaFor(ref), qh = DayConfig.quotaFor(high), ql = DayConfig.quotaFor(low);
        assertTrue(qh + " < " + qr + " < " + ql, qh < qr && qr < ql);
        assertEquals(qh, new GameEngine(1, high).getCustomerQuota());
        // süreli: geliş aralığı fiyat çarpanına bölünür (motor gün başında sabitler)
        high.timePressure = true;
        low.timePressure = true;
        GameEngine eh = new GameEngine(9, high), el = new GameEngine(9, low), er = new GameEngine(9, ref);
        assertEquals(1.0, er.getPriceArrivalMultiplier(), 0);
        assertEquals(Pricing.arrivalMultiplier(high), eh.getPriceArrivalMultiplier(), 1e-9);
        assertTrue(eh.getPriceArrivalMultiplier() < 1.0 && el.getPriceArrivalMultiplier() > 1.0);
        // geniş sırada (masa takımı 5) ilk 60 sn: ucuz menüye daha çok müşteri gelir
        int[] n = new int[2];
        int[] pc = {130, 75};
        for (int i = 0; i < 2; i++) {
            SaveData q = day(15);
            q.upgradeLevels.put("masa", 5);
            Pricing.setAll(q, pc[i]);
            GameEngine g = new GameEngine(9, q);
            final int k = i;
            g.addListener(new GameListenerAdapter() { @Override public void onCustomerArrived(Order o) { n[k]++; } });
            g.startDay();
            g.tick(60f);
        }
        assertTrue(java.util.Arrays.toString(n), n[0] < n[1]);
        assertTrue("öneri talebe uyar", shopCostAt(100) > shopCostAt(130) && shopCostAt(75) > shopCostAt(100));
    }

    private static int shopCostAt(int pc) { SaveData s = day(15); Pricing.setAll(s, pc); for (Ingredient i : Catalog.ingredients()) s.inventory.set(i.id, 0); return Shop.recommendedCost(s); }

    @Test public void expensiveOrdersEarnMoreButCostStarsAndTipsCheapOnesTheReverse() {
        double[] gain = new double[3];
        int[] tip = new int[3], money = new int[3], react = new int[3];
        int[] pcs = {100, 140, 70};
        for (int i = 0; i < 3; i++) {
            SaveData s = day(10);
            s.stars = 3.0;
            Pricing.setAll(s, pcs[i]);
            GameEngine e = ResourceModelTest.engine(s);
            final int[] got = new int[2];
            e.addListener(new GameListenerAdapter() { @Override public void onOrderDelivered(Order o, int m, int t, boolean h) { got[0] = m; got[1] = t; } });
            Order o = e.spawnOrder(Collections.singletonList("ayran"), Collections.<String>emptyList());
            Flow.makeReady(e, o);
            double before = s.stars;
            assertTrue(e.deliver(o.id));
            gain[i] = s.stars - before;
            money[i] = got[0];
            tip[i] = got[1];
            react[i] = o.getPriceReaction();
        }
        assertTrue(money[1] > money[0] && money[0] > money[2]);
        assertTrue("pahalıda yıldız kazancı az (ya da düşer)", gain[1] < gain[0]);
        assertTrue("ucuzda yıldız kazancı fazla", gain[2] > gain[0]);
        assertEquals(0, react[0]);
        assertEquals(-1, react[2]);
        // aile duyarlılığı 1 (senaryo müşterisi): %140'ta v ≈ 0,4·1,0 > 0,2 -> "pahalı buldu"
        assertEquals(1, react[1]);
        assertTrue("bahşiş oranı pahalıda düşük", tip[1] / (double) money[1] < tip[0] / (double) money[0]);
        Pricing.Feel f = Pricing.feel(1.0, 1.8);
        assertEquals(1.0, f.starMult, 0);
        assertEquals(1.0, f.tipMult, 0);
        assertEquals(0.0, f.penalty, 0);
    }

    @Test public void cheapPriceMakesCustomersSlightlyMorePatient() {
        assertEquals(1.0, Pricing.patienceMult(1.0, 1.0), 0);
        assertEquals(1.0, Pricing.patienceMult(1.3, 1.0), 0);
        assertEquals(1.12, Pricing.patienceMult(0.8, 1.0), 1e-9);
        SaveData a = day(10), b = day(10);
        Pricing.setAll(b, 80);
        Order oa = ResourceModelTest.engine(a).spawnOrder(Collections.singletonList("ekmek_arasi"), Collections.<String>emptyList());
        Order ob = ResourceModelTest.engine(b).spawnOrder(Collections.singletonList("ekmek_arasi"), Collections.<String>emptyList());
        assertTrue(ob.customer.patienceMax > oa.customer.patienceMax);
    }

    @Test public void referencePricesKeepTheV20SimulationBitForBit() {
        // tüm fiyatlar %100 iken talep/rastgelelik değişmez: aynı tohum aynı gün
        SaveData a = day(8), b = day(8);
        Pricing.setAll(b, 100);
        GameEngine ea = new GameEngine(77, a), eb = new GameEngine(77, b);
        new TestBot(0.9f, true, true).playDay(ea);
        new TestBot(0.9f, true, true).playDay(eb);
        assertEquals(ea.getSummary().toString(), eb.getSummary().toString());
        List<String> x = new ArrayList<>();
        assertEquals(x, new ArrayList<String>());
    }
}
