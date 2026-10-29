package com.doner.oyunu.ui;

import com.doner.oyunu.engine.ActiveStep;
import com.doner.oyunu.engine.Catalog;
import com.doner.oyunu.engine.DayConfig;
import com.doner.oyunu.engine.DaySummary;
import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Ingredient;
import com.doner.oyunu.engine.Narrator;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.OrderLine;
import com.doner.oyunu.engine.PrepKind;
import com.doner.oyunu.engine.Product;
import com.doner.oyunu.engine.SaveData;
import com.doner.oyunu.engine.Shop;
import com.doner.oyunu.engine.Station;
import com.doner.oyunu.engine.Step;
import com.doner.oyunu.engine.Upgrade;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Saf Java metin/contentDescription üreticileri (Android bağımlılığı yok, JVM'de test edilir). */
public final class Texts {
    private Texts() {}

    // ------------------------------------------------------------------ biçim
    public static String money(int m) { return m + " TL"; }

    public static String moneySpoken(int m) { return Narrator.fmtMoney(m); }

    public static String clock(float seconds) {
        int s = Math.max(0, (int) Math.ceil(seconds));
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    public static String percent(double v) { return Math.round(v * 100) + " yüzde"; }

    public static String percentShort(double v) { return "%" + Math.round(v * 100); }

    /** 1.25 -> "1,25 kat" ; 1.0 -> "1,0 kat". */
    public static String times(double v) {
        long t = Math.round(v * 10);
        return (t / 10) + "," + (t % 10) + " kat";
    }

    private static final int[] BUCKETS = {3, 5, 10, 15, 20, 30, 45, 60, 90, 120};

    /** Konuşma için kovalanmış saniye ("en fazla N"): açıklama sık değişip TalkBack'i yormasın. */
    public static int roundedSeconds(float s) {
        for (int b : BUCKETS) if (s <= b) return b;
        return BUCKETS[BUCKETS.length - 1];
    }

    // ------------------------------------------------------------------ sipariş
    public static int patienceLevel(float fraction) { return fraction <= 0.2f ? 2 : (fraction <= 0.5f ? 1 : 0); }

    public static String patienceWord(float fraction) {
        switch (patienceLevel(fraction)) {
            case 2: return "sabır kritik";
            case 1: return "sabır azalıyor";
            default: return "sabır iyi";
        }
    }

    public static String orderTitle(Order o) { return "Sipariş " + o.getPosition() + ": " + o.customer.name; }

    /** Kartta görünen durum satırı. */
    public static String orderStatus(Order o, ActiveStep act, boolean selected) {
        if (o.isReady()) return "HAZIR: teslim et";
        if (o.isPacking()) return "Paketleniyor";
        if (act != null) return act.step.name + " sürüyor";
        Step n = o.nextStep();
        if (n == null) return o.isRunning() ? "İşler sürüyor" : "Hazır";
        return (selected ? "Önerilen: " : "Bekliyor: ") + n.name + " (" + n.station.displayName + ")";
    }

    public static String orderProgress(Order o) {
        return o.getNextStepIndex() + "/" + o.steps.size() + " adım tamam";
    }

    public static String orderDescription(Order o, boolean selected, ActiveStep act) {
        StringBuilder sb = new StringBuilder();
        sb.append(orderTitle(o)).append(". ").append(o.itemsText()).append(". ");
        if (o.hasRequests()) sb.append("İstek: ").append(o.requestsText()).append(". ");
        if (o.lineCount() > 1) sb.append(o.lineCount()).append(" kalem. ");
        if (selected) sb.append("Seçili. ");
        sb.append(orderStatus(o, act, true)).append(". ").append(orderProgress(o)).append(". ");
        sb.append(patienceWord(o.customer.patienceFraction()));
        sb.append(", en fazla ").append(roundedSeconds(o.customer.getPatienceLeft())).append(" saniye kaldı. ");
        sb.append(selected ? "" : "Seçmek için çift dokun.");
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------ kalem
    /** İşi yapan etiket: oyuncu ya da çırak. */
    private static ActiveStep jobOf(GameEngine e, Order o, OrderLine l, int stepIndex) {
        for (ActiveStep a : e.getActiveSteps()) {
            if (a.order == o && a.itemIndex == l.index && a.step == l.step(stepIndex)) return a;
        }
        return null;
    }

    /** Kilitli adımın nedeni (kısa). */
    public static String lockReason(OrderLine l, int stepIndex) {
        Step s = l.step(stepIndex);
        if (s.isTopping()) return "önce kalemin diğer adımları bitmeli";
        return "önce önceki adımlar bitmeli";
    }

    /** Kalem satırının görsel metni: her adım bir satır (simge + ad + istasyon). */
    public static String lineSteps(GameEngine e, Order o, OrderLine l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.stepCount(); i++) {
            Step s = l.step(i);
            if (i > 0) sb.append('\n');
            if (l.isStepDone(i)) sb.append("✓ ").append(s.name);
            else if (l.isStepRunning(i)) {
                ActiveStep j = jobOf(e, o, l, i);
                sb.append("▶ ").append(s.name).append(j != null && j.isApprentice() ? "  (çırak yapıyor)" : "  (sürüyor)");
            } else if (l.isStepUnlocked(i)) sb.append("○ ").append(s.name).append("  ·  ").append(s.station.displayName);
            else sb.append("– ").append(s.name).append("  (kilitli)");
        }
        return sb.toString();
    }

    /** Kalemin sesli açıklaması: durum + adımlar + kilit nedenleri. */
    public static String lineDescription(GameEngine e, Order o, OrderLine l, boolean selectedItem) {
        StringBuilder sb = new StringBuilder();
        sb.append("Sipariş ").append(o.getPosition()).append(", kalem ").append(l.index + 1).append(" / ").append(o.lineCount())
                .append(": ").append(l.product.name).append(". ");
        if (l.isDone()) sb.append("Kalem hazır. ");
        else {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < l.stepCount(); i++) {
                Step s = l.step(i);
                if (l.isStepDone(i)) parts.add(s.name + " tamam");
                else if (l.isStepRunning(i)) {
                    ActiveStep j = jobOf(e, o, l, i);
                    parts.add(s.name + (j != null && j.isApprentice() ? " sürüyor, çırak yapıyor" : " sürüyor"));
                } else if (l.isStepUnlocked(i)) {
                    String hint = s.station.displayName;
                    if (s.meatId() != null && e.getReadyMeat(s.meatId()) > 0) hint += ", hazır döner var, hızlı";
                    else if (s.isDrinkFill() && e.getReadyDrinks() > 0) hint += ", hazır içecek var, hızlı";
                    else if (s.isTopping() && e.getTrayTotal() > 0) hint += ", hazır tepsi var, hızlı";
                    parts.add(s.name + " bekliyor, " + hint);
                } else parts.add(s.name + " kilitli, " + lockReason(l, i));
            }
            sb.append(join(parts)).append(". ");
        }
        if (selectedItem) sb.append("Bu kalem seçili. ");
        else if (!l.isDone()) sb.append("Önce bu kalem yapılsın diye seçmek için çift dokun.");
        return sb.toString().trim();
    }

    /** Ortak "Siparişi paketle" adımının görsel metni. */
    public static String packText(Order o) {
        if (o.isReady()) return "✓ Siparişi paketle  ·  teslim et";
        if (o.isPacking()) return "▶ Siparişi paketle  (sürüyor)";
        if (o.canPack()) return "○ Siparişi paketle  ·  " + Station.PAKET.displayName;
        int left = 0;
        for (OrderLine l : o.getLines()) if (!l.isDone()) left++;
        return "– Siparişi paketle  (kilitli, " + left + " kalem bekliyor)";
    }

    public static String packDescription(Order o) {
        String p = "Sipariş " + o.getPosition() + ", Siparişi paketle: ";
        if (o.isReady()) return p + "tamam. Sipariş teslime hazır.";
        if (o.isPacking()) return p + "sürüyor.";
        if (o.canPack()) return p + "hazır. Çift dokununca " + Station.PAKET.displayName + " paketlemeyi başlatır.";
        int left = 0;
        for (OrderLine l : o.getLines()) if (!l.isDone()) left++;
        return p + "kilitli, önce tüm kalemler hazır olmalı. " + left + " kalem bekliyor.";
    }

    // ------------------------------------------------------------------ istasyon
    /** Bugünün menüsünde kullanılan istasyonlar. */
    public static Set<Station> stationsInUse(int day) {
        Set<Station> set = new HashSet<>();
        for (Product p : Catalog.productsForDay(day)) for (Step s : p.steps) set.add(s.station);
        set.add(Station.PAKET);
        return set;
    }

    /** Seçili siparişin bu istasyonda bekleyen açık adımı var mı (paketleme dahil)? */
    public static boolean needsStation(Order sel, Station st) {
        if (sel == null || sel.isReady()) return false;
        for (OrderLine l : sel.getLines()) {
            for (int i = 0; i < l.stepCount(); i++) if (l.isStepOpen(i) && l.step(i).station == st) return true;
        }
        return st == Station.PAKET && sel.canPack();
    }

    public static String stationVisualStatus(GameEngine e, Station st) {
        for (ActiveStep a : e.getActiveSteps()) if (a.step.station == st) return "Çalışıyor";
        if (needsStation(e.getSelectedOrder(), st)) return "Şimdi bu";
        return "Boş";
    }

    public static String stationDescription(Narrator n, GameEngine e, Station st, boolean inUse) {
        StringBuilder sb = new StringBuilder(n.stationLabel(st));
        for (ActiveStep a : e.getActiveSteps()) {
            if (a.step.station != st) continue;
            sb.append(", ").append(a.step.name).append(" yapılıyor");
            if (a.order != null) sb.append(", ").append(a.order.customer.name);
            if (a.isApprentice()) sb.append(", çırak");
            break;
        }
        if (!inUse) sb.append(", bugünkü menüde kullanılmıyor");
        sb.append(". Dokunarak kullan.");
        return sb.toString();
    }

    // ------------------------------------------------------------------ hazırlık / çalışanlar
    public static String prepKindName(PrepKind k) {
        switch (k) {
            case DONER: return "döner dilimleme";
            case DRINK: return "ayran doldurma";
            default: return "salata ve sos tepsisi hazırlama";
        }
    }

    /** İş satırı etiketi: "Sen: ..." ya da "Çırak: ayran dolduruyor". */
    public static String jobLabel(GameEngine e, ActiveStep a) {
        String who = a.isApprentice() ? (e.getApprenticeLevel() > 1 ? "Çırak " + a.worker : "Çırak") : "Sen";
        String what;
        if (a.isPrep()) {
            if (a.isApprentice()) {
                what = a.prep == PrepKind.DONER ? "döner dilimliyor" : a.prep == PrepKind.DRINK ? "ayran dolduruyor" : "salata ve sos tepsisi hazırlıyor";
            } else what = prepKindName(a.prep);
        } else if (a.isPack()) {
            what = a.order.getPosition() + " numaralı siparişi paketliyor";
        } else {
            what = a.order.getPosition() + " numaralı siparişte: " + a.step.name;
        }
        return who + ": " + what;
    }

    public static String stockCellDescription(GameEngine e, PrepKind k) {
        int cap = e.getReadyCapacity(k);
        switch (k) {
            case DONER:
                return "Hazır döner: " + e.getReadyDoner() + " porsiyon. Her döner türü için en fazla " + cap + ".";
            case DRINK:
                return "Hazır ayran: " + e.getReadyDrinks() + " bardak. En fazla " + cap + ". Gün sonunda atılmaz.";
            default:
                return "Hazır salata ve sos tepsisi: " + e.getTrayTotal() + " kullanım. Her malzeme için en fazla " + cap + ".";
        }
    }

    public static String prepButtonDescription(GameEngine e, PrepKind k, Ingredient meat) {
        int cap = e.getReadyCapacity(k);
        switch (k) {
            case DONER: {
                int have = e.getReadyMeat(meat.id);
                return "Döner dilimle: " + meat.name + ". " + k.batch + " porsiyon hazırlar, " + k.batch + " " + meat.unit + " " + meat.name
                        + " harcar, yaklaşık " + Math.round(k.baseSeconds) + " saniye. Hazır " + have + " / " + cap + " porsiyon.";
            }
            case DRINK:
                return "Ayran doldur. " + k.batch + " bardak hazırlar, " + k.batch + " ayran harcar, yaklaşık " + Math.round(k.baseSeconds)
                        + " saniye. Hazır " + e.getReadyDrinks() + " / " + cap + " bardak. Gün sonunda atılmaz.";
            default:
                return "Salata ve sos tepsisi hazırla. Malzemeleri seçersin, her biri için " + k.batch + " kullanım hazırlanır, "
                        + "yaklaşık " + Math.round(k.baseSeconds) + " saniye. Hazır tepsi " + e.getTrayTotal() + " kullanım.";
        }
    }

    public static String prepButtonLabel(GameEngine e, PrepKind k, Ingredient meat) {
        int cap = e.getReadyCapacity(k);
        switch (k) {
            case DONER: return "Döner dilimle: " + meat.name + "\n" + k.batch + " porsiyon · hazır " + e.getReadyMeat(meat.id) + " / " + cap;
            case DRINK: return "Ayran doldur\n" + k.batch + " bardak · hazır " + e.getReadyDrinks() + " / " + cap;
            default: return "Salata ve sos tepsisi hazırla\nMalzeme seç · hazır " + e.getTrayTotal() + " kullanım";
        }
    }

    public static String slotsText(GameEngine e) {
        return "Boş işlem yuvası: " + e.getFreeSlots() + " / " + e.getSlotCount();
    }

    public static String deliverDescription(GameEngine e) {
        List<String> names = new ArrayList<>();
        for (Order o : e.getOrders()) if (o.isReady()) names.add(o.customer.name);
        if (names.isEmpty()) return "Teslim et. Şu an hazır sipariş yok.";
        return "Teslim et. Hazır sipariş: " + join(names) + ".";
    }

    public static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(i == l.size() - 1 ? " ve " : ", ");
            sb.append(l.get(i));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ market
    public static String ingredientInfo(Ingredient i, int stock, int cap) {
        return i.name + ": stok " + stock + " / " + cap + " " + i.unit + ", birim " + i.price + " lira";
    }

    /** Stok kapasitesini ve parayı gözeterek GERÇEKTE alınacak miktarı ve ödenecek tutarı söyler. */
    public static String buyDescription(Ingredient i, int qty, SaveData save) {
        int real = Shop.purchasableQty(save, i.id, qty);
        String head = qty + " " + i.name + " al";
        if (real <= 0) return head + ": stok dolu, hiçbir şey alınmaz, ödeme yok";
        int cost = real * i.price;
        String t = real < qty
                ? head + ", ama stok kapasitesi nedeniyle yalnızca " + real + " " + i.unit + " alınır, " + cost + " lira ödenir"
                : head + ", " + cost + " lira";
        if (save.money < cost) t += ". Paran yetmiyor";
        return t;
    }

    /** Acil al düğmesi metni (görsel). */
    public static String emergencyLabel(List<Ingredient> missing, int cost) {
        return "Acil al: " + names(missing) + " (" + cost + " TL)";
    }

    /** Acil al düğmesi erişilebilirlik açıklaması: malzeme adları ve toplam fiyat. */
    public static String emergencyDescription(List<Ingredient> missing, int cost) {
        String t = "Acil al: " + names(missing) + ", toplam " + cost + " lira. ";
        t += missing.size() > 1 ? "Eksik malzemelerin hepsi tek dokunuşla alınır. " : "";
        return t + "Normalden yüzde elli pahalı.";
    }

    private static String names(List<Ingredient> l) {
        List<String> n = new ArrayList<>();
        for (Ingredient i : l) n.add(i.name);
        return join(n);
    }

    public static String recommendedDescription(SaveData save) {
        int cost = Shop.recommendedCost(save);
        if (cost <= 0) return "Önerilen malzemeleri al. Stok zaten yeterli.";
        String t = "Önerilen malzemeleri al. Tahmini maliyet " + cost + " lira.";
        if (cost > save.money) t += " Paran tamamına yetmiyor, yettiği kadarı alınır.";
        return t;
    }

    public static String upgradeStatus(Upgrade u, SaveData save) {
        int lv = save.level(u.id);
        if (lv >= u.maxLevel()) return "En üst seviyede";
        int price = u.priceForNext(lv);
        int unlock = u.unlockDayForNext(lv);
        if (save.day < unlock) return "Kilitli: " + unlock + ". günde açılır, " + price + " lira";
        if (save.money < price) return "Paran yetmiyor: " + price + " lira gerekli";
        return "Satın alınabilir: " + price + " lira";
    }

    public static boolean upgradeBuyable(Upgrade u, SaveData save) {
        int lv = save.level(u.id);
        return lv < u.maxLevel() && save.day >= u.unlockDayForNext(lv) && save.money >= u.priceForNext(lv);
    }

    public static String upgradeInfo(Upgrade u, SaveData save) {
        int lv = save.level(u.id);
        return u.name + ", seviye " + lv + " / " + u.maxLevel() + ". " + upgradeDescription(u, save);
    }

    public static String upgradeButton(Upgrade u, SaveData save) {
        int lv = save.level(u.id);
        if (lv >= u.maxLevel()) return "En üst seviye";
        return "Satın al: " + u.priceForNext(lv) + " TL";
    }

    public static String buyResult(Shop.BuyResult r, String name, int qty, int cost) {
        switch (r) {
            case OK: return qty > 0 ? qty + " " + name + " alındı, " + cost + " lira." : name + " satın alındı, " + cost + " lira.";
            case NOT_ENOUGH_MONEY: return "Yeterli paran yok. " + cost + " lira gerekli.";
            case MAX_LEVEL: return name + " zaten en üst seviyede.";
            case LOCKED: return name + " henüz kilitli.";
            case STOCK_FULL: return name + " stoğu dolu. Daha fazla alamazsın.";
            default: return "Satın alma yapılamadı.";
        }
    }

    // ------------------------------------------------------------------ brifing
    public static String durationText(DayConfig c) {
        return Math.round(c.duration) + " saniye (en fazla " + Math.round(c.overtime) + " saniye mesai uzatması)";
    }

    public static List<String> briefingLines(SaveData save) {
        DayConfig c = DayConfig.forDay(save.day);
        List<String> l = new ArrayList<>();
        l.add("Süre: " + durationText(c) + ".");
        l.add("Kasada " + save.money + " lira var. Yıldızın " + Narrator.fmtStars(save.stars) + ".");
        l.add("Gün sonunda " + c.rent + " lira kira ödenecek.");
        l.add("Hedef: yıldızın 1,0'ın altına düşmeden günü bitir, müşterileri hızlı ve doğru hazırlanmış siparişlerle memnun et.");
        List<Product> news = Catalog.newProductsOnDay(save.day);
        if (news.isEmpty()) l.add("Bugün yeni ürün yok.");
        else {
            List<String> n = new ArrayList<>();
            for (Product p : news) n.add(p.name + " (" + p.price + " lira)");
            l.add("Yeni ürün: " + join(n) + ".");
        }
        String newIng = Narrator.newIngredientsText(save.day);
        if (!newIng.isEmpty()) l.add(newIng);
        String newSize = Narrator.newSizesText(save.day);
        if (!newSize.isEmpty()) l.add(newSize);
        List<String> ups = new ArrayList<>();
        for (Upgrade u : Catalog.upgrades()) {
            for (int k = 0; k < u.maxLevel(); k++) {
                if (u.unlockDays[k] == save.day) ups.add(u.name + " seviye " + (k + 1));
            }
        }
        if (!ups.isEmpty()) l.add("Markette yeni açılan yükseltme: " + join(ups) + ".");
        List<String> menu = new ArrayList<>();
        for (Product p : Catalog.productsForDay(save.day)) menu.add(p.name);
        l.add("Bugünkü menü: " + join(menu) + ".");
        int rec = Shop.recommendedCost(save);
        if (rec > 0) l.add("Stoğun bugün için yetersiz olabilir. Önerilen malzemelerin maliyeti " + rec + " lira. Markete uğramanı öneririm.");
        else l.add("Stoğun bugün için yeterli görünüyor.");
        l.add("Güne başlayınca önce hazırlık aşaması gelir: döner dilimle, ayran doldur, salata ve sos tepsisi hazırla. Süre akmaz; hazır olunca Dükkânı aç ya da Hazırlık yapmadan aç.");
        if (save.day == 1 && !save.tutorialDone) l.add("Öğretici açık: hazırlık yapmayı, sipariş seçmeyi, malzeme seçmeyi ve teslim etmeyi adım adım sesle öğreneceksin.");
        return l;
    }

    // ------------------------------------------------------------------ gün sonu
    public static List<String[]> summaryRows(DaySummary s) {
        List<String[]> r = new ArrayList<>();
        r.add(new String[]{"Teslim edilen sipariş", String.valueOf(s.delivered)});
        r.add(new String[]{"Giden müşteri", String.valueOf(s.lost)});
        r.add(new String[]{"Satış", s.revenue + " lira"});
        r.add(new String[]{"Bahşiş", s.tips + " lira"});
        if (s.emergencySpend > 0) r.add(new String[]{"Acil alım", s.emergencySpend + " lira"});
        if (s.rentUnpaid() > 0) {
            r.add(new String[]{"Kira", s.rent + " lira ödendi (" + s.rentDue + " lira gerekiyordu, " + s.rentUnpaid() + " lira ödenemedi)"});
        } else {
            r.add(new String[]{"Kira", s.rent + " lira"});
        }
        int p = s.profit();
        r.add(new String[]{"Net kazanç", (p < 0 ? "eksi " + (-p) : String.valueOf(p)) + " lira"});
        r.add(new String[]{"Kasada", s.moneyAfter + " lira"});
        r.add(new String[]{"Yıldız", Narrator.fmtStars(s.startStars) + " puandan " + Narrator.fmtStars(s.endStars) + " puana"});
        if (s.wrongTaps > 0) r.add(new String[]{"Yanlış istasyon dokunuşu", String.valueOf(s.wrongTaps)});
        if (s.perfect > 0) r.add(new String[]{"Kusursuz malzemeli sipariş", String.valueOf(s.perfect)});
        if (s.mistakes > 0) r.add(new String[]{"Hatalı malzemeli sipariş", String.valueOf(s.mistakes)});
        if (s.prepBatches > 0) r.add(new String[]{"Hazırlık", s.prepBatches + " parti"});
        if (s.apprenticeTasks > 0) r.add(new String[]{"Çırağın yaptığı iş", String.valueOf(s.apprenticeTasks)});
        if (s.wastedPortions > 0 || s.wastedCost > 0) {
            r.add(new String[]{"İsraf", s.wastedPortions + " porsiyon hazır malzeme atıldı, " + s.wastedCost + " lira"});
        } else if (s.prepBatches > 0) {
            r.add(new String[]{"İsraf", "yok"});
        }
        return r;
    }

    // ------------------------------------------------------------------ ayarlar
    public static String volumeText(double v) { return percentShort(v); }

    public static String volumeSpoken(double v) { return Math.round(v * 100) + " yüzde"; }

    // ------------------------------------------------------------------ yardım
    public static List<String[]> helpSections() {
        List<String[]> l = new ArrayList<>();
        l.add(new String[]{"Amaç",
                "Bir döner dükkanı işletiyorsun. Gelen müşterilerin siparişlerini hazırlayıp sabırları tükenmeden teslim et. "
                        + "Yıldızın 1,0'ın altına düşerse gün başarısız olur, aynı günü paran kaybolmadan tekrar denersin."});
        l.add(new String[]{"Gün öncesi hazırlık",
                "Markete uğradıktan sonra hazırlık aşamasına girersin. Bu aşamada müşteri gelmez ve süre akmaz. "
                        + "Döner dilimle, ayran doldur ve salata ve sos tepsisi hazırla düğmeleriyle önceden hazırlık yaparsın. "
                        + "Hazır döner dilimleme adımını, hazır ayran doldurmayı, hazır tepsi malzeme seçimini hızlandırır. "
                        + "Hazır stok sayıları ekranda ve sesli okunur. Hazırlığı bitirince Dükkânı aç düğmesine dokun; istersen Hazırlık yapmadan aç diyerek geçebilirsin. "
                        + "Gün sonunda artan hazır döner ve tepsi israf olur ve özette görünür. Hazır ayran ertesi güne kalır. "
                        + "Gün içinde boş anında da hazırlık yapabilirsin; hazırlık sayfanın altındaki Hazırlık bölümündedir."});
        l.add(new String[]{"Oyun ekranı",
                "En üstte durum çubuğu var: para, yıldız, kalan süre ve bekleyen sipariş sayısı. Ortada siparişler listelenir. "
                        + "Altında istasyonlar var: döner makinesi, ekmek ısıtıcı, sos ve salata tezgahı, ızgara ve fırın, paketleme, içecek dolabı, tatlı tezgahı. "
                        + "En altta Teslim et, Sonraki sipariş, Duraklat ve Durumu oku düğmeleri bulunur."});
        l.add(new String[]{"Sipariş hazırlama",
                "Bir siparişe dokunarak seç. Siparişin her ürünü bir kalemdir ve kalemleri istediğin sırayla yapabilirsin. "
                        + "İstersen bir kaleme dokunarak önce onu seç. Sistem önerilen adımı söyler; ilgili istasyona dokun, işlem başlar. "
                        + "Malzeme ekleme adımı, kalemin ısıtma ve dilimleme gibi diğer adımları bitince açılır; erken dokunursan sistem nedenini söyler ve yıldız düşmez. "
                        + "Tüm kalemler hazır olunca tek bir Siparişi paketle adımı açılır: Paketleme istasyonuna ya da kartın paketle satırına dokun. "
                        + "Paketleme bitince Teslim et düğmesine dokun."});
        l.add(new String[]{"Malzeme seçimi",
                "Sos ve salata tezgahına dokununca Malzeme seçimi paneli açılır. Her malzeme bir aç kapa düğmesidir; durumu sesli söylenir, örneğin soğan eklenmedi. "
                        + "Müşteri isteği varsa, örneğin soğansız ya da acılı, siparişin kartında yazar ve panelde okunur. "
                        + "Hedefi oku düğmesi hedef malzemeleri ve müşteri isteklerini söyler. Seçimini bitirince Tamam düğmesine dokun. "
                        + "Stokta olmayan malzeme belirtilir; Acil al düğmesiyle alabilirsin. Panel açıkken süre durur. "
                        + "Malzemeler teslimde kontrol edilir: eksik ya da fazla malzeme yıldızı düşürür ve bahşişi yarıya indirir, tam doğru seçim küçük bir ödül verir. "
                        + "Dördüncü günden itibaren panel boş açılır; ne ekleyeceğini kendin seçersin."});
        l.add(new String[]{"Çırak",
                "Markette çırak alabilirsin. Çırak kendi başına çalışan yardımcıdır ve yeni yuva eklemez. Seviye 1: siparişleri paketler, içecek doldurur; boştayken ayran doldurur ve döner dilimler. "
                        + "Seviye 2: ayrıca ekmek ve lavaş ısıtır, salata ve sos tepsisi hazırlar. Seviye 3: ayrıca döner dilimler, ızgara, tatlı ve harç işlerini yapar. "
                        + "Seviye sayısı kadar çırak aynı anda çalışır. Çırak senden yüzde yirmi yavaştır ama hata yapmaz; en sabırsız siparişe öncelik verir ve seçili siparişine dokunmaz. "
                        + "Malzeme seçimini her zaman sen yaparsın. Yaptığı işler ekranda Çırak diye yazar; ayarlardan çırak duyurularını kapatabilirsin."});
        l.add(new String[]{"Hatalar ve eksik malzeme",
                "Yanlış istasyona dokunursan hata sesi gelir ve yıldızın çok az düşer. Malzeme biterse Acil al düğmesi görünür; acil alım yüzde elli daha pahalıdır. "
                        + "Boş yuva yoksa bir işlemin bitmesini beklemelisin."});
        l.add(new String[]{"Sesler ve titreşim",
                "Her olayın kendine özgü sesi ve titreşimi vardır. Yeni müşteri kapı zili ve sipariş sesiyle gelir. Sipariş sesleri stereo çalar: birinci sipariş solda, üçüncü sipariş sağda duyulur. "
                        + "İstasyon sesleri işlem süresince sürer: kızartma, içecek dökme ve paketleme sesleri kesintisiz, dilimleme, sos ve ekmek sesleri tekrar tekrar duyulur. Çırak sesleri kısık ve ortadadır. "
                        + "Sabır azalınca uyarı sesi, sabır bitince müşteri gider ve yıldız düşer. Teslimde para sesi duyarsın."});
        l.add(new String[]{"Market",
                "Her günün başında markete uğra. Önerilen malzemeleri al düğmesi günün tahmini ihtiyacını tek dokunuşla alır. "
                        + "Mutfak aletleri işlemleri hızlandırır, çırak senin yerine iş yapar, buzdolabı stok ve hazır stok kapasitesini büyütür. "
                        + "Restoran öğeleri müşteri sayısını, sabrı ve bahşişi artırır."});
        l.add(new String[]{"Gün sonu",
                "Gün bitince satış, bahşiş, kira, israf ve hatalı sipariş özetini dinlersin. Kayıt otomatik yapılır. Sonraki gün yeni ürünler açılabilir; menü birikimlidir."});
        l.add(new String[]{"Kolaylıklar",
                "Her ekranda Nasıl oynanır yardımına ulaşabilirsin. Oyun sırasında Durumu oku düğmesi para, yıldız, süre, seçili sipariş ve hazır stoğu söyler. "
                        + "Duraklat düğmesi oyunu durdurur; uygulama arka plana gidince oyun kendiliğinden durur. "
                        + "Ayarlardan müzik ve efekt sesini, anlatım hızını, oyun temposunu, titreşimi ve çırak duyurularını değiştirebilirsin. Tüm eylemler tek dokunuşla yapılır."});
        return l;
    }

    public static String quickHelp() {
        return "Hazırlık aşamasında önceden döner dilimle, ayran doldur, tepsi hazırla; sonra Dükkânı aç. "
                + "Siparişe dokunarak seç, istersen bir kalemini seç, önerilen istasyona dokun. Sos tezgahında malzemeleri aç kapa düğmeleriyle seç, Hedefi oku ile kontrol et, Tamam de. "
                + "Tüm kalemler bitince siparişi paketle, sonra Teslim et. Durumu oku düğmesi bilgi verir. Acele et, sabır tükenirse müşteri gider.";
    }

    // ------------------------------------------------------------------ çırak (market)
    public static String apprenticeLevelsText(int currentLevel) {
        String t = "Seviye 1: siparişleri paketler, ayran doldurur ve dolaptan içecek alır; boştayken ayran doldurur, döner dilimler. "
                + "Seviye 2: ayrıca ekmek ve lavaş ısıtır; boştayken salata ve sos tepsisi hazırlar. "
                + "Seviye 3: ayrıca döner dilimler, ızgara, tatlı ve harç işlerini yapar. "
                + "Seviye sayısı kadar çırak aynı anda çalışır, yeni yuva eklemez. Senden yüzde yirmi yavaştır, hata yapmaz, en sabırsız siparişe öncelik verir ve seçili siparişine dokunmaz. "
                + "Malzeme seçimi her zaman sende.";
        if (currentLevel > 0) t += " Şu an seviye " + currentLevel + ".";
        return t;
    }

    /** Yükseltmenin market açıklaması (çırak için seviye ayrıntılarıyla). */
    public static String upgradeDescription(Upgrade u, SaveData save) {
        if (u.id.equals("cirak")) return apprenticeLevelsText(save.level(u.id));
        return u.description;
    }

    // ------------------------------------------------------------------ öğretici
    public static final String TUT_PREP = "Hazırlık aşamasındasın. Burada müşteri yok ve süre akmıyor. "
            + "Döner dilimle düğmesine dokun; hazır döner sonra dilimleme adımını hızlandırır. Bitince Dükkânı aç düğmesine dokun. "
            + "İstersen Hazırlık yapmadan aç diyerek de geçebilirsin.";
    public static final String TUT_PREP_DONE = "Hazırlık bitti, hazır stok arttı. Başka hazırlık da yapabilirsin. Hazır olunca Dükkânı aç düğmesine dokun.";
    public static final String TUT_OPEN = "Dükkân açıldı. Müşteri gelince sipariş listesinde görünür. Sana adım adım yol göstereceğim.";
    public static final String TUT_CUSTOMER = "Bir müşteri geldi. Siparişi seçmek için sipariş kartına çift dokun, ya da Sonraki sipariş düğmesini kullan.";
    public static final String TUT_SELECTED = "Şimdi önerilen istasyonun düğmesine dokun. Siparişin her ürünü bir kalemdir; kalemleri istediğin sırayla yapabilirsin.";
    public static final String TUT_STEP = "İşlem sürerken bekle. Bitince bir ses duyacaksın ve sıradaki adımı söyleyeceğim.";
    public static final String TUT_TOPPING = "Malzeme seçimi paneli açıldı. Her malzeme bir aç kapa düğmesidir. Hedefi oku düğmesi müşterinin istediği malzemeleri söyler. "
            + "İlk günlerde standart malzemeler hazır seçili gelir. Seçimi bitirince Tamam düğmesine dokun.";
    public static final String TUT_PACK = "Tüm kalemler hazır. Şimdi Paketleme istasyonuna dokunarak siparişi paketle.";
    public static final String TUT_READY = "Sipariş paketlendi. Teslim et düğmesine dokun.";
    public static final String TUT_END = "Harika, ilk siparişini teslim ettin! Malzemeler teslimde kontrol edilir. Sabır azalınca uyarı sesi gelir. "
            + "Durumu oku düğmesiyle istediğin an bilgi alabilirsin. Öğretici bitti.";
}
