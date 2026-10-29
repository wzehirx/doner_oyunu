package com.doner.oyunu.engine;

/** Motor olayları. Tüm çağrılar tick()/eylem metodunu çağıran iş parçacığında yapılır. */
public interface GameListener {
    // ---- gün
    /** Gün öncesi hazırlık aşaması başladı (beginPrep). Hazırlık gün süresine sayılmaz. */
    void onPrepPhaseStarted(int day);
    /** Hazırlık aşaması bitti (startDay / "Dükkânı aç"); hemen ardından onDayStarted gelir. */
    void onPrepPhaseEnded(int day);
    void onDayStarted(int day, DayConfig config);
    void onDayFinished(DaySummary summary);
    void onDayFailed(DaySummary summary);

    // ---- sipariş / müşteri
    void onCustomerArrived(Order order);
    void onOrderSelected(Order order);
    void onItemSelected(Order order, int itemIndex);
    /** Önerilen sıradaki adım (motor sıra dayatmaz). Paketleme önerisinde step = order.getPackStep(). */
    void onNextStepHint(Order order, Step step);
    void onPatienceWarning(Order order, int level);
    void onCustomerLeft(Order order);

    // ---- adımlar (yalnızca OYUNCU işleri; çırak işleri onApprenticeAction ile gelir)
    void onStepStarted(Order order, Step step, int slot, float seconds);
    /** orderComplete: paketleme bitti ve sipariş hazır. */
    void onStepFinished(Order order, Step step, boolean orderComplete);
    void onWrongStation(Order order, Station tapped, Station expected);
    /** Adım var ama henüz açık değil (ör. topping için önce ısıt/dilimle bitmeli, paketleme için tüm kalemler). */
    void onStepLocked(Order order, Step step);
    void onNoFreeSlot(Order order);

    // ---- kalem / paketleme / teslim (oyuncu veya çırak fark etmez)
    /** Kalemin tüm adımları bitti (kalem hazır). */
    void onItemFinished(Order order, int itemIndex);
    /** Tüm kalemler hazır: "Siparişi paketle" adımı açıldı. */
    void onOrderReadyToPack(Order order);
    /** Paketleme bitti: teslim edilebilir. */
    void onOrderReady(Order order);
    void onOrderDelivered(Order order, int money, int tip, boolean happy);

    // ---- malzeme (topping) seçimi
    /** startStep topping adımına çözüldü: UI malzeme seçimi panelini açmalı. */
    void onToppingSelectionNeeded(Order order, int itemIndex);
    /** confirmToppings reddedildi (stok yok, havuz dışı, yuva yok...). ingredient null olabilir. */
    void onToppingsRejected(Order order, int itemIndex, StartResult reason, Ingredient ingredient);
    /** Teslimde malzeme seçimi hedeften farklı çıktı: müşteri şikâyeti; penalty = düşülen yıldız (pozitif). */
    void onOrderMistake(Order order, ToppingCheck check, double penalty);
    /** Teslimde tüm malzemeler doğruydu (küçük memnuniyet bonusu verildi). */
    void onOrderPerfect(Order order);

    // ---- hazırlık
    /** Bir hazırlık işi başladı (oyuncu veya çırak). */
    void onPrepStarted(PrepKind kind, boolean byApprentice, float seconds);
    /** Hazırlık işi bitti; hazır stok arttı. amount: eklenen porsiyon/bardak/kullanım (tepside malzeme başına). */
    void onPrepFinished(PrepKind kind, boolean byApprentice, int amount);
    /** startPrep reddedildi. ingredient yalnızca OUT_OF_STOCK'ta dolu olabilir. */
    void onPrepRejected(PrepKind kind, PrepResult reason, Ingredient ingredient);

    // ---- çırak
    void onApprenticeAction(ApprenticeAction action);

    // ---- diğer
    void onStarsChanged(double oldStars, double newStars);
    void onOutOfStock(Order order, Ingredient ingredient);
    void onEmergencyPurchase(Ingredient ingredient, int qty, int cost);
    void onNotEnoughMoney(int needed);
}
