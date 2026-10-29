package com.doner.oyunu.engine;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Olaylar için hazır Türkçe duyuru metinleri. GameListener olarak motora eklenirse
 * metinleri {@link Output} arayüzüne gönderir (UI bunu announce/TTS'e bağlar).
 * Metin üreten metotlar herkese açıktır; UI isterse doğrudan kullanabilir.
 */
public class Narrator implements GameListener {

    public interface Output {
        /** urgent=true: mevcut konuşmayı kesip hemen söyle (assertive). */
        void say(String text, boolean urgent);
    }

    /**
     * Yıldız değişimini yalnızca EKRANDA görünen değer (1 ondalık) en son duyurulan değerden farklılaşınca
     * "duyurulmalı" sayar. Her karşılaştırma son duyurulan değere göredir; böylece 0,02'lik küçük adımlar
     * sessiz kalır ama birikip görünen değeri değiştirdiği an (ör. 3,0 -> 2,9) duyurulur; hiçbir görünür değişim kaçmaz.
     */
    public static final class StarsTracker {
        private String shown;

        /** Gün başında/sıfırlamada görünen değeri sabitler. */
        public void reset(double stars) { shown = fmtStars(stars); }

        /** Bu değişim duyurulmalı mı? Duyurulacaksa son duyurulan değeri günceller. */
        public boolean changed(double oldS, double newS) {
            String now = fmtStars(newS);
            String base = shown != null ? shown : fmtStars(oldS);
            if (now.equals(base)) { shown = base; return false; }
            shown = now;
            return true;
        }
    }

    private final GameEngine engine;
    private final StarsTracker starsTracker = new StarsTracker();
    private Output output;
    /** Çırak duyurularını kısıtlamak için son duyuru zamanı (motor süresi). */
    private float lastApprenticeSay = -100f;
    private static final float APPRENTICE_MIN_GAP = 4f;

    public Narrator(GameEngine engine) { this.engine = engine; }

    public Narrator(GameEngine engine, Output output) {
        this.engine = engine;
        this.output = output;
    }

    public void setOutput(Output output) { this.output = output; }

    private void say(String t, boolean urgent) { if (output != null) output.say(t, urgent); }

    // ------------------------------------------------------------ biçimleme yardımcıları
    /** 3.25 -> "3,3" */
    public static String fmtStars(double s) {
        long t = Math.round(s * 10);
        return (t / 10) + "," + (t % 10);
    }

    public static String fmtSeconds(float s) {
        int v = Math.max(1, Math.round(s));
        return v + " saniye";
    }

    public static String fmtMoney(int m) { return m + " lira"; }

    // ------------------------------------------------------------ yardımcılar
    /** "domates, marul ve ketçap". */
    public static String joinNames(Collection<String> ids) {
        List<String> names = new java.util.ArrayList<>();
        for (String id : ids) {
            Ingredient i = Catalog.ingredient(id);
            names.add((i == null ? id : i.name).toLowerCase(new java.util.Locale("tr")));
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append(i == names.size() - 1 ? " ve " : ", ");
            sb.append(names.get(i));
        }
        return sb.toString();
    }

    /** Adımın hangi kaleme ait olduğunu bulur (önce henüz bitmemiş adım). Yoksa null. */
    private static OrderLine lineOf(Order o, Step s) {
        OrderLine fallback = null;
        for (OrderLine l : o.getLines()) {
            for (int i = 0; i < l.stepCount(); i++) {
                if (l.step(i) == s) {
                    if (!l.isStepDone(i)) return l;
                    if (fallback == null) fallback = l;
                }
            }
        }
        return fallback;
    }

    private static String itemPrefix(Order o, Step s) {
        if (o.lineCount() < 2) return "";
        OrderLine l = lineOf(o, s);
        return l == null ? "" : l.product.name + ": ";
    }

    /** Başlayan adımın gerçek boyu (motordaki çalışan işten): dilimlemede seçilen boy (2. günden itibaren tam ekmek de söylenir). */
    private String startedSizeSuffix(Order o, Step s) {
        if (engine != null && (s.isSized() || s.isBreadStep())) {
            for (ActiveStep a : engine.getActiveSteps()) {
                if (a.order == o && a.step == s && a.worker == 0 && a.getSize() != null) {
                    PortionSize sz = a.getSize();
                    if (sz == PortionSize.FULL && (s.isBreadStep() || engine.getDay() < PortionSize.HALF.unlockDay)) return "";
                    return ", " + sz.spoken();
                }
            }
        }
        return sizeSuffix(o, s);
    }

    /** Boylu adımlarda (dilimleme, ekmek/lavaş ısıtma) ", yarım ekmek" gibi ek; tam ekmekte boş. */
    private static String sizeSuffix(Order o, Step s) {
        if (!s.isSized() && !s.isBreadStep()) return "";
        OrderLine l = lineOf(o, s);
        if (l == null || !l.hasSize()) return "";
        PortionSize sz = s.isSized() && l.getChosenSize() != null ? l.getChosenSize() : l.getSize();
        return sz == PortionSize.FULL ? "" : ", " + sz.spoken();
    }

    private static String requestSentence(Order o) {
        return o.hasRequests() ? " İstek: " + o.requestsText() + "." : "";
    }

    // ------------------------------------------------------------ metin üreticiler
    public String prepPhaseStarted(int day) {
        String t = "Hazırlık aşaması. Müşteri yok, süre akmıyor. Dilediğin kadar hazırlık yap, hazır olunca dükkânı aç.";
        if (engine != null && engine.getApprenticeLevel() > 0) t += " Çırak da hazırlık yapacak.";
        return t;
    }

    public String prepPhaseEnded(int day) { return "Dükkân açılıyor."; }

    public String dayStarted(int day, DayConfig cfg) {
        StringBuilder sb = new StringBuilder();
        sb.append("Gün ").append(day).append(" başladı. Süre ").append(Math.round(cfg.duration)).append(" saniye.");
        List<Product> news = Catalog.newProductsOnDay(day);
        if (!news.isEmpty()) {
            sb.append(" Yeni ürün: ");
            for (int i = 0; i < news.size(); i++) { if (i > 0) sb.append(", "); sb.append(news.get(i).name); }
            sb.append('.');
        }
        String ni = newIngredientsText(day);
        if (!ni.isEmpty()) sb.append(' ').append(ni);
        String ps = newSizesText(day);
        if (!ps.isEmpty()) sb.append(' ').append(ps);
        return sb.toString();
    }

    /** Brifing: o gün açılan yeni sos/salata malzemeleri. "Yeni: nar ekşisi." / "Yeni malzemeler: ketçap ve mayonez." Yoksa (veya 1. gün) boş. */
    public static String newIngredientsText(int day) {
        if (day <= 1) return "";
        List<Ingredient> news = Catalog.newIngredientsOnDay(day);
        if (news.isEmpty()) return "";
        List<String> ids = new java.util.ArrayList<>();
        for (Ingredient i : news) ids.add(i.id);
        return (news.size() == 1 ? "Yeni malzeme: " : "Yeni malzemeler: ") + joinNames(ids) + ".";
    }

    /** Brifing: o gün açılan yeni döner boyu ("Yeni boy: yarım ekmek."); yoksa boş. */
    public static String newSizesText(int day) {
        for (PortionSize sz : PortionSize.values()) {
            if (sz.unlockDay == day && day > 1) return "Yeni boy: " + sz.spoken() + ". Döner makinesinde boyu sen seçersin.";
        }
        return "";
    }

    public String customerArrived(Order o) {
        return "Yeni müşteri: " + o.customer.name + ", " + o.itemsText() + " istiyor."
                + (o.lineCount() > 1 ? " " + o.lineCount() + " kalem." : "") + requestSentence(o)
                + " " + o.customer.type.hint + " Sipariş " + o.position + ".";
    }

    public String orderSelected(Order o) {
        String t = "Seçilen sipariş " + o.position + ": " + o.customer.name + ", " + o.itemsText() + ".";
        return t + requestSentence(o);
    }

    public String itemSelected(Order o, int itemIndex) {
        OrderLine l = o.getLine(itemIndex);
        return "Kalem " + (itemIndex + 1) + " seçildi: " + l.product.name + (l.isDone() ? ", hazır." : ".");
    }

    public String nextStepHint(Order o, Step s) {
        return "Önerilen: " + itemPrefix(o, s) + s.name + sizeSuffix(o, s) + ". " + s.station.displayName + " istasyonuna dokun.";
    }

    public String stepStarted(Order o, Step s, int slot, float seconds) {
        return itemPrefix(o, s) + s.name + " başladı" + startedSizeSuffix(o, s) + ", " + fmtSeconds(seconds) + ".";
    }

    public String stepFinished(Order o, Step s, boolean complete) {
        String t = itemPrefix(o, s) + s.name + " bitti.";
        if (!complete) {
            Step n = o.nextStep();
            if (n != null) t += " Önerilen: " + itemPrefix(o, n) + n.name + sizeSuffix(o, n) + ", " + n.station.displayName + ".";
        }
        return t;
    }

    public String wrongStation(Order o, Station tapped, Station expected) {
        return "Yanlış istasyon. Bu sipariş için şimdi " + expected.displayName + " gerekli.";
    }

    public String stepLocked(Order o, Step s) {
        if (s == o.getPackStep()) return "Paketlemeden önce tüm kalemler hazır olmalı.";
        if (s.isTopping()) return "Malzeme eklemeden önce kalemin diğer adımları bitmeli: ısıtma, dilimleme.";
        return "Bu adım için önce kalemin önceki adımları bitmeli.";
    }

    public String noFreeSlot(Order o) {
        return "Boş yuva yok. Önce devam eden bir işlemin bitmesini bekle.";
    }

    public String itemFinished(Order o, int itemIndex) {
        return o.getLine(itemIndex).product.name + " hazır.";
    }

    public String orderReadyToPack(Order o) {
        return "Tüm kalemler hazır. Siparişi paketle, " + Station.PAKET.displayName + ".";
    }

    public String orderReady(Order o) {
        return "Sipariş hazır: " + o.customer.name + ", " + o.itemsText() + ". Teslim et.";
    }

    public String orderDelivered(Order o, int money, int tip, boolean happy) {
        String t = "Teslim edildi. " + fmtMoney(money) + " kazandın";
        if (tip > 0) t += ", " + fmtMoney(tip) + " bahşiş";
        t += ". " + (happy ? (tip > 0 ? "Müşteri çok memnun." : "Müşteri memnun.") : "Müşteri biraz geç kaldı.");
        return t;
    }

    // ---- malzeme (topping) seçimi
    public String toppingSelectionNeeded(Order o, int itemIndex) {
        OrderLine l = o.getLine(itemIndex);
        return "Malzeme seçimi: " + l.product.name + ". Her malzeme bir aç kapa düğmesi. Onaylamadan önce hedefi okuyabilirsin.";
    }

    /** "Hedefi oku": kalemin hedef malzemeleri ve müşteri istekleri. */
    public String toppingTarget(Order o, int itemIndex) {
        OrderLine l = o.getLine(itemIndex);
        Set<String> target = l.getTarget();
        String t = "Hedef, " + l.product.name + ": " + (target.isEmpty() ? "hiç malzeme yok" : joinNames(target)) + ".";
        t += o.hasRequests() ? " Müşteri isteği: " + o.requestsText() + "." : " Müşteri isteği yok, standart malzemeler.";
        return t;
    }

    /** Düğmenin sesli durumu: "Soğan, eklenmedi". */
    public String toppingState(Ingredient ing, boolean selected, boolean available) {
        if (!available && !selected) return ing.name + ", stokta yok";
        return ing.name + ", " + (selected ? "eklendi" : "eklenmedi");
    }

    /** Seçili malzemelerin özeti. */
    public String toppingSelectionSummary(Collection<String> ids) {
        if (ids.isEmpty()) return "Hiç malzeme seçili değil.";
        return "Seçili: " + joinNames(ids) + ". Toplam " + ids.size() + " malzeme.";
    }

    public String toppingsRejected(Order o, int itemIndex, StartResult reason, Ingredient ing) {
        if (reason == StartResult.OUT_OF_STOCK && ing != null) {
            return ing.name + " stokta yok, seçilemez. Marketten acil al, "
                    + fmtMoney(ing.emergencyPrice() * Ingredient.EMERGENCY_BATCH) + ", ya da seçimden çıkar.";
        }
        if (reason == StartResult.INVALID) return "Bu malzeme bu ürüne eklenemez.";
        if (reason == StartResult.MAX_REACHED && ing != null) return ing.name + " zaten bol, en fazla iki kez eklenir.";
        return startResultText(reason);
    }

    // ---- v0.6: tek tek ekleme, boy, eylem menüsü
    /** Ekleme bitti: "Domates eklendi." / ikinci eklemede "Domates bol eklendi." */
    public String toppingAdded(Ingredient ing, int count) {
        return ing.name + (count >= 2 ? " bol eklendi." : " eklendi.");
    }

    public String toppingRemoved(Ingredient ing, int remaining) {
        return ing.name + " geri alındı, stoğa döndü." + (remaining > 0 ? " Bir kez kaldı." : "");
    }

    public String toppingsFinished(Order o, int itemIndex, Collection<String> chosen) {
        return o.getLine(itemIndex).product.name + " malzemeleri tamam. "
                + (chosen == null || chosen.isEmpty() ? "Hiç malzeme eklenmedi." : "Eklenen: " + joinNames(chosen) + ".");
    }

    /** "Eklenenleri oku": eklenen malzemeler ve bol olanlar; hedefle karşılaştırma YAPILMAZ. */
    public String addedText(Order o, int itemIndex) {
        OrderLine l = o.getLine(itemIndex);
        java.util.Map<String, Integer> counts = l.getAddedCounts();
        if (counts.isEmpty()) return "Henüz hiçbir malzeme eklenmedi.";
        StringBuilder sb = new StringBuilder("Eklenenler: ");
        int i = 0, n = counts.size();
        for (java.util.Map.Entry<String, Integer> e : counts.entrySet()) {
            if (i > 0) sb.append(i == n - 1 ? " ve " : ", ");
            Ingredient ing = Catalog.ingredient(e.getKey());
            sb.append((ing == null ? e.getKey() : ing.name).toLowerCase(new java.util.Locale("tr")));
            if (e.getValue() >= 2) sb.append(" bol");
            i++;
        }
        return sb.append('.').toString();
    }

    /** read:added / read:target seçeneklerinin okunacak metni. */
    public String stationInfo(Order o, int itemIndex, String optionId) {
        if ("read:target".equals(optionId)) return toppingTarget(o, itemIndex);
        if ("read:added".equals(optionId)) return addedText(o, itemIndex);
        return "";
    }

    /** Boy seçimi duyurusu: "Duble dilimleniyor." */
    public String sizeChosen(PortionSize chosen) { return chosen.displayName + " dilimleniyor."; }

    /** Seçenek reddedildi (kilitli, azami, geri alınacak yok...). */
    public String optionRejected(Order o, String optionId, StartResult reason) {
        switch (reason) {
            case NOTHING_TO_UNDO: return "Geri alınacak malzeme yok.";
            case LOCKED:
                if (optionId != null && optionId.startsWith("size:")) {
                    PortionSize sz = PortionSize.byId(optionId.substring(5));
                    if (sz != null) return sz.displayName + " boy " + sz.unlockDay + ". günde açılır.";
                }
                return "Bu seçenek henüz açılmadı.";
            case ORDER_BUSY: return "Bir ekleme sürüyor, bitmesini bekle.";
            case UNKNOWN_OPTION: return "Bilinmeyen seçenek.";
            case INVALID: return "Bu seçenek şu an uygulanamaz.";
            default: return startResultText(reason);
        }
    }

    /** Menü seçeneğinin TalkBack etiketi: durumu içerir, kilitliyse nedenini söyler. */
    public String optionLabel(StationOption op) { return op.label(); }

    /** Müşteri şikâyeti: "Soğan istememiştim. Soğan fazla olmuş..." */
    public String orderMistake(Order o, ToppingCheck check, double penalty) {
        StringBuilder sb = new StringBuilder("Hatalı sipariş. ");
        int n = 0;
        for (ToppingCheck.Mistake m : check.getMistakes()) {
            if (n >= 3) break;
            Ingredient ing = Catalog.ingredient(m.ingredientId);
            String name = ing == null ? m.ingredientId : ing.name;
            if (m.extra) sb.append(m.requested ? name + " istememiştim. " : name + " fazla olmuş. ");
            else sb.append(m.requested ? name + " istemiştim. " : name + " eksik olmuş. ");
            n++;
        }
        for (ToppingCheck.SizeMistake m : check.getSizeMistakes()) {
            if (n >= 3) break;
            sb.append(m.ordered.spoken().substring(0, 1).toUpperCase(new java.util.Locale("tr")))
                    .append(m.ordered.spoken().substring(1)).append(" istemiştim, ")
                    .append(m.served.spoken()).append(" yapmışsın. ").append(m.tooMuch() ? "Çok fazla. " : "Çok az. ");
            n++;
        }
        if (check.mistakes() > 3) sb.append("Daha başka hatalar da var. ");
        sb.append("Yıldız düştü, bahşiş yarıya indi.");
        return sb.toString();
    }

    public String orderPerfect(Order o) { return "Malzemeler tam istediği gibi."; }

    // ---- hazırlık
    public String prepStarted(PrepKind kind, boolean byApprentice, float seconds) {
        return kind.displayName + " başladı, " + fmtSeconds(seconds) + ".";
    }

    /** Hazır döner sayacı: yarım porsiyon birimiyle "3,5". */
    static String portionsText(int halves) { return PortionSize.halvesText(halves); }

    public String prepFinished(PrepKind kind, boolean byApprentice, int amount) {
        switch (kind) {
            case DONER: return amount + " porsiyon döner dilimlendi. Hazır döner " + (engine == null ? "" : portionsText(engine.getReadyDonerHalves()) + " porsiyon") + ".";
            case DRINK: return amount + " bardak ayran dolduruldu. Hazır içecek " + (engine == null ? "" : engine.getReadyDrinks() + " bardak") + ".";
            default: return "Salata ve sos tepsisi hazır.";
        }
    }

    public String prepRejected(PrepKind kind, PrepResult reason, Ingredient ing) {
        switch (reason) {
            case FULL: return "Hazır stok dolu, " + kind.displayName + " yapılamaz.";
            case OUT_OF_STOCK: return (ing == null ? "Malzeme" : ing.name) + " yetmiyor, " + kind.displayName + " yapılamaz.";
            case NO_SLOT: return "Boş yuva yok. Önce devam eden bir işlemin bitmesini bekle.";
            case INVALID: return "Bu hazırlık için geçersiz seçim.";
            default: return "Hazırlık yapılamaz.";
        }
    }

    /** Hazır stok durumu: "Hazır: döner 4 porsiyon, ayran 3 bardak, tepsi 12 kullanım." Hiç yoksa boş metin. */
    public String readyStockText() {
        if (engine == null) return "";
        List<String> parts = new java.util.ArrayList<>();
        if (engine.getReadyDonerHalves() > 0) parts.add("döner " + portionsText(engine.getReadyDonerHalves()) + " porsiyon");
        if (engine.getReadyDrinks() > 0) parts.add("ayran " + engine.getReadyDrinks() + " bardak");
        if (engine.getTrayTotal() > 0) parts.add("salata ve sos tepsisi " + engine.getTrayTotal() + " kullanım");
        if (parts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Hazır: ");
        for (int i = 0; i < parts.size(); i++) { if (i > 0) sb.append(", "); sb.append(parts.get(i)); }
        return sb.append('.').toString();
    }

    // ---- çırak
    /** Çırak duyurusu; sessiz kalması gereken durumda null (yalnızca bitişi önemsiz adımlar). */
    public String apprenticeAction(ApprenticeAction a) {
        if (a.pack) {
            return a.started ? "Çırak " + a.order.position + " numaralı siparişi paketliyor."
                    : "Çırak " + a.order.position + " numaralı siparişi paketledi.";
        }
        if (!a.started) {
            return a.isPrep() ? (a.prep == PrepKind.DONER ? "Çırak döner dilimledi." : a.prep == PrepKind.DRINK ? "Çırak ayranları doldurdu."
                    : "Çırak salata ve sos tepsisini hazırladı.") : null;
        }
        if (a.isPrep()) {
            return a.prep == PrepKind.DONER ? "Çırak döner dilimliyor." : a.prep == PrepKind.DRINK ? "Çırak ayran dolduruyor."
                    : "Çırak salata ve sos tepsisi hazırlıyor.";
        }
        return "Çırak " + a.order.position + " numaralı siparişte: " + a.step.name + ".";
    }

    public String patienceWarning(Order o, int level) {
        if (level >= 2) return "Acele et! " + o.customer.name + " gitmek üzere.";
        return "Dikkat: " + o.customer.name + " sabırsızlanıyor.";
    }

    public String customerLeft(Order o) {
        return o.customer.name + " sabrı tükendi ve gitti. Yıldız düştü.";
    }

    public String starsChanged(double oldS, double newS) {
        return "Yıldız " + fmtStars(newS) + " oldu, " + (newS > oldS ? "yükseldi." : "düştü.");
    }

    public String outOfStock(Order o, Ingredient ing) {
        return "Malzeme bitti: " + ing.name + ". Marketten acil al, "
                + fmtMoney(ing.emergencyPrice() * Ingredient.EMERGENCY_BATCH) + ".";
    }

    /** Sıradaki adım için eksik TÜM malzemeleri ve tek dokunuşla toplam acil alım tutarını söyler. */
    public String outOfStock(Order o) {
        List<Ingredient> missing = engine == null ? null : engine.missingIngredients(o.id);
        if (missing == null || missing.isEmpty()) return null;
        if (missing.size() == 1) return outOfStock(o, missing.get(0));
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < missing.size(); i++) {
            if (i > 0) names.append(i == missing.size() - 1 ? " ve " : ", ");
            names.append(missing.get(i).name);
        }
        return "Malzemeler bitti: " + names + ". Marketten hepsini acil al, "
                + fmtMoney(engine.emergencyCostForMissing(o.id)) + ".";
    }

    public String emergencyPurchase(Ingredient ing, int qty, int cost) {
        return qty + " " + ing.name + " alındı, " + fmtMoney(cost) + ".";
    }

    public String notEnoughMoney(int needed) {
        return "Yeterli paran yok. " + fmtMoney(needed) + " gerekli.";
    }

    public String dayFinished(DaySummary s) {
        String t = "Gün " + s.day + " bitti. " + s.delivered + " sipariş teslim, " + s.lost + " müşteri kaybedildi. "
                + "Satış " + fmtMoney(s.revenue) + ", bahşiş " + fmtMoney(s.tips);
        if (s.emergencySpend > 0) t += ", acil alım " + fmtMoney(s.emergencySpend);
        if (s.rentUnpaid() > 0) {
            t += ", kira " + fmtMoney(s.rentDue) + " ama paran yetmediği için yalnızca " + fmtMoney(s.rent)
                    + " ödenebildi, " + fmtMoney(s.rentUnpaid()) + " ödenemedi";
        } else {
            t += ", kira " + fmtMoney(s.rent);
        }
        t += ". Net " + (s.profit() < 0 ? "eksi " + fmtMoney(-s.profit()) : fmtMoney(s.profit()))
                + ". Kasada " + fmtMoney(s.moneyAfter) + ". Yıldız " + fmtStars(s.endStars) + ".";
        if (s.wastedPortions > 0) t += " İsraf: " + s.wastedPortions + " porsiyon hazır malzeme atıldı, değeri " + fmtMoney(s.wastedCost) + ".";
        if (s.mistakes > 0) t += " Hatalı malzemeli sipariş: " + s.mistakes + ".";
        if (s.sizeMistakes > 0) t += " Yanlış boyda dilimlenen sipariş: " + s.sizeMistakes + ".";
        if (s.newProductsNext != null && !s.newProductsNext.isEmpty()) t += " Yarın yeni ürün: " + s.newProductsNext + ".";
        if (s.newIngredientsNext != null && !s.newIngredientsNext.isEmpty()) t += " Yarın yeni malzeme: " + s.newIngredientsNext + ".";
        return t;
    }

    public String dayFailed(DaySummary s) {
        return "Gün " + s.day + " başarısız oldu, itibarın çok düştü. Aynı günü tekrar deneyebilirsin, paran ve stokların korundu.";
    }

    /** Kısa durum özeti: para, yıldız, kalan süre, bekleyen sipariş, hazır stok. */
    public String statusText() {
        if (engine == null) return "";
        int n = engine.getOrders().size();
        String t = "Para " + fmtMoney(engine.getMoney()) + ", yıldız " + fmtStars(engine.getStars());
        if (engine.isPrepPhase()) t += ", hazırlık aşaması, süre akmıyor";
        else if (engine.isOvertime()) t += ", mesai uzatması";
        else t += ", kalan süre " + Math.round(engine.getTimeLeft()) + " saniye";
        t += ", " + (n == 0 ? "bekleyen sipariş yok" : n + " bekleyen sipariş") + ".";
        Order sel = engine.getSelectedOrder();
        if (sel != null) {
            Step s = sel.nextStep();
            t += " Seçili: " + sel.customer.name + ", " + sel.itemsText() + ", "
                    + (sel.isReady() ? "teslime hazır." : s == null ? "işler sürüyor." : "önerilen " + s.name + ".");
            if (sel.hasRequests()) t += " İstek: " + sel.requestsText() + ".";
        }
        String ready = readyStockText();
        if (!ready.isEmpty()) t += " " + ready;
        return t;
    }

    /** İstasyon düğmesi için erişilebilirlik etiketi. */
    public String stationLabel(Station st) {
        boolean busy = false;
        for (ActiveStep a : engine.getActiveSteps()) if (a.step.station == st) busy = true;
        StringBuilder sb = new StringBuilder(st.displayName).append(" istasyonu, ").append(busy ? "çalışıyor" : "boş");
        Order sel = engine.getSelectedOrder();
        if (sel != null && !sel.isReady()) {
            Step need = null;
            OrderLine needLine = null;
            List<OrderLine> lines = new java.util.ArrayList<>(sel.getLines());
            int pref = engine.getSelectedItem();
            if (pref >= 0 && pref < lines.size()) { OrderLine p = lines.remove(pref); lines.add(0, p); }
            for (OrderLine l : lines) {
                for (int i = 0; i < l.stepCount() && need == null; i++) {
                    if (l.isStepOpen(i) && l.step(i).station == st) { need = l.step(i); needLine = l; }
                }
                if (need != null) break;
            }
            if (need == null && st == Station.PAKET && sel.canPack()) need = sel.getPackStep();
            if (need != null) {
                sb.append(", ").append(needLine != null && sel.lineCount() > 1 ? needLine.product.name + " " : "")
                        .append(need.name).append(" için gerekli").append(sizeSuffix(sel, need));
            }
        }
        if (st == Station.DONER && engine.getReadyDonerHalves() > 0) sb.append(", hazır döner ").append(portionsText(engine.getReadyDonerHalves()));
        if (st == Station.ICECEK && engine.getReadyDrinks() > 0) sb.append(", hazır ayran ").append(engine.getReadyDrinks());
        if (st == Station.SOS && engine.getTrayTotal() > 0) sb.append(", hazır tepsi var");
        return sb.toString();
    }

    /** startStep sonucu için kısa açıklama (UI isterse okur). */
    public String startResultText(StartResult r) {
        switch (r) {
            case STARTED: return "";
            case NO_ORDER: return "Önce bir sipariş seç.";
            case WRONG_STATION: return "Yanlış istasyon.";
            case NO_SLOT: return "Boş yuva yok.";
            case OUT_OF_STOCK: return "Malzeme yok.";
            case ORDER_BUSY: return "Bu adım zaten sürüyor.";
            case ORDER_READY: return "Sipariş hazır, teslim et.";
            case NEEDS_TOPPINGS: return "Malzeme seçimi gerekli.";
            case STEP_LOCKED: return "Bu adım henüz açılmadı.";
            case INVALID: return "Geçersiz seçim.";
            case LOCKED: return "Bu seçenek henüz açılmadı.";
            case MAX_REACHED: return "En fazla iki kez, bol eklenir.";
            case NOTHING_TO_UNDO: return "Geri alınacak malzeme yok.";
            case FULL: return "Hazır stok dolu.";
            case INFO: return "Bilgi okundu.";
            case UNKNOWN_OPTION: return "Bilinmeyen seçenek.";
            default: return "Gün bitti.";
        }
    }

    // ------------------------------------------------------------ GameListener
    @Override public void onPrepPhaseStarted(int day) { say(prepPhaseStarted(day), true); }
    @Override public void onPrepPhaseEnded(int day) { say(prepPhaseEnded(day), false); }
    @Override public void onDayStarted(int day, DayConfig c) {
        if (engine != null) starsTracker.reset(engine.getStars());
        say(dayStarted(day, c), false);
    }
    @Override public void onCustomerArrived(Order o) { say(customerArrived(o), false); }
    @Override public void onOrderSelected(Order o) { say(orderSelected(o), true); }
    @Override public void onItemSelected(Order o, int i) { say(itemSelected(o, i), true); }
    @Override public void onNextStepHint(Order o, Step s) { say(nextStepHint(o, s), false); }
    @Override public void onStepStarted(Order o, Step s, int slot, float sec) { say(stepStarted(o, s, slot, sec), false); }
    @Override public void onStepFinished(Order o, Step s, boolean c) { say(stepFinished(o, s, c), false); }
    @Override public void onWrongStation(Order o, Station t, Station e) { say(wrongStation(o, t, e), true); }
    @Override public void onStepLocked(Order o, Step s) { say(stepLocked(o, s), true); }
    @Override public void onNoFreeSlot(Order o) { say(noFreeSlot(o), true); }
    /** Kalem/paketleme hazır duyuruları stepFinished metnine dahildir; ayrıca konuşulmaz. */
    @Override public void onItemFinished(Order o, int i) {}
    @Override public void onOrderReadyToPack(Order o) {}
    @Override public void onOrderReady(Order o) { say(orderReady(o), false); }
    @Override public void onOrderDelivered(Order o, int m, int t, boolean h) { say(orderDelivered(o, m, t, h), false); }
    @Override public void onToppingSelectionNeeded(Order o, int i) { say(toppingSelectionNeeded(o, i), true); }
    @Override public void onToppingsRejected(Order o, int i, StartResult r, Ingredient ing) { say(toppingsRejected(o, i, r, ing), true); }
    @Override public void onOrderMistake(Order o, ToppingCheck c, double p) { say(orderMistake(o, c, p), true); }
    @Override public void onOrderPerfect(Order o) { say(orderPerfect(o), false); }
    @Override public void onPrepStarted(PrepKind k, boolean byApprentice, float sec) {
        if (!byApprentice) say(prepStarted(k, false, sec), false);
    }
    @Override public void onPrepFinished(PrepKind k, boolean byApprentice, int amount) {
        if (!byApprentice) say(prepFinished(k, false, amount), false);
    }
    @Override public void onPrepRejected(PrepKind k, PrepResult r, Ingredient ing) { say(prepRejected(k, r, ing), true); }
    @Override public void onApprenticeAction(ApprenticeAction a) {
        if (engine != null && !engine.getSave().apprenticeAnnounce) return;
        String t = apprenticeAction(a);
        if (t == null) return;
        float now = engine == null ? 0f : engine.getElapsed();
        // paketleme bitişi her zaman söylenir; diğerleri en az APPRENTICE_MIN_GAP arayla
        boolean important = a.pack && !a.started;
        if (!important && engine != null && engine.getState() == GameEngine.State.RUNNING && now - lastApprenticeSay < APPRENTICE_MIN_GAP) return;
        lastApprenticeSay = now;
        say(t, false);
    }
    @Override public void onPatienceWarning(Order o, int l) { say(patienceWarning(o, l), l >= 2); }
    @Override public void onCustomerLeft(Order o) { say(customerLeft(o), true); }
    @Override public void onStarsChanged(double a, double b) {
        if (starsTracker.changed(a, b)) say(starsChanged(a, b), false);
    }
    @Override public void onOutOfStock(Order o, Ingredient i) {
        String all = outOfStock(o);
        say(all != null ? all : outOfStock(o, i), true);
    }
    @Override public void onEmergencyPurchase(Ingredient i, int q, int c) { say(emergencyPurchase(i, q, c), false); }
    @Override public void onNotEnoughMoney(int n) { say(notEnoughMoney(n), true); }
    @Override public void onDayFinished(DaySummary s) { say(dayFinished(s), true); }
    @Override public void onDayFailed(DaySummary s) { say(dayFailed(s), true); }

    // ---- v0.6
    /** Ekleme başlangıcı sessiz (kısa süre); bitiş "Domates eklendi" söyler. */
    @Override public void onToppingAddStarted(Order o, int i, Ingredient ing, int count, float sec) {}
    @Override public void onToppingAdded(Order o, int i, Ingredient ing, int count) { say(toppingAdded(ing, count), false); }
    @Override public void onToppingRemoved(Order o, int i, Ingredient ing, int remaining) { say(toppingRemoved(ing, remaining), false); }
    /** Bitiriş, onStepFinished ("Malzemeleri ekle bitti. Önerilen: ...") ile duyurulur; ayrıca konuşulmaz. */
    @Override public void onToppingsFinished(Order o, int i, java.util.Set<String> chosen) {}
    /** Boy, adım başlangıcı duyurusuna dahildir ("Döneri dilimle başladı, duble, 5 saniye"). */
    @Override public void onSizeChosen(Order o, int i, PortionSize ordered, PortionSize chosen) {}
    @Override public void onStationInfo(Order o, int i, String optionId) { say(stationInfo(o, i, optionId), true); }
    @Override public void onOptionRejected(Order o, String optionId, StartResult r) { say(optionRejected(o, optionId, r), true); }
}
