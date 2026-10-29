package com.doner.oyunu.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;

/**
 * AudioManager ses odağı sarmalayıcısı. API 26+ için {@link AudioFocusRequest}, API 24-25 için eski
 * {@code requestAudioFocus(listener, stream, gain)} kullanılır.
 */
public final class AudioFocus {
    /** Odak değişimi ({@link AudioManager#AUDIOFOCUS_GAIN} vb.). */
    public interface Listener { void onFocusChange(int change); }

    private final AudioManager am;
    private final int sdk;
    private final int usage;
    private final int contentType;
    private final int gain;
    private final Listener listener;
    private final AudioManager.OnAudioFocusChangeListener systemListener;
    private Object request26; // API 26+: AudioFocusRequest
    private boolean held;

    public AudioFocus(Context ctx, int usage, int contentType, int gain, Listener l) {
        this((AudioManager) ctx.getApplicationContext().getSystemService(Context.AUDIO_SERVICE),
                Build.VERSION.SDK_INT, usage, contentType, gain, l);
    }

    /** Sınama için: AudioManager ve API düzeyi verilebilir. */
    AudioFocus(AudioManager am, int sdk, int usage, int contentType, int gain, Listener l) {
        this.am = am;
        this.sdk = sdk;
        this.usage = usage;
        this.contentType = contentType;
        this.gain = gain;
        this.listener = l;
        this.systemListener = new AudioManager.OnAudioFocusChangeListener() {
            @Override public void onAudioFocusChange(int change) {
                if (change == AudioManager.AUDIOFOCUS_LOSS) held = false;
                else if (change == AudioManager.AUDIOFOCUS_GAIN) held = true;
                if (listener != null) listener.onFocusChange(change);
            }
        };
    }

    public boolean isHeld() { return held; }

    /** Odak ister; verildiyse true. Zaten tutuluyorsa tekrar istemez. */
    public boolean request() {
        if (held) return true;
        if (am == null) return false;
        int r;
        try {
            if (sdk >= 26) {
                AudioFocusRequest req = Api26.build(usage, contentType, gain, systemListener);
                request26 = req;
                r = am.requestAudioFocus(req);
            } else {
                r = am.requestAudioFocus(systemListener, AudioManager.STREAM_MUSIC, gain);
            }
        } catch (Exception e) {
            return false;
        }
        held = r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return held;
    }

    /** Odağı bırakır (yoksa bir şey yapmaz); önceki sahibine odak geri döner (sesi açılır). */
    public void abandon() {
        if (!held && request26 == null) return;
        held = false;
        if (am == null) return;
        try {
            if (sdk >= 26) {
                if (request26 != null) am.abandonAudioFocusRequest((AudioFocusRequest) request26);
            } else {
                am.abandonAudioFocus(systemListener);
            }
        } catch (Exception ignored) { }
        request26 = null;
    }

    /** AudioFocusRequest yalnızca API 26+ yolunda yüklenir (API 24-25'te sınıf yüklenmez). */
    private static final class Api26 {
        static AudioFocusRequest build(int usage, int contentType, int gain, AudioManager.OnAudioFocusChangeListener l) {
            AudioAttributes attrs = new AudioAttributes.Builder().setUsage(usage).setContentType(contentType).build();
            return new AudioFocusRequest.Builder(gain).setAudioAttributes(attrs).setOnAudioFocusChangeListener(l).build();
        }
    }
}
