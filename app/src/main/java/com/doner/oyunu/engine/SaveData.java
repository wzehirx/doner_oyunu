package com.doner.oyunu.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kalıcı oyun durumu + ayarlar. "anahtar=değer" düz metin olarak serileştirilir.
 * v2.5: kayıt DÖNEMİ ({@link #EPOCH}): dönemi eski (ya da dönemsiz) kayıt yüklenince İLERLEME sıfırlanır, AYARLAR
 * korunur ve {@link #progressResetNotice} bir kez bildirilir. İleride dönem artırılarak aynı mekanizma yeniden kullanılır.
 */
public final class SaveData {
    /** Bu sürümün içerik dönemi (v2.5 = 25). Kayıttaki {@code epoch} bundan küçükse ilerleme sıfırlanır. */
    public static final int EPOCH = 25;
    /** Yazılan kayıt biçimi sürümü (v2.5: 5). */
    public static final int FORMAT = 5;
    public static final int START_MONEY = 5000;
    public static final double START_STARS = 3.0;

    public int day = 1;
    public int bestDay = 1;
    public int money = START_MONEY;
    public double stars = START_STARS;
    public Inventory inventory = new Inventory();
    public final Map<String, Integer> upgradeLevels = new LinkedHashMap<>();

    // Ayarlar
    public double tempo = 1.0;          // 0.6 - 1.6: süre ve sabır ölçeği
    public boolean speechOn = true;
    public double speechRate = 1.0;
    public boolean vibration = true;
    public double musicVolume = 0.7;
    public double sfxVolume = 1.0;
    /** Dükkân ambiyansı ses düzeyi (v0.9; müzikten bağımsız, 0 = kapalı). Eski kayıtlarda yoksa varsayılan. */
    public static final double DEFAULT_AMBIENCE = 0.35;
    public double ambienceVolume = DEFAULT_AMBIENCE;
    public boolean tutorialDone = false;
    /** Çırak duyuruları (ayar). */
    public boolean apprenticeAnnounce = true;
    /** v2.7 Anlatım düzeyi ({@link NarrationLevel}); yalnızca TalkBack açıkken geçerli (kapalıyken her zaman Çok). */
    public NarrationLevel narrationLevel = NarrationLevel.DEFAULT;

    /** Oyun akışı (v0.7): Rahat / Dengeli / Gerçekçi. Eski kayıtlarda yoksa varsayılan (Dengeli). */
    public FlowMode flowMode = FlowMode.DEFAULT;
    /** Oyuncu oyun akışını bilerek seçti/onayladı mı (brifing ilk seferde sorar). */
    public boolean flowChosen = false;

    /**
     * Süre baskısı (v0.8): true (varsayılan; eski kayıtlar) = süreli gün, müşteri sabrı azalır. false = süresiz gün:
     * sayaç yok, sabır sabit, müşteri gitmez; kota kadar müşteri gelip tüm siparişler teslim edilince gün biter.
     */
    public boolean timePressure = true;

    /** Hazır (doldurulmuş) içecek stoğu; gün sonunda atılmaz, ertesi güne kalır. */
    public int readyDrinks = 0;

    // ---- v2.5
    /** Kaydın içerik dönemi (yeni nesne = bu sürüm). */
    public int epoch = EPOCH;
    /** Güncelleme nedeniyle ilerleme sıfırlandı: UI bir kez duyurup {@link #clearProgressResetNotice()} çağırır. */
    public boolean progressResetNotice = false;
    /** Ayar: çırak teslime hazır siparişi teslim etsin (varsayılan açık). */
    public boolean apprenticeDelivery = true;
    /** Ürün fiyat yüzdeleri (ürün id -> 50..150; yoksa 100). {@link Pricing}. */
    public final Map<String, Integer> prices = new LinkedHashMap<>();
    /** Açık faturalar. {@link Finance}. */
    public final List<Bill> bills = new ArrayList<>();
    /** Banka kredileri. {@link Bank}. */
    public final List<Loan> loans = new ArrayList<>();
    /** Kredi notu 0..100. */
    public int creditScore = Finance.CREDIT_START;
    /** Ödenemeyen çırak maaşı (varken çırak gelmez). */
    public int wageDebt = 0;
    /** Son vergi kesiminden beri satış + bahşiş. */
    public int salesSinceTax = 0;
    /** Fatura/kredi kimlik sayacı. */
    public int nextFinanceId = 1;

    // ---- v2.6
    /** Ayar: faturaları otomatik öde (varsayılan açık; eski kayıtlarda açık). {@link Finance#autoPay}. */
    public boolean autoPayBills = true;
    /** Art arda başarısız gün sayısı (başarılı gün sonunda 0). {@link Finance#bankruptcy}. */
    public int failStreak = 0;

    /** Sıfırlama bildirimi gösterildi. */
    public void clearProgressResetNotice() { progressResetNotice = false; }

    /** Yeni oyun: 1. gün için önerilen stok hazır gelir. */
    public static SaveData newGame() {
        SaveData s = new SaveData();
        Shop.grantRecommended(s);
        return s;
    }

    public int level(String upgradeId) {
        Integer v = upgradeLevels.get(upgradeId);
        return v == null ? 0 : v;
    }

    public void setTempo(double t) { tempo = Math.max(0.6, Math.min(1.6, t)); }

    public SaveData copy() {
        SaveData c = parse(serialize());
        return c;
    }

    /**
     * Yeni oyun (ilerleme sıfır), AYARLAR {@code old}'dan korunur: tempo, konuşma ve hızı, titreşim, müzik/efekt/ambiyans
     * sesi, öğretici, çırak duyuruları, çırak teslimi, oyun akışı, süre baskısı, v2.6 otomatik fatura ödemesi. Dönem sıfırlaması ve "Yeni oyun" bunu kullanır.
     */
    public static SaveData newGameKeepingSettings(SaveData old) {
        SaveData s = newGame();
        if (old != null) old.copySettingsTo(s);
        return s;
    }

    /** Yalnızca ayarları kopyalar (ilerleme alanlarına dokunmaz). */
    public void copySettingsTo(SaveData s) {
        s.tempo = tempo;
        s.speechOn = speechOn;
        s.speechRate = speechRate;
        s.vibration = vibration;
        s.musicVolume = musicVolume;
        s.sfxVolume = sfxVolume;
        s.ambienceVolume = ambienceVolume;
        s.tutorialDone = tutorialDone;
        s.apprenticeAnnounce = apprenticeAnnounce;
        s.narrationLevel = narrationLevel;
        s.apprenticeDelivery = apprenticeDelivery;
        s.flowMode = flowMode;
        s.flowChosen = flowChosen;
        s.timePressure = timePressure;
        s.autoPayBills = autoPayBills;
    }

    /**
     * v2.6 iflas sonrası yeniden başla: yeni oyun, ayarlar korunur ({@link #newGameKeepingSettings}); en iyi gün rekoru
     * ({@link #bestDay}) da korunur.
     */
    public static SaveData restartAfterBankruptcy(SaveData old) {
        SaveData s = newGameKeepingSettings(old);
        if (old != null) s.bestDay = Math.max(s.bestDay, old.bestDay);
        return s;
    }

    /** Başarısız günde para/stokla birlikte geri alınan finans durumu (gün içi fatura ödemesi dahil). */
    void restoreFinanceFrom(SaveData snap) {
        bills.clear();
        for (Bill b : snap.bills) bills.add(new Bill(b.id, b.type, b.amount, b.issuedDay, b.dueDay, b.paid));
        loans.clear();
        for (Loan l : snap.loans) loans.add(Loan.parse(l.id, l.serialize()));
        creditScore = snap.creditScore;
        wageDebt = snap.wageDebt;
        salesSinceTax = snap.salesSinceTax;
        nextFinanceId = snap.nextFinanceId;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("v=").append(FORMAT).append('\n'); // v2.5: v=5; eski biçimler okunur (dönemi eskiyse ilerleme sıfırlanır)
        sb.append("epoch=").append(epoch).append('\n');
        sb.append("day=").append(day).append('\n');
        sb.append("bestDay=").append(bestDay).append('\n');
        sb.append("money=").append(money).append('\n');
        sb.append("stars=").append(stars).append('\n');
        for (Map.Entry<String, Integer> e : inventory.asMap().entrySet()) {
            sb.append("stock.").append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        for (Map.Entry<String, Integer> e : upgradeLevels.entrySet()) {
            if (e.getValue() > 0) sb.append("up.").append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        sb.append("tempo=").append(tempo).append('\n');
        sb.append("speech=").append(speechOn ? 1 : 0).append('\n');
        sb.append("speechRate=").append(speechRate).append('\n');
        sb.append("vibration=").append(vibration ? 1 : 0).append('\n');
        sb.append("music=").append(musicVolume).append('\n');
        sb.append("sfx=").append(sfxVolume).append('\n');
        sb.append("ambience=").append(ambienceVolume).append('\n');
        sb.append("tutorial=").append(tutorialDone ? 1 : 0).append('\n');
        sb.append("apprenticeAnnounce=").append(apprenticeAnnounce ? 1 : 0).append('\n');
        sb.append("narration=").append(narrationLevel.id).append('\n');
        sb.append("readyDrinks=").append(readyDrinks).append('\n');
        sb.append("flow=").append(flowMode.id).append('\n');
        sb.append("flowChosen=").append(flowChosen ? 1 : 0).append('\n');
        sb.append("timer=").append(timePressure ? 1 : 0).append('\n');
        sb.append("apprenticeDelivery=").append(apprenticeDelivery ? 1 : 0).append('\n');
        if (progressResetNotice) sb.append("resetNotice=1\n");
        for (Map.Entry<String, Integer> e : prices.entrySet()) sb.append("price.").append(e.getKey()).append('=').append(e.getValue()).append('\n');
        for (Bill b : bills) sb.append("bill.").append(b.id).append('=').append(b.serialize()).append('\n');
        for (Loan l : loans) sb.append("loan.").append(l.id).append('=').append(l.serialize()).append('\n');
        sb.append("credit=").append(creditScore).append('\n');
        sb.append("wageDebt=").append(wageDebt).append('\n');
        sb.append("salesTax=").append(salesSinceTax).append('\n');
        sb.append("finId=").append(nextFinanceId).append('\n');
        sb.append("autoPay=").append(autoPayBills ? 1 : 0).append('\n');
        sb.append("failStreak=").append(failStreak).append('\n');
        return sb.toString();
    }

    /** NaN/Infinity kabul etmez (bozuk kayıt oyunu kilitlemesin); NumberFormatException fırlatır, çağıran satırı atlar. */
    private static double num(String v) {
        double d = Double.parseDouble(v);
        if (Double.isNaN(d) || Double.isInfinite(d)) throw new NumberFormatException(v);
        return d;
    }

    /**
     * Hataya toleranslı yükleme: bozuk/eksik satırlar atlanır, null/boş = yeni oyun. v2.5: {@code epoch} anahtarı yoksa ya
     * da {@link #EPOCH}'tan küçükse ilerleme sıfırlanır, ayarlar korunur ({@link #newGameKeepingSettings}); kayıtta ilerleme
     * varsa {@link #progressResetNotice} true olur.
     */
    public static SaveData parse(String text) {
        if (text == null || text.trim().isEmpty()) return newGame();
        SaveData s = new SaveData();
        s.epoch = 0; // anahtar yoksa eski dönem
        boolean progress = false;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String k = line.substring(0, eq).trim();
            String v = line.substring(eq + 1).trim();
            if (k.equals("day") || k.equals("money") || k.equals("stars") || k.startsWith("stock.") || k.startsWith("up.")) progress = true;
            try {
                if (k.equals("epoch")) s.epoch = Integer.parseInt(v);
                else if (k.equals("day")) s.day = Math.max(1, Integer.parseInt(v));
                else if (k.equals("bestDay")) s.bestDay = Math.max(1, Integer.parseInt(v));
                else if (k.equals("money")) s.money = Math.max(0, Integer.parseInt(v));
                else if (k.equals("stars")) s.stars = Math.max(1.0, Math.min(5.0, num(v)));
                else if (k.startsWith("stock.")) {
                    String id = k.substring(6);
                    if (Catalog.ingredient(id) != null) s.inventory.set(id, Integer.parseInt(v));
                } else if (k.startsWith("up.")) {
                    String id = k.substring(3);
                    Upgrade u = Catalog.upgrade(id);
                    if (u != null) s.upgradeLevels.put(id, Math.max(0, Math.min(u.maxLevel(), Integer.parseInt(v))));
                } else if (k.equals("tempo")) s.setTempo(num(v));
                else if (k.equals("speech")) s.speechOn = v.equals("1");
                else if (k.equals("speechRate")) s.speechRate = Math.max(0.5, Math.min(2.0, num(v)));
                else if (k.equals("vibration")) s.vibration = v.equals("1");
                else if (k.equals("music")) s.musicVolume = Math.max(0, Math.min(1, num(v)));
                else if (k.equals("sfx")) s.sfxVolume = Math.max(0, Math.min(1, num(v)));
                else if (k.equals("ambience")) s.ambienceVolume = Math.max(0, Math.min(1, num(v)));
                else if (k.equals("tutorial")) s.tutorialDone = v.equals("1");
                else if (k.equals("apprenticeAnnounce")) s.apprenticeAnnounce = v.equals("1");
                else if (k.equals("narration")) s.narrationLevel = NarrationLevel.fromId(Integer.parseInt(v));
                else if (k.equals("flow")) { FlowMode m = FlowMode.byId(v); if (m != null) s.flowMode = m; }
                else if (k.equals("flowChosen")) s.flowChosen = v.equals("1");
                else if (k.equals("timer")) s.timePressure = !v.equals("0");
                else if (k.equals("readyDrinks")) s.readyDrinks = Math.max(0, Math.min(40, Integer.parseInt(v)));
                else if (k.equals("apprenticeDelivery")) s.apprenticeDelivery = !v.equals("0");
                else if (k.equals("resetNotice")) s.progressResetNotice = v.equals("1");
                else if (k.startsWith("price.")) s.prices.put(k.substring(6), Integer.parseInt(v));
                else if (k.startsWith("bill.")) {
                    Bill b = Bill.parse(Integer.parseInt(k.substring(5)), v);
                    if (b != null) s.bills.add(b);
                } else if (k.startsWith("loan.")) {
                    Loan l = Loan.parse(Integer.parseInt(k.substring(5)), v);
                    if (l != null && !l.isClosed()) s.loans.add(l);
                }
                else if (k.equals("credit")) s.creditScore = Math.max(0, Math.min(Finance.CREDIT_MAX, Integer.parseInt(v)));
                else if (k.equals("wageDebt")) s.wageDebt = Math.max(0, Integer.parseInt(v));
                else if (k.equals("salesTax")) s.salesSinceTax = Math.max(0, Integer.parseInt(v));
                else if (k.equals("finId")) s.nextFinanceId = Math.max(1, Integer.parseInt(v));
                else if (k.equals("autoPay")) s.autoPayBills = !v.equals("0");
                else if (k.equals("failStreak")) s.failStreak = Math.max(0, Math.min(1000, Integer.parseInt(v)));
            } catch (RuntimeException ignored) {
                // bozuk satır atlanır
            }
        }
        if (s.bestDay < s.day) s.bestDay = s.day;
        Pricing.sanitize(s.prices);
        for (Bill b : s.bills) s.nextFinanceId = Math.max(s.nextFinanceId, b.id + 1);
        for (Loan l : s.loans) s.nextFinanceId = Math.max(s.nextFinanceId, l.id + 1);
        if (s.epoch < EPOCH) {
            // v2.5 dönem sıfırlaması: ilerleme sıfır, ayarlar korunur, bir kez bildirim
            SaveData fresh = newGameKeepingSettings(s);
            fresh.progressResetNotice = progress || s.progressResetNotice;
            return fresh;
        }
        return s;
    }
}
