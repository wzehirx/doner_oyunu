package com.doner.oyunu.engine;

import java.util.List;
import java.util.Map;

/**
 * v2.5 satış fiyatı ve talep. Oyuncu her ürünün fiyatını referans (katalog) fiyatın %50–%150'si arasında (v2.7: her tam yüzde; −/+ düğmeleri %5 basamakla)
 * belirler ({@link SaveData#prices}). Talep müşteri tipinin fiyat duyarlılığına ve yıldıza bağlıdır (design_v25 §2).
 * Tüm fiyatlar %100 iken her çarpan tam 1'dir (v2.0 ile aynı).
 */
public final class Pricing {
    private Pricing() {}

    public static final int MIN_PERCENT = 50;
    public static final int MAX_PERCENT = 150;
    public static final int STEP_PERCENT = 5;
    public static final int DEFAULT_PERCENT = 100;
    /** Talep eğimi: D = exp(−K · e' · (oran − 1)). */
    public static final double DEMAND_K = 1.7;
    /** Sipariş içinde ürün seçimi eğimi. */
    public static final double CHOICE_K = 1.5;
    public static final double MIN_DEMAND = 0.15;
    /** Referansın üstünde talebin dışbükeyliği: üs × (1 + CONVEX·(oran−1)) (çok pahalıda talep hızla düşer). */
    public static final double CONVEX = 2.0;
    /** Referansın altında da hafif dışbükey (indirim haberi yayılır): üs × (1 + 1·(1−oran)). */
    public static final double CONVEX_LOW = 1.0;
    /** Ucuz fiyatın sabır bonusu: sabır × (1 + PATIENCE_BONUS·(−v)) (v < 0). */
    public static final double PATIENCE_BONUS = 0.6;
    public static final double MAX_DEMAND = 1.8;
    /** Süresiz modda kota çarpanı sınırları. */
    public static final double MIN_QUOTA_MULT = 0.5, MAX_QUOTA_MULT = 1.5;

    // ------------------------------------------------------------ fiyat ayarı
    /** Ürünün fiyat yüzdesi (50–150; ayarlanmadıysa 100). */
    public static int percent(SaveData save, String productId) {
        Integer v = save.prices.get(productId);
        return v == null ? DEFAULT_PERCENT : v;
    }

    /** Fiyat oranı (1,0 = referans). */
    public static double ratio(SaveData save, String productId) { return percent(save, productId) / 100.0; }

    /**
     * Yüzdeyi 50–150 aralığına sınırlar. v2.7: her TAM yüzde geçerlidir (eskiden %5'in katına yuvarlanırdı); oyuncu
     * yazarak ya da kaydırma çubuğuyla 112 gibi bir değer verebilir. {@link #STEP_PERCENT} yalnızca −/+ düğmelerinin adımıdır.
     */
    public static int clamp(int percent) {
        return Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, percent));
    }

    /** Ürünün fiyat yüzdesini ayarlar (50–150'ye sınırlanır, tam yüzde). Bilinmeyen ürün: false. Gün başından itibaren geçerlidir. */
    public static boolean setPercent(SaveData save, String productId, int percent) {
        if (Catalog.product(productId) == null) return false;
        int p = clamp(percent);
        if (p == DEFAULT_PERCENT) save.prices.remove(productId); else save.prices.put(productId, p);
        return true;
    }

    /** Fiyatı bir sonraki %5'lik basamağa çıkarır (112 -> 115, 115 -> 120); yeni yüzde. */
    public static int raise(SaveData save, String productId) {
        int p = percent(save, productId);
        setPercent(save, productId, (p / STEP_PERCENT + 1) * STEP_PERCENT);
        return percent(save, productId);
    }

    /** Fiyatı bir önceki %5'lik basamağa indirir (112 -> 110, 110 -> 105); yeni yüzde. */
    public static int lower(SaveData save, String productId) {
        int p = percent(save, productId);
        setPercent(save, productId, ((p + STEP_PERCENT - 1) / STEP_PERCENT - 1) * STEP_PERCENT);
        return percent(save, productId);
    }

    /** Tüm ürünlere aynı yüzde. */
    public static void setAll(SaveData save, int percent) {
        for (Product p : Catalog.products()) setPercent(save, p.id, percent);
    }

    public static boolean allDefault(SaveData save) { return save.prices.isEmpty(); }

    /** Ürünün tam ekmek satış fiyatı (TL): referans × oran. */
    public static int priceOf(SaveData save, Product p) { return (int) Math.round(p.price * ratio(save, p.id)); }

    /** Referans fiyat (katalog). */
    public static int referencePrice(Product p) { return p.price; }

    // ------------------------------------------------------------ talep
    /** Etkin duyarlılık: e × (1,15 − 0,05·yıldız) (5★'da %10 daha az, 1★'da %10 daha çok). */
    public static double sensitivity(CustomerType t, double stars) {
        double s = Math.max(1.0, Math.min(5.0, stars));
        return t.priceSensitivity * (1.15 - 0.05 * s);
    }

    /**
     * Talep çarpanı: sınırla(exp(−K·e'·x·(1 + 2·max(0, x) + max(0, −x))), 0,15, 1,8), x = oran − 1. İndirimde talep
     * artar (indirim haberi yayılır), pahalıda giderek hızlanan düşüş (çok pahalı menüye müşteri gelmez). Oran 1 iken tam 1.
     */
    public static double demand(double ratio, double eff) {
        if (ratio == 1.0) return 1.0;
        double x = ratio - 1.0;
        double exp = -DEMAND_K * eff * x * (1 + CONVEX * Math.max(0.0, x) + CONVEX_LOW * Math.max(0.0, -x));
        return Math.max(MIN_DEMAND, Math.min(MAX_DEMAND, Math.exp(exp)));
    }

    /** Sipariş içinde ürün seçimi ağırlık çarpanı: exp(−1,5·e'·(oran−1)). */
    public static double choiceWeight(double ratio, double eff) {
        if (ratio == 1.0) return 1.0;
        return Math.exp(-CHOICE_K * eff * (ratio - 1.0));
    }

    /** Tipin sepet oranı: tipin seçim ağırlığı (tercih 3, diğer 1) × referans fiyatla ağırlıklı ortalama fiyat oranı. */
    public static double basketRatio(SaveData save, CustomerType t, List<Product> menu) {
        if (allDefault(save) || menu.isEmpty()) return 1.0;
        double num = 0, den = 0;
        for (Product p : menu) {
            double w = (t.prefers(p.id) ? 3.0 : 1.0) * p.price;
            num += w * ratio(save, p.id);
            den += w;
        }
        return den <= 0 ? 1.0 : num / den;
    }

    /** Müşteri tipinin bugünkü fiyatlarla talep çarpanı. */
    public static double typeDemand(SaveData save, CustomerType t, List<Product> menu, double stars) {
        if (allDefault(save)) return 1.0;
        return demand(basketRatio(save, t, menu), sensitivity(t, stars));
    }

    /**
     * Genel müşteri geliş çarpanı A = Σ W_t·D_t / Σ W_t (W_t: tip ağırlığı × turist çarpanı). Tüm fiyatlar %100 ise 1.
     * UI fiyat ekranında "tahmini müşteri: %+N" için.
     */
    public static double arrivalMultiplier(SaveData save) {
        if (allDefault(save)) return 1.0;
        UtilityStatus u = UtilityStatus.of(save, save.day);
        List<Product> menu = new java.util.ArrayList<>();
        for (Product p : Catalog.productsForDay(save.day)) if (u.productAvailable(p)) menu.add(p);
        if (menu.isEmpty()) menu = Catalog.productsForDay(save.day);
        return arrivalMultiplier(save, menu, Catalog.typesForDay(save.day), new Upgrades(save), save.stars);
    }

    static double arrivalMultiplier(SaveData save, List<Product> menu, List<CustomerType> types, Upgrades up, double stars) {
        if (allDefault(save)) return 1.0;
        double num = 0, den = 0;
        for (CustomerType t : types) {
            double w = t.weight * (t.id.equals("turist") ? up.touristMult() : 1.0);
            num += w * typeDemand(save, t, menu, stars);
            den += w;
        }
        return den <= 0 ? 1.0 : num / den;
    }

    /** Teslimde fiyat memnuniyeti (design_v25 §2). */
    public static final class Feel {
        /** v = (sipariş oranı − 1) × e'. */
        public final double v;
        public final double starMult, tipMult, penalty;
        /** −1 "uygun buldu", 0 sessiz, +1 "pahalı buldu". */
        public final int reaction;
        Feel(double v) {
            this.v = v;
            this.starMult = v == 0 ? 1.0 : Math.max(0.0, Math.min(1.3, 1 - 1.5 * v));
            this.tipMult = v == 0 ? 1.0 : Math.max(0.3, Math.min(1.3, 1 - 1.2 * v));
            this.penalty = v > COMPLAINT_V ? Math.min(0.15, 0.3 * (v - COMPLAINT_V)) : 0.0;
            this.reaction = v > COMPLAINT_V ? 1 : v < -0.15 ? -1 : 0;
        }
    }

    /** Bu memnuniyetsizliğin üstünde müşteri "pahalı" der ve ek yıldız cezası verir. */
    public static final double COMPLAINT_V = 0.2;

    /** Ucuz fiyatın sabır çarpanı: 1 + 0,6·(−v) (v < 0); referansta ve pahalıda 1. */
    public static double patienceMult(double orderRatio, double eff) {
        if (orderRatio >= 1.0) return 1.0;
        return 1.0 + PATIENCE_BONUS * (1.0 - orderRatio) * eff;
    }

    /** Siparişin fiyat oranı ve tipin etkin duyarlılığıyla memnuniyet. */
    public static Feel feel(double orderRatio, double eff) { return new Feel(orderRatio == 1.0 ? 0.0 : (orderRatio - 1.0) * eff); }

    /** Yüzde metni: "yüzde 120" (TalkBack için "%" yerine). */
    public static String percentText(int percent) { return "yüzde " + percent; }

    /** Kayıt uyumu: geçersiz yüzdeleri atar. */
    static void sanitize(Map<String, Integer> prices) {
        for (String id : new java.util.ArrayList<>(prices.keySet())) {
            Integer v = prices.get(id);
            if (Catalog.product(id) == null || v == null) { prices.remove(id); continue; }
            int c = clamp(v);
            if (c == DEFAULT_PERCENT) prices.remove(id); else prices.put(id, c);
        }
    }
}
