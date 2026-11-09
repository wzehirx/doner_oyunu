package com.doner.oyunu;

import com.doner.oyunu.engine.Bank;
import com.doner.oyunu.engine.Bill;
import com.doner.oyunu.engine.Catalog;
import com.doner.oyunu.engine.Finance;
import com.doner.oyunu.engine.Loan;
import com.doner.oyunu.engine.Pricing;
import com.doner.oyunu.engine.DaySummary;
import com.doner.oyunu.engine.FlowMode;
import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.SaveData;
import com.doner.oyunu.engine.Shop;
import com.doner.oyunu.engine.Upgrade;

import java.util.ArrayList;
import java.util.List;

/**
 * 1..N günlük bot kampanyası; gün aralarında market alışverişi yapar. v2.5: "akıllı" bot fiyat belirler
 * ({@link #pricePercent} sabit ya da {@link #smartPrice}), faturaları ve maaş borcunu öder, gerekirse krediyle öder,
 * stok parası yetmezse kredi alır, zengince krediyi erken kapatır. {@link #payBills}=false: hiç fatura ödemeyen bot.
 */
public final class Campaign {
    public static final String[] PRIORITY = {
            "cirak", "ekmek_isitici", "doner_makinesi", "buzdolabi", "tabela", "oto_dilimleyici",
            "klima", "muzik", "masa", "dekor", "ocak", "havalandirma", "sogutma", "ikinci_kasa", "jenerator", "su_deposu"};

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
    /** v2.5: faturaları öder (false: hiç ödemeyen bot). */
    public boolean payBills = true;
    /** v2.5: gerektiğinde kredi alır. */
    public boolean useLoans = true;
    /** v2.5: tüm ürünlere sabit fiyat yüzdesi (100 = referans). */
    public int pricePercent = Pricing.DEFAULT_PERCENT;
    /**
     * v2.5: akıllı fiyat (gün sonuna bakar): yıldız 4,3'ün altındaysa %5 indirir (en az 85); müşteri kaçırdıysa (kapasite
     * yetmiyor) ya da yıldız 4,9+ ise %5 artırır (en çok 125); diğer durumda korur.
     */
    public boolean smartPrice;
    /** v2.5 ölçütleri: alınan kredi sayısı/toplamı, ödenen fatura sayısı, krediyle ödenen fatura, kampanya boyu toplam fatura/maaş. */
    public int loansTaken, loanTotal, billsPaid, billsByLoan;
    public long billSpend, wageSpend;

    /** Varsayılan: Gerçekçi akış (v0.6 davranışı; eski denge testleri bu akışı ölçer). */
    public Campaign() { this(FlowMode.GERCEKCI); }

    public Campaign(FlowMode flow) {
        save.flowMode = flow;
        save.flowChosen = true;
    }

    /** timePressure=false: süresiz mod (v0.8): sayaç yok, sabır sabit, kota kadar müşteri. */
    public Campaign(FlowMode flow, boolean timePressure) {
        this(flow);
        save.timePressure = timePressure;
    }
    public final List<Integer> attemptsPerDay = new ArrayList<>();
    public int firstUnpassedDay = -1;
    /** Son oynanan günün motoru (hata ayıklama / takılma incelemesi için). */
    public GameEngine lastEngine;

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
                prices();
                finance();
                if (buyUpgrades) shop();
                else stock();
                GameEngine e = new GameEngine(seed * 1000 + d * 7 + attempt, save, v06Balance);
                TestBot bot = new TestBot(reaction, prep, careful);
                if (humanLike) bot.humanLike(seed * 31 + d * 7 + attempt);
                bot.playDay(e);
                lastEngine = e;
                taps += bot.taps;
                v04Taps += bot.v04Taps;
                delivered += bot.delivered;
                lost += e.getSummary().lost;
                results.add(e.getSummary());
                wageSpend += e.getSummary().wagesPaid;
                // süresiz modda günün bitmemesi (takılma) geçmek sayılmaz
                passed = !e.getSummary().failed && e.getState() == GameEngine.State.FINISHED;
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

    /** v2.5 servet: para + alınan yükseltmelerin fiyatı − kredi borcu. */
    public long netWorth() {
        long w = save.money - Bank.totalDebt(save);
        for (Upgrade u : Catalog.upgrades()) for (int k = 0; k < save.level(u.id); k++) w += u.prices[k];
        return w;
    }

    /** Toplam deneme sayısı (geçilen + başarısız). */
    public int attempts() { int n = 0; for (int a : attemptsPerDay) n += a; return n; }

    /** Başarısız deneme sayısı. */
    public int failedAttempts() { int n = 0; for (DaySummary d : results) if (d.failed) n++; return n; }

    /** Yükseltmeyi alınca günün önerilen stoğu hâlâ karşılanıyor mu (alım sonrası yeniden hesaplanır)? */
    private boolean affordsStockAfter(String id) {
        SaveData sim = save.copy();
        if (Shop.buyUpgrade(sim, id) != Shop.BuyResult.OK) return false;
        return sim.money >= Shop.recommendedCost(sim);
    }

    // ------------------------------------------------------------ v2.5 ekonomi
    private void prices() {
        if (smartPrice && !results.isEmpty()) {
            DaySummary d = results.get(results.size() - 1);
            int cur = Pricing.percent(save, "ekmek_arasi");
            if (save.stars < 4.3) cur = Math.max(85, cur - Pricing.STEP_PERCENT);
            else if (d.lost >= 2 || save.stars >= 4.9) cur = Math.min(125, cur + Pricing.STEP_PERCENT);
            Pricing.setAll(save, cur);
        } else if (!smartPrice) {
            Pricing.setAll(save, pricePercent);
        }
    }

    /** Yakın giderler: 2 gün içinde son günü gelen faturalar, 3 günlük tahmini faturalar, 2 günlük maaş, bugünkü taksit. */
    int upcomingCosts() {
        int t = Finance.dueWithin(save, 2) + save.wageDebt + Bank.dueToday(save);
        for (Bill b : Finance.forecast(save, 3)) t += b.amount;
        t += 2 * Finance.dailyWage(new com.doner.oyunu.engine.Upgrades(save).apprenticeLevel(), save.day);
        return t;
    }

    /** Maaş borcu, faturalar (önce geciken / son günü yakın), gerekirse kredi; zenginse krediyi erken kapatır. */
    void finance() {
        if (!payBills) return;
        Finance.payWages(save);
        int stock = Shop.recommendedCost(save);
        for (Bill b : Finance.openBills(save)) {
            int due = b.amountDue(save.day);
            boolean last = b.daysLeft(save.day) <= 0; // yarın gecikir
            if (save.money - due >= (last ? 0 : stock / 2)) {
                if (Finance.pay(save, b.id) == Finance.PayResult.OK) { billsPaid++; billSpend += due; }
            } else if (last && useLoans) {
                if (Finance.payWithLoan(save, b.id, 10) == Bank.Result.OK) { billsPaid++; billsByLoan++; loansTaken++; loanTotal += due; billSpend += due; }
            }
        }
        if (save.wageDebt > 0 && useLoans) borrow(save.wageDebt);
        Finance.payWages(save);
        // stok parası yetmiyorsa kredi
        if (useLoans && save.money < stock && save.day > 1) borrow(stock - save.money);
        // zenginse krediyi erken kapat
        for (Loan l : new ArrayList<>(save.loans)) {
            if (save.money - l.payoffAmount() > stock + upcomingCosts() + 3000) Bank.repay(save, l.id);
        }
    }

    private void borrow(int need) {
        int amount = Math.max(Bank.MIN_LOAN, ((need + Bank.STEP - 1) / Bank.STEP) * Bank.STEP);
        amount = Math.min(amount, Bank.available(save));
        if (amount >= Bank.MIN_LOAN && Bank.takeLoan(save, amount, 10) == Bank.Result.OK) { loansTaken++; loanTotal += amount; }
    }

    private void stock() {
        Shop.buyRecommended(save);
    }

    private void shop() {
        boolean bought = true;
        while (bought) {
            bought = false;
            int reserve = Shop.recommendedCost(save) + (payBills ? upcomingCosts() : 0);
            for (String id : PRIORITY) {
                Upgrade u = Catalog.upgrade(id);
                int lv = save.level(id);
                int price = u.priceForNext(lv);
                if (price < 0 || save.day < u.unlockDayForNext(lv)) continue;
                if (humanLike && java.util.Arrays.asList(CROWD).contains(id) && lastLost() > 1) continue;
                if (!save.timePressure && !affordsStockAfter(id)) continue; // süresiz modda stoksuz kalmak günü kilitler
                if (save.money - price >= reserve) {
                    if (Shop.buyUpgrade(save, id) == Shop.BuyResult.OK) { bought = true; break; }
                } else if (id.equals("cirak") && lv < 3) {
                    break; // çırak için biriktir (akıllı oyuncu önce çırağı alır; v2.5: 4–5. seviye kalfa/usta için biriktirmez)
                }
            }
        }
        Shop.buyRecommended(save);
    }
}
