package com.doner.oyunu.audio;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Olay başına ayırt edilebilir titreşim desenleri. Ayarlardan kapatılabilir. */
public final class Haptics {
    private final Vibrator vibrator;
    private boolean enabled = true;

    @SuppressWarnings("deprecation")
    public Haptics(Context ctx) {
        Vibrator v = null;
        try { v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE); } catch (Exception ignored) { }
        vibrator = v;
    }

    public void setEnabled(boolean on) { enabled = on; }

    public boolean isEnabled() { return enabled; }

    /** timings: [bekleme, açık, kapalı, açık, ...] milisaniye. */
    @SuppressWarnings("deprecation")
    public void pattern(long... timings) {
        if (!enabled || vibrator == null) return;
        try {
            if (!vibrator.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, -1));
            } else {
                vibrator.vibrate(timings, -1);
            }
        } catch (Exception ignored) { }
    }

    public void tap() { pattern(0, 12); }
    public void stepStart() { pattern(0, 18, 50, 18); }
    public void stepDone() { pattern(0, 90); }
    public void orderReady() { pattern(0, 320); }
    public void error() { pattern(0, 60, 60, 60, 60, 60); }
    public void warning() { pattern(0, 40, 90, 40); }
    public void critical() { pattern(0, 50, 60, 50, 60, 120); }
    public void deliver() { pattern(0, 30, 40, 100); }
    public void customer() { pattern(0, 25, 70, 25); }
    public void starUp() { pattern(0, 25, 40, 25, 40, 70); }
    public void starDown() { pattern(0, 180); }
    public void buy() { pattern(0, 20, 30, 45); }
    public void dayStart() { pattern(0, 60, 80, 60, 80, 160); }
    public void dayEnd() { pattern(0, 250, 100, 250); }
}
