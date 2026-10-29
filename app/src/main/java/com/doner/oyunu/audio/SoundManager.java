package com.doner.oyunu.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Ses efektleri (SoundPool, sol/sağ pan) ve döngülü müzik (MediaPlayer).
 * Ses dosyaları res/raw altından ADIYLA (getIdentifier) yüklenir; eksik dosya sessizce atlanır.
 */
public final class SoundManager {
    public static final String[] SFX = {
            "sfx_customer_arrive", "sfx_order_new", "sfx_slice", "sfx_bread_warm", "sfx_sauce", "sfx_wrap",
            "sfx_grill_sizzle", "sfx_pour", "sfx_step_done", "sfx_deliver", "sfx_coin", "sfx_error",
            "sfx_warning", "sfx_customer_happy", "sfx_customer_angry", "sfx_star_up", "sfx_star_down",
            "sfx_day_start", "sfx_day_end", "sfx_buy", "sfx_tap", "sfx_tick"
    };

    private final Context ctx;
    /** Müzik geçişleri (iptal edilmez). */
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Gecikmeli efektler (ekran değişince/duraklatınca iptal edilir). */
    private final Handler sfxHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Integer> ids = new HashMap<>();
    private final Set<Integer> loaded = new HashSet<>();
    private SoundPool pool;
    private float sfxVolume = 1f;
    private float musicVolume = 0.7f;

    private MediaPlayer music;
    /** Sönümlenip bırakılmayı bekleyen eski oynatıcılar (release sırasında sızmasın). */
    private final java.util.List<MediaPlayer> retiring = new java.util.ArrayList<>();
    private String musicName;
    private boolean paused;
    private float fade = 1f; // 0..1 müzik geçiş çarpanı
    private int fadeToken;

    public SoundManager(Context context) {
        ctx = context.getApplicationContext();
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

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    // ------------------------------------------------------------------ efekt
    public void play(String name) { play(name, 0f); }

    /** pan: -1 (tam sol) ... +1 (tam sağ). */
    public void play(String name, float pan) {
        if (pool == null || sfxVolume <= 0f) return;
        Integer id = ids.get(name);
        if (id == null || !loaded.contains(id)) return;
        float p = Math.max(-1f, Math.min(1f, pan));
        float left = sfxVolume * (p > 0 ? 1f - p * 0.85f : 1f);
        float right = sfxVolume * (p < 0 ? 1f + p * 0.85f : 1f);
        try { pool.play(id, left, right, 1, 0, 1f); } catch (Exception ignored) { }
    }

    /** Gecikmeli çalma (art arda efektler için). */
    public void playLater(final String name, final float pan, long delayMs) {
        sfxHandler.postDelayed(new Runnable() {
            @Override public void run() { if (!paused) play(name, pan); }
        }, delayMs);
    }

    public void cancelScheduled() { sfxHandler.removeCallbacksAndMessages(null); }

    // ------------------------------------------------------------------ müzik
    public void playMusic(String name) {
        if (name == null) { stopMusic(); return; }
        if (name.equals(musicName) && music != null) {
            if (!paused) try { if (!music.isPlaying()) music.start(); } catch (Exception ignored) { }
            return;
        }
        stopMusic();
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
            try { mp.start(); } catch (Exception ignored) { }
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
        float v = musicVolume * fade;
        try { music.setVolume(v, v); } catch (Exception ignored) { }
    }

    public void stopMusic() {
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

    // ------------------------------------------------------------------ yaşam döngüsü
    public void pause() {
        paused = true;
        sfxHandler.removeCallbacksAndMessages(null);
        if (music != null) try { music.pause(); } catch (Exception ignored) { }
        if (pool != null) try { pool.autoPause(); } catch (Exception ignored) { }
    }

    public void resume() {
        paused = false;
        if (music != null) {
            try { music.start(); } catch (Exception ignored) { }
            fadeIn();
        }
        if (pool != null) try { pool.autoResume(); } catch (Exception ignored) { }
    }

    public void release() {
        handler.removeCallbacksAndMessages(null);
        sfxHandler.removeCallbacksAndMessages(null);
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
