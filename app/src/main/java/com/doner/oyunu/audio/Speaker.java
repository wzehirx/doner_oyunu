package com.doner.oyunu.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import com.doner.oyunu.engine.Narrator;

import java.util.ArrayDeque;
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
    private static final int MAX_QUEUE = 4;

    private final Context ctx;
    private final AccessibilityManager am;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View anchor;
    private boolean enabled = true;
    private float rate = 1f;

    // TalkBack yolu
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private long busyUntil;
    private boolean pumpScheduled;
    private boolean noDrop;

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
    @Override public void say(String text, boolean urgent) {
        if (!enabled || text == null || text.isEmpty()) return;
        route(text, urgent);
    }

    /**
     * Arayüz geri bildirimi (satın alma onayı, ekran başlığı...). TalkBack açıkken her zaman duyurulur,
     * TTS modunda "Sesli anlatım" kapalıysa susar.
     */
    public void announce(String text, boolean urgent) {
        if (text == null || text.isEmpty()) return;
        if (isTalkBackOn() || enabled) route(text, urgent);
    }

    /** Kullanıcı açıkça istedi (Durumu oku vb.): ayardan bağımsız konuşur. */
    public void force(String text) {
        if (text == null || text.isEmpty()) return;
        route(text, true);
    }

    /** Uzun metin (yardım): kuyruk sınırı olmadan sıraya ekler; flush ise öncekileri keser. */
    public void speakLong(String text, boolean flush) {
        if (text == null || text.isEmpty()) return;
        noDrop = true;
        try { route(text, flush); } finally { noDrop = false; }
    }

    /** Ekran değişti: TalkBack odak olayıyla başlığı okur; TTS modunda biz okuruz. */
    public void screenChanged(String title) {
        if (!isTalkBackOn() && enabled) route(title, true);
    }

    public boolean isBusy() {
        if (isTalkBackOn()) return SystemClock.uptimeMillis() < busyUntil || !queue.isEmpty();
        return !utterances.isEmpty() || !pendingBeforeReady.isEmpty();
    }

    public void stop() {
        queue.clear();
        busyUntil = 0;
        pendingBeforeReady.clear();
        utterances.clear();
        if (tts != null) try { tts.stop(); } catch (Exception ignored) { }
    }

    public void shutdown() {
        stop();
        handler.removeCallbacksAndMessages(null);
        if (tts != null) {
            try { tts.shutdown(); } catch (Exception ignored) { }
            tts = null;
            ttsReady = false;
            ttsInitStarted = false;
        }
    }

    // ------------------------------------------------------------------ yönlendirme
    private void route(String text, boolean urgent) {
        if (isTalkBackOn() && anchor != null) {
            enqueueAnnounce(text, urgent);
        } else {
            speakTts(text, urgent);
        }
    }

    private static long estimateMs(String t) { return 450 + (long) t.length() * 62; }

    private void enqueueAnnounce(String text, boolean urgent) {
        if (urgent) {
            queue.clear();
            announceNow(text);
            return;
        }
        if (!noDrop && queue.size() >= MAX_QUEUE) queue.pollFirst(); // en eskiyi at
        queue.addLast(text);
        pump();
    }

    private void announceNow(String text) {
        busyUntil = SystemClock.uptimeMillis() + estimateMs(text);
        try { if (anchor != null) anchor.announceForAccessibility(text); } catch (Exception ignored) { }
        schedulePump(estimateMs(text));
    }

    private void schedulePump(long delay) {
        if (pumpScheduled) handler.removeCallbacks(pumper);
        pumpScheduled = true;
        handler.postDelayed(pumper, delay);
    }

    private final Runnable pumper = new Runnable() {
        @Override public void run() { pumpScheduled = false; pump(); }
    };

    private void pump() {
        if (queue.isEmpty()) return;
        long now = SystemClock.uptimeMillis();
        if (now < busyUntil) { schedulePump(busyUntil - now + 20); return; }
        announceNow(queue.pollFirst());
    }

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
                    for (String[] m : p) speakTts(m[0], "1".equals(m[1]));
                }
            });
        } catch (Exception e) {
            tts = null;
        }
    }

    private void finished(final String id) {
        handler.post(new Runnable() { @Override public void run() { utterances.remove(id); } });
    }

    private void speakTts(String text, boolean urgent) {
        ensureTts();
        if (tts == null) return;
        if (!ttsReady) {
            if (urgent) pendingBeforeReady.clear();
            if (noDrop || pendingBeforeReady.size() < MAX_QUEUE) pendingBeforeReady.add(new String[]{text, urgent ? "1" : "0"});
            return;
        }
        if (!urgent && !noDrop && utterances.size() >= MAX_QUEUE) return; // kuyruk şişmesin
        String id = "u" + (++utteranceCounter);
        try {
            if (urgent) utterances.clear();
            utterances.add(id);
            Bundle params = new Bundle();
            tts.speak(text, urgent ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, params, id);
        } catch (Exception e) {
            utterances.remove(id);
        }
    }
}
