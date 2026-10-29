package com.doner.oyunu;

import static org.junit.Assert.*;

import com.doner.oyunu.engine.GameEngine;
import com.doner.oyunu.engine.Narrator;
import com.doner.oyunu.engine.Order;
import com.doner.oyunu.engine.SaveData;
import com.doner.oyunu.engine.SpeechQueue;
import com.doner.oyunu.engine.StartResult;
import com.doner.oyunu.engine.Station;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * v0.9 madde 6: gerçek motor + Narrator + SpeechQueue (sentetik saat/oynatıcı). Oyuncu bir adımı bitirir bitirmez
 * sıradakini başlatırsa "Önerilen: ..." ipucu bayat olur ve söylenmez; öğretici ipucu oyuncu eyleminden sonra susar.
 */
public class SpeechScenarioTest {
    /** Motor süresine bağlı saat (ms) ve söyleneni kaydeden oynatıcı. */
    static final class Rig implements SpeechQueue.Clock, SpeechQueue.Player {
        final GameEngine e;
        final SpeechQueue q;
        final Narrator n;
        final List<String> spoken = new ArrayList<>();
        long extra;

        Rig(GameEngine e) {
            this.e = e;
            q = new SpeechQueue(this, this);
            n = new Narrator(e, q);
            e.addListener(n);
        }

        @Override public long now() { return Math.round(e.getElapsed() * 1000f) + extra; }

        @Override public long play(String text, boolean interrupt) {
            spoken.add(text);
            return 450 + 62L * text.length();
        }

        /** Motoru ve konuşma sırasını birlikte ilerletir (0,1 sn adımlarla, Speaker'ın zamanlayıcısı gibi). */
        void tick(float seconds) {
            for (float t = 0; t < seconds - 1e-4f; t += 0.1f) {
                e.tick(0.1f);
                q.pump(now());
            }
        }

        boolean said(String part) {
            for (String s : spoken) if (s.contains(part)) return true;
            return false;
        }
    }

    private static Rig rig(int seed) {
        GameEngine e = new GameEngine(seed, SaveData.newGame());
        e.setSpawning(false);
        e.startDay();
        return new Rig(e);
    }

    @Test public void breadDoneThenSlicingImmediatelyNeverHearsTheStaleSliceHint() {
        Rig r = rig(7);
        Order o = r.e.spawnOrder(Arrays.asList("ekmek_arasi"), Collections.<String>emptyList());
        r.e.selectOrder(o.id);                              // sipariş seçili: ipuçları açık
        r.tick(6f);                                         // önceki duyurular okunur
        assertEquals(StartResult.STARTED, r.e.startStep(o.id, Station.EKMEK.id));
        // ekmek ısınır; biter bitmez oyuncu 0,2 sn içinde döner dilimlemeye başlar
        for (int i = 0; i < 600 && o.getLine(0).isStepRunning(0); i++) r.tick(0.1f);
        assertTrue("ekmek bitti", o.getLine(0).isStepDone(0));
        r.tick(0.2f);
        assertEquals(StartResult.STARTED, r.e.startStep(o.id, Station.DONER.id));
        r.tick(10f);
        assertTrue(r.spoken.toString(), r.said("Ekmeği ısıt bitti."));
        assertFalse("oyuncu dilimlemeye başladıktan sonra 'Önerilen: Döneri dilimle' söylenmemeli: " + r.spoken,
                r.said("Önerilen: Döneri dilimle"));
        assertTrue("başlama duyurusu söylenir", r.said("Döneri dilimle başladı"));
    }

    @Test public void breadDoneAndPlayerWaitingStillHearsTheSliceHintOnce() {
        Rig r = rig(8);
        Order o = r.e.spawnOrder(Arrays.asList("ekmek_arasi"), Collections.<String>emptyList());
        r.e.selectOrder(o.id);
        r.tick(6f);
        r.e.startStep(o.id, Station.EKMEK.id);
        for (int i = 0; i < 600 && o.getLine(0).isStepRunning(0); i++) r.tick(0.1f);
        r.tick(6f);                                         // oyuncu beklemede
        int hints = 0;
        for (String s : r.spoken) if (s.startsWith("Önerilen: Döneri dilimle")) hints++;
        assertEquals("ipucu bir kez, kısa biçimde: " + r.spoken, 1, hints);
        assertFalse("bitiş duyurusu sıradakini tekrar etmez", r.said("Ekmeği ısıt bitti. Önerilen"));
    }

    @Test public void tutorialHintStaysSilentAfterThePlayerActed() {
        Rig r = rig(9);
        Order o = r.e.spawnOrder(Arrays.asList("ekmek_arasi"), Collections.<String>emptyList());
        // başka bir duyuru konuşurken (kuyrukta bekler) öğretici ipucu gelir: "Önerilen istasyona dokun"
        r.q.say("Uzun bir önceki duyuru, bitmesi zaman alacak kadar uzun bir cümle", false);
        r.q.say("Öğretici. Önerilen istasyona dokun.", false, Narrator.CAT_TUTORIAL, r.n.freshSinceNow());
        assertEquals(StartResult.STARTED, r.e.startStep(o.id, Station.EKMEK.id)); // oyuncu ipucunu beklemeden yaptı
        r.tick(14f);
        assertFalse("oyuncu eylem yaptıktan sonra öğretici ipucu gecikmeli söylenmemeli: " + r.spoken, r.said("Öğretici. Önerilen"));
        // eylem yapılmadıysa söylenir
        Rig r2 = rig(10);
        r2.e.spawnOrder(Arrays.asList("ekmek_arasi"), Collections.<String>emptyList());
        r2.q.say("Uzun bir önceki duyuru, bitmesi zaman alacak kadar uzun bir cümle", false);
        r2.q.say("Öğretici. Önerilen istasyona dokun.", false, Narrator.CAT_TUTORIAL, r2.n.freshSinceNow());
        r2.tick(14f);
        assertTrue(r2.spoken.toString(), r2.said("Öğretici. Önerilen istasyona dokun."));
    }

    @Test public void freshSinceNowIsFalseAfterSelectingOrdersTooAndTrueWhenNothingHappened() {
        Rig r = rig(11);
        Order o = r.e.spawnOrder(Arrays.asList("ekmek_arasi", "ayran"), Collections.<String>emptyList());
        Narrator.Fresh f = r.n.freshSinceNow();
        assertTrue(f.ok());
        r.e.selectOrder(o.id);
        assertFalse("sipariş seçimi de oyuncu eylemidir", f.ok());
        Narrator.Fresh g = r.n.freshSinceNow();
        r.tick(3f);
        assertTrue("zamanla (oyuncu eylemi olmadan) bayatlamaz", g.ok());
        r.e.selectItem(1);
        assertFalse(g.ok());
    }

    @Test public void staleCustomerArrivalIsNotReadAfterTheQueueWasBusyTooLong() {
        Rig r = rig(12);
        // gün sonu gibi uzun ve acil bir duyuru konuşurken iki müşteri gelir; sıra boşalınca bayat olanlar okunmaz
        r.q.say("Uzun gün sonu özeti, bitmesi epey zaman alan uzun bir cümle olarak okunuyor burada, hâlâ okunuyor.", true, Narrator.CAT_ALERT, null);
        r.e.spawnOrder(Arrays.asList("ekmek_arasi"), Collections.<String>emptyList());
        r.e.spawnOrder(Arrays.asList("ayran"), Collections.<String>emptyList());
        r.tick(12f);
        int arrivals = 0;
        for (String s : r.spoken) if (s.startsWith("Yeni müşteri")) arrivals++;
        assertEquals("bayatlayan müşteri duyuruları okunmaz: " + r.spoken, 0, arrivals);
        // sıra boşken gelen müşteri hemen okunur
        r.e.spawnOrder(Arrays.asList("ekmek_arasi"), Collections.<String>emptyList());
        r.tick(1f);
        assertTrue(r.spoken.toString(), r.said("Yeni müşteri"));
    }

    @Test public void urgentPatienceWarningJumpsAheadOfTheBacklog() {
        Rig r = rig(13);
        r.q.say("Sıradan duyuru bir", false, Narrator.CAT_DAY, null);
        r.q.say("Sıradan duyuru iki", false, Narrator.CAT_ORDER, null);
        Order o = r.e.spawnOrder(Arrays.asList("ekmek_arasi"), Collections.<String>emptyList());
        // sabır kritik seviyesine kadar bekle
        for (int i = 0; i < 6000 && !r.said("gitmek üzere"); i++) r.tick(0.1f);
        assertTrue(r.spoken.toString(), r.said("gitmek üzere"));
        assertTrue("acil olan arkada kalmaz: " + r.spoken, r.spoken.indexOf(r.spoken.get(0)) == 0);
        assertNotNull(o);
    }

    @Test public void narratorKeepsAnnouncementsShort() {
        Rig r = rig(14);
        Order o = r.e.spawnOrder(Arrays.asList("ekmek_arasi", "ayran"), Arrays.asList("soğansız"));
        String t = r.n.customerArrived(o);
        assertTrue(t, t.length() <= 90);
        assertFalse("müşteri tipi cümlesi yok", t.contains("Acelesi var") || t.contains("Sabırlı"));
        assertTrue(r.n.nextStepHint(o, o.nextStep()).length() <= 70);
        assertEquals("Ekmeği ısıt bitti.", r.n.stepFinished(o, o.nextStep(), false).replace("Ekmek arası döner: ", ""));
    }
}
