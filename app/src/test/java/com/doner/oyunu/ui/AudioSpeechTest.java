package com.doner.oyunu.ui;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.media.MediaPlayer;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;

import com.doner.oyunu.audio.SoundManager;
import com.doner.oyunu.audio.Speaker;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAccessibilityManager;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h740dp-xxhdpi")
public class AudioSpeechTest {

    private static Object get(Object o, String f) throws Exception {
        Field x = o.getClass().getDeclaredField(f);
        x.setAccessible(true);
        return x.get(o);
    }

    private void idle(long ms) { ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS); }

    @Test public void everyReferencedSoundExistsInRawAndInSfxList() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        Set<String> names = new HashSet<>();
        scan(new File("src/main/java/com/doner/oyunu"), names);
        assertFalse(names.isEmpty());
        Set<String> sfxList = new HashSet<>(Arrays.asList(SoundManager.SFX));
        for (String n : names) {
            int id = c.getResources().getIdentifier(n, "raw", c.getPackageName());
            assertTrue("res/raw içinde yok: " + n, id != 0);
            if (n.startsWith("sfx_")) assertTrue("SoundManager.SFX listesinde yok (yüklenmez, hep sessiz): " + n, sfxList.contains(n));
        }
        for (String n : SoundManager.SFX) assertTrue("SFX raw yok: " + n, c.getResources().getIdentifier(n, "raw", c.getPackageName()) != 0);
        for (String n : new String[]{"music_menu", "music_day_calm", "music_day_busy", "music_shop"})
            assertTrue(n, c.getResources().getIdentifier(n, "raw", c.getPackageName()) != 0);
    }

    private void scan(File d, Set<String> out) throws Exception {
        Pattern p = Pattern.compile("\"((?:sfx|music)_[a-z_]+)\"");
        for (File f : d.listFiles()) {
            if (f.isDirectory()) { scan(f, out); continue; }
            if (!f.getName().endsWith(".java")) continue;
            Matcher m = p.matcher(new String(Files.readAllBytes(f.toPath()), "UTF-8"));
            while (m.find()) out.add(m.group(1));
        }
    }

    @Test public void soundManagerSurvivesBadInputsAndRelease() {
        Context c = RuntimeEnvironment.getApplication();
        SoundManager sm = new SoundManager(c);
        sm.play("yok_boyle_bir_ses");
        sm.play(null == null ? "sfx_tap" : "", 5f);   // pan sınır dışı
        sm.playLater("sfx_tap", -9f, 100);
        sm.playMusic("yok_boyle_muzik");
        sm.playMusic(null);
        sm.playMusic("music_menu");
        sm.setMusicVolume(5f); sm.setSfxVolume(-3f);
        sm.pause(); sm.pause(); sm.resume(); sm.resume();
        sm.playMusic("music_shop");
        idle(1000);
        sm.release();
        sm.release();           // çift release
        sm.play("sfx_tap");     // release sonrası çalma
        sm.pause(); sm.resume();
        idle(1000);
    }

    /** Ekran değişiminin hemen ardından Activity yok edilirse sönümlenmekte olan eski MediaPlayer serbest kalmalı. */
    @Test public void fadingOutPlayerIsReleasedOnDestroy() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        SoundManager sm = new SoundManager(c);
        // Robolectric gerçek ses çözmediğinden çalan oynatıcıyı yansımayla yerleştiriyoruz.
        MediaPlayer first = new MediaPlayer();
        Field fm = SoundManager.class.getDeclaredField("music"); fm.setAccessible(true); fm.set(sm, first);
        Field fn = SoundManager.class.getDeclaredField("musicName"); fn.setAccessible(true); fn.set(sm, "music_menu");
        sm.playMusic("music_shop");   // eskisi 5 adımda sönümlenip bırakılacak
        sm.release();                 // sönümleme tamamlanmadan release
        idle(2000);
        assertEquals("Sönümlenen eski MediaPlayer release edilmemiş (sızıntı)",
                org.robolectric.shadows.ShadowMediaPlayer.State.END, shadowOf(first).getState());
    }

    @Test public void talkBackModeUsesAnnouncementsOnlyAndQueuesInOrder() {
        Context c = RuntimeEnvironment.getApplication();
        AccessibilityManager am = (AccessibilityManager) c.getSystemService(Context.ACCESSIBILITY_SERVICE);
        ShadowAccessibilityManager sam = shadowOf(am);
        sam.setEnabled(true);
        sam.setTouchExplorationEnabled(true);
        UiHarness h = new UiHarness(false);
        assertTrue(h.act.speaker.isTalkBackOn());
        h.click("Ayarlar"); h.settle();
        evBase = sam.getSentAccessibilityEvents().size();
        h.act.speaker.say("Birinci olay", false);
        h.act.speaker.say("İkinci olay", false);
        h.act.speaker.say("Üçüncü olay", false);
        h.idle(10);
        List<String> spoken = announcements(sam);
        assertEquals("İlk olay hemen, diğerleri sırayla duyurulmalı", 1, spoken.size());
        h.idle(6000);
        spoken = announcements(sam);
        assertEquals(Arrays.asList("Birinci olay", "İkinci olay", "Üçüncü olay"), spoken);
        // acil olay kuyruğu keser
        h.act.speaker.say("a", false); h.act.speaker.say("b", false);
        h.act.speaker.say("ACİL", true);
        h.idle(10);
        assertEquals("ACİL", announcements(sam).get(announcements(sam).size() - 1));
        // ayarlardan kapalıyken oyun anlatımı susar, arayüz duyurusu (announce) TalkBack'te yine çalışır
        h.act.speaker.setEnabled(false);
        evBase = sam.getSentAccessibilityEvents().size();
        h.act.speaker.say("susmalı", false);
        h.idle(3000);
        assertTrue(announcements(sam).isEmpty());
        h.act.speaker.announce("arayüz duyurusu", true);
        h.idle(10);
        assertEquals(Arrays.asList("arayüz duyurusu"), announcements(sam));
    }

    private int evBase;

    private List<String> announcements(ShadowAccessibilityManager sam) {
        List<String> l = new ArrayList<>();
        List<AccessibilityEvent> all = new ArrayList<>(sam.getSentAccessibilityEvents());
        for (AccessibilityEvent e : all.subList(Math.min(evBase, all.size()), all.size()))
            if (e.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT && !e.getText().isEmpty()) l.add(e.getText().get(0).toString());
        return l;
    }

    @Test public void ttsModeWithTalkBackOffDoesNotCrashAndDoesNotAnnounce() {
        Context c = RuntimeEnvironment.getApplication();
        ShadowAccessibilityManager sam = shadowOf((AccessibilityManager) c.getSystemService(Context.ACCESSIBILITY_SERVICE));
        sam.setEnabled(false);
        UiHarness h = new UiHarness(false);
        assertFalse(h.act.speaker.isTalkBackOn());
        evBase = sam.getSentAccessibilityEvents().size();
        h.act.speaker.say("merhaba", false);
        h.act.speaker.speakLong("uzun metin", true);
        h.act.speaker.force("zorla");
        h.act.speaker.announce("duyuru", false);
        h.idle(3000);
        assertTrue("TalkBack kapalıyken announceForAccessibility kullanılmamalı", announcements(sam).isEmpty());
        h.controller.pause();   // speaker.stop
        h.controller.resume();
        h.controller.destroy(); // shutdown
        idle(1000);
    }

    @Test public void speakerWithoutAnchorOrWithNullTextIsSafe() {
        Context c = RuntimeEnvironment.getApplication();
        Speaker s = new Speaker(c);
        s.say(null, false); s.say("", true); s.announce(null, true); s.force(null); s.speakLong(null, true);
        s.setRate(99f); s.setRate(-1f); s.setEnabled(false); s.setEnabled(true);
        s.say("x", false);
        s.stop(); s.shutdown(); s.shutdown();
        s.say("shutdown sonrası", true);   // yeniden TTS başlatır; çökmemeli
        idle(1000);
        s.shutdown();
    }
}
