package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** v2.5: çırak maaşı, faturalar (takvim, tutar, gecikme), ödenmeyen fatura zorlukları, gün içi ödeme (design_v25 §3–§4). */
public class V25FinanceTest {
    static final List<String> NONE = Collections.emptyList();

    /** v2.6: bu dosyadaki senaryolar elle ödeyen oyuncuyu sınar (ödenmeyen fatura zorlukları); otomatik ödeme kapalı. */
    static SaveData day(int d) {
        SaveData s = ResourceModelTest.day(d, FlowMode.DENGELI);
        s.autoPayBills = false;
        return s;
    }

    /** Müşterisiz bir günü sonuna kadar oynatır (gün sonu hesabı çalışır). */
    static GameEngine finishEmptyDay(SaveData s) {
        GameEngine e = new GameEngine(1, s);
        e.setSpawning(false);
        e.startDay();
        e.tick(e.getConfig().duration + 1f);
        assertEquals(GameEngine.State.FINISHED, e.getState());
        return e;
    }

    // ------------------------------------------------------------ maaş
    @Test public void wageTableGrowsWithLevelAndDay() {
        assertEquals(0, Finance.dailyWage(0, 10));
        assertEquals(250, Finance.dailyWage(1, 1));
        assertEquals(550, Finance.dailyWage(2, 1));
        assertEquals(900, Finance.dailyWage(3, 1));
        assertEquals(1250, Finance.dailyWage(4, 1));
        assertEquals(1650, Finance.dailyWage(5, 1));
        assertEquals("gün başına %1", 1188, Finance.dailyWage(3, 33));
        for (int lv = 1; lv < 5; lv++) assertTrue(Finance.dailyWage(lv + 1, 20) > Finance.dailyWage(lv, 20));
    }

    @Test public void wageIsPaidAtDayEndAndUnpaidWageKeepsTheApprenticeHome() {
        SaveData s = day(5);
        s.upgradeLevels.put("cirak", 2);
        s.money = 100;
        GameEngine e = finishEmptyDay(s);
        DaySummary d = e.getSummary();
        assertEquals(Finance.dailyWage(2, 5), d.wagesDue);
        assertEquals("yalnızca eldeki para", 100, d.wagesPaid);
        assertEquals(d.wagesDue - 100, s.wageDebt);
        assertEquals(0, s.money);
        // ertesi gün çırak gelmez, maaş da yazılmaz
        GameEngine f = new GameEngine(2, s);
        f.setSpawning(false);
        assertTrue(f.isApprenticeAbsent());
        assertEquals(0, f.getApprenticeLevel());
        assertEquals(0, f.getApprenticeWorkers());
        final List<ApprenticeAction> acts = new ArrayList<>();
        f.addListener(new GameListenerAdapter() { @Override public void onApprenticeAction(ApprenticeAction a) { acts.add(a); } });
        final List<String> notes = new ArrayList<>();
        f.addListener(new GameListenerAdapter() { @Override public void onFinanceNotices(List<String> n) { notes.addAll(n); } });
        f.startDay();
        assertTrue(notes.toString(), notes.toString().contains("Çırak maaşını alamadı, bugün gelmedi."));
        Order o = f.spawnOrder(Collections.singletonList("ayran"), NONE);
        ResourceModelTest.run(f, 4f);
        assertTrue("çırak yok: hiçbir iş yapmaz", acts.isEmpty());
        // gün içinde borcu ödeyince çırak hemen gelir
        s.money = 5000;
        assertEquals(Finance.PayResult.OK, f.payWages());
        assertFalse(f.isApprenticeAbsent());
        assertEquals(2, f.getApprenticeLevel());
        ResourceModelTest.run(f, 1f);
        assertFalse("çırak işe başladı", acts.isEmpty());
        assertNotNull(o);
    }

    @Test public void absentApprenticeDayAddsNoNewWageButCollectsDebt() {
        SaveData s = day(6);
        s.upgradeLevels.put("cirak", 1);
        s.wageDebt = 300;
        s.money = 5000;
        GameEngine e = finishEmptyDay(s);
        assertEquals("gelmeyen çırağa maaş yazılmaz", 0, e.getSummary().wagesDue);
        assertEquals("birikmiş borç kapanır", 300, e.getSummary().wagesPaid);
        assertEquals(0, s.wageDebt);
    }

    // ------------------------------------------------------------ fatura takvimi ve tutarları
    @Test public void billCalendarAndDueDays() {
        assertTrue(BillType.ELEKTRIK.issuedOn(3) && BillType.ELEKTRIK.issuedOn(10) && BillType.ELEKTRIK.issuedOn(59));
        assertTrue(BillType.DOGALGAZ.issuedOn(4) && BillType.SU.issuedOn(5) && BillType.KIRA.issuedOn(7) && BillType.KIRA.issuedOn(56));
        assertTrue(BillType.VERGI.issuedOn(10) && BillType.VERGI.issuedOn(60));
        assertFalse(BillType.ELEKTRIK.issuedOn(1) || BillType.ELEKTRIK.issuedOn(2) || BillType.KIRA.issuedOn(6));
        assertEquals(14, BillType.KIRA.nextIssueDay(8));
        // gün gün: ilk iki gün fatura yok, sonra her tür kendi gününde, son ödeme +2
        SaveData s = SaveData.newGame();
        s.money = 100000;
        List<BillType> seen = new ArrayList<>();
        for (int d = 1; d <= 10; d++) {
            s.day = d;
            GameEngine e = finishEmptyDay(s);
            for (Bill b : e.getSummary().newBills) {
                seen.add(b.type);
                assertEquals(d, b.issuedDay);
                assertEquals(d + Finance.DUE_DAYS, b.dueDay);
                assertTrue(b.amount > 0);
            }
            if (d <= 2) assertTrue(e.getSummary().newBills.isEmpty());
        }
        assertEquals(Arrays.asList(BillType.ELEKTRIK, BillType.DOGALGAZ, BillType.SU, BillType.KIRA), seen.subList(0, 4));
        assertFalse("satış yoksa vergi kesilmez", seen.contains(BillType.VERGI));
    }

    @Test public void amountsFollowTheFormulaAndGrowWithDayAndEquipment() {
        SaveData s = SaveData.newGame();
        assertEquals(7 * (30 + 3), Finance.billAmount(s, BillType.ELEKTRIK, 3));
        assertEquals(207, Finance.billAmount(s, BillType.DOGALGAZ, 4)); // 7·(25 + 1,5·3) = 206,5 -> 207
        assertEquals(7 * 200 + 7 * 20 * 6, Finance.billAmount(s, BillType.KIRA, 7));
        assertEquals(7 * Finance.weeklyRentBase(56), Finance.billAmount(s, BillType.KIRA, 56));
        int e10 = Finance.billAmount(s, BillType.ELEKTRIK, 10);
        assertTrue("gün ilerledikçe artar", Finance.billAmount(s, BillType.ELEKTRIK, 31) > e10);
        s.upgradeLevels.put("ekmek_isitici", 3);
        s.upgradeLevels.put("buzdolabi", 2);
        assertEquals(5, Finance.load(s, BillType.ELEKTRIK));
        assertEquals(e10 + 7 * 10 * 5, Finance.billAmount(s, BillType.ELEKTRIK, 10));
        int g = Finance.billAmount(s, BillType.DOGALGAZ, 11);
        s.upgradeLevels.put("ocak", 2);
        assertEquals(g + 7 * 12 * 2, Finance.billAmount(s, BillType.DOGALGAZ, 11));
        int w = Finance.billAmount(s, BillType.SU, 12);
        s.upgradeLevels.put("cirak", 3);
        assertEquals(w + 7 * 6 * 3, Finance.billAmount(s, BillType.SU, 12));
        // vergi: dönemin satış + bahşişinin %5'i
        s.salesSinceTax = 40000;
        assertEquals(2000, Finance.billAmount(s, BillType.VERGI, 10));
    }

    @Test public void taxAccumulatesSalesAndIsIssuedEveryTenDays() {
        SaveData s = day(9);
        GameEngine e = new GameEngine(3, s);
        new TestBot(0.8f, true, true).playDay(e);
        int sales9 = e.getSummary().revenue + e.getSummary().tips;
        assertEquals(sales9, s.salesSinceTax);
        GameEngine f = new GameEngine(4, s);
        new TestBot(0.8f, true, true).playDay(f);
        int sales10 = f.getSummary().revenue + f.getSummary().tips;
        Bill tax = null;
        for (Bill b : f.getSummary().newBills) if (b.type == BillType.VERGI) tax = b;
        assertNotNull(tax);
        assertEquals((int) Math.round((sales9 + sales10) * Finance.TAX_RATE), tax.amount);
        assertEquals("vergi kesilince birikim sıfırlanır", 0, s.salesSinceTax);
    }

    @Test public void lateFeeLevelsPaymentAndCredit() {
        SaveData s = day(10);
        Bill b = Finance.issue(s, BillType.ELEKTRIK, 1000, 8); // son ödeme 10
        assertEquals(10, b.dueDay);
        assertEquals(0, b.daysLate(10));
        assertEquals(0, b.daysLeft(10));
        assertEquals(1000, b.amountDue(10));
        assertEquals(0, b.level(10));
        assertEquals(1, b.level(11));
        assertEquals(1030, b.amountDue(11));
        assertEquals(1, b.level(12));
        assertEquals(2, b.level(13));
        assertEquals(1090, b.amountDue(13));
        Bill rent = Finance.issue(s, BillType.KIRA, 2000, 8);
        assertEquals(1, rent.level(12));
        assertEquals(2, rent.level(13));
        assertEquals(2, rent.level(15));
        assertEquals("tahliye", 3, rent.level(16));
        Bill tax = Finance.issue(s, BillType.VERGI, 500, 8);
        assertEquals(1, tax.level(13));
        assertEquals("haciz", 2, tax.level(14));
        // zamanında ödeme: kredi notu +2
        int score = s.creditScore;
        assertEquals(Finance.PayResult.OK, Finance.pay(s, b.id));
        assertEquals(score + 2, s.creditScore);
        assertNull(Finance.bill(s, b.id));
        // para yetmezse ödenmez
        s.money = 10;
        assertEquals(Finance.PayResult.NOT_ENOUGH_MONEY, Finance.pay(s, rent.id));
        assertEquals(Finance.PayResult.UNKNOWN, Finance.pay(s, 9999));
        // payAll kasada bırakılacak parayı korur
        s.money = 2600;
        assertEquals(1, Finance.payAll(s, 500)); // kira 2000 ödenir, vergi 500 ödenirse kasada 100 kalırdı
        assertEquals(1, s.bills.size());
        // gecikmiş her fatura gün sonunda kredi notunu 2 düşürür
        SaveData t = day(12);
        Finance.issue(t, BillType.SU, 300, 8);
        int before = t.creditScore;
        finishEmptyDay(t);
        assertEquals(before - 2, t.creditScore);
    }

    @Test public void noticesWarnAheadClearlyAndShortly() {
        SaveData s = day(5);
        Finance.issue(s, BillType.ELEKTRIK, 315, 3); // son 5
        Finance.issue(s, BillType.DOGALGAZ, 200, 4); // son 6
        List<String> n = Finance.notices(s, 5);
        assertEquals(n.toString(), "Elektrik faturası 315 lira, son ödeme günü bugün. Gün sonunda ödemezsen yarın elektrik kesilir.", n.get(0));
        assertEquals("Doğalgaz faturası 200 lira, son ödeme yarın.", n.get(1));
        List<String> late = Finance.notices(s, 6);
        assertTrue(late.get(0), late.get(0).startsWith("Elektrik faturası 1 gündür ödenmedi, borç 324 lira. Elektrik kısmen kesik"));
        assertTrue(late.get(0), late.get(0).endsWith("Ödersen hemen düzelir."));
        for (String t : Finance.notices(s, 9)) assertTrue(t, t.length() < 200);
        assertTrue(Finance.notices(SaveData.newGame(), 1).isEmpty());
    }

    // ------------------------------------------------------------ kesinti etkileri
    @Test public void electricityPartialThenFullCutAndGenerator() {
        SaveData s = day(8);
        s.readyDrinks = 6;
        Finance.issue(s, BillType.ELEKTRIK, 300, 5); // son 7: gün 8'de 1 gün gecikme
        GameEngine e = new GameEngine(1, s);
        UtilityStatus u = e.getUtilityStatus();
        assertEquals(1, u.electric);
        assertEquals("ekmek ısıtıcı bir yer az", 1, e.getStationCapacity(Station.EKMEK));
        e.setSpawning(false);
        e.startDay();
        assertEquals("hazır ayranın yarısı bozuldu", 3, s.readyDrinks);
        assertEquals(3, e.getSummary().spoiledDrinks);
        // süreler: ekmek ×1,3, ocak etkilenmez
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        GameEngine ref = ResourceModelTest.engine(day(8));
        Order r = ref.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        e.startStep(o.id, 0, Station.EKMEK.id);
        ref.startStep(r.id, 0, Station.EKMEK.id);
        assertEquals(1.3, e.getActiveSteps().get(0).total / ref.getActiveSteps().get(0).total, 0.02);
        // tam kesinti (3+ gün): tek yer, döner yavaş, gün sonunda et bozulur
        SaveData t = day(10);
        Finance.issue(t, BillType.ELEKTRIK, 300, 5);
        int meat = t.inventory.get("et_doner");
        GameEngine f = finishEmptyDay(t);
        assertEquals(2, f.getUtilityStatus().electric);
        assertEquals(1, f.getStationCapacity(Station.EKMEK));
        assertEquals((int) (meat - Math.ceil(meat * 0.25)), t.inventory.get("et_doner"));
        assertTrue(f.getSummary().spoiledStock > 0 && f.getSummary().spoiledCost > 0);
        // jeneratör 1 kademe, 2. seviye 2 kademe karşılar
        SaveData g = day(10);
        Finance.issue(g, BillType.ELEKTRIK, 300, 5);
        g.upgradeLevels.put("jenerator", 1);
        GameEngine ge = new GameEngine(1, g);
        assertEquals(2, ge.getUtilityStatus().electricBill);
        assertEquals(1, ge.getUtilityStatus().electric);
        g.upgradeLevels.put("jenerator", 2);
        assertFalse(new GameEngine(1, g).getUtilityStatus().any());
    }

    @Test public void gasPartialNarrowsOcakAndFullCutClosesIt() {
        SaveData s = day(12);
        Finance.issue(s, BillType.DOGALGAZ, 300, 9); // son 11: 1 gün gecikme
        GameEngine e = new GameEngine(1, s);
        assertEquals(1, e.getStationCapacity(Station.IZGARA));
        assertFalse(e.isStationClosed(Station.IZGARA));
        SaveData t = day(16);
        Finance.issue(t, BillType.DOGALGAZ, 300, 9); // 5 gün gecikme: tam kesinti
        GameEngine f = new GameEngine(1, t);
        f.setSpawning(false);
        final List<Station> blocked = new ArrayList<>();
        f.addListener(new GameListenerAdapter() { @Override public void onUtilityBlocked(Order o, Station st, BillType c) { blocked.add(st); } });
        f.startDay();
        assertTrue(f.isStationClosed(Station.IZGARA));
        for (Product p : f.getMenu()) for (Step st : p.steps) assertNotSame("Ocak isteyen ürün menüde yok: " + p.id, Station.IZGARA, st.station);
        assertTrue(f.getMenu().size() < Catalog.productsForDay(16).size());
        assertFalse("patates yok", f.menuHasFries());
        assertEquals(PrepResult.UTILITY_OFF, f.startPrep(PrepKind.FRIES, Collections.<String>emptyList()));
        // senaryo siparişi Ocak isterse: kapalı
        Order o = f.spawnOrder(Collections.singletonList("lahmacun"), NONE);
        Flow.doItemStation(f, o, 0, Station.SOS);
        assertEquals(StartResult.UTILITY_OFF, f.startStep(o.id, 0, Station.IZGARA.id));
        assertTrue(blocked.contains(Station.IZGARA));
        boolean closedRow = false;
        for (ReadyAction a : f.getReadyActions(o.id)) if (a.status == ReadyAction.Status.CLOSED) closedRow = true;
        assertTrue(closedRow);
        assertTrue(new Narrator(f).stationLabel(Station.IZGARA).contains("kapalı, doğalgaz kesik"));
        // gün içinde ödeyince hemen açılır
        Bill gas = t.bills.get(0);
        final List<Bill> paid = new ArrayList<>();
        f.addListener(new GameListenerAdapter() { @Override public void onBillPaid(Bill b, int amt) { paid.add(b); } });
        assertEquals(Finance.PayResult.OK, f.payBill(gas.id));
        assertFalse(f.isStationClosed(Station.IZGARA));
        assertEquals(StartResult.STARTED, f.startStep(o.id, 0, Station.IZGARA.id));
        assertEquals(1, paid.size());
        assertEquals(Catalog.productsForDay(16).size(), f.getMenu().size());
    }

    @Test public void waterCutBlocksTrayThenRemovesTeaAndSoupAndHurtsStars() {
        SaveData s = day(33);
        Finance.issue(s, BillType.SU, 200, 30); // son 32: 1 gün
        GameEngine e = new GameEngine(1, s);
        e.setSpawning(false);
        e.startDay();
        assertEquals(PrepResult.UTILITY_OFF, e.startPrepTray(Arrays.asList("domates", "sogan")));
        StationOption tray = null;
        for (StationOption op : e.getStationOptions(-1, -1, Station.SOS.id)) if (op.id.equals("prep:tray")) tray = op;
        assertFalse(tray.enabled);
        assertEquals(GameEngine.LOCK_WATER, tray.lockReason);
        boolean tea = false;
        for (Product p : e.getMenu()) if (p.id.equals("cay")) tea = true;
        assertTrue("kısmi kesintide çay menüde", tea);
        SaveData t = day(36);
        Finance.issue(t, BillType.SU, 200, 30); // 4 gün: tam
        GameEngine f = new GameEngine(1, t);
        for (Product p : f.getMenu()) assertFalse(p.id, p.needsWater());
        assertEquals(0.6, f.getUtilityStatus().starGainMult(), 1e-9);
        t.upgradeLevels.put("su_deposu", 1);
        assertEquals("su deposu bir kademe karşılar", 1, new GameEngine(1, t).getUtilityStatus().water);
    }

    @Test public void unpaidRentEscalatesToEvictionAndPayingReopensTheShop() {
        SaveData s = day(12);
        s.stars = 4.0;
        Finance.issue(s, BillType.KIRA, 3000, 7); // son 9: gün 12'de 3 gün gecikme = ihtarname
        GameEngine e = new GameEngine(1, s);
        e.setSpawning(false);
        e.startDay();
        assertEquals(2, e.getUtilityStatus().rent);
        assertEquals("ihtarname: yıldız düşer", 3.85, s.stars, 1e-9);
        assertEquals(0.9, e.getUtilityStatus().arrivalMult(), 1e-9);
        // 6+ gün: tahliye, dükkân açılamaz
        SaveData t = day(15);
        Bill rent = Finance.issue(t, BillType.KIRA, 3000, 7);
        int money = t.money;
        GameEngine f = new GameEngine(1, t);
        final List<String> said = new ArrayList<>();
        f.addListener(new Narrator(f, new Narrator.Output() { @Override public void say(String x, boolean u) { said.add(x); } }));
        assertFalse(f.startDay());
        assertEquals(GameEngine.State.FAILED, f.getState());
        assertEquals("tahliye", f.getSummary().failReason);
        assertEquals("para korunur", money, t.money);
        assertEquals(15, t.day);
        assertTrue(said.toString(), said.get(said.size() - 1).contains("kira 6 gündür ödenmedi"));
        // hazırlık da açılamaz; tick de günü başlatmaz
        GameEngine g = new GameEngine(1, t);
        assertFalse(g.beginPrep());
        assertEquals(GameEngine.State.FAILED, g.getState());
        // kirayı öde: dükkân açılır
        assertEquals(Finance.PayResult.OK, Finance.pay(t, rent.id));
        GameEngine h = new GameEngine(1, t);
        assertTrue(h.startDay());
        assertFalse(h.getUtilityStatus().evicted());
    }

    @Test public void lateTaxIsSeizedWithFine() {
        SaveData s = day(15);
        s.money = 10000;
        Bill tax = Finance.issue(s, BillType.VERGI, 1000, 10); // son 12: gün 15'te 3 gün
        finishEmptyDay(s);
        assertNotNull("3 gün gecikmede haciz yok", Finance.bill(s, tax.id));
        SaveData t = day(16);
        t.money = 10000;
        Bill tax2 = Finance.issue(t, BillType.VERGI, 1000, 10); // gün 16: 4 gün = haciz
        int credit = t.creditScore;
        int due = (int) Math.round(tax2.amountDue(16) * 1.2);
        GameEngine e = finishEmptyDay(t);
        assertEquals(due, e.getSummary().seized);
        assertNull(Finance.bill(t, tax2.id));
        assertTrue(t.creditScore <= credit - 10);
        assertTrue(new Narrator(e).dayFinished(e.getSummary()).contains("haciz " + due + " lira"));
    }

    @Test public void failedDayRestoresBillsPaidDuringTheDay() {
        SaveData s = day(9);
        s.stars = 1.05;
        Bill b = Finance.issue(s, BillType.ELEKTRIK, 300, 7);
        int money = s.money;
        GameEngine e = new GameEngine(1, s);
        e.setSpawning(false);
        e.startDay();
        assertEquals(Finance.PayResult.OK, e.payBill(b.id));
        assertTrue(s.bills.isEmpty());
        Order o = e.spawnOrder(Collections.singletonList("ekmek_arasi"), NONE);
        e.startStep(o.id, Station.PAKET.id); // kilitli: ceza yok
        e.startStep(o.id, Station.TATLI.id); // yanlış istasyon −0,02 → < 1 → başarısız
        e.startStep(o.id, Station.TATLI.id);
        e.startStep(o.id, Station.TATLI.id);
        assertEquals(GameEngine.State.FAILED, e.getState());
        assertEquals(money, s.money);
        assertEquals("gün başına döner: fatura yine açık", 1, s.bills.size());
    }

    @Test public void narratorReadsBillsWagesAndDayEndFinance() {
        SaveData s = day(7);
        s.upgradeLevels.put("cirak", 1);
        Finance.issue(s, BillType.ELEKTRIK, 315, 3); // son 5: gecikmiş
        GameEngine e = new GameEngine(2, s);
        final List<String> said = new ArrayList<>();
        e.addListener(new Narrator(e, new Narrator.Output() { @Override public void say(String x, boolean u) { said.add(x); } }));
        new TestBot(0.8f, true, true).playDay(e);
        String end = said.get(said.size() - 1);
        assertTrue(end, end.contains("çırak maaşı " + Finance.dailyWage(1, 7) + " lira"));
        assertTrue(end, end.contains("Yeni fatura: kira " + Finance.billAmount(s, BillType.KIRA, 7) + " lira, son ödeme 9. gün."));
        assertTrue(end, end.contains("Açık fatura borcu"));
        assertTrue(end, end.contains("gecikmiş fatura: 1"));
        boolean warned = false;
        for (String t : said) if (t.startsWith("Elektrik faturası 2 gündür ödenmedi")) warned = true;
        assertTrue(said.toString(), warned);
        assertTrue(Narrator.billsText(s).startsWith("2 açık fatura, toplam "));
        assertEquals("Açık fatura yok.", Narrator.billsText(SaveData.newGame()));
        Bill k = Finance.openBills(s).get(1);
        assertEquals("Kira " + k.amount + " lira, son ödeme 9. gün, 1 gün kaldı.", Narrator.billText(k, 8));
        assertEquals("Kira " + k.amount + " lira, son ödeme bugün.", Narrator.billText(k, 9));
        assertTrue(Narrator.billText(k, 11).contains("2 gün gecikti, borç "));
        assertEquals("Çırak maaşı günlük " + Finance.dailyWage(1, s.day) + " lira.", Narrator.wageText(s));
        assertTrue(new Narrator(e).financeStatusText().isEmpty() || new Narrator(e).financeStatusText().contains("Açık fatura"));
    }
}
