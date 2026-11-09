package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.Narrator;

import org.junit.Test;

import java.util.Arrays;

/**
 * v2.5 "Stoğu oku" düzeltmesi: oyuncunun açıkça istediği okuma ({@code SpeechQueue.sayExplicit}) her çağrıda hemen ve
 * konuşanı keserek söylenir; aynı metin art arda da okunur; kesilmeyen acil uyarı konuşurken de; oyun duyuruları onu
 * kesmez ama atılmaz.
 */
public class SpeechQueueExplicitTest {

    @Test public void sameTextThreeTimesInARowIsSpokenThreeTimesWithInterrupt() {
        SpeechQueueTest.Rig r = new SpeechQueueTest.Rig();
        for (int i = 0; i < 3; i++) { r.q.sayExplicit("Hazır: döner 4 porsiyon."); r.advance(100); }
        assertEquals(Arrays.asList("Hazır: döner 4 porsiyon.", "Hazır: döner 4 porsiyon.", "Hazır: döner 4 porsiyon."), r.spoken);
        assertEquals(Arrays.asList(true, true, true), r.interrupts);
        assertEquals(3, r.q.explicitCount());
    }

    @Test public void explicitInterruptsNonInterruptibleAlertAndAlertsWaitAfterIt() {
        SpeechQueueTest.Rig r = new SpeechQueueTest.Rig();
        r.duration = 3000;
        r.q.say("Elin dolu.", true, Narrator.CAT_ALERT, null);
        r.advance(200);
        r.q.sayExplicit("Durum.");
        assertEquals("açık okuma kesilmeyen uyarıyı da keser", "Durum.", r.spoken.get(r.spoken.size() - 1));
        // okuma sürerken gelen acil uyarı okumayı kesmez, sonra söylenir
        r.q.say("Ocak dolu (2/2).", true, Narrator.CAT_ALERT, null);
        assertEquals("Durum.", r.spoken.get(r.spoken.size() - 1));
        r.advance(3500);
        assertEquals("Ocak dolu (2/2).", r.spoken.get(r.spoken.size() - 1));
        // oyun anlatımı (acil olmayan) okumayı kesmez
        r.q.sayExplicit("Durum.");
        r.q.say("Müşteri geldi.", false, Narrator.CAT_CUSTOMER, null);
        assertEquals("Durum.", r.spoken.get(r.spoken.size() - 1));
    }

    @Test public void explicitIsNeverDroppedByStalenessGapOrCaps() {
        SpeechQueueTest.Rig r = new SpeechQueueTest.Rig();
        r.duration = 10_000;
        r.q.say("Uzun uyarı.", true, Narrator.CAT_ALERT, null);
        for (int i = 0; i < 5; i++) r.q.say("Uyarı " + i, true, Narrator.CAT_ALERT, null); // sıra sınırı dolar
        r.advance(7000); // bayatlık süresi geçti
        r.q.sayExplicit("Hazır stok yok.");
        assertEquals("Hazır stok yok.", r.spoken.get(r.spoken.size() - 1));
        r.q.sayExplicit("Hazır stok yok.");
        assertEquals(2, r.q.explicitCount());
    }
}
