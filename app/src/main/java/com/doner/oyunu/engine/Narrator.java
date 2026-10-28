package com.doner.oyunu.engine;

import java.util.List;

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

    private final GameEngine engine;
    private Output output;

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

    // ------------------------------------------------------------ metin üreticiler
    public String dayStarted(int day, DayConfig cfg) {
        StringBuilder sb = new StringBuilder();
        sb.append("Gün ").append(day).append(" başladı. Süre ").append(Math.round(cfg.duration)).append(" saniye.");
        List<Product> news = Catalog.newProductsOnDay(day);
        if (!news.isEmpty()) {
            sb.append(" Yeni ürün: ");
            for (int i = 0; i < news.size(); i++) { if (i > 0) sb.append(", "); sb.append(news.get(i).name); }
            sb.append('.');
        }
        return sb.toString();
    }

    public String customerArrived(Order o) {
        return "Yeni müşteri: " + o.customer.name + ", " + o.itemsText() + " istiyor. "
                + o.customer.type.hint + " Sipariş " + o.position + ".";
    }

    public String orderSelected(Order o) {
        return "Seçilen sipariş " + o.position + ": " + o.customer.name + ", " + o.itemsText() + ".";
    }

    public String nextStepHint(Order o, Step s) {
        return "Sıradaki adım: " + s.name + ". " + s.station.displayName + " istasyonuna dokun.";
    }

    public String stepStarted(Order o, Step s, int slot, float seconds) {
        return s.name + " başladı, " + fmtSeconds(seconds) + ".";
    }

    public String stepFinished(Order o, Step s, boolean complete) {
        String t = s.name + " bitti.";
        if (!complete) {
            Step n = o.nextStep();
            if (n != null) t += " Sıradaki: " + n.name + ", " + n.station.displayName + ".";
        }
        return t;
    }

    public String wrongStation(Order o, Station tapped, Station expected) {
        return "Yanlış istasyon. Bu adım için " + expected.displayName + " gerekli.";
    }

    public String noFreeSlot(Order o) {
        return "Boş yuva yok. Önce devam eden bir işlemin bitmesini bekle.";
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
        t += ", kira " + fmtMoney(s.rent) + ". Net " + (s.profit() < 0 ? "eksi " + fmtMoney(-s.profit()) : fmtMoney(s.profit()))
                + ". Kasada " + fmtMoney(s.moneyAfter) + ". Yıldız " + fmtStars(s.endStars) + ".";
        if (s.newProductsNext != null && !s.newProductsNext.isEmpty()) t += " Yarın yeni ürün: " + s.newProductsNext + ".";
        return t;
    }

    public String dayFailed(DaySummary s) {
        return "Gün " + s.day + " başarısız oldu, itibarın çok düştü. Aynı günü tekrar deneyebilirsin, paran ve stokların korundu.";
    }

    /** Kısa durum özeti: para, yıldız, kalan süre, bekleyen sipariş. */
    public String statusText() {
        if (engine == null) return "";
        int n = engine.getOrders().size();
        String t = "Para " + fmtMoney(engine.getMoney()) + ", yıldız " + fmtStars(engine.getStars());
        if (engine.isOvertime()) t += ", mesai uzatması";
        else t += ", kalan süre " + Math.round(engine.getTimeLeft()) + " saniye";
        t += ", " + (n == 0 ? "bekleyen sipariş yok" : n + " bekleyen sipariş") + ".";
        Order sel = engine.getSelectedOrder();
        if (sel != null) {
            Step s = sel.nextStep();
            t += " Seçili: " + sel.customer.name + ", " + (s == null ? "teslime hazır." : "sıradaki " + s.name + ".");
        }
        return t;
    }

    /** İstasyon düğmesi için erişilebilirlik etiketi. */
    public String stationLabel(Station st) {
        boolean busy = false;
        for (ActiveStep a : engine.getActiveSteps()) if (a.step.station == st) busy = true;
        StringBuilder sb = new StringBuilder(st.displayName).append(" istasyonu, ").append(busy ? "çalışıyor" : "boş");
        Order sel = engine.getSelectedOrder();
        if (sel != null && sel.nextStep() != null && !sel.isRunning() && sel.nextStep().station == st) {
            sb.append(", ").append(sel.nextStep().name).append(" için gerekli");
        }
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
            case ORDER_BUSY: return "Bu siparişin adımı zaten sürüyor.";
            case ORDER_READY: return "Sipariş hazır, teslim et.";
            default: return "Gün bitti.";
        }
    }

    // ------------------------------------------------------------ GameListener
    @Override public void onDayStarted(int day, DayConfig c) { say(dayStarted(day, c), false); }
    @Override public void onCustomerArrived(Order o) { say(customerArrived(o), false); }
    @Override public void onOrderSelected(Order o) { say(orderSelected(o), true); }
    @Override public void onNextStepHint(Order o, Step s) { say(nextStepHint(o, s), false); }
    @Override public void onStepStarted(Order o, Step s, int slot, float sec) { say(stepStarted(o, s, slot, sec), false); }
    @Override public void onStepFinished(Order o, Step s, boolean c) { say(stepFinished(o, s, c), false); }
    @Override public void onWrongStation(Order o, Station t, Station e) { say(wrongStation(o, t, e), true); }
    @Override public void onNoFreeSlot(Order o) { say(noFreeSlot(o), true); }
    @Override public void onOrderReady(Order o) { say(orderReady(o), false); }
    @Override public void onOrderDelivered(Order o, int m, int t, boolean h) { say(orderDelivered(o, m, t, h), false); }
    @Override public void onPatienceWarning(Order o, int l) { say(patienceWarning(o, l), l >= 2); }
    @Override public void onCustomerLeft(Order o) { say(customerLeft(o), true); }
    @Override public void onStarsChanged(double a, double b) { say(starsChanged(a, b), false); }
    @Override public void onOutOfStock(Order o, Ingredient i) { say(outOfStock(o, i), true); }
    @Override public void onEmergencyPurchase(Ingredient i, int q, int c) { say(emergencyPurchase(i, q, c), false); }
    @Override public void onNotEnoughMoney(int n) { say(notEnoughMoney(n), true); }
    @Override public void onDayFinished(DaySummary s) { say(dayFinished(s), true); }
    @Override public void onDayFailed(DaySummary s) { say(dayFailed(s), true); }
}
