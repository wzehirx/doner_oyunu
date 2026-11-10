package com.doner.oyunu.engine;

/** Gün sonu özeti. */
public final class DaySummary {
    public int day;
    public boolean failed;
    /** Gün bittiğinde süre baskısı açık mıydı (false: süresiz gün; kayıp müşteri olmaz). */
    public boolean timed = true;
    public int revenue;
    public int tips;
    public int emergencySpend;
    /**
     * ESKİ (v2.0 günlük kira). v2.5'te kira haftalık FATURADIR ({@link #newBills}); motor gün sonunda kira düşmez, bu alan
     * her zaman 0'dır (UI/derleme uyumu için durur).
     */
    @Deprecated public int rent;
    /** ESKİ: her zaman 0 (bkz. {@link #rent}). */
    @Deprecated public int rentDue;
    public int delivered;
    public int lost;
    public int wrongTaps;
    public double startStars;
    public double endStars;
    public int moneyAfter;
    /** Gün sonunda atılan hazır döner porsiyonu + tepsi kullanımı (israf). */
    public int wastedPortions;
    /** Atılan hazır stokun malzeme değeri (TL; hazırlanırken zaten harcanmıştı). */
    public int wastedCost;
    /** Hatalı malzemeli teslim sayısı / kusursuz malzemeli teslim sayısı. */
    public int mistakes;
    public int perfect;
    /** Boy hatası içeren teslim sayısı (mistakes sayısına da dahildir). */
    public int sizeMistakes;
    /** Biten hazırlık işi sayısı (oyuncu + çırak) ve çırağın bitirdiği sipariş adımı sayısı. */
    public int prepBatches;
    public int apprenticeTasks;
    /** Sonraki gün açılan yeni ürünler (adlar). */
    public String newProductsNext = "";
    /** Sonraki gün açılan yeni sos/salata malzemeleri (adlar). */
    public String newIngredientsNext = "";

    // ---- v2.5 gün sonu hesabı (Finance.endOfDay)
    /** Bugünkü çırak maaşı (çırak gelmediyse 0) ve kasadan ödenen (birikmiş borç dahil). */
    public int wagesDue, wagesPaid;
    /** Gün sonundaki maaş borcu (varsa çırak yarın gelmez). */
    public int wageDebt;
    /** Bugün vadesi gelen taksit, ödenen (gecikmiş + bedel dahil) ve kaçan kredi sayısı. */
    public int installmentsDue, installmentsPaid, installmentsMissed;
    /** Vergi haczi ile kasadan alınan. */
    public int seized;
    /** Bugün kesilen yeni faturalar. */
    public final java.util.List<Bill> newBills = new java.util.ArrayList<>();
    /** Gün sonundaki açık fatura borcu (yarın açılırsa, gecikme bedeli dahil) ve gecikmiş fatura sayısı. */
    public int billsDue, billsLate;
    /** Toplam kredi borcu ve kredi notu (gün sonunda). */
    public int loanDebt, creditScore;
    /** Gün başında bozulan hazır ayran (bardak) ve gün sonunda bozulan et stoğu (birim) ile değeri (elektrik kesintisi). */
    public int spoiledDrinks, spoiledStock, spoiledCost;
    /** Kira ihtarnamesinden düşen yıldız (gün başında). */
    public double rentStarPenalty;
    /** Gün başarısızlığının nedeni: null (yıldız) ya da "tahliye" (kira 6+ gün gecikti, dükkân açılamadı). */
    public String failReason;
    /** Fiyatı pahalı / uygun bulan müşteri sayısı (teslimde). */
    public int priceComplaints, priceCompliments;
    /** Çırağın teslim ettiği sipariş sayısı. */
    public int apprenticeDeliveries;

    // ---- v2.6
    /** Otomatik ödenen faturalar (gün açılışında + gün sonunda; {@link Bill#getPaid()} = ödenen tutar) ve toplamı. */
    public final java.util.List<Bill> autoPaid = new java.util.ArrayList<>();
    public int autoPaidTotal;
    /**
     * Süresiz günde yapılabilir iş, stok ve acil alım parası kalmadığı için gün erken kapandı (bekleyen müşteriler cezasız
     * gitti; {@link #stuckClosedOrders} sipariş).
     */
    public boolean stuckEnded;
    public int stuckClosedOrders;
    /** Gün sonu (başarılı ya da başarısız) iflas denetimi; NONE değilse UI "Dükkân kapandı" + yeniden başla gösterir. */
    public Finance.Bankruptcy bankruptcy = Finance.Bankruptcy.NONE;
    /** Gün sonundaki art arda başarısız gün sayısı. */
    public int failStreak;

    public boolean isBankrupt() { return bankruptcy != Finance.Bankruptcy.NONE; }

    /** Gün sonunda kasadan otomatik çıkan giderler: maaş + taksit + haciz (+ eski kira, 0). */
    public int expenses() { return rent + wagesPaid + installmentsPaid + seized; }

    /** Para yetmediği için ödenemeyen kira. */
    public int rentUnpaid() { return Math.max(0, rentDue - rent); }

    /** Günün net kârı: satış + bahşiş − acil alım − gün sonu giderleri (maaş, taksit, haciz). Faturalar ödendiği gün kasadan düşer. */
    public int profit() { return revenue + tips - emergencySpend - expenses(); }

    @Override
    public String toString() {
        return "Gün " + day + (failed ? " BAŞARISIZ" : "") + " gelir=" + revenue + " bahşiş=" + tips
                + " acil=" + emergencySpend + " maaş=" + wagesPaid + (wageDebt > 0 ? "(borç " + wageDebt + ")" : "") + " taksit=" + installmentsPaid
                + (seized > 0 ? " haciz=" + seized : "") + " fatura=" + billsDue + (billsLate > 0 ? "(geciken " + billsLate + ")" : "")
                + (failReason != null ? " neden=" + failReason : "") + " teslim=" + delivered + " kayıp=" + lost
                + (wastedPortions > 0 ? " israf=" + wastedPortions : "") + " yıldız=" + startStars + "->" + endStars + " para=" + moneyAfter;
    }
}
