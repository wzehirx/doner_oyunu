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

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * v2.0 ses havuzu: {@code <ad>_1.._N} varyantlarından rastgele (art arda aynısı yok), aynı sesin üst üste binmesi engellenir
 * (çok yakın istek atılır, kısa çakışmada eskisi söner), içecek zinciri (varyantlar art arda, birbirini kesmeden), OGG süreleri.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class SoundPoolV20Test {

    static final class Rec implements SoundManager.Sink {
        final List<String> names = new ArrayList<>();
        final List<Long> at = new ArrayList<>();
        final List<float[]> args = new ArrayList<>();
        final List<Integer> stopped = new ArrayList<>();
        final List<Long> stoppedAt = new ArrayList<>();
        final List<Integer> faded = new ArrayList<>();
        int next = 1;

        @Override public int start(String name, float l, float r, float rate, int loop) {
            names.add(name);
            at.add(SystemClock.uptimeMillis());
            args.add(new float[]{l, r, rate, loop});
            return next++;
        }

        @Override public void stop(int stream) { stopped.add(stream); stoppedAt.add(SystemClock.uptimeMillis()); }

        @Override public void setVolume(int stream, float l, float r) { faded.add(stream); }
    }

    private static void idle(long ms) { ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS); }

    private SoundManager sm(Rec rec) {
        Context c = RuntimeEnvironment.getApplication();
        SoundManager s = new SoundManager(c);
        s.setSinkForTest(rec);
        return s;
    }

    @Test public void oggDurationsAreReadFromTheFiles() throws Exception {
        long[][] want = {{480}, {3000}, {1000}};
        String[] files = {"sfx_drink_ayran_1", "sfx_drink_ayran_2", "sfx_drink_ayran_3"};
        for (int i = 0; i < files.length; i++) {
            byte[] d = Files.readAllBytes(new File("src/main/res/raw/" + files[i] + ".ogg").toPath());
            long ms = OggInfo.durationMs(d);
            assertEquals(files[i], want[i][0], ms, 30);
        }
        assertEquals(0L, OggInfo.durationMs(new byte[10]));
        assertEquals(0L, OggInfo.durationMs((byte[]) null));
        SoundManager s = sm(new Rec());
        assertEquals("kaynaktan da okunur", 3000L, s.durationMs("sfx_drink_ayran_2"), 30);
        s.release();
    }

    @Test public void drinkPoolsHaveTheirVariantsAndSingleFilesArePoolsOfOne() {
        SoundManager s = sm(new Rec());
        assertEquals(Arrays.asList("sfx_drink_ayran_1", "sfx_drink_ayran_2", "sfx_drink_ayran_3"), s.variants("sfx_drink_ayran"));
        assertEquals(Arrays.asList("sfx_drink_can_1", "sfx_drink_can_2"), s.variants("sfx_drink_can"));
        assertEquals(Arrays.asList("sfx_drink_fridge_1", "sfx_drink_fridge_2"), s.variants("sfx_drink_fridge"));
        assertEquals(Arrays.asList("sfx_drink_glass_1", "sfx_drink_glass_2"), s.variants("sfx_drink_glass"));
        assertEquals(Arrays.asList("sfx_slice"), s.variants("sfx_slice"));
        assertTrue(s.variants("yok_boyle").isEmpty());
        s.release();
    }

    @Test public void sameVariantIsNeverPlayedTwiceInARowAndAllVariantsAreUsed() {
        for (String pool : new String[]{"sfx_drink_ayran", "sfx_drink_glass", "sfx_drink_can", "sfx_drink_fridge"}) {
            Rec r = new Rec();
            SoundManager s = sm(r);
            for (int i = 0; i < 40; i++) { s.play(pool); idle(400); }
            assertEquals(40, r.names.size());
            Set<String> used = new HashSet<>(r.names);
            assertEquals(pool + " tüm varyantlar", new HashSet<>(s.variants(pool)), used);
            for (int i = 1; i < r.names.size(); i++) assertNotEquals(pool + " art arda aynı varyant", r.names.get(i - 1), r.names.get(i));
            s.release();
        }
    }

    @Test public void oneShotsGetSlightPitchAndVolumeJitterButUiSignalsStayFixed() {
        Rec r = new Rec();
        SoundManager s = sm(r);
        for (int i = 0; i < 30; i++) { s.play("sfx_step_done"); idle(1500); }
        float min = 9, max = 0;
        for (float[] a : r.args) { min = Math.min(min, a[2]); max = Math.max(max, a[2]); assertTrue(a[2] >= 0.95f && a[2] <= 1.05f); }
        assertTrue("perde hafif rastgele", max - min > 0.01f);
        Rec r2 = new Rec();
        s.setSinkForTest(r2);
        for (int i = 0; i < 5; i++) { s.play("sfx_error"); idle(600); }
        for (float[] a : r2.args) assertEquals("hata sesi sabit", 1f, a[2], 1e-6f);
        s.release();
    }

    @Test public void sameSoundDoesNotStackAndAnOverlappingOneFadesOut() {
        Rec r = new Rec();
        SoundManager s = sm(r);
        s.play("sfx_drink_glass");     // 2 sn
        s.play("sfx_drink_glass");     // aynı anda ikinci istek: atılır (üst üste binme yok)
        idle(10);
        s.play("sfx_drink_glass");
        assertEquals("çok yakın istekler tek sese iner", 1, r.names.size());
        idle(300);
        s.play("sfx_drink_glass");     // ilki hâlâ çalıyor: eskisi söner, yenisi başlar
        assertEquals(2, r.names.size());
        idle(300);
        assertTrue("eski ses sönerek kısıldı", r.faded.contains(1));
        assertTrue("eski ses durduruldu", r.stopped.contains(1));
        assertFalse("yeni ses kesilmedi", r.stopped.contains(2));
        // farklı sesler birbirini etkilemez
        s.play("sfx_coin");
        assertEquals(3, r.names.size());
        assertFalse(r.stopped.contains(2));
        // bitmiş sesten sonra yeniden çalmak söndürme gerektirmez
        idle(2500);
        int stops = r.stopped.size();
        s.play("sfx_drink_glass");
        idle(300);
        assertEquals(stops, r.stopped.size());
        s.release();
    }

    @Test public void drinkChainPlaysVariantsBackToBackWithoutCuttingAndFadesAtTheEnd() {
        Rec r = new Rec();
        SoundManager s = sm(r);
        s.startLoop("p:1:Ayranı doldur", "sfx_drink_ayran", 0.4f, 1f, 6f);
        idle(6500);
        assertTrue("birden çok varyant art arda: " + r.names, r.names.size() >= 2);
        for (int i = 0; i < r.names.size(); i++) {
            assertTrue(r.names.get(i).startsWith("sfx_drink_ayran_"));
            assertEquals("zincir döngüsüz tek atışlar", 0f, r.args.get(i)[3], 0f);
            if (i > 0) {
                assertNotEquals(r.names.get(i - 1), r.names.get(i));
                long prevDur = (long) (s.durationMs(r.names.get(i - 1)) / r.args.get(i - 1)[2]);
                long gap = r.at.get(i) - r.at.get(i - 1);
                assertTrue("önceki varyant bitmeden yenisi başlamaz (" + gap + " / " + prevDur + ")", gap >= prevDur - 40);
            }
        }
        assertEquals("iş bitti", 0, s.activeLoopCount());
        assertTrue("son ses sönerek biter", r.faded.contains(r.next - 1));
        // aynı ad farklı anahtar: iki ayran işi (oyuncu + çırak) ayrı zincir
        s.release();
    }

    @Test public void holdLoopKeepsOneLoopAndExtendsIt() {
        Rec r = new Rec();
        SoundManager s = sm(r);
        s.holdLoop("st:EKMEK:sfx_bread_warm", "sfx_bread_warm", -0.4f, 0.9f, 2f);
        idle(1000);
        s.holdLoop("st:EKMEK:sfx_bread_warm", "sfx_bread_warm", -0.4f, 0.9f, 3f); // ikinci ekmek: aynı döngü uzar
        assertEquals("yeniden başlatılmadı", 1, r.names.size());
        idle(2500);
        assertEquals("ilk süre dolduğunda hâlâ çalıyor", 1, s.activeLoopCount());
        idle(700);
        assertEquals(0, s.activeLoopCount());
        assertEquals(1, r.stopped.size());
        // duraklatılmışken uzatma kalan süreye yazılır
        s.holdLoop("k", "sfx_fry", 0f, 1f, 1f);
        s.pauseLoops();
        s.holdLoop("k", "sfx_fry", 0f, 1f, 3f);
        s.resumeLoops();
        idle(2000);
        assertEquals(1, s.activeLoopCount());
        idle(1200);
        assertEquals(0, s.activeLoopCount());
        assertTrue(s.hasLoop("k") == false);
        s.release();
    }
}
