package com.doner.oyunu.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;

import android.os.SystemClock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Ses efektleri (SoundPool, sol/sağ pan) ve döngülü müzik (MediaPlayer).
 * Ses dosyaları res/raw altından ADIYLA (getIdentifier) yüklenir; eksik dosya sessizce atlanır.
 */
public final class SoundManager {
    public static final String[] SFX = {
            "sfx_customer_arrive", "sfx_order_new", "sfx_slice", "sfx_bread_warm", "sfx_sauce", "sfx_wrap", "sfx_veg_tomato", "sfx_veg_lettuce",
            "sfx_grill_sizzle", "sfx_fry", "sfx_pour", "sfx_step_done", "sfx_deliver", "sfx_coin", "sfx_error",
            "sfx_warning", "sfx_customer_happy", "sfx_customer_angry", "sfx_star_up", "sfx_star_down",
            "sfx_day_start", "sfx_day_end", "sfx_buy", "sfx_tap", "sfx_tick"
    };

    /** Döngülenebilen istasyon sesleri: SoundPool döngüsüyle adım süresince çalar. */
    public static final String[] LOOPED = {"sfx_grill_sizzle", "sfx_fry", "sfx_pour", "sfx_wrap", "sfx_bread_warm"};
    /** Dükkân ambiyansı (res/raw/amb_shop.ogg, döngülenebilir): gün boyunca düşük sesle çalar; dosya yoksa sessizce atlanır. */
    public static final String AMBIENCE_SHOP = "amb_shop";
    /** Ambiyans ses düzeyi varsayılanı (SaveData.ambienceVolume ile aynı). */
    public static final float DEFAULT_AMBIENCE_VOLUME = 0.35f;
    /** Darbeli istasyon sesleri: adım süresince rastgele aralıkla tekrarlanır. */
    public static final String[] PULSED = {"sfx_slice", "sfx_sauce"};
    /** Darbeli tekrar aralığı (sn), perde ve ses rastgeleliği (şartname §5). */
    static final float PULSE_MIN_S = 0.35f, PULSE_MAX_S = 0.6f, PULSE_RATE_JITTER = 0.05f, PULSE_VOL_JITTER = 0.08f;

    /** Konuşma / başka bir uygulamanın kısa ses odağı sırasında müziğin çarpanı (kısma). */
    static final float DUCK_FACTOR = 0.25f;

    private final Context ctx;
    /** Müzik odağı: başka uygulama alırsa müzik durur/kısılır; bitince geri açılır. */
    private final AudioFocus focus;
    private boolean speechDuck;   // Speaker konuşuyor
    private boolean focusDuck;    // sistem "kıs" dedi (örn. TalkBack/başka uygulama)
    private boolean focusPaused;  // odak kaybı yüzünden duraklatıldı
    /** Müzik geçişleri (iptal edilmez). */
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Gecikmeli efektler (ekran değişince/duraklatınca iptal edilir). */
    private final Handler sfxHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Integer> ids = new HashMap<>();
    private final Set<Integer> loaded = new HashSet<>();
    private SoundPool pool;
    /** Ses çıkışı (SoundPool); testte yerine konabilir. */
    private Sink sink = new PoolSink();
    private float sfxVolume = 1f;
    private float musicVolume = 0.7f;
    private float ambienceVolume = DEFAULT_AMBIENCE_VOLUME;

    /** Ambiyans oynatıcısı (dosya yoksa null); istenen ad ayrı tutulur, böylece dosya sonradan gelse de durum tutarlıdır. */
    private MediaPlayer amb;
    private String ambWanted;
    /** Ekran/oyun duraklatması (Duraklat paneli) ambiyansı sustursun. */
    private boolean ambHold;

    private MediaPlayer music;
    /** Sönümlenip bırakılmayı bekleyen eski oynatıcılar (release sırasında sızmasın). */
    private final java.util.List<MediaPlayer> retiring = new java.util.ArrayList<>();
    private String musicName;
    private boolean paused;
    private float fade = 1f; // 0..1 müzik geçiş çarpanı
    private int fadeToken;

    public SoundManager(Context context) {
        ctx = context.getApplicationContext();
        focus = new AudioFocus(ctx, AudioAttributes.USAGE_GAME, AudioAttributes.CONTENT_TYPE_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN, new AudioFocus.Listener() {
            @Override public void onFocusChange(int change) { onMusicFocusChange(change); }
        });
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            pool = new SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build();
            pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override public void onLoadComplete(SoundPool sp, int sampleId, int status) {
                    if (status == 0) loaded.add(sampleId);
                }
            });
            for (String name : SFX) {
                int res = resId(name);
                if (res == 0) continue;
                try { ids.put(name, pool.load(ctx, res, 1)); } catch (Exception ignored) { }
            }
        } catch (Exception e) {
            pool = null;
        }
    }

    private int resId(String name) {
        try { return ctx.getResources().getIdentifier(name, "raw", ctx.getPackageName()); }
        catch (Exception e) { return 0; }
    }

    // ------------------------------------------------------------------ ayarlar
    public void setSfxVolume(float v) { sfxVolume = clamp(v); }

    public void setMusicVolume(float v) { musicVolume = clamp(v); applyMusicVolume(); }

    public void setAmbienceVolume(float v) { ambienceVolume = clamp(v); applyAmbienceVolume(); syncAmbience(); }

    /** Speaker konuşurken müziği kıs, bitince geri aç. */
    public void setSpeechDucking(boolean on) {
        speechDuck = on;
        applyMusicVolume();
        applyAmbienceVolume();
    }

    /** Müzik şu an kısık mı (konuşma ya da sistem isteğiyle)? */
    public boolean isDucked() { return speechDuck || focusDuck; }

    /** Müziğin fiilen çalındığı ses düzeyi (0..1). */
    public float musicOutputVolume() { return musicVolume * fade * (isDucked() ? DUCK_FACTOR : 1f); }

    /** Odak kaybı yüzünden müzik duraklatıldı mı? */
    public boolean isMusicPausedByFocus() { return focusPaused; }

    void onMusicFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                focusPaused = true;
                focusDuck = false;
                if (music != null) try { music.pause(); } catch (Exception ignored) { }
                syncAmbience();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                focusDuck = true;
                applyMusicVolume();
                applyAmbienceVolume();
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                focusDuck = false;
                if (focusPaused) {
                    focusPaused = false;
                    if (!paused && music != null) try { music.start(); } catch (Exception ignored) { }
                }
                applyMusicVolume();
                applyAmbienceVolume();
                syncAmbience();
                break;
            default:
                break;
        }
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    // ------------------------------------------------------------------ efekt
    public void play(String name) { play(name, 0f); }

    /** pan: -1 (tam sol) ... +1 (tam sağ). */
    public void play(String name, float pan) { play(name, pan, 1f); }

    /** volume: 0..1 ek çarpan (ör. çırak sesleri için 0,6). */
    public void play(String name, float pan, float volume) { play(name, pan, volume, 1f); }

    /** rate: çalma hızı/perde çarpanı (0,5-2); kısa tek atış malzeme sesleri varyasyon için kullanır. */
    public void play(String name, float pan, float volume, float rate) {
        if (pool == null || sfxVolume <= 0f) return;
        sink.start(name, left(pan, volume), right(pan, volume), Math.max(0.5f, Math.min(2f, rate)), 0);
    }

    private float left(float pan, float volume) {
        float p = Math.max(-1f, Math.min(1f, pan));
        return sfxVolume * clamp(volume) * (p > 0 ? 1f - p * 0.85f : 1f);
    }

    private float right(float pan, float volume) {
        float p = Math.max(-1f, Math.min(1f, pan));
        return sfxVolume * clamp(volume) * (p < 0 ? 1f + p * 0.85f : 1f);
    }

    // ------------------------------------------------------------------ istasyon sesleri (adım süresince)
    /** SoundPool çıkışı soyutlaması (testte yerine konabilir). */
    public interface Sink {
        /** Akış kimliği (0: çalınamadı). */
        int start(String name, float left, float right, float rate, int loop);
        void stop(int stream);
    }

    private final class PoolSink implements Sink {
        @Override public int start(String name, float left, float right, float rate, int loop) {
            SoundPool p = pool;
            if (p == null || name == null) return 0;
            Integer id = ids.get(name);
            if (id == null || !loaded.contains(id)) return 0;
            try { return p.play(id, left, right, 1, loop, rate); } catch (Exception e) { return 0; }
        }

        @Override public void stop(int stream) {
            SoundPool p = pool;
            if (p == null || stream == 0) return;
            try { p.stop(stream); } catch (Exception ignored) { }
        }
    }

    /** Ses çıkışını değiştirir (testlerde çalınan sesleri izlemek için). */
    public void setSinkForTest(Sink s) { sink = s; }

    public static boolean isLooped(String name) {
        for (String n : LOOPED) if (n.equals(name)) return true;
        return false;
    }

    public static boolean isPulsed(String name) {
        for (String n : PULSED) if (n.equals(name)) return true;
        return false;
    }

    private final class Job {
        final String key, name;
        final float pan, volume;
        long endAt;          // uptime ms
        long remainingMs;    // duraklatılmışken kalan
        int stream;
        boolean running;
        Runnable pulse, stopper;
        /** Sıralı ses planı (otomatik malzeme: domates, marul, sos...); null: döngü/darbe işi. */
        List<Cue> cues;
        long totalMs;
        final List<Runnable> cueRunnables = new ArrayList<>();

        Job(String key, String name, float pan, float volume, long ms) {
            this.key = key; this.name = name; this.pan = pan; this.volume = volume; this.remainingMs = ms;
        }
    }

    /** Sıralı ses planında bir vuruş: işin başından atMs sonra bir kez çalınır. */
    public static final class Cue {
        public final long atMs;
        public final String name;
        public final float rate;

        public Cue(long atMs, String name, float rate) {
            this.atMs = atMs;
            this.name = name;
            this.rate = rate;
        }
    }

    private final Handler loopHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Job> jobs = new HashMap<>();
    private final Random rnd = new Random();
    private boolean loopsPaused;

    /**
     * Bir istasyon sesini {@code seconds} süresince çalar. Döngülenebilen sesler SoundPool döngüsüyle sürer,
     * darbeli sesler 0,35-0,6 sn aralıkla (±%5 perde, ±%8 ses) tekrarlanır. Aynı anahtar yeniden başlarsa eskisi kesilir.
     * {@link #stopLoop} ya da süre dolunca durur; {@link #pauseLoops} ile bekletilir.
     */
    public void startLoop(String key, String name, float pan, float volume, float seconds) {
        if (key == null || name == null) return;
        stopLoop(key);
        if (pool == null || sfxVolume <= 0f || seconds <= 0f) return;
        Job j = new Job(key, name, pan, volume, (long) (seconds * 1000f));
        jobs.put(key, j);
        if (!loopsPaused) begin(j);
    }

    /**
     * Bir iş süresince sıralı vuruşlar çalar (her cue kendi zamanında bir kez). Otomatik malzeme eklemede her malzemenin
     * kendi sesi (domates, marul, sos) ekleme süresine yayılarak çalınır. Duraklatılabilir; {@link #stopLoop} kalanları iptal eder.
     */
    public void startSequence(String key, List<Cue> cues, float pan, float volume, float seconds) {
        if (key == null || cues == null || cues.isEmpty()) return;
        stopLoop(key);
        if (pool == null || sfxVolume <= 0f || seconds <= 0f) return;
        Job j = new Job(key, cues.get(0).name, pan, volume, (long) (seconds * 1000f));
        j.cues = new ArrayList<>(cues);
        j.totalMs = j.remainingMs;
        jobs.put(key, j);
        if (!loopsPaused) begin(j);
    }

    private void begin(final Job j) {
        j.running = true;
        j.endAt = SystemClock.uptimeMillis() + j.remainingMs;
        j.stopper = new Runnable() { @Override public void run() { stopLoop(j.key); } };
        loopHandler.postDelayed(j.stopper, j.remainingMs);
        if (j.cues != null) {
            long elapsed = j.totalMs - j.remainingMs;
            for (final Cue c : j.cues) {
                if (c.atMs < elapsed) continue;
                Runnable r = new Runnable() {
                    @Override public void run() {
                        if (!j.running) return;
                        sink.start(c.name, left(j.pan, j.volume), right(j.pan, j.volume), Math.max(0.5f, Math.min(2f, c.rate)), 0);
                    }
                };
                j.cueRunnables.add(r);
                loopHandler.postDelayed(r, c.atMs - elapsed);
            }
        } else if (isLooped(j.name)) {
            j.stream = sink.start(j.name, left(j.pan, j.volume), right(j.pan, j.volume), 1f, -1);
        } else {
            j.pulse = new Runnable() {
                @Override public void run() {
                    if (!j.running) return;
                    float rate = 1f + (rnd.nextFloat() * 2f - 1f) * PULSE_RATE_JITTER;
                    float vol = j.volume * (1f + (rnd.nextFloat() * 2f - 1f) * PULSE_VOL_JITTER);
                    sink.start(j.name, left(j.pan, vol), right(j.pan, vol), rate, 0);
                    long gap = (long) ((PULSE_MIN_S + rnd.nextFloat() * (PULSE_MAX_S - PULSE_MIN_S)) * 1000f);
                    if (SystemClock.uptimeMillis() + gap < j.endAt) loopHandler.postDelayed(this, gap);
                }
            };
            j.pulse.run();
        }
    }

    private void halt(Job j) {
        j.running = false;
        if (j.stopper != null) loopHandler.removeCallbacks(j.stopper);
        if (j.pulse != null) loopHandler.removeCallbacks(j.pulse);
        for (Runnable r : j.cueRunnables) loopHandler.removeCallbacks(r);
        j.cueRunnables.clear();
        if (j.stream != 0) sink.stop(j.stream);
        j.stream = 0;
    }

    public void stopLoop(String key) {
        Job j = jobs.remove(key);
        if (j != null) halt(j);
    }

    public void stopAllLoops() {
        for (Job j : new ArrayList<>(jobs.values())) halt(j);
        jobs.clear();
    }

    /** Duraklatma: sesler susar, kalan süreleri saklanır ({@link #resumeLoops} ile sürer). */
    public void pauseLoops() {
        loopsPaused = true;
        long now = SystemClock.uptimeMillis();
        for (Job j : new ArrayList<>(jobs.values())) {
            if (j.running) j.remainingMs = Math.max(0L, j.endAt - now);
            halt(j);
            if (j.remainingMs <= 0L) jobs.remove(j.key);
        }
    }

    public void resumeLoops() {
        loopsPaused = false;
        for (Job j : new ArrayList<>(jobs.values())) if (!j.running) begin(j);
    }

    /** Çalışan (duraklatılmamış) istasyon sesi sayısı; testler içindir. */
    public int activeLoopCount() {
        int n = 0;
        for (Job j : jobs.values()) if (j.running) n++;
        return n;
    }

    /** Gecikmeli çalma (art arda efektler için). */
    public void playLater(final String name, final float pan, long delayMs) {
        sfxHandler.postDelayed(new Runnable() {
            @Override public void run() { if (!paused) play(name, pan); }
        }, delayMs);
    }

    public void cancelScheduled() {
        sfxHandler.removeCallbacksAndMessages(null);
        stopAllLoops();
        loopsPaused = false;
    }

    // ------------------------------------------------------------------ müzik
    public void playMusic(String name) {
        if (name == null) { stopMusic(); return; }
        if (name.equals(musicName) && music != null) {
            if (!paused && !focusPaused) try { if (!music.isPlaying()) music.start(); } catch (Exception ignored) { }
            return;
        }
        fadeOutCurrent();
        musicName = name;
        int res = resId(name);
        if (res == 0) return;
        MediaPlayer mp = new MediaPlayer();
        try {
            AssetFileDescriptor afd = ctx.getResources().openRawResourceFd(res);
            if (afd == null) { mp.release(); return; }
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            mp.setLooping(true);
            mp.prepare();
        } catch (Exception e) {
            try { mp.release(); } catch (Exception ignored) { }
            return;
        }
        music = mp;
        fade = 0f;
        applyMusicVolume();
        if (!paused) {
            focus.request();
            if (!focusPaused) try { mp.start(); } catch (Exception ignored) { }
            fadeIn();
        }
    }

    public String currentMusic() { return musicName; }

    private void fadeIn() {
        final int token = ++fadeToken;
        handler.post(new Runnable() {
            @Override public void run() {
                if (token != fadeToken || music == null) return;
                fade = Math.min(1f, fade + 0.1f);
                applyMusicVolume();
                if (fade < 1f) handler.postDelayed(this, 60);
            }
        });
    }

    private void applyMusicVolume() {
        if (music == null) return;
        float v = musicOutputVolume();
        try { music.setVolume(v, v); } catch (Exception ignored) { }
    }

    public void stopMusic() {
        fadeOutCurrent();
        focus.abandon();
    }

    /** Odağı bırakmadan geçerli müziği sönümleyip bırakır (parça değişiminde odak korunur). */
    private void fadeOutCurrent() {
        fadeToken++;
        musicName = null;
        final MediaPlayer old = music;
        music = null;
        if (old == null) return;
        retiring.add(old);
        // kısa sönümleme sonra bırak
        final float start = musicVolume * fade;
        final int[] step = {0};
        handler.post(new Runnable() {
            @Override public void run() {
                step[0]++;
                float v = start * Math.max(0f, 1f - step[0] / 5f);
                try { old.setVolume(v, v); } catch (Exception ignored) { }
                if (step[0] < 5) { handler.postDelayed(this, 40); return; }
                if (!retiring.remove(old)) return; // release() zaten bıraktı
                try { old.stop(); } catch (Exception ignored) { }
                try { old.release(); } catch (Exception ignored) { }
            }
        });
    }

    // ------------------------------------------------------------------ ambiyans (v0.9)
    /**
     * Ambiyans döngüsünü ister ({@link #AMBIENCE_SHOP}); null durdurur. Dosya (res/raw) yoksa sessizce atlanır, istek yine
     * kaydedilir. Ses düzeyi müzikten bağımsızdır ({@link #setAmbienceVolume}), konuşma/sistem kısmasında kısılır.
     */
    public void playAmbience(String name) {
        if (name == null) { stopAmbience(); return; }
        if (name.equals(ambWanted)) { syncAmbience(); return; }
        releaseAmbience();
        ambWanted = name;
        int res = resId(name);
        if (res == 0) return;
        MediaPlayer mp = new MediaPlayer();
        try {
            AssetFileDescriptor afd = ctx.getResources().openRawResourceFd(res);
            if (afd == null) { mp.release(); return; }
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            mp.setLooping(true);
            mp.prepare();
        } catch (Exception e) {
            try { mp.release(); } catch (Exception ignored) { }
            return;
        }
        amb = mp;
        applyAmbienceVolume();
        syncAmbience();
    }

    public void stopAmbience() {
        ambWanted = null;
        releaseAmbience();
    }

    private void releaseAmbience() {
        MediaPlayer old = amb;
        amb = null;
        if (old == null) return;
        try { old.stop(); } catch (Exception ignored) { }
        try { old.release(); } catch (Exception ignored) { }
    }

    /** Oyun Duraklat paneli / ekran dışı: ambiyans susar (true) ya da devam eder (false). */
    public void setAmbienceHold(boolean hold) {
        ambHold = hold;
        syncAmbience();
    }

    /** İstenen ambiyans adı (dosya olmasa da); istek yoksa null. */
    public String currentAmbience() { return ambWanted; }

    /** Ambiyans şu an çalıyor olmalı mı (istenmiş, duraklatılmamış, odak kaybı yok). */
    public boolean ambienceShouldPlay() { return ambWanted != null && !paused && !ambHold && !focusPaused; }

    /** Ambiyansın fiilen çalındığı ses düzeyi (0..1): ayar x (konuşma/sistem kısması). */
    public float ambienceOutputVolume() { return ambienceVolume * (isDucked() ? DUCK_FACTOR : 1f); }

    private void applyAmbienceVolume() {
        if (amb == null) return;
        float v = ambienceOutputVolume();
        try { amb.setVolume(v, v); } catch (Exception ignored) { }
    }

    /** Oynatıcıyı istenen duruma getirir (çalmalıysa başlatır, aksi halde duraklatır). */
    private void syncAmbience() {
        if (amb == null) return;
        try {
            if (ambienceShouldPlay() && ambienceVolume > 0f) { if (!amb.isPlaying()) amb.start(); }
            else if (amb.isPlaying()) amb.pause();
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ yaşam döngüsü
    public void pause() {
        paused = true;
        syncAmbience();
        sfxHandler.removeCallbacksAndMessages(null);
        pauseLoops();
        if (music != null) try { music.pause(); } catch (Exception ignored) { }
        focus.abandon();
        if (pool != null) try { pool.autoPause(); } catch (Exception ignored) { }
    }

    public void resume() {
        paused = false;
        if (music != null) {
            focus.request();
            focusPaused = false;
            try { music.start(); } catch (Exception ignored) { }
            fadeIn();
        }
        syncAmbience();
        if (pool != null) try { pool.autoResume(); } catch (Exception ignored) { }
    }

    public void release() {
        focus.abandon();
        handler.removeCallbacksAndMessages(null);
        sfxHandler.removeCallbacksAndMessages(null);
        stopAllLoops();
        loopHandler.removeCallbacksAndMessages(null);
        releaseAmbience();
        fadeToken++;
        for (MediaPlayer old : new java.util.ArrayList<>(retiring)) {
            try { old.stop(); } catch (Exception ignored) { }
            try { old.release(); } catch (Exception ignored) { }
        }
        retiring.clear();
        if (music != null) {
            try { music.stop(); } catch (Exception ignored) { }
            try { music.release(); } catch (Exception ignored) { }
            music = null;
        }
        if (pool != null) { try { pool.release(); } catch (Exception ignored) { } pool = null; }
    }
}
