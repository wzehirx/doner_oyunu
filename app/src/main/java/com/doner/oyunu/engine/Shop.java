package com.doner.oyunu.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/** Market: malzeme ve yükseltme alışverişi. */
public final class Shop {
    private Shop() {}

    public enum BuyResult { OK, NOT_ENOUGH_MONEY, MAX_LEVEL, LOCKED, UNKNOWN, STOCK_FULL }

    /** Marketin bugün sattığı malzemeler (yalnızca açık olanlar; katalog sırası). */
    public static java.util.List<Ingredient> availableIngredients(SaveData save) {
        return Catalog.ingredientsForDay(save.day);
    }

    /** Boy çeşitliliği (yarım/duble) ile et/ekmek talebi: 4. günden itibaren dubleler ortalamayı yükseltir. */
    static double sizeDemand(int day) { return day >= PortionSize.DOUBLE.unlockDay ? 1.12 : 1.0; }

    public static int ingredientCost(String id, int qty) {
        return Catalog.ingredient(id).price * qty;
    }

    /** İstenen adetten, stok kapasitesine sığacak gerçekten alınabilir adet (0 = stok dolu). */
    public static int purchasableQty(SaveData save, String id, int qty) {
        if (Catalog.ingredient(id) == null || qty <= 0) return 0;
        int room = new Upgrades(save).stockCap() - save.inventory.get(id);
        return Math.max(0, Math.min(qty, room));
    }

    /** Kapasiteyi aşmayacak kadar alır; kapasite doluysa STOCK_FULL. */
    public static BuyResult buyIngredient(SaveData save, String id, int qty) {
        Ingredient ing = Catalog.ingredient(id);
        if (ing == null || qty <= 0) return BuyResult.UNKNOWN;
        if (!ing.isOpen(save.day)) return BuyResult.LOCKED; // gün gün açılan sos/salata malzemeleri
        int cap = new Upgrades(save).stockCap();
        int room = cap - save.inventory.get(id);
        if (room <= 0) return BuyResult.STOCK_FULL;
        if (qty > room) qty = room;
        int cost = ing.price * qty;
        if (save.money < cost) return BuyResult.NOT_ENOUGH_MONEY;
        save.money -= cost;
        save.inventory.add(id, qty);
        return BuyResult.OK;
    }

    public static BuyResult buyUpgrade(SaveData save, String upgradeId) {
        Upgrade u = Catalog.upgrade(upgradeId);
        if (u == null) return BuyResult.UNKNOWN;
        int lv = save.level(upgradeId);
        if (lv >= u.maxLevel()) return BuyResult.MAX_LEVEL;
        if (save.day < u.unlockDayForNext(lv)) return BuyResult.LOCKED;
        int price = u.priceForNext(lv);
        if (save.money < price) return BuyResult.NOT_ENOUGH_MONEY;
        save.money -= price;
        save.upgradeLevels.put(upgradeId, lv + 1);
        return BuyResult.OK;
    }

    /** Verilen gün için, mevcut stoğu da düşerek önerilen alım listesi (malzeme id -> adet). */
    public static Map<String, Integer> recommendedPurchase(SaveData save) {
        DayConfig cfg = DayConfig.forDay(save.day);
        Upgrades up = new Upgrades(save);
        double starMult = Math.max(1.0, DayConfig.starMultiplier(save.stars));
        double customers = cfg.duration / cfg.arrivalInterval * starMult * up.arrivalMult() * 1.15;
        double items = customers * cfg.expectedItemsPerOrder();
        java.util.List<Product> menu = Catalog.productsForDay(save.day);
        Map<String, Double> demand = new LinkedHashMap<>();
        for (Product p : menu) {
            for (Map.Entry<String, Integer> e : p.totalNeeds(save.day).entrySet()) {
                Double d = demand.get(e.getKey());
                double mult = Catalog.ingredient(e.getKey()).scalesWithSize() ? sizeDemand(save.day) : 1.0;
                demand.put(e.getKey(), (d == null ? 0 : d) + e.getValue() * mult * items / menu.size());
            }
            // seçilebilir (varsayılan olmayan) malzemeler, bol ekleme ve müşteri istekleri için küçük pay
            for (String t : p.toppingPool(save.day)) {
                if (p.defaultToppings(save.day).contains(t)) continue;
                Double d = demand.get(t);
                demand.put(t, (d == null ? 0 : d) + 0.25 * items / menu.size());
            }
        }
        int cap = up.stockCap();
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : demand.entrySet()) {
            int want = (int) Math.ceil(e.getValue() * 1.25) + 2;
            want = Math.min(want, cap);
            int need = want - save.inventory.get(e.getKey());
            if (need > 0) out.put(e.getKey(), need);
        }
        return out;
    }

    public static int recommendedCost(SaveData save) {
        int c = 0;
        for (Map.Entry<String, Integer> e : recommendedPurchase(save).entrySet()) c += ingredientCost(e.getKey(), e.getValue());
        return c;
    }

    /** "Önerilen malzemeleri al": parası yettiği kadar alır, harcanan parayı döner. */
    public static int buyRecommended(SaveData save) {
        int before = save.money;
        for (Map.Entry<String, Integer> e : recommendedPurchase(save).entrySet()) {
            int qty = e.getValue();
            int price = Catalog.ingredient(e.getKey()).price;
            int afford = Math.min(qty, save.money / price);
            if (afford > 0) buyIngredient(save, e.getKey(), afford);
        }
        return before - save.money;
    }

    /** Yeni oyunun başlangıç stoğu: bedava. */
    static void grantRecommended(SaveData save) {
        for (Map.Entry<String, Integer> e : recommendedPurchase(save).entrySet()) save.inventory.add(e.getKey(), e.getValue());
    }
}
