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
    /** ESKİ (v0.9 ortak yuva). v2.0 motoru çağırmaz; yerine {@link #onHandsBusy} / {@link #onStationFull}. */
    @Deprecated void onNoFreeSlot(Order order);

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

    // ---- v0.6: tek tek malzeme ekleme, boy, eylem menüsü
    /** Tek malzeme eklenmeye başlandı (stok düştü); kısa süre (salata 0,6 sn, sos 0,5 sn; tepsiden yarısı). count: bu malzemenin kaçıncı eklemesi (2 = bol). */
    void onToppingAddStarted(Order order, int itemIndex, Ingredient ingredient, int count, float seconds);
    /** Ekleme tamamlandı ("Domates eklendi"). count 2 ise bol. */
    void onToppingAdded(Order order, int itemIndex, Ingredient ingredient, int count);
    /** Son eklenen geri alındı (malzeme stoğa/tepsiye iade edildi). remaining: o malzemenin kalan adedi (0..1). */
    void onToppingRemoved(Order order, int itemIndex, Ingredient ingredient, int remaining);
    /** "Malzemeleri bitir": kalemin malzeme adımı tamamlandı (doğrulama teslimde). chosen havuz sırasında. */
    void onToppingsFinished(Order order, int itemIndex, java.util.Set<String> chosen);
    /** DONER istasyonunda boy seçildi ve dilimleme başladı. Farklıysa boy hatası teslimde cezalandırılır (burada ipucu verilmez). */
    void onSizeChosen(Order order, int itemIndex, PortionSize ordered, PortionSize chosen);
    /** Bilgi seçeneği ("read:added", "read:target"): UI/Narrator metni okur. */
    void onStationInfo(Order order, int itemIndex, String optionId);
    /** applyStationOption reddedildi (kilitli, azami, geri alınacak yok, geçersiz). order null olabilir. */
    void onOptionRejected(Order order, String optionId, StartResult reason);

    // ---- v0.7: oyun akışı (Rahat / Dengeli / Gerçekçi) ve otomatik malzeme
    /** Otomatik malzeme işi başladı ("auto": Rahat'ta hedef küme, Dengeli'de varsayılan küme). adding: eklenecekler (havuz sırasında). */
    void onAutoToppingsStarted(Order order, int itemIndex, java.util.List<Ingredient> adding, float seconds);
    /**
     * Otomatik malzeme işi bitti. added: eklenenler, removed: (Rahat) hedefte olmadığı için çıkarılıp stoğa dönenler.
     * finished: malzeme adımı da bitti mi (Rahat her zaman; Dengeli yalnızca istek farkı yoksa). finished ise ardından
     * onToppingsFinished + onStepFinished gelir; değilse oyuncu farkı (çıkar/ekle) yapıp "Malzemeleri bitir" der.
     */
    void onToppingsAutoAdded(Order order, int itemIndex, java.util.List<Ingredient> added, java.util.List<Ingredient> removed, boolean finished);
    /** Oyun akışı değişti (oyun ortasında da olabilir). */
    void onFlowModeChanged(FlowMode mode);

    // ---- v0.8: süre baskısı
    /** Süre baskısı ayarı değişti (oyun ortasında da olabilir). timed=false: süresiz gün (sayaç yok, sabır sabit). */
    void onTimePressureChanged(boolean timed);

    // ---- v2.0: kaynak modeli (eller + istasyon kapasitesi), sıradaki sipariş
    /**
     * Oyuncunun eli dolu: yeni bir AKTİF iş (ya da pasif işin koyma anı) başlatılamadı. current: eli tutan iş (kalan süresi
     * {@link ActiveStep#getHandsRemaining()}). order: denenen sipariş (hazırlıkta null).
     */
    default void onHandsBusy(Order order, ActiveStep current) {}
    /** Pasif istasyon dolu ("Ocak dolu (2/2)"): used = kullanılan yer, capacity = toplam. order: hazırlıkta null. */
    default void onStationFull(Order order, Station station, int used, int capacity) {}
    /** İstenen iş zaten sürüyor (oyuncu ya da çırak yapıyor). job: süren iş. */
    default void onAlreadyRunning(Order order, ActiveStep job) {}
    /** Pasif işin koyma süresi bitti: eller serbest, iş istasyonda kendi başına sürüyor (yalnızca oyuncu işleri). */
    default void onHandsFree(ActiveStep job) {}
}
