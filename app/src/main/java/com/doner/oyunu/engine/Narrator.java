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

        /**
         * Zengin biçim (v0.9): category aynı türden duyuruları birleştirir/aralıklar (null: birleşme yok); fresh söylenmeden
         * hemen önce denetlenir, false ise duyuru bayattır ve atılır (null: denetim yok). Varsayılan: düz say.
         */
        default void say(String text, boolean urgent, String category, Fresh fresh) { say(text, urgent); }
    }

    /** Duyurunun söylenme anında hâlâ geçerli (bayat değil) olup olmadığı. */
    public interface Fresh { boolean ok(); }

    // konuşma kategorileri (SpeechQueue aynı kategoriyi birleştirir ve en az 1,2 sn arayla söyler)
    public static final String CAT_DAY = "day", CAT_CUSTOMER = "customer", CAT_SELECT = "select", CAT_HINT = "hint",
            CAT_STEP = "step", CAT_ALERT = "alert", CAT_ORDER = "order", CAT_TOPPING = "topping", CAT_PREP = "prep",
            CAT_APPRENTICE = "apprentice", CAT_STARS = "stars", CAT_MONEY = "money", CAT_INFO = "info", CAT_SYSTEM = "system",
            CAT_PATIENCE = "patience", CAT_TUTORIAL = "tutorial";

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

    private void say(String t, boolean urgent, String category) { if (output != null) output.say(t, urgent, category, null); }

    private void say(String t, boolean urgent, String category, Fresh fresh) {
        if (output != null) output.say(t, urgent, category, fresh);
    }

    /**
     * Şimdiki oyuncu eylem sürümüne bağlı denetim: oyuncu bundan sonra herhangi bir eylem yaparsa (adım başlatma, seçim,
     * teslim...) false olur. Öğretici ipuçları "şunu yap" derken bunu kullanır; oyuncu zaten yaptıysa söylenmez.
     */
    public Fresh freshSinceNow() {
        if (engine == null) return null;
        final int v = engine.getActionVersion();
        return new Fresh() { @Override public boolean ok() { return engine.getActionVersion() == v; } };
    }

    /** Önerilen adım duyurusu için denetim: oyuncu eylem yapmadı, sipariş duruyor ve adım hâlâ açık. */
    private Fresh freshHint(final Order o, final Step s) {
        if (engine == null) return null;
        final int v = engine.getActionVersion();
        return new Fresh() {
            @Override public boolean ok() {
                if (engine.getActionVersion() != v || engine.getOrder(o.id) != o || o.isReady()) return false;
                if (s == o.getPackStep()) return o.canPack();
                for (OrderLine l : o.getLines()) {
                    for (int i = 0; i < l.stepCount(); i++) if (l.step(i) == s && l.isStepOpen(i)) return true;
                }
                return false;
            }
        };
    }

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
                    // v0.7: boyu akış seçtiyse kısa duyuru ("boy otomatik: yarım ekmek")
                    if (s.isSized() && engine.getFlowMode().autoSize(sz) && autoSized(o, a)) return ", boy otomatik: " + sz.spoken();
                    return ", " + sz.spoken();
                }
            }
        }
        return sizeSuffix(o, s);
    }

    /** Dilimleme boyu akış tarafından (istenen boy) mı seçildi: kalemin istenen boyu = çalışan işin boyu ve akış o boyu otomatik yapıyor. */
    private boolean autoSized(Order o, ActiveStep a) {
        if (a.itemIndex < 0 || a.itemIndex >= o.lineCount()) return false;
        OrderLine l = o.getLine(a.itemIndex);
        return l.getSize() == a.getSize() && engine.getFlowMode().autoSize(l.getSize());
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
        String t = "Hazırlık aşaması. Müşteri yok, süre akmıyor. Hazır olunca dükkânı aç.";
        if (engine != null && engine.getApprenticeLevel() > 0) t += " Çırak da hazırlık yapacak.";
        return t;
    }

    public String prepPhaseEnded(int day) { return "Dükkân açılıyor."; }

    public String dayStarted(int day, DayConfig cfg) {
        StringBuilder sb = new StringBuilder();
        if (engine != null && !engine.isTimed()) sb.append("Gün ").append(day).append(" başladı. ").append(untimedDayText(engine.getCustomerQuota()));
        else sb.append("Gün ").append(day).append(" başladı. Süre ").append(Math.round(cfg.duration)).append(" saniye.");
        List<Product> news = Catalog.newProductsOnDay(day);
        if (!news.isEmpty()) {
            sb.append(" Yeni ürün: ");
            for (int i = 0; i < news.size(); i++) { if (i > 0) sb.append(", "); sb.append(news.get(i).name); }
            sb.append('.');
        }
        String ni = newIngredientsText(day);
        if (!ni.isEmpty()) sb.append(' ').append(ni);
        String ps = engine != null ? newSizesText(day, engine.getFlowMode()) : newSizesText(day);
        if (!ps.isEmpty()) sb.append(' ').append(ps);
        if (engine != null) sb.append(" Oyun akışı: ").append(engine.getFlowMode().displayName).append('.');
        return sb.toString();
    }

    /** Süresiz gün duyurusu: "Bu gün süre yok, 7 müşteri. Müşteriler beklemekten gitmez; hepsi teslim edilince gün biter." */
    public static String untimedDayText(int quota) {
        return "Bu gün süre yok, " + quota + " müşteri. Müşteriler beklemekten gitmez; hepsi teslim edilince gün biter.";
    }

    /** Süre baskısı değişimi duyurusu (ayar, duraklat menüsü). */
    public String timePressureChanged(boolean timed) {
        if (timed) {
            return "Süre baskısı: açık. Süre sayacı işliyor ve müşteriler sabırsızlanır."
                    + (engine != null && engine.getState() == GameEngine.State.RUNNING && !engine.isPrepPhase()
                    ? " Kalan süre " + Math.round(engine.getTimeLeft()) + " saniye." : "");
        }
        return "Süre baskısı: kapalı. Süre sayacı yok, müşteri sabrı azalmaz, kimse gitmez."
                + (engine != null && engine.getState() == GameEngine.State.RUNNING ? " Kalan müşteri: " + engine.getRemainingCustomers() + "." : "");
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

    /** Brifing: o gün açılan yeni boy, akışa göre ("Rahat akışta boy otomatik seçilir."). Gerçekçi = {@link #newSizesText(int)}. */
    public static String newSizesText(int day, FlowMode mode) {
        String base = newSizesText(day);
        if (base.isEmpty() || mode == null || mode == FlowMode.GERCEKCI) return base;
        for (PortionSize sz : PortionSize.values()) {
            if (sz.unlockDay == day) {
                return "Yeni boy: " + sz.spoken() + ". " + (mode == FlowMode.RAHAT
                        ? "Rahat akışta boy otomatik seçilir."
                        : "Dengeli akışta tam ekmek otomatik; yarım ekmek ya da duble istenince boyu sen seçersin.");
            }
        }
        return base;
    }

    public String customerArrived(Order o) {
        return "Yeni müşteri: " + o.customer.name + ", " + o.itemsText() + "." + requestSentence(o) + " Sipariş " + o.position + ".";
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
        return "Önerilen: " + itemPrefix(o, s) + s.name + sizeSuffix(o, s) + ". " + s.station.displayName + ".";
    }

    public String stepStarted(Order o, Step s, int slot, float seconds) {
        String t = itemPrefix(o, s) + s.name + " başladı" + startedSizeSuffix(o, s) + ", " + fmtSeconds(seconds) + ".";
        // v2.0: pasif iş (ekmek ısıtıcı, ocak) kendi pişirir; oyuncu başka işe geçebilir
        if (engine != null && isPassiveRun(o, s)) t += " Elin boş.";
        return t;
    }

    /** Bu adım şu an pasif iş olarak mı çalışıyor (oyuncunun işi). */
    private boolean isPassiveRun(Order o, Step s) {
        for (ActiveStep a : engine.getActiveSteps()) if (a.order == o && a.step == s && a.worker == 0) return a.isPassive();
        return false;
    }

    public String stepFinished(Order o, Step s, boolean complete) {
        // Sıradaki adım ayrı ("Önerilen") duyurudur ve söylenmeden önce yeniden denetlenir; burada tekrarlanmaz.
        return itemPrefix(o, s) + s.name + " bitti.";
    }

    public String wrongStation(Order o, Station tapped, Station expected) {
        return "Yanlış istasyon. Bu sipariş için şimdi " + expected.displayName + " gerekli.";
    }

    public String stepLocked(Order o, Step s) {
        if (s == o.getPackStep()) return "Paketlemeden önce tüm kalemler hazır olmalı.";
        if (s.isTopping()) return "Malzeme eklemeden önce kalemin diğer adımları bitmeli: ısıtma, dilimleme.";
        return "Bu adım için önce kalemin önceki adımları bitmeli.";
    }

    /** ESKİ (v0.9 ortak yuva); v2.0 motoru {@code onNoFreeSlot} göndermez. */
    public String noFreeSlot(Order o) {
        return "Elin dolu, işin bitmesini bekle.";
    }

    /** v2.0: "Elin dolu: Döneri dilimle sürüyor, 2 saniye." */
    public String handsBusy(Order o, ActiveStep current) {
        if (current == null) return "Elin dolu.";
        String what = current.isPrep() ? current.step.name : (current.order != null && current.order.lineCount() > 1 && current.itemIndex >= 0
                ? current.order.getLine(current.itemIndex).product.name + ": " : "") + current.step.name;
        return "Elin dolu: " + what + " sürüyor, " + fmtSeconds(current.getHandsRemaining()) + ".";
    }

    /** v2.0: "Ocak dolu (2/2)." / "Ekmek ısıtıcı dolu (2/2)." (+ ilk bitecek işin kalan süresi). */
    public String stationFull(Order o, Station st, int used, int capacity) {
        String t = st.displayName + " dolu (" + used + "/" + capacity + ").";
        float first = -1f;
        if (engine != null) for (ActiveStep a : engine.getStationJobs(st)) if (a.isPassive() && (first < 0 || a.getRemaining() < first)) first = a.getRemaining();
        if (first >= 0) t += " İlk yer " + fmtSeconds(first) + " sonra boşalır.";
        return t;
    }

    /** v2.0: istenen iş zaten sürüyor: "Ekmeği ısıt zaten sürüyor, çırak yapıyor, 2 saniye." */
    public String alreadyRunning(Order o, ActiveStep job) {
        if (job == null) return "Bu iş zaten sürüyor.";
        return job.step.name + " zaten sürüyor" + (job.isApprentice() ? ", çırak yapıyor" : "") + ", " + fmtSeconds(job.getRemaining()) + ".";
    }

    public String itemFinished(Order o, int itemIndex) {
        return o.getLine(itemIndex).product.name + " hazır.";
    }

    public String orderReadyToPack(Order o) {
        return "Tüm kalemler hazır. Siparişi paketle.";
    }

    public String orderReady(Order o) {
        return "Sipariş hazır: " + o.customer.name + ". Teslim et.";
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

    // ---- v0.7: otomatik malzeme ve oyun akışı
    private FlowMode mode() { return engine == null ? FlowMode.DEFAULT : engine.getFlowMode(); }

    /** "Malzemeler ekleniyor, 1,7 saniye." / Dengeli "Varsayılan…" / Gerçekçi "Standart…". */
    public String autoToppingsStarted(Order o, int itemIndex, List<Ingredient> adding, float seconds) {
        String who = o.lineCount() > 1 ? o.getLine(itemIndex).product.name + ": " : "";
        String what = mode() == FlowMode.RAHAT ? "Malzemeler" : mode() == FlowMode.DENGELI ? "Varsayılan malzemeler" : "Standart malzemeler";
        return who + what + " ekleniyor, " + fmtSeconds(seconds) + ".";
    }

    private static List<String> ids(List<Ingredient> l) {
        List<String> out = new java.util.ArrayList<>();
        for (Ingredient i : l) out.add(i.id);
        return out;
    }

    /**
     * Otomatik ekleme bitti. Rahat: "Malzemeler eklendi: domates ve marul. İstek uygulandı: soğansız."; Dengeli:
     * "Varsayılan malzemeler eklendi: domates, marul ve soğan." + istek farkı varsa "İstek: soğansız. Farkı sen yap, sonra
     * Malzemeleri bitir."; Gerçekçi: "Standart malzemeler eklendi: …. Gerekirse farkı yap, sonra Malzemeleri bitir."
     * (istek olup olmadığını ele vermez).
     */
    public String toppingsAutoAdded(Order o, int itemIndex, List<Ingredient> added, List<Ingredient> removed, boolean finished) {
        OrderLine l = o.getLine(itemIndex);
        FlowMode m = mode();
        StringBuilder sb = new StringBuilder(o.lineCount() > 1 ? l.product.name + ": " : "");
        String what = m == FlowMode.RAHAT ? "Malzemeler" : m == FlowMode.DENGELI ? "Varsayılan malzemeler" : "Standart malzemeler";
        if (added.isEmpty()) sb.append(m == FlowMode.RAHAT ? "Eklenecek malzeme yok." : "Eklenecek " + what.toLowerCase(new java.util.Locale("tr")).replace("malzemeler", "malzeme") + " yok.");
        else sb.append(what).append(" eklendi: ").append(joinNames(ids(added))).append('.');
        if (!removed.isEmpty()) sb.append(" Çıkarıldı: ").append(joinNames(ids(removed))).append('.');
        if (finished) {
            if (m == FlowMode.RAHAT && o.hasRequests() && !l.getTarget().equals(l.getDefaults())) sb.append(" İstek uygulandı: ").append(o.requestsText()).append('.');
        } else {
            sb.append(" İstek: ").append(o.hasRequests() ? o.requestsText() : "farklı").append(". Farkı sen yap, sonra Malzemeleri bitir.");
        }
        return sb.toString();
    }

    /** Oyun akışı değişti: "Oyun akışı: Rahat. …" */
    public String flowModeChanged(FlowMode mode) { return mode.spoken(); }

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
            case FRIES: return amount + " porsiyon patates kızardı. Hazır patates " + (engine == null ? "" : engine.getReadyFries() + " porsiyon") + ".";
            default: return "Salata ve sos tepsisi hazır.";
        }
    }

    public String prepRejected(PrepKind kind, PrepResult reason, Ingredient ing) {
        switch (reason) {
            case FULL: return "Hazır stok dolu, " + kind.displayName + " yapılamaz.";
            case OUT_OF_STOCK: return (ing == null ? "Malzeme" : ing.name) + " yetmiyor, " + kind.displayName + " yapılamaz.";
            case NO_SLOT:
            case HANDS_BUSY: return "Elin dolu, " + kind.displayName + " için işin bitmesini bekle.";
            case STATION_FULL: return engine != null ? stationFull(null, kind.station, engine.getStationUsed(kind.station), engine.getStationCapacity(kind.station))
                    : kind.station.displayName + " dolu.";
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
        if (engine.getReadyFries() > 0) parts.add("patates " + engine.getReadyFries() + " porsiyon");
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
                    : a.prep == PrepKind.FRIES ? "Çırak patates kızarttı." : "Çırak salata ve sos tepsisini hazırladı.") : null;
        }
        if (a.isPrep()) {
            return a.prep == PrepKind.DONER ? "Çırak döner dilimliyor." : a.prep == PrepKind.DRINK ? "Çırak ayran dolduruyor."
                    : a.prep == PrepKind.FRIES ? "Çırak patates kızartıyor." : "Çırak salata ve sos tepsisi hazırlıyor.";
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
        String t = "Gün " + s.day + " bitti. " + s.delivered + (s.timed ? " sipariş teslim, " + s.lost + " müşteri kaybedildi. "
                : " sipariş teslim, süre baskısı kapalıydı. ")
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

    /** Durum çubuğunun kısa etiketi (v0.9): para, yıldız, süre ya da kalan müşteri, bekleyen sipariş. Ayrıntı {@link #statusText()}. */
    public String statusShort() {
        if (engine == null) return "";
        int n = engine.getOrders().size();
        String t = "Para " + fmtMoney(engine.getMoney()) + ", yıldız " + fmtStars(engine.getStars());
        if (engine.isPrepPhase() || engine.getState() == GameEngine.State.READY) t += ", hazırlık";
        else if (!engine.isTimed()) t += ", kalan müşteri " + engine.getRemainingCustomers();
        else if (engine.isOvertime()) t += ", mesai";
        else t += ", kalan süre " + Math.round(engine.getTimeLeft()) + " saniye";
        return t + ", " + (n == 0 ? "bekleyen sipariş yok" : n + " bekleyen sipariş");
    }

    /** Kısa durum özeti: para, yıldız, kalan süre, bekleyen sipariş, hazır stok. */
    public String statusText() {
        if (engine == null) return "";
        int n = engine.getOrders().size();
        String t = "Para " + fmtMoney(engine.getMoney()) + ", yıldız " + fmtStars(engine.getStars());
        if (engine.isPrepPhase()) t += ", hazırlık aşaması, süre akmıyor";
        else if (!engine.isTimed()) t += ", süre yok, kalan müşteri " + engine.getRemainingCustomers();
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
        if (engine.getState() == GameEngine.State.RUNNING || engine.isPrepPhase()) t += " " + handsText();
        String ready = readyStockText();
        if (!ready.isEmpty()) t += " " + ready;
        t += " Oyun akışı: " + engine.getFlowMode().displayName + ".";
        return t;
    }

    /** İstasyon düğmesi etiketi (v0.9, kısa): ad + durum ("Ekmek ısıtıcı, boş, sıradaki: Ekmeği ısıt"). */
    public String stationLabel(Station st) {
        boolean busy = false;
        for (ActiveStep a : engine.getActiveSteps()) if (a.step.station == st) busy = true;
        StringBuilder sb = new StringBuilder(st.displayName).append(", ");
        int cap = engine.getStationCapacity(st);
        sb.append(busy ? "çalışıyor" : "boş");
        if (cap > 0 && engine.getStationUsed(st) > 0) sb.append(", ").append(engine.getStationUsed(st)).append("/").append(cap).append(" dolu");
        Order sel = engine.getSelectedOrder();
        if (sel != null && !sel.isReady()) {
            Step need = null;
            List<OrderLine> lines = new java.util.ArrayList<>(sel.getLines());
            int pref = engine.getSelectedItem();
            if (pref >= 0 && pref < lines.size()) { OrderLine p = lines.remove(pref); lines.add(0, p); }
            for (OrderLine l : lines) {
                for (int i = 0; i < l.stepCount() && need == null; i++) {
                    if (l.isStepOpen(i) && l.step(i).station == st) need = l.step(i);
                }
                if (need != null) break;
            }
            if (need == null && st == Station.PAKET && sel.canPack()) need = sel.getPackStep();
            if (need != null) sb.append(", sıradaki: ").append(need.name).append(sizeSuffix(sel, need));
        }
        if (st == Station.DONER && engine.getReadyDonerHalves() > 0) sb.append(", hazır ").append(portionsText(engine.getReadyDonerHalves())).append(" porsiyon");
        if (st == Station.ICECEK && engine.getReadyDrinks() > 0) sb.append(", hazır ").append(engine.getReadyDrinks()).append(" bardak ayran");
        if (st == Station.SOS && engine.getTrayTotal() > 0) sb.append(", hazır tepsi ").append(engine.getTrayTotal()).append(" kullanım");
        if (st == Station.IZGARA && engine.getReadyFries() > 0) sb.append(", hazır ").append(engine.getReadyFries()).append(" porsiyon patates");
        return sb.toString();
    }

    // ---- v2.0: eller, istasyonlar, sıradaki sipariş
    /** "Elin boş." / "Elin dolu: Döneri dilimle, 2 saniye." + pişenler: "Pişiyor: Ekmek ısıtıcı 1/2, Ocak 2/2." */
    public String handsText() {
        if (engine == null) return "";
        ActiveStep h = engine.getHandsJob();
        String t = h == null ? "Elin boş." : "Elin dolu: " + h.step.name + ", " + fmtSeconds(h.getHandsRemaining()) + ".";
        List<String> parts = new java.util.ArrayList<>();
        for (Station st : Station.values()) {
            int cap = engine.getStationCapacity(st), used = engine.getStationUsed(st);
            if (cap > 0 && used > 0) parts.add(st.displayName + " " + used + "/" + cap);
        }
        if (!parts.isEmpty()) {
            t += " Pişiyor: ";
            for (int i = 0; i < parts.size(); i++) { if (i > 0) t += ", "; t += parts.get(i); }
            t += ".";
        }
        return t;
    }

    /**
     * Siparişte şimdi yapılabilecekler (UI "Siparişi oku" / kart açıklaması): "Şimdi: Ekmeği ısıt, Ekmek ısıtıcı; Döneri
     * dilimle, Döner makinesi. Bekliyor: Patates kızart, Ocak dolu (2/2). Ayranı doldur sürüyor, 1 saniye, çırak."
     * Teslime hazırsa "Teslime hazır." Sipariş yoksa boş.
     */
    public String readyActionsText(int orderId) {
        if (engine == null) return "";
        List<ReadyAction> acts = engine.getReadyActions(orderId);
        if (acts.isEmpty()) return "";
        if (acts.get(0).kind == ReadyAction.Kind.DELIVER) return "Teslime hazır.";
        StringBuilder now = new StringBuilder(), wait = new StringBuilder(), run = new StringBuilder();
        for (ReadyAction a : acts) {
            StringBuilder b = a.status == ReadyAction.Status.NOW ? now : a.status == ReadyAction.Status.RUNNING ? run : wait;
            if (b.length() > 0) b.append("; ");
            b.append(a.text);
        }
        StringBuilder sb = new StringBuilder();
        if (now.length() > 0) sb.append("Şimdi: ").append(now).append(". ");
        if (wait.length() > 0) sb.append("Bekliyor: ").append(wait).append(". ");
        if (run.length() > 0) sb.append(run).append(". ");
        return sb.toString().trim();
    }

    /** "Sıradaki sipariş" duyurusu: odak siparişi + şimdi yapılabilecekler. Sipariş yoksa "Bekleyen sipariş yok." */
    public String focusText() {
        if (engine == null) return "";
        Order o = engine.getFocusOrder();
        if (o == null) return "Bekleyen sipariş yok.";
        String t = "Sipariş " + o.position + ": " + o.customer.name + ", " + o.itemsText() + "." + requestSentence(o);
        String acts = readyActionsText(o.id);
        return acts.isEmpty() ? t : t + " " + acts;
    }

    /** Sıra özeti metni ({@link QueueSummary#text}). */
    public String queueText() { return engine == null ? "" : engine.getQueueSummary().text; }

    /** startStep sonucu için kısa açıklama (UI isterse okur). */
    public String startResultText(StartResult r) {
        switch (r) {
            case STARTED: return "";
            case NO_ORDER: return "Önce bir sipariş seç.";
            case WRONG_STATION: return "Yanlış istasyon.";
            case NO_SLOT:
            case HANDS_BUSY: return "Elin dolu.";
            case STATION_FULL: return "İstasyon dolu.";
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
    @Override public void onPrepPhaseStarted(int day) { say(prepPhaseStarted(day), true, CAT_DAY); }
    @Override public void onPrepPhaseEnded(int day) { say(prepPhaseEnded(day), false, CAT_DAY); }
    @Override public void onDayStarted(int day, DayConfig c) {
        if (engine != null) starsTracker.reset(engine.getStars());
        say(dayStarted(day, c), false, CAT_DAY);
    }
    @Override public void onCustomerArrived(Order o) { say(customerArrived(o), false, CAT_CUSTOMER); }
    @Override public void onOrderSelected(Order o) { say(orderSelected(o), true, CAT_SELECT); }
    @Override public void onItemSelected(Order o, int i) { say(itemSelected(o, i), true, CAT_SELECT); }
    /** Önerilen adım: söylenmeden önce yeniden denetlenir; oyuncu adımı başlattıysa/başka eylem yaptıysa susar. */
    @Override public void onNextStepHint(Order o, Step s) { say(nextStepHint(o, s), false, CAT_HINT, freshHint(o, s)); }
    @Override public void onStepStarted(Order o, Step s, int slot, float sec) { say(stepStarted(o, s, slot, sec), false, CAT_STEP); }
    @Override public void onStepFinished(Order o, Step s, boolean c) { say(stepFinished(o, s, c), false, CAT_STEP); }
    @Override public void onWrongStation(Order o, Station t, Station e) { say(wrongStation(o, t, e), true, CAT_ALERT); }
    @Override public void onStepLocked(Order o, Step s) { say(stepLocked(o, s), true, CAT_ALERT); }
    @Override public void onNoFreeSlot(Order o) { say(noFreeSlot(o), true, CAT_ALERT); }
    @Override public void onHandsBusy(Order o, ActiveStep current) { say(handsBusy(o, current), true, CAT_ALERT); }
    @Override public void onStationFull(Order o, Station st, int used, int capacity) { say(stationFull(o, st, used, capacity), true, CAT_ALERT); }
    @Override public void onAlreadyRunning(Order o, ActiveStep job) { say(alreadyRunning(o, job), true, CAT_ALERT); }
    /** Kalem/paketleme hazır duyuruları stepFinished metnine dahildir; ayrıca konuşulmaz. */
    @Override public void onItemFinished(Order o, int i) {}
    @Override public void onOrderReadyToPack(Order o) {}
    @Override public void onOrderReady(Order o) { say(orderReady(o), false, CAT_ORDER); }
    @Override public void onOrderDelivered(Order o, int m, int t, boolean h) { say(orderDelivered(o, m, t, h), false, CAT_ORDER); }
    @Override public void onToppingSelectionNeeded(Order o, int i) { say(toppingSelectionNeeded(o, i), true, CAT_ALERT); }
    @Override public void onToppingsRejected(Order o, int i, StartResult r, Ingredient ing) { say(toppingsRejected(o, i, r, ing), true, CAT_ALERT); }
    @Override public void onOrderMistake(Order o, ToppingCheck c, double p) { say(orderMistake(o, c, p), true, CAT_ALERT); }
    @Override public void onOrderPerfect(Order o) { say(orderPerfect(o), false, CAT_ORDER); }
    @Override public void onPrepStarted(PrepKind k, boolean byApprentice, float sec) {
        if (!byApprentice) say(prepStarted(k, false, sec), false, CAT_PREP);
    }
    @Override public void onPrepFinished(PrepKind k, boolean byApprentice, int amount) {
        if (!byApprentice) say(prepFinished(k, false, amount), false, CAT_PREP);
    }
    @Override public void onPrepRejected(PrepKind k, PrepResult r, Ingredient ing) { say(prepRejected(k, r, ing), true, CAT_ALERT); }
    @Override public void onApprenticeAction(ApprenticeAction a) {
        if (engine != null && !engine.getSave().apprenticeAnnounce) return;
        String t = apprenticeAction(a);
        if (t == null) return;
        float now = engine == null ? 0f : engine.getElapsed();
        // v2.0: sipariş işleri (başlangıç) ve paketleme bitişi HER ZAMAN söylenir (konuşma sırası aynı kategoriyi birleştirir);
        // yalnızca hazırlık duyuruları en az APPRENTICE_MIN_GAP arayla
        boolean important = !a.isPrep();
        if (!important && engine != null && engine.getState() == GameEngine.State.RUNNING && now - lastApprenticeSay < APPRENTICE_MIN_GAP) return;
        lastApprenticeSay = now;
        say(t, false, CAT_APPRENTICE);
    }
    @Override public void onPatienceWarning(Order o, int l) { say(patienceWarning(o, l), l >= 2, CAT_PATIENCE); }
    @Override public void onCustomerLeft(Order o) { say(customerLeft(o), true, CAT_ALERT); }
    @Override public void onStarsChanged(double a, double b) {
        if (starsTracker.changed(a, b)) say(starsChanged(a, b), false, CAT_STARS);
    }
    @Override public void onOutOfStock(Order o, Ingredient i) {
        String all = outOfStock(o);
        say(all != null ? all : outOfStock(o, i), true, CAT_ALERT);
    }
    @Override public void onEmergencyPurchase(Ingredient i, int q, int c) { say(emergencyPurchase(i, q, c), false, CAT_MONEY); }
    @Override public void onNotEnoughMoney(int n) { say(notEnoughMoney(n), true, CAT_ALERT); }
    @Override public void onDayFinished(DaySummary s) { say(dayFinished(s), true, CAT_DAY); }
    @Override public void onDayFailed(DaySummary s) { say(dayFailed(s), true, CAT_DAY); }

    // ---- v0.6
    /** Ekleme başlangıcı sessiz (kısa süre); bitiş "Domates eklendi" söyler. */
    @Override public void onToppingAddStarted(Order o, int i, Ingredient ing, int count, float sec) {}
    @Override public void onToppingAdded(Order o, int i, Ingredient ing, int count) { say(toppingAdded(ing, count), false, CAT_TOPPING); }
    @Override public void onToppingRemoved(Order o, int i, Ingredient ing, int remaining) { say(toppingRemoved(ing, remaining), false, CAT_TOPPING); }
    /** Bitiriş, onStepFinished ("Malzemeleri ekle bitti. Önerilen: ...") ile duyurulur; ayrıca konuşulmaz. */
    @Override public void onToppingsFinished(Order o, int i, java.util.Set<String> chosen) {}
    /** Boy, adım başlangıcı duyurusuna dahildir ("Döneri dilimle başladı, duble, 5 saniye"). */
    @Override public void onSizeChosen(Order o, int i, PortionSize ordered, PortionSize chosen) {}
    @Override public void onStationInfo(Order o, int i, String optionId) { say(stationInfo(o, i, optionId), true, CAT_INFO); }
    @Override public void onOptionRejected(Order o, String optionId, StartResult r) { say(optionRejected(o, optionId, r), true, CAT_ALERT); }

    // ---- v0.7
    @Override public void onAutoToppingsStarted(Order o, int i, List<Ingredient> adding, float sec) { say(autoToppingsStarted(o, i, adding, sec), false, CAT_TOPPING); }
    @Override public void onToppingsAutoAdded(Order o, int i, List<Ingredient> added, List<Ingredient> removed, boolean finished) {
        say(toppingsAutoAdded(o, i, added, removed, finished), !finished, CAT_TOPPING);
    }
    @Override public void onFlowModeChanged(FlowMode mode) { say(flowModeChanged(mode), true, CAT_SYSTEM); }
    @Override public void onTimePressureChanged(boolean timed) { say(timePressureChanged(timed), true, CAT_SYSTEM); }
}
