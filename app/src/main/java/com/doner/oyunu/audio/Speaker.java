package com.doner.oyunu.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import com.doner.oyunu.engine.Narrator;
import com.doner.oyunu.engine.SpeechQueue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Sesli anlatım. TalkBack AÇIKSA yalnızca announceForAccessibility (çift konuşma olmaz, kendi kuyruğumuzla
 * sıralanır); KAPALIYSA kendi TextToSpeech (tr-TR) motorunu kullanır.
 */
public final class Speaker implements Narrator.Output {
    /** Konuşma başladı/bitti (müzik kısma gibi tepkiler için). */
    public interface SpeechListener { void onSpeaking(boolean active); }

    private final Context ctx;
    private final AccessibilityManager am;
    /** Konuşurken kısa süreli, "kıs" (duck) ses odağı: diğer müzik/medya kısılır, bitince geri açılır. */
    private final AudioFocus focus;
    private SpeechListener speechListener;
    private boolean speaking;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View anchor;
    private boolean enabled = true;
    private float rate = 1f;

    /**
     * Konuşma sırası (v0.9): bayat duyuruyu atar, aynı kategoriyi birleştirir/aralıklar, acil olanı öne alır, ipucunu
     * söylemeden önce yeniden denetler. TalkBack ve TTS yolları aynı sırayı kullanır; tek seferde bir duyuru verilir,
     * böylece announceForAccessibility kuyruğu şişmez.
     */
    private final SpeechQueue queue = new SpeechQueue(new SpeechQueue.Clock() {
        @Override public long now() { return SystemClock.uptimeMillis(); }
    }, new SpeechQueue.Player() {
        @Override public long play(String text, boolean interrupt) { return playNow(text, interrupt); }
    });

    // TTS yolu
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean ttsInitStarted;
    private final List<String[]> pendingBeforeReady = new ArrayList<>();
    private final Set<String> utterances = new HashSet<>();
    private int utteranceCounter;

    public Speaker(Context context) {
        ctx = context.getApplicationContext();
        am = (AccessibilityManager) ctx.getSystemService(Context.ACCESSIBILITY_SERVICE);
        focus = new AudioFocus(ctx, AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY, AudioAttributes.CONTENT_TYPE_SPEECH,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, null);
    }

    public void setSpeechListener(SpeechListener l) { speechListener = l; }

    /** Şu an konuşuyor (duyuru süresince ya da TTS sürerken) mu? */
    public boolean isSpeaking() { return speaking; }

    private void beginSpeech() {
        if (speaking) return;
        speaking = true;
        focus.request();
        if (speechListener != null) speechListener.onSpeaking(true);
    }

    private void endSpeech() {
        if (!speaking) return;
        speaking = false;
        focus.abandon();
        if (speechListener != null) speechListener.onSpeaking(false);
    }

    public void setAnchor(View v) { anchor = v; }

    /** Sesli anlatım ayarı (oyun anlatımını ve TTS'i kapatır). */
    public void setEnabled(boolean on) {
        enabled = on;
        if (!on) stop();
    }

    public boolean isEnabled() { return enabled; }

    public void setRate(float r) {
        rate = Math.max(0.5f, Math.min(2f, r));
        if (tts != null && ttsReady) try { tts.setSpeechRate(rate); } catch (Exception ignored) { }
    }

    public boolean isTalkBackOn() {
        try { return am != null && am.isEnabled() && am.isTouchExplorationEnabled(); }
        catch (Exception e) { return false; }
    }

    // ------------------------------------------------------------------ Narrator.Output
    /** Oyun anlatımı: "Sesli anlatım" ayarına bağlıdır. */
    @Override public void say(String text, boolean urgent) { say(text, urgent, null, null); }

    /** Oyun anlatımı (kategorili, bayatlık denetimli): "Sesli anlatım" ayarına bağlıdır. */
    @Override public void say(String text, boolean urgent, String category, Narrator.Fresh fresh) {
        if (!enabled || text == null || text.isEmpty()) return;
        queue.say(text, urgent, category, fresh);
        kick();
    }

    /**
     * Arayüz geri bildirimi (satın alma onayı, ekran başlığı...). TalkBack açıkken her zaman duyurulur,
     * TTS modunda "Sesli anlatım" kapalıysa susar.
     */
    public void announce(String text, boolean urgent) {
        if (text == null || text.isEmpty()) return;
        if (isTalkBackOn() || enabled) { queue.say(text, urgent); kick(); }
    }

    /** Kullanıcı açıkça istedi (Durumu oku vb.): ayardan bağımsız konuşur. */
    public void force(String text) {
        if (text == null || text.isEmpty()) return;
        queue.say(text, true);
        kick();
    }

    /** Uzun metin (yardım): bayatlamaz, sıraya eklenir; flush ise öncekileri keser. */
    public void speakLong(String text, boolean flush) {
        if (text == null || text.isEmpty()) return;
        queue.sayLong(text, flush);
        kick();
    }

    /** Ekran değişti: TalkBack odak olayıyla başlığı okur; TTS modunda biz okuruz. */
    public void screenChanged(String title) {
        if (!isTalkBackOn() && enabled && title != null && !title.isEmpty()) { queue.say(title, true); kick(); }
    }

    public boolean isBusy() {
        return queue.isBusy(SystemClock.uptimeMillis()) || isTtsActive();
    }

    public void stop() {
        queue.clear();
        handler.removeCallbacks(pumper);
        pendingBeforeReady.clear();
        utterances.clear();
        if (tts != null) try { tts.stop(); } catch (Exception ignored) { }
        endSpeech();
    }

    public void shutdown() {
        stop();
        endSpeech();
        handler.removeCallbacksAndMessages(null);
        if (tts != null) {
            try { tts.shutdown(); } catch (Exception ignored) { }
            tts = null;
            ttsReady = false;
            ttsInitStarted = false;
        }
    }

    // ------------------------------------------------------------------ sıra çalıştırma
    private static long estimateMs(String t) { return 450 + (long) t.length() * 62; }

    private final Runnable pumper = new Runnable() {
        @Override public void run() { kick(); }
    };

    /** Sırayı ilerletir ve bir sonraki uyanmayı kurar; konuşma bittiyse ses odağını bırakır. */
    private void kick() {
        long now = SystemClock.uptimeMillis();
        queue.pump(now);
        handler.removeCallbacks(pumper);
        long w = queue.nextWakeup(now);
        if (w >= 0) handler.postDelayed(pumper, w + 15);
        else if (speaking && !isTtsActive()) endSpeech();
    }

    /** SpeechQueue oynatıcısı: bir duyuruyu hemen söyler, beklenen süreyi (ms) döner. */
    private long playNow(String text, boolean interrupt) {
        if (isTalkBackOn() && anchor != null) {
            beginSpeech();
            try { anchor.announceForAccessibility(text); } catch (Exception ignored) { }
            return estimateMs(text);
        }
        return speakTtsNow(text, interrupt);
    }

    private boolean isTtsActive() { return !utterances.isEmpty() || !pendingBeforeReady.isEmpty(); }

    // ------------------------------------------------------------------ TTS
    private void ensureTts() {
        if (ttsInitStarted) return;
        ttsInitStarted = true;
        try {
            tts = new TextToSpeech(ctx, new TextToSpeech.OnInitListener() {
                @Override public void onInit(int status) {
                    if (status != TextToSpeech.SUCCESS || tts == null) { ttsReady = false; tts = null; return; }
                    try {
                        int r = tts.setLanguage(new Locale("tr", "TR"));
                        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                            tts.setLanguage(Locale.getDefault());
                        }
                        tts.setSpeechRate(rate);
                        tts.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                            @Override public void onStart(String id) { }
                            @Override public void onDone(String id) { finished(id); }
                            @Override public void onError(String id) { finished(id); }
                            @Override public void onStop(String id, boolean interrupted) { finished(id); }
                        });
                    } catch (Exception ignored) { }
                    ttsReady = true;
                    List<String[]> p = new ArrayList<>(pendingBeforeReady);
                    pendingBeforeReady.clear();
                    for (String[] m : p) speakTtsNow(m[0], false);
                }
            });
        } catch (Exception e) {
            tts = null;
        }
    }

    private void finished(final String id) {
        handler.post(new Runnable() {
            @Override public void run() {
                utterances.remove(id);
                if (!isTtsActive()) queue.finished(SystemClock.uptimeMillis());
                kick();
            }
        });
    }

    /** TTS ile söyler; beklenen süreyi döner (bitiş geri çağrısı sırayı erken açar). */
    private long speakTtsNow(String text, boolean interrupt) {
        ensureTts();
        long est = (long) (estimateMs(text) / Math.max(0.5f, rate)) + 800;
        if (tts == null) return 0;
        if (!ttsReady) {
            if (interrupt) pendingBeforeReady.clear();
            pendingBeforeReady.add(new String[]{text, interrupt ? "1" : "0"});
            return est;
        }
        String id = "u" + (++utteranceCounter);
        try {
            if (interrupt) utterances.clear();
            utterances.add(id);
            beginSpeech();
            Bundle params = new Bundle();
            tts.speak(text, interrupt ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, params, id);
        } catch (Exception e) {
            utterances.remove(id);
            return 0;
        }
        return est;
    }
}
