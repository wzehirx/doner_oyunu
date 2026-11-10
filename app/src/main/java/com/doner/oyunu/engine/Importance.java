package com.doner.oyunu.engine;

/**
 * v2.7 duyuru önem sınıfı. Oyunun kendi sesli anlatımı (TalkBack açıkken az konuşma) bu sınıfa göre süzülür
 * ({@link NarrationLevel#allows}).
 * <ul>
 * <li>{@link #CRITICAL}: zaman-kritik olaylar: yeni müşteri/sipariş, adım bitti/hazır, hata/uyarı, gün başı/sonu,
 * para ve fatura uyarıları, sabır kritik.</li>
 * <li>{@link #NORMAL}: olay sonuçları: teslim, yıldız değişimi, çırak, malzeme eklendi, ödeme/alışveriş onayı.</li>
 * <li>{@link #VERBOSE}: odak/seçim yankıları, ipuçları, öğretici, adım başladı, ekran/ayar onayları (TalkBack zaten okur).</li>
 * </ul>
 */
public enum Importance { CRITICAL, NORMAL, VERBOSE }
