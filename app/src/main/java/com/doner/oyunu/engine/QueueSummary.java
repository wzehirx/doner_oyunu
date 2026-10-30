package com.doner.oyunu.engine;

/**
 * v2.0: sipariş sırasının kısa özeti ({@link GameEngine#getQueueSummary}); UI durum çubuğu ve "Sıradaki sipariş" düğmesi
 * için. Süresiz modda süre alanları -1'dir.
 */
public final class QueueSummary {
    /** Bekleyen sipariş sayısı (teslime hazır olanlar dahil). */
    public final int orders;
    /** Paketlenmiş, teslim bekleyen sipariş sayısı. */
    public final int readyToDeliver;
    /** Şu an başlatılabilir işi olan sipariş sayısı. */
    public final int workable;
    /** {@link GameEngine#getFocusOrder()} (seçili sipariş; yoksa en acil); yoksa -1. */
    public final int focusOrderId;
    /** En acil sipariş (en az kalan sabır oranı; seçimden bağımsız); yoksa -1. */
    public final int mostUrgentOrderId;
    /** En acil siparişin kalan sabrı (sn); süresiz modda ya da sipariş yoksa -1. */
    public final float mostUrgentSecondsLeft;
    /** En acil siparişin kalan sabır oranı (0..1); sipariş yoksa 1. */
    public final float mostUrgentFraction;
    /** Kısa Türkçe metin: "3 sipariş, 1 teslime hazır. En acil: sipariş 2, Ali, 12 saniye." / "Bekleyen sipariş yok." */
    public final String text;

    QueueSummary(int orders, int readyToDeliver, int workable, int focusOrderId, int mostUrgentOrderId,
                 float mostUrgentSecondsLeft, float mostUrgentFraction, String text) {
        this.orders = orders;
        this.readyToDeliver = readyToDeliver;
        this.workable = workable;
        this.focusOrderId = focusOrderId;
        this.mostUrgentOrderId = mostUrgentOrderId;
        this.mostUrgentSecondsLeft = mostUrgentSecondsLeft;
        this.mostUrgentFraction = mostUrgentFraction;
        this.text = text;
    }

    @Override public String toString() { return text; }
}
