package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * v2.6 motor düzeltmeleri (docs/denetim_v25_fable.md): sıfır kasa/boş stok tuzağı (acil destek, süresiz gün kilidi, iflas),
 * gün içi maaş ödemesi, fiyatın gün başında donması, kira kredisi sınırı ve kredi notu çiftliği, kesinti kalkınca talep/kota,
 * otomatik fatura ödemesi.
 */
public class V26EngineTest {
    static final List<String> NONE = Collections.emptyList();

    static SaveData day(int d) {
        SaveData s = ResourceModelTest.day(d, FlowMode.DENGELI);
        return s;
    }

    static void emptyStock(SaveData s) {
        for (Ingredient i : Catalog.ingredients()) s.inventory.set(i.id, 0);
        s.readyDrinks = 0;
    }

    /** Bankayı tamamen kapatır: 3 kredi, biri art arda 3 taksit kaçırmış. */
    static void blockBank(SaveData s) {
        s.creditScore = 0;
        s.money = 100000;
        for (int i = 0; i < Bank.MAX_LOANS; i++) assertEquals(Bank.Result.OK, Bank.takeLoan(s, 500, 5));
        // üç gün sonu: taksitler kaçar
        for (int d = 0; d < 3; d++) {
            s.money = 0;
            V25FinanceTest.finishEmptyDay(s);
        }
        s.bills.clear();
        assertTrue(Bank.blocked(s));
        assertEquals(0, Bank.available(s));
    }

    // ------------------------------------------------------------ (1) P0 sıfır kasa / boş stok
    @Test public void emergencyAidOpensTheZeroCashEmptyStockTrapEvenWhenTheBankIsBlocked() {
        SaveData s = day(28);
        s.autoPayBills = false;
        blockBank(s);
        emptyStock(s);
        s.money = 0;
        assertFalse(Finance.canOperate(s));
        assertTrue(Bank.emergencyAidAvailable(s));
        assertEquals("kurtarma yolu var: iflas değil", Finance.Bankruptcy.NONE, Finance.bankruptcy(s));
        int amount = Bank.emergencyAidAmount(s);
        assertTrue(amount >= Shop.recommendedCost(s));
        assertEquals(0, amount % Bank.STEP);
        assertEquals(Bank.Result.OK, Bank.takeEmergencyAid(s));
        assertEquals(amount, s.money);
        Loan aid = null;
        for (Loan l : s.loans) if (l.kind == Loan.Kind.EMERGENCY) aid = l;
        assertNotNull(aid);
        assertEquals(Bank.EMERGENCY_TERM, aid.term);
        assertEquals("en yüksek faiz", (int) Math.round(amount * Bank.WORST_RATE * Bank.EMERGENCY_TERM), aid.interest);
        // kayıtta tür korunur
        SaveData r = SaveData.parse(s.serialize());
        int n = 0;
        for (Loan l : r.loans) if (l.kind == Loan.Kind.EMERGENCY) n++;
        assertEquals(1, n);
        // aynı anda ikinci destek yok
        assertEquals(Bank.Result.TOO_MANY_LOANS, Bank.takeEmergencyAid(s));
        // destekle stok alınır, dükkân çalışabilir
        Shop.buyRecommended(s);
        assertTrue(Finance.canOperate(s));
        assertEquals(Finance.Bankruptcy.NONE, Finance.bankruptcy(s));
    }

    @Test public void noMeansAndNoRescueIsBankruptcy() {
        SaveData s = day(28);
        blockBank(s);
        emptyStock(s);
        s.money = 0;
        assertEquals(Bank.Result.OK, Bank.takeEmergencyAid(s));
        s.money = 0; // destek de harcandı
        assertFalse(Bank.emergencyAidAvailable(s));
        assertEquals(Finance.Bankruptcy.NO_MEANS, Finance.bankruptcy(s));
        GameEngine e = new GameEngine(1, s);
        assertTrue(e.isBankrupt());
        assertEquals(Finance.Bankruptcy.NO_MEANS, e.getBankruptcy());
        // biraz para ya da stok: iflas değil
        s.money = 200;
        assertTrue(Finance.canOperate(s));
        assertEquals(Finance.Bankruptcy.NONE, Finance.bankruptcy(s));
    }

    @Test public void restartAfterBankruptcyKeepsSettingsAndRecord() {
        SaveData s = day(40);
        s.bestDay = 41;
        s.tempo = 1.3;
        s.autoPayBills = false;
        s.timePressure = false;
        s.failStreak = 4;
        SaveData n = SaveData.restartAfterBankruptcy(s);
        assertEquals(1, n.day);
        assertEquals(SaveData.START_MONEY, n.money);
        assertEquals(41, n.bestDay);
        assertEquals(1.3, n.tempo, 1e-9);
        assertFalse(n.autoPayBills);
        assertFalse(n.timePressure);
        assertEquals(0, n.failStreak);
        assertTrue(n.loans.isEmpty());
        assertTrue(n.bills.isEmpty());
    }

    @Test public void untimedDayWithNothingDoableAndNoMoneyClosesInsteadOfHangingForever() {
        SaveData s = day(10);
        s.timePressure = false;
        emptyStock(s);
        s.money = 0;
        double stars = s.stars;
        GameEngine e = new GameEngine(1, s);
        e.setSpawning(false);
        final int[] stuck = {-1};
        e.addListener(new GameListenerAdapter() { @Override public void onDayStuck(int n) { stuck[0] = n; } });
        e.startDay();
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        assertNotNull(o);
        ResourceModelTest.run(e, 2f);
        assertTrue(e.isStuckNow());
        assertEquals(GameEngine.State.RUNNING, e.getState());
        ResourceModelTest.run(e, GameEngine.STUCK_SECONDS + 2f);
        assertEquals(GameEngine.State.FINISHED, e.getState());
        DaySummary d = e.getSummary();
        assertTrue(d.stuckEnded);
        assertEquals(1, d.stuckClosedOrders);
        assertEquals(1, stuck[0]);
        assertEquals("cezasız: müşteri kaybı yok", 0, d.lost);
        assertTrue(s.stars >= stars - 1e-9);
        assertEquals(11, s.day);
    }

    @Test public void untimedDayIsNotStuckWhenEmergencyPurchaseIsAffordableOrAidIsTaken() {
        SaveData s = day(10);
        s.timePressure = false;
        emptyStock(s);
        s.money = 0;
        GameEngine e = new GameEngine(1, s);
        e.setSpawning(false);
        e.startDay();
        e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        ResourceModelTest.run(e, 5f);
        assertTrue(e.isStuckNow());
        assertTrue(e.isEmergencyAidAvailable());
        assertEquals(Bank.Result.OK, e.takeEmergencyAid());
        assertFalse("acil alım artık yapılabilir", e.isStuckNow());
        ResourceModelTest.run(e, GameEngine.STUCK_SECONDS + 5f);
        assertEquals(GameEngine.State.RUNNING, e.getState());
    }

    @Test public void failedDaysCountUpAndASuccessfulDayResetsTheStreak() {
        SaveData s = day(12);
        s.stars = 1.1;
        for (int k = 1; k <= 2; k++) {
            GameEngine e = new GameEngine(k, s);
            e.setSpawning(false);
            e.startDay();
            e.spawnOrder(Collections.singletonList("ayran"), NONE);
            s.stars = 1.1;
            ResourceModelTest.run(e, 200f);
            assertEquals(GameEngine.State.FAILED, e.getState());
            assertEquals(k, s.failStreak);
            assertEquals(k, e.getSummary().failStreak);
            assertEquals(Finance.Bankruptcy.NONE, e.getSummary().bankruptcy);
            s.stars = 1.1;
        }
        assertEquals(2, SaveData.parse(s.serialize()).failStreak);
        GameEngine ok = V25FinanceTest.finishEmptyDay(s);
        assertEquals(0, s.failStreak);
        assertEquals(Finance.Bankruptcy.NONE, ok.getSummary().bankruptcy);
    }

    @Test public void failLoopWithoutRescueIsBankruptcy() {
        SaveData s = day(20);
        blockBank(s);
        s.money = 0;
        emptyStock(s);
        assertEquals(Bank.Result.OK, Bank.takeEmergencyAid(s));
        s.money = 100;
        s.inventory.set("tavuk_doner", 2);
        s.inventory.set("lavas", 2);
        s.inventory.set("ekmek", 2);
        s.failStreak = Finance.FAIL_LOOP_DAYS - 1;
        assertEquals(Finance.Bankruptcy.NONE, Finance.bankruptcy(s));
        s.failStreak = Finance.FAIL_LOOP_DAYS;
        assertEquals(Finance.Bankruptcy.FAIL_LOOP, Finance.bankruptcy(s));
    }

    @Test public void evictionWithoutRentLoanIsBankruptcy() {
        SaveData s = day(20);
        s.money = 0;
        s.creditScore = 0;
        s.stars = 1.0;
        // limit dışı iki kira kredisi zaten açık
        for (int i = 0; i < Bank.MAX_RENT_LOANS; i++) {
            Bill b = Finance.issue(s, BillType.KIRA, 9000, s.day);
            assertEquals(Bank.Result.OK, Finance.payWithLoan(s, b.id, 10));
        }
        s.money = 0;
        Finance.issue(s, BillType.KIRA, 5000, s.day - 9); // 7 gün gecikti: tahliye
        assertTrue(UtilityStatus.of(s, s.day).evicted());
        assertEquals(Finance.Bankruptcy.EVICTED, Finance.bankruptcy(s));
        GameEngine e = new GameEngine(1, s);
        assertFalse(e.startDay());
        assertEquals(GameEngine.State.FAILED, e.getState());
        assertEquals(Finance.Bankruptcy.EVICTED, e.getSummary().bankruptcy);
        assertTrue(e.getSummary().isBankrupt());
    }

    // ------------------------------------------------------------ (2) gün içi maaş
    @Test public void wagePaidDuringTheDayIsChargedAtDayEnd() {
        SaveData s = day(9);
        s.upgradeLevels.put("cirak", 2);
        s.wageDebt = 400;
        GameEngine e = new GameEngine(1, s);
        e.setSpawning(false);
        e.startDay();
        assertTrue(e.isApprenticeAbsent());
        assertEquals(Finance.PayResult.OK, e.payWages());
        assertEquals(2, e.getApprenticeLevel());
        e.tick(e.getConfig().duration + 1f);
        assertEquals(GameEngine.State.FINISHED, e.getState());
        assertEquals("çırak gün içinde geldi: maaş yazılır", Finance.dailyWage(2, 9), e.getSummary().wagesDue);
        assertEquals(Finance.dailyWage(2, 9), e.getSummary().wagesPaid);
    }

    @Test public void absentApprenticeWhoNeverComesStillCostsNothing() {
        SaveData s = day(9);
        s.upgradeLevels.put("cirak", 2);
        s.wageDebt = 400;
        GameEngine e = V25FinanceTest.finishEmptyDay(s);
        assertEquals(0, e.getSummary().wagesDue);
    }

    // ------------------------------------------------------------ (3) fiyat gün başında donar
    @Test public void priceChangesDuringTheDayDoNotAffectToday() {
        SaveData s = day(6);
        GameEngine e = ResourceModelTest.engine(s);
        double arrival = e.getPriceArrivalMultiplier();
        Pricing.setAll(s, 150);
        Order o = e.spawnOrder(Arrays.asList("ekmek_arasi", "ayran"), NONE);
        assertEquals("kurucudaki fiyat (%100) geçerli", 1.0, o.priceRatio(), 1e-12);
        assertEquals(o.referencePrice(), o.price());
        assertEquals(100, e.getDayPricePercent("ekmek_arasi"));
        assertEquals(arrival, e.getPriceArrivalMultiplier(), 1e-12);

        SaveData t = day(6);
        Pricing.setAll(t, 130);
        GameEngine f = ResourceModelTest.engine(t);
        double arr = f.getPriceArrivalMultiplier();
        assertTrue(arr < 1.0);
        Pricing.setAll(t, 50);
        Order p = f.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        assertEquals("gün içinde indirim bugünü etkilemez", 1.3, p.priceRatio(), 1e-9);
        assertEquals(130, f.getDayPricePercent("ekmek_arasi"));
        assertEquals(arr, f.getPriceArrivalMultiplier(), 1e-12);
    }

    // ------------------------------------------------------------ (4) kira kredisi sınırı, kredi notu çiftliği
    @Test public void limitlessRentLoansAreCapped() {
        SaveData s = day(15);
        s.money = 0;
        s.creditScore = 0;
        s.stars = 1.0;
        for (int i = 0; i < Bank.MAX_RENT_LOANS; i++) {
            Bill rent = Finance.issue(s, BillType.KIRA, 9000, 14);
            assertTrue(Bank.rentLoanAvailable(s, 9000));
            assertEquals(Bank.Result.OK, Finance.payWithLoan(s, rent.id, 10));
        }
        assertEquals(Bank.MAX_RENT_LOANS, Bank.openCount(s, Loan.Kind.RENT));
        Bill third = Finance.issue(s, BillType.KIRA, 9000, 14);
        assertFalse(Bank.rentLoanAvailable(s, 9000));
        int money = s.money;
        assertEquals(Bank.Result.TOO_MANY_LOANS, Finance.payWithLoan(s, third.id, 10));
        assertNotNull(Finance.bill(s, third.id));
        assertEquals(money, s.money);
        // kayıt: tür korunur
        assertEquals(Bank.MAX_RENT_LOANS, Bank.openCount(SaveData.parse(s.serialize()), Loan.Kind.RENT));
    }

    @Test public void rentLoanWithinTheNormalLimitIsANormalLoanAndNotCapped() {
        SaveData s = day(30);
        s.money = 0;
        Bill rent = Finance.issue(s, BillType.KIRA, 1000, 29);
        assertEquals(Bank.Result.OK, Finance.payWithLoan(s, rent.id, 10));
        assertEquals(0, Bank.openCount(s, Loan.Kind.RENT));
        assertEquals(1, Bank.openCount(s, Loan.Kind.NORMAL));
    }

    @Test public void earlyRepayBonusNeedsAPastDayAndAnInstallment() {
        SaveData s = day(10);
        s.creditScore = 60;
        assertEquals(Bank.Result.OK, Bank.takeLoan(s, 500, 5));
        Loan l = s.loans.get(0);
        assertFalse(Bank.earnsRepayBonus(s, l));
        assertEquals(Bank.Result.OK, Bank.repay(s, l.id));
        assertEquals("çiftlik kapandı: aynı gün kapatma ödülsüz", 60, s.creditScore);
        // ertesi gün ama taksit yok (gün sonu geçmedi): yine ödülsüz
        assertEquals(Bank.Result.OK, Bank.takeLoan(s, 500, 5));
        Loan m = s.loans.get(0);
        s.day++;
        assertFalse(Bank.earnsRepayBonus(s, m));
        // bir gün sonu geçti (taksit vadesi geldi ve ödendi): ödül
        s.day--;
        V25FinanceTest.finishEmptyDay(s);
        assertEquals(4, m.getInstallmentsLeft());
        assertTrue(Bank.earnsRepayBonus(s, m));
        int c = s.creditScore;
        assertEquals(Bank.Result.OK, Bank.repay(s, m.id));
        assertEquals(c + 3, s.creditScore);
    }

    // ------------------------------------------------------------ (5) kesinti kalkınca talep ve kota
    @Test public void payingAGasBillMidDayRestoresMenuDemandAndQuota() {
        SaveData s = day(6);
        s.autoPayBills = false;
        s.timePressure = false;
        Pricing.setPercent(s, "ekmek_arasi", 150); // tek ürün pahalı: menü değişince sepet oranı değişir
        Bill gas = Finance.issue(s, BillType.DOGALGAZ, 200, 1); // 3 gün gecikti: Ocak kapalı
        GameEngine e = new GameEngine(1, s);
        e.setSpawning(false);
        e.startDay();
        assertTrue(e.isStationClosed(Station.IZGARA));
        int quota = e.getCustomerQuota();
        double arrival = e.getPriceArrivalMultiplier();
        int menu = e.getMenu().size();
        assertEquals(Finance.PayResult.OK, e.payBill(gas.id));
        assertFalse(e.isStationClosed(Station.IZGARA));
        assertTrue(e.getMenu().size() > menu);
        assertTrue("Ocak kapalıyken ×0,9 olan kota geri gelir", e.getCustomerQuota() > quota);
        // talep yeni menüye göre: kesintisiz kurulmuş motorla aynı
        SaveData ref = s.copy();
        GameEngine f = new GameEngine(1, ref);
        assertEquals(f.getPriceArrivalMultiplier(), e.getPriceArrivalMultiplier(), 1e-12);
        assertEquals(f.getCustomerQuota(), e.getCustomerQuota());
        assertNotEquals(arrival, e.getPriceArrivalMultiplier(), 0);
    }

    // ------------------------------------------------------------ (6) otomatik fatura ödemesi
    @Test public void autoPaySettingDefaultsOnAndPersists() {
        assertTrue(new SaveData().autoPayBills);
        assertTrue(SaveData.newGame().autoPayBills);
        SaveData s = SaveData.newGame();
        String old = s.serialize().replace("autoPay=1\n", "");
        assertFalse(old.contains("autoPay"));
        assertTrue("eski kayıtta açık", SaveData.parse(old).autoPayBills);
        GameEngine e = new GameEngine(1, s);
        assertTrue(e.isAutoPayBills());
        e.setAutoPayBills(false);
        assertFalse(e.isAutoPayBills());
        assertFalse(s.autoPayBills);
        assertFalse(SaveData.parse(s.serialize()).autoPayBills);
        assertFalse("ayar yeni oyunda korunur", SaveData.newGameKeepingSettings(s).autoPayBills);
        SaveData z = SaveData.parse(s.serialize().replace("epoch=" + SaveData.EPOCH, "epoch=1"));
        assertFalse("dönem sıfırlamasında da korunur", z.autoPayBills);
    }

    @Test public void dayEndAutoPaysBillsDueTodayWhenCashSuffices() {
        SaveData s = day(8);
        Bill due = Finance.issue(s, BillType.ELEKTRIK, 300, 6); // son ödeme 8 (bugün)
        Bill later = Finance.issue(s, BillType.SU, 100, 7);      // son ödeme 9
        int credit = s.creditScore;
        GameEngine e = V25FinanceTest.finishEmptyDay(s);
        DaySummary d = e.getSummary();
        assertNull("son günü gelen ödendi", Finance.bill(s, due.id));
        assertNotNull("son günü gelmeyen bekler", Finance.bill(s, later.id));
        assertEquals(1, d.autoPaid.size());
        assertEquals(BillType.ELEKTRIK, d.autoPaid.get(0).type);
        assertEquals(300, d.autoPaidTotal);
        assertTrue("zamanında: kredi notu düşmez", s.creditScore >= credit);
    }

    @Test public void dayEndAutoPayDoesNothingWhenOffOrCashTooLow() {
        SaveData s = day(8);
        s.autoPayBills = false;
        Bill b = Finance.issue(s, BillType.ELEKTRIK, 300, 6);
        V25FinanceTest.finishEmptyDay(s);
        assertNotNull(Finance.bill(s, b.id));

        SaveData t = day(8);
        emptyStock(t);
        Bill c = Finance.issue(t, BillType.ELEKTRIK, 300, 6);
        t.money = Finance.autoPayReserve(t, 9) + 100; // fatura stok payını yer: ödenmez
        GameEngine e = V25FinanceTest.finishEmptyDay(t);
        assertNotNull("kasa stok payını korur", Finance.bill(t, c.id));
        assertEquals(0, e.getSummary().autoPaidTotal);
        assertEquals("ertesi gün normal gecikme akışı", 1, Finance.bill(t, c.id).daysLate(t.day));
    }

    @Test public void dayOpenAutoPaysLateBillsSoTheCutNeverStarts() {
        SaveData s = day(6);
        s.money = 30000;
        Bill gas = Finance.issue(s, BillType.DOGALGAZ, 200, 1); // 3 gün gecikti
        final List<Bill> paid = new ArrayList<>();
        GameEngine e = new GameEngine(1, s);
        e.addListener(new GameListenerAdapter() { @Override public void onBillPaid(Bill b, int amount) { paid.add(b); } });
        e.setSpawning(false);
        e.startDay();
        assertNull(Finance.bill(s, gas.id));
        assertFalse(e.isStationClosed(Station.IZGARA));
        assertEquals(1, paid.size());
        assertTrue(e.getSummary().autoPaidTotal >= 200);
        // brifing (UI) aynı ödemeyi motordan önce yapabilir: çift ödeme olmaz
        SaveData t = day(6);
        t.money = 30000;
        Finance.issue(t, BillType.DOGALGAZ, 200, 1);
        assertEquals(1, Finance.autoPayAtOpen(t).size());
        int m = t.money;
        assertTrue(Finance.autoPayAtOpen(t).isEmpty());
        GameEngine f = new GameEngine(1, t);
        f.setSpawning(false);
        f.startDay();
        assertEquals(m, t.money);
    }

    @Test public void failedDayUndoesTheOpeningAutoPayment() {
        SaveData s = day(6);
        s.money = 30000;
        Bill gas = Finance.issue(s, BillType.DOGALGAZ, 200, 1);
        s.stars = 1.1;
        GameEngine e = new GameEngine(1, s);
        e.setSpawning(false);
        e.startDay();
        assertNull(Finance.bill(s, gas.id));
        e.spawnOrder(Collections.singletonList("ayran"), NONE);
        s.stars = 1.1;
        ResourceModelTest.run(e, 200f);
        assertEquals(GameEngine.State.FAILED, e.getState());
        assertEquals(30000, s.money);
        assertNotNull("fatura gün başına döner", Finance.bill(s, gas.id));
    }
}
