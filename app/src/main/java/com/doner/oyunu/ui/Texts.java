package com.doner.oyunu.ui;

import com.doner.oyunu.engine.ActiveStep;
import com.doner.oyunu.engine.Catalog;
import com.doner.oyunu.engine.DayConfig;
import com.doner.oyunu.engine.DaySummary;
import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Ingredient;
import com.doner.oyunu.engine.Narrator;
import com.doner.oyunu.engine.Order;
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
        if (act != null) return act.step.name + " sürüyor";
        Step n = o.nextStep();
        if (n == null) return "Hazır";
        return (selected ? "Sıradaki: " : "Bekliyor: ") + n.name + " (" + n.station.displayName + ")";
    }

    public static String orderProgress(Order o) {
        return o.getNextStepIndex() + "/" + o.steps.size() + " adım tamam";
    }

    public static String orderDescription(Order o, boolean selected, ActiveStep act) {
        StringBuilder sb = new StringBuilder();
        sb.append(orderTitle(o)).append(". ").append(o.itemsText()).append(". ");
        if (selected) sb.append("Seçili. ");
        sb.append(orderStatus(o, act, true)).append(". ").append(orderProgress(o)).append(". ");
        sb.append(patienceWord(o.customer.patienceFraction()));
        sb.append(", en fazla ").append(roundedSeconds(o.customer.getPatienceLeft())).append(" saniye kaldı. ");
        sb.append(selected ? "" : "Seçmek için çift dokun.");
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------ istasyon
    /** Bugünün menüsünde kullanılan istasyonlar. */
    public static Set<Station> stationsInUse(int day) {
        Set<Station> set = new HashSet<>();
        for (Product p : Catalog.productsForDay(day)) for (Step s : p.steps) set.add(s.station);
        return set;
    }

    public static String stationVisualStatus(GameEngine e, Station st) {
        for (ActiveStep a : e.getActiveSteps()) if (a.step.station == st) return "Çalışıyor";
        Order sel = e.getSelectedOrder();
        if (sel != null && !sel.isRunning() && sel.nextStep() != null && sel.nextStep().station == st) return "Şimdi bu";
        return "Boş";
    }

    public static String stationDescription(Narrator n, GameEngine e, Station st, boolean inUse) {
        StringBuilder sb = new StringBuilder(n.stationLabel(st));
        for (ActiveStep a : e.getActiveSteps()) {
            if (a.step.station == st) { sb.append(", ").append(a.step.name).append(" yapılıyor, ").append(a.order.customer.name); break; }
        }
        if (!inUse) sb.append(", bugünkü menüde kullanılmıyor");
        sb.append(". Dokunarak kullan.");
        return sb.toString();
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
        return u.name + ", seviye " + lv + " / " + u.maxLevel() + ". " + u.description;
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
        if (save.day == 1 && !save.tutorialDone) l.add("Öğretici açık: ilk siparişlerde adım adım sesle yönlendirileceksin.");
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
        return r;
    }

    // ------------------------------------------------------------------ ayarlar
    public static String volumeText(double v) { return percentShort(v); }

    public static String volumeSpoken(double v) { return Math.round(v * 100) + " yüzde"; }

    // ------------------------------------------------------------------ yardım
    public static List<String[]> helpSections() {
        List<String[]> l = new ArrayList<>();
        l.add(new String[]{"Amaç",
                "Bir döner dükkanı işletiyorsun. Gelen müşterilerin siparişlerini doğru adımlarla hazırlayıp sabırları tükenmeden teslim et. "
                        + "Yıldızın 1,0'ın altına düşerse gün başarısız olur, aynı günü paran kaybolmadan tekrar denersin."});
        l.add(new String[]{"Oyun ekranı",
                "En üstte durum çubuğu var: para, yıldız, kalan süre ve bekleyen sipariş sayısı. Ortada siparişler listelenir. "
                        + "Altında istasyonlar var: döner makinesi, ekmek ısıtıcı, sos ve salata tezgahı, ızgara ve fırın, paketleme, içecek dolabı, tatlı tezgahı. "
                        + "En altta Teslim et, Sonraki sipariş, Duraklat ve Durumu oku düğmeleri bulunur."});
        l.add(new String[]{"Sipariş hazırlama",
                "Bir siparişe dokunarak seç. Sistem sıradaki adımı ve gereken istasyonu söyler. O istasyona dokun, işlem başlar. "
                        + "İşlem bitince bir ses duyarsın ve sıradaki adım söylenir. Tüm adımlar bitince sipariş hazır olur: Teslim et düğmesine dokun. "
                        + "Aynı anda yapabileceğin işlem sayısı yuva sayına bağlıdır."});
        l.add(new String[]{"Hatalar ve eksik malzeme",
                "Yanlış istasyona dokunursan hata sesi gelir ve yıldızın çok az düşer. Malzeme biterse Acil al düğmesi görünür; acil alım yüzde elli daha pahalıdır. "
                        + "Boş yuva yoksa bir işlemin bitmesini beklemelisin."});
        l.add(new String[]{"Sesler ve titreşim",
                "Her olayın kendine özgü sesi ve titreşimi vardır. Yeni müşteri kapı zili ve sipariş sesiyle gelir. Sipariş sesleri stereo çalar: birinci sipariş solda, üçüncü sipariş sağda duyulur. "
                        + "Sabır azalınca uyarı sesi, sabır bitince müşteri gider ve yıldız düşer. Teslimde para sesi duyarsın."});
        l.add(new String[]{"Market",
                "Her günün başında markete uğra. Önerilen malzemeleri al düğmesi günün tahmini ihtiyacını tek dokunuşla alır. "
                        + "Mutfak aletleri işlemleri hızlandırır, çırak yuva sayını artırır, buzdolabı stok kapasitesini büyütür. "
                        + "Restoran öğeleri müşteri sayısını, sabrı ve bahşişi artırır."});
        l.add(new String[]{"Gün sonu",
                "Gün bitince satış, bahşiş ve kira özetini dinlersin. Kayıt otomatik yapılır. Sonraki gün yeni ürünler açılabilir; menü birikimlidir."});
        l.add(new String[]{"Kolaylıklar",
                "Her ekranda Nasıl oynanır yardımına ulaşabilirsin. Oyun sırasında Durumu oku düğmesi para, yıldız, süre ve seçili siparişi söyler. "
                        + "Duraklat düğmesi oyunu durdurur; uygulama arka plana gidince oyun kendiliğinden durur. "
                        + "Ayarlardan müzik ve efekt sesini, anlatım hızını, oyun temposunu ve titreşimi değiştirebilirsin. Tüm eylemler tek dokunuşla yapılır."});
        return l;
    }

    public static String quickHelp() {
        return "Siparişe dokunarak seç, sistemin söylediği istasyona dokun, işlem bitince sıradaki adıma geç. "
                + "Tüm adımlar bitince Teslim et düğmesine dokun. Durumu oku düğmesi bilgi verir. Acele et, sabır tükenirse müşteri gider.";
    }
}
