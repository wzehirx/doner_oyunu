package com.doner.oyunu.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v2.5 dükkân giderleri: çırak maaşı, faturalar (elektrik, doğalgaz, su, kira, vergi), gün sonu hesabı ve kredi notu.
 * Saf, deterministik; {@link SaveData} üzerinde çalışır (Market/gün sonu ekranı ile motor aynı kuralları kullanır).
 * Gün içinde ödeme için {@link GameEngine#payBill} (kesinti o an kalkar).
 */
public final class Finance {
    private Finance() {}

    /** Son ödeme günü = kesildiği gün + DUE_DAYS. */
    public static final int DUE_DAYS = 2;
    /** Her gecikme günü için gecikme bedeli oranı. */
    public static final double LATE_FEE_RATE = 0.03;
    /** Kira bu kadar gün gecikince tahliye: dükkân açılamaz. */
    public static final int EVICTION_DAYS = 6;
    /** Vergi oranı: son vergi döneminin satış + bahşişinin %5'i. */
    public static final double TAX_RATE = 0.05;
    /** Haciz cezası (vergi 4+ gün gecikince borç + %20 kasadan alınır). */
    public static final double SEIZURE_FINE = 0.20;
    /** Çırak seviyesine göre temel günlük maaş (sigorta dahil); gün başına %1 artar. */
    static final int[] WAGE = {0, 250, 550, 900, 1250, 1650};
    public static final int CREDIT_START = 60;
    /** Ödenmemiş fatura yokken kredi notunun en çok yükseldiği değer. */
    public static final int CREDIT_MAX = 100;

    public enum PayResult { OK, NOT_ENOUGH_MONEY, UNKNOWN, NOTHING_DUE }

    // ------------------------------------------------------------ maaş
    /** Çırak seviyesinin {@code day} günündeki günlük maaşı (TL). */
    public static int dailyWage(int level, int day) {
        int lv = Math.max(0, Math.min(WAGE.length - 1, level));
        return (int) Math.round(WAGE[lv] * (1 + 0.01 * (Math.max(1, day) - 1)));
    }

    /** Maaş borcunu öder (para yetmezse ödenebildiği kadar değil, hiç ödenmez). */
    public static PayResult payWages(SaveData save) {
        if (save.wageDebt <= 0) return PayResult.NOTHING_DUE;
        if (save.money < save.wageDebt) return PayResult.NOT_ENOUGH_MONEY;
        save.money -= save.wageDebt;
        save.wageDebt = 0;
        return PayResult.OK;
    }

    // ------------------------------------------------------------ fatura tutarları
    /** Elektrik tüketen yükseltmelerin seviye toplamı. */
    public static int load(SaveData save, BillType type) {
        int n = 0;
        for (Upgrade u : Catalog.upgrades()) if (u.raises(type)) n += save.level(u.id);
        return n;
    }

    /**
     * Gün {@code day} sonunda kesilecek {@code type} faturasının tutarı (TL). Vergi: son dönemin satış + bahşişinin %5'i ({@link #TAX_RATE})
     * ({@link SaveData#salesSinceTax}).
     */
    public static int billAmount(SaveData save, BillType type, int day) {
        int g = Math.max(1, day) - 1;
        switch (type) {
            case ELEKTRIK: return (int) Math.round(7 * (30 + 1.5 * g + 10 * load(save, BillType.ELEKTRIK)));
            case DOGALGAZ: return (int) Math.round(7 * (25 + 1.5 * g + 12 * load(save, BillType.DOGALGAZ)));
            case SU: return (int) Math.round(7 * (15 + 0.8 * g + 6 * load(save, BillType.SU)));
            case KIRA: return 7 * weeklyRentBase(day);
            case VERGI: return (int) Math.round(save.salesSinceTax * TAX_RATE);
            default: return 0;
        }
    }

    /** Haftalık kiranın günlük karşılığı: 200 + 20·(gün−1) (v2.0 günlük kirası 300 + 30·(gün−1) idi; faturalar ayrıldı). */
    public static int weeklyRentBase(int day) { return 200 + 20 * (Math.max(1, day) - 1); }

    // ------------------------------------------------------------ sorgular
    /** Açık faturalar: önce en çok geciken / son ödeme günü en yakın, sonra kesilme sırası. */
    public static List<Bill> openBills(SaveData save) {
        List<Bill> l = new ArrayList<>(save.bills);
        Collections.sort(l, new Comparator<Bill>() {
            @Override public int compare(Bill a, Bill b) {
                int c = Integer.compare(a.dueDay, b.dueDay);
                return c != 0 ? c : Integer.compare(a.id, b.id);
            }
        });
        return l;
    }

    public static Bill bill(SaveData save, int billId) {
        for (Bill b : save.bills) if (b.id == billId) return b;
        return null;
    }

    /** Açık faturaların bugünkü toplam borcu (gecikme bedeli dahil). */
    public static int totalDue(SaveData save) {
        int t = 0;
        for (Bill b : save.bills) t += b.amountDue(save.day);
        return t;
    }

    /** {@code day} itibarıyla {@code days} gün içinde son ödeme günü gelen (ya da geçmiş) faturaların toplamı. */
    public static int dueWithin(SaveData save, int days) {
        int t = 0;
        for (Bill b : save.bills) if (b.daysLeft(save.day) <= days) t += b.amountDue(save.day);
        return t;
    }

    /** Gecikmiş fatura sayısı (bugün açılırsa). */
    public static int lateCount(SaveData save) {
        int n = 0;
        for (Bill b : save.bills) if (b.daysLate(save.day) > 0) n++;
        return n;
    }

    /**
     * Önümüzdeki {@code days} günde (bugün dahil, gün sonlarında) kesilecek faturaların TAHMİNİ (id = -1; vergi bugünkü
     * birikimle). UI "Yaklaşan faturalar" ve botlar için.
     */
    public static List<Bill> forecast(SaveData save, int days) {
        List<Bill> out = new ArrayList<>();
        for (int d = save.day; d < save.day + days; d++) {
            for (BillType t : BillType.values()) {
                if (!t.issuedOn(d)) continue;
                int amt = billAmount(save, t, d);
                if (amt > 0) out.add(new Bill(-1, t, amt, d, d + DUE_DAYS, 0));
            }
        }
        return out;
    }

    /**
     * Senaryo/öğretici/test: verilen türde fatura keser (gün {@code issuedDay} sonu, son ödeme +2). Oyunda faturaları
     * yalnızca gün sonu ({@link #endOfDay}) keser.
     */
    public static Bill issue(SaveData save, BillType type, int amount, int issuedDay) {
        Bill b = new Bill(save.nextFinanceId++, type, Math.max(1, amount), issuedDay, issuedDay + DUE_DAYS, 0);
        save.bills.add(b);
        return b;
    }

    // ------------------------------------------------------------ ödeme
    /** Faturayı tamamen öder (bugünkü tutar). Zamanında ödenirse kredi notu +2. */
    public static PayResult pay(SaveData save, int billId) {
        Bill b = bill(save, billId);
        if (b == null) return PayResult.UNKNOWN;
        int due = b.amountDue(save.day);
        if (save.money < due) return PayResult.NOT_ENOUGH_MONEY;
        save.money -= due;
        save.bills.remove(b);
        if (b.daysLate(save.day) == 0) adjustCredit(save, 2);
        return PayResult.OK;
    }

    /**
     * Parası yettiği kadar faturayı öder (önce en çok geciken / son günü en yakın). {@code keep}: kasada bırakılacak en az
     * para (ör. malzeme için). Ödenen fatura sayısı.
     */
    public static int payAll(SaveData save, int keep) {
        int n = 0;
        for (Bill b : openBills(save)) {
            if (save.money - b.amountDue(save.day) < keep) continue;
            if (pay(save, b.id) == PayResult.OK) n++;
        }
        return n;
    }

    /**
     * Faturayı bankadan kredi alarak öder (kredi tutarı = fatura tutarı, 100'e yuvarlı). Kira için limit aranmaz; v2.6: limit
     * dışı açık kira kredisi en çok {@link Bank#MAX_RENT_LOANS} (aşılırsa TOO_MANY_LOANS).
     */
    public static Bank.Result payWithLoan(SaveData save, int billId, int term) {
        Bill b = bill(save, billId);
        if (b == null) return Bank.Result.UNKNOWN;
        int due = b.amountDue(save.day);
        int amount = Math.max(Bank.MIN_LOAN, ((due + Bank.STEP - 1) / Bank.STEP) * Bank.STEP);
        Bank.Result r = Bank.takeLoan(save, amount, term, b.type == BillType.KIRA);
        if (r != Bank.Result.OK) return r;
        pay(save, billId);
        return Bank.Result.OK;
    }

    // ------------------------------------------------------------ kredi notu
    static void adjustCredit(SaveData save, int delta) {
        save.creditScore = Math.max(0, Math.min(CREDIT_MAX, save.creditScore + delta));
    }

    // ------------------------------------------------------------ gün sonu
    /**
     * Gün {@code day} sonunda: vergi birikimi, maaş (çırak geldiyse), kredi taksitleri, vergi haczi, kredi notu, yeni
     * faturalar. Sonuçlar {@code s}'ye yazılır. {@code apprenticeWorked}: bugün çalışan çırak seviyesi (gelmediyse 0).
     */
    static void endOfDay(SaveData save, int day, DaySummary s, int sales, int apprenticeWorked) {
        save.salesSinceTax += Math.max(0, sales);
        // 1) maaş: önce birikmiş borç, sonra bugünkü maaş
        int wage = dailyWage(apprenticeWorked, day);
        s.wagesDue = wage;
        int owe = save.wageDebt + wage;
        int pay = Math.min(owe, Math.max(0, save.money));
        save.money -= pay;
        save.wageDebt = owe - pay;
        s.wagesPaid = pay;
        s.wageDebt = save.wageDebt;
        // 2) kredi taksitleri
        int score = 0;
        boolean anyPaid = false;
        for (Loan l : new ArrayList<>(save.loans)) {
            if (l.installmentsLeft > 0) {
                int inst = Math.min(l.installment(), l.remaining - l.dueNow);
                if (inst > 0) { l.dueNow += inst; s.installmentsDue += inst; }
                l.installmentsLeft--;
            } else if (l.dueNow < l.remaining) {
                l.dueNow = l.remaining; // vade doldu: kalan hepsi vadeli
            }
            int want = l.fees + l.dueNow;
            int p = Math.min(want, Math.max(0, save.money));
            save.money -= p;
            int toFees = Math.min(p, l.fees);
            l.fees -= toFees;
            int toDue = p - toFees;
            l.dueNow -= toDue;
            l.remaining -= toDue;
            s.installmentsPaid += p;
            if (l.dueNow > 0) {
                l.missedStreak++;
                l.missedTotal++;
                l.fees += (int) Math.round(l.dueNow * Bank.MISSED_FEE);
                s.installmentsMissed++;
                score -= 5;
            } else if (want > 0) {
                l.missedStreak = 0;
                anyPaid = true;
            }
            if (l.isClosed()) save.loans.remove(l);
        }
        if (anyPaid && s.installmentsMissed == 0) score += 1;
        // 2b) v2.6 otomatik fatura ödemesi: son günü bugün olan ya da geciken faturalar, kasa (stok payı bırakarak) yetiyorsa
        if (save.autoPayBills) {
            for (Bill b : autoPay(save, day, true)) { s.autoPaid.add(b); s.autoPaidTotal += b.paid; }
        }
        // 3) vergi haczi (4+ gün gecikmiş vergi): borç + %20 kasadan alınır
        for (Bill b : new ArrayList<>(save.bills)) {
            if (b.type != BillType.VERGI || b.level(day) < 2) continue;
            int due = (int) Math.round(b.amountDue(day) * (1 + SEIZURE_FINE));
            int take = Math.min(due, Math.max(0, save.money));
            if (take <= 0) continue;
            save.money -= take;
            s.seized += take;
            // cezanın oranı kadar faturadan düşülür; tamamı alındıysa fatura kapanır
            int toBill = (int) Math.round(take / (1 + SEIZURE_FINE));
            b.paid += toBill;
            if (take >= due || b.amountDue(day) <= 0) save.bills.remove(b);
            score -= 10;
        }
        // 4) kredi notu: geciken her fatura −2, maaş borcu −1
        for (Bill b : save.bills) if (b.daysLate(day) > 0) score -= 2;
        if (save.wageDebt > 0) score -= 1;
        adjustCredit(save, score);
        // 5) yeni faturalar (vergi: birikimden)
        for (BillType t : BillType.values()) {
            if (!t.issuedOn(day)) continue;
            int amt = billAmount(save, t, day);
            if (t == BillType.VERGI) save.salesSinceTax = 0;
            if (amt <= 0) continue;
            Bill b = new Bill(save.nextFinanceId++, t, amt, day, day + DUE_DAYS, 0);
            save.bills.add(b);
            s.newBills.add(b);
        }
        s.creditScore = save.creditScore;
        int total = 0, late = 0;
        for (Bill b : save.bills) {
            total += b.amountDue(day + 1);
            if (b.daysLate(day + 1) > 0) late++;
        }
        s.billsDue = total;
        s.billsLate = late;
        s.loanDebt = Bank.totalDebt(save);
    }

    // ------------------------------------------------------------ v2.6 otomatik ödeme
    /**
     * Otomatik fatura ödemesi ({@link SaveData#autoPayBills}): {@code includeDueToday} true ise son ödeme günü {@code day}
     * olan ve geciken, false ise yalnızca geciken faturalar (önce en çok geciken) ödenir; kasada önerilen stok payı
     * ({@link #autoPayReserve}) kalmayacaksa o fatura atlanır (normal kesinti/zorluk akışı sürer). Ödenen faturaların
     * kopyaları döner ({@link Bill#paid} = ödenen tutar). Ayar kapalı olsa da çağrılabilir (çağıran denetler).
     */
    public static List<Bill> autoPay(SaveData save, int day, boolean includeDueToday) {
        List<Bill> out = new ArrayList<>();
        int keep = -1;
        for (Bill b : openBills(save)) {
            if (includeDueToday ? b.dueDay > day : b.dueDay >= day) continue;
            if (keep < 0) keep = autoPayReserve(save, day + (includeDueToday ? 1 : 0));
            int due = b.amountDue(day);
            if (due <= 0 || save.money - due < keep) continue;
            save.money -= due;
            save.bills.remove(b);
            if (b.daysLate(day) == 0) adjustCredit(save, 2);
            out.add(new Bill(b.id, b.type, b.amount, b.issuedDay, b.dueDay, due));
        }
        return out;
    }

    /**
     * Brifing/gün açılışı otomatik ödemesi: ayar açıksa bugün ({@code save.day}) geciken faturaları kasa yetiyorsa öder
     * (kesinti o gün hiç başlamaz). Ayar kapalıysa boş liste. Motor gün açılışında kendisi de çağırır (çift ödeme olmaz).
     */
    public static List<Bill> autoPayAtOpen(SaveData save) {
        if (!save.autoPayBills) return new ArrayList<>();
        return autoPay(save, save.day, false);
    }

    /** Otomatik ödemenin kasada bıraktığı pay: {@code day} gününün önerilen stok alımının maliyeti (stok tamsa 0). */
    public static int autoPayReserve(SaveData save, int day) {
        int old = save.day;
        try {
            save.day = Math.max(1, day);
            return Shop.recommendedCost(save);
        } finally {
            save.day = old;
        }
    }

    // ------------------------------------------------------------ v2.6 iflas
    /** Art arda bu kadar başarısız günden sonra kurtarma yolu da yoksa iflas. */
    public static final int FAIL_LOOP_DAYS = 3;

    /** İflas nedeni (NONE: iflas yok). UI özet/brifingde "Dükkân kapandı" + "Yeni oyun" gösterir. */
    public enum Bankruptcy {
        NONE,
        /** Tahliye kararı var, kira ödenemiyor ve kira kredisi de alınamıyor. */
        EVICTED,
        /** Stokla tek ürün bile yapılamıyor, kasa en ucuz ürünün malzemesine yetmiyor, kredi/acil destek yok. */
        NO_MEANS,
        /** {@link #FAIL_LOOP_DAYS} gün art arda başarısız, kasa önerilen stoğun yarısına yetmiyor, kredi/acil destek yok. */
        FAIL_LOOP
    }

    /** Kasa/stok ile en az bir ürün yapılabilir mi (eksik malzeme market fiyatıyla alınabiliyorsa da evet). */
    public static boolean canOperate(SaveData save) {
        UtilityStatus u = UtilityStatus.of(save, save.day);
        for (Product p : Catalog.productsForDay(save.day)) {
            if (!u.productAvailable(p)) continue;
            Map<String, Integer> need = new LinkedHashMap<>();
            for (Step st : p.steps) for (Map.Entry<String, Integer> e : st.needs.entrySet()) {
                Integer o = need.get(e.getKey());
                need.put(e.getKey(), (o == null ? 0 : o) + e.getValue());
            }
            int cost = 0;
            for (Map.Entry<String, Integer> e : need.entrySet()) {
                int have = save.inventory.get(e.getKey());
                if (e.getKey().equals(Catalog.AYRAN)) have += save.readyDrinks;
                int lack = Math.max(0, e.getValue() - have);
                if (lack > 0) cost += Catalog.ingredient(e.getKey()).price * lack;
            }
            if (cost <= save.money) return true;
        }
        return false;
    }

    /** Kurtarma yolu var mı: normal kredi (en az {@link Bank#MIN_LOAN}) ya da acil destek alınabilir. */
    public static boolean canRescue(SaveData save) {
        return Bank.available(save) >= Bank.MIN_LOAN || Bank.emergencyAidAvailable(save);
    }

    /** Açılacak gün ({@code save.day}) için iflas denetimi (saf; durum değiştirmez). */
    public static Bankruptcy bankruptcy(SaveData save) {
        if (UtilityStatus.of(save, save.day).evicted()) {
            int rent = 0, biggest = 0;
            for (Bill b : save.bills) if (b.type == BillType.KIRA && b.level(save.day) >= 3) {
                int d = b.amountDue(save.day);
                rent += d;
                biggest = Math.max(biggest, d);
            }
            if (save.money < rent && !Bank.rentLoanAvailable(save, biggest)) return Bankruptcy.EVICTED;
        }
        boolean rescue = canRescue(save);
        if (!rescue && !canOperate(save)) return Bankruptcy.NO_MEANS;
        if (!rescue && save.failStreak >= FAIL_LOOP_DAYS && save.money < Shop.recommendedCost(save) / 2) return Bankruptcy.FAIL_LOOP;
        return Bankruptcy.NONE;
    }

    // ------------------------------------------------------------ uyarılar
    /**
     * Gün {@code day} açılırken söylenecek kısa uyarılar (önce en ağır): kesinti/tahliye durumu, son günü gelen fatura,
     * maaş borcu, gecikmiş taksit. Uyarı yoksa boş liste. UI brifingde de kullanır.
     */
    public static List<String> notices(SaveData save, int day) {
        List<String> out = new ArrayList<>();
        UtilityStatus u = UtilityStatus.of(save, day);
        for (Bill b : openBills(save)) {
            int late = b.daysLate(day), lv = b.level(day);
            String amt = Narrator.fmtMoney(b.amountDue(day));
            if (late > 0) out.add(lateText(b, lv, late, amt, u));
            else if (b.daysLeft(day) == 0) out.add(b.type.displayName + " " + amt + ", son ödeme günü bugün. Gün sonunda ödemezsen yarın "
                    + consequence(b.type) + ".");
            else if (b.daysLeft(day) == 1) out.add(b.type.displayName + " " + amt + ", son ödeme yarın.");
        }
        if (save.wageDebt > 0) out.add("Çırak maaşını alamadı, bugün gelmedi. Maaş borcu " + Narrator.fmtMoney(save.wageDebt) + ".");
        for (Loan l : save.loans) {
            if (l.dueNow > 0) out.add("Kredi taksiti gecikti: " + Narrator.fmtMoney(l.dueNow + l.fees) + ". Gün sonunda kasadan alınacak.");
        }
        return out;
    }

    /** Ödenmezse ilk kademede ne olur: "elektrik kesilir". */
    static String consequence(BillType t) {
        switch (t) {
            case ELEKTRIK: return "elektrik kesilir";
            case DOGALGAZ: return "doğalgaz kısılır";
            case SU: return "su kesilir";
            case KIRA: return "ev sahibi ihtar çeker";
            default: return "gecikme bedeli eklenir";
        }
    }

    private static String lateText(Bill b, int lv, int late, String amt, UtilityStatus u) {
        String head = b.type.displayName + " " + late + " gündür ödenmedi, borç " + amt + ". ";
        switch (b.type) {
            case ELEKTRIK:
                if (u.electric <= 0) return head + "Jeneratör çalışıyor, kesinti yok.";
                return head + (u.electric >= 2 ? "Elektrik tamamen kesik: ekmek ısıtıcı tek yer, ekmek, içecek ve döner yavaş; gün sonunda dönerin bir kısmı bozulur."
                        : "Elektrik kısmen kesik: ekmek ısıtıcı ve içecek dolabı yavaş.") + (lv < 2 ? " " + (3 - late) + " gün sonra tam kesinti." : "") + " Ödersen hemen düzelir.";
            case DOGALGAZ:
                return head + (lv >= 2 ? "Doğalgaz kesik: Ocak kapalı, ocaklı ürünler menüden çıktı."
                        : "Doğalgaz kısık: Ocak tek göz ve yavaş. " + (3 - late) + " gün sonra Ocak kapanır.") + " Ödersen hemen düzelir.";
            case SU:
                if (u.water <= 0) return head + "Su deposu kullanılıyor, kesinti yok.";
                return head + (u.water >= 2 ? "Su kesik: tepsi hazırlanamaz, çay ve çorba menüden çıktı, müşteriler hijyenden şikâyetçi."
                        : "Su kesik: salata ve sos tepsisi hazırlanamaz, tezgâh yavaş.") + (lv < 2 ? " " + (3 - late) + " gün sonra çay ve çorba da çıkar." : "") + " Ödersen hemen düzelir.";
            case KIRA:
                if (lv >= 3) return head + "Tahliye kararı: kirayı ödemeden dükkân açılamaz. Banka kira kredisi verir.";
                if (lv >= 2) return head + "İhtarname geldi: her gün yıldız düşer, müşteri azalır. " + (EVICTION_DAYS - late) + " gün sonra dükkân açılamaz.";
                return head + "Ev sahibi ihtar çekti. " + (3 - late) + " gün sonra ihtarname gelir.";
            default:
                if (lv >= 2) return head + "Haciz: gün sonunda borç ve yüzde 20 ceza kasadan alınır.";
                return head + "Gecikme bedeli işliyor, kredi notun düşüyor. " + (4 - late) + " gün sonra haciz.";
        }
    }
}
