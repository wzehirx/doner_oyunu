package com.doner.oyunu;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;

import com.doner.oyunu.audio.Haptics;
import com.doner.oyunu.audio.SoundManager;
import com.doner.oyunu.audio.Speaker;
import com.doner.oyunu.engine.SaveData;
import com.doner.oyunu.ui.BriefingScreen;
import com.doner.oyunu.ui.GradientBackground;
import com.doner.oyunu.ui.MenuScreen;
import com.doner.oyunu.ui.Screen;
import com.doner.oyunu.ui.Theme;
import com.doner.oyunu.ui.Ui;

/** Tek Activity: ekran yöneticisi, kayıt, ses ve konuşma servisleri. */
public class MainActivity extends Activity {
    private static final String PREFS = "doner_oyunu";
    private static final String KEY_SAVE = "save";
    private static final String KEY_STARTED = "game_started";

    public SaveData save;
    public SoundManager sound;
    public Speaker speaker;
    public Haptics haptics;

    private SharedPreferences prefs;
    private FrameLayout container;
    private Screen current;
    private View currentView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        save = SaveData.parse(prefs.getString(KEY_SAVE, null));
        sound = new SoundManager(this);
        speaker = new Speaker(this);
        haptics = new Haptics(this);

        getWindow().setStatusBarColor(Theme.BG_TOP);
        getWindow().setNavigationBarColor(Theme.BG_BOTTOM);
        container = new FrameLayout(this);
        container.setBackground(new GradientBackground());
        setContentView(container);
        speaker.setAnchor(container);
        speaker.setSpeechListener(new Speaker.SpeechListener() {
            @Override public void onSpeaking(boolean active) { sound.setSpeechDucking(active); }
        });
        applySettings();
        show(new MenuScreen(this));
    }

    // ------------------------------------------------------------------ kayıt / ayarlar
    public boolean hasGame() { return prefs.getBoolean(KEY_STARTED, false); }

    public void saveNow() {
        try { prefs.edit().putString(KEY_SAVE, save.serialize()).apply(); } catch (Exception ignored) { }
    }

    /** Kayıtlı oyunu diske yazılan haliyle yeniden yükler (yarım gün bırakılınca). */
    public void reloadSave() {
        SaveData loaded = SaveData.parse(prefs.getString(KEY_SAVE, null));
        save = loaded;
        applySettings();
    }

    /** Yeni oyun: ayarlar korunur, ilerleme sıfırlanır. */
    public void newGame() {
        SaveData old = save;
        SaveData s = SaveData.newGame();
        s.tempo = old.tempo;
        s.speechOn = old.speechOn;
        s.speechRate = old.speechRate;
        s.vibration = old.vibration;
        s.musicVolume = old.musicVolume;
        s.sfxVolume = old.sfxVolume;
        s.ambienceVolume = old.ambienceVolume;
        s.tutorialDone = old.tutorialDone;
        s.apprenticeAnnounce = old.apprenticeAnnounce;
        s.flowMode = old.flowMode; // oyun akışı bir ayardır: yeni oyunda korunur
        s.flowChosen = old.flowChosen;
        s.timePressure = old.timePressure; // süre baskısı da bir ayardır: yeni oyunda korunur
        save = s;
        prefs.edit().putBoolean(KEY_STARTED, true).apply();
        saveNow();
    }

    /** Devam et: kayıt var ise brifinge gider. */
    public void markStarted() { prefs.edit().putBoolean(KEY_STARTED, true).apply(); }

    public void applySettings() {
        sound.setMusicVolume((float) save.musicVolume);
        sound.setSfxVolume((float) save.sfxVolume);
        sound.setAmbienceVolume((float) save.ambienceVolume);
        speaker.setEnabled(save.speechOn);
        speaker.setRate((float) save.speechRate);
        haptics.setEnabled(save.vibration);
    }

    public Screen currentScreen() { return current; }

    public void startBriefing() { show(new BriefingScreen(this)); }

    // ------------------------------------------------------------------ ekran geçişi
    public void show(final Screen next) {
        final Screen prev = current;
        final View prevView = currentView;
        sound.cancelScheduled();
        if (prev != null) prev.onExit();

        final View v = next.create();
        current = next;
        currentView = v;
        if (Build.VERSION.SDK_INT >= 28) v.setAccessibilityPaneTitle(next.title());
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        container.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sound.playMusic(next.music());
        sound.setAmbienceHold(false);
        sound.playAmbience(next.ambience()); // gün ekranı dışında null: ambiyans durur

        boolean anim = Ui.animationsOn(this);
        long dur = anim ? 260 : 0;
        if (prevView != null) {
            prevView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            if (anim) {
                prevView.animate().alpha(0f).translationX(-Ui.dp(this, 28)).setDuration(200)
                        .withEndAction(new Runnable() {
                            @Override public void run() { container.removeView(prevView); }
                        }).start();
            } else {
                container.removeView(prevView);
            }
        }
        if (anim) {
            v.setAlpha(0f);
            v.setTranslationX(Ui.dp(this, 36));
            v.animate().alpha(1f).translationX(0f).setDuration(dur).start();
        }
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (current != next) return;
                next.onEnter();
                moveFocus(next);
            }
        }, dur + 40);
    }

    /** Ekran değişince başlığa odak (TalkBack) / başlığı seslendirme (TTS). */
    private void moveFocus(Screen s) {
        View f = s.focusTarget();
        if (f != null) {
            f.requestFocus();
            f.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
            if (speaker.isTalkBackOn()) {
                f.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
            }
        }
        speaker.screenChanged(s.title());
    }

    /** Aynı ekran içinde bir görünüme odak taşı (ör. duraklatma paneli). */
    public void focusOn(View f) {
        if (f == null) return;
        f.requestFocus();
        f.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        if (speaker.isTalkBackOn()) {
            f.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
        }
    }

    // ------------------------------------------------------------------ yaşam döngüsü
    @Override protected void onPause() {
        super.onPause();
        sound.pause();
        speaker.stop();
        if (current != null) current.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        sound.resume();
        if (current != null) current.onResume();
    }

    @Override public void onBackPressed() {
        if (current != null && current.onBack()) return;
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (current != null) current.onExit();
        sound.release();
        speaker.shutdown();
        super.onDestroy();
    }
}
