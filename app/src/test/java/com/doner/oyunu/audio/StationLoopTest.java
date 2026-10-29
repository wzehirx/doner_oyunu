package com.doner.oyunu.audio;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.SystemClock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** İstasyon seslerinin adım süresince sürmesi (şartname §5): döngü, darbeli tekrar, duraklatma, çökmeme. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class StationLoopTest {

    /** Çıkışı kaydeden sahte SoundPool. */
    static final class Rec implements SoundManager.Sink {
        final List<String> names = new ArrayList<>();
        final List<long[]> when = new ArrayList<>();
        final List<float[]> args = new ArrayList<>(); // left,right,rate,loop
        final List<Integer> stopped = new ArrayList<>();
        int next = 1;

        @Override public int start(String name, float l, float r, float rate, int loop) {
            names.add(name);
            when.add(new long[]{SystemClock.uptimeMillis()});
            args.add(new float[]{l, r, rate, loop});
            return next++;
        }

        @Override public void stop(int stream) { stopped.add(stream); }
    }

    private static void idle(long ms) { ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS); }

    private SoundManager sm(Rec rec) {
        Context c = RuntimeEnvironment.getApplication();
        SoundManager s = new SoundManager(c);
        s.setSinkForTest(rec);
        return s;
    }

    @Test public void loopableSoundsLoopForTheWholeStepThenStop() {
        for (String name : SoundManager.LOOPED) {
            Rec r = new Rec();
            SoundManager s = sm(r);
            s.startLoop("k", name, -0.4f, 1f, 3f);
            assertEquals(1, r.names.size());
            assertEquals(name, r.names.get(0));
            assertEquals("SoundPool döngüsü (-1)", -1f, r.args.get(0)[3], 0f);
            idle(2900);
            assertEquals("döngülü ses tekrar tetiklenmez", 1, r.names.size());
            assertTrue(r.stopped.isEmpty());
            assertEquals(1, s.activeLoopCount());
            idle(200);
            assertEquals("süre dolunca durur", 1, r.stopped.size());
            assertEquals(0, s.activeLoopCount());
            s.release();
        }
    }

    @Test public void stopLoopCutsEarlyAndRestartReplaces() {
        Rec r = new Rec();
        SoundManager s = sm(r);
        s.startLoop("p", "sfx_pour", 0.4f, 1f, 5f);
        idle(500);
        s.stopLoop("p");
        assertEquals(1, r.stopped.size());
        assertEquals(0, s.activeLoopCount());
        idle(6000);
        assertEquals("durdurulmuş ses geri gelmez", 1, r.names.size());
        s.startLoop("p", "sfx_pour", 0.4f, 1f, 5f);
        s.startLoop("p", "sfx_wrap", 0.4f, 1f, 5f); // aynı anahtar: eskisi kesilir
        assertEquals(1, s.activeLoopCount());
        assertEquals(2, r.stopped.size());
        s.stopLoop("yok");
        s.release();
    }

    @Test public void pulsedSoundsRepeatEvery035To06SecondsWithRateAndVolumeJitter() {
        for (String name : SoundManager.PULSED) {
            Rec r = new Rec();
            SoundManager s = sm(r);
            s.startLoop("k", name, 0f, 1f, 10f);
            idle(10_500);
            assertTrue("en az 15 vuruş (10 sn / 0,6 sn): " + r.names.size(), r.names.size() >= 15);
            assertTrue("en çok 29 vuruş: " + r.names.size(), r.names.size() <= 29);
            float minRate = 9, maxRate = 0, minVol = 9, maxVol = 0;
            for (int i = 0; i < r.names.size(); i++) {
                assertEquals(name, r.names.get(i));
                assertEquals("darbeli ses döngüsüz", 0f, r.args.get(i)[3], 0f);
                float rate = r.args.get(i)[2];
                minRate = Math.min(minRate, rate); maxRate = Math.max(maxRate, rate);
                assertTrue("perde ±%5: " + rate, rate >= 0.95f - 1e-4f && rate <= 1.05f + 1e-4f);
                float vol = r.args.get(i)[0];
                minVol = Math.min(minVol, vol); maxVol = Math.max(maxVol, vol);
                assertTrue("ses ±%8: " + vol, vol >= 0.92f - 1e-4f && vol <= 1.08f + 1e-4f);
                if (i > 0) {
                    long gap = r.when.get(i)[0] - r.when.get(i - 1)[0];
                    assertTrue("aralık 350-600 ms: " + gap, gap >= 349 && gap <= 601);
                }
            }
            assertTrue("perde rastgele değişmeli", maxRate - minRate > 0.02f);
            assertTrue("ses rastgele değişmeli", maxVol - minVol > 0.03f);
            assertEquals("süre dolunca biter", 0, s.activeLoopCount());
            int n = r.names.size();
            idle(3000);
            assertEquals("süre bitince vuruş olmaz", n, r.names.size());
            s.release();
        }
    }

    @Test public void apprenticeSoundsAreQuietAndCentered() {
        Rec r = new Rec();
        SoundManager s = sm(r);
        s.setSfxVolume(1f);
        s.startLoop("a:1", "sfx_pour", 0f, 0.6f, 2f);
        assertEquals(0.6f, r.args.get(0)[0], 1e-4f);
        assertEquals("pan merkez", r.args.get(0)[0], r.args.get(0)[1], 1e-6f);
        s.startLoop("p", "sfx_wrap", 0.8f, 1f, 2f);
        float[] a = r.args.get(1);
        assertTrue("sağa pan: sol kısık", a[0] < a[1]);
        s.release();
    }

    @Test public void pauseSilencesLoopsAndResumeContinuesRemainingTime() {
        Rec r = new Rec();
        SoundManager s = sm(r);
        s.startLoop("loop", "sfx_grill_sizzle", 0f, 1f, 4f);
        s.startLoop("pulse", "sfx_slice", 0f, 1f, 4f);
        idle(1000);
        int hits = r.names.size();
        s.pauseLoops();
        assertEquals(0, s.activeLoopCount());
        assertTrue("döngü akışı durduruldu", r.stopped.size() >= 1);
        idle(5000);
        assertEquals("duraklatmada yeni vuruş yok", hits, r.names.size());
        s.resumeLoops();
        assertEquals(2, s.activeLoopCount());
        idle(2500);
        assertTrue("devam edince darbeler sürer", r.names.size() > hits + 2);
        idle(1000); // kalan ~3 sn doldu
        assertEquals("kalan süre bitince durur", 0, s.activeLoopCount());
        s.release();
    }

    @Test public void activityPauseAndScreenChangeStopEverything() {
        Rec r = new Rec();
        SoundManager s = sm(r);
        s.startLoop("a", "sfx_pour", 0f, 1f, 9f);
        s.pause();
        assertEquals(0, s.activeLoopCount());
        s.resume(); // etkinlik dönse de oyun duraklatma paneli açıkken sesler kendiliğinden başlamaz
        assertEquals(0, s.activeLoopCount());
        s.resumeLoops();
        assertEquals(1, s.activeLoopCount());
        s.cancelScheduled(); // ekran değişimi
        assertEquals(0, s.activeLoopCount());
        s.startLoop("b", "sfx_slice", 0f, 1f, 9f);
        assertEquals("ekran değişiminden sonra yeni ses çalar", 1, s.activeLoopCount());
        s.release();
    }

    @Test public void badInputAndReleasedManagerNeverCrash() {
        Rec r = new Rec();
        SoundManager s = sm(r);
        s.startLoop(null, "sfx_pour", 0f, 1f, 1f);
        s.startLoop("x", null, 0f, 1f, 1f);
        s.startLoop("x", "yok_boyle_ses", 5f, 9f, 1f);
        s.startLoop("y", "sfx_slice", -9f, -3f, -1f);
        s.startLoop("z", "sfx_slice", 0f, 1f, 0f);
        s.setSfxVolume(0f);
        s.startLoop("w", "sfx_pour", 0f, 1f, 1f);
        s.setSfxVolume(1f);
        idle(2000);
        s.release();
        s.startLoop("late", "sfx_pour", 0f, 1f, 1f);
        s.pauseLoops(); s.resumeLoops(); s.stopLoop("late"); s.stopAllLoops();
        idle(2000);
        // gerçek (sahte olmayan) çıkışla da çökmez
        SoundManager real = new SoundManager(RuntimeEnvironment.getApplication());
        real.startLoop("r", "sfx_slice", 0f, 1f, 2f);
        real.startLoop("r2", "sfx_pour", 0f, 1f, 2f);
        idle(3000);
        real.pause(); real.resume(); real.release();
    }
}
