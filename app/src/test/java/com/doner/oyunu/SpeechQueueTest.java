package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.Narrator;
import com.doner.oyunu.engine.SpeechQueue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * v0.9 konuşma sırası (madde 6): bayat duyuru atılır, aynı kategori birleşir ve aralıklanır, acil olan öne geçer ve keser,
 * ipucu söylenmeden önce yeniden denetlenir, uzun metin bayatlamaz. Sentetik saat ve oynatıcı (JVM, Robolectric yok).
 */
public class SpeechQueueTest {
    /** Oynatılanları ve kesmeleri kaydeden sahte oynatıcı; her duyuru 1 sn sürer. */
    static final class Rig implements SpeechQueue.Clock, SpeechQueue.Player {
        long now;
        final List<String> spoken = new ArrayList<>();
        final List<Boolean> interrupts = new ArrayList<>();
        long duration = 1000;
        final SpeechQueue q = new SpeechQueue(this, this);

        @Override public long now() { return now; }

        @Override public long play(String text, boolean interrupt) {
            spoken.add(text);
            interrupts.add(interrupt);
            return duration;
        }

        /** Saati ilerletir ve sırayı çalıştırır (Speaker'ın zamanlayıcısı gibi, 50 ms adımlarla). */
        void advance(long ms) {
            long end = now + ms;
            while (now < end) {
                now = Math.min(end, now + 50);
                q.pump(now);
            }
        }
    }

    @Test public void idleQueueSpeaksImmediatelyAndInOrderWithoutCategories() {
        Rig r = new Rig();
        r.q.say("Birinci", false);
        r.q.say("İkinci", false);
        r.q.say("Üçüncü", false);
        assertEquals(Arrays.asList("Birinci"), r.spoken);
        r.advance(5000);
        assertEquals("kategorisiz duyurular sırayla okunur", Arrays.asList("Birinci", "İkinci", "Üçüncü"), r.spoken);
    }

    @Test public void staleNonUrgentAnnouncementIsDroppedWhenItsTurnComes() {
        Rig r = new Rig();
        r.duration = 6000;                                  // uzun bir konuşma sürerken
        r.q.say("Uzun duyuru", false, Narrator.CAT_DAY, null);
        r.q.say("Yeni müşteri: Ali", false, Narrator.CAT_CUSTOMER, null);
        r.advance(7000);                                    // sıra gelince 3,5 sn'den fazla beklemiş
        assertEquals(Arrays.asList("Uzun duyuru"), r.spoken);
        assertEquals(1, r.q.droppedCount());
    }

    @Test public void oldPendingMessagesDropWhenANewEventArrives() {
        Rig r = new Rig();
        r.duration = 5000;
        r.q.say("Konuşan", false, Narrator.CAT_DAY, null);
        r.q.say("Eski adım", false, Narrator.CAT_STEP, null);
        r.advance(4000);                                    // eski duyuru bayatladı ve elendi
        r.q.say("Yeni olay", false, Narrator.CAT_CUSTOMER, null); // yeni olay yalnızca kendisi bekler
        assertEquals(1, r.q.pendingCount());
        r.advance(20_000);
        assertEquals(Arrays.asList("Konuşan", "Yeni olay"), r.spoken);
    }

    @Test public void sameCategoryKeepsOnlyTheLatestPendingOne() {
        Rig r = new Rig();
        r.q.say("Başladı 1", false, Narrator.CAT_STEP, null);
        r.q.say("Başladı 2", false, Narrator.CAT_STEP, null);
        r.q.say("Başladı 3", false, Narrator.CAT_STEP, null);
        r.q.say("Başladı 4", false, Narrator.CAT_STEP, null);
        assertEquals("bekleyen tek kalır", 1, r.q.pendingCount());
        r.advance(5000);
        assertEquals(Arrays.asList("Başladı 1", "Başladı 4"), r.spoken);
    }

    @Test public void sameCategoryStartsAtLeast1200MsApart() {
        Rig r = new Rig();
        r.duration = 300;                                   // çok kısa duyurular
        r.q.say("A", false, Narrator.CAT_TOPPING, null);
        r.advance(400);
        r.q.say("B", false, Narrator.CAT_TOPPING, null);
        assertEquals("konuşma boş ama aynı tür 1,2 sn dolmadı: bekler", Arrays.asList("A"), r.spoken);
        r.advance(700);
        assertEquals(Arrays.asList("A"), r.spoken);
        r.advance(200);                                     // t = 1300 >= 1200
        assertEquals(Arrays.asList("A", "B"), r.spoken);
        // farklı tür beklemez
        Rig r2 = new Rig();
        r2.duration = 300;
        r2.q.say("A", false, Narrator.CAT_TOPPING, null);
        r2.advance(400);
        r2.q.say("C", false, Narrator.CAT_STEP, null);
        assertEquals(Arrays.asList("A", "C"), r2.spoken);
    }

    @Test public void urgentCutsTheSpeakingOneAndDropsPendingNonUrgent() {
        Rig r = new Rig();
        r.q.say("Sıradan", false, Narrator.CAT_STEP, null);
        r.q.say("Bekleyen 1", false, Narrator.CAT_CUSTOMER, null);
        r.q.say("Bekleyen 2", false, Narrator.CAT_ORDER, null);
        r.q.say("Sabır kritik", true, Narrator.CAT_PATIENCE, null);
        assertEquals(Arrays.asList("Sıradan", "Sabır kritik"), r.spoken);
        assertEquals("ikincisi konuşmayı kesti", Boolean.TRUE, r.interrupts.get(1));
        assertEquals("bekleyen acil olmayanlar silindi", 0, r.q.pendingCount());
        r.advance(10_000);
        assertEquals(2, r.spoken.size());
    }

    @Test public void urgentAlertIsNotCutByAnotherAlertButGoesNextAndNeverStays() {
        Rig r = new Rig();
        r.q.say("Hata 1", true, Narrator.CAT_ALERT, null);
        r.q.say("Sıradan", false, Narrator.CAT_STEP, null);
        r.q.say("Hata 2", true, Narrator.CAT_PATIENCE, null);
        assertEquals("konuşan hata kesilmez", Arrays.asList("Hata 1"), r.spoken);
        assertEquals("acil olmayan silindi, acil sırada", 1, r.q.pendingCount());
        r.advance(1100);
        assertEquals(Arrays.asList("Hata 1", "Hata 2"), r.spoken);
    }

    @Test public void playersOwnResponsesCanBeCutByTheNextResponse() {
        Rig r = new Rig();
        r.q.say("Seçilen sipariş 1", true, Narrator.CAT_SELECT, null);
        r.q.say("Seçilen sipariş 2", true, Narrator.CAT_SELECT, null);
        assertEquals("art arda seçimde yalnızca sonuncusu okunur", Arrays.asList("Seçilen sipariş 1", "Seçilen sipariş 2"), r.spoken);
        r.q.say("Malzeme bitti", true, Narrator.CAT_ALERT, null);
        assertEquals("hata seçim yanıtını keser", "Malzeme bitti", r.spoken.get(r.spoken.size() - 1));
    }

    @Test public void freshCheckIsRepeatedJustBeforeSpeaking() {
        Rig r = new Rig();
        final boolean[] still = {true};
        Narrator.Fresh f = new Narrator.Fresh() { @Override public boolean ok() { return still[0]; } };
        r.q.say("Ekmeği ısıt bitti.", false, Narrator.CAT_STEP, null);
        r.q.say("Önerilen: Döneri dilimle", false, Narrator.CAT_HINT, f);
        still[0] = false;                                   // oyuncu bu arada adımı başlattı
        r.advance(3000);
        assertEquals(Arrays.asList("Ekmeği ısıt bitti."), r.spoken);
        assertEquals(1, r.q.droppedCount());
        // hâlâ geçerliyse söylenir
        still[0] = true;
        r.q.say("Önerilen: Siparişi paketle", false, Narrator.CAT_HINT, f);
        r.advance(3000);
        assertEquals("Önerilen: Siparişi paketle", r.spoken.get(r.spoken.size() - 1));
    }

    @Test public void longTextNeverGoesStaleAndFlushCutsEverything() {
        Rig r = new Rig();
        r.duration = 20_000;
        r.q.sayLong("Bölüm 1", false);
        r.q.sayLong("Bölüm 2", false);
        r.q.say("Oyun duyurusu", false, Narrator.CAT_STEP, null);
        r.advance(60_000);
        assertEquals("uzun metin bayatlamaz, oyun duyurusu bayatlayıp atıldı", Arrays.asList("Bölüm 1", "Bölüm 2"), r.spoken);
        r.q.sayLong("Bölüm 3", true);
        assertEquals("Bölüm 3", r.spoken.get(r.spoken.size() - 1));
        r.q.sayLong("Bölüm 4", false);
        r.q.sayLong("Yeni", true);                          // flush: bekleyen uzun metin silinir
        assertEquals(0, r.q.pendingCount());
    }

    @Test public void clearSilencesEverythingAndSpeakingContinuesAfter() {
        Rig r = new Rig();
        r.q.say("A", false);
        r.q.say("B", false);
        r.q.clear();
        assertFalse(r.q.isBusy(r.now));
        r.q.say("C", false);
        assertEquals(Arrays.asList("A", "C"), r.spoken);
    }

    @Test public void finishedEarlyOpensTheQueueAtOnce() {
        Rig r = new Rig();
        r.duration = 10_000;
        r.q.say("Uzun", false);
        r.q.say("Sonraki", false);
        r.now = 500;
        r.q.finished(r.now);                                // TTS erken bitti
        assertEquals(Arrays.asList("Uzun", "Sonraki"), r.spoken);
    }

    @Test public void wakeupTimeTellsWhenToPumpNext() {
        Rig r = new Rig();
        r.q.say("A", false, Narrator.CAT_STEP, null);
        assertEquals(1000, r.q.nextWakeup(0));
        r.q.say("B", false, Narrator.CAT_ORDER, null);
        assertTrue(r.q.nextWakeup(0) >= 20);
        r.advance(3000);
        assertEquals(-1, r.q.nextWakeup(r.now));
    }

    @Test public void tutorialHintsUseALongerStaleLimitButStillRespectFreshness() {
        Rig r = new Rig();
        r.duration = 8000;
        r.q.say("Uzun", false);
        final boolean[] ok = {true};
        r.q.say("Öğretici. Müşteri geldi.", false, Narrator.CAT_TUTORIAL, new Narrator.Fresh() { @Override public boolean ok() { return ok[0]; } });
        r.advance(8100);                                    // 3,5 sn'den uzun bekledi ama öğretici ipucu bayatlamaz
        assertEquals("Öğretici. Müşteri geldi.", r.spoken.get(1));
        Rig r2 = new Rig();
        r2.duration = 8000;
        r2.q.say("Uzun", false);
        r2.q.say("Öğretici. Müşteri geldi.", false, Narrator.CAT_TUTORIAL, new Narrator.Fresh() { @Override public boolean ok() { return false; } });
        r2.advance(9000);
        assertEquals("oyuncu eylem yaptıysa susar", 1, r2.spoken.size());
    }
}
