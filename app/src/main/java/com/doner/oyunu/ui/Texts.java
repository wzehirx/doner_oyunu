package com.doner.oyunu.ui;

import com.doner.oyunu.engine.ActiveStep;
import com.doner.oyunu.engine.Catalog;
import com.doner.oyunu.engine.DayConfig;
import com.doner.oyunu.engine.DaySummary;
import com.doner.oyunu.engine.FlowMode;
import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Ingredient;
import com.doner.oyunu.engine.Narrator;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.OrderLine;
import com.doner.oyunu.engine.PortionSize;
import com.doner.oyunu.engine.PrepKind;
import com.doner.oyunu.engine.Product;
import com.doner.oyunu.engine.SaveData;
import com.doner.oyunu.engine.Shop;
import com.doner.oyunu.engine.Station;
import com.doner.oyunu.engine.StationOption;
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

    public static String orderDescription(Order o, boolean selected, ActiveStep act) { return orderDescription(o, selected, act, true); }

    /**
     * Sipariş kartı etiketi (v0.9, kısa): "Sipariş 2, Ali, ayran ve ekmek arası döner, soğansız, seçili". Durum yalnızca
     * gerektiğinde eklenir (hazır, paketleniyor, sabır azalıyor/kritik). timed=false: sabır yoktur.
     */
    public static String orderDescription(Order o, boolean selected, ActiveStep act, boolean timed) {
        StringBuilder sb = new StringBuilder("Sipariş ").append(o.getPosition()).append(", ").append(o.customer.name)
                .append(", ").append(o.itemsText());
        if (o.hasRequests()) sb.append(", ").append(o.requestsText());
        if (o.isReady()) sb.append(", hazır");
        else if (o.isPacking()) sb.append(", paketleniyor");
        if (timed && !o.isReady()) sb.append(", ").append(patienceWord(o.customer.patienceFraction()));
        if (selected) sb.append(", seçili");
        return sb.toString();
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
        String added = addedLine(l);
        if (!added.isEmpty()) sb.append('\n').append(added);
        return sb.toString();
    }

    /** Kalemin eklenen malzemeleri: "Eklenen: domates bol, marul" (malzeme adımı yoksa ya da hiç eklenmediyse boş). */
    public static String addedLine(OrderLine l) {
        if (l.product.toppingStepIndex() < 0 || l.addedTotal() == 0) return "";
        return "Eklenen: " + addedNames(l);
    }

    /** "domates bol, marul ve soğan" (ekleme sırasıyla havuz sırasında). */
    public static String addedNames(OrderLine l) {
        List<String> n = new ArrayList<>();
        for (java.util.Map.Entry<String, Integer> e : l.getAddedCounts().entrySet()) {
            Ingredient ing = Catalog.ingredient(e.getKey());
            String nm = (ing == null ? e.getKey() : ing.name).toLowerCase(new java.util.Locale("tr"));
            n.add(e.getValue() >= 2 ? nm + " bol" : nm);
        }
        return join(n);
    }

    /** Seçenek panelinin bilgi satırı: sipariş, kalemler (boylu) ve müşteri istekleri; sipariş yoksa hazırlık notu. */
    public static String menuInfo(Order o) {
        if (o == null) return "Hazırlık seçenekleri.";
        StringBuilder sb = new StringBuilder("Sipariş ").append(o.getPosition()).append(", ").append(o.customer.name)
                .append(": ").append(o.itemsText()).append('.');
        if (o.hasRequests()) sb.append(" Müşteri isteği: ").append(o.requestsText()).append('.');
        return sb.toString();
    }

    /** Menü panelinin durum satırı (SOS): "Eklenenler: domates bol, marul." / "Henüz malzeme eklenmedi." */
    public static String addedStatus(OrderLine l) {
        if (l == null || l.product.toppingStepIndex() < 0) return "";
        if (l.addedTotal() == 0) return "Henüz malzeme eklenmedi.";
        return "Eklenenler: " + addedNames(l) + ".";
    }

    /** Kalem etiketi (v0.9, kısa): "Sipariş 2, kalem 1, ekmek arası döner, 2/3 adım" / "…, hazır" / sürüyorsa çalışan adım; seçiliyse "seçili". */
    public static String lineDescription(GameEngine e, Order o, OrderLine l, boolean selectedItem) {
        StringBuilder sb = new StringBuilder("Sipariş ").append(o.getPosition()).append(", kalem ").append(l.index + 1).append(", ").append(l.displayName());
        if (l.isDone()) sb.append(", hazır");
        else {
            sb.append(", ").append(l.doneCount()).append('/').append(l.stepCount()).append(" adım");
            for (int i = 0; i < l.stepCount(); i++) {
                if (l.isStepRunning(i)) {
                    ActiveStep j = jobOf(e, o, l, i);
                    sb.append(", ").append(l.step(i).name).append(j != null && j.isApprentice() ? " sürüyor, çırak" : " sürüyor");
                    break;
                }
            }
        }
        String added = addedStatus(l);
        if (!added.isEmpty() && l.addedTotal() > 0) sb.append(", ").append(Character.toLowerCase(added.charAt(0))).append(added.substring(1, added.length() - 1));
        if (selectedItem) sb.append(", seçili");
        return sb.toString();
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
        String p = "Siparişi paketle, ";
        if (o.isReady()) return p + "tamam";
        if (o.isPacking()) return p + "sürüyor";
        if (o.canPack()) return p + "hazır";
        return p + "kilitli";
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

    /** İstasyon düğmesi etiketi (v0.9, kısa): ad + durum; menüde kullanılmıyorsa sonuna eklenir. */
    public static String stationDescription(Narrator n, GameEngine e, Station st, boolean inUse) {
        StringBuilder sb = new StringBuilder(n.stationLabel(st));
        for (ActiveStep a : e.getActiveSteps()) {
            if (a.step.station == st && a.isApprentice()) { sb.append(", çırak"); break; }
        }
        if (!inUse) sb.append(", menüde yok");
        return sb.toString();
    }

    // ------------------------------------------------------------------ hazırlık / çalışanlar
    public static String prepKindName(PrepKind k) {
        switch (k) {
            case DONER: return "döner dilimleme";
            case DRINK: return "ayran doldurma";
            case FRIES: return "patates kızartma";
            default: return "salata ve sos tepsisi hazırlama";
        }
    }

    /** İş satırı etiketi: "Sen: ..." ya da "Çırak: ayran dolduruyor". */
    public static String jobLabel(GameEngine e, ActiveStep a) {
        String who = a.isApprentice() ? (e.getApprenticeLevel() > 1 ? "Çırak " + a.worker : "Çırak") : "Sen";
        String what;
        if (a.isPrep()) {
            if (a.isApprentice()) {
                what = a.prep == PrepKind.DONER ? "döner dilimliyor" : a.prep == PrepKind.DRINK ? "ayran dolduruyor"
                        : a.prep == PrepKind.FRIES ? "patates kızartıyor" : "salata ve sos tepsisi hazırlıyor";
            } else what = prepKindName(a.prep);
        } else if (a.isPack()) {
            what = a.order.getPosition() + " numaralı siparişi paketliyor";
        } else {
            what = a.order.getPosition() + " numaralı siparişte: " + a.step.name;
        }
        return who + ": " + what;
    }

    /** Hazır stok hücresi etiketi (v0.9, kısa): "Hazır döner: 3,5 porsiyon". */
    public static String stockCellDescription(GameEngine e, PrepKind k) {
        switch (k) {
            case DONER: return "Hazır döner: " + PortionSize.halvesText(e.getReadyDonerHalves()) + " porsiyon";
            case DRINK: return "Hazır ayran: " + e.getReadyDrinks() + " bardak";
            case FRIES: return "Hazır patates: " + e.getReadyFries() + " porsiyon";
            default: return "Hazır salata ve sos tepsisi: " + e.getTrayTotal() + " kullanım";
        }
    }

    /** Hazırlık düğmesi etiketi (v0.9, kısa): ad + hazır stok. */
    public static String prepButtonDescription(GameEngine e, PrepKind k, Ingredient meat) {
        int cap = e.getReadyCapacity(k);
        switch (k) {
            case DONER: return "Döner dilimle: " + meat.name + ", hazır " + PortionSize.halvesText(e.getReadyMeatHalves(meat.id)) + " / " + cap;
            case DRINK: return "Ayran doldur, hazır " + e.getReadyDrinks() + " / " + cap;
            case FRIES: return "Patates kızart, hazır " + e.getReadyFries() + " / " + cap;
            default: return "Salata ve sos tepsisi hazırla, hazır " + e.getTrayTotal();
        }
    }

    public static String prepButtonLabel(GameEngine e, PrepKind k, Ingredient meat) {
        int cap = e.getReadyCapacity(k);
        switch (k) {
            case DONER: return "Döner dilimle: " + meat.name + "\n4 ya da 2 porsiyon · hazır " + PortionSize.halvesText(e.getReadyMeatHalves(meat.id)) + " / " + cap;
            case DRINK: return "Ayran doldur\n" + k.batch + " bardak · hazır " + e.getReadyDrinks() + " / " + cap;
            case FRIES: return "Patates kızart\n" + k.batch + " porsiyon · hazır " + e.getReadyFries() + " / " + cap;
            default: return "Salata ve sos tepsisi hazırla\nÖnerilen malzemeler · hazır " + e.getTrayTotal() + " kullanım";
        }
    }

    public static String slotsText(GameEngine e) {
        return "Boş yuva " + e.getFreeSlots() + " / " + e.getSlotCount();
    }

    public static String deliverDescription(GameEngine e) {
        List<String> names = new ArrayList<>();
        for (Order o : e.getOrders()) if (o.isReady()) names.add(o.customer.name);
        if (names.isEmpty()) return "Teslim et, hazır sipariş yok";
        return "Teslim et, hazır: " + join(names);
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
        return i.name + ", stok " + stock + " / " + cap + ", birim " + i.price + " lira";
    }

    /** Stok kapasitesini ve parayı gözeterek GERÇEKTE alınacak miktarı ve ödenecek tutarı söyler. */
    public static String buyDescription(Ingredient i, int qty, SaveData save) {
        int real = Shop.purchasableQty(save, i.id, qty);
        String head = qty + " " + i.name + " al";
        if (real <= 0) return head + ", stok dolu";
        int cost = real * i.price;
        String t = real < qty ? head + ", yalnızca " + real + " alınır, " + cost + " lira" : head + ", " + cost + " lira";
        if (save.money < cost) t += ", paran yetmiyor";
        return t;
    }

    /** Acil al düğmesi metni (görsel). */
    public static String emergencyLabel(List<Ingredient> missing, int cost) {
        return "Acil al: " + names(missing) + " (" + cost + " TL)";
    }

    /** Acil al düğmesi erişilebilirlik açıklaması: malzeme adları ve toplam fiyat. */
    public static String emergencyDescription(List<Ingredient> missing, int cost) {
        return "Acil al: " + names(missing) + ", " + cost + " lira";
    }

    private static String names(List<Ingredient> l) {
        List<String> n = new ArrayList<>();
        for (Ingredient i : l) n.add(i.name);
        return join(n);
    }

    public static String recommendedDescription(SaveData save) {
        int cost = Shop.recommendedCost(save);
        if (cost <= 0) return "Önerilen malzemeleri al, stok yeterli";
        String t = "Önerilen malzemeleri al, " + cost + " lira";
        if (cost > save.money) t += ", paran yetmiyor";
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
        if (save.timePressure) l.add("Süre: " + durationText(c) + ".");
        else l.add("Bu gün süre yok, " + DayConfig.quotaFor(save) + " müşteri. Süre sayacı yok, müşteri sabrı azalmaz, kimse gitmez; kota kadar müşteri gelip hepsi teslim edilince gün biter.");
        l.add("Kasada " + save.money + " lira var. Yıldızın " + Narrator.fmtStars(save.stars) + ".");
        l.add("Gün sonunda " + c.rent + " lira kira ödenecek.");
        l.add(save.timePressure
                ? "Hedef: yıldızın 1,0'ın altına düşmeden günü bitir, müşterileri hızlı ve doğru hazırlanmış siparişlerle memnun et."
                : "Hedef: yıldızın 1,0'ın altına düşmeden günü bitir. Acele etmene gerek yok; doğru hazırlanmış siparişler yıldızını korur. Hız bonusu ve hızlı teslim bahşişi yoktur, bahşişler biraz daha azdır.");
        List<Product> news = Catalog.newProductsOnDay(save.day);
        if (news.isEmpty()) l.add("Bugün yeni ürün yok.");
        else {
            List<String> n = new ArrayList<>();
            for (Product p : news) n.add(p.name + " (" + p.price + " lira)");
            l.add("Yeni ürün: " + join(n) + ".");
        }
        String newIng = Narrator.newIngredientsText(save.day);
        if (!newIng.isEmpty()) l.add(newIng);
        String newSize = Narrator.newSizesText(save.day, save.flowMode);
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
        l.add("Önce hazırlık aşaması gelir, süre akmaz. Hazır olunca Dükkânı aç ya da Hazırlık yapmadan aç. "
                + "Gün içinde hazırlığı istasyonun Eylemler menüsünden yaparsın.");
        l.add(flowBriefingLine(save));
        l.add(timePressureBriefingLine(save));
        l.add("İstasyon seçeneklerini TalkBack Eylemleri menüsünden ya da düğmeye dokununca açılan panelden seçersin.");
        if (save.day == 1 && !save.tutorialDone) l.add("Öğretici açık: kısa sesli ipuçlarıyla ilk siparişi hazırlarsın.");
        return l;
    }

    // ------------------------------------------------------------------ süre baskısı (v0.8)
    /** Brifing satırı: "Süre baskısı: açık. …" / "kapalı. …" */
    public static String timePressureBriefingLine(SaveData save) {
        return (save.timePressure
                ? "Süre baskısı: açık. Gün süreli, müşteriler zamanla sabırsızlanır."
                : "Süre baskısı: kapalı. Süre sayacı yok, müşteriler beklemekten gitmez.")
                + " Ayarlardan, oyun sırasında da Duraklat menüsünden değiştirebilirsin.";
    }

    /** Duraklat menüsü düğmesi: "Süre baskısı: Açık". */
    public static String timePressureButtonLabel(boolean timed) { return "Süre baskısı: " + (timed ? "Açık" : "Kapalı"); }

    /** Düğme etiketi (v0.9, kısa): yalnızca durum. */
    public static String timePressureButtonDescription(boolean timed) {
        return "Süre baskısı: " + (timed ? "açık" : "kapalı");
    }

    /** Ayarlardaki büyük aç/kapa düğmesi. */
    public static String timePressureSettingLabel(boolean timed) { return "Süre baskısı: " + (timed ? "Açık" : "Kapalı"); }

    public static String timePressureSettingDescription(boolean timed) {
        return "Süre baskısı: " + (timed ? "açık" : "kapalı");
    }

    /** Tek cümlelik açıklama notu (Ayarlar). */
    public static final String TIME_PRESSURE_NOTE = "Kapalıyken süre sayacı yok, müşteriler beklemekten gitmez; bahşişler biraz azalır.";

    // ------------------------------------------------------------------ oyun akışı (v0.7)
    /** Brifing satırı: "Oyun akışı: Dengeli. <açıklama> Oyun akışı düğmesiyle ya da Ayarlardan değiştirebilirsin." */
    public static String flowBriefingLine(SaveData save) {
        String t = save.flowMode.spoken();
        if (!save.flowChosen) t = "Seçim: " + t + " Rahat, Dengeli ya da Gerçekçi; istediğini seç.";
        return t + " Oyun akışı düğmesiyle ya da Ayarlardan, oyun sırasında da Duraklat menüsünden değiştirebilirsin.";
    }

    /** "Oyun akışı: Dengeli" düğmesi etiketi (sıradaki akışa geçer). */
    public static String flowButtonLabel(FlowMode m) { return "Oyun akışı: " + m.displayName; }

    /** Düğme etiketi (v0.9, kısa): yalnızca durum. */
    public static String flowButtonDescription(FlowMode m) {
        return "Oyun akışı: " + m.displayName;
    }

    /** Ayarlardaki seçenek düğmesi: "Dengeli, seçili. <açıklama>" */
    public static String flowOptionDescription(FlowMode m, boolean selected) {
        return m.displayName + (selected ? ", seçili" : "") + (m == FlowMode.DEFAULT ? ", önerilen" : "");
    }

    /** Ayarlar > Oyun akışı altında okunan tek paragraf (düğmeler kısa kalır). */
    public static final String FLOW_NOTE = "Rahat: boy ve malzemeler otomatik. Dengeli: standart olan otomatik, farklı olanı sen yaparsın. Gerçekçi: her şeyi sen yaparsın.";

    /** Akışa göre döner boyu öğretici metni (tek kısa cümle; Gerçekçi = {@link #TUT_SIZE}). */
    public static String tutSize(FlowMode m) {
        switch (m) {
            case RAHAT: return "Döner boyu otomatik, makineye bir kez dokun.";
            case DENGELI: return "Tam ekmek otomatik; yarım ekmek ya da duble istenirse boyu sen seç.";
            default: return TUT_SIZE;
        }
    }

    /** Akışa göre malzeme öğretici metni (tek kısa cümle; Gerçekçi = {@link #TUT_TOPPING}). */
    public static String tutTopping(FlowMode m) {
        switch (m) {
            case RAHAT: return "Sos tezgahına dokun, malzemeler otomatik eklenir.";
            case DENGELI: return "Sos tezgahına dokun; varsayılanlar eklenir. İstek varsa farkı yap, sonra Malzemeleri bitir.";
            default: return TUT_TOPPING;
        }
    }

    /** Akışa göre kısa yardım (Gerçekçi = {@link #quickHelp()}). */
    public static String quickHelp(FlowMode m) { return quickHelp(m, true); }

    /** Akışa ve süre baskısına göre kısa yardım. */
    public static String quickHelp(FlowMode m, boolean timed) {
        String base = quickHelpFlow(m);
        return timed ? base : base.replace(" Acele et, sabır tükenirse müşteri gider.", " Süre baskısı kapalı: acele yok, müşteri gitmez.");
    }

    private static String quickHelpFlow(FlowMode m) {
        if (m == FlowMode.GERCEKCI) return quickHelp();
        String mid = m == FlowMode.RAHAT
                ? "Rahat akışta boy ve malzemeler otomatik; her istasyona bir kez dokun. "
                : "Dengeli akışta standart olan otomatik; istek ya da yarım ekmek, duble varsa farkı yap, sonra Malzemeleri bitir. Hedefi oku ile kontrol et. ";
        return "Hazırlıkta önceden döner dilimle, ayran doldur; sonra Dükkânı aç. Siparişi seç, önerilen istasyona dokun; seçenekleri Eylemler menüsünden de seçebilirsin. "
                + mid + "Kalemler bitince siparişi paketle, sonra Teslim et. Acele et, sabır tükenirse müşteri gider.";
    }

    // ------------------------------------------------------------------ gün sonu
    public static List<String[]> summaryRows(DaySummary s) {
        List<String[]> r = new ArrayList<>();
        r.add(new String[]{"Teslim edilen sipariş", String.valueOf(s.delivered)});
        if (s.timed) r.add(new String[]{"Giden müşteri", String.valueOf(s.lost)});
        else r.add(new String[]{"Süre baskısı", "kapalıydı; müşteri gitmedi"});
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
        if (s.sizeMistakes > 0) r.add(new String[]{"Yanlış boyda dilimlenen sipariş", String.valueOf(s.sizeMistakes)});
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
                "Döner dükkanı işletirsin. Müşterilerin siparişini sabırları bitmeden hazırlayıp teslim et. "
                        + "Yıldızın 1,0'ın altına düşerse gün başarısız olur; aynı günü paran kaybolmadan tekrar denersin."});
        l.add(new String[]{"Gün öncesi hazırlık",
                "Markete uğradıktan sonra hazırlık aşamasına girersin: müşteri gelmez, süre akmaz. "
                        + "Döner dilimle, ayran doldur, patates kızart ve salata ve sos tepsisi hazırla düğmeleriyle önceden hazırlık yaparsın. Hazır stok ilgili adımı hızlandırır. "
                        + "Bitince Dükkânı aç; istersen Hazırlık yapmadan aç. Gün sonunda artan hazır döner, patates ve tepsi israf olur; hazır ayran ertesi güne kalır. "
                        + "Dükkân açılınca hazırlık düğmeleri ve hazır stok göstergeleri ekrandan kalkar; gün içinde hazırlığı istasyonun Eylemler menüsünden ya da panelinden yaparsın."});
        l.add(new String[]{"Oyun ekranı",
                "Üstte durum çubuğu var: para, yıldız, kalan süre ve bekleyen sipariş. Ortada siparişler, altında istasyonlar: "
                        + "döner makinesi, ekmek ısıtıcı, sos ve salata tezgahı, Ocak, paketleme, içecek dolabı, tatlı tezgahı. "
                        + "En altta Teslim et, Sonraki sipariş, Duraklat ve Durumu oku düğmeleri bulunur."});
        l.add(new String[]{"Sipariş hazırlama",
                "Siparişe dokunarak seç. Her ürün bir kalemdir; istediğin sırayla yaparsın, istersen kalemi seçersin. "
                        + "Sistem önerilen adımı söyler, ilgili istasyona dokun. Tek işlem varsa hemen başlar, birden çok seçenek varsa panel açılır. "
                        + "Malzeme ekleme, kalemin diğer adımları bitince açılır. Tüm kalemler hazır olunca Siparişi paketle, sonra Teslim et."});
        l.add(new String[]{"Seçenekler ve TalkBack Eylemleri",
                "İstasyon düğmelerinin seçenekleri iki yolla kullanılır, ikisi de aynı sonucu verir. "
                        + "TalkBack Eylemleri: düğmeye odaklan, Eylemler menüsünü aç, seçeneği seç; hemen uygulanır. "
                        + "Panel: düğmeye dokununca büyük düğmeli bir panel açılır. Gruplar: Boy seç, Malzeme ekle, Kontrol, Oku, Hazırlık. Kapat ile vazgeçersin."});
        l.add(new String[]{"Oyun akışı",
                "Ayarlardan, brifingden ya da Duraklat menüsünden seçersin; istediğin an değişir. "
                        + "Rahat: boy ve malzemeler, müşteri istekleri dahil, otomatik. "
                        + "Dengeli, önerilen: standart olan otomatik; yarım ekmek, duble ya da istek varsa farkı sen yaparsın. "
                        + "Gerçekçi: her malzemeyi ve boyu sen seçersin; bahşişler yüzde on beş fazladır. "
                        + "Gerçekçi akışta çırak ikinci seviyedeyken Standart malzemelerle bitir kısayolu açılır; istek varsa kullanma. "
                        + "Otomatik eklenenler kısaca söylenir, örneğin Varsayılan malzemeler eklendi: domates, marul. Stok yoksa Acil al çıkar."});
        l.add(new String[]{"Döner boyu",
                "Müşterinin istediği boy sipariş kartında yazar: yarım ekmek, tam ekmek ya da duble. Yanlış boy hatalı sipariş sayılır: yıldız düşer, bahşiş yarıya iner. "
                        + "Yarım boy ikinci günden, duble dördüncü günden gelir. Gerçekçi akışta boyu sen seçersin; Rahat'ta otomatik, Dengeli'de tam ekmek otomatik."});
        l.add(new String[]{"Malzeme ekleme",
                "Sos ve salata tezgahında her malzeme ayrı seçenektir, örneğin Domates ekle. Aynı malzemeyi iki kez eklersen bol olur. "
                        + "Son ekleneni geri al malzemeyi iade eder; Eklenenleri oku ve Hedefi oku bilgi verir. İşin bitince Malzemeleri bitir. "
                        + "Malzemeler teslimde kontrol edilir: eksik ya da fazla malzeme yıldızı düşürür. Stok bittiyse Acil al çıkar. "
                        + "Dengeli akışta Varsayılan malzemeleri ekle tek dokunuştur; Rahat'ta Malzemeleri ekle her şeyi yapar."});
        l.add(new String[]{"Ocak ve patates",
                "Ocak istasyonunda pilav, mühürleme ve pişirme işleri yapılır. Üçüncü günden müşteri patatesli dürüm ya da ekmek arası isteyebilir: "
                        + "Ocakta Patates kızart adımı çıkar ve sipariş biraz daha pahalıdır. Önceden patates kızartırsan adım çok kısalır."});
        l.add(new String[]{"Süre baskısı",
                "Ayarlardan ya da Duraklat menüsünden açıp kapatırsın. Açıkken sayaç işler, sabır azalır, sabır bitince müşteri gider. "
                        + "Kapalıyken süre yoktur: durum çubuğunda kalan müşteri yazar, kimse gitmez; kota kadar müşteri gelip hepsi teslim edilince gün biter. "
                        + "Kapalıyken hız bonusu yoktur ve bahşişler biraz azdır; hatalar yine yıldız düşürür."});
        l.add(new String[]{"Çırak",
                "Markette çırak alırsın; kendi başına çalışır. Seviye 1: paketler, içecek doldurur. Seviye 2: ekmek ve lavaş ısıtır, tepsi hazırlar. "
                        + "Seviye 3: döner dilimler, Ocak ve tatlı işlerini yapar. Senden yüzde yirmi yavaştır, hata yapmaz. "
                        + "Boy ve malzemeyi her zaman sen seçersin. Ayarlardan çırak duyurularını kapatabilirsin."});
        l.add(new String[]{"Hatalar ve eksik malzeme",
                "Yanlış istasyona dokunursan hata sesi gelir, yıldız çok az düşer. Malzeme biterse Acil al düğmesi çıkar; yüzde elli pahalıdır. "
                        + "Boş yuva yoksa bir işlemin bitmesini bekle."});
        l.add(new String[]{"Sesler",
                "Her olayın kendi sesi ve titreşimi vardır. Siparişler stereo çalar: birinci sipariş solda, üçüncü sağda. "
                        + "İşlem sesleri işlem süresince sürer. Gün boyunca dükkân ambiyansı kısık çalar; Ayarlardan ambiyans, müzik ve efekt sesini ayrı ayarlarsın."});
        l.add(new String[]{"Market",
                "Her günün başında markete uğra. Önerilen malzemeleri al, günün tahmini ihtiyacını alır. "
                        + "Mutfak aletleri işleri hızlandırır, buzdolabı stoku büyütür, restoran öğeleri müşteri, sabır ve bahşişi artırır."});
        l.add(new String[]{"Gün sonu ve kolaylıklar",
                "Gün bitince satış, bahşiş, kira, israf ve hatalar dinletilir; kayıt otomatiktir. Her ekranda Nasıl oynanır vardır. "
                        + "Durumu oku düğmesi para, yıldız, süre, seçili sipariş ve hazır stoğu söyler. Uygulama arka plana giderse oyun durur. Tüm eylemler tek dokunuşludur."});
        return l;
    }

    public static String quickHelp() {
        return "Hazırlıkta önceden döner dilimle, ayran doldur; sonra Dükkânı aç. Siparişi seç, önerilen istasyona dokun; seçenekleri Eylemler menüsünden de seçebilirsin. "
                + "Döner makinesinde boyu sen seç. Sos tezgahında malzemeleri tek tek ekle, Hedefi oku ile kontrol et, bitince Malzemeleri bitir. "
                + "Kalemler bitince siparişi paketle, sonra Teslim et. Acele et, sabır tükenirse müşteri gider.";
    }

    // ------------------------------------------------------------------ çırak (market)
    public static String apprenticeLevelsText(int currentLevel) {
        String t = "Seviye 1: paketler, içecek doldurur. Seviye 2: ekmek ve lavaş ısıtır, tepsi hazırlar. Seviye 3: döner dilimler, Ocak ve tatlı işlerini yapar. "
                + "Senden yüzde yirmi yavaştır, hata yapmaz. Boy ve malzemeyi sen seçersin; Gerçekçi akışta seviye 2 ile Standart malzemelerle bitir kısayolu açılır.";
        if (currentLevel > 0) t += " Şu an seviye " + currentLevel + ".";
        return t;
    }

    /** Yükseltmenin market açıklaması (çırak için seviye ayrıntılarıyla). */
    public static String upgradeDescription(Upgrade u, SaveData save) {
        if (u.id.equals("cirak")) return apprenticeLevelsText(save.level(u.id));
        return u.description;
    }

    // ------------------------------------------------------------------ öğretici
    // v0.9: en az adım, her adım tek kısa cümle. Sessiz aşamalar (hazırlık bitti, dükkân açıldı, işlem sürüyor, paketle)
    // yalnızca aşamayı ilerletir; ilgili duyuru ("Önerilen: …", "Sipariş hazır") zaten Narrator'dan gelir.
    public static final String TUT_PREP = "Hazırlık aşaması. Hazır olunca Dükkânı aç düğmesine dokun.";
    public static final String TUT_PREP_DONE = "";
    public static final String TUT_OPEN = "";
    public static final String TUT_CUSTOMER = "Müşteri geldi. Siparişe dokunarak seç.";
    public static final String TUT_SELECTED = "Önerilen istasyona dokun.";
    public static final String TUT_STEP = "";
    public static final String TUT_SIZE = "Döner boyunu sen seçersin.";
    public static final String TUT_TOPPING = "Sos tezgahına dokun, malzemeleri tek tek ekle, sonra Malzemeleri bitir.";
    public static final String TUT_PACK = "";
    public static final String TUT_READY = "Sipariş hazır. Teslim et düğmesine dokun.";
    public static final String TUT_END = "Öğretici bitti. Durumu oku düğmesi her an bilgi verir.";
    /** Süre baskısı kapalıyken öğretici sonu (sabır uyarısı yok). */
    public static final String TUT_END_UNTIMED = "Öğretici bitti. Süre baskısı kapalı, acele yok.";
    public static String tutEnd(boolean timed) { return timed ? TUT_END : TUT_END_UNTIMED; }
}
