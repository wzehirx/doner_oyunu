package com.doner.oyunu.audio;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAudioManager;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowTextToSpeech;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Ses odağı (audio focus) ve konuşurken müzik kısma testleri. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AudioFocusTest {

    private static void idle(long ms) { ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS); }

    private static Context ctx() { return RuntimeEnvironment.getApplication(); }

    private static ShadowAudioManager sam() {
        return shadowOf((AudioManager) ctx().getSystemService(Context.AUDIO_SERVICE));
    }

    private static Object get(Object o, String f) throws Exception {
        Field x = o.getClass().getDeclaredField(f);
        x.setAccessible(true);
        return x.get(o);
    }

    private static void talkBack(boolean on) {
        AccessibilityManager am = (AccessibilityManager) ctx().getSystemService(Context.ACCESSIBILITY_SERVICE);
        shadowOf(am).setEnabled(on);
        shadowOf(am).setTouchExplorationEnabled(on);
    }

    // ------------------------------------------------------------ AudioFocus sarmalayıcısı
    @Test public void modernPathUsesAudioFocusRequestAndAbandonsIt() {
        AudioManager am = (AudioManager) ctx().getSystemService(Context.AUDIO_SERVICE);
        final List<Integer> changes = new ArrayList<>();
        AudioFocus f = new AudioFocus(am, 30, AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
                AudioAttributes.CONTENT_TYPE_SPEECH, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                new AudioFocus.Listener() { @Override public void onFocusChange(int c) { changes.add(c); } });
        assertFalse(f.isHeld());
        assertTrue(f.request());
        assertTrue(f.isHeld());
        assertNotNull(sam().getLastAudioFocusRequest());
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, sam().getLastAudioFocusRequest().durationHint);
        // ikinci istek yeni istek üretmez
        Object first = sam().getLastAudioFocusRequest();
        assertTrue(f.request());
        assertSame(first, sam().getLastAudioFocusRequest());
        f.abandon();
        assertFalse(f.isHeld());
        assertNotNull("API 26+ yolunda AudioFocusRequest bırakılmalı", sam().getLastAbandonedAudioFocusRequest());
        f.abandon(); // tekrar bırakmak zararsız
    }

    @Test public void legacyPathForApi24And25UsesStreamBasedRequest() {
        AudioManager am = (AudioManager) ctx().getSystemService(Context.AUDIO_SERVICE);
        AudioFocus f = new AudioFocus(am, 24, AudioAttributes.USAGE_GAME, AudioAttributes.CONTENT_TYPE_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN, null);
        assertTrue(f.request());
        ShadowAudioManager.AudioFocusRequest r = sam().getLastAudioFocusRequest();
        assertNotNull(r);
        assertEquals(AudioManager.STREAM_MUSIC, r.streamType);
        assertEquals(AudioManager.AUDIOFOCUS_GAIN, r.durationHint);
        assertNull("eski yolda AudioFocusRequest nesnesi kullanılmaz", sam().getLastAbandonedAudioFocusRequest());
        f.abandon();
        assertNotNull("eski API: abandonAudioFocus(listener)", sam().getLastAbandonedAudioFocusListener());
        assertNull(sam().getLastAbandonedAudioFocusRequest());
    }

    @Test public void deniedFocusIsNotHeldAndNullManagerIsSafe() {
        AudioManager am = (AudioManager) ctx().getSystemService(Context.AUDIO_SERVICE);
        sam().setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED);
        AudioFocus f = new AudioFocus(am, 34, AudioAttributes.USAGE_GAME, AudioAttributes.CONTENT_TYPE_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN, null);
        assertFalse(f.request());
        assertFalse(f.isHeld());
        AudioFocus none = new AudioFocus((AudioManager) null, 24, 1, 1, AudioManager.AUDIOFOCUS_GAIN, null);
        assertFalse(none.request());
        none.abandon();
    }

    // ------------------------------------------------------------ Speaker: konuşurken odak + kısma
    @Test public void talkBackAnnouncementRequestsDuckFocusThenReleasesIt() {
        talkBack(true);
        Speaker sp = new Speaker(ctx());
        sp.setAnchor(new View(ctx()));
        final List<Boolean> events = new ArrayList<>();
        sp.setSpeechListener(new Speaker.SpeechListener() {
            @Override public void onSpeaking(boolean a) { events.add(a); }
        });
        assertFalse(sp.isSpeaking());
        sp.say("Yeni müşteri geldi", false);
        assertTrue(sp.isSpeaking());
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, sam().getLastAudioFocusRequest().durationHint);
        sp.say("İkinci duyuru", false); // kuyrukta; aralıksız konuşma boyunca odak bırakılmaz
        idle(1000);
        assertTrue("konuşma sürerken odak tutulmalı", sp.isSpeaking());
        idle(8000);
        assertFalse("duyurular bitince odak bırakılmalı", sp.isSpeaking());
        assertNotNull(sam().getLastAbandonedAudioFocusRequest());
        assertEquals("tek başla, tek bit", java.util.Arrays.asList(true, false), events);
    }

    @Test public void stopReleasesFocusImmediately() {
        talkBack(true);
        Speaker sp = new Speaker(ctx());
        sp.setAnchor(new View(ctx()));
        sp.say("uzun bir duyuru metni burada okunuyor", true);
        assertTrue(sp.isSpeaking());
        sp.stop();
        assertFalse(sp.isSpeaking());
        assertNotNull(sam().getLastAbandonedAudioFocusRequest());
    }

    @Test public void ttsModeRequestsFocusUntilUtteranceDone() throws Exception {
        talkBack(false);
        Speaker sp = new Speaker(ctx());
        final List<Boolean> events = new ArrayList<>();
        sp.setSpeechListener(new Speaker.SpeechListener() {
            @Override public void onSpeaking(boolean a) { events.add(a); }
        });
        sp.say("merhaba", true);
        TextToSpeech tts = (TextToSpeech) get(sp, "tts");
        assertNotNull(tts);
        ShadowTextToSpeech st = shadowOf(tts);
        assertFalse("motor hazır değilken henüz konuşma/odak yok", sp.isSpeaking());
        st.getOnInitListener().onInit(TextToSpeech.SUCCESS); // gerçek cihazda asenkron
        assertEquals("merhaba", st.getLastSpokenText());
        assertTrue("TTS konuşurken odak tutulmalı", sp.isSpeaking());
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, sam().getLastAudioFocusRequest().durationHint);
        idle(100); // Robolectric TTS gölgesi onDone'ı ana döngüde gönderir
        assertFalse("TTS bitince odak bırakılmalı", sp.isSpeaking());
        assertNotNull(sam().getLastAbandonedAudioFocusRequest());
        assertEquals(java.util.Arrays.asList(true, false), events);
    }

    @Test public void ttsInterruptedByUrgentDoesNotReleaseFocusPrematurely() throws Exception {
        talkBack(false);
        Speaker sp = new Speaker(ctx());
        final List<Boolean> events = new ArrayList<>();
        sp.setSpeechListener(new Speaker.SpeechListener() {
            @Override public void onSpeaking(boolean a) { events.add(a); }
        });
        sp.say("birinci", false);
        ShadowTextToSpeech st = shadowOf((TextToSpeech) get(sp, "tts"));
        st.getOnInitListener().onInit(TextToSpeech.SUCCESS);
        sp.say("acil", true);                              // birinciyi keser
        st.getUtteranceProgressListener().onStop("u1", true); // kesilen eski konuşmanın bitişi
        assertTrue(sp.isSpeaking());
        idle(100);
        assertFalse(sp.isSpeaking());
        assertEquals("tek başlangıç, tek bitiş (kesilme odağı bırakmamalı)", java.util.Arrays.asList(true, false), events);
    }

    // ------------------------------------------------------------ SoundManager: kısma ve odak kaybı
    private static SoundManager withMusic() throws Exception {
        SoundManager sm = new SoundManager(ctx());
        MediaPlayer mp = new MediaPlayer();
        Field fm = SoundManager.class.getDeclaredField("music"); fm.setAccessible(true); fm.set(sm, mp);
        Field fn = SoundManager.class.getDeclaredField("musicName"); fn.setAccessible(true); fn.set(sm, "music_menu");
        return sm;
    }

    @Test public void speechDuckingLowersMusicAndRestoresIt() throws Exception {
        SoundManager sm = withMusic();
        sm.setMusicVolume(0.8f);
        float normal = sm.musicOutputVolume();
        assertEquals(0.8f, normal, 1e-4f);
        assertFalse(sm.isDucked());
        sm.setSpeechDucking(true);
        assertTrue(sm.isDucked());
        assertEquals(normal * SoundManager.DUCK_FACTOR, sm.musicOutputVolume(), 1e-4f);
        assertTrue(sm.musicOutputVolume() < normal);
        sm.setSpeechDucking(false);
        assertEquals("konuşma bitince müzik geri açılmalı", normal, sm.musicOutputVolume(), 1e-4f);
        sm.release();
    }

    @Test public void systemDuckRequestAndFocusLossAreHandled() throws Exception {
        SoundManager sm = withMusic();
        sm.setMusicVolume(1f);
        sm.onMusicFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
        assertTrue(sm.isDucked());
        assertEquals(SoundManager.DUCK_FACTOR, sm.musicOutputVolume(), 1e-4f);
        sm.onMusicFocusChange(AudioManager.AUDIOFOCUS_GAIN);
        assertFalse(sm.isDucked());
        assertEquals(1f, sm.musicOutputVolume(), 1e-4f);
        sm.onMusicFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
        assertTrue("geçici kayıpta müzik duraklar", sm.isMusicPausedByFocus());
        sm.onMusicFocusChange(AudioManager.AUDIOFOCUS_GAIN);
        assertFalse("odak dönünce müzik sürer", sm.isMusicPausedByFocus());
        // iki ducking kaynağı birlikte: biri bitince diğeri sürer
        sm.setSpeechDucking(true);
        sm.onMusicFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
        sm.setSpeechDucking(false);
        assertTrue(sm.isDucked());
        sm.onMusicFocusChange(AudioManager.AUDIOFOCUS_GAIN);
        assertFalse(sm.isDucked());
        sm.release();
    }

    @Test public void musicRequestsFocusOnResumeAndAbandonsOnPauseAndRelease() throws Exception {
        SoundManager sm = withMusic();
        sm.resume();
        assertNotNull("müzik çalarken ses odağı istenmeli", sam().getLastAudioFocusRequest());
        assertEquals(AudioManager.AUDIOFOCUS_GAIN, sam().getLastAudioFocusRequest().durationHint);
        sm.pause();
        assertNotNull("arka plana geçince odak bırakılmalı", sam().getLastAbandonedAudioFocusRequest());
        sm.resume();
        sm.release();
        idle(500);
    }

    // ------------------------------------------------------------ uçtan uca: Speaker -> SoundManager (MainActivity)
    @Test public void mainActivityDucksMusicWhileSpeakerSpeaks() throws Exception {
        talkBack(true);
        android.provider.Settings.Global.putFloat(ctx().getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        com.doner.oyunu.MainActivity act = org.robolectric.Robolectric.buildActivity(com.doner.oyunu.MainActivity.class).setup().get();
        idle(600);
        assertFalse(act.sound.isDucked());
        act.speaker.force("Para yüz lira.");
        assertTrue("konuşurken müzik kısılmalı", act.sound.isDucked());
        idle(10000);
        assertFalse("konuşma bitince müzik geri açılmalı", act.sound.isDucked());
    }
}
