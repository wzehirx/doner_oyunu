package com.doner.oyunu.audio;

import android.accessibilityservice.AccessibilityServiceInfo;
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
    private String lastUtterance;

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

    /**
     * Konuşan bir ekran okuyucu (TalkBack vb.) açık mı (v2.6). Eskiden yalnızca "dokunarak keşfet" açıksa true dönüyordu;
     * dokunarak keşfi kapatıp TalkBack kullanan oyuncuda kendi TTS'imiz de konuşuyor, TalkBack ile ÇİFT konuşma oluyordu.
     * Şimdi: erişilebilirlik açık VE (dokunarak keşif açık YA DA sesli geri bildirim veren bir servis çalışıyor).
     */
    public boolean isTalkBackOn() {
        try {
            if (am == null || !am.isEnabled()) return false;
            if (am.isTouchExplorationEnabled()) return true;
            List<AccessibilityServiceInfo> l = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN);
            return l != null && !l.isEmpty();
        } catch (Exception e) { return false; }
    }

    // ------------------------------------------------------------------ Narrator.Output
    /** Oyun anlatımı: "Sesli anlatım" ayarına bağlıdır. */
    @Override public void say(String text, boolean urgent) { say(text, urgent, null, null); }

    /** Oyun anlatımı (kategorili, bayatlık denetimli): "Sesli anlatım" ayarına bağlıdır. */
    @Override public void say(String text, boolean urgent, String category, Narrator.Fresh fresh) {
        if (!enabled || text == null || text.isEmpty()) return;
        // "Hedefi oku" / "Eklenenleri oku" (panel okuma seçenekleri): oyuncunun açık isteği, her basışta okunur
        if (Narrator.CAT_INFO.equals(category)) { force(text); return; }
        // v2.6: gerçek acil uyarı (hata/uyarı, sabır kritik) süren AÇIK OKUMAYI keser: uzun "Durumu oku" sürerken
        // "sabır kritik" beklemez. (Sıra kuralı değişmedi; kesmeyi Speaker yapar.)
        if (urgent && (Narrator.CAT_ALERT.equals(category) || Narrator.CAT_PATIENCE.equals(category))) cutExplicit();
        queue.say(text, urgent, category, fresh);
        interruptNext = false;
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

    /**
     * Kullanıcı açıkça istedi ("Durumu oku", "Hazır stoğu oku", "Hedefi oku", fatura/banka/fiyat okumaları): ayardan
     * bağımsız, HER basışta, konuşanı keserek okunur (v2.5 düzeltmesi). Kök neden: eskiden bu okumalar sıradan bir acil
     * duyuru gibi sıraya giriyordu: konuşan bir uyarı varken bekliyor, 6 sn'de bayatlayıp atılıyor, art arda gelen
     * uyarılarla sıradan çıkarılıyordu; TalkBack ise art arda gelen AYNI metinli duyuruyu yutuyordu (ikinci basış sessiz).
     * Şimdi: konuşma sırası {@link SpeechQueue#sayExplicit} ile hemen keserek söyler; TalkBack açıkken aynı metin
     * görünmez bir ayırt edici ekle ({@link #DISTINCT_MARK}) ve dokunma olayından sonra kısa gecikmeyle duyurulur;
     * TTS'de QUEUE_FLUSH ile önceki konuşma kesilir.
     */
    public void force(String text) { forceAfter(text, EXPLICIT_DELAY_MS); }

    /**
     * Odak taşındıktan SONRA söylenecek açık okuma (v2.6): TalkBack açıkken odak okuması önce başlasın, duyuru onun
     * arkasına sıralansın diye {@link #FOCUS_SAY_DELAY_MS} bekler (odak + duyuru yarışmaz). TTS'de hemen söylenir.
     */
    public void forceAfterFocus(String text) { forceAfter(text, FOCUS_SAY_DELAY_MS); }

    private void forceAfter(String text, long talkBackDelay) {
        if (text == null || text.isEmpty()) return;
        explicitText = text;
        handler.removeCallbacks(explicitRunner);
        if (isTalkBackOn()) handler.postDelayed(explicitRunner, talkBackDelay);
        else explicitRunner.run();
    }

    /** Odak taşıma ile ardından gelen duyuru arasındaki süre (TalkBack). */
    public static final long FOCUS_SAY_DELAY_MS = 180;

    /** TalkBack açıkken açık okuma, dokunma (TYPE_VIEW_CLICKED) olayından sonra gelsin diye bu kadar ms bekler. */
    public static final long EXPLICIT_DELAY_MS = 90;
    /** Görünmez ayırt edici (sıfır genişlikli boşluk): TalkBack aynı metni art arda yutmasın. Seslendirilmez. */
    public static final String DISTINCT_MARK = "\u200B";
    private String explicitText;
    private String lastExplicitSent;
    private int explicitSerial;

    private final Runnable explicitRunner = new Runnable() {
        @Override public void run() {
            String t = explicitText;
            explicitText = null;
            if (t == null) return;
            if (isTalkBackOn()) {
                // aynı metin art arda: görünmez ekle farklılaştır (TalkBack yinelenen duyuruyu atmasın)
                String base = t;
                if (lastExplicitSent != null && stripMark(lastExplicitSent).equals(base)) {
                    explicitSerial++;
                    StringBuilder sb = new StringBuilder(base);
                    for (int i = 0; i < 1 + (explicitSerial % 2); i++) sb.append(DISTINCT_MARK);
                    if (sb.toString().equals(lastExplicitSent)) sb.append(DISTINCT_MARK);
                    t = sb.toString();
                } else {
                    explicitSerial = 0;
                }
            }
            lastExplicitSent = t;
            explicitStarting = true;
            try { queue.sayExplicit(t); } finally { explicitStarting = false; }
            kick();
        }
    };

    /** Ayırt edici ekleri siler (testler ve karşılaştırma için). */
    public static String stripMark(String s) { return s == null ? null : s.replace(DISTINCT_MARK, ""); }

    /** Söylenen açık okuma sayısı (test). */
    public int explicitCount() { return queue.explicitCount(); }

    /** Uzun metin (yardım): bayatlamaz, sıraya eklenir; flush ise öncekileri keser. */
    public void speakLong(String text, boolean flush) {
        if (text == null || text.isEmpty()) return;
        if (flush && isExplicitSpeaking()) { explicitUntil = 0; explicitUtterance = null; interruptNext = true; }
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
        handler.removeCallbacks(explicitRunner);
        explicitText = null;
        explicitUntil = 0;
        speechUntil = 0;
        explicitUtterance = null;
        interruptNext = false;
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
    /** Tahmini konuşma süresi (normal hızda, ms). */
    static long estimateMs(String t) { return 450 + (long) t.length() * 62; }

    /**
     * v2.6: sıranın bir duyuruyu "meşgul" saydığı en uzun süre (ms). TalkBack duyurusunun bitiş geri çağrısı yoktur; süre
     * tahminle kurulur. Hızlı TalkBack kullanan oyuncuda 250 karakterlik okuma 16 sn meşgul sayılıyor, bu sırada gelen
     * olağan duyurular ({@link SpeechQueue#STALE_MS} = 3,5 sn) bayatlayıp ATILIYORDU. Sınır bayatlık süresinden kısadır:
     * bekleyen duyuru atılmadan söylenir; TalkBack kendi kuyruğunda okumanın arkasına sıralar. TTS'de yalnızca açık okumaya
     * uygulanır (sonrakiler QUEUE_ADD ile okumanın arkasına eklenir).
     */
    public static final long MAX_BUSY_MS = 3000;

    /** Açık okuma şu an başlatılıyor (sıranın oynatıcı çağrısı içinde). */
    private boolean explicitStarting;
    /** Süren açık okumanın tahmini GERÇEK bitişi (uptime ms; 0 = yok) ve TTS söz kimliği. */
    private long explicitUntil;
    private String explicitUtterance;
    /** TalkBack duyurularının tahmini gerçek bitişi (müzik kısma / ses odağı için; sıra bundan önce açılabilir). */
    private long speechUntil;
    /** Sıradaki oynatma süren konuşmayı kessin (acil uyarı açık okumayı kesti). */
    private boolean interruptNext;

    /** Açık okuma hâlâ sürüyor mu (tahmini). */
    public boolean isExplicitSpeaking() { return SystemClock.uptimeMillis() < explicitUntil; }

    /** Süren açık okumayı keser: sıra hemen açılır, sıradaki duyuru konuşanı keserek söylenir. */
    private void cutExplicit() {
        long now = SystemClock.uptimeMillis();
        if (now >= explicitUntil) return;
        explicitUntil = 0;
        explicitUtterance = null;
        interruptNext = true;
        queue.finished(now); // meşgul süresini bitirir; bekleyen acil varsa o söylenir
    }

    private final Runnable pumper = new Runnable() {
        @Override public void run() { kick(); }
    };

    /** Sırayı ilerletir ve bir sonraki uyanmayı kurar; konuşma bittiyse ses odağını bırakır. */
    private void kick() {
        long now = SystemClock.uptimeMillis();
        queue.pump(now);
        handler.removeCallbacks(pumper);
        long w = queue.nextWakeup(now);
        if (w < 0 && now < speechUntil && !isTtsActive()) w = speechUntil - now; // TalkBack hâlâ okuyor: ses odağı kalsın
        if (w >= 0) handler.postDelayed(pumper, w + 15);
        else if (speaking && !isTtsActive()) endSpeech();
    }

    /** SpeechQueue oynatıcısı: bir duyuruyu hemen söyler, sıranın meşgul sayacağı süreyi (ms) döner. */
    private long playNow(String text, boolean interrupt) {
        boolean explicit = explicitStarting;
        boolean cut = interruptNext;
        interruptNext = false;
        long now = SystemClock.uptimeMillis();
        if (isTalkBackOn() && anchor != null) {
            beginSpeech();
            long est = estimateMs(text);
            if (cut) try { am.interrupt(); } catch (Exception ignored) { } // TalkBack'in süren okumasını sustur
            try { anchor.announceForAccessibility(text); } catch (Exception ignored) { }
            if (explicit) explicitUntil = now + est;
            else if (interrupt || cut) explicitUntil = 0;
            speechUntil = interrupt || cut ? now + est : Math.max(speechUntil, now) + est;
            return Math.min(est, MAX_BUSY_MS);
        }
        long est = speakTtsNow(text, interrupt || cut);
        if (explicit) {
            explicitUntil = est > 0 ? now + est : 0;
            explicitUtterance = lastUtterance;
            return Math.min(est, MAX_BUSY_MS);
        }
        if (interrupt || cut) { explicitUntil = 0; explicitUtterance = null; }
        return est;
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
                if (id != null && id.equals(explicitUtterance)) { explicitUtterance = null; explicitUntil = 0; }
                if (!isTtsActive()) queue.finished(SystemClock.uptimeMillis());
                kick();
            }
        });
    }

    /** TTS ile söyler; beklenen süreyi döner (bitiş geri çağrısı sırayı erken açar). */
    private long speakTtsNow(String text, boolean interrupt) {
        ensureTts();
        lastUtterance = null;
        long est = (long) (estimateMs(text) / Math.max(0.5f, rate)) + 800;
        if (tts == null) return 0;
        if (!ttsReady) {
            if (interrupt) pendingBeforeReady.clear();
            pendingBeforeReady.add(new String[]{text, interrupt ? "1" : "0"});
            return est;
        }
        String id = "u" + (++utteranceCounter);
        lastUtterance = id;
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
